package com.treasure.pcboard;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import java.util.*;

public final class KeyboardLayoutFactory {
    public enum ShiftState { OFF, ON, LOCKED }
    public enum InputKind { TEXT, EMAIL, URL, PHONE, NUMBER, PASSWORD }

    private static final Map<String,String> ACCENTS = new HashMap<>();
    private static final Map<String,String> SYMBOLS = new HashMap<>();
    static {
        ACCENTS.put("a", "áàâäãå"); ACCENTS.put("e", "éèêë");
        ACCENTS.put("i", "íìîï"); ACCENTS.put("o", "óòôöõø");
        ACCENTS.put("u", "úùûü"); ACCENTS.put("c", "ç");
        ACCENTS.put("n", "ñ"); ACCENTS.put("s", "ß");

        SYMBOLS.put("q", "1"); SYMBOLS.put("w", "2"); SYMBOLS.put("e", "3");
        SYMBOLS.put("r", "4"); SYMBOLS.put("t", "5"); SYMBOLS.put("y", "6");
        SYMBOLS.put("u", "7"); SYMBOLS.put("i", "8"); SYMBOLS.put("o", "9"); SYMBOLS.put("p", "0");
        SYMBOLS.put("a", "@"); SYMBOLS.put("s", "#"); SYMBOLS.put("d", "$");
        SYMBOLS.put("f", "%"); SYMBOLS.put("g", "&"); SYMBOLS.put("h", "-");
        SYMBOLS.put("j", "+"); SYMBOLS.put("k", "("); SYMBOLS.put("l", ")");
        SYMBOLS.put("z", "*"); SYMBOLS.put("x", "\""); SYMBOLS.put("c", "'");
        SYMBOLS.put("v", ":"); SYMBOLS.put("b", ";"); SYMBOLS.put("n", "!"); SYMBOLS.put("m", "?");
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
                                              ProfileManager.Profile profile, InputKind inputKind,
                                              CustomLayoutStore.Config config, boolean longPressSymbols) {
        if (inputKind == InputKind.PHONE || inputKind == InputKind.NUMBER) return numberPad(inputKind, config);
        List<List<KeySpec>> rows = new ArrayList<>();
        if (!symbols && numberRow) rows.add(numberRow(longPressSymbols));
        if (!symbols) {
            List<KeySpec> utility = profileRow(profile);
            if (!utility.isEmpty()) rows.add(utility);
            rows.add(letterRow("qwertyuiop", shift, longPressSymbols));
            List<KeySpec> second = new ArrayList<>();
            second.add(KeySpec.action("⇥", KeySpec.Action.TAB, 1.25f, false, "Tab"));
            second.addAll(letterRow("asdfghjkl", shift, longPressSymbols));
            rows.add(second);
            List<KeySpec> third = new ArrayList<>();
            third.add(KeySpec.action(shift == ShiftState.LOCKED ? "⇧●" : "⇧", KeySpec.Action.SHIFT, 1.35f, false, shift == ShiftState.LOCKED ? "Caps lock on" : "Shift"));
            third.addAll(letterRow("zxcvbnm", shift, longPressSymbols));
            third.add(KeySpec.action("⌫", KeySpec.Action.BACKSPACE, 1.45f, true, "Backspace"));
            rows.add(third);
            rows.add(bottomRow(inputKind, false, config));
        } else {
            rows.add(symbolNumberRow());
            rows.add(Arrays.asList(
                    symbolKey("@", "~"), symbolKey("#", "`"), symbolKey("$", "€£¥"), symbolKey("%", "‰"), symbolKey("&", "§"),
                    symbolKey("-", "—–_"), symbolKey("+", "±"), symbolKey("(", "[{") , symbolKey(")", "]}"), symbolKey("/", "\\|")));
            rows.add(Arrays.asList(
                    symbolKey("*", "•×"), symbolKey("\"", "“”"), symbolKey("'", "‘’"), symbolKey(":", "…"), symbolKey(";", null),
                    symbolKey("!", "¡"), symbolKey("?", "¿"), symbolKey("=", "≠≈"), KeySpec.action("⌫", KeySpec.Action.BACKSPACE, 1.45f, true, "Backspace")));
            rows.add(bottomRow(inputKind, true, config));
        }
        return rows;
    }

    private static List<KeySpec> numberRow(boolean longPressSymbols) {
        String digits = "1234567890";
        String shifted = "!@#$%^&*()";
        List<KeySpec> row = new ArrayList<>();
        for (int index = 0; index < digits.length(); index++) {
            String digit = String.valueOf(digits.charAt(index));
            String alternate = longPressSymbols ? String.valueOf(shifted.charAt(index)) : null;
            row.add(KeySpec.text(digit, digit, alternate, alternate, 1f));
        }
        return row;
    }

    private static List<KeySpec> symbolNumberRow() {
        List<KeySpec> row = new ArrayList<>();
        for (char c = '1'; c <= '9'; c++) row.add(KeySpec.text(String.valueOf(c)));
        row.add(KeySpec.text("0"));
        return row;
    }

    private static List<KeySpec> letterRow(String letters, ShiftState shift, boolean longPressSymbols) {
        List<KeySpec> row = new ArrayList<>();
        for (char c : letters.toCharArray()) {
            String lower = String.valueOf(c);
            String label = shift == ShiftState.OFF ? lower : lower.toUpperCase(Locale.ROOT);
            String symbol = longPressSymbols ? SYMBOLS.get(lower) : null;
            String accent = ACCENTS.get(lower);
            String alternate = join(symbol, accent);
            row.add(KeySpec.text(label, label, alternate, symbol, 1f));
        }
        return row;
    }

    private static KeySpec symbolKey(String value, String alternates) {
        return KeySpec.text(value, value, alternates, alternates == null ? null : firstCodePoint(alternates), 1f);
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

    private static List<KeySpec> bottomRow(InputKind kind, boolean symbols, CustomLayoutStore.Config config) {
        List<KeySpec> row = new ArrayList<>();
        row.add(KeySpec.action(symbols ? "ABC" : "?123", KeySpec.Action.SYMBOLS, 1.15f, false, symbols ? "Letters" : "Symbols"));
        row.add(modifierKey(config == null ? "ctrl" : config.bottomModifier));
        String left = kind == InputKind.EMAIL ? "@" : kind == InputKind.URL ? "/" : config == null ? "," : config.leftPunctuation;
        String right = config == null ? "." : config.rightPunctuation;
        row.add(KeySpec.text(left, left, punctuationAlternates(left), null, .75f));
        row.add(new KeySpec("space", " ", null, null, KeySpec.Action.SPACE, 4.2f, false, "Space"));
        row.add(KeySpec.text(right, right, punctuationAlternates(right), null, .75f));
        row.add(KeySpec.action("↵", KeySpec.Action.ENTER, 1.25f, false, "Enter"));
        return row;
    }

    private static KeySpec modifierKey(String value) {
        if ("tab".equals(value)) return KeySpec.action("Tab", KeySpec.Action.TAB, 1f, false, "Tab");
        if ("alt".equals(value)) return KeySpec.action("Alt", KeySpec.Action.ALT, 1f, false, "Alt");
        if ("esc".equals(value)) return KeySpec.action("Esc", KeySpec.Action.ESCAPE, 1f, false, "Escape");
        return KeySpec.action("Ctrl", KeySpec.Action.CTRL, 1f, false, "Control");
    }

    private static List<List<KeySpec>> numberPad(InputKind kind, CustomLayoutStore.Config config) {
        List<List<KeySpec>> rows = new ArrayList<>();
        rows.add(Arrays.asList(KeySpec.text("1"), KeySpec.text("2"), KeySpec.text("3"), KeySpec.action("⌫", KeySpec.Action.BACKSPACE, 1f, true, "Backspace")));
        rows.add(Arrays.asList(KeySpec.text("4"), KeySpec.text("5"), KeySpec.text("6"), KeySpec.text(kind == InputKind.PHONE ? "+" : "-")));
        rows.add(Arrays.asList(KeySpec.text("7"), KeySpec.text("8"), KeySpec.text("9"), KeySpec.text(kind == InputKind.PHONE ? "#" : ".")));
        rows.add(Arrays.asList(modifierKey(config == null ? "ctrl" : config.bottomModifier), KeySpec.text("0"), KeySpec.action("⇥", KeySpec.Action.TAB, 1f, false, "Tab"), KeySpec.action("↵", KeySpec.Action.ENTER, 1f, false, "Enter")));
        return rows;
    }

    private static String punctuationAlternates(String value) {
        if (",".equals(value)) return ";:'";
        if (".".equals(value)) return "…!?";
        if ("?".equals(value)) return "¿!";
        if ("!".equals(value)) return "¡?";
        if ("/".equals(value)) return "\\|";
        if ("@".equals(value)) return "#&_";
        return null;
    }

    private static String join(String first, String second) {
        if (first == null || first.isEmpty()) return second;
        if (second == null || second.isEmpty()) return first;
        return first + second;
    }

    private static String firstCodePoint(String value) {
        if (value == null || value.isEmpty()) return null;
        return new String(Character.toChars(value.codePointAt(0)));
    }
}
