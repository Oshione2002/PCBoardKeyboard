package com.treasure.pcboard;

import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.SystemClock;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;

public final class PcKeyboardService extends InputMethodService implements KeyboardSurface.Listener {
    private enum Panel { SUGGESTIONS, TOOLS, CLIPBOARD, EMOJI }

    private KeyboardPreferences preferences;
    private PersonalLexicon personalLexicon;
    private ClipboardRepository clipboardRepository;
    private SuggestionEngine suggestionEngine;

    private LinearLayout root;
    private LinearLayout topRow;
    private FrameLayout keyboardFrame;
    private KeyboardSurface keyboardSurface;

    private Panel panel = Panel.SUGGESTIONS;
    private KeyboardLayoutFactory.ShiftState shiftState = KeyboardLayoutFactory.ShiftState.OFF;
    private ProfileManager.Profile profile = ProfileManager.Profile.DEFAULT;
    private KeyboardLayoutFactory.InputKind inputKind = KeyboardLayoutFactory.InputKind.TEXT;
    private boolean symbols, ctrl, alt;
    private long lastShiftTap, lastSpaceTap;
    private EditorInfo editorInfo;
    private Correction lastCorrection;

    private static final class Correction {
        final String original, corrected;
        final long time;
        Correction(String original, String corrected) {
            this.original = original; this.corrected = corrected; this.time = SystemClock.uptimeMillis();
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        preferences = new KeyboardPreferences(this);
        personalLexicon = new PersonalLexicon(this);
        clipboardRepository = new ClipboardRepository(this);
        suggestionEngine = new SuggestionEngine(this);
    }

    @Override public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        HorizontalScrollView topScroll = new HorizontalScrollView(this);
        topScroll.setHorizontalScrollBarEnabled(false);
        topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(dp(3), dp(3), dp(3), dp(3));
        topScroll.addView(topRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(50)));
        root.addView(topScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        keyboardFrame = new FrameLayout(this);
        keyboardSurface = new KeyboardSurface(this);
        keyboardSurface.configure(this, preferences.keyPopup(), preferences.haptic(), preferences.sound(), preferences.longPressDelay());
        keyboardFrame.addView(keyboardSurface);
        root.addView(keyboardFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rebuildAll();
        return root;
    }

    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        editorInfo = attribute;
        inputKind = KeyboardLayoutFactory.inputKind(attribute);
        if (preferences.autoProfile()) profile = ProfileManager.detect(attribute == null ? null : attribute.packageName);
        symbols = false; ctrl = false; alt = false; panel = Panel.SUGGESTIONS; lastCorrection = null;
        shiftState = preferences.autoCap() && shouldAutoCap() ? KeyboardLayoutFactory.ShiftState.ON : KeyboardLayoutFactory.ShiftState.OFF;
        rebuildAll();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        rebuildAll();
    }

    @Override public void onWindowHidden() {
        ctrl = false; alt = false; lastCorrection = null;
        super.onWindowHidden();
    }

    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        if (panel == Panel.SUGGESTIONS) renderTopPanel();
    }

    @Override public void onKey(KeySpec key) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        switch (key.action) {
            case TEXT: commitTextKey(connection, key.output); break;
            case SHIFT: toggleShift(); break;
            case CTRL: ctrl = !ctrl; rebuildKeyboard(); break;
            case ALT: alt = !alt; rebuildKeyboard(); break;
            case SYMBOLS: symbols = !symbols; rebuildKeyboard(); break;
            case BACKSPACE: handleBackspace(connection); break;
            case FORWARD_DELETE: connection.deleteSurroundingTextInCodePoints(0, 1); break;
            case SPACE: handleSpace(connection); break;
            case ENTER: handleEnter(connection); break;
            case TAB: sendKey(connection, KeyEvent.KEYCODE_TAB, activeMeta()); clearOneShotModifiers(); break;
            case ESCAPE: sendKey(connection, KeyEvent.KEYCODE_ESCAPE, activeMeta()); clearOneShotModifiers(); break;
            case LEFT: sendKey(connection, KeyEvent.KEYCODE_DPAD_LEFT, activeMeta()); break;
            case RIGHT: sendKey(connection, KeyEvent.KEYCODE_DPAD_RIGHT, activeMeta()); break;
            case UP: sendKey(connection, KeyEvent.KEYCODE_DPAD_UP, activeMeta()); break;
            case DOWN: sendKey(connection, KeyEvent.KEYCODE_DPAD_DOWN, activeMeta()); break;
            case COPY: contextAction(connection, android.R.id.copy, KeyEvent.KEYCODE_C); break;
            case CUT: contextAction(connection, android.R.id.cut, KeyEvent.KEYCODE_X); break;
            case PASTE: contextAction(connection, android.R.id.paste, KeyEvent.KEYCODE_V); break;
            case SELECT_ALL: contextAction(connection, android.R.id.selectAll, KeyEvent.KEYCODE_A); break;
            case UNDO: contextAction(connection, android.R.id.undo, KeyEvent.KEYCODE_Z); break;
            case REDO: contextAction(connection, android.R.id.redo, KeyEvent.KEYCODE_Y); break;
            case CLIPBOARD: panel = Panel.CLIPBOARD; renderTopPanel(); break;
            case TOOLS: panel = Panel.TOOLS; renderTopPanel(); break;
            case EMOJI: panel = Panel.EMOJI; renderTopPanel(); break;
            case PROFILE: profile = ProfileManager.next(profile); rebuildAll(); break;
            case SETTINGS: openSettings(); break;
            case HIDE: requestHideSelf(0); break;
        }
        if (panel == Panel.SUGGESTIONS) renderTopPanel();
    }

    @Override public void onAlternate(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.commitText(text, 1);
        consumeShift();
        renderTopPanel();
    }

    @Override public void onSpaceCursor(int direction) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) sendKey(connection, direction > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT, ctrl ? KeyEvent.META_CTRL_ON : 0);
    }

    @Override public void onDeleteWord() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        String before = beforeCursor(120);
        if (before.isEmpty()) return;
        int index = before.length() - 1;
        while (index >= 0 && Character.isWhitespace(before.charAt(index))) index--;
        while (index >= 0 && isWordCharacter(before.charAt(index))) index--;
        int count = before.length() - 1 - index;
        if (count > 0) connection.deleteSurroundingText(count, 0);
        lastCorrection = null;
        renderTopPanel();
    }

    @Override public void onHide() { requestHideSelf(0); }

    private void commitTextKey(InputConnection connection, String output) {
        if (output == null) return;
        if ((ctrl || alt) && output.codePointCount(0, output.length()) == 1) {
            int codePoint = output.toUpperCase(Locale.ROOT).codePointAt(0);
            int keyCode = KeyEvent.keyCodeFromString("KEYCODE_" + new String(Character.toChars(codePoint)));
            if (keyCode != KeyEvent.KEYCODE_UNKNOWN) sendKey(connection, keyCode, activeMeta());
            clearOneShotModifiers();
            return;
        }
        String value = shiftState == KeyboardLayoutFactory.ShiftState.OFF ? output : output.toUpperCase(Locale.ROOT);
        connection.commitText(value, 1);
        lastCorrection = null;
        consumeShift();
        renderTopPanel();
    }

    private void toggleShift() {
        long now = SystemClock.uptimeMillis();
        if (now - lastShiftTap < 360) shiftState = KeyboardLayoutFactory.ShiftState.LOCKED;
        else if (shiftState == KeyboardLayoutFactory.ShiftState.OFF) shiftState = KeyboardLayoutFactory.ShiftState.ON;
        else shiftState = KeyboardLayoutFactory.ShiftState.OFF;
        lastShiftTap = now;
        rebuildKeyboard();
    }

    private void consumeShift() {
        if (shiftState == KeyboardLayoutFactory.ShiftState.ON) {
            shiftState = KeyboardLayoutFactory.ShiftState.OFF;
            rebuildKeyboard();
        }
    }

    private void clearOneShotModifiers() {
        ctrl = false; alt = false; rebuildKeyboard();
    }

    private void handleBackspace(InputConnection connection) {
        if (tryUndoCorrection(connection)) return;
        connection.deleteSurroundingTextInCodePoints(1, 0);
        lastCorrection = null;
        if (preferences.autoCap() && shouldAutoCap() && shiftState == KeyboardLayoutFactory.ShiftState.OFF) {
            shiftState = KeyboardLayoutFactory.ShiftState.ON;
            rebuildKeyboard();
        }
        renderTopPanel();
    }

    private boolean tryUndoCorrection(InputConnection connection) {
        if (lastCorrection == null || SystemClock.uptimeMillis() - lastCorrection.time > 6000) return false;
        String before = beforeCursor(lastCorrection.corrected.length() + 2);
        String expected = lastCorrection.corrected + " ";
        if (!before.endsWith(expected)) return false;
        connection.deleteSurroundingText(expected.length(), 0);
        connection.commitText(lastCorrection.original, 1);
        lastCorrection = null;
        renderTopPanel();
        return true;
    }

    private void handleSpace(InputConnection connection) {
        long now = SystemClock.uptimeMillis();
        String before = beforeCursor(160);
        if (preferences.doubleSpacePeriod() && now - lastSpaceTap < 520 && before.endsWith(" ") && before.length() > 1) {
            char previous = before.charAt(before.length() - 2);
            if (Character.isLetterOrDigit(previous)) {
                connection.deleteSurroundingText(1, 0);
                connection.commitText(". ", 1);
                lastSpaceTap = 0;
                if (preferences.autoCap()) shiftState = KeyboardLayoutFactory.ShiftState.ON;
                rebuildKeyboard(); renderTopPanel(); return;
            }
        }

        String original = currentWord();
        String previous = previousWord();
        String finalWord = original;
        if (preferences.autocorrect() && !isPrivateMode() && !original.isEmpty()) {
            String corrected = suggestionEngine.bestCorrection(original, previous, personalLexicon, profile, preferences.dialect());
            if (!corrected.equalsIgnoreCase(original)) {
                connection.deleteSurroundingText(original.length(), 0);
                connection.commitText(matchCase(original, corrected), 1);
                finalWord = corrected;
                lastCorrection = new Correction(original, matchCase(original, corrected));
            }
        }
        if (!isPrivateMode() && !finalWord.isEmpty()) personalLexicon.learn(finalWord, previous);
        connection.commitText(" ", 1);
        lastSpaceTap = now;
        if (preferences.autoCap() && endsSentence(before)) shiftState = KeyboardLayoutFactory.ShiftState.ON;
        else if (shiftState == KeyboardLayoutFactory.ShiftState.ON) shiftState = KeyboardLayoutFactory.ShiftState.OFF;
        rebuildKeyboard(); renderTopPanel();
    }

    private void handleEnter(InputConnection connection) {
        int options = editorInfo == null ? EditorInfo.IME_ACTION_NONE : editorInfo.imeOptions;
        int action = options & EditorInfo.IME_MASK_ACTION;
        boolean noAction = (options & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            if (!connection.performEditorAction(action)) sendKey(connection, KeyEvent.KEYCODE_ENTER, activeMeta());
        } else sendKey(connection, KeyEvent.KEYCODE_ENTER, activeMeta());
        clearOneShotModifiers();
        if (preferences.autoCap()) shiftState = KeyboardLayoutFactory.ShiftState.ON;
        rebuildKeyboard(); renderTopPanel();
    }

    private void contextAction(InputConnection connection, int actionId, int fallbackKey) {
        if (!connection.performContextMenuAction(actionId)) sendKey(connection, fallbackKey, KeyEvent.META_CTRL_ON);
        clearOneShotModifiers();
    }

    private void replaceCurrentWord(String suggestion) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        String current = currentWord();
        if (!current.isEmpty()) connection.deleteSurroundingText(current.length(), 0);
        connection.commitText(matchCase(current, suggestion) + " ", 1);
        if (!isPrivateMode()) personalLexicon.learn(suggestion, previousWord());
        lastCorrection = null;
        renderTopPanel();
    }

    private void rebuildAll() {
        if (root == null) return;
        applyPalette();
        keyboardSurface.configure(this, preferences.keyPopup(), preferences.haptic(), preferences.sound(), preferences.longPressDelay());
        rebuildKeyboard();
        renderTopPanel();
    }

    private void rebuildKeyboard() {
        if (keyboardSurface == null) return;
        Set<KeySpec.Action> active = EnumSet.noneOf(KeySpec.Action.class);
        if (ctrl) active.add(KeySpec.Action.CTRL);
        if (alt) active.add(KeySpec.Action.ALT);
        if (shiftState != KeyboardLayoutFactory.ShiftState.OFF) active.add(KeySpec.Action.SHIFT);
        keyboardSurface.render(
                KeyboardLayoutFactory.build(symbols, shiftState, preferences.numberRow(), profile, inputKind),
                palette(), active, preferences.heightPercent());
        applyWidthMode();
    }

    private void renderTopPanel() {
        if (topRow == null) return;
        topRow.removeAllViews();
        addTopButton("▣", "Clipboard", () -> { panel = panel == Panel.CLIPBOARD ? Panel.SUGGESTIONS : Panel.CLIPBOARD; renderTopPanel(); });
        addTopButton("↔", "Editing tools", () -> { panel = panel == Panel.TOOLS ? Panel.SUGGESTIONS : Panel.TOOLS; renderTopPanel(); });
        addTopButton("☺", "Emoji", () -> { panel = panel == Panel.EMOJI ? Panel.SUGGESTIONS : Panel.EMOJI; renderTopPanel(); });
        addTopButton(ProfileManager.shortLabel(profile), "Change profile", () -> { profile = ProfileManager.next(profile); panel = Panel.SUGGESTIONS; rebuildAll(); });

        switch (panel) {
            case TOOLS: renderTools(); break;
            case CLIPBOARD: renderClipboard(); break;
            case EMOJI: renderEmoji(); break;
            default: renderSuggestions(); break;
        }
    }

    private void renderSuggestions() {
        if (!preferences.predictions() || isPrivateMode()) {
            addTopLabel(isPrivateMode() ? "Private typing" : "Suggestions off");
            addTopButton("⚙", "Keyboard settings", this::openSettings);
            return;
        }
        String current = currentWord();
        String previous = previousWord();
        List<String> suggestions = suggestionEngine.suggest(current, previous, personalLexicon, profile, preferences.dialect(), 3);
        if (preferences.emojiSuggestions()) {
            List<String> emoji = suggestionEngine.emojiForWord(current.isEmpty() ? previous : current);
            if (!emoji.isEmpty()) suggestions.set(Math.min(2, suggestions.size() - 1), emoji.get(0));
        }
        for (String suggestion : suggestions) {
            addTopButton(suggestion, "Suggestion " + suggestion, () -> {
                if (suggestion.codePointAt(0) > 0x1F000 || suggestion.length() <= 3 && !suggestion.matches("[A-Za-z']+")) {
                    InputConnection connection = getCurrentInputConnection();
                    if (connection != null) connection.commitText(suggestion, 1);
                } else replaceCurrentWord(suggestion);
            });
        }
        addTopButton("⚙", "Keyboard settings", this::openSettings);
    }

    private void renderTools() {
        addActionButton("Undo", KeySpec.Action.UNDO); addActionButton("Redo", KeySpec.Action.REDO);
        addActionButton("Cut", KeySpec.Action.CUT); addActionButton("Copy", KeySpec.Action.COPY); addActionButton("Paste", KeySpec.Action.PASTE);
        addActionButton("All", KeySpec.Action.SELECT_ALL);
        addActionButton("←", KeySpec.Action.LEFT); addActionButton("↑", KeySpec.Action.UP); addActionButton("↓", KeySpec.Action.DOWN); addActionButton("→", KeySpec.Action.RIGHT);
        addActionButton("Del", KeySpec.Action.FORWARD_DELETE); addActionButton("Esc", KeySpec.Action.ESCAPE);
        addTopButton("⚙", "Keyboard settings", this::openSettings);
    }

    private void renderClipboard() {
        List<ClipboardRepository.Clip> clips = clipboardRepository.captureCurrent(preferences.clipboardHistory() && !isPrivateMode());
        if (clips.isEmpty()) addTopLabel("Clipboard is empty");
        for (ClipboardRepository.Clip clip : clips) {
            String label = clip.text.replace('\n', ' ');
            if (label.length() > 24) label = label.substring(0, 24) + "…";
            String finalLabel = label;
            addTopButton(label, "Paste " + label, () -> {
                InputConnection connection = getCurrentInputConnection();
                if (connection != null) connection.commitText(clip.text, 1);
                panel = Panel.SUGGESTIONS; renderTopPanel();
            });
        }
        addTopButton("Clear", "Clear clipboard history", () -> { clipboardRepository.clearUnpinned(); renderTopPanel(); });
    }

    private void renderEmoji() {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(suggestionEngine.emojiForWord(currentWord()));
        Collections.addAll(values, "😀","😂","🥰","😍","😊","🙏","👍","❤️","🔥","🎉","✅","💯","🤔","😢","😡","👏","🤝","🚀");
        for (String value : values) addTopButton(value, "Emoji " + value, () -> {
            InputConnection connection = getCurrentInputConnection();
            if (connection != null) connection.commitText(value, 1);
        });
    }

    private void addActionButton(String label, KeySpec.Action action) {
        addTopButton(label, label, () -> onKey(KeySpec.action(label, action, 1f, false, label)));
    }

    private void addTopButton(String label, String description, Runnable action) {
        TextView button = new TextView(this);
        button.setText(label); button.setTextSize(label.length() > 9 ? 14 : 17); button.setGravity(Gravity.CENTER);
        button.setTextColor(palette().text); button.setContentDescription(description); button.setFocusable(true); button.setClickable(true);
        button.setPadding(dp(12), 0, dp(12), 0); button.setMinWidth(dp(48)); button.setMinHeight(dp(44));
        GradientDrawable background = new GradientDrawable(); background.setColor(palette().special); background.setCornerRadius(dp(18));
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)); params.setMargins(dp(3), 0, dp(3), 0);
        topRow.addView(button, params); button.setOnClickListener(v -> action.run());
    }

    private void addTopLabel(String text) {
        TextView label = new TextView(this); label.setText(text); label.setTextSize(15); label.setTextColor(palette().text); label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(14), 0, dp(14), 0); topRow.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
    }

    private void applyPalette() {
        KeyboardSurface.Palette p = palette();
        root.setBackgroundColor(p.background); topRow.setBackgroundColor(p.background);
        if (Build.VERSION.SDK_INT >= 21 && getWindow() != null && getWindow().getWindow() != null) getWindow().getWindow().setNavigationBarColor(p.background);
    }

    private KeyboardSurface.Palette palette() {
        String theme = preferences == null ? "system" : preferences.theme();
        boolean systemDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean light = "light".equals(theme) || ("system".equals(theme) && !systemDark);
        if ("amoled".equals(theme)) return new KeyboardSurface.Palette(Color.BLACK, Color.rgb(28,28,28), Color.rgb(70,70,70), Color.rgb(40,40,40), Color.rgb(63,95,210), Color.WHITE);
        if (light) return new KeyboardSurface.Palette(Color.rgb(232,235,240), Color.WHITE, Color.rgb(210,215,224), Color.rgb(210,215,224), Color.rgb(73,107,220), Color.rgb(25,28,34));
        return new KeyboardSurface.Palette(Color.rgb(24,26,31), Color.rgb(53,56,64), Color.rgb(80,84,94), Color.rgb(41,44,51), Color.rgb(76,112,220), Color.WHITE);
    }

    private void applyWidthMode() {
        if (keyboardFrame == null || keyboardSurface == null) return;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        String mode = preferences.oneHanded();
        if ("left".equals(mode)) { params.width = Math.round(getResources().getDisplayMetrics().widthPixels * .84f); params.gravity = Gravity.START; }
        else if ("right".equals(mode)) { params.width = Math.round(getResources().getDisplayMetrics().widthPixels * .84f); params.gravity = Gravity.END; }
        else if ("compact".equals(mode)) { params.width = Math.round(getResources().getDisplayMetrics().widthPixels * .76f); params.gravity = Gravity.CENTER_HORIZONTAL; }
        keyboardSurface.setLayoutParams(params);
    }

    private boolean isPrivateMode() {
        if (preferences.incognito() || inputKind == KeyboardLayoutFactory.InputKind.PASSWORD) return true;
        return editorInfo != null && (editorInfo.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
    }

    private boolean shouldAutoCap() {
        if (inputKind == KeyboardLayoutFactory.InputKind.EMAIL || inputKind == KeyboardLayoutFactory.InputKind.URL || inputKind == KeyboardLayoutFactory.InputKind.PASSWORD) return false;
        String before = beforeCursor(80).trim();
        return before.isEmpty() || endsSentence(before);
    }

    private static boolean endsSentence(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return false;
        char last = trimmed.charAt(trimmed.length() - 1);
        return last == '.' || last == '!' || last == '?' || last == '\n';
    }

    private String currentWord() {
        String before = beforeCursor(100);
        int end = before.length(), start = end;
        while (start > 0 && isWordCharacter(before.charAt(start - 1))) start--;
        return before.substring(start, end);
    }

    private String previousWord() {
        String before = beforeCursor(180);
        int end = before.length();
        while (end > 0 && isWordCharacter(before.charAt(end - 1))) end--;
        while (end > 0 && !isWordCharacter(before.charAt(end - 1))) end--;
        int start = end;
        while (start > 0 && isWordCharacter(before.charAt(start - 1))) start--;
        return before.substring(start, end);
    }

    private String beforeCursor(int length) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return "";
        CharSequence before = connection.getTextBeforeCursor(length, 0);
        return before == null ? "" : before.toString();
    }

    private int activeMeta() {
        int meta = 0;
        if (ctrl) meta |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        if (alt) meta |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        if (shiftState != KeyboardLayoutFactory.ShiftState.OFF) meta |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        return meta;
    }

    private static void sendKey(InputConnection connection, int keyCode, int meta) {
        long time = SystemClock.uptimeMillis();
        connection.sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0, meta));
        connection.sendKeyEvent(new KeyEvent(time, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0, meta));
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private static String matchCase(String original, String replacement) {
        if (original == null || original.isEmpty()) return replacement;
        if (original.equals(original.toUpperCase(Locale.ROOT))) return replacement.toUpperCase(Locale.ROOT);
        if (Character.isUpperCase(original.charAt(0))) return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
        return replacement;
    }

    private static boolean isWordCharacter(char c) {
        return Character.isLetter(c) || c == '\'' || c == '’' || c == '-';
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
