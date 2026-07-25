package com.treasure.pcboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.*;

public final class ClipboardRepository {
    public static final class Clip {
        public final String text;
        public final long timestamp;
        public final boolean pinned;
        Clip(String text, long timestamp, boolean pinned) {
            this.text = text;
            this.timestamp = timestamp;
            this.pinned = pinned;
        }
    }

    private static final String FILE = "pcboard_clipboard";
    private static final String ITEMS = "items";
    private static final int MAX_ITEMS = 20;
    private static final long EXPIRY_MS = 60L * 60L * 1000L;

    private final Context context;
    private final SharedPreferences prefs;

    public ClipboardRepository(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public synchronized List<Clip> captureCurrent(boolean keepHistory) {
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null && manager.hasPrimaryClip()) {
            ClipData data = manager.getPrimaryClip();
            if (data != null && data.getItemCount() > 0) {
                CharSequence value = data.getItemAt(0).coerceToText(context);
                if (value != null) add(value.toString(), false, keepHistory);
            }
        }
        return list();
    }

    public synchronized void add(String text, boolean pinned, boolean keepHistory) {
        String clean = sanitize(text);
        if (clean.isEmpty()) return;
        List<Clip> clips = listInternal();
        clips.removeIf(item -> item.text.equals(clean));
        if (keepHistory || pinned) clips.add(0, new Clip(clean, System.currentTimeMillis(), pinned));
        save(trim(clips));
    }

    public synchronized List<Clip> list() {
        List<Clip> clips = trim(listInternal());
        save(clips);
        return clips;
    }

    public synchronized void clearUnpinned() {
        List<Clip> pinned = new ArrayList<>();
        for (Clip clip : listInternal()) if (clip.pinned) pinned.add(clip);
        save(pinned);
    }

    public synchronized void clearAll() {
        prefs.edit().clear().apply();
    }

    private List<Clip> listInternal() {
        List<Clip> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(ITEMS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                result.add(new Clip(object.optString("text", ""), object.optLong("time", 0), object.optBoolean("pinned", false)));
            }
        } catch (JSONException ignored) {}
        return result;
    }

    private List<Clip> trim(List<Clip> clips) {
        long now = System.currentTimeMillis();
        List<Clip> result = new ArrayList<>();
        for (Clip clip : clips) {
            if (clip.pinned || now - clip.timestamp <= EXPIRY_MS) result.add(clip);
            if (result.size() >= MAX_ITEMS) break;
        }
        return result;
    }

    private void save(List<Clip> clips) {
        JSONArray array = new JSONArray();
        for (Clip clip : clips) {
            JSONObject object = new JSONObject();
            try {
                object.put("text", clip.text);
                object.put("time", clip.timestamp);
                object.put("pinned", clip.pinned);
                array.put(object);
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString(ITEMS, array.toString()).apply();
    }

    private static String sanitize(String text) {
        if (text == null) return "";
        String clean = text.trim();
        if (clean.length() > 500) clean = clean.substring(0, 500);
        return clean;
    }
}
