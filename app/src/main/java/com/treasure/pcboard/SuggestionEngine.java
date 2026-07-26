package com.treasure.pcboard;

import android.content.Context;
import java.io.*;
import java.util.*;

public final class SuggestionEngine {
    private static final class Candidate {
        final String word;
        final double score;
        Candidate(String word, double score) { this.word = word; this.score = score; }
    }

    private final List<String> words = new ArrayList<>();
    private final Map<String,Integer> rank = new HashMap<>();
    private final Map<String,List<String>> bigrams = new HashMap<>();
    private final Map<String,List<String>> emoji = new HashMap<>();
    private final Set<String> terminalWords = new LinkedHashSet<>(Arrays.asList(
            "sudo","ssh","cd","ls","mkdir","grep","chmod","chown","curl","wget","git","docker","npm","python","bash","clear","exit","localhost"));
    private final Set<String> codingWords = new LinkedHashSet<>(Arrays.asList(
            "function","class","return","import","public","private","static","const","let","var","boolean","string","array","object","null","true","false","async","await","interface","extends"));
    private final Set<String> sheetWords = new LinkedHashSet<>(Arrays.asList(
            "sum","average","count","filter","sort","lookup","formula","column","row","cell","range","total","subtotal","percentage","variance","forecast"));
    private final Set<String> pidginWords = new LinkedHashSet<>(Arrays.asList(
            "abeg","abi","dey","wetin","wahala","sabi","una","oga","pikin","sha","na","no wahala","how far","ehen","chop","gist","jare","sef"));

    public SuggestionEngine(Context context) {
        loadWords(context);
        loadBigrams();
        loadEmoji();
    }

    public List<String> suggest(String prefix, String previous, PersonalLexicon personal,
                                ProfileManager.Profile profile, String dialect, int limit) {
        String cleanPrefix = clean(prefix);
        String cleanPrevious = clean(previous);
        Map<String,Double> scores = new HashMap<>();

        if (cleanPrefix.isEmpty()) {
            addNextWords(scores, cleanPrevious, personal);
        } else {
            for (String learned : personal.matching(cleanPrefix, Math.max(limit * 3, 12))) {
                addScore(scores, learned, 2_000_000 + personal.wordBoost(learned) * 20_000);
            }
            for (int i = 0; i < words.size(); i++) {
                String word = words.get(i);
                if (word.startsWith(cleanPrefix)) {
                    double score = 1_200_000 - i * 140.0;
                    score += personal.wordBoost(word) * 16_000.0;
                    score += personal.bigramBoost(cleanPrevious, word) * 22_000.0;
                    if (word.equals(cleanPrefix)) score += 120_000;
                    addScore(scores, applyDialect(word, dialect), score);
                }
            }
            if (cleanPrefix.length() >= 3 && cleanPrefix.length() <= 18) {
                for (int i = 0; i < words.size(); i++) {
                    String word = words.get(i);
                    if (Math.abs(word.length() - cleanPrefix.length()) > 2) continue;
                    int distance = damerauLevenshtein(cleanPrefix, word, 2);
                    if (distance <= 2) {
                        double score = 650_000 - distance * 180_000 - i * 35.0;
                        if (isKeyboardNearMiss(cleanPrefix, word)) score += 80_000;
                        score += personal.wordBoost(word) * 12_000.0;
                        addScore(scores, applyDialect(word, dialect), score);
                    }
                }
            }
        }

        addProfileWords(scores, cleanPrefix, profile);
        if (dialect != null && dialect.toLowerCase(Locale.ROOT).contains("nigerian")) {
            for (String word : pidginWords) {
                if (cleanPrefix.isEmpty() || word.startsWith(cleanPrefix)) addScore(scores, word, 900_000);
            }
        }
        return ranked(scores, limit);
    }

    public List<String> glideCandidates(List<String> path, PersonalLexicon personal,
                                        ProfileManager.Profile profile, String dialect, int limit) {
        String signature = collapsePath(path);
        if (signature.length() < 2) return Collections.emptyList();
        Map<String,Double> scores = new HashMap<>();
        for (int index = 0; index < words.size(); index++) {
            String word = words.get(index);
            if (word.length() < 2 || word.length() > 20) continue;
            String wordSignature = collapseLetters(word);
            int distance = levenshtein(signature, wordSignature);
            int endpointPenalty = 0;
            if (signature.charAt(0) != wordSignature.charAt(0)) endpointPenalty += 2;
            if (signature.charAt(signature.length() - 1) != wordSignature.charAt(wordSignature.length() - 1)) endpointPenalty += 2;
            int missing = subsequenceMissing(wordSignature, signature);
            int tolerance = Math.max(4, signature.length() / 2 + 1);
            if (distance + endpointPenalty + missing > tolerance) continue;
            double score = 1_800_000
                    - distance * 190_000.0
                    - endpointPenalty * 140_000.0
                    - missing * 110_000.0
                    - Math.abs(wordSignature.length() - signature.length()) * 30_000.0
                    - index * 75.0
                    + personal.wordBoost(word) * 18_000.0;
            addScore(scores, applyDialect(word, dialect), score);
        }
        addProfileWords(scores, "", profile);
        if (dialect != null && dialect.toLowerCase(Locale.ROOT).contains("nigerian")) {
            for (String word : pidginWords) {
                String candidate = clean(word);
                if (!candidate.contains(" ") && levenshtein(signature, collapseLetters(candidate)) <= 3) addScore(scores, word, 1_150_000);
            }
        }
        return ranked(scores, limit);
    }

    public String bestCorrection(String word, String previous, PersonalLexicon personal,
                                 ProfileManager.Profile profile, String dialect) {
        String clean = clean(word);
        if (clean.length() < 3 || rank.containsKey(clean) || personal.wordBoost(clean) > 0) return clean;
        List<String> options = suggest(clean, previous, personal, profile, dialect, 4);
        String best = clean;
        int bestDistance = 3;
        for (String option : options) {
            String candidate = clean(option);
            int distance = damerauLevenshtein(clean, candidate, 2);
            if (distance < bestDistance && distance <= (clean.length() >= 7 ? 2 : 1)) {
                bestDistance = distance; best = option;
            }
        }
        return best;
    }

    public List<String> emojiForWord(String word) {
        List<String> value = emoji.get(clean(word));
        return value == null ? Collections.emptyList() : value;
    }

    private List<String> ranked(Map<String,Double> scores, int limit) {
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<String,Double> entry : scores.entrySet()) {
            if (!entry.getKey().isEmpty()) candidates.add(new Candidate(entry.getKey(), entry.getValue()));
        }
        candidates.sort((a,b) -> Double.compare(b.score, a.score));
        List<String> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (!result.contains(candidate.word)) result.add(candidate.word);
            if (result.size() >= limit) break;
        }
        if (result.isEmpty()) result.addAll(Arrays.asList("the", "and", "you"));
        return result;
    }

    private void loadWords(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("starter_words.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = clean(line);
                if (word.length() < 2 || rank.containsKey(word)) continue;
                rank.put(word, words.size()); words.add(word);
            }
        } catch (IOException ignored) {
            Collections.addAll(words, "the","and","you","that","this","with","have","from","will","your","about","there","what","when","where","which","would","could","should","please","thanks","hello","good","great","keyboard","android");
            for (int i = 0; i < words.size(); i++) rank.put(words.get(i), i);
        }
    }

    private void loadBigrams() {
        putBigram("how", "are","far","to","much","many");
        putBigram("thank", "you","God");
        putBigram("i", "am","will","have","think","need","want","can");
        putBigram("you", "are","can","will","have","need");
        putBigram("we", "are","can","will","have","need");
        putBigram("it", "is","was","will","can");
        putBigram("this", "is","will","can","should");
        putBigram("that", "is","was","will","you");
        putBigram("can", "you","we","be","I");
        putBigram("will", "be","you","have","need");
        putBigram("good", "morning","afternoon","evening","luck","job");
        putBigram("no", "problem","worries","wahala","longer");
        putBigram("please", "send","confirm","check","let","help");
        putBigram("for", "the","you","your","more");
        putBigram("to", "the","be","do","make","get","go");
        putBigram("in", "the","a","this","my");
        putBigram("of", "the","this","a","your");
    }

    private void loadEmoji() {
        emoji.put("happy", Arrays.asList("😊","😄","🙂")); emoji.put("love", Arrays.asList("❤️","😍","🥰"));
        emoji.put("laugh", Arrays.asList("😂","🤣","😆")); emoji.put("sad", Arrays.asList("😢","😭","☹️"));
        emoji.put("angry", Arrays.asList("😠","😡","🤬")); emoji.put("thanks", Arrays.asList("🙏","😊","🤝"));
        emoji.put("thank", Arrays.asList("🙏","😊","🤝")); emoji.put("good", Arrays.asList("👍","✅","👌"));
        emoji.put("fire", Arrays.asList("🔥","🚒","❤️‍🔥")); emoji.put("money", Arrays.asList("💰","💵","🤑"));
        emoji.put("birthday", Arrays.asList("🎂","🎉","🥳")); emoji.put("work", Arrays.asList("💼","🧑‍💻","✅"));
        emoji.put("okay", Arrays.asList("👌","👍","✅")); emoji.put("yes", Arrays.asList("✅","👍","💯"));
        emoji.put("no", Arrays.asList("❌","🙅","👎"));
    }

    private void addNextWords(Map<String,Double> scores, String previous, PersonalLexicon personal) {
        for (String word : personal.nextWords(previous, 8)) addScore(scores, word, 2_200_000 + personal.bigramBoost(previous, word) * 20_000);
        List<String> defaults = bigrams.get(previous);
        if (defaults != null) for (int i = 0; i < defaults.size(); i++) addScore(scores, defaults.get(i), 1_500_000 - i * 40_000);
        if (scores.isEmpty()) { addScore(scores, "the", 500_000); addScore(scores, "and", 490_000); addScore(scores, "you", 480_000); }
    }

    private void addProfileWords(Map<String,Double> scores, String prefix, ProfileManager.Profile profile) {
        Set<String> source;
        switch (profile) {
            case TERMINAL: source = terminalWords; break;
            case CODING: source = codingWords; break;
            case SPREADSHEET: source = sheetWords; break;
            default: return;
        }
        for (String word : source) if (prefix.isEmpty() || word.startsWith(prefix)) addScore(scores, word, 1_350_000);
    }

    private static void addScore(Map<String,Double> scores, String word, double score) {
        if (word == null || word.isEmpty()) return;
        scores.put(word, Math.max(score, scores.getOrDefault(word, Double.NEGATIVE_INFINITY)));
    }

    private void putBigram(String previous, String... next) { bigrams.put(previous, Arrays.asList(next)); }

    private static String applyDialect(String word, String dialect) {
        if (dialect == null) return word;
        String d = dialect.toLowerCase(Locale.ROOT);
        boolean british = d.contains("british") || d.contains("canadian") || d.contains("nigerian");
        if (!british) return word;
        switch (word) {
            case "color": return "colour"; case "favorite": return "favourite"; case "organize": return "organise";
            case "organization": return "organisation"; case "center": return "centre"; case "behavior": return "behaviour";
            case "analyze": return "analyse"; case "license": return "licence"; default: return word;
        }
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z'’-]", "").trim();
    }

    private static String collapsePath(List<String> path) {
        StringBuilder builder = new StringBuilder();
        if (path == null) return "";
        for (String item : path) {
            String clean = clean(item);
            if (clean.isEmpty()) continue;
            char value = clean.charAt(0);
            if (builder.length() == 0 || builder.charAt(builder.length() - 1) != value) builder.append(value);
        }
        return builder.toString();
    }

    private static String collapseLetters(String value) {
        String clean = clean(value); StringBuilder builder = new StringBuilder();
        for (int index = 0; index < clean.length(); index++) {
            char c = clean.charAt(index);
            if (builder.length() == 0 || builder.charAt(builder.length() - 1) != c) builder.append(c);
        }
        return builder.toString();
    }

    private static int subsequenceMissing(String word, String path) {
        int wordIndex = 0, pathIndex = 0;
        while (wordIndex < word.length() && pathIndex < path.length()) {
            if (word.charAt(wordIndex) == path.charAt(pathIndex)) wordIndex++;
            pathIndex++;
        }
        return word.length() - wordIndex;
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1]; int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous; previous = current; current = swap;
        }
        return previous[b.length()];
    }

    private static boolean isKeyboardNearMiss(String a, String b) {
        if (a.length() != b.length()) return false;
        String[] rows = {"qwertyuiop", "asdfghjkl", "zxcvbnm"}; int differences = 0;
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) == b.charAt(i)) continue;
            differences++; if (differences > 1 || !adjacent(a.charAt(i), b.charAt(i), rows)) return false;
        }
        return differences == 1;
    }

    private static boolean adjacent(char a, char b, String[] rows) {
        for (String row : rows) {
            int ia = row.indexOf(a), ib = row.indexOf(b);
            if (ia >= 0 && ib >= 0 && Math.abs(ia - ib) <= 1) return true;
        }
        return false;
    }

    private static int damerauLevenshtein(String a, String b, int max) {
        if (Math.abs(a.length() - b.length()) > max) return max + 1;
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int rowMin = max + 1;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                rowMin = Math.min(rowMin, d[i][j]);
            }
            if (rowMin > max) return max + 1;
        }
        return d[a.length()][b.length()];
    }
}
