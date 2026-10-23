package com.konvink.assistant.parser;

import com.konvink.assistant.model.Praxisauftrag;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PraxisauftragParserTest {

    @TempDir
    Path ordner;

    private Path erzeugePdf(String dateiname, String... zeilen) throws Exception {
        Path pfad = ordner.resolve(dateiname);
        try (PDDocument doc = new PDDocument()) {
            PDPage seite = new PDPage();
            doc.addPage(seite);
            try (PDPageContentStream cs = new PDPageContentStream(doc, seite)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.setLeading(16f);
                cs.newLineAtOffset(50, 700);
                for (String z : zeilen) {
                    cs.showText(z);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(pfad.toFile());
        }
        return pfad;
    }

    @Test
    void liestTextAusPdf() throws Exception {
        Path pdf = erzeugePdf("test.pdf", "Hallo Praxisauftrag", "Zweite Zeile");

        String text = PraxisauftragParser.extractTextFromPdf(pdf);

        assertTrue(text.contains("Hallo Praxisauftrag"));
        assertTrue(text.contains("Zweite Zeile"));
    }

    @Test
    void erkenntLehrjahr() throws Exception {
        Path pdf = erzeugePdf("lj.pdf", "Praxisauftrag Lehrjahr 2", "Handlungskompetenz: Kunden beraten");

        Praxisauftrag a = PraxisauftragParser.parsePraxisauftragPdf(pdf);

        assertEquals("lj.pdf", a.getDateiname());
        assertTrue(a.getLehrjahr().contains("2"));
    }

    @Test
    void ordnerEnthaeltAlleAuftraege() throws Exception {
        erzeugePdf("eins.pdf", "Praxisauftrag Lehrjahr 1");
        erzeugePdf("zwei.pdf", "Praxisauftrag Lehrjahr 3");

        List<Praxisauftrag> alle = PraxisauftragParser.parseAllPraxisauftraege(ordner);

        assertEquals(2, alle.size());
    }
}
