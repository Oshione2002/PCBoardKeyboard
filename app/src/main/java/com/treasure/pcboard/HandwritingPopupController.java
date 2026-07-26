package com.treasure.pcboard;

import android.content.*;
import android.graphics.Color;
import android.gesture.GestureOverlayView;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

public final class HandwritingPopupController {
    public interface Listener {
        void onHandwritingText(String text);
        void onHandwritingStatus(String status);
    }

    private final Context context;
    private final HandwritingStore store;
    private final Listener listener;
    private PopupWindow popup;

    public HandwritingPopupController(Context context, HandwritingStore store, Listener listener) {
        this.context = context;
        this.store = store;
        this.listener = listener;
    }

    public void show(View anchor, int background, int foreground, int accent) {
        dismiss();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable rootBackground = new GradientDrawable();
        rootBackground.setColor(background); rootBackground.setCornerRadius(dp(16));
        root.setBackground(rootBackground);

        TextView status = new TextView(context);
        status.setText(store.entries().isEmpty()
                ? "No trained symbols yet. Tap Train to add one."
                : "Draw a trained letter, word, symbol or shortcut");
        status.setTextColor(foreground); status.setTextSize(15); status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        GestureOverlayView pad = new GestureOverlayView(context);
        pad.setGestureColor(accent); pad.setUncertainGestureColor(accent);
        pad.setGestureStrokeWidth(dp(4)); pad.setFadeEnabled(true); pad.setFadeOffset(650);
        GradientDrawable padBackground = new GradientDrawable();
        padBackground.setColor(Color.argb(55, 127, 127, 127)); padBackground.setCornerRadius(dp(12));
        pad.setBackground(padBackground);
        root.addView(pad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));

        pad.addOnGesturePerformedListener((overlay, gesture) -> {
            HandwritingStore.Match match = store.recognise(gesture);
            if (match == null) {
                status.setText("No confident match. Train this shape in settings.");
                listener.onHandwritingStatus("Handwriting shape was not recognised");
            } else {
                listener.onHandwritingText(match.text);
                status.setText("Inserted: " + match.text);
            }
        });

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button clear = button("Clear", foreground); clear.setOnClickListener(v -> pad.clear(false));
        Button train = button("Train", foreground); train.setOnClickListener(v -> {
            Intent intent = new Intent(context, HandwritingTrainingActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            dismiss();
        });
        Button close = button("Close", foreground); close.setOnClickListener(v -> dismiss());
        actions.addView(clear); actions.addView(train); actions.addView(close);
        root.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        int width = Math.round(context.getResources().getDisplayMetrics().widthPixels * .94f);
        popup = new PopupWindow(root, width, dp(250), true);
        popup.setOutsideTouchable(true); popup.setClippingEnabled(false);
        popup.showAtLocation(anchor, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(74));
    }

    public void dismiss() {
        if (popup != null) { popup.dismiss(); popup = null; }
    }

    private Button button(String text, int colour) {
        Button button = new Button(context); button.setText(text); button.setAllCaps(false); button.setTextColor(colour);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
