package com.treasure.pcboard;

public final class KeySpec {
    public enum Action {
        TEXT, SHIFT, BACKSPACE, ENTER, SPACE, SYMBOLS, CTRL, ALT, TAB,
        LEFT, RIGHT, UP, DOWN, FORWARD_DELETE, ESCAPE,
        COPY, CUT, PASTE, SELECT_ALL, UNDO, REDO,
        CLIPBOARD, TOOLS, EMOJI, PROFILE, SETTINGS, HIDE
    }

    public final String label;
    public final String output;
    public final String alternate;
    public final Action action;
    public final float weight;
    public final boolean repeatable;
    public final String accessibilityLabel;

    public KeySpec(String label, String output, String alternate, Action action,
                   float weight, boolean repeatable, String accessibilityLabel) {
        this.label = label;
        this.output = output;
        this.alternate = alternate;
        this.action = action;
        this.weight = weight;
        this.repeatable = repeatable;
        this.accessibilityLabel = accessibilityLabel == null ? label : accessibilityLabel;
    }

    public static KeySpec text(String label) {
        return new KeySpec(label, label, null, Action.TEXT, 1f, false, label);
    }

    public static KeySpec text(String label, String output, String alternate, float weight) {
        return new KeySpec(label, output, alternate, Action.TEXT, weight, false, output);
    }

    public static KeySpec action(String label, Action action, float weight, boolean repeatable, String description) {
        return new KeySpec(label, null, null, action, weight, repeatable, description);
    }
}
