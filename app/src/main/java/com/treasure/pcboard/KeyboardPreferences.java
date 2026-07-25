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

    private final SharedPreferences prefs;

    public KeyboardPreferences(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public SharedPreferences raw() { return prefs; }
    public boolean predictions() { return prefs.getBoolean(PREDICTIONS, true); }
    public boolean autocorrect() { return prefs.getBoolean(AUTOCORRECT, true); }
    public boolean autoCap() { return prefs.getBoolean(AUTO_CAP, true); }
    public boolean doubleSpacePeriod() { return prefs.getBoolean(DOUBLE_SPACE, true); }
    public boolean numberRow() { return prefs.getBoolean(NUMBER_ROW, false); }
    public boolean keyPopup() { return prefs.getBoolean(KEY_POPUP, true); }
    public boolean haptic() { return prefs.getBoolean(HAPTIC, true); }
    public boolean sound() { return prefs.getBoolean(SOUND, false); }
    public boolean clipboardHistory() { return prefs.getBoolean(CLIPBOARD_HISTORY, true); }
    public boolean emojiSuggestions() { return prefs.getBoolean(EMOJI_SUGGESTIONS, true); }
    public boolean autoProfile() { return prefs.getBoolean(AUTO_PROFILE, true); }
    public boolean incognito() { return prefs.getBoolean(INCOGNITO, false); }
    public String theme() { return prefs.getString(THEME, "system"); }
    public String dialect() { return prefs.getString(DIALECT, "Nigerian English"); }
    public String oneHanded() { return prefs.getString(ONE_HANDED, "off"); }
    public int heightPercent() { return clamp(prefs.getInt(HEIGHT, 100), 80, 130); }
    public int longPressDelay() { return clamp(prefs.getInt(LONG_PRESS_DELAY, 420), 250, 700); }

    public void setString(String key, String value) { prefs.edit().putString(key, value).apply(); }
    public void setBoolean(String key, boolean value) { prefs.edit().putBoolean(key, value).apply(); }
    public void setInt(String key, int value) { prefs.edit().putInt(key, value).apply(); }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
