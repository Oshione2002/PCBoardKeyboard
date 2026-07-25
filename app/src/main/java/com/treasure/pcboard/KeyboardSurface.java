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
    private boolean showPopup = true, haptic = true, sound = false;
    private int longPressDelay = 420;
    private PopupWindow previewPopup;
    private PopupWindow alternatePopup;
    private Palette palette;
    private Set<KeySpec.Action> activeActions = Collections.emptySet();

    public KeyboardSurface(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void configure(Listener listener, boolean showPopup, boolean haptic, boolean sound, int longPressDelay) {
        this.listener = listener;
        this.showPopup = showPopup;
        this.haptic = haptic;
        this.sound = sound;
        this.longPressDelay = longPressDelay;
    }

    public void render(List<List<KeySpec>> rows, Palette palette, Set<KeySpec.Action> activeActions, int heightPercent) {
        this.palette = palette;
        this.activeActions = activeActions == null ? Collections.emptySet() : activeActions;
        removeAllViews();
        setBackgroundColor(palette.background);
        int baseHeight = Math.round(dp(54) * heightPercent / 100f);
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
        TextView key = new TextView(getContext());
        key.setText(spec.label);
        key.setTextColor(palette.text);
        key.setTextSize(spec.label.length() > 4 ? 13 : spec.label.length() > 2 ? 15 : 22);
        key.setGravity(Gravity.CENTER);
        key.setMinWidth(dp(42));
        key.setMinHeight(dp(48));
        key.setPadding(dp(2), 0, dp(2), 0);
        key.setContentDescription(spec.accessibilityLabel);
        key.setFocusable(true);
        key.setClickable(true);
        key.setBackground(keyBackground(spec));
        key.setOnTouchListener(new KeyTouchListener(spec, key));
        return key;
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
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(7));
        return drawable;
    }

    private LinearLayout.LayoutParams keyParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
        params.setMargins(dp(2), dp(3), dp(2), dp(3));
        return params;
    }

    private final class KeyTouchListener implements OnTouchListener {
        private final KeySpec spec;
        private final View keyView;
        private float downX, downY, lastX;
        private boolean consumed, repeating;
        private Runnable longPressRunnable;
        private Runnable repeatRunnable;

        KeyTouchListener(KeySpec spec, View keyView) {
            this.spec = spec;
            this.keyView = keyView;
        }

        @Override public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = lastX = event.getRawX(); downY = event.getRawY(); consumed = false; repeating = false;
                    feedback(view);
                    if (showPopup && shouldPreview(spec)) showPreview(spec.label, keyView);
                    scheduleLongPress();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (spec.action == KeySpec.Action.SPACE) {
                        float step = dp(22);
                        float delta = event.getRawX() - lastX;
                        if (Math.abs(delta) >= step) {
                            int direction = delta > 0 ? 1 : -1;
                            int count = Math.max(1, (int)(Math.abs(delta) / step));
                            for (int i = 0; i < count; i++) if (listener != null) listener.onSpaceCursor(direction);
                            lastX = event.getRawX(); consumed = true;
                        }
                    } else if (spec.action == KeySpec.Action.BACKSPACE && dx < -dp(48)) {
                        cancelScheduled();
                        if (listener != null) listener.onDeleteWord();
                        downX = event.getRawX(); consumed = true;
                    }
                    if (dy > dp(70)) {
                        cancelScheduled();
                        if (listener != null) listener.onHide();
                        consumed = true;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelScheduled();
                    dismissPreview();
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && !consumed && !repeating && listener != null) listener.onKey(spec);
                    return true;
                default:
                    return false;
            }
        }

        private void scheduleLongPress() {
            longPressRunnable = () -> {
                if (spec.alternate != null && !spec.alternate.isEmpty()) {
                    consumed = true;
                    dismissPreview();
                    showAlternates(spec.alternate, keyView);
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

        private void cancelScheduled() {
            if (longPressRunnable != null) handler.removeCallbacks(longPressRunnable);
            if (repeatRunnable != null) handler.removeCallbacks(repeatRunnable);
        }
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
        bubble.setText(label); bubble.setTextSize(28); bubble.setGravity(Gravity.CENTER); bubble.setTextColor(palette.text);
        bubble.setBackground(rounded(palette.special));
        previewPopup = new PopupWindow(bubble, dp(54), dp(66), false);
        previewPopup.setClippingEnabled(false);
        previewPopup.showAsDropDown(anchor, (anchor.getWidth() - dp(54)) / 2, -anchor.getHeight() - dp(70));
    }

    private void showAlternates(String alternates, View anchor) {
        dismissAlternates();
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL); row.setPadding(dp(4), dp(4), dp(4), dp(4)); row.setBackground(rounded(palette.special));
        for (int i = 0; i < alternates.length();) {
            int codePoint = alternates.codePointAt(i);
            String value = new String(Character.toChars(codePoint));
            i += Character.charCount(codePoint);
            TextView option = new TextView(getContext());
            option.setText(value); option.setTextSize(22); option.setTextColor(palette.text); option.setGravity(Gravity.CENTER);
            option.setContentDescription(value); option.setFocusable(true); option.setClickable(true);
            row.addView(option, new LinearLayout.LayoutParams(dp(46), dp(50)));
            option.setOnClickListener(v -> {
                if (listener != null) listener.onAlternate(value);
                dismissAlternates();
            });
        }
        alternatePopup = new PopupWindow(row, LayoutParams.WRAP_CONTENT, dp(58), true);
        alternatePopup.setOutsideTouchable(true); alternatePopup.setClippingEnabled(false);
        alternatePopup.showAsDropDown(anchor, 0, -anchor.getHeight() - dp(64));
    }

    private void dismissPreview() {
        if (previewPopup != null) { previewPopup.dismiss(); previewPopup = null; }
    }

    private void dismissAlternates() {
        if (alternatePopup != null) { alternatePopup.dismiss(); alternatePopup = null; }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
