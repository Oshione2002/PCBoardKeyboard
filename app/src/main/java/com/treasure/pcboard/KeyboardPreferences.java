package com.treasure.pcboard;

import android.content.Context;
import android.content.SharedPreferences;

public final class KeyboardPreferences {
    public static final String FILE = "pcboard_settings";
    public static final String PREDICTIONS = "predictions";
    public static final String AUTOCORRECT = "autocorrect";
    public static final String AUTO_CAP = "auto_cap";
    public static final String DOUBLE_SPACE = "double_space";
    public static final String NUMBER_ROW = "number_row";
    public static final String KEY_POPUP = "key_popup";
    public static final String HAPTIC = "haptic";
    public static final String SOUND = "sound";
    public static final String CLIPBOARD_HISTORY = "clipboard_history";
    public static final String EMOJI_SUGGESTIONS = "emoji_suggestions";
    public static final String AUTO_PROFILE = "auto_profile";
    public static final String INCOGNITO = "incognito";
    public static final String THEME = "theme";
    public static final String DIALECT = "dialect";
    public static final String ONE_HANDED = "one_handed";
    public static final String HEIGHT = "height";
    public static final String LONG_PRESS_DELAY = "long_press_delay";
    public static final String LONG_PRESS_SYMBOLS = "long_press_symbols";
    public static final String SYMBOL_HINTS = "symbol_hints";
    public static final String GLIDE_TYPING = "glide_typing";
    public static final String VOICE_TYPING = "voice_typing";
    public static final String BOTTOM_GAP = "bottom_gap";
    public static final String TRANSLATION_TARGET = "translation_target";

    private final SharedPreferences prefs;

    public KeyboardPreferences(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public SharedPreferences raw() { return prefs; }
    public boolean predictions() { return prefs.getBoolean(PREDICTIONS, true); }
    public boolean autocorrect() { return prefs.getBoolean(AUTOCORRECT, true); }
    public boolean autoCap() { return prefs.getBoolean(AUTO_CAP, true); }
    public boolean doubleSpacePeriod() { return prefs.getBoolean(DOUBLE_SPACE, true); }
    public boolean numberRow() { return prefs.getBoolean(NUMBER_ROW, true); }
    public boolean keyPopup() { return prefs.getBoolean(KEY_POPUP, true); }
    public boolean haptic() { return prefs.getBoolean(HAPTIC, true); }
    public boolean sound() { return prefs.getBoolean(SOUND, false); }
    public boolean clipboardHistory() { return prefs.getBoolean(CLIPBOARD_HISTORY, true); }
    public boolean emojiSuggestions() { return prefs.getBoolean(EMOJI_SUGGESTIONS, true); }
    public boolean autoProfile() { return prefs.getBoolean(AUTO_PROFILE, true); }
    public boolean incognito() { return prefs.getBoolean(INCOGNITO, false); }
    public boolean longPressSymbols() { return prefs.getBoolean(LONG_PRESS_SYMBOLS, true); }
    public boolean symbolHints() { return prefs.getBoolean(SYMBOL_HINTS, true); }
    public boolean glideTyping() { return prefs.getBoolean(GLIDE_TYPING, true); }
    public boolean voiceTyping() { return prefs.getBoolean(VOICE_TYPING, true); }
    public String theme() { return prefs.getString(THEME, "system"); }
    public String dialect() { return prefs.getString(DIALECT, "Nigerian English"); }
    public String oneHanded() { return prefs.getString(ONE_HANDED, "off"); }
    public String translationTarget() { return prefs.getString(TRANSLATION_TARGET, "fr"); }
    public int heightPercent() { return clamp(prefs.getInt(HEIGHT, 118), 90, 180); }
    public int longPressDelay() { return clamp(prefs.getInt(LONG_PRESS_DELAY, 390), 220, 700); }
    public int bottomGap() { return clamp(prefs.getInt(BOTTOM_GAP, 20), 0, 56); }

    public void setString(String key, String value) { prefs.edit().putString(key, value).apply(); }
    public void setBoolean(String key, boolean value) { prefs.edit().putBoolean(key, value).apply(); }
    public void setInt(String key, int value) { prefs.edit().putInt(key, value).apply(); }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
