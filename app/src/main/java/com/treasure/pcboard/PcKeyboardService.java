package com.treasure.pcboard;

import android.inputmethodservice.InputMethodService;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import java.util.*;

public class PcKeyboardService extends InputMethodService {
    private LinearLayout root, suggestionBar, keyboard;
    private boolean shift = false, ctrl = false, symbols = false;
    private long lastShiftTap = 0;
    private final String[] common = {"the","to","and","you","that","it","is","for","of","in","this","with","have","on","be","are","not","can","will","keyboard","android","hello","thanks","please","good","yes","no"};

    @Override public View onCreateInputView() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(22,24,29));
        suggestionBar = new LinearLayout(this); suggestionBar.setGravity(Gravity.CENTER); suggestionBar.setPadding(6,6,6,6);
        root.addView(suggestionBar, new LinearLayout.LayoutParams(-1, dp(48)));
        keyboard = new LinearLayout(this); keyboard.setOrientation(LinearLayout.VERTICAL); root.addView(keyboard);
        rebuild(); updateSuggestions(); return root;
    }

    private void rebuild() {
        keyboard.removeAllViews();
        if (symbols) {
            addRow(new String[]{"1","2","3","4","5","6","7","8","9","0"});
            addRow(new String[]{"@","#","$","%","&","-","+","(",")","/"});
            addRow(new String[]{"*","\"","'",":",";","!","?","⌫"});
            addBottom(true);
        } else {
            addRow(new String[]{"q","w","e","r","t","y","u","i","o","p"});
            addRow(new String[]{"⇥","a","s","d","f","g","h","j","k","l"});
            addRow(new String[]{"⇧","z","x","c","v","b","n","m","⌫"});
            addBottom(false);
        }
    }

    private void addBottom(boolean symbolMode) {
        LinearLayout row = row();
        addKey(row, symbolMode ? "ABC" : "?123", 1.25f);
        addKey(row, "Ctrl", 1.0f);
        addKey(row, ",", .75f);
        addKey(row, "space", 4.2f);
        addKey(row, ".", .75f);
        addKey(row, "↵", 1.25f);
        keyboard.addView(row, new LinearLayout.LayoutParams(-1, dp(58)));
    }

    private void addRow(String[] keys) {
        LinearLayout row = row();
        for (String k : keys) addKey(row, k, (k.equals("⇥") || k.equals("⇧") || k.equals("⌫")) ? 1.25f : 1f);
        keyboard.addView(row, new LinearLayout.LayoutParams(-1, dp(58)));
    }
    private LinearLayout row(){ LinearLayout r=new LinearLayout(this); r.setGravity(Gravity.CENTER); r.setPadding(4,2,4,2); return r; }

    private void addKey(LinearLayout row, String label, float weight) {
        TextView key = new TextView(this); key.setText(label); key.setGravity(Gravity.CENTER); key.setTextSize(label.length()>2?16:24); key.setTextColor(Color.WHITE); key.setClickable(true);
        GradientDrawable bg = new GradientDrawable(); bg.setColor((label.equals("Ctrl")&&ctrl)||(label.equals("⇧")&&shift)?Color.rgb(76,112,220):Color.rgb(48,51,58)); bg.setCornerRadius(dp(7)); key.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,-1,weight); lp.setMargins(3,3,3,3); row.addView(key,lp);
        key.setOnClickListener(v -> press(label));
    }

    private void press(String key) {
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return;
        switch(key) {
            case "?123": symbols=true; rebuild(); return;
            case "ABC": symbols=false; rebuild(); return;
            case "⇧": long now=System.currentTimeMillis(); shift = now-lastShiftTap<350 ? true : !shift; lastShiftTap=now; rebuild(); return;
            case "Ctrl": ctrl=!ctrl; rebuild(); return;
            case "⇥": sendKey(ic, KeyEvent.KEYCODE_TAB, ctrl?KeyEvent.META_CTRL_ON:0); ctrl=false; rebuild(); return;
            case "⌫": ic.deleteSurroundingText(1,0); updateSuggestions(); return;
            case "space": commitAutocorrect(ic); ic.commitText(" ",1); updateSuggestions(); return;
            case "↵": if(!ic.performEditorAction(EditorInfo.IME_ACTION_DONE)) sendKey(ic,KeyEvent.KEYCODE_ENTER,0); return;
            default:
                if (ctrl && key.length()==1) { sendKey(ic, KeyEvent.keyCodeFromString("KEYCODE_"+key.toUpperCase()), KeyEvent.META_CTRL_ON); ctrl=false; rebuild(); return; }
                String out = shift ? key.toUpperCase() : key; ic.commitText(out,1); if(shift){shift=false; rebuild();} updateSuggestions();
        }
    }

    private void sendKey(InputConnection ic,int code,int meta){ long t=System.currentTimeMillis(); ic.sendKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,code,0,meta)); ic.sendKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_UP,code,0,meta)); }

    private void updateSuggestions() {
        if (suggestionBar == null) return; suggestionBar.removeAllViews();
        String prefix = currentWord(); List<String> picks = new ArrayList<>();
        if (!prefix.isEmpty()) for(String w:common) if(w.startsWith(prefix.toLowerCase()) && !w.equals(prefix.toLowerCase())) picks.add(w);
        if (picks.isEmpty()) picks.addAll(Arrays.asList("the","and","you"));
        for(int i=0;i<Math.min(3,picks.size());i++){ String s=picks.get(i); Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setBackgroundColor(Color.TRANSPARENT); suggestionBar.addView(b,new LinearLayout.LayoutParams(0,-1,1)); b.setOnClickListener(v->replaceCurrentWord(s)); }
    }

    private String currentWord(){ InputConnection ic=getCurrentInputConnection(); if(ic==null)return ""; CharSequence before=ic.getTextBeforeCursor(40,0); if(before==null)return ""; String[] p=before.toString().split("[^A-Za-z']+"); return p.length==0?"":p[p.length-1]; }
    private void replaceCurrentWord(String word){ InputConnection ic=getCurrentInputConnection(); String old=currentWord(); if(ic!=null){ic.deleteSurroundingText(old.length(),0);ic.commitText(word+" ",1);} updateSuggestions(); }
    private void commitAutocorrect(InputConnection ic){ String w=currentWord().toLowerCase(); Map<String,String> m=new HashMap<>(); m.put("teh","the");m.put("adn","and");m.put("recieve","receive");m.put("dont","don't");m.put("cant","can't");m.put("youre","you're"); String c=m.get(w); if(c!=null){ic.deleteSurroundingText(w.length(),0);ic.commitText(c,1);} }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }
}
