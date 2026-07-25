package com.treasure.pcboard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.*;

public final class PersonalLexicon {
    private static final String FILE = "pcboard_personal_lexicon";
    private static final String WORDS = "words";
    private static final String BIGRAMS = "bigrams";
    private static final int MAX_WORDS = 1200;
    private static final int MAX_BIGRAMS = 1800;

    private final SharedPreferences prefs;
    private final Map<String, Integer> words = new HashMap<>();
    private final Map<String, Integer> bigrams = new HashMap<>();

    public PersonalLexicon(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        loadObject(prefs.getString(WORDS, "{}"), words);
        loadObject(prefs.getString(BIGRAMS, "{}"), bigrams);
    }

    public synchronized void learn(String word, String previousWord) {
        String clean = clean(word);
        if (clean.length() < 2 || clean.length() > 32) return;
        if (words.containsKey(clean) || words.size() < MAX_WORDS) {
            words.put(clean, Math.min(999, words.getOrDefault(clean, 0) + 1));
        }
        String previous = clean(previousWord);
        if (!previous.isEmpty()) {
            String key = previous + "\t" + clean;
            if (bigrams.containsKey(key) || bigrams.size() < MAX_BIGRAMS) {
                bigrams.put(key, Math.min(999, bigrams.getOrDefault(key, 0) + 1));
            }
        }
        save();
    }

    public synchronized int wordBoost(String word) {
        return words.getOrDefault(clean(word), 0);
    }

    public synchronized int bigramBoost(String previous, String word) {
        return bigrams.getOrDefault(clean(previous) + "\t" + clean(word), 0);
    }

    public synchronized List<String> matching(String prefix, int limit) {
        String p = clean(prefix);
        List<Map.Entry<String,Integer>> entries = new ArrayList<>();
        for (Map.Entry<String,Integer> entry : words.entrySet()) {
            if (entry.getKey().startsWith(p)) entries.add(entry);
        }
        entries.sort((a,b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> result = new ArrayList<>();
        for (Map.Entry<String,Integer> entry : entries) {
            result.add(entry.getKey());
            if (result.size() >= limit) break;
        }
        return result;
    }

    public synchronized List<String> nextWords(String previous, int limit) {
        String prefix = clean(previous) + "\t";
        List<Map.Entry<String,Integer>> entries = new ArrayList<>();
        for (Map.Entry<String,Integer> entry : bigrams.entrySet()) {
            if (entry.getKey().startsWith(prefix)) entries.add(entry);
        }
        entries.sort((a,b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> result = new ArrayList<>();
        for (Map.Entry<String,Integer> entry : entries) {
            result.add(entry.getKey().substring(prefix.length()));
            if (result.size() >= limit) break;
        }
        return result;
    }

    public synchronized void remove(String word) {
        words.remove(clean(word));
        save();
    }

    public synchronized void clear() {
        words.clear();
        bigrams.clear();
        prefs.edit().clear().apply();
    }

    private void save() {
        prefs.edit().putString(WORDS, toJson(words)).putString(BIGRAMS, toJson(bigrams)).apply();
    }

    private static void loadObject(String raw, Map<String,Integer> target) {
        try {
            JSONObject object = new JSONObject(raw);
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                target.put(key, object.optInt(key, 0));
            }
        } catch (JSONException ignored) {
            target.clear();
        }
    }

    private static String toJson(Map<String,Integer> map) {
        JSONObject object = new JSONObject();
        for (Map.Entry<String,Integer> entry : map.entrySet()) {
            try { object.put(entry.getKey(), entry.getValue()); } catch (JSONException ignored) {}
        }
        return object.toString();
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z'’-]", "").trim();
    }
}
