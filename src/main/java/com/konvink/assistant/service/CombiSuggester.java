package com.konvink.assistant.service;

import com.konvink.assistant.model.KombiVorschlag;
import com.konvink.assistant.model.Praxisauftrag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Findet Praxisauftraege mit aehnlicher "Handlung" (Handlungskompetenz) und
 * schlaegt sie als moegliche Kombi-Auftraege vor.
 *
 * Java-Pendant zu combi_suggester.py. Statt difflib.SequenceMatcher wird hier
 * eine Ratio basierend auf der laengsten gemeinsamen Teilfolge (LCS)
 * berechnet - das Verhalten ist vergleichbar mit Python's SequenceMatcher.ratio().
 */
public final class CombiSuggester {

    private static final double DEFAULT_THRESHOLD = 0.8;

    private CombiSuggester() {
    }

    public static List<KombiVorschlag> findCombiVorschlaege(List<Praxisauftrag> auftraege) {
        return findCombiVorschlaege(auftraege, DEFAULT_THRESHOLD);
    }

    public static List<KombiVorschlag> findCombiVorschlaege(List<Praxisauftrag> auftraege, double threshold) {
        List<KombiVorschlag> vorschlaege = new ArrayList<>();

        for (int i = 0; i < auftraege.size(); i++) {
            Praxisauftrag a1 = auftraege.get(i);
            String h1 = normalize(a1.getHandlung());
            if (h1.isEmpty()) {
                continue;
            }
            for (int j = i + 1; j < auftraege.size(); j++) {
                Praxisauftrag a2 = auftraege.get(j);
                String h2 = normalize(a2.getHandlung());
                if (h2.isEmpty()) {
                    continue;
                }
                double ratio = similarityRatio(h1, h2);
                if (ratio >= threshold) {
                    vorschlaege.add(new KombiVorschlag(a1, a2, ratio));
                }
            }
        }

        vorschlaege.sort((v1, v2) -> Double.compare(v2.getAehnlichkeit(), v1.getAehnlichkeit()));
        return vorschlaege;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.GERMAN);
    }

    /**
     * Berechnet eine Aehnlichkeits-Ratio im Stil von Python's
     * difflib.SequenceMatcher.ratio(): 2 * M / T, wobei M die Laenge der
     * laengsten gemeinsamen Teilfolge (LCS) und T die Gesamtlaenge beider
     * Strings ist.
     */
    static double similarityRatio(String a, String b) {
        int lcsLength = longestCommonSubsequence(a, b);
        int totalLength = a.length() + b.length();
        if (totalLength == 0) {
            return 1.0;
        }
        return (2.0 * lcsLength) / totalLength;
    }

    private static int longestCommonSubsequence(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[n][m];
    }
}
