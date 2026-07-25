package com.treasure.pcboard;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24), dp(32), dp(24), dp(40)); root.setBackgroundColor(Color.rgb(247,248,251));
        scroll.addView(root);

        TextView title = text("PCBoard Keyboard", 32, Color.rgb(20,23,30)); root.addView(title);
        TextView subtitle = text("Modern Android typing with Ctrl, Tab and productivity profiles.", 17, Color.DKGRAY); subtitle.setPadding(0, dp(8), 0, dp(22)); root.addView(subtitle);

        root.addView(card("1. Enable the keyboard", "Android requires you to enable every third-party keyboard manually.", "Open input settings", v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));
        root.addView(card("2. Select PCBoard", "Choose PCBoard Keyboard from Android's keyboard picker.", "Choose keyboard", v -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.showInputMethodPicker();
        }));
        root.addView(card("3. Configure it", "Adjust prediction, autocorrection, layout, height, feedback, privacy and English variant.", "Open settings", v -> startActivity(new Intent(this, SettingsActivity.class))));

        TextView testLabel = text("Test the keyboard", 19, Color.rgb(55,80,170)); testLabel.setPadding(0, dp(22), 0, dp(6)); root.addView(testLabel);
        EditText test = new EditText(this); test.setHint("Tap here, then select PCBoard Keyboard"); test.setMinLines(7); test.setGravity(Gravity.TOP); test.setTextSize(18); test.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.addView(test, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));

        TextView privacy = text("Privacy: PCBoard has no internet permission. Learning and suggestions are disabled in password fields and whenever an app requests no personalised learning.", 14, Color.DKGRAY);
        privacy.setPadding(0, dp(20), 0, 0); root.addView(privacy);
        setContentView(scroll);
    }

    private LinearLayout card(String title, String body, String action, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16), dp(14), dp(16), dp(14)); card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); params.setMargins(0, 0, 0, dp(12)); card.setLayoutParams(params);
        card.addView(text(title, 19, Color.rgb(25,28,36))); TextView details = text(body, 15, Color.DKGRAY); details.setPadding(0, dp(5), 0, dp(8)); card.addView(details);
        Button button = new Button(this); button.setText(action); button.setAllCaps(false); button.setOnClickListener(listener); card.addView(button); return card;
    }

    private TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
