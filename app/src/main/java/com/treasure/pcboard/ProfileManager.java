package com.treasure.pcboard;

import java.util.Locale;

public final class ProfileManager {
    public enum Profile { DEFAULT, TERMINAL, CODING, SPREADSHEET }

    private ProfileManager() {}

    public static Profile detect(String packageName) {
        if (packageName == null) return Profile.DEFAULT;
        String p = packageName.toLowerCase(Locale.ROOT);
        if (containsAny(p, "termux", "terminal", "ssh", "connectbot", "juicessh")) return Profile.TERMINAL;
        if (containsAny(p, "code", "editor", "studio", "ide", "github", "gitlab", "replit")) return Profile.CODING;
        if (containsAny(p, "sheets", "excel", "spreadsheet", "office")) return Profile.SPREADSHEET;
        return Profile.DEFAULT;
    }

    public static Profile next(Profile current) {
        switch (current) {
            case DEFAULT: return Profile.TERMINAL;
            case TERMINAL: return Profile.CODING;
            case CODING: return Profile.SPREADSHEET;
            default: return Profile.DEFAULT;
        }
    }

    public static String shortLabel(Profile profile) {
        switch (profile) {
            case TERMINAL: return "TERM";
            case CODING: return "CODE";
            case SPREADSHEET: return "SHEET";
            default: return "AUTO";
        }
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
