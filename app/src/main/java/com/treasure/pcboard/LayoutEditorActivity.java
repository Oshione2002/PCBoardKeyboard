package com.treasure.pcboard;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class LayoutEditorActivity extends Activity {
    private CustomLayoutStore store;
    private final List<Spinner> toolbarSpinners = new ArrayList<>();
    private final List<EditText> snippetFields = new ArrayList<>();
    private Spinner modifierSpinner, leftPunctuationSpinner, rightPunctuationSpinner;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new CustomLayoutStore(this);
        CustomLayoutStore.Config config = store.load();

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(24), dp(20), dp(48));
        root.setBackgroundColor(Color.rgb(247,248,251)); scroll.addView(root);

        root.addView(text("PCBoard layout editor", 28, Color.rgb(20,23,30)));
        TextView help = text("Choose the toolbar order, the bottom-left modifier, punctuation keys and quick-text snippets. Changes apply the next time the keyboard opens.", 15, Color.DKGRAY);
        help.setPadding(0, dp(8), 0, dp(18)); root.addView(help);

        root.addView(section("Toolbar order"));
        String[] actions = CustomLayoutStore.TOOLBAR_OPTIONS;
        for (int index = 0; index < 9; index++) {
            String selected = index < config.toolbarActions.size() ? config.toolbarActions.get(index) : actions[index % actions.length];
            Spinner spinner = addSpinnerRow(root, "Slot " + (index + 1), actions, selected);
            toolbarSpinners.add(spinner);
        }

        root.addView(section("Bottom row"));
        modifierSpinner = addSpinnerRow(root, "Modifier key", new String[]{"ctrl","tab","alt","esc"}, config.bottomModifier);
        leftPunctuationSpinner = addSpinnerRow(root, "Left punctuation", new String[]{",","@","/","_","'"}, config.leftPunctuation);
        rightPunctuationSpinner = addSpinnerRow(root, "Right punctuation", new String[]{".","?","!",":",";"}, config.rightPunctuation);

        root.addView(section("Quick text snippets"));
        for (int index = 0; index < 8; index++) {
            EditText input = new EditText(this); input.setSingleLine(true); input.setHint("Snippet " + (index + 1));
            if (index < config.snippets.size()) input.setText(config.snippets.get(index));
            root.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
            snippetFields.add(input);
        }

        Button save = button("Save layout", v -> save()); root.addView(save);
        Button reset = button("Reset layout", v -> {
            store.reset(); Toast.makeText(this, "Layout reset", Toast.LENGTH_SHORT).show(); recreate();
        });
        root.addView(reset);
        setContentView(scroll);
    }

    private void save() {
        List<String> toolbar = new ArrayList<>();
        for (Spinner spinner : toolbarSpinners) toolbar.add(String.valueOf(spinner.getSelectedItem()));
        store.setToolbarActions(toolbar);
        store.setBottomModifier(String.valueOf(modifierSpinner.getSelectedItem()));
        store.setLeftPunctuation(String.valueOf(leftPunctuationSpinner.getSelectedItem()));
        store.setRightPunctuation(String.valueOf(rightPunctuationSpinner.getSelectedItem()));
        List<String> snippets = new ArrayList<>();
        for (EditText field : snippetFields) snippets.add(field.getText().toString());
        store.setSnippets(snippets);
        Toast.makeText(this, "Layout saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private Spinner addSpinnerRow(LinearLayout root, String label, String[] values, String selected) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(label, 16, Color.rgb(30,33,40)); row.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        Spinner spinner = new Spinner(this); ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values); spinner.setAdapter(adapter);
        int position = 0; for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) position = i;
        spinner.setSelection(position); row.addView(spinner, new LinearLayout.LayoutParams(dp(190), dp(52))); root.addView(row);
        return spinner;
    }

    private TextView section(String value) { TextView view = text(value, 19, Color.rgb(55,80,170)); view.setPadding(0, dp(22), 0, dp(6)); return view; }
    private Button button(String value, View.OnClickListener listener) { Button button = new Button(this); button.setText(value); button.setAllCaps(false); button.setOnClickListener(listener); return button; }
    private TextView text(String value, int size, int colour) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(colour); return view; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
