package com.konvink.assistant.service;

import com.konvink.assistant.model.KombiVorschlag;
import com.konvink.assistant.model.Praxisauftrag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Berechnet aus den Paar-Vorschlaegen zusammenhaengende Kombi-Gruppen
 * (transitiv: A+B und B+C => {A,B,C}, immer mindestens 2 Auftraege).
 */
public final class KombiGruppen {

    private KombiGruppen() {
    }

    /** Gruppe zu einem Auftrag (enthaelt ihn selbst als erstes Element). */
    public static List<Praxisauftrag> gruppeVon(Praxisauftrag start, List<KombiVorschlag> vorschlaege) {
        LinkedHashSet<Praxisauftrag> gruppe = new LinkedHashSet<>();
        ArrayDeque<Praxisauftrag> offen = new ArrayDeque<>();
        gruppe.add(start);
        offen.add(start);
        while (!offen.isEmpty()) {
            Praxisauftrag aktuell = offen.poll();
            for (KombiVorschlag v : vorschlaege) {
                Praxisauftrag nachbar = null;
                if (v.getAuftrag1() == aktuell) {
                    nachbar = v.getAuftrag2();
                } else if (v.getAuftrag2() == aktuell) {
                    nachbar = v.getAuftrag1();
                }
                if (nachbar != null && gruppe.add(nachbar)) {
                    offen.add(nachbar);
                }
            }
        }
        return new ArrayList<>(gruppe);
    }

    /** Alle Gruppen mit mindestens 2 Auftraegen. */
    public static List<List<Praxisauftrag>> alle(List<Praxisauftrag> auftraege, List<KombiVorschlag> vorschlaege) {
        List<List<Praxisauftrag>> gruppen = new ArrayList<>();
        Set<Praxisauftrag> besucht = new HashSet<>();
        for (Praxisauftrag start : auftraege) {
            if (besucht.contains(start)) {
                continue;
            }
            List<Praxisauftrag> gruppe = gruppeVon(start, vorschlaege);
            besucht.addAll(gruppe);
            if (gruppe.size() >= 2) {
                gruppen.add(gruppe);
            }
        }
        return gruppen;
    }

    /** Hoechste direkte Aehnlichkeit zweier Auftraege, sonst -1. */
    public static double aehnlichkeit(Praxisauftrag a, Praxisauftrag b, List<KombiVorschlag> vorschlaege) {
        double best = -1;
        for (KombiVorschlag v : vorschlaege) {
            boolean passt = (v.getAuftrag1() == a && v.getAuftrag2() == b)
                    || (v.getAuftrag1() == b && v.getAuftrag2() == a);
            if (passt) {
                best = Math.max(best, v.getAehnlichkeit());
            }
        }
        return best;
    }
}
