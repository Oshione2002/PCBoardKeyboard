package com.treasure.pcboard;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

public final class CustomLayoutStore {
    private static final String FILE = "pcboard_layout_v2";
    private static final String TOOLBAR = "toolbar";
    private static final String BOTTOM_MODIFIER = "bottom_modifier";
    private static final String LEFT_PUNCTUATION = "left_punctuation";
    private static final String RIGHT_PUNCTUATION = "right_punctuation";
    private static final String SNIPPETS = "snippets";

    private static final String DEFAULT_TOOLBAR =
            "clipboard,tools,emoji,voice,translate,handwriting,snippets,profile,settings";
    private static final String DEFAULT_SNIPPETS =
            "Thank you\nPlease send the details\nI am on my way\nI will get back to you\nNo problem\nNo wahala";

    public static final String[] TOOLBAR_OPTIONS = {
            "clipboard", "tools", "emoji", "voice", "translate", "handwriting",
            "snippets", "profile", "settings", "hide"
    };

    public static final class Config {
        public final List<String> toolbarActions;
        public final String bottomModifier;
        public final String leftPunctuation;
        public final String rightPunctuation;
        public final List<String> snippets;

        Config(List<String> toolbarActions, String bottomModifier, String leftPunctuation,
               String rightPunctuation, List<String> snippets) {
            this.toolbarActions = toolbarActions;
            this.bottomModifier = bottomModifier;
            this.leftPunctuation = leftPunctuation;
            this.rightPunctuation = rightPunctuation;
            this.snippets = snippets;
        }
    }

    private final SharedPreferences preferences;

    public CustomLayoutStore(Context context) {
        preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public Config load() {
        return new Config(
                parseCsv(preferences.getString(TOOLBAR, DEFAULT_TOOLBAR)),
                preferences.getString(BOTTOM_MODIFIER, "ctrl"),
                preferences.getString(LEFT_PUNCTUATION, ","),
                preferences.getString(RIGHT_PUNCTUATION, "."),
                parseLines(preferences.getString(SNIPPETS, DEFAULT_SNIPPETS))
        );
    }

    public void setToolbarActions(List<String> values) {
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String value : values) {
            String candidate = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (Arrays.asList(TOOLBAR_OPTIONS).contains(candidate)) cleaned.add(candidate);
        }
        if (cleaned.isEmpty()) cleaned.addAll(parseCsv(DEFAULT_TOOLBAR));
        preferences.edit().putString(TOOLBAR, String.join(",", cleaned)).apply();
    }

    public void setBottomModifier(String value) {
        preferences.edit().putString(BOTTOM_MODIFIER, safeChoice(value,
                new String[]{"ctrl", "tab", "alt", "esc"}, "ctrl")).apply();
    }

    public void setLeftPunctuation(String value) {
        preferences.edit().putString(LEFT_PUNCTUATION, safeChoice(value,
                new String[]{",", "@", "/", "_", "'"}, ",")).apply();
    }

    public void setRightPunctuation(String value) {
        preferences.edit().putString(RIGHT_PUNCTUATION, safeChoice(value,
                new String[]{".", "?", "!", ":", ";"}, ".")).apply();
    }

    public void setSnippets(List<String> values) {
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            String text = value == null ? "" : value.trim();
            if (!text.isEmpty()) cleaned.add(text.replace('\n', ' '));
            if (cleaned.size() == 8) break;
        }
        preferences.edit().putString(SNIPPETS, String.join("\n", cleaned)).apply();
    }

    public void reset() {
        preferences.edit().clear().apply();
    }

    private static List<String> parseCsv(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        for (String item : value.split(",")) {
            String cleaned = item.trim().toLowerCase(Locale.ROOT);
            if (!cleaned.isEmpty()) result.add(cleaned);
        }
        return result;
    }

    private static List<String> parseLines(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        for (String item : value.split("\\n")) {
            String cleaned = item.trim();
            if (!cleaned.isEmpty()) result.add(cleaned);
        }
        return result;
    }

    private static String safeChoice(String value, String[] allowed, String fallback) {
        if (value != null) {
            for (String option : allowed) if (option.equals(value)) return value;
        }
        return fallback;
    }
}
