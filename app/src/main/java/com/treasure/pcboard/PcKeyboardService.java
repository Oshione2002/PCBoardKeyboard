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

public final class PcKeyboardService extends InputMethodService implements
        KeyboardSurface.Listener, VoiceTypingController.Listener,
        SystemTranslationController.Listener, HandwritingPopupController.Listener {

    private enum Panel { SUGGESTIONS, TOOLS, CLIPBOARD, EMOJI, TRANSLATION, SNIPPETS }

    private KeyboardPreferences preferences;
    private PersonalLexicon personalLexicon;
    private ClipboardRepository clipboardRepository;
    private SuggestionEngine suggestionEngine;
    private CustomLayoutStore customLayoutStore;
    private CustomLayoutStore.Config layoutConfig;
    private RichContentHelper richContentHelper;
    private VoiceTypingController voiceController;
    private SystemTranslationController translationController;
    private HandwritingPopupController handwritingController;

    private LinearLayout root, topRow;
    private FrameLayout keyboardFrame;
    private KeyboardSurface keyboardSurface;
    private Panel panel = Panel.SUGGESTIONS;
    private KeyboardLayoutFactory.ShiftState shiftState = KeyboardLayoutFactory.ShiftState.OFF;
    private ProfileManager.Profile profile = ProfileManager.Profile.DEFAULT;
    private KeyboardLayoutFactory.InputKind inputKind = KeyboardLayoutFactory.InputKind.TEXT;
    private boolean symbols, ctrl, alt, voiceComposing;
    private long lastShiftTap, lastSpaceTap;
    private EditorInfo editorInfo;
    private Correction lastCorrection;
    private List<String> glideAlternatives = Collections.emptyList();
    private String voiceStatus = "", translationStatus = "", translationResult = "";

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
        customLayoutStore = new CustomLayoutStore(this);
        layoutConfig = customLayoutStore.load();
        richContentHelper = new RichContentHelper(this);
        voiceController = new VoiceTypingController(this, this);
        translationController = new SystemTranslationController(this, this);
        handwritingController = new HandwritingPopupController(this, new HandwritingStore(this), this);
    }

    @Override public void onDestroy() {
        if (voiceController != null) voiceController.destroy();
        if (translationController != null) translationController.destroy();
        if (handwritingController != null) handwritingController.dismiss();
        super.onDestroy();
    }

    @Override public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false); root.setClipToPadding(false);
        applyBottomSafetyGap(null);
        root.setOnApplyWindowInsetsListener((view, insets) -> { applyBottomSafetyGap(insets); return insets; });

        HorizontalScrollView topScroll = new HorizontalScrollView(this);
        topScroll.setHorizontalScrollBarEnabled(false);
        topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL); topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(dp(3), dp(3), dp(3), dp(3));
        topScroll.addView(topRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));
        root.addView(topScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        keyboardFrame = new FrameLayout(this);
        keyboardSurface = new KeyboardSurface(this);
        keyboardFrame.addView(keyboardSurface);
        root.addView(keyboardFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rebuildAll(); root.post(root::requestApplyInsets); return root;
    }

    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        editorInfo = attribute;
        inputKind = KeyboardLayoutFactory.inputKind(attribute);
        if (preferences.autoProfile()) profile = ProfileManager.detect(attribute == null ? null : attribute.packageName);
        symbols = ctrl = alt = false; panel = Panel.SUGGESTIONS; lastCorrection = null;
        glideAlternatives = Collections.emptyList(); translationStatus = translationResult = "";
        stopVoiceComposing();
        if (voiceController.isListening()) voiceController.stop();
        layoutConfig = customLayoutStore.load();
        shiftState = preferences.autoCap() && shouldAutoCap() ? KeyboardLayoutFactory.ShiftState.ON : KeyboardLayoutFactory.ShiftState.OFF;
        rebuildAll();
    }

    @Override public void onFinishInput() {
        stopVoiceComposing();
        if (voiceController.isListening()) voiceController.stop();
        super.onFinishInput();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) { super.onConfigurationChanged(newConfig); rebuildAll(); }

    @Override public void onWindowHidden() {
        ctrl = alt = false; lastCorrection = null;
        if (voiceController.isListening()) voiceController.stop();
        handwritingController.dismiss();
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
        glideAlternatives = Collections.emptyList();
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
        glideAlternatives = Collections.emptyList(); consumeShift(); renderTopPanel();
    }

    @Override public void onGlide(List<String> path) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || path == null || path.size() < 2) return;
        List<String> candidates = suggestionEngine.glideCandidates(path, personalLexicon, profile, preferences.dialect(), 4);
        if (candidates.isEmpty()) return;
        String best = candidates.get(0);
        String value = shiftState == KeyboardLayoutFactory.ShiftState.OFF ? best : matchCase("A", best);
        connection.commitText(value + " ", 1);
        if (!isPrivateMode()) personalLexicon.learn(best, previousWord());
        glideAlternatives = candidates; lastCorrection = null; consumeShift(); renderTopPanel();
    }

    @Override public void onSpaceCursor(int direction) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) sendKey(connection, direction > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT, ctrl ? KeyEvent.META_CTRL_ON : 0);
    }

    @Override public void onDeleteWord() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        String before = beforeCursor(120); if (before.isEmpty()) return;
        int index = before.length() - 1;
        while (index >= 0 && Character.isWhitespace(before.charAt(index))) index--;
        while (index >= 0 && isWordCharacter(before.charAt(index))) index--;
        int count = before.length() - 1 - index;
        if (count > 0) connection.deleteSurroundingText(count, 0);
        lastCorrection = null; glideAlternatives = Collections.emptyList(); renderTopPanel();
    }

    @Override public void onHide() { requestHideSelf(0); }

    @Override public void onVoiceState(String state, boolean listening) { voiceStatus = state == null ? "" : state; renderTopPanel(); }

    @Override public void onVoiceText(String text, boolean isFinal) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || text == null || text.trim().isEmpty()) return;
        String value = text.trim();
        if (isFinal) {
            connection.setComposingText(value + " ", 1); connection.finishComposingText(); voiceComposing = false;
            if (!isPrivateMode()) {
                String previous = previousWord();
                for (String word : value.split("\\s+")) { personalLexicon.learn(word, previous); previous = word; }
            }
        } else { connection.setComposingText(value, 1); voiceComposing = true; }
        renderTopPanel();
    }

    @Override public void onVoiceError(String message) {
        voiceStatus = message == null ? "Voice typing error" : message;
        Toast.makeText(this, voiceStatus, Toast.LENGTH_LONG).show();
        stopVoiceComposing(); renderTopPanel();
    }

    @Override public void onTranslationState(String message) {
        translationStatus = message == null ? "" : message; panel = Panel.TRANSLATION; renderTopPanel();
    }

    @Override public void onTranslationResult(String original, String translated) {
        translationResult = translated == null ? "" : translated;
        translationStatus = "Translation ready"; panel = Panel.TRANSLATION; renderTopPanel();
    }

    @Override public void onTranslationError(String message) {
        translationStatus = message == null ? "Translation failed" : message;
        translationResult = ""; panel = Panel.TRANSLATION; renderTopPanel();
        Toast.makeText(this, translationStatus, Toast.LENGTH_LONG).show();
    }

    @Override public void onHandwritingText(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null && text != null) connection.commitText(text, 1);
        renderTopPanel();
    }

    @Override public void onHandwritingStatus(String status) {
        if (status != null && !status.isEmpty()) Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
    }

    private void commitTextKey(InputConnection connection, String output) {
        if (output == null) return;
        if ((ctrl || alt) && output.codePointCount(0, output.length()) == 1) {
            int codePoint = output.toUpperCase(Locale.ROOT).codePointAt(0);
            int keyCode = KeyEvent.keyCodeFromString("KEYCODE_" + new String(Character.toChars(codePoint)));
            if (keyCode != KeyEvent.KEYCODE_UNKNOWN) sendKey(connection, keyCode, activeMeta());
            clearOneShotModifiers(); return;
        }
        String value = shiftState == KeyboardLayoutFactory.ShiftState.OFF ? output : output.toUpperCase(Locale.ROOT);
        connection.commitText(value, 1); lastCorrection = null; consumeShift(); renderTopPanel();
    }

    private void toggleShift() {
        long now = SystemClock.uptimeMillis();
        if (now - lastShiftTap < 360) shiftState = KeyboardLayoutFactory.ShiftState.LOCKED;
        else if (shiftState == KeyboardLayoutFactory.ShiftState.OFF) shiftState = KeyboardLayoutFactory.ShiftState.ON;
        else shiftState = KeyboardLayoutFactory.ShiftState.OFF;
        lastShiftTap = now; rebuildKeyboard();
    }

    private void consumeShift() {
        if (shiftState == KeyboardLayoutFactory.ShiftState.ON) { shiftState = KeyboardLayoutFactory.ShiftState.OFF; rebuildKeyboard(); }
    }

    private void clearOneShotModifiers() { ctrl = alt = false; rebuildKeyboard(); }

    private void handleBackspace(InputConnection connection) {
        if (tryUndoCorrection(connection)) return;
        connection.deleteSurroundingTextInCodePoints(1, 0);
        lastCorrection = null; glideAlternatives = Collections.emptyList();
        if (preferences.autoCap() && shouldAutoCap() && shiftState == KeyboardLayoutFactory.ShiftState.OFF) {
            shiftState = KeyboardLayoutFactory.ShiftState.ON; rebuildKeyboard();
        }
        renderTopPanel();
    }

    private boolean tryUndoCorrection(InputConnection connection) {
        if (lastCorrection == null || SystemClock.uptimeMillis() - lastCorrection.time > 6000) return false;
        String expected = lastCorrection.corrected + " ";
        if (!beforeCursor(expected.length() + 1).endsWith(expected)) return false;
        connection.deleteSurroundingText(expected.length(), 0);
        connection.commitText(lastCorrection.original, 1);
        lastCorrection = null; renderTopPanel(); return true;
    }

    private void handleSpace(InputConnection connection) {
        long now = SystemClock.uptimeMillis();
        String before = beforeCursor(160);
        if (preferences.doubleSpacePeriod() && now - lastSpaceTap < 520 && before.endsWith(" ") && before.length() > 1) {
            char previous = before.charAt(before.length() - 2);
            if (Character.isLetterOrDigit(previous)) {
                connection.deleteSurroundingText(1, 0); connection.commitText(". ", 1); lastSpaceTap = 0;
                if (preferences.autoCap()) shiftState = KeyboardLayoutFactory.ShiftState.ON;
                rebuildKeyboard(); renderTopPanel(); return;
            }
        }
        String original = currentWord(), previous = previousWord(), finalWord = original;
        if (preferences.autocorrect() && !isPrivateMode() && !original.isEmpty()) {
            String corrected = suggestionEngine.bestCorrection(original, previous, personalLexicon, profile, preferences.dialect());
            if (!corrected.equalsIgnoreCase(original)) {
                connection.deleteSurroundingText(original.length(), 0);
                String matched = matchCase(original, corrected);
                connection.commitText(matched, 1); finalWord = corrected; lastCorrection = new Correction(original, matched);
            }
        }
        if (!isPrivateMode() && !finalWord.isEmpty()) personalLexicon.learn(finalWord, previous);
        connection.commitText(" ", 1); lastSpaceTap = now; glideAlternatives = Collections.emptyList();
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
        InputConnection connection = getCurrentInputConnection(); if (connection == null) return;
        String current = currentWord();
        if (!current.isEmpty()) connection.deleteSurroundingText(current.length(), 0);
        connection.commitText(matchCase(current, suggestion) + " ", 1);
        if (!isPrivateMode()) personalLexicon.learn(suggestion, previousWord());
        lastCorrection = null; glideAlternatives = Collections.emptyList(); renderTopPanel();
    }

    private void rebuildAll() {
        if (root == null) return;
        layoutConfig = customLayoutStore.load(); applyPalette(); applyBottomSafetyGap(null);
        boolean glideAvailable = preferences.glideTyping() && !symbols
                && inputKind == KeyboardLayoutFactory.InputKind.TEXT
                && profile == ProfileManager.Profile.DEFAULT && !isPrivateMode();
        keyboardSurface.configure(this, preferences.keyPopup(), preferences.haptic(), preferences.sound(),
                preferences.longPressDelay(), preferences.symbolHints(), glideAvailable);
        rebuildKeyboard(); renderTopPanel();
    }

    private void rebuildKeyboard() {
        if (keyboardSurface == null) return;
        Set<KeySpec.Action> active = EnumSet.noneOf(KeySpec.Action.class);
        if (ctrl) active.add(KeySpec.Action.CTRL);
        if (alt) active.add(KeySpec.Action.ALT);
        if (shiftState != KeyboardLayoutFactory.ShiftState.OFF) active.add(KeySpec.Action.SHIFT);
        keyboardSurface.render(
                KeyboardLayoutFactory.build(symbols, shiftState, preferences.numberRow(), profile, inputKind,
                        layoutConfig, preferences.longPressSymbols()),
                palette(), active, preferences.heightPercent());
        applyWidthMode();
    }

    private void renderTopPanel() {
        if (topRow == null) return;
        topRow.removeAllViews();
        for (String action : layoutConfig.toolbarActions) renderToolbarAction(action);
        switch (panel) {
            case TOOLS: renderTools(); break;
            case CLIPBOARD: renderClipboard(); break;
            case EMOJI: renderRichContent(); break;
            case TRANSLATION: renderTranslation(); break;
            case SNIPPETS: renderSnippets(); break;
            default: renderSuggestions(); break;
        }
    }

    private void renderToolbarAction(String action) {
        switch (action) {
            case "clipboard": addTopButton("▣", "Clipboard", () -> togglePanel(Panel.CLIPBOARD)); break;
            case "tools": addTopButton("↔", "Editing tools", () -> togglePanel(Panel.TOOLS)); break;
            case "emoji": addTopButton("☺", "Emoji and rich content", () -> togglePanel(Panel.EMOJI)); break;
            case "voice": addTopButton(voiceController.isListening() ? "■" : "🎙", voiceController.isListening() ? "Stop voice typing" : "Voice typing", this::toggleVoiceTyping); break;
            case "translate": addTopButton("文", "Translate selected text", this::startTranslation); break;
            case "handwriting": addTopButton("✍", "Handwriting", this::showHandwriting); break;
            case "snippets": addTopButton("⌘", "Quick text snippets", () -> togglePanel(Panel.SNIPPETS)); break;
            case "profile": addTopButton(ProfileManager.shortLabel(profile), "Change profile", () -> { profile = ProfileManager.next(profile); panel = Panel.SUGGESTIONS; rebuildAll(); }); break;
            case "settings": addTopButton("⚙", "Keyboard settings", this::openSettings); break;
            case "hide": addTopButton("⌄", "Hide keyboard", () -> requestHideSelf(0)); break;
        }
    }

    private void togglePanel(Panel target) { panel = panel == target ? Panel.SUGGESTIONS : target; renderTopPanel(); }

    private void renderSuggestions() {
        if (!voiceStatus.isEmpty() && voiceController.isListening()) { addTopLabel(voiceStatus); return; }
        if (!preferences.predictions() || isPrivateMode()) { addTopLabel(isPrivateMode() ? "Private typing" : "Suggestions off"); return; }
        List<String> suggestions = glideAlternatives.isEmpty()
                ? suggestionEngine.suggest(currentWord(), previousWord(), personalLexicon, profile, preferences.dialect(), 3)
                : new ArrayList<>(glideAlternatives);
        if (preferences.emojiSuggestions() && glideAlternatives.isEmpty()) {
            List<String> emoji = suggestionEngine.emojiForWord(currentWord().isEmpty() ? previousWord() : currentWord());
            if (!emoji.isEmpty() && !suggestions.isEmpty()) suggestions.set(Math.min(2, suggestions.size() - 1), emoji.get(0));
        }
        for (String suggestion : suggestions) addTopButton(suggestion, "Suggestion " + suggestion, () -> {
            if (isEmojiLike(suggestion)) {
                InputConnection connection = getCurrentInputConnection(); if (connection != null) connection.commitText(suggestion, 1);
            } else replaceCurrentWord(suggestion);
        });
    }

    private void renderTools() {
        addActionButton("Undo", KeySpec.Action.UNDO); addActionButton("Redo", KeySpec.Action.REDO);
        addActionButton("Cut", KeySpec.Action.CUT); addActionButton("Copy", KeySpec.Action.COPY); addActionButton("Paste", KeySpec.Action.PASTE);
        addActionButton("All", KeySpec.Action.SELECT_ALL);
        addActionButton("←", KeySpec.Action.LEFT); addActionButton("↑", KeySpec.Action.UP); addActionButton("↓", KeySpec.Action.DOWN); addActionButton("→", KeySpec.Action.RIGHT);
        addActionButton("Del", KeySpec.Action.FORWARD_DELETE); addActionButton("Esc", KeySpec.Action.ESCAPE);
        addTopButton("Voice", "Voice typing", this::toggleVoiceTyping);
        addTopButton("Translate", "Translate", this::startTranslation);
    }

    private void renderClipboard() {
        List<ClipboardRepository.Clip> clips = clipboardRepository.captureCurrent(preferences.clipboardHistory() && !isPrivateMode());
        if (clips.isEmpty()) addTopLabel("Clipboard is empty");
        for (ClipboardRepository.Clip clip : clips) {
            String label = clip.text.replace('\n', ' '); if (label.length() > 24) label = label.substring(0, 24) + "…";
            addTopButton(label, "Paste " + label, () -> {
                InputConnection connection = getCurrentInputConnection(); if (connection != null) connection.commitText(clip.text, 1);
                panel = Panel.SUGGESTIONS; renderTopPanel();
            });
        }
        RichContentHelper.ClipboardContent content = richContentHelper.currentClipboardContent();
        if (content != null && richContentHelper.canCommit(editorInfo, content)) addTopButton("Paste image", "Paste clipboard image", this::commitClipboardContent);
        addTopButton("Clear", "Clear clipboard history", () -> { clipboardRepository.clearUnpinned(); renderTopPanel(); });
    }

    private void renderRichContent() {
        RichContentHelper.ClipboardContent content = richContentHelper.currentClipboardContent();
        if (content != null && richContentHelper.canCommit(editorInfo, content)) addTopButton("🖼", "Insert clipboard image", this::commitClipboardContent);
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(suggestionEngine.emojiForWord(currentWord().isEmpty() ? previousWord() : currentWord()));
        Collections.addAll(values, "😀","😂","🥰","😍","😊","🙏","👍","❤️","🔥","🎉","✅","💯","🤔","😢","😡","👏","🤝","🚀",
                "¯\\_(ツ)_/¯","( ͡° ͜ʖ ͡°)","(╯°□°)╯︵ ┻━┻","ಠ_ಠ","ʕ•ᴥ•ʔ");
        for (String value : values) addTopButton(value, "Insert " + value, () -> {
            InputConnection connection = getCurrentInputConnection(); if (connection != null) connection.commitText(value, 1);
        });
    }

    private void renderTranslation() {
        if (!translationStatus.isEmpty()) addTopLabel(translationStatus);
        if (!translationResult.isEmpty()) {
            String label = translationResult.length() > 32 ? translationResult.substring(0, 32) + "…" : translationResult;
            addTopButton(label, "Insert translation", () -> {
                InputConnection connection = getCurrentInputConnection(); if (connection != null) connection.commitText(translationResult, 1);
                panel = Panel.SUGGESTIONS; renderTopPanel();
            });
            addTopButton("Copy", "Copy translation", () -> {
                ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("PCBoard translation", translationResult));
            });
        } else addTopButton("Try again", "Translate text", this::startTranslation);
    }

    private void renderSnippets() {
        if (layoutConfig.snippets.isEmpty()) addTopLabel("Add snippets in the layout editor");
        for (String snippet : layoutConfig.snippets) {
            String label = snippet.length() > 26 ? snippet.substring(0, 26) + "…" : snippet;
            addTopButton(label, "Insert " + snippet, () -> {
                InputConnection connection = getCurrentInputConnection(); if (connection != null) connection.commitText(snippet, 1);
                panel = Panel.SUGGESTIONS; renderTopPanel();
            });
        }
    }

    private void toggleVoiceTyping() {
        if (!preferences.voiceTyping()) { Toast.makeText(this, "Enable voice typing in PCBoard settings", Toast.LENGTH_LONG).show(); return; }
        if (isPrivateMode()) { Toast.makeText(this, "Voice typing is disabled in private fields", Toast.LENGTH_LONG).show(); return; }
        voiceController.toggle(voiceLanguageTag()); renderTopPanel();
    }

    private void startTranslation() {
        if (isPrivateMode()) { onTranslationError("Translation is disabled in private fields."); return; }
        InputConnection connection = getCurrentInputConnection(); if (connection == null) return;
        CharSequence selected = connection.getSelectedText(0);
        String source = selected == null ? "" : selected.toString().trim();
        if (source.isEmpty()) source = recentSentence();
        translationResult = ""; translationStatus = "Preparing translation…";
        panel = Panel.TRANSLATION; renderTopPanel();
        translationController.translate(source, preferences.translationTarget());
    }

    private void showHandwriting() {
        if (isPrivateMode()) { Toast.makeText(this, "Handwriting is disabled in private fields", Toast.LENGTH_LONG).show(); return; }
        KeyboardSurface.Palette p = palette(); handwritingController.show(root, p.special, p.text, p.active);
    }

    private void commitClipboardContent() {
        InputConnection connection = getCurrentInputConnection();
        RichContentHelper.ClipboardContent content = richContentHelper.currentClipboardContent();
        boolean success = richContentHelper.commit(connection, editorInfo, content);
        Toast.makeText(this, success ? "Content inserted" : "The current app does not accept this clipboard content", Toast.LENGTH_LONG).show();
    }

    private void stopVoiceComposing() {
        if (!voiceComposing) return;
        InputConnection connection = getCurrentInputConnection(); if (connection != null) connection.finishComposingText();
        voiceComposing = false;
    }

    private void addActionButton(String label, KeySpec.Action action) {
        addTopButton(label, label, () -> onKey(KeySpec.action(label, action, 1f, false, label)));
    }

    private void addTopButton(String label, String description, Runnable action) {
        TextView button = new TextView(this);
        button.setText(label); button.setTextSize(label.length() > 12 ? 13 : label.length() > 8 ? 14 : 17); button.setGravity(Gravity.CENTER);
        button.setTextColor(palette().text); button.setContentDescription(description); button.setFocusable(true); button.setClickable(true);
        button.setPadding(dp(11), 0, dp(11), 0); button.setMinWidth(dp(48)); button.setMinHeight(dp(44));
        GradientDrawable background = new GradientDrawable(); background.setColor(palette().special); background.setCornerRadius(dp(18)); button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)); params.setMargins(dp(3), 0, dp(3), 0);
        topRow.addView(button, params); button.setOnClickListener(v -> action.run());
    }

    private void addTopLabel(String text) {
        TextView label = new TextView(this); label.setText(text); label.setTextSize(15); label.setTextColor(palette().text); label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(14), 0, dp(14), 0); topRow.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
    }

    private void applyPalette() {
        KeyboardSurface.Palette p = palette(); root.setBackgroundColor(p.background); topRow.setBackgroundColor(p.background);
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

    private void applyBottomSafetyGap(WindowInsets insets) {
        if (root == null) return;
        int bottom = dp(preferences == null ? 20 : preferences.bottomGap());
        if (insets != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets system = insets.getInsets(WindowInsets.Type.navigationBars() | WindowInsets.Type.systemGestures());
                bottom = Math.max(bottom, system.bottom + dp(4));
            } else bottom = Math.max(bottom, insets.getSystemWindowInsetBottom() + dp(4));
        }
        root.setPadding(0, 0, 0, bottom);
    }

    private boolean isPrivateMode() {
        if (preferences.incognito() || inputKind == KeyboardLayoutFactory.InputKind.PASSWORD) return true;
        return editorInfo != null && (editorInfo.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
    }

    private boolean shouldAutoCap() {
        if (inputKind == KeyboardLayoutFactory.InputKind.EMAIL || inputKind == KeyboardLayoutFactory.InputKind.URL || inputKind == KeyboardLayoutFactory.InputKind.PASSWORD) return false;
        String before = beforeCursor(80).trim(); return before.isEmpty() || endsSentence(before);
    }

    private static boolean endsSentence(String text) {
        if (text == null) return false; String trimmed = text.trim(); if (trimmed.isEmpty()) return false;
        char last = trimmed.charAt(trimmed.length() - 1); return last == '.' || last == '!' || last == '?' || last == '\n';
    }

    private String currentWord() {
        String before = beforeCursor(100); int end = before.length(), start = end;
        while (start > 0 && isWordCharacter(before.charAt(start - 1))) start--;
        return before.substring(start, end);
    }

    private String previousWord() {
        String before = beforeCursor(180); int end = before.length();
        while (end > 0 && isWordCharacter(before.charAt(end - 1))) end--;
        while (end > 0 && !isWordCharacter(before.charAt(end - 1))) end--;
        int start = end; while (start > 0 && isWordCharacter(before.charAt(start - 1))) start--;
        return before.substring(start, end);
    }

    private String recentSentence() {
        String before = beforeCursor(500).trim(); if (before.isEmpty()) return "";
        int start = Math.max(Math.max(before.lastIndexOf('.'), before.lastIndexOf('!')), Math.max(before.lastIndexOf('?'), before.lastIndexOf('\n')));
        String sentence = before.substring(Math.min(before.length(), start + 1)).trim();
        if (sentence.length() > 220) sentence = sentence.substring(sentence.length() - 220);
        return sentence;
    }

    private String beforeCursor(int length) {
        InputConnection connection = getCurrentInputConnection(); if (connection == null) return "";
        CharSequence before = connection.getTextBeforeCursor(length, 0); return before == null ? "" : before.toString();
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
        Intent intent = new Intent(this, SettingsActivity.class); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent);
    }

    private String voiceLanguageTag() {
        String dialect = preferences.dialect().toLowerCase(Locale.ROOT);
        if (dialect.contains("british")) return "en-GB";
        if (dialect.contains("canadian")) return "en-CA";
        if (dialect.contains("us")) return "en-US";
        return "en-NG";
    }

    private static String matchCase(String original, String replacement) {
        if (original == null || original.isEmpty()) return replacement;
        if (original.equals(original.toUpperCase(Locale.ROOT))) return replacement.toUpperCase(Locale.ROOT);
        if (Character.isUpperCase(original.charAt(0))) return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
        return replacement;
    }

    private static boolean isWordCharacter(char c) { return Character.isLetter(c) || c == '\'' || c == '’' || c == '-'; }
    private static boolean isEmojiLike(String value) {
        if (value == null || value.isEmpty()) return false;
        int first = value.codePointAt(0); return first > 0x1F000 || !value.matches("[A-Za-z'’ -]+");
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
