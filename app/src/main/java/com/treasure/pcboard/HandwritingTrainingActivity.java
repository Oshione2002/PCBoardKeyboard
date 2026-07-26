package com.treasure.pcboard;

import android.app.Activity;
import android.graphics.Color;
import android.gesture.Gesture;
import android.gesture.GestureOverlayView;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class HandwritingTrainingActivity extends Activity {
    private HandwritingStore store;
    private GestureOverlayView pad;
    private EditText label;
    private TextView entries;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new HandwritingStore(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(24), dp(20), dp(40));
        root.setBackgroundColor(Color.rgb(247,248,251)); scroll.addView(root);

        TextView title = text("Train handwriting", 28, Color.rgb(20,23,30)); root.addView(title);
        TextView help = text("Draw a letter, word, symbol or shortcut several times, enter what it should insert, then save each sample. This local training avoids cloud or downloaded recognition models.", 15, Color.DKGRAY);
        help.setPadding(0, dp(8), 0, dp(14)); root.addView(help);

        label = new EditText(this); label.setHint("Text to insert, for example: A, @, thank you"); label.setSingleLine(true); root.addView(label);

        pad = new GestureOverlayView(this); pad.setGestureColor(Color.rgb(66,105,220)); pad.setGestureStrokeWidth(dp(5));
        pad.setFadeEnabled(false); pad.setBackgroundColor(Color.WHITE);
        root.addView(pad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));

        LinearLayout buttons = new LinearLayout(this); buttons.setGravity(Gravity.CENTER);
        Button clear = button("Clear drawing"); clear.setOnClickListener(v -> pad.clear(false));
        Button save = button("Save sample"); save.setOnClickListener(v -> saveSample());
        buttons.addView(clear); buttons.addView(save); root.addView(buttons);

        entries = text("", 15, Color.rgb(35,38,45)); entries.setPadding(0, dp(18), 0, dp(8)); root.addView(entries);
        Button clearAll = button("Delete all trained handwriting"); clearAll.setOnClickListener(v -> {
            store.clear(); updateEntries(); Toast.makeText(this, "Handwriting training cleared", Toast.LENGTH_SHORT).show();
        });
        root.addView(clearAll);
        updateEntries();
        setContentView(scroll);
    }

    private void saveSample() {
        String value = label.getText().toString().trim();
        Gesture gesture = pad.getGesture();
        if (value.isEmpty()) { Toast.makeText(this, "Enter the text this drawing should insert", Toast.LENGTH_SHORT).show(); return; }
        if (gesture == null || gesture.getLength() < dp(40)) { Toast.makeText(this, "Draw a clearer symbol first", Toast.LENGTH_SHORT).show(); return; }
        if (store.add(value, gesture)) {
            Toast.makeText(this, "Sample saved. Add two or three more samples for better recognition.", Toast.LENGTH_LONG).show();
            pad.clear(false); updateEntries();
        } else Toast.makeText(this, "The sample could not be saved", Toast.LENGTH_SHORT).show();
    }

    private void updateEntries() {
        List<String> values = store.entries();
        entries.setText(values.isEmpty() ? "No trained entries" : "Trained entries: " + String.join(", ", values));
    }

    private Button button(String label) { Button button = new Button(this); button.setText(label); button.setAllCaps(false); return button; }
    private TextView text(String value, int size, int colour) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(colour); return view; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
