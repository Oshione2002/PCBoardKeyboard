package com.treasure.pcboard;

import android.content.Context;
import android.gesture.*;
import java.util.*;

public final class HandwritingStore {
    public static final class Match {
        public final String text;
        public final double score;
        Match(String text, double score) { this.text = text; this.score = score; }
    }

    private static final String FILE = "pcboard_handwriting_gestures";
    private final GestureLibrary library;

    public HandwritingStore(Context context) {
        library = GestureLibraries.fromPrivateFile(context, FILE);
        library.load();
    }

    public Match recognise(Gesture gesture) {
        if (gesture == null) return null;
        ArrayList<Prediction> predictions = library.recognize(gesture);
        if (predictions == null || predictions.isEmpty()) return null;
        Prediction best = predictions.get(0);
        return best.score < 1.5 ? null : new Match(best.name, best.score);
    }

    public boolean add(String text, Gesture gesture) {
        String label = text == null ? "" : text.trim();
        if (label.isEmpty() || gesture == null) return false;
        library.addGesture(label, gesture);
        return library.save();
    }

    public boolean remove(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        library.removeEntry(text.trim());
        return library.save();
    }

    public boolean clear() {
        for (String entry : new ArrayList<>(library.getGestureEntries())) library.removeEntry(entry);
        return library.save();
    }

    public List<String> entries() {
        List<String> result = new ArrayList<>(library.getGestureEntries());
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }
}
