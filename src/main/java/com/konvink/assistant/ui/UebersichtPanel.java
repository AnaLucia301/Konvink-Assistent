package com.konvink.assistant.ui;

import com.konvink.assistant.model.KombiVorschlag;
import com.konvink.assistant.model.Praxisauftrag;
import com.konvink.assistant.parser.PraxisauftragParser;
import com.konvink.assistant.service.CombiSuggester;
import com.konvink.assistant.service.KombiGruppen;
import com.konvink.assistant.service.OutlookIntegration;
import com.konvink.assistant.service.Terminplaner;
import com.konvink.assistant.store.AppStore;
import com.konvink.assistant.store.AppStore.AuftragInfo;
import com.konvink.assistant.ui.UiKit.Karte;
import com.konvink.assistant.ui.UiKit.VerticalStackPanel;
import com.konvink.assistant.ui.UiKit.WrapLabel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Startseite: Zeitblocker, Kalender, Wochenuebersicht, Auftragskarten und Kombi-Vorschlaege. */
public class UebersichtPanel extends JPanel {

    private static final Logger LOGGER = Logger.getLogger(UebersichtPanel.class.getName());
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter KURZ = DateTimeFormatter.ofPattern("dd.MM.");

    private final Consumer<Praxisauftrag> oeffne;

    private final JTextField subjectField = new JTextField("Zeitblock");
    private final JTextField dateField = new JTextField(LocalDate.now().format(DATE_FMT));
    private final JTextField timeField = new JTextField(LocalTime.now().format(TIME_FMT));
    private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(60, 5, 480, 5));
    private final JComboBox<String> auftragBox = new JComboBox<>();
    private final KalenderPanel kalender = new KalenderPanel();
    private final VerticalStackPanel wochePanel = new VerticalStackPanel();

    private final JTextField sucheFeld = new JTextField();
    private final JToggleButton favoritenFilter = new JToggleButton("★ Favoriten");
    private final JToggleButton offenFilter = new JToggleButton("Nur offene");
    private final JLabel statusLabel = new JLabel(" ");
    private final JComboBox<String> sortBox = new JComboBox<>(new String[]{
            "Sortierung: Standard", "Nach Lehrjahr", "Nach Frist", "Nach Name", "Nach Status"});
    private final Fortschritt fortschritt = new Fortschritt();

    /** Fortschrittsbalken "X von Y erledigt". */
    private static class Fortschritt extends JComponent {
        private int erledigt;
        private int gesamt;

        Fortschritt() {
            setPreferredSize(new Dimension(100, 30));
        }

        void setWerte(int erledigt, int gesamt) {
            this.erledigt = erledigt;
            this.gesamt = gesamt;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(Theme.SURFACE);
            g2.fillRoundRect(0, 0, w, h, 16, 16);
            double anteil = gesamt == 0 ? 0 : erledigt / (double) gesamt;
            int bw = (int) Math.round((w - 8) * anteil);
            if (bw > 0) {
                g2.setPaint(new GradientPaint(0, 0, Theme.LIME, w, 0, Theme.CYAN));
                g2.fillRoundRect(4, 4, Math.max(bw, 12), h - 8, 12, 12);
            }
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            String t = erledigt + " von " + gesamt + " erledigt" + (gesamt > 0 ? "  (" + Math.round(anteil * 100) + " %)" : "")
                    + (gesamt > 0 && erledigt == gesamt ? "  🎉 Alles geschafft!" : "");
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT);
            g2.drawString(t, (w - fm.stringWidth(t)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }
    private final VerticalStackPanel auftraegePanel = new VerticalStackPanel();
    private final VerticalStackPanel kombiPanel = new VerticalStackPanel();

    private List<Praxisauftrag> auftraege = new ArrayList<>();
    private List<KombiVorschlag> vorschlaege = new ArrayList<>();
    private Map<LocalDate, Praxisauftrag> termine = new TreeMap<>();

    public UebersichtPanel(Consumer<Praxisauftrag> oeffne) {
        super(new BorderLayout(14, 14));
        this.oeffne = oeffne;
        setBackground(Theme.BG_APP);
        setBorder(new EmptyBorder(14, 14, 14, 14));

        add(baueSeitenleiste(), BorderLayout.WEST);
        add(baueMitte(), BorderLayout.CENTER);

        kalender.setOeffneListener(oeffne);
        kalender.setVerschiebeListener(this::terminVerschoben);
        kalender.setBlockLoeschListener(b -> {
            AppStore.get().state().bloecke.remove(b);
            AppStore.get().speichern();
            aktualisiereBloecke();
        });
        kalender.setBloecke(AppStore.get().state().bloecke);
        zeichneAuftraege();
        zeichneKombi();
        zeichneWoche();
    }

    // ------------------------------------------------------------ Aufbau

    private JComponent baueSeitenleiste() {
        VerticalStackPanel stapel = new VerticalStackPanel();
        stapel.setOpaque(false);

        // --- Zeitblocker
        Karte zeit = neueKarte();
        zeit.add(UiKit.sectionHeading("Zeitblocker"));
        zeit.add(Box.createVerticalStrut(8));
        zeit.add(UiKit.formLabel("Betreff"));
        zeit.add(Box.createVerticalStrut(3));
        zeit.add(UiKit.styledField(subjectField));
        zeit.add(Box.createVerticalStrut(8));
        zeit.add(UiKit.formLabel("Auftrag (optional)"));
        zeit.add(Box.createVerticalStrut(3));
        auftragBox.setPrototypeDisplayValue("Auftrag auswählen …");
        auftragBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        auftragBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        auftragBox.addActionListener(e -> {
            int i = auftragBox.getSelectedIndex() - 1;
            if (i >= 0 && i < auftraege.size()) {
                subjectField.setText("Bearbeiten: " + UiKit.name(auftraege.get(i)));
            }
        });
        zeit.add(auftragBox);
        zeit.add(Box.createVerticalStrut(8));
        zeit.add(UiKit.formLabel("Datum (dd.MM.yyyy)"));
        zeit.add(Box.createVerticalStrut(3));
        zeit.add(UiKit.styledField(dateField));
        zeit.add(Box.createVerticalStrut(8));
        zeit.add(UiKit.formLabel("Uhrzeit (HH:mm)"));
        zeit.add(Box.createVerticalStrut(3));
        zeit.add(UiKit.styledField(timeField));
        zeit.add(Box.createVerticalStrut(8));
        zeit.add(UiKit.formLabel("Dauer (Minuten)"));
        zeit.add(Box.createVerticalStrut(3));
        durationSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        durationSpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        zeit.add(durationSpinner);
        zeit.add(Box.createVerticalStrut(12));
        JButton outlook = UiKit.primaryButton("＋ Einplanen");
        outlook.setAlignmentX(Component.LEFT_ALIGNMENT);
        outlook.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        outlook.addActionListener(e -> blockEinplanen());
        zeit.add(outlook);
        stapel.add(zeit);
        stapel.add(Box.createVerticalStrut(12));

        // --- Kalender
        Karte kal = neueKarte();
        kal.add(UiKit.sectionHeading("Termine & Fristen"));
        kal.add(Box.createVerticalStrut(4));
        JLabel tipp = new JLabel("<html>Dein Kalender – trag Zeitblöcke selbst ein.<br>rosa Punkt = Frist, blauer Punkt = Zeitblock</html>");
        tipp.setForeground(Theme.MUTED);
        tipp.setFont(tipp.getFont().deriveFont(Font.PLAIN, 11f));
        tipp.setAlignmentX(Component.LEFT_ALIGNMENT);
        kal.add(tipp);
        kal.add(Box.createVerticalStrut(6));
        kalender.setAlignmentX(Component.LEFT_ALIGNMENT);
        kal.add(kalender);
        stapel.add(kal);
        stapel.add(Box.createVerticalStrut(12));

        // --- Woche
        Karte woche = neueKarte();
        woche.add(UiKit.sectionHeading("Was steht diese Woche an?"));
        woche.add(Box.createVerticalStrut(8));
        wochePanel.setOpaque(false);
        wochePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        woche.add(wochePanel);
        stapel.add(woche);

        JScrollPane sp = UiKit.scroll(stapel);
        sp.setPreferredSize(new Dimension(320, 0));
        sp.setMinimumSize(new Dimension(320, 0));
        return sp;
    }

    private static Karte neueKarte() {
        Karte k = new Karte(null, Theme.SURFACE, null);
        k.setLayout(new BoxLayout(k, BoxLayout.Y_AXIS));
        k.setBorder(new EmptyBorder(14, 14, 14, 14));
        return k;
    }

    private JComponent baueMitte() {
        JPanel mitte = new JPanel(new BorderLayout(0, 10));
        mitte.setOpaque(false);

        JPanel oben = new JPanel(new BorderLayout(8, 0));
        oben.setOpaque(false);
        sucheFeld.putClientProperty("JTextField.placeholderText", "Aufträge durchsuchen … (Strg+F)");
        sucheFeld.putClientProperty("JTextField.showClearButton", true);
        sucheFeld.setBorder(new javax.swing.border.CompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true), new EmptyBorder(6, 10, 6, 10)));
        sucheFeld.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                zeichneAuftraege();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                zeichneAuftraege();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                zeichneAuftraege();
            }
        });
        favoritenFilter.setFocusPainted(false);
        favoritenFilter.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        favoritenFilter.addActionListener(e -> zeichneAuftraege());
        offenFilter.setFocusPainted(false);
        offenFilter.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        offenFilter.addActionListener(e -> zeichneAuftraege());
        JButton neuLaden = UiKit.iconButton("↻", "Ordner neu laden");
        neuLaden.addActionListener(e -> laden());
        JPanel rechts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rechts.setOpaque(false);
        sortBox.addActionListener(e -> zeichneAuftraege());
        sortBox.setToolTipText("Sortierung");
        rechts.add(sortBox);
        rechts.add(favoritenFilter);
        rechts.add(offenFilter);
        rechts.add(neuLaden);
        oben.add(sucheFeld, BorderLayout.CENTER);
        oben.add(rechts, BorderLayout.EAST);

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11.5f));
        statusLabel.setForeground(Theme.MUTED);
        JPanel kopf = new JPanel(new BorderLayout(0, 6));
        kopf.setOpaque(false);
        kopf.add(fortschritt, BorderLayout.NORTH);
        JPanel sucheUndStatus = new JPanel(new BorderLayout(0, 4));
        sucheUndStatus.setOpaque(false);
        sucheUndStatus.add(oben, BorderLayout.CENTER);
        sucheUndStatus.add(statusLabel, BorderLayout.SOUTH);
        kopf.add(sucheUndStatus, BorderLayout.CENTER);
        mitte.add(kopf, BorderLayout.NORTH);

        JPanel spalten = new JPanel(new GridLayout(1, 2, 14, 0));
        spalten.setOpaque(false);
        spalten.add(spalte("Praxisaufträge", auftraegePanel));
        spalten.add(spalte("Kombi-Vorschläge", kombiPanel));
        mitte.add(spalten, BorderLayout.CENTER);
        return mitte;
    }

    private JComponent spalte(String titel, VerticalStackPanel inhalt) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.SURFACE);
        p.setBorder(UiKit.titledBorder(titel));
        inhalt.setOpaque(false);
        p.add(UiKit.scroll(inhalt), BorderLayout.CENTER);
        return p;
    }

    /** Fokus ins Suchfeld (Strg+F). */
    public void fokusSuche() {
        sucheFeld.requestFocusInWindow();
        sucheFeld.selectAll();
    }

    // ------------------------------------------------------------ Laden

    private Runnable nachLaden;

    /** Wird einmalig nach dem ersten erfolgreichen Laden ausgefuehrt. */
    public void setNachLaden(Runnable r) {
        this.nachLaden = r;
    }

    public List<Praxisauftrag> getAuftraege() {
        return auftraege;
    }

    public List<KombiVorschlag> getVorschlaege() {
        return vorschlaege;
    }

    /** Laedt die Auftraege aus dem in den Einstellungen gewaehlten Ordner. */public void laden() {
        Path ordner = Paths.get(AppStore.get().settings().ordner);
        statusLabel.setText("Lade Praxisaufträge aus '" + ordner + "' …");
        zeigeLadeSkeleton();

        new SwingWorker<Void, Void>() {
            private List<Praxisauftrag> geladen = List.of();
            private List<KombiVorschlag> vs = List.of();
            private String fehler;

            @Override
            protected Void doInBackground() {
                if (!ordner.toFile().isDirectory()) {
                    fehler = "Ordner nicht gefunden: " + ordner.toAbsolutePath();
                    return null;
                }
                geladen = PraxisauftragParser.parseAllPraxisauftraege(ordner);
                vs = CombiSuggester.findCombiVorschlaege(geladen);
                return null;
            }

            @Override
            protected void done() {
                if (fehler != null) {
                    statusLabel.setText(fehler);
                    LOGGER.warning(fehler);
                    auftraege = new ArrayList<>();
                    vorschlaege = new ArrayList<>();
                } else {
                    auftraege = new ArrayList<>(geladen);
                    vorschlaege = new ArrayList<>(vs);
                    statusLabel.setText(auftraege.size() + " Praxisaufträge geladen aus: " + ordner);
                }
                // Kalender bleibt leer: keine automatischen Terminvorschlaege mehr
                termine = new TreeMap<>();
                AppStore.get().state().termine.clear();
                zeichneAuftraege();
                zeichneKombi();
                kalender.setVorschlaege(termine);
                fuelleAuftragBox();
                kalender.setBloecke(AppStore.get().state().bloecke);
                aktualisiereFristen();
                if (nachLaden != null) {
                    Runnable r = nachLaden;
                    nachLaden = null; // nur beim ersten Laden ausfuehren
                    r.run();
                }
            }
        }.execute();
    }

    private void zeigeLadeSkeleton() {
        for (VerticalStackPanel p : List.of(auftraegePanel, kombiPanel)) {
            p.removeAll();
            for (int i = 0; i < 4; i++) {
                Karte k = new Karte(new BorderLayout(), Theme.CARD, Theme.mitAlpha(Theme.MUTED, 80));
                k.add(new UiKit.Skeleton(2), BorderLayout.CENTER);
                p.add(k);
                p.add(Box.createVerticalStrut(10));
            }
            p.revalidate();
            p.repaint();
        }
    }

    // ------------------------------------------------------- Termine

    private Map<LocalDate, Praxisauftrag> mergeMitGespeichert(Map<LocalDate, Praxisauftrag> standard,
                                                              List<Praxisauftrag> alle) {
        Map<String, Praxisauftrag> nachName = new LinkedHashMap<>();
        alle.forEach(a -> nachName.put(a.getDateiname(), a));
        Map<LocalDate, Praxisauftrag> ergebnis = new TreeMap<>();
        Set<Praxisauftrag> benutzt = new HashSet<>();
        LocalDate heute = LocalDate.now();

        for (Map.Entry<String, String> e : AppStore.get().state().termine.entrySet()) {
            try {
                LocalDate d = LocalDate.parse(e.getKey());
                Praxisauftrag a = nachName.get(e.getValue());
                if (!d.isBefore(heute) && a != null && !benutzt.contains(a) && !ergebnis.containsKey(d)) {
                    ergebnis.put(d, a);
                    benutzt.add(a);
                }
            } catch (DateTimeParseException ignored) {
                // kaputter Eintrag wird verworfen
            }
        }
        for (Map.Entry<LocalDate, Praxisauftrag> e : standard.entrySet()) {
            if (benutzt.contains(e.getValue())) {
                continue;
            }
            LocalDate d = e.getKey();
            while (ergebnis.containsKey(d)) {
                d = d.plusDays(1);
            }
            ergebnis.put(d, e.getValue());
            benutzt.add(e.getValue());
        }
        return ergebnis;
    }

    private void speichereTermine() {
        Map<String, String> m = AppStore.get().state().termine;
        m.clear();
        termine.forEach((d, a) -> m.put(d.toString(), a.getDateiname()));
        AppStore.get().speichern();
    }

    private void terminVerschoben(LocalDate von, LocalDate nach) {
        Praxisauftrag a = termine.get(von);
        if (a == null) {
            return;
        }
        Praxisauftrag anderer = termine.get(nach);
        termine.remove(von);
        termine.put(nach, a);
        if (anderer != null) {
            termine.put(von, anderer); // Tausch, wenn der Zieltag schon belegt ist
        }
        speichereTermine();
        kalender.aktualisiereVorschlaege(termine);
        zeichneWoche();
    }

    // --------------------------------------------------- Fristen / Woche

    private static AuftragInfo infoOrNull(Praxisauftrag a) {
        return AppStore.get().state().auftraege.get(a.getDateiname());
    }

    /** Neu zeichnen nach Aenderungen im Detail (Favorit, Frist). */
    public void aktualisiereFristen() {
        Map<LocalDate, List<Praxisauftrag>> fristen = new LinkedHashMap<>();
        for (Praxisauftrag a : auftraege) {
            LocalDate f = DetailView.fristVon(infoOrNull(a));
            if (f != null) {
                fristen.computeIfAbsent(f, k -> new ArrayList<>()).add(a);
            }
        }
        kalender.setFristen(fristen);
        kalender.setBloecke(AppStore.get().state().bloecke);
        kalender.setBloecke(AppStore.get().state().bloecke);
        zeichneAuftraege();
        zeichneWoche();
    }

    /** Texte fuer Auftraege, deren Frist in den naechsten {@code tage} Tagen ablaeuft (oder ueberfaellig ist). */
    public List<String> anstehendeFristen(int tage) {
        List<String> liste = new ArrayList<>();
        LocalDate heute = LocalDate.now();
        for (Praxisauftrag a : auftraege) {
            LocalDate f = DetailView.fristVon(infoOrNull(a));
            if (f != null && !f.isAfter(heute.plusDays(tage))) {
                long diff = java.time.temporal.ChronoUnit.DAYS.between(heute, f);
                String wann = diff < 0 ? "überfällig seit " + f.format(DATE_FMT)
                        : diff == 0 ? "heute" : diff == 1 ? "morgen" : "am " + f.format(DATE_FMT);
                liste.add(UiKit.name(a) + " – " + wann);
            }
        }
        return liste;
    }

    private void zeichneWoche() {
        wochePanel.removeAll();
        List<AppStore.Block> bloeckeSortiert = new ArrayList<>(AppStore.get().state().bloecke);
        bloeckeSortiert.sort((x, y) -> String.valueOf(x.zeit).compareTo(String.valueOf(y.zeit)));
        LocalDate montag = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean etwas = false;
        for (int i = 0; i < 7; i++) {
            LocalDate d = montag.plusDays(i);
            String tag = d.equals(LocalDate.now()) ? "Heute"
                    : d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.GERMAN) + " " + d.format(KURZ);
            for (AppStore.Block b : bloeckeSortiert) {
                if (d.toString().equals(b.datum)) {
                    Praxisauftrag verknuepft = null;
                    for (Praxisauftrag a : auftraege) {
                        if (a.getDateiname().equals(b.auftrag)) {
                            verknuepft = a;
                        }
                    }
                    wochePanel.add(wochenZeile(tag, "🕒 " + b.zeit + " · " + b.dauer + " Min\n" + b.betreff,
                            verknuepft, Theme.CYAN));
                    etwas = true;
                }
            }
            Praxisauftrag t = termine.get(d);
            if (t != null) {
                wochePanel.add(wochenZeile(tag, "📌 " + UiKit.name(t), t, Theme.ACCENT));
                etwas = true;
            }
            for (Praxisauftrag a : auftraege) {
                if (d.equals(DetailView.fristVon(infoOrNull(a)))) {
                    wochePanel.add(wochenZeile(tag, "⏰ Frist: " + UiKit.name(a), a, Theme.PINK));
                    etwas = true;
                }
            }
        }
        if (!etwas) {
            wochePanel.add(UiKit.leerZustand("🎉", "Diese Woche ist frei",
                    "Keine Termine oder Fristen bis Sonntag."));
        }
        wochePanel.revalidate();
        wochePanel.repaint();
    }

    private JComponent wochenZeile(String tag, String text, Praxisauftrag a, Color farbe) {
        Karte z = new Karte(new BorderLayout(8, 0), Theme.CARD, farbe);
        z.setBorder(new EmptyBorder(6, 16, 6, 8));
        z.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel t = new JLabel(tag);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11.5f));
        t.setForeground(farbe);
        t.setVerticalAlignment(SwingConstants.TOP);
        z.add(t, BorderLayout.WEST);
        z.add(new WrapLabel(text, Theme.TEXT), BorderLayout.CENTER);
        z.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (a != null) {
                    oeffne.accept(a);
                }
            }
        });
        JPanel huelle = new JPanel(new BorderLayout());
        huelle.setOpaque(false);
        huelle.setBorder(new EmptyBorder(0, 0, 6, 0));
        huelle.add(z);
        huelle.setAlignmentX(Component.LEFT_ALIGNMENT);
        return huelle;
    }

    // --------------------------------------------------------- Karten

    private void zeichneAuftraege() {
        auftraegePanel.removeAll();
        String q = sucheFeld.getText().trim().toLowerCase(Locale.ROOT);
        boolean nurFav = favoritenFilter.isSelected();
        List<Praxisauftrag> sichtbar = new ArrayList<>();
        for (Praxisauftrag a : auftraege) {
            AuftragInfo info = infoOrNull(a);
            boolean fav = info != null && info.favorit;
            if (nurFav && !fav) {
                continue;
            }
            if (offenFilter.isSelected() && istErledigt(a)) {
                continue;
            }
            String heu = (UiKit.name(a) + " " + nz(a.getHandlung()) + " " + a.getDateiname()
                    + " " + nz(a.getLehrjahr()) + " " + (info == null ? "" : String.join(" ", info.tags)))
                    .toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !heu.contains(q)) {
                continue;
            }
            sichtbar.add(a);
        }
        // Sortierung: je nach Auswahl; innerhalb gleicher Werte: offene zuerst, dann Favoriten
        Comparator<Praxisauftrag> standard = (x, y) -> {
            int c = Boolean.compare(istErledigt(x), istErledigt(y));
            return c != 0 ? c : Boolean.compare(istFav(y), istFav(x));
        };
        Comparator<Praxisauftrag> wahl = switch (sortBox.getSelectedIndex()) {
            case 1 -> Comparator.comparing((Praxisauftrag a) -> nz(a.getLehrjahr()));
            case 2 -> Comparator.comparing((Praxisauftrag a) -> {
                LocalDate f = DetailView.fristVon(infoOrNull(a));
                return f == null ? LocalDate.MAX : f;
            });
            case 3 -> Comparator.comparing((Praxisauftrag a) -> UiKit.name(a).toLowerCase(Locale.ROOT));
            case 4 -> Comparator.comparing((Praxisauftrag a) -> istErledigt(a));
            default -> null;
        };
        sichtbar.sort(wahl == null ? standard : wahl.thenComparing(standard));

        int fertig = 0;
        for (Praxisauftrag a : auftraege) {
            if (istErledigt(a)) {
                fertig++;
            }
        }
        fortschritt.setWerte(fertig, auftraege.size());

        if (sichtbar.isEmpty()) {
            if (auftraege.isEmpty()) {
                auftraegePanel.add(UiKit.leerZustand("📂", "Keine Praxisaufträge",
                        "Prüfe den Ordnerpfad in den Einstellungen (⚙)."));
            } else {
                auftraegePanel.add(UiKit.leerZustand("🔍", "Keine Treffer",
                        nurFav ? "Du hast noch keine Favoriten markiert (☆ im Auftrag)."
                                : "Zu deiner Suche gibt es keinen Auftrag."));
            }
        }
        int i = 0;
        for (Praxisauftrag a : sichtbar) {
            auftraegePanel.add(auftragsKarte(a, i++));
            auftraegePanel.add(Box.createVerticalStrut(10));
        }
        auftraegePanel.revalidate();
        auftraegePanel.repaint();
    }

    private static boolean istErledigt(Praxisauftrag a) {
        AuftragInfo i = infoOrNull(a);
        return i != null && i.erledigt;
    }

    private static boolean istFav(Praxisauftrag a) {
        AuftragInfo i = infoOrNull(a);
        return i != null && i.favorit;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private JComponent auftragsKarte(Praxisauftrag a, int index) {
        boolean erledigt = istErledigt(a);
        Karte karte = new Karte(new BorderLayout(8, 0), Theme.CARD, erledigt ? Theme.LIME : Theme.akzent(index));
        karte.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        WrapLabel titel = new WrapLabel((erledigt ? "✔ " : "") + UiKit.name(a),
                erledigt ? Theme.MUTED : Theme.TEXT);
        titel.setFont(titel.getFont().deriveFont(Font.BOLD, 15f));
        titel.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(titel);

        StringBuilder meta = new StringBuilder();
        if (!nz(a.getLehrjahr()).isBlank()) {
            meta.append("Lehrjahr ").append(a.getLehrjahr());
        }
        if (!nz(a.getHandlung()).isBlank()) {
            meta.append(meta.length() > 0 ? " • " : "").append(a.getHandlung());
        }
        if (meta.length() > 0) {
            WrapLabel m = new WrapLabel(meta.toString(), Theme.MUTED);
            m.setFont(m.getFont().deriveFont(Font.PLAIN, 11f));
            m.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(Box.createVerticalStrut(3));
            text.add(m);
        }
        LocalDate frist = DetailView.fristVon(infoOrNull(a));
        if (frist != null && !erledigt) {
            long tage = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), frist);
            Color fc = tage < 0 ? Theme.PINK : tage < 3 ? Theme.ORANGE : Theme.MUTED;
            String rest = tage < 0 ? "überfällig" : tage == 0 ? "heute" : tage == 1 ? "morgen" : "in " + tage + " Tagen";
            JLabel f = new JLabel("⏰ Frist " + frist.format(DATE_FMT) + " (" + rest + ")");
            f.setFont(f.getFont().deriveFont(tage < 3 ? Font.BOLD : Font.PLAIN, 11.5f));
            f.setForeground(fc);
            f.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(Box.createVerticalStrut(4));
            text.add(f);
        }
        karte.add(text, BorderLayout.CENTER);

        AuftragInfo ai = infoOrNull(a);
        if (ai != null && ai.tags != null && !ai.tags.isEmpty()) {
            JPanel tagZeile = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            tagZeile.setOpaque(false);
            tagZeile.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (String tag : ai.tags) {
                JButton chip = UiKit.chip("🏷 " + tag, UiKit.tagFarbe(tag));
                chip.setToolTipText("Nach \"" + tag + "\" filtern");
                chip.addActionListener(e -> sucheFeld.setText(tag));
                tagZeile.add(chip);
            }
            text.add(Box.createVerticalStrut(5));
            text.add(tagZeile);
        }
        JPanel aktionen = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        aktionen.setOpaque(false);
        JLabel haken = new JLabel(erledigt ? "✔" : "○");
        haken.setFont(haken.getFont().deriveFont(Font.BOLD, 16f));
        haken.setForeground(erledigt ? Theme.LIME : Theme.MUTED);
        haken.setToolTipText(erledigt ? "Als offen markieren" : "Als erledigt markieren");
        haken.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        haken.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                AuftragInfo info = AppStore.get().info(a.getDateiname());
                info.erledigt = !info.erledigt;
                AppStore.get().speichern();
                zeichneAuftraege();
            }
        });
        aktionen.add(haken);
        JLabel stern = new JLabel(istFav(a) ? "★" : "☆");
        stern.setFont(stern.getFont().deriveFont(Font.BOLD, 18f));
        stern.setForeground(istFav(a) ? Theme.ORANGE : Theme.MUTED);
        stern.setVerticalAlignment(SwingConstants.TOP);
        stern.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        stern.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                AuftragInfo info = AppStore.get().info(a.getDateiname());
                info.favorit = !info.favorit;
                AppStore.get().speichern();
                zeichneAuftraege();
            }
        });
        aktionen.add(stern);
        karte.add(aktionen, BorderLayout.EAST);

        karte.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                oeffne.accept(a);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                karte.setBg(Theme.CARD_SELECTED);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                karte.setBg(Theme.CARD);
            }
        });
        return karte;
    }

    private void zeichneKombi() {
        kombiPanel.removeAll();
        List<List<Praxisauftrag>> gruppen = KombiGruppen.alle(auftraege, vorschlaege);
        if (gruppen.isEmpty()) {
            kombiPanel.add(UiKit.leerZustand("🧩", "Noch keine Kombi-Vorschläge",
                    "Sobald zwei Aufträge inhaltlich zusammenpassen, erscheinen sie hier."));
        }
        int g = 0;
        for (List<Praxisauftrag> gruppe : gruppen) {
            Color farbe = Theme.akzent(g++);
            Karte karte = new Karte(new BorderLayout(10, 0), Theme.CARD, farbe);

            JLabel badge = new JLabel(String.valueOf(gruppe.size()), SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics gr) {
                    Graphics2D g2 = (Graphics2D) gr.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(farbe);
                    g2.fillOval(0, 0, getWidth(), getHeight());
                    g2.dispose();
                    super.paintComponent(gr);
                }
            };
            badge.setForeground(Theme.aufAkzent());
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 13f));
            badge.setPreferredSize(new Dimension(30, 30));
            JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            badgeWrap.setOpaque(false);
            badgeWrap.add(badge);

            JPanel namen = new JPanel();
            namen.setOpaque(false);
            namen.setLayout(new BoxLayout(namen, BoxLayout.Y_AXIS));
            int nr = 0;
            for (Praxisauftrag a : gruppe) {
                nr++;
                Karte zeile = new Karte(new BorderLayout(10, 0), Theme.SURFACE, null);
                zeile.setBorder(new EmptyBorder(7, 10, 7, 10));
                zeile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                JLabel nummer = new JLabel(String.valueOf(nr), SwingConstants.CENTER);
                nummer.setForeground(farbe);
                nummer.setFont(nummer.getFont().deriveFont(Font.BOLD, 12f));
                nummer.setPreferredSize(new Dimension(22, 22));
                nummer.setBorder(BorderFactory.createLineBorder(farbe, 1, true));
                JPanel nummerWrap = new JPanel(new BorderLayout());
                nummerWrap.setOpaque(false);
                nummerWrap.add(nummer, BorderLayout.NORTH);
                zeile.add(nummerWrap, BorderLayout.WEST);
                zeile.add(new WrapLabel(UiKit.name(a), Theme.TEXT), BorderLayout.CENTER);
                zeile.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        oeffne.accept(a);
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        zeile.setBg(Theme.CARD_SELECTED);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        zeile.setBg(Theme.SURFACE);
                    }
                });
                if (nr > 1) {
                    namen.add(Box.createVerticalStrut(5));
                }
                namen.add(zeile);
            }
            JPanel badgeSpalte = new JPanel(new BorderLayout());
            badgeSpalte.setOpaque(false);
            badgeSpalte.add(badgeWrap, BorderLayout.NORTH);
            karte.add(badgeSpalte, BorderLayout.WEST);
            karte.add(namen, BorderLayout.CENTER);
            kombiPanel.add(karte);
            kombiPanel.add(Box.createVerticalStrut(10));
        }
        kombiPanel.revalidate();
        kombiPanel.repaint();
    }

    // --------------------------------------------------------- Outlook

    private void fuelleAuftragBox() {
        auftragBox.removeAllItems();
        auftragBox.addItem("– kein Auftrag –");
        for (Praxisauftrag a : auftraege) {
            auftragBox.addItem(UiKit.name(a));
        }
    }

    /** Zeichnet Kalender und Wochenuebersicht neu (z.B. nach neuen Zeitbloecken). */
    public void aktualisiereBloecke() {
        kalender.setBloecke(AppStore.get().state().bloecke);
        zeichneWoche();
    }

    private void blockEinplanen() {
        try {
            LocalDate date = LocalDate.parse(dateField.getText().strip(), DATE_FMT);
            LocalTime time = LocalTime.parse(timeField.getText().strip(), TIME_FMT);
            int idx = auftragBox.getSelectedIndex() - 1;
            AppStore.Block b = new AppStore.Block();
            b.datum = date.toString();
            b.zeit = time.format(TIME_FMT);
            b.dauer = (int) durationSpinner.getValue();
            b.betreff = subjectField.getText().strip();
            b.auftrag = idx >= 0 && idx < auftraege.size() ? auftraege.get(idx).getDateiname() : "";
            AppStore.get().state().bloecke.add(b);
            AppStore.get().speichern();
            aktualisiereBloecke();
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this,
                    "Bitte Datum als dd.MM.yyyy (z.B. 24.10.2026) und Uhrzeit als HH:mm (z.B. 14:30) eingeben.",
                    "Ungültige Eingabe", JOptionPane.WARNING_MESSAGE);
        }
    }
}
