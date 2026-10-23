package com.konvink.assistant.ui;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** PDF-Ansicht mit allen Seiten, Zoom, Seitenwechsel und Textsuche. */
public class PdfPanel extends JPanel {

    private PDDocument doc;
    private PDFRenderer renderer;
    private int seitenAnzahl;
    private double zoom = 1.0;

    private final JPanel seitenContainer = new JPanel();
    private final List<SeitenLabel> seitenLabels = new ArrayList<>();
    private final JScrollPane scroll;
    private final JLabel seitenAnzeige = new JLabel("Seite – / –");
    private final JLabel zoomAnzeige = new JLabel("100 %");
    private final JTextField suchFeld = new JTextField(12);
    private final JLabel suchStatus = new JLabel(" ");
    private final Timer neuRendern;
    private int letzteBreite = -1;
    private long renderId;

    private List<Integer> treffer = new ArrayList<>();
    private int trefferIndex = -1;
    private String letzteSuche = "";

    public PdfPanel(Path pdfPfad) {
        super(new BorderLayout());
        setBackground(Theme.PDF_BG);

        seitenContainer.setLayout(new BoxLayout(seitenContainer, BoxLayout.Y_AXIS));
        seitenContainer.setBackground(Theme.PDF_BG);
        seitenContainer.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel huelle = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        huelle.setBackground(Theme.PDF_BG);
        huelle.add(seitenContainer);
        // FlowLayout bricht nicht um; Container bleibt so beim Zoomen zentriert.
        scroll = new JScrollPane(huelle);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.PDF_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        UiKit.flatScrollBar(scroll);

        add(baueToolbar(), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        neuRendern = new Timer(250, e -> rendern());
        neuRendern.setRepeats(false);
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = scroll.getViewport().getWidth();
                if (w != letzteBreite) {
                    letzteBreite = w;
                    neuRendern.restart();
                }
            }
        });
        scroll.getVerticalScrollBar().addAdjustmentListener(e -> aktualisiereSeitenAnzeige());

        try {
            File datei = pdfPfad == null ? null : pdfPfad.toFile();
            if (datei == null || !datei.isFile()) {
                throw new java.io.IOException("PDF nicht gefunden: " + pdfPfad);
            }
            doc = PDDocument.load(datei);
            renderer = new PDFRenderer(doc);
            seitenAnzahl = doc.getNumberOfPages();
            for (int i = 0; i < seitenAnzahl; i++) {
                SeitenLabel l = new SeitenLabel();
                l.setAlignmentX(Component.CENTER_ALIGNMENT);
                l.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
                seitenLabels.add(l);
                seitenContainer.add(l);
                seitenContainer.add(Box.createVerticalStrut(12));
            }
            aktualisiereSeitenAnzeige();
        } catch (Exception e) {
            JLabel fehler = new JLabel("PDF konnte nicht geladen werden: " + e.getMessage());
            fehler.setForeground(Theme.MUTED);
            seitenContainer.add(fehler);
        }
    }

    private JComponent baueToolbar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(Theme.SURFACE);
        bar.setBorder(new EmptyBorder(6, 10, 6, 10));

        JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        links.setOpaque(false);
        JButton zurueck = UiKit.iconButton("◀", "Vorherige Seite");
        JButton vor = UiKit.iconButton("▶", "Nächste Seite");
        JButton minus = UiKit.iconButton("−", "Verkleinern");
        JButton plus = UiKit.iconButton("+", "Vergrößern");
        JButton anpassen = UiKit.iconButton("⤢", "An Breite anpassen");
        zurueck.addActionListener(e -> springeZu(aktuelleSeite() - 1));
        vor.addActionListener(e -> springeZu(aktuelleSeite() + 1));
        minus.addActionListener(e -> setzeZoom(zoom / 1.25));
        plus.addActionListener(e -> setzeZoom(zoom * 1.25));
        anpassen.addActionListener(e -> setzeZoom(1.0));
        for (JLabel l : new JLabel[]{seitenAnzeige, zoomAnzeige}) {
            l.setForeground(Theme.MUTED);
            l.setFont(l.getFont().deriveFont(Font.PLAIN, 12f));
        }
        links.add(zurueck);
        links.add(seitenAnzeige);
        links.add(vor);
        links.add(Box.createHorizontalStrut(10));
        links.add(minus);
        links.add(zoomAnzeige);
        links.add(plus);
        links.add(anpassen);
        bar.add(links, BorderLayout.WEST);

        JPanel rechts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rechts.setOpaque(false);
        suchFeld.putClientProperty("JTextField.placeholderText", "Im PDF suchen …");
        suchFeld.addActionListener(e -> suche());
        JButton los = UiKit.iconButton("🔍", "Suchen (Enter = nächster Treffer)");
        los.addActionListener(e -> suche());
        suchStatus.setForeground(Theme.MUTED);
        suchStatus.setFont(suchStatus.getFont().deriveFont(Font.PLAIN, 11.5f));
        rechts.add(suchStatus);
        rechts.add(suchFeld);
        rechts.add(los);
        bar.add(rechts, BorderLayout.EAST);
        return bar;
    }

    public void fokusSuche() {
        suchFeld.requestFocusInWindow();
        suchFeld.selectAll();
    }

    public void schliessen() {
        neuRendern.stop();
        renderId++;
        if (doc != null) {
            try {
                synchronized (doc) {
                    doc.close();
                }
            } catch (Exception ignored) {
                // egal beim Schliessen
            }
            doc = null;
        }
    }

    // ---------------------------------------------------------- Rendern

    private void setzeZoom(double neu) {
        zoom = Math.max(0.4, Math.min(4.0, neu));
        zoomAnzeige.setText(Math.round(zoom * 100) + " %");
        neuRendern.restart();
    }

    private void rendern() {
        if (doc == null || seitenAnzahl == 0) {
            return;
        }
        int breite = scroll.getViewport().getWidth() - 48;
        if (breite <= 50) {
            return;
        }
        final long id = ++renderId;
        final double faktor = zoom;
        final PDDocument d = doc;
        new SwingWorker<List<BufferedImage>, Void>() {
            @Override
            protected List<BufferedImage> doInBackground() throws Exception {
                List<BufferedImage> bilder = new ArrayList<>();
                for (int i = 0; i < seitenAnzahl && id == renderId; i++) {
                    synchronized (d) {
                        PDRectangle box = d.getPage(i).getCropBox();
                        float scale = (float) (breite * faktor / box.getWidth());
                        bilder.add(renderer.renderImage(i, scale));
                    }
                }
                return bilder;
            }

            @Override
            protected void done() {
                if (id != renderId) {
                    return;
                }
                try {
                    List<BufferedImage> bilder = get();
                    for (int i = 0; i < bilder.size() && i < seitenLabels.size(); i++) {
                        BufferedImage bild = bilder.get(i);
                        synchronized (d) {
                            seitenLabels.get(i).scale = bild.getWidth() / d.getPage(i).getCropBox().getWidth();
                        }
                        seitenLabels.get(i).setIcon(new ImageIcon(bild));
                    }
                    seitenContainer.revalidate();
                    aktualisiereSeitenAnzeige();
                } catch (Exception ignored) {
                    // Rendern abgebrochen oder PDF geschlossen
                }
            }
        }.execute();
    }

    // ------------------------------------------------------- Navigation

    private int aktuelleSeite() {
        if (seitenLabels.isEmpty()) {
            return 0;
        }
        int ziel = scroll.getViewport().getViewPosition().y + scroll.getViewport().getHeight() / 3;
        int aktuell = 0;
        for (int i = 0; i < seitenLabels.size(); i++) {
            JLabel l = seitenLabels.get(i);
            int y = SwingUtilities.convertPoint(l.getParent(), l.getLocation(),
                    scroll.getViewport().getView()).y;
            if (y <= ziel) {
                aktuell = i;
            }
        }
        return aktuell;
    }

    private void aktualisiereSeitenAnzeige() {
        seitenAnzeige.setText(seitenAnzahl == 0 ? "Seite – / –"
                : "Seite " + (aktuelleSeite() + 1) + " / " + seitenAnzahl);
    }

    private void springeZu(int seite) {
        if (seite < 0 || seite >= seitenLabels.size()) {
            return;
        }
        JLabel l = seitenLabels.get(seite);
        Point p = SwingUtilities.convertPoint(l.getParent(), l.getLocation(), scroll.getViewport().getView());
        scroll.getViewport().setViewPosition(new Point(scroll.getViewport().getViewPosition().x,
                Math.max(0, p.y - 8)));
        aktualisiereSeitenAnzeige();
    }

    // ------------------------------------------------------------ Suche

    private void suche() {
        String begriff = suchFeld.getText().trim().toLowerCase(Locale.ROOT);
        if (begriff.isEmpty() || doc == null) {
            suchStatus.setText(" ");
            letzteSuche = "";
            markiere(new java.util.TreeMap<>());
            return;
        }
        if (begriff.equals(letzteSuche) && !treffer.isEmpty()) {
            trefferIndex = (trefferIndex + 1) % treffer.size();
            zeigeTreffer();
            return;
        }
        letzteSuche = begriff;
        suchStatus.setText("suche …");
        final PDDocument d = doc;
        new SwingWorker<java.util.Map<Integer, List<float[]>>, Void>() {
            @Override
            protected java.util.Map<Integer, List<float[]>> doInBackground() throws Exception {
                java.util.Map<Integer, List<float[]>> seiten = new java.util.TreeMap<>();
                synchronized (d) {
                    for (int i = 1; i <= seitenAnzahl; i++) {
                        TrefferStripper stripper = new TrefferStripper(begriff);
                        stripper.setStartPage(i);
                        stripper.setEndPage(i);
                        stripper.getText(d);
                        if (!stripper.rects.isEmpty()) {
                            seiten.put(i - 1, stripper.rects);
                        }
                    }
                }
                return seiten;
            }

            @Override
            protected void done() {
                java.util.Map<Integer, List<float[]>> map;
                try {
                    map = get();
                } catch (Exception e) {
                    map = new java.util.TreeMap<>();
                }
                treffer = new ArrayList<>(map.keySet());
                markiere(map);
                trefferIndex = treffer.isEmpty() ? -1 : 0;
                zeigeTreffer();
            }
        }.execute();
    }

    /** Gelbe Markierung der Treffer auf den Seiten. */
    private void markiere(java.util.Map<Integer, List<float[]>> map) {
        for (int i = 0; i < seitenLabels.size(); i++) {
            seitenLabels.get(i).treffer = map.getOrDefault(i, List.of());
            seitenLabels.get(i).repaint();
        }
    }

    /** Seiten-Label, das Suchtreffer als halbtransparente Rechtecke ueber das Bild zeichnet. */
    private static class SeitenLabel extends JLabel {
        List<float[]> treffer = List.of();
        double scale = 1.0;

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (treffer.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            Insets in = getInsets();
            g2.setColor(new Color(255, 214, 0, 110));
            for (float[] r : treffer) {
                g2.fillRect(in.left + (int) Math.floor(r[0] * scale) - 1, in.top + (int) Math.floor(r[1] * scale) - 1,
                        (int) Math.ceil(r[2] * scale) + 2, (int) Math.ceil(r[3] * scale) + 2);
            }
            g2.dispose();
        }
    }

    /** Sammelt die Positionen aller Vorkommen eines Suchbegriffs (in PDF-Punkten, Ursprung oben links). */
    private static class TrefferStripper extends PDFTextStripper {
        final String term;
        final List<float[]> rects = new ArrayList<>();

        TrefferStripper(String term) throws java.io.IOException {
            this.term = term;
        }

        @Override
        protected void writeString(String text, List<org.apache.pdfbox.text.TextPosition> pos)
                throws java.io.IOException {
            String low = text.toLowerCase(Locale.ROOT);
            int idx = 0;
            while ((idx = low.indexOf(term, idx)) >= 0) {
                int end = Math.min(idx + term.length(), pos.size());
                if (idx < pos.size() && end > idx) {
                    org.apache.pdfbox.text.TextPosition first = pos.get(idx);
                    org.apache.pdfbox.text.TextPosition last = pos.get(end - 1);
                    float h = first.getHeightDir();
                    rects.add(new float[]{first.getXDirAdj(), first.getYDirAdj() - h,
                            last.getXDirAdj() + last.getWidthDirAdj() - first.getXDirAdj(), h});
                }
                idx += term.length();
            }
            super.writeString(text, pos);
        }
    }

    private void zeigeTreffer() {
        if (treffer.isEmpty()) {
            suchStatus.setText("Nichts gefunden");
            return;
        }
        int seite = treffer.get(trefferIndex);
        suchStatus.setText("Seite " + (seite + 1) + " (" + (trefferIndex + 1) + "/" + treffer.size() + ")");
        springeZu(seite);
    }
}
