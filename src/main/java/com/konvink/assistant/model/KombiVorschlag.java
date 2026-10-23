package com.konvink.assistant.model;

/** Ergebnis eines Ähnlichkeitsvergleichs zweier Praxisaufträge (Kombi-Vorschlag). */
public class KombiVorschlag {

    private final Praxisauftrag auftrag1;
    private final Praxisauftrag auftrag2;
    private final double aehnlichkeit;

    public KombiVorschlag(Praxisauftrag auftrag1, Praxisauftrag auftrag2, double aehnlichkeit) {
        this.auftrag1 = auftrag1;
        this.auftrag2 = auftrag2;
        this.aehnlichkeit = aehnlichkeit;
    }

    public Praxisauftrag getAuftrag1() { return auftrag1; }
    public Praxisauftrag getAuftrag2() { return auftrag2; }
    public double getAehnlichkeit() { return aehnlichkeit; }

    @Override
    public String toString() {
        return String.format("%s <-> %s (%.0f%%)",
                auftrag1.getTitel(), auftrag2.getTitel(), aehnlichkeit * 100);
    }
}
