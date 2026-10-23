package com.konvink.assistant.parser;

import com.konvink.assistant.model.Praxisauftrag;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrahiert strukturierte Informationen aus Praxisauftrags-PDFs
 * (Titel, zugehörige Handlungskompetenz/"Handlung", Lehrjahr, etc.).
 *
 * Java-Pendant zu praxisauftrag_parser.py. Die Regex-Muster sind ein erster
 * Ansatz und werden angepasst, sobald echte Beispiel-PDFs aus Konvink
 * vorliegen.
 */
public final class PraxisauftragParser {

    private static final Logger LOGGER = Logger.getLogger(PraxisauftragParser.class.getName());

    // Echte Konvink-PDF-Struktur (ermittelt anhand realer Beispiel-PDFs):
    //
    //   Praxisauftrag «Sich dienstleistungsorientiert verhalten» Seite 1 von 1   <- Titel (Kopfzeile, robust!)
    //   © 2021 Konvink AG
    //   Lehrjahr 2
    //
    //   Sich dienstleistungsorientiert verhalten
    //
    //   Praxisauftrag
    //
    //   Handlungskompetenz b2: ...                    <- Handlung
    //
    // Titel steht zuverlässig in der ersten Kopfzeile in « ... » Anführungszeichen.
    // Zusätzlich können Titel/Handlung durch den PDF-Zeilenumbruch mit
    // Bindestrich getrennt sein (z.B. "ein-\nrichten") - das wird vor dem
    // eigentlichen Parsen bereinigt (siehe cleanupLineBreaks()).
    private static final Pattern TITEL_HEADER_PATTERN =
            Pattern.compile("Praxisauftrag\\s*\u00ab(.+?)\u00bb", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TITEL_FALLBACK_PATTERN =
            Pattern.compile("\\n\\s*\\n(.+?)\\n\\s*\\nPraxisauftrag\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HANDLUNG_PATTERN =
            Pattern.compile("(?<![A-Za-z\u00e4\u00f6\u00fc\u00c4\u00d6\u00dc])Handlungskompetenz\\s*:?\\s*(.+?)(?=\\n\\s*\\n|\\nAusgangslage|$)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LEHRJAHR_PATTERN =
            Pattern.compile("Lehrjahr\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    // Titel/Lehrjahr stehen immer auf der allerersten Seite ganz am Anfang
    // des Dokuments. Die Suche wird bewusst auf diesen Anfangsbereich
    // begrenzt, damit die (reluctant/lazy) Regex nicht versehentlich bis zu
    // einem späteren, zufälligen Vorkommen des Worts "Praxisauftrag"
    // mitten im Fließtext "weiterläuft" und dabei den gesamten
    // dazwischenliegenden Text fälschlicherweise als Titel erfasst.
    private static final int HEADER_SEARCH_LIMIT = 600;

    private PraxisauftragParser() {
    }

    /** Liest den vollständigen Text aus einem PDF aus. */
    public static String extractTextFromPdf(Path pdfPath) throws IOException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Entfernt durch den Zeilenumbruch verursachte Bindestrich-Trennungen
     * (z.B. "ein-\nrichten" -> "einrichten") und normalisiert verbleibende
     * Zeilenumbrüche innerhalb von Sätzen zu Leerzeichen, damit Titel und
     * Handlung sich über mehrere PDF-Zeilen erstrecken können.
     */
    private static String joinWrappedText(String rawSnippet) {
        String dehyphenated = rawSnippet.replaceAll("-\\s*\\n\\s*", "");
        return dehyphenated.replaceAll("\\s*\\n\\s*", " ").strip();
    }

    /** Parst ein einzelnes Praxisauftrags-PDF und extrahiert die wichtigsten Felder. */
    public static Praxisauftrag parsePraxisauftragPdf(Path pdfPath) throws IOException {
        String text = extractTextFromPdf(pdfPath);

        Praxisauftrag auftrag = new Praxisauftrag(pdfPath.getFileName().toString());
        auftrag.setPdfPfad(pdfPath.toAbsolutePath());
        auftrag.setRohtext(text);

        // Titel-Suche bewusst auf den Kopfbereich des Dokuments begrenzen
        // (siehe HEADER_SEARCH_LIMIT) - verhindert, dass die Regex bei
        // fehlgeschlagenem Header-Match bis tief in den Fließtext läuft.
        String headerSection = text.substring(0, Math.min(HEADER_SEARCH_LIMIT, text.length()));

        Matcher titelHeaderMatcher = TITEL_HEADER_PATTERN.matcher(headerSection);
        if (titelHeaderMatcher.find()) {

            auftrag.setTitel(joinWrappedText(titelHeaderMatcher.group(1)));
        } else {
            Matcher titelFallbackMatcher = TITEL_FALLBACK_PATTERN.matcher(headerSection);
            if (titelFallbackMatcher.find()) {
                auftrag.setTitel(joinWrappedText(titelFallbackMatcher.group(1)));

            } else {
                String dateiname = pdfPath.getFileName().toString();
                int dotIndex = dateiname.lastIndexOf('.');
                auftrag.setTitel(dotIndex > 0 ? dateiname.substring(0, dotIndex) : dateiname);
            }
        }

        Matcher handlungMatcher = HANDLUNG_PATTERN.matcher(text);
        if (handlungMatcher.find()) {
            auftrag.setHandlung(joinWrappedText(handlungMatcher.group(1)));
        }

        Matcher lehrjahrMatcher = LEHRJAHR_PATTERN.matcher(text);
        if (lehrjahrMatcher.find()) {
            auftrag.setLehrjahr("Lehrjahr " + lehrjahrMatcher.group(1).strip());
        }

        if (auftrag.getHandlung().isEmpty()) {
            LOGGER.log(Level.WARNING,
                    "Konnte keine Handlungskompetenz in ''{0}'' erkennen - bitte "
                            + "Regex-Muster in PraxisauftragParser.java anpassen, sobald die "
                            + "echte PDF-Struktur bekannt ist.",
                    pdfPath.getFileName());
        }

        return auftrag;
    }

    /** Parst alle PDF-Dateien in einem Ordner. */
    public static List<Praxisauftrag> parseAllPraxisauftraege(Path pdfFolder) {
        List<Praxisauftrag> auftraege = new ArrayList<>();
        File[] files = pdfFolder.toFile().listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
        if (files == null) {
            return auftraege;
        }
        java.util.Arrays.sort(files);
        for (File file : files) {
            try {
                auftraege.add(parsePraxisauftragPdf(file.toPath()));
            } catch (IOException exc) {
                LOGGER.log(Level.SEVERE, "Fehler beim Parsen von ''{0}'': {1}",
                        new Object[]{file.getName(), exc.getMessage()});
            }
        }
        return auftraege;
    }
}
