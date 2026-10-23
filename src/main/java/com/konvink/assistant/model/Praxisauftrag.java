package com.konvink.assistant.model;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Repraesentiert einen einzelnen, aus einem PDF extrahierten Praxisauftrag.
 * (Java-Pendant zur Python-Dataclass in praxisauftrag_parser.py)
 */
public class Praxisauftrag {

    private String dateiname;
    private Path pdfPfad;
    private String titel = "";
    private String handlung = "";
    private String lehrjahr = "";
    private String rohtext = "";
    private final Map<String, String> weitereFelder = new HashMap<>();

    public Praxisauftrag(String dateiname) {
        this.dateiname = dateiname;
    }

    public String getDateiname() { return dateiname; }
    public void setDateiname(String dateiname) { this.dateiname = dateiname; }

    /** Vollstaendiger Pfad zur Original-PDF-Datei, zum Oeffnen/Anzeigen in der App. */
    public Path getPdfPfad() { return pdfPfad; }
    public void setPdfPfad(Path pdfPfad) { this.pdfPfad = pdfPfad; }

    public String getTitel() { return titel; }
    public void setTitel(String titel) { this.titel = titel; }

    public String getHandlung() { return handlung; }
    public void setHandlung(String handlung) { this.handlung = handlung; }

    public String getLehrjahr() { return lehrjahr; }
    public void setLehrjahr(String lehrjahr) { this.lehrjahr = lehrjahr; }

    public String getRohtext() { return rohtext; }
    public void setRohtext(String rohtext) { this.rohtext = rohtext; }

    public Map<String, String> getWeitereFelder() { return weitereFelder; }

    @Override
    public String toString() {
        return "Praxisauftrag{" +
                "dateiname='" + dateiname + '\'' +
                ", titel='" + titel + '\'' +
                ", handlung='" + handlung + '\'' +
                ", lehrjahr='" + lehrjahr + '\'' +
                '}';
    }
}
