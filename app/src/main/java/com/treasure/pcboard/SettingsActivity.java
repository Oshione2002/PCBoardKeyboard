package com.treasure.pcboard;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

public final class SettingsActivity extends Activity {
    private static final int AUDIO_PERMISSION_REQUEST = 2001;
    private KeyboardPreferences preferences;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = new KeyboardPreferences(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(22), dp(20), dp(48)); root.setBackgroundColor(Color.rgb(247,248,251));
        scroll.addView(root);

        TextView title = text("PCBoard 2.0 settings", 28, Color.rgb(20,23,30)); title.setPadding(0,0,0,dp(12)); root.addView(title);
        root.addView(text("Smart typing", 19, Color.rgb(55,80,170)));
        addSwitch(root, "Predictive suggestions", KeyboardPreferences.PREDICTIONS, true);
        addSwitch(root, "Autocorrection", KeyboardPreferences.AUTOCORRECT, true);
        addSwitch(root, "Glide typing", KeyboardPreferences.GLIDE_TYPING, true);
        addSwitch(root, "Automatic capitalisation", KeyboardPreferences.AUTO_CAP, true);
        addSwitch(root, "Double-space full stop", KeyboardPreferences.DOUBLE_SPACE, true);
        addSwitch(root, "Emoji suggestions", KeyboardPreferences.EMOJI_SUGGESTIONS, true);

        root.addView(section("Layout and feedback"));
        addSwitch(root, "Number row", KeyboardPreferences.NUMBER_ROW, true);
        addSwitch(root, "Long-press letters for symbols", KeyboardPreferences.LONG_PRESS_SYMBOLS, true);
        addSwitch(root, "Show symbol hints", KeyboardPreferences.SYMBOL_HINTS, true);
        addSwitch(root, "Key preview popup", KeyboardPreferences.KEY_POPUP, true);
        addSwitch(root, "Haptic feedback", KeyboardPreferences.HAPTIC, true);
        addSwitch(root, "Keypress sound", KeyboardPreferences.SOUND, false);
        addSpinner(root, "Theme", KeyboardPreferences.THEME, new String[]{"system","dark","amoled","light"}, preferences.theme());
        addSpinner(root, "Keyboard width", KeyboardPreferences.ONE_HANDED, new String[]{"off","left","right","compact"}, preferences.oneHanded());
        addSeek(root, "Keyboard height", KeyboardPreferences.HEIGHT, 90, 180, preferences.heightPercent(), "%");
        addSeek(root, "Bottom safety gap", KeyboardPreferences.BOTTOM_GAP, 0, 56, preferences.bottomGap(), " dp");
        addSeek(root, "Long-press delay", KeyboardPreferences.LONG_PRESS_DELAY, 220, 700, preferences.longPressDelay(), " ms");
        root.addView(button("Open layout and shortcut editor", v -> startActivity(new Intent(this, LayoutEditorActivity.class))));

        root.addView(section("Voice and translation"));
        addSwitch(root, "Voice typing", KeyboardPreferences.VOICE_TYPING, true);
        root.addView(button(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                ? "Microphone permission granted" : "Grant microphone permission", v -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_REQUEST)));
        addMappedSpinner(root, "Translation target", KeyboardPreferences.TRANSLATION_TARGET,
                new String[]{"French","Spanish","German","Portuguese","Arabic","Hausa"},
                new String[]{"fr","es","de","pt","ar","ha"}, preferences.translationTarget());
        root.addView(button("Open device translation settings", v -> openTranslationSettings()));

        root.addView(section("Handwriting"));
        root.addView(button("Train handwriting gestures", v -> startActivity(new Intent(this, HandwritingTrainingActivity.class))));
        TextView handwritingNote = text("Handwriting is trained locally by you, so it does not require a downloadable recognition model.", 14, Color.DKGRAY);
        root.addView(handwritingNote);

        root.addView(section("Language and profiles"));
        addSpinner(root, "English variant", KeyboardPreferences.DIALECT,
                new String[]{"Nigerian English","British English","Canadian English","US English"}, preferences.dialect());
        addSwitch(root, "Detect terminal, coding and spreadsheet apps", KeyboardPreferences.AUTO_PROFILE, true);

        root.addView(section("Privacy"));
        addSwitch(root, "Incognito mode", KeyboardPreferences.INCOGNITO, false);
        addSwitch(root, "Clipboard history", KeyboardPreferences.CLIPBOARD_HISTORY, true);
        root.addView(button("Clear learned words", v -> { new PersonalLexicon(this).clear(); Toast.makeText(this, "Learned words cleared", Toast.LENGTH_SHORT).show(); }));
        root.addView(button("Clear clipboard history", v -> { new ClipboardRepository(this).clearAll(); Toast.makeText(this, "Clipboard history cleared", Toast.LENGTH_SHORT).show(); }));

        root.addView(section("Keyboard setup"));
        root.addView(button("Enable PCBoard Keyboard", v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));
        root.addView(button("Select active keyboard", v -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.showInputMethodPicker();
        }));

        TextView note = text("Settings are stored on this device. Password fields and apps that request no personalised learning automatically disable learning and suggestions.", 14, Color.DKGRAY);
        note.setPadding(0, dp(18), 0, 0); root.addView(note);
        setContentView(scroll);
    }

    private void openTranslationSettings() {
        if (Build.VERSION.SDK_INT < 31) { Toast.makeText(this, "Translation settings require Android 12 or later", Toast.LENGTH_LONG).show(); return; }
        PendingIntent pendingIntent = new SystemTranslationController(this, new EmptyTranslationListener()).settingsIntent();
        if (pendingIntent == null) { Toast.makeText(this, "This device has no translation settings screen", Toast.LENGTH_LONG).show(); return; }
        try { pendingIntent.send(); } catch (PendingIntent.CanceledException error) { Toast.makeText(this, "Could not open translation settings", Toast.LENGTH_SHORT).show(); }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Toast.makeText(this, granted ? "Voice typing enabled" : "Microphone permission was not granted", Toast.LENGTH_LONG).show();
            recreate();
        }
    }

    private void addSwitch(LinearLayout root, String label, String key, boolean fallback) {
        Switch control = new Switch(this);
        control.setText(label); control.setTextSize(16); control.setTextColor(Color.rgb(30,33,40)); control.setPadding(dp(4), dp(8), dp(4), dp(8));
        control.setChecked(preferences.raw().getBoolean(key, fallback));
        control.setOnCheckedChangeListener((button, checked) -> preferences.setBoolean(key, checked));
        root.addView(control, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addSpinner(LinearLayout root, String label, String key, String[] values, String selected) {
        Spinner spinner = spinnerRow(root, label, values, selected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { preferences.setString(key, values[position]); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void addMappedSpinner(LinearLayout root, String label, String key, String[] labels, String[] values, String selectedValue) {
        int selected = 0; for (int index = 0; index < values.length; index++) if (values[index].equals(selectedValue)) selected = index;
        Spinner spinner = spinnerRow(root, label, labels, labels[selected]);
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { preferences.setString(key, values[position]); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private Spinner spinnerRow(LinearLayout root, String label, String[] values, String selected) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(4), dp(6), dp(4), dp(6));
        TextView title = text(label, 16, Color.rgb(30,33,40)); row.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        Spinner spinner = new Spinner(this); ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values); spinner.setAdapter(adapter);
        int position = 0; for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) position = i; spinner.setSelection(position);
        row.addView(spinner, new LinearLayout.LayoutParams(dp(180), dp(52))); root.addView(row); return spinner;
    }

    private void addSeek(LinearLayout root, String label, String key, int min, int max, int current, String suffix) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(4), dp(6), dp(4), dp(6));
        TextView value = text(label + ": " + current + suffix, 16, Color.rgb(30,33,40)); box.addView(value);
        SeekBar seek = new SeekBar(this); seek.setMax(max - min); seek.setProgress(current - min);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { int actual = min + progress; value.setText(label + ": " + actual + suffix); preferences.setInt(key, actual); }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        box.addView(seek); root.addView(box);
    }

    private TextView section(String text) { TextView view = text(text, 19, Color.rgb(55,80,170)); view.setPadding(0, dp(22), 0, dp(4)); return view; }
    private Button button(String label, View.OnClickListener listener) { Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setOnClickListener(listener); return button; }
    private TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class EmptyTranslationListener implements SystemTranslationController.Listener {
        @Override public void onTranslationState(String message) {}
        @Override public void onTranslationResult(String original, String translated) {}
        @Override public void onTranslationError(String message) {}
    }
}
