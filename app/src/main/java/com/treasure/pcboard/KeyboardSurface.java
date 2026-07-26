package com.treasure.pcboard;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class KeyboardSurface extends LinearLayout {
    public interface Listener {
        void onKey(KeySpec key);
        void onAlternate(String text);
        void onGlide(List<String> path);
        void onSpaceCursor(int direction);
        void onDeleteWord();
        void onHide();
    }

    public static final class Palette {
        public final int background, key, keyPressed, special, active, text;
        public Palette(int background, int key, int keyPressed, int special, int active, int text) {
            this.background = background; this.key = key; this.keyPressed = keyPressed;
            this.special = special; this.active = active; this.text = text;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioManager audioManager;
    private Listener listener;
    private boolean showPopup = true, haptic = true, sound = false, showSymbolHints = true, glideEnabled = true;
    private int longPressDelay = 390;
    private PopupWindow previewPopup;
    private PopupWindow alternatePopup;
    private Palette palette;
    private Set<KeySpec.Action> activeActions = Collections.emptySet();
    private final List<String> glidePath = new ArrayList<>();
    private View currentGlideView;

    public KeyboardSurface(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void configure(Listener listener, boolean showPopup, boolean haptic, boolean sound,
                          int longPressDelay, boolean showSymbolHints, boolean glideEnabled) {
        this.listener = listener;
        this.showPopup = showPopup;
        this.haptic = haptic;
        this.sound = sound;
        this.longPressDelay = longPressDelay;
        this.showSymbolHints = showSymbolHints;
        this.glideEnabled = glideEnabled;
    }

    public void render(List<List<KeySpec>> rows, Palette palette, Set<KeySpec.Action> activeActions, int heightPercent) {
        this.palette = palette;
        this.activeActions = activeActions == null ? Collections.emptySet() : activeActions;
        removeAllViews();
        setBackgroundColor(palette.background);
        int baseHeight = Math.round(dp(58) * heightPercent / 100f);
        for (List<KeySpec> rowSpecs : rows) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setPadding(dp(3), dp(1), dp(3), dp(1));
            for (KeySpec spec : rowSpecs) row.addView(createKey(spec), keyParams(spec.weight));
            addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, baseHeight));
        }
    }

    private View createKey(KeySpec spec) {
        FrameLayout container = new FrameLayout(getContext());
        container.setTag(spec);
        container.setMinimumWidth(dp(42));
        container.setMinimumHeight(dp(48));
        container.setContentDescription(spec.accessibilityLabel + (spec.hint == null ? "" : ", long press for " + spec.hint));
        container.setFocusable(true); container.setClickable(true); container.setBackground(keyBackground(spec));

        TextView main = new TextView(getContext());
        main.setText(spec.label); main.setTextColor(palette.text);
        main.setTextSize(spec.label.length() > 4 ? 13 : spec.label.length() > 2 ? 15 : 22);
        main.setGravity(Gravity.CENTER); main.setPadding(dp(2), 0, dp(2), 0);
        container.addView(main, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        if (showSymbolHints && spec.hint != null && !spec.hint.isEmpty()) {
            TextView hint = new TextView(getContext());
            hint.setText(spec.hint); hint.setTextColor(palette.text); hint.setAlpha(.72f); hint.setTextSize(10); hint.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(dp(22), dp(19), Gravity.TOP | Gravity.END);
            hintParams.setMargins(0, dp(1), dp(3), 0); container.addView(hint, hintParams);
        }

        container.setOnTouchListener(new KeyTouchListener(spec, container));
        return container;
    }

    private StateListDrawable keyBackground(KeySpec spec) {
        boolean active = activeActions.contains(spec.action);
        boolean special = spec.action != KeySpec.Action.TEXT && spec.action != KeySpec.Action.SPACE;
        int normal = active ? palette.active : special ? palette.special : palette.key;
        StateListDrawable state = new StateListDrawable();
        state.addState(new int[]{android.R.attr.state_pressed}, rounded(palette.keyPressed));
        state.addState(new int[]{}, rounded(normal));
        return state;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(8)); return drawable;
    }

    private LinearLayout.LayoutParams keyParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
        params.setMargins(dp(2), dp(3), dp(2), dp(3)); return params;
    }

    private final class KeyTouchListener implements OnTouchListener {
        private final KeySpec spec;
        private final View keyView;
        private float downX, downY, lastX;
        private boolean consumed, repeating, glideActive, glideCandidate;
        private Runnable longPressRunnable;
        private Runnable repeatRunnable;

        KeyTouchListener(KeySpec spec, View keyView) { this.spec = spec; this.keyView = keyView; }

        @Override public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = lastX = event.getRawX(); downY = event.getRawY(); consumed = false; repeating = false; glideActive = false;
                    glideCandidate = glideEnabled && isGlideLetter(spec);
                    glidePath.clear();
                    if (glideCandidate) glidePath.add(spec.output.toLowerCase(Locale.ROOT));
                    feedback(view); view.setPressed(true);
                    if (showPopup && shouldPreview(spec)) showPreview(spec.label, keyView);
                    scheduleLongPress(); return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (Math.abs(dx) > dp(10) || Math.abs(dy) > dp(10)) cancelLongPressOnly();
                    if (glideCandidate && Math.hypot(dx, dy) > dp(14)) {
                        View hovered = findTextKeyAt(event.getRawX(), event.getRawY());
                        if (hovered != null) {
                            KeySpec hoveredSpec = (KeySpec) hovered.getTag();
                            String value = hoveredSpec.output.toLowerCase(Locale.ROOT);
                            if (glidePath.isEmpty() || !glidePath.get(glidePath.size() - 1).equals(value)) glidePath.add(value);
                            if (currentGlideView != hovered) {
                                if (currentGlideView != null) currentGlideView.setPressed(false);
                                currentGlideView = hovered; currentGlideView.setPressed(true);
                            }
                            glideActive = glidePath.size() > 1; consumed = glideActive;
                        }
                    } else if (spec.action == KeySpec.Action.SPACE) {
                        float step = dp(22); float delta = event.getRawX() - lastX;
                        if (Math.abs(delta) >= step) {
                            int direction = delta > 0 ? 1 : -1;
                            int count = Math.max(1, (int)(Math.abs(delta) / step));
                            for (int i = 0; i < count; i++) if (listener != null) listener.onSpaceCursor(direction);
                            lastX = event.getRawX(); consumed = true;
                        }
                    } else if (spec.action == KeySpec.Action.BACKSPACE && dx < -dp(48)) {
                        cancelScheduled(); if (listener != null) listener.onDeleteWord(); downX = event.getRawX(); consumed = true;
                    }
                    if (!glideCandidate && dy > dp(70)) {
                        cancelScheduled(); if (listener != null) listener.onHide(); consumed = true;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelScheduled(); dismissPreview(); clearPressedKeys();
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && glideActive && glidePath.size() >= 2 && listener != null) {
                        listener.onGlide(new ArrayList<>(glidePath)); consumed = true;
                    }
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && !consumed && !repeating && listener != null) listener.onKey(spec);
                    glidePath.clear(); glideCandidate = false; glideActive = false; return true;
                default: return false;
            }
        }

        private void scheduleLongPress() {
            longPressRunnable = () -> {
                if (spec.alternate != null && !spec.alternate.isEmpty()) {
                    consumed = true; dismissPreview(); showAlternates(spec.alternate, keyView);
                } else if (spec.repeatable) {
                    repeating = true; consumed = true;
                    repeatRunnable = new Runnable() {
                        @Override public void run() {
                            if (listener != null) listener.onKey(spec);
                            handler.postDelayed(this, 55);
                        }
                    };
                    handler.post(repeatRunnable);
                }
            };
            handler.postDelayed(longPressRunnable, longPressDelay);
        }

        private void cancelLongPressOnly() {
            if (longPressRunnable != null) handler.removeCallbacks(longPressRunnable);
        }

        private void cancelScheduled() {
            cancelLongPressOnly();
            if (repeatRunnable != null) handler.removeCallbacks(repeatRunnable);
        }
    }

    private View findTextKeyAt(float rawX, float rawY) {
        int[] location = new int[2];
        for (int rowIndex = 0; rowIndex < getChildCount(); rowIndex++) {
            View rowView = getChildAt(rowIndex);
            if (!(rowView instanceof ViewGroup)) continue;
            ViewGroup row = (ViewGroup) rowView;
            for (int index = 0; index < row.getChildCount(); index++) {
                View candidate = row.getChildAt(index);
                Object tag = candidate.getTag();
                if (!(tag instanceof KeySpec) || !isGlideLetter((KeySpec) tag)) continue;
                candidate.getLocationOnScreen(location);
                if (rawX >= location[0] && rawX <= location[0] + candidate.getWidth()
                        && rawY >= location[1] && rawY <= location[1] + candidate.getHeight()) return candidate;
            }
        }
        return null;
    }

    private void clearPressedKeys() {
        if (currentGlideView != null) currentGlideView.setPressed(false);
        currentGlideView = null;
        for (int rowIndex = 0; rowIndex < getChildCount(); rowIndex++) {
            View rowView = getChildAt(rowIndex);
            if (!(rowView instanceof ViewGroup)) continue;
            ViewGroup row = (ViewGroup) rowView;
            for (int index = 0; index < row.getChildCount(); index++) row.getChildAt(index).setPressed(false);
        }
    }

    private static boolean isGlideLetter(KeySpec spec) {
        return spec.action == KeySpec.Action.TEXT && spec.output != null && spec.output.matches("[A-Za-z]");
    }

    private void feedback(View view) {
        if (haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (sound && audioManager != null) audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.45f);
    }

    private boolean shouldPreview(KeySpec spec) {
        return spec.action == KeySpec.Action.TEXT || spec.action == KeySpec.Action.BACKSPACE || spec.action == KeySpec.Action.SHIFT;
    }

    private void showPreview(String label, View anchor) {
        dismissPreview();
        TextView bubble = new TextView(getContext());
        bubble.setText(label); bubble.setTextSize(28); bubble.setGravity(Gravity.CENTER); bubble.setTextColor(palette.text); bubble.setBackground(rounded(palette.special));
        previewPopup = new PopupWindow(bubble, dp(54), dp(66), false); previewPopup.setClippingEnabled(false);
        previewPopup.showAsDropDown(anchor, (anchor.getWidth() - dp(54)) / 2, -anchor.getHeight() - dp(70));
    }

    private void showAlternates(String alternates, View anchor) {
        dismissAlternates();
        LinearLayout row = new LinearLayout(getContext()); row.setOrientation(HORIZONTAL); row.setPadding(dp(4), dp(4), dp(4), dp(4)); row.setBackground(rounded(palette.special));
        for (int i = 0; i < alternates.length();) {
            int codePoint = alternates.codePointAt(i); String value = new String(Character.toChars(codePoint)); i += Character.charCount(codePoint);
            TextView option = new TextView(getContext()); option.setText(value); option.setTextSize(22); option.setTextColor(palette.text); option.setGravity(Gravity.CENTER);
            option.setContentDescription(value); option.setFocusable(true); option.setClickable(true); row.addView(option, new LinearLayout.LayoutParams(dp(46), dp(50)));
            option.setOnClickListener(v -> { if (listener != null) listener.onAlternate(value); dismissAlternates(); });
        }
        alternatePopup = new PopupWindow(row, LayoutParams.WRAP_CONTENT, dp(58), true);
        alternatePopup.setOutsideTouchable(true); alternatePopup.setClippingEnabled(false);
        alternatePopup.showAsDropDown(anchor, 0, -anchor.getHeight() - dp(64));
    }

    private void dismissPreview() { if (previewPopup != null) { previewPopup.dismiss(); previewPopup = null; } }
    private void dismissAlternates() { if (alternatePopup != null) { alternatePopup.dismiss(); alternatePopup = null; } }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
