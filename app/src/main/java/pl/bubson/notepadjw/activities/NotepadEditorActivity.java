package pl.bubson.notepadjw.activities;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ActionMenuView;
import androidx.core.content.ContextCompat;
import io.github.mthli.knife.KnifeText;
import pl.bubson.notepadjw.R;
import pl.bubson.notepadjw.core.HyperlinkVerseTextView;
import pl.bubson.notepadjw.core.RichSelectableEditText;
import pl.bubson.notepadjw.core.Verse;
import pl.bubson.notepadjw.services.InstallLanguageService;
import pl.bubson.notepadjw.utils.HtmlPrinter;
import pl.bubson.notepadjw.utils.Language;
import pl.bubson.notepadjw.utils.Permissions;
import pl.bubson.notepadjw.utils.SpanToHtmlConverter;

import static pl.bubson.notepadjw.activities.NotepadEditorActivity.Mode.EDIT;
import static pl.bubson.notepadjw.activities.NotepadEditorActivity.Mode.UNSET;
import static pl.bubson.notepadjw.activities.NotepadEditorActivity.Mode.VIEW;
import static pl.bubson.notepadjw.core.RichSelectableEditText.COLOR_MARKER_BLUE;
import static pl.bubson.notepadjw.core.RichSelectableEditText.COLOR_MARKER_GREEN;
import static pl.bubson.notepadjw.core.RichSelectableEditText.COLOR_MARKER_YELLOW;
import static pl.bubson.notepadjw.core.RichSelectableEditText.COLOR_TEXT_GREEN;
import static pl.bubson.notepadjw.utils.LanguageUtils.getCurrentVersesLanguage;

public class NotepadEditorActivity extends AppCompatActivity {

    public static final double MINIMUM_VERSE_HEIGHT_PROPORTION = 0.05;
    public static final double MAXIMUM_VERSE_HEIGHT_PROPORTION = 0.45;
    public static final int TOLERANCE_TO_DRAG_VERSE_AREA_EDGE_IN_PX = 100;
    public static final int MAX_VERSE_LINES_IN_AUTO_FIT = 10;
    public static final int CHARS_AT_THE_END_TO_CHECK = 50;
    private static final int MAX_FILE_SIZE_IN_BYTES = 300000;
    private static final String TAG = "NotepadEditorActivity";
    private static final String VERSE_AREA_SIZE_EDIT_MODE_KEY = "verseAreaSizeEditMode";
    private static final String VERSE_AREA_SIZE_VIEW_MODE_KEY = "verseAreaSizeViewMode";
    private static int screenHeight;
    View.OnLongClickListener longClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            try {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                String textToCopy = ((TextView) view).getText().toString();
                ClipData clip = ClipData.newPlainText("verse", textToCopy);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getApplicationContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        }
    };
    private int cursorPosition, textModifications;
    private Language versesLanguage;
    private Context activityContext = this;
    private RichSelectableEditText noteEditText;
    private MenuItem viewModeButton, editModeButton, saveButton, boldTextButton, italicTextButton, underlineTextButton, bulletTextButton, undoButton, redoButton, formatClearButton, printButton;
    private MenuItem textRedButton, textBlueButton, textGreenButton, markerYellowButton, markerBlueButton, markerGreenButton;
    private ActionMenuView bottomBar;
    private TextView versePreviewTextInEditMode, versePreviewTextInViewMode;
    private final TextWatcher mTextEditorWatcher = new TextWatcher() {

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        public void afterTextChanged(Editable s) {
            try {
                String text;

                // performance improvement - verifying last 50 chars before cursor
                if (noteEditText.getSelectionEnd() >= CHARS_AT_THE_END_TO_CHECK) {
                    text = s.toString().substring(noteEditText.getSelectionEnd() - CHARS_AT_THE_END_TO_CHECK, noteEditText.getSelectionEnd());
                } else {
                    text = s.toString().substring(0, noteEditText.getSelectionEnd());
                }
                Log.d(TAG, "text: " + text);

                if (Verse.isTextContainingVerseAtTheEnd(text, versesLanguage)) {
                    versePreviewTextInEditMode.setText(Html.fromHtml(Verse.getTextOfLastVerse(getApplicationContext(), text, versesLanguage)));
                    versePreviewTextInEditMode.scrollTo(0, 0);
                }
                textModifications++;
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // arbitrarily chosen to distinguish newer and older devices. M = Android 6.0
//                    if (textModifications % 20 == 0)
//                        saveDocumentIfChanged(); // saving note after every 20 changes in the text, which are 3-4 words (on newer devices)
//                } else {
//                    if (textModifications % 100 == 0)
//                        saveDocumentIfChanged(); // saving note after every 100 changes in the text, which are 15-20 words (on older devices)
//                }
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), R.string.unexpected_exception, Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
    };
    private HyperlinkVerseTextView noteTextView;
    private ViewFlipper viewFlipper;
    private Mode currentMode, savedMode;
    private File currentFile;
    private String htmlTextInFile;
    private CharSequence savedVersePreview;
    private HyperlinkVerseTextView.OnLinkClickListener linkClickListener = new HyperlinkVerseTextView.OnLinkClickListener() {

        @Override
        public void onLinkClick(String linkText, int id) {
            Verse verse = new Verse(activityContext, linkText, versesLanguage);
            versePreviewTextInViewMode.setText(Html.fromHtml(verse.getVerseDescriptorAndTextInHtmlForm()));
            versePreviewTextInViewMode.scrollTo(0, 0);
        }
    };
    private boolean verseClosed = false;

    private static void hideSoftKeyboard(Activity activity) {
        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        View view = activity.getCurrentFocus();
        if (view != null) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prepareDatabase(); // with preload of example verse
        forceVisibilityOfThreeDotsInMenuBar();
        savedMode = UNSET;
    }

    // this feature doesn't work well... it closes verse when user scrolls it. So it's disabled for now
    private void closeVerse(View view) {
        try {
            if (!verseClosed) {
                view.getLayoutParams().height = (int) (MINIMUM_VERSE_HEIGHT_PROPORTION * screenHeight);
                view.requestLayout();
                verseClosed = true;
            } else {
                view.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                verseClosed = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() { // executed e.g. on back from Settings or when screen was switched on
        super.onStart();
        setScreenHeight(); // might be changed in Settings - should be in onStart
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        setLayout(); // might be changed in Settings - should be in onStart

        // Flipper to change layouts
        viewFlipper = (ViewFlipper) findViewById(R.id.viewFlipper); // below line is connected with this
        currentMode = EDIT; // because first view (when ViewFlipper is created) contains EditText

        // Edit layout
        noteEditText = (RichSelectableEditText) findViewById(R.id.edit_text);
        if (noteEditText != null) {
            noteEditText.addTextChangedListener(mTextEditorWatcher);
            // Trying to avoid "Done" button / fullscreen in landscape mode
//            noteEditText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI); // option 1
//            noteEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI); // option 2
            noteEditText.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN); // option 3
        }

        versePreviewTextInEditMode = (TextView) findViewById(R.id.text_view_in_edit_layout);
        versePreviewTextInEditMode.setMovementMethod(ScrollingMovementMethod.getInstance());
        versePreviewTextInEditMode.setOnLongClickListener(longClickListener);
//        versePreviewTextInEditMode.setOnClickListener(quickClickListener);

        // View layout
        noteTextView = (HyperlinkVerseTextView) findViewById(R.id.hyperlink_verse_text_view);
        if (noteTextView != null) {
            noteTextView.setOnLinkClickListener(linkClickListener);
        }
        versePreviewTextInViewMode = (TextView) findViewById(R.id.text_view_in_view_layout);
        versePreviewTextInViewMode.setMovementMethod(ScrollingMovementMethod.getInstance());
        versePreviewTextInViewMode.setOnLongClickListener(longClickListener);

        setVerseAreaSizeIfItsChangeable();
        setVerseAreaSizing();
        setVersesLanguage();
        openFileFromIntent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setFontSizes();
        setFontColors();
        noteTextView.setTextWithVerses(noteEditText.getText(), versesLanguage); // to refresh verses language
        invalidateOptionsMenu();
        if (currentMode.equals(EDIT)) {
            restoreCursorPositionAndVerseInEditMode();
            noteEditText.showSoftInput();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideSoftKeyboard(this);
        cursorPosition = noteEditText.getSelectionStart();
        savedMode = currentMode;
        savedVersePreview = versePreviewTextInEditMode.getText();
        saveDocumentIfChanged();
        saveVerseAreaSizeIfItsChangeable();
    }

    private void prepareDatabase() {
        Intent installLanguageServiceIntent = new Intent(this, InstallLanguageService.class);
        startService(installLanguageServiceIntent); // Intent without data will pre-install db if needed
    }

    private void setScreenHeight() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screenHeight = displayMetrics.heightPixels;
    }

    private void setVerseAreaSizeIfItsChangeable() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activityContext);
        final String autoFit = activityContext.getResources().getString(R.string.verse_area_size_auto);
        String sizeKey = activityContext.getResources().getString(R.string.verse_area_size_key);
        String verseAreaSize = sharedPref.getString(sizeKey, autoFit);
        String positionKey = activityContext.getResources().getString(R.string.verse_position_key);
        final String top = activityContext.getResources().getString(R.string.verse_position_top);
        String versePosition = sharedPref.getString(positionKey, top);
        if (!verseAreaSize.equals(autoFit) && versePosition.equals(top)) {
            int savedVerseSizeInEditMode = sharedPref.getInt(VERSE_AREA_SIZE_EDIT_MODE_KEY, 0);
            Log.v(TAG, "savedVerseSizeInEditMode = " + savedVerseSizeInEditMode);
            Log.v(TAG, "screenHeight = " + screenHeight);
            Log.v(TAG, "MAXIMUM_VERSE_HEIGHT_PROPORTION * screenHeight = " + MAXIMUM_VERSE_HEIGHT_PROPORTION * screenHeight);
            if (savedVerseSizeInEditMode > 0 && savedVerseSizeInEditMode <= MAXIMUM_VERSE_HEIGHT_PROPORTION * screenHeight)
                versePreviewTextInEditMode.getLayoutParams().height = savedVerseSizeInEditMode;
            int savedVerseSizeInViewMode = sharedPref.getInt(VERSE_AREA_SIZE_VIEW_MODE_KEY, 0);
            if (savedVerseSizeInViewMode > 0 && savedVerseSizeInEditMode <= MAXIMUM_VERSE_HEIGHT_PROPORTION * screenHeight)
                versePreviewTextInViewMode.getLayoutParams().height = savedVerseSizeInViewMode;
        }
    }

    private void setOnEditTextTouchListener() {
        noteEditText.addOnSelectionChangedListener(new RichSelectableEditText.OnSelectionChangedListener() {
            @Override
            public void onSelectionChanged(int selStart, int selEnd) {
                try {
                    if (noteEditText.hasSelection()) {
                        boldTextButton.setVisible(true);
                        italicTextButton.setVisible(true);
                        underlineTextButton.setVisible(true);
                        formatClearButton.setVisible(true);
                        setColorButtonsVisible(true);
                        bulletTextButton.setVisible(false);
                        undoButton.setVisible(false);
                        redoButton.setVisible(false);
                        viewModeButton.setVisible(false);
                    } else {
                        boldTextButton.setVisible(false);
                        italicTextButton.setVisible(false);
                        underlineTextButton.setVisible(false);
                        formatClearButton.setVisible(false);
                        setColorButtonsVisible(false);
                        bulletTextButton.setVisible(true);
                        undoButton.setVisible(true);
                        redoButton.setVisible(true);
                        viewModeButton.setVisible(true);
                    }
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), R.string.unexpected_exception, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void setColorButtonsVisible(boolean visible) {
        if (isThereAPlaceOnTopForColorsButtons()) {
            bottomBar.setVisibility(View.GONE);
            if (visible) {
                textRedButton.setVisible(true);
                textBlueButton.setVisible(true);
                textGreenButton.setVisible(true);
                markerYellowButton.setVisible(true);
                markerBlueButton.setVisible(true);
                markerGreenButton.setVisible(true);
            } else {
                textRedButton.setVisible(false);
                textBlueButton.setVisible(false);
                textGreenButton.setVisible(false);
                markerYellowButton.setVisible(false);
                markerBlueButton.setVisible(false);
                markerGreenButton.setVisible(false);
            }
        } else {
            textRedButton.setVisible(false);
            textBlueButton.setVisible(false);
            textGreenButton.setVisible(false);
            markerYellowButton.setVisible(false);
            markerBlueButton.setVisible(false);
            markerGreenButton.setVisible(false);
            if (visible) bottomBar.setVisibility(View.VISIBLE);
            else bottomBar.setVisibility(View.GONE);
        }
    }

    private void forceVisibilityOfThreeDotsInMenuBar() {
        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveVerseAreaSizeIfItsChangeable() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activityContext);
        String sizeKey = activityContext.getResources().getString(R.string.verse_area_size_key);
        final String autoFit = activityContext.getResources().getString(R.string.verse_area_size_auto);
        String verseAreaSize = sharedPref.getString(sizeKey, autoFit);
        if (!verseAreaSize.equals(autoFit)) {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt(VERSE_AREA_SIZE_EDIT_MODE_KEY, versePreviewTextInEditMode.getLayoutParams().height);
            editor.putInt(VERSE_AREA_SIZE_VIEW_MODE_KEY, versePreviewTextInViewMode.getLayoutParams().height);
            editor.commit();
        }
    }

    private void restoreCursorPositionAndVerseInEditMode() {
        try {
            noteEditText.requestFocus();
            noteEditText.setSelection(cursorPosition);
            versePreviewTextInEditMode.setText(savedVersePreview);
        } catch (Exception unexpectedException) {
            Toast.makeText(this, R.string.unexpected_exception, Toast.LENGTH_LONG).show();
        }
    }

    private void setLayout() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activityContext);
        String positionKey = activityContext.getResources().getString(R.string.verse_position_key);
        final String top = activityContext.getResources().getString(R.string.verse_position_top);
        String versePosition = sharedPref.getString(positionKey, top);
        if (versePosition.equals(top)) {
            setContentView(R.layout.activity_notepad_vertical);
        } else {
            setContentView(R.layout.activity_notepad_horizontal);
        }
    }

    private void setVerseAreaSizing() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activityContext);
        String positionKey = activityContext.getResources().getString(R.string.verse_position_key);
        final String top = activityContext.getResources().getString(R.string.verse_position_top);
        String versePosition = sharedPref.getString(positionKey, top);
        if (versePosition.equals(top)) { // sizing options are available only to verse on top mode
            String sizeKey = activityContext.getResources().getString(R.string.verse_area_size_key);
            final String autoFit = activityContext.getResources().getString(R.string.verse_area_size_auto);
            String verseAreaSize = sharedPref.getString(sizeKey, autoFit);
            if (!verseAreaSize.equals(autoFit)) {
                setViewSizeChangeable(versePreviewTextInEditMode);
                setViewSizeChangeable(versePreviewTextInViewMode);
            } else {
                setViewSizeNotChangeable(versePreviewTextInEditMode);
                setViewSizeNotChangeable(versePreviewTextInViewMode);
            }
        }
    }

    private void setViewSizeNotChangeable(TextView view) {
        view.setMaxLines(MAX_VERSE_LINES_IN_AUTO_FIT);
        view.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        });
    }

    private void setViewSizeChangeable(TextView view) {
        view.setMaxLines(100); // any high number
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    float y = event.getY();
                    int height = v.getMeasuredHeight();
                    float actualProportion = y / screenHeight;
                    if (height - y <= TOLERANCE_TO_DRAG_VERSE_AREA_EDGE_IN_PX
                            && actualProportion >= MINIMUM_VERSE_HEIGHT_PROPORTION
                            && actualProportion <= MAXIMUM_VERSE_HEIGHT_PROPORTION) {
                        v.getLayoutParams().height = (int) y;
                        v.requestLayout();
                    }
                    v.requestLayout();
                    return false;
                }
                return false;
            }
        });
    }

    private void setVersesLanguage() {
        versesLanguage = getCurrentVersesLanguage(this);
        Log.v(TAG, "Set verseLanguage: " + versesLanguage);
    }

    private boolean isThereAPlaceOnTopForColorsButtons() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false; // there is an issue with Android < 6.0 - Copy, Cut etc. buttons are placed on top of the top app bar, so we cannot place color buttons there (they would be hidden)
        return getResources().getBoolean(R.bool.isThereAPlaceOnTopForColorsButtons);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notepad, menu);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Inflate and initialize the bottom menu
        bottomBar = (ActionMenuView) findViewById(R.id.notepad_bottom_toolbar);
        if (bottomBar != null) {
            Menu bottomMenu = bottomBar.getMenu();
            bottomMenu.clear();
            getMenuInflater().inflate(R.menu.menu_notepad_bottom, bottomMenu);
            for (int i = 0; i < bottomMenu.size(); i++) {
                bottomMenu.getItem(i).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        return onOptionsItemSelected(item);
                    }
                });
            }
        }

        viewModeButton = menu.findItem(R.id.action_switch_to_view);
        editModeButton = menu.findItem(R.id.action_switch_to_edit);
        saveButton = menu.findItem(R.id.action_save);
        boldTextButton = menu.findItem(R.id.action_text_bold);
        italicTextButton = menu.findItem(R.id.action_text_italic);
        underlineTextButton = menu.findItem(R.id.action_text_underline);
        formatClearButton = menu.findItem(R.id.action_text_format_clear);
        bulletTextButton = menu.findItem(R.id.action_text_bullet);
        undoButton = menu.findItem(R.id.action_undo);
        redoButton = menu.findItem(R.id.action_redo);
        printButton = menu.findItem(R.id.action_print);
        textRedButton = menu.findItem(R.id.action_text_foreground_color_red);
        textBlueButton = menu.findItem(R.id.action_text_foreground_color_blue);
        textGreenButton = menu.findItem(R.id.action_text_foreground_color_green);
        markerYellowButton = menu.findItem(R.id.action_text_background_color_yellow);
        markerBlueButton = menu.findItem(R.id.action_text_background_color_blue);
        markerGreenButton = menu.findItem(R.id.action_text_background_color_green);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (currentMode.equals(EDIT)) {
            editModeButton.setVisible(false);
            viewModeButton.setVisible(true);
            boldTextButton.setVisible(false);
            italicTextButton.setVisible(false);
            underlineTextButton.setVisible(false);
            formatClearButton.setVisible(false);
            setColorButtonsVisible(false);
            bulletTextButton.setVisible(true);
            undoButton.setVisible(true);
            redoButton.setVisible(true);
        } else {
            editModeButton.setVisible(true);
            viewModeButton.setVisible(false);
            boldTextButton.setVisible(false);
            italicTextButton.setVisible(false);
            underlineTextButton.setVisible(false);
            formatClearButton.setVisible(false);
            setColorButtonsVisible(false);
            bulletTextButton.setVisible(false);
            undoButton.setVisible(false);
            redoButton.setVisible(false);
        }
        if (currentFile == null) {
            saveButton.setVisible(true);
        } else {
            saveButton.setVisible(false);
        }
        setOnEditTextTouchListener();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            noteEditText.setCustomSelectionActionModeCallback(new StyleCallback());
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            printButton.setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int sizePre, sizePost;
        // Handle item selection
        switch (item.getItemId()) {
            case android.R.id.home:
//                checkIfFileWasChangedAndCloseActivity();
                saveDocumentIfChanged();
                finish();
                return true;
            case R.id.action_switch_to_view:
                switchToMode(VIEW, true);
                return true;
            case R.id.action_switch_to_edit:
                switchToMode(EDIT, false);
                return true;
            case R.id.action_undo:
                cursorPosition = noteEditText.getSelectionStart();
                sizePre = noteEditText.getEditableText().length();
                noteEditText.undo();
                sizePost = noteEditText.getEditableText().length();
                try {
                    noteEditText.setSelection(cursorPosition + (sizePost - sizePre));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            case R.id.action_redo:
                cursorPosition = noteEditText.getSelectionStart();
                sizePre = noteEditText.getEditableText().length();
                noteEditText.redo();
                sizePost = noteEditText.getEditableText().length();
                try {
                    noteEditText.setSelection(cursorPosition + (sizePost - sizePre));
                } catch (Exception e) {
                    e.printStackTrace();
                }
//                closeVerse(versePreviewTextInEditMode);
                return true;
            case R.id.action_text_bold:
                noteEditText.bold(!noteEditText.contains(KnifeText.FORMAT_BOLD));
                return true;
            case R.id.action_text_italic:
                noteEditText.italic(!noteEditText.contains(KnifeText.FORMAT_ITALIC));
                return true;
            case R.id.action_text_underline:
                noteEditText.underline(!noteEditText.contains(KnifeText.FORMAT_UNDERLINED));
                return true;
            case R.id.action_text_format_clear:
                noteEditText.clearFormats();
                return true;
            case R.id.action_text_foreground_color_red:
                noteEditText.textColor(Color.RED, !noteEditText.contains(RichSelectableEditText.FORMAT_TEXT_COLOR_RED));
                return true;
            case R.id.action_text_foreground_color_blue:
                noteEditText.textColor(Color.BLUE, !noteEditText.contains(RichSelectableEditText.FORMAT_TEXT_COLOR_BLUE));
                return true;
            case R.id.action_text_foreground_color_green:
                noteEditText.textColor(Color.parseColor(COLOR_TEXT_GREEN), !noteEditText.contains(RichSelectableEditText.FORMAT_TEXT_COLOR_GREEN));
                return true;
            case R.id.action_text_background_color_yellow:
                noteEditText.markerColor(Color.parseColor(COLOR_MARKER_YELLOW), !noteEditText.contains(RichSelectableEditText.FORMAT_MARKER_COLOR_YELLOW));
                return true;
            case R.id.action_text_background_color_blue:
                noteEditText.markerColor(Color.parseColor(COLOR_MARKER_BLUE), !noteEditText.contains(RichSelectableEditText.FORMAT_MARKER_COLOR_BLUE));
                return true;
            case R.id.action_text_background_color_green:
                noteEditText.markerColor(Color.parseColor(COLOR_MARKER_GREEN), !noteEditText.contains(RichSelectableEditText.FORMAT_MARKER_COLOR_GREEN));
                return true;
            case R.id.action_text_bullet:
                noteEditText.bullet(!noteEditText.contains(KnifeText.FORMAT_BULLET));
                return true;
            case R.id.action_save:
                importDocument();
                return true;
            case R.id.action_settings:
                hideSoftKeyboard(this);
                Intent intentSettings = new Intent(this, SettingsActivity.class);
                startActivity(intentSettings);
                return true;
            case R.id.action_help:
                hideSoftKeyboard(this);
                Intent intent = new Intent(this, HelpActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_print:
                hideSoftKeyboard(this);
                printCurrentDocument();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setFontSizes() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activityContext);
        String verseFontSizeKey = activityContext.getResources().getString(R.string.verse_font_size_key);
        String noteFontSizeKey = activityContext.getResources().getString(R.string.note_font_size_key);
        String defaultVerseFontSize = activityContext.getResources().getString(R.string.font_size_small);
        String defaultNoteFontSize = activityContext.getResources().getString(R.string.font_size_medium);
        float verseFontSize = Float.valueOf(sharedPref.getString(verseFontSizeKey, defaultVerseFontSize));
        float noteFontSize = Float.valueOf(sharedPref.getString(noteFontSizeKey, defaultNoteFontSize));
        versePreviewTextInEditMode.setTextSize(verseFontSize);
        versePreviewTextInViewMode.setTextSize(verseFontSize);
        noteEditText.setTextSize(noteFontSize);
        noteTextView.setTextSize(noteFontSize);
    }

    private void setFontColors() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activityContext);
        String verseColorSizeKey = activityContext.getResources().getString(R.string.verse_font_color_key);
        int defaultVerseFontColor = ContextCompat.getColor(activityContext, R.color.default_text_color);
        int verseFontColor = sharedPref.getInt(verseColorSizeKey, defaultVerseFontColor);
        versePreviewTextInEditMode.setTextColor(verseFontColor);
        versePreviewTextInViewMode.setTextColor(verseFontColor);
        versePreviewTextInEditMode.setHintTextColor(verseFontColor);
        versePreviewTextInViewMode.setHintTextColor(verseFontColor);
    }

    private void openFileFromIntent() {
        Log.v(TAG, "openFileFromIntent - started");
        try {

            Intent intent = getIntent();
            String action = intent.getAction();
            String type = intent.getType();

            if ((action.equals(Intent.ACTION_EDIT) || action.equals(Intent.ACTION_VIEW))
                    && (type.equals("text/plain") || type.equals("text/html"))) {
                Uri uri = intent.getData();
                if (uri != null && (uri.getScheme().equals(ContentResolver.SCHEME_FILE) ||
                        uri.getScheme().equals(ContentResolver.SCHEME_ANDROID_RESOURCE) ||
                        uri.getScheme().equals(ContentResolver.SCHEME_CONTENT))) {
                    String content = readFile(uri);

                    noteEditText.fromHtml(content);
                    htmlTextInFile = SpanToHtmlConverter.toHtml(noteEditText.getEditableText());
                    Log.d(TAG, "htmlTextInFile: " + htmlTextInFile);

                    // below switch has to be here because of getting action from intent
                    switch (savedMode) {
                        case UNSET:
                            if (action.equals(Intent.ACTION_EDIT)) {
                                switchToMode(EDIT, false);
                            } else {
                                switchToMode(VIEW, false);
                            }
                            break;
                        case EDIT:
                            switchToMode(EDIT, false);
                            break;
                        case VIEW:
                            switchToMode(VIEW, true);
                            break;
                    }
                    Log.v(TAG, "openFileFromIntent - mode switched: " + currentMode);

                    if (uri.getScheme().equals(ContentResolver.SCHEME_FILE)) {
                        currentFile = new File(uri.getPath());
                        setTitle(FileManagerActivity.fileWithoutExtension(uri.getLastPathSegment()));
                    } else {
                        currentFile = null;
                        setTitle(R.string.temporary_file);
                    }
                    Log.v(TAG, "openFileFromIntent - finished");
                } else {
                    Toast.makeText(this, R.string.uri_not_supported, Toast.LENGTH_LONG).show();
                    finish();
                }
            } else {
                Toast.makeText(this, R.string.not_text_file, Toast.LENGTH_LONG).show();
                finish();
            }
        } catch (Exception unexpectedException) {
            // All exceptions should be caught before this catch...
            Toast.makeText(this, R.string.unexpected_exception, Toast.LENGTH_LONG).show();
        }
    }

    String readFile(Uri uri) {
        StringBuilder text = new StringBuilder();
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if ((is != null) && is.available() <= MAX_FILE_SIZE_IN_BYTES) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                String line;
                String lineEnding = "";
                while ((line = reader.readLine()) != null) {
                    text.append(lineEnding).append(line);
                    lineEnding = "<BR>";
                }
                is.close();
            } else {
                Toast.makeText(this, R.string.file_is_too_big, Toast.LENGTH_LONG).show();
                finish();
            }
        } catch (Exception exception) {
            if (exception.getCause().toString().contains("Permission denied")) {
                Log.v(TAG, exception.getCause().toString());
                FileManagerActivity.askForPermissionsIfNotGranted(this);
            } else {
                exception.printStackTrace();
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_LONG).show();
                finish();
            }
        }
        String content = text.toString();
        Log.d(TAG, "openFileFromIntent - content:\n" + content);
        return content;
    }

    /**
     * @param requestedMode choose target mode
     * @param setVerses     verses will be set in onResume(), so to not double executing setTextWithVerses()
     *                      (for example when file is opened from Intent), use setVerses = false
     */
    private void switchToMode(Mode requestedMode, boolean setVerses) {
        if (currentMode != requestedMode) {
            if (requestedMode.equals(VIEW)) {
                noteEditText.clearComposingText();
                hideSoftKeyboard(this);
                if (setVerses)
                    noteTextView.setTextWithVerses(noteEditText.getText(), versesLanguage);
            } else {
                noteEditText.showSoftInput();
            }
            viewFlipper.showNext();
            currentMode = requestedMode;
            invalidateOptionsMenu();
        }
    }

    private void saveDocumentIfChanged() {
        Log.d(TAG, "Start of saveDocumentIfChanged");
        noteEditText.clearComposingText(); // not sure why, but if we use this method during editing note, last word is doubled in the editor. So reverting such saving
        final String currentHtml = SpanToHtmlConverter.toHtml(noteEditText.getEditableText());
        if (currentFile != null && !currentHtml.equals(htmlTextInFile)) {
            noteEditText.correctBullets();
            saveStringToFile(SpanToHtmlConverter.toHtml(noteEditText.getEditableText()), currentFile);
//            Log.d(TAG, "saveStringToFile: " + SpanToHtmlConverter.toHtml(noteEditText.getEditableText())); // now this method is frequent, so avoid wasting time on conversions in logs
        }
        Log.d(TAG, "End of saveDocumentIfChanged");
    }

    private void printCurrentDocument() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                HtmlPrinter hp = new HtmlPrinter(activityContext);
                hp.print(SpanToHtmlConverter.toHtml(noteEditText.getEditableText()));
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(activityContext, R.string.unsuccessful, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(activityContext, R.string.available_from_android_4_4, Toast.LENGTH_SHORT).show();
        }
    }

    private void importDocument() {
        final File mainDir = FileManagerActivity.getMainDirectory();
        if (mainDir==null) {
            Toast.makeText(activityContext, R.string.main_dir_is_null, Toast.LENGTH_LONG).show();
            return;
        }
        final String html = SpanToHtmlConverter.toHtml(noteEditText.getEditableText());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.create_new_note_from_this_file_dialog_title);
        String message = activityContext.getResources().getString(R.string.file_will_be_created_in) +
                activityContext.getResources().getString(R.string.downloads_folder_name);
        builder.setMessage(message);

        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.requestFocus();
        input.setHint(R.string.note_name);
        builder.setView(input);

        builder.setPositiveButton(R.string.ok, null);
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Boolean isFileNameCorrect = true;
                String newFileName = input.getText().toString();
                File importsDirectory = new File(mainDir, activityContext.getResources().getString(R.string.downloads_folder_name));

                if (importsDirectory.mkdirs() || importsDirectory.isDirectory()) {
                    if (newFileName.equals("")) {
                        isFileNameCorrect = false;
                        Toast.makeText(activityContext, R.string.name_cannot_be_empty, Toast.LENGTH_SHORT).show();
                    } else if (newFileName.contains("/")) {
                        isFileNameCorrect = false;
                        Toast.makeText(activityContext, R.string.name_cannot_contain_slash, Toast.LENGTH_SHORT).show();
                    } else {
                        newFileName = newFileName + "." + FileManagerActivity.NOTE_FILE_EXTENSION;
                        File[] filesAndDirectoriesInDownloadsDirectory = importsDirectory.listFiles();
                        for (File file : filesAndDirectoriesInDownloadsDirectory) {
                            if (file.getName().equalsIgnoreCase(newFileName)) {
                                isFileNameCorrect = false;
                                Toast.makeText(activityContext, R.string.file_name_exists, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    if (isFileNameCorrect) {
                        currentFile = new File(importsDirectory, newFileName);
                        if (saveStringToFile(html, currentFile)) {
                            setTitle(FileManagerActivity.fileWithoutExtension(currentFile.getName()));
                            saveButton.setVisible(false);
                        } else {
                            currentFile = null;
                        }
                        dialog.dismiss();
                    }
                } else {
                    Toast.makeText(activityContext, R.string.storage_not_writable, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            }
        });
    }

    private boolean saveStringToFile(String htmlString, File file) {
        try {
            FileOutputStream stream = new FileOutputStream(file);
            try {
                stream.write(htmlString.getBytes());
            } finally {
                stream.close();
            }
            FileManagerActivity.getFilesDatabase().updateFile(file);
            MediaScannerConnection.scanFile(activityContext, new String[]{currentFile.getAbsolutePath()}, null, null);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.error_while_saving, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    // Used when user opens file from external storage selecting EditorNotepadJW for this action
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case Permissions.MY_REQUEST_PERMISSIONS_CODE: {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activityContext);
                SharedPreferences.Editor editor = prefs.edit();
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    editor.putInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.ACCEPTED);
                    editor.commit();
                    openFileFromIntent();
                } else {
                    Toast.makeText(this, R.string.permission_storage_not_granted, Toast.LENGTH_LONG).show();
                    editor.putInt(Permissions.WRITE_EXTERNAL_STORAGE, Permissions.DENIED);
                    editor.commit();
                    finish();
                }
            }
        }
    }

    enum Mode {EDIT, VIEW, UNSET}

    class StyleCallback implements ActionMode.Callback {
        private final String TAG = "StyleCallback";

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            Log.d(TAG, "onCreateActionMode");
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.menu_for_compatibility, menu);
            menu.findItem(R.id.action_text_bold).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.findItem(R.id.action_text_italic).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS); // it was SHOW_AS_ACTION_IF_ROOM previously
            menu.findItem(R.id.action_text_underline).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            if (menu.findItem(android.R.id.cut) != null) {
                menu.findItem(android.R.id.cut).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            if (menu.findItem(android.R.id.copy) != null) {
                menu.findItem(android.R.id.copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            if (menu.findItem(android.R.id.selectAll) != null) {
                menu.findItem(android.R.id.selectAll).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            }
            if (menu.findItem(android.R.id.paste) != null) {
                menu.findItem(android.R.id.paste).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            }
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            try {
                switch (item.getItemId()) {
                    case R.id.action_text_bold:
                        noteEditText.bold(!noteEditText.contains(KnifeText.FORMAT_BOLD));
                        return true;
                    case R.id.action_text_italic:
                        noteEditText.italic(!noteEditText.contains(KnifeText.FORMAT_ITALIC));
                        return true;
                    case R.id.action_text_underline:
                        noteEditText.underline(!noteEditText.contains(KnifeText.FORMAT_UNDERLINED));
                        return true;
                }
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), R.string.unexpected_exception, Toast.LENGTH_LONG).show();
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {

        }
    }
}
