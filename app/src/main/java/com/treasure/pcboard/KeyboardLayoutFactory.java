package com.treasure.pcboard;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import java.util.*;

public final class KeyboardLayoutFactory {
    public enum ShiftState { OFF, ON, LOCKED }
    public enum InputKind { TEXT, EMAIL, URL, PHONE, NUMBER, PASSWORD }

    private static final Map<String,String> ALTERNATES = new HashMap<>();
    static {
        ALTERNATES.put("a", "áàâäãå"); ALTERNATES.put("e", "éèêë");
        ALTERNATES.put("i", "íìîï"); ALTERNATES.put("o", "óòôöõø");
        ALTERNATES.put("u", "úùûü"); ALTERNATES.put("c", "ç");
        ALTERNATES.put("n", "ñ"); ALTERNATES.put("s", "ß");
        ALTERNATES.put("q", "1"); ALTERNATES.put("w", "2");
        ALTERNATES.put("r", "4"); ALTERNATES.put("t", "5");
        ALTERNATES.put("y", "6"); ALTERNATES.put("p", "0");
    }

    private KeyboardLayoutFactory() {}

    public static InputKind inputKind(EditorInfo info) {
        if (info == null) return InputKind.TEXT;
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        if (cls == InputType.TYPE_CLASS_PHONE) return InputKind.PHONE;
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_DATETIME) return InputKind.NUMBER;
        if (cls == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) return InputKind.EMAIL;
            if (variation == InputType.TYPE_TEXT_VARIATION_URI) return InputKind.URL;
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) return InputKind.PASSWORD;
        }
        return InputKind.TEXT;
    }

    public static List<List<KeySpec>> build(boolean symbols, ShiftState shift, boolean numberRow,
                                             ProfileManager.Profile profile, InputKind inputKind) {
        if (inputKind == InputKind.PHONE || inputKind == InputKind.NUMBER) return numberPad(inputKind);
        List<List<KeySpec>> rows = new ArrayList<>();
        if (!symbols && numberRow) rows.add(numberRow());
        if (!symbols) {
            List<KeySpec> utility = profileRow(profile);
            if (!utility.isEmpty()) rows.add(utility);
            rows.add(letterRow("qwertyuiop", shift));
            List<KeySpec> second = new ArrayList<>();
            second.add(KeySpec.action("⇥", KeySpec.Action.TAB, 1.25f, false, "Tab"));
            second.addAll(letterRow("asdfghjkl", shift));
            rows.add(second);
            List<KeySpec> third = new ArrayList<>();
            third.add(KeySpec.action(shift == ShiftState.LOCKED ? "⇧●" : "⇧", KeySpec.Action.SHIFT, 1.35f, false, shift == ShiftState.LOCKED ? "Caps lock on" : "Shift"));
            third.addAll(letterRow("zxcvbnm", shift));
            third.add(KeySpec.action("⌫", KeySpec.Action.BACKSPACE, 1.45f, true, "Backspace"));
            rows.add(third);
            rows.add(bottomRow(inputKind, false));
        } else {
            rows.add(symbolRow("1234567890"));
            rows.add(Arrays.asList(
                    KeySpec.text("@"), KeySpec.text("#"), KeySpec.text("$"), KeySpec.text("%"), KeySpec.text("&"),
                    KeySpec.text("-"), KeySpec.text("+"), KeySpec.text("("), KeySpec.text(")"), KeySpec.text("/")));
            rows.add(Arrays.asList(
                    KeySpec.text("*"), KeySpec.text("\""), KeySpec.text("'"), KeySpec.text(":"), KeySpec.text(";"),
                    KeySpec.text("!"), KeySpec.text("?"), KeySpec.text("="), KeySpec.action("⌫", KeySpec.Action.BACKSPACE, 1.45f, true, "Backspace")));
            rows.add(bottomRow(inputKind, true));
        }
        return rows;
    }

    private static List<KeySpec> numberRow() {
        List<KeySpec> row = new ArrayList<>();
        for (char c = '1'; c <= '9'; c++) row.add(KeySpec.text(String.valueOf(c)));
        row.add(KeySpec.text("0"));
        return row;
    }

    private static List<KeySpec> letterRow(String letters, ShiftState shift) {
        List<KeySpec> row = new ArrayList<>();
        for (char c : letters.toCharArray()) {
            String lower = String.valueOf(c);
            String label = shift == ShiftState.OFF ? lower : lower.toUpperCase(Locale.ROOT);
            String alternate = ALTERNATES.get(lower);
            row.add(KeySpec.text(label, label, alternate, 1f));
        }
        return row;
    }

    private static List<KeySpec> symbolRow(String symbols) {
        List<KeySpec> row = new ArrayList<>();
        for (char c : symbols.toCharArray()) row.add(KeySpec.text(String.valueOf(c)));
        return row;
    }

    private static List<KeySpec> profileRow(ProfileManager.Profile profile) {
        switch (profile) {
            case TERMINAL:
                return Arrays.asList(
                        KeySpec.action("Esc", KeySpec.Action.ESCAPE, 1f, false, "Escape"),
                        KeySpec.action("Ctrl", KeySpec.Action.CTRL, 1f, false, "Control"),
                        KeySpec.action("Alt", KeySpec.Action.ALT, 1f, false, "Alt"),
                        KeySpec.action("Tab", KeySpec.Action.TAB, 1f, false, "Tab"),
                        KeySpec.text("/"), KeySpec.text("-"), KeySpec.text("_"), KeySpec.text("|"), KeySpec.text("`"));
            case CODING:
                return Arrays.asList(
                        KeySpec.action("Tab", KeySpec.Action.TAB, 1f, false, "Tab"),
                        KeySpec.action("Ctrl", KeySpec.Action.CTRL, 1f, false, "Control"),
                        KeySpec.text("{"), KeySpec.text("}"), KeySpec.text("["), KeySpec.text("]"),
                        KeySpec.text("("), KeySpec.text(")"), KeySpec.text(";"), KeySpec.text("="));
            case SPREADSHEET:
                return Arrays.asList(
                        KeySpec.action("Tab", KeySpec.Action.TAB, 1f, false, "Tab"),
                        KeySpec.action("Ctrl", KeySpec.Action.CTRL, 1f, false, "Control"),
                        KeySpec.action("←", KeySpec.Action.LEFT, 1f, true, "Left arrow"),
                        KeySpec.action("↑", KeySpec.Action.UP, 1f, true, "Up arrow"),
                        KeySpec.action("↓", KeySpec.Action.DOWN, 1f, true, "Down arrow"),
                        KeySpec.action("→", KeySpec.Action.RIGHT, 1f, true, "Right arrow"),
                        KeySpec.action("Copy", KeySpec.Action.COPY, 1.2f, false, "Copy"),
                        KeySpec.action("Paste", KeySpec.Action.PASTE, 1.2f, false, "Paste"));
            default:
                return Collections.emptyList();
        }
    }

    private static List<KeySpec> bottomRow(InputKind kind, boolean symbols) {
        List<KeySpec> row = new ArrayList<>();
        row.add(KeySpec.action(symbols ? "ABC" : "?123", KeySpec.Action.SYMBOLS, 1.15f, false, symbols ? "Letters" : "Symbols"));
        row.add(KeySpec.action("Ctrl", KeySpec.Action.CTRL, 1.0f, false, "Control"));
        if (kind == InputKind.EMAIL) row.add(KeySpec.text("@", "@", null, .75f));
        else if (kind == InputKind.URL) row.add(KeySpec.text("/", "/", null, .75f));
        else row.add(KeySpec.text(",", ",", null, .75f));
        row.add(new KeySpec("space", " ", null, KeySpec.Action.SPACE, 4.2f, false, "Space"));
        row.add(KeySpec.text(".", ".", null, .75f));
        row.add(KeySpec.action("↵", KeySpec.Action.ENTER, 1.25f, false, "Enter"));
        return row;
    }

    private static List<List<KeySpec>> numberPad(InputKind kind) {
        List<List<KeySpec>> rows = new ArrayList<>();
        rows.add(Arrays.asList(KeySpec.text("1"), KeySpec.text("2"), KeySpec.text("3"), KeySpec.action("⌫", KeySpec.Action.BACKSPACE, 1f, true, "Backspace")));
        rows.add(Arrays.asList(KeySpec.text("4"), KeySpec.text("5"), KeySpec.text("6"), KeySpec.text(kind == InputKind.PHONE ? "+" : "-")));
        rows.add(Arrays.asList(KeySpec.text("7"), KeySpec.text("8"), KeySpec.text("9"), KeySpec.text(kind == InputKind.PHONE ? "#" : ".")));
        rows.add(Arrays.asList(KeySpec.action("Ctrl", KeySpec.Action.CTRL, 1f, false, "Control"), KeySpec.text("0"), KeySpec.action("⇥", KeySpec.Action.TAB, 1f, false, "Tab"), KeySpec.action("↵", KeySpec.Action.ENTER, 1f, false, "Enter")));
        return rows;
    }
}
