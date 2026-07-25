package com.treasure.pcboard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.graphics.Color;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(48,64,48,48); root.setBackgroundColor(Color.rgb(246,247,250));
        TextView title = new TextView(this); title.setText("PCBoard Keyboard"); title.setTextSize(30); title.setTextColor(Color.BLACK); title.setPadding(0,0,0,20);
        TextView body = new TextView(this); body.setText("A familiar Android keyboard with Ctrl, Tab, suggestions and basic autocorrection.\n\n1. Enable PCBoard Keyboard\n2. Select it as your keyboard\n3. Test it below"); body.setTextSize(17); body.setTextColor(Color.DKGRAY);
        Button enable = new Button(this); enable.setText("Enable keyboard"); enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        Button select = new Button(this); select.setText("Select keyboard"); select.setOnClickListener(v -> ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        EditText test = new EditText(this); test.setHint("Tap here to test PCBoard"); test.setMinLines(5); test.setGravity(48); test.setTextSize(18);
        root.addView(title); root.addView(body); root.addView(enable); root.addView(select); root.addView(test, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);
    }
}
