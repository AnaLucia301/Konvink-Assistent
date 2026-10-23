package com.konvink.assistant.ui;

import com.konvink.assistant.model.KombiVorschlag;
import com.konvink.assistant.model.Praxisauftrag;
import com.konvink.assistant.parser.PraxisauftragParser;
import com.konvink.assistant.service.AiAssist;
import com.konvink.assistant.service.CombiSuggester;
import com.konvink.assistant.service.OllamaChatService;
import com.konvink.assistant.service.OutlookIntegration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hauptfenster des Konvink Assistant (Java/Swing).
 *
 * Java-Pendant zu main.py (dort customtkinter). Linke Seite: eigener
 * Kalender/Zeitblocker mit Outlook-Button. Rechte Seite: Liste der
 * geparsten Praxisaufträge mit KI-Hilfe-Button und Kombi-Vorschlägen.
 */
public class MainWindow extends JFrame {

    private static final Logger LOGGER = Logger.getLogger(MainWindow.class.getName());
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // Fest verbundener Ordner mit den Praxisauftrags-PDFs - wird beim
    // Start der App automatisch eingelesen, damit die Liste permanent
    // sichtbar ist, ohne jedes Mal manuell "PDFs laden" klicken zu müssen.
    private static final Path PRAXISAUFTRAEGE_ORDNER =
            Paths.get("downloads", "Praxisauftraege_Lehrjahr3");

    private final JTextField subjectField = new JTextField("Zeitblock");
    private final JTextField dateField = new JTextField(LocalDate.now().format(DATE_FMT));
    private final JTextField timeField = new JTextField(LocalTime.now().format(TIME_FMT));
    private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(60, 5, 480, 5));
    private final MiniCalendarPanel calendarPanel = new MiniCalendarPanel();

    private final DefaultListModel<Praxisauftrag> auftraegeListModel = new DefaultListModel<>();
    private final JList<Praxisauftrag> auftraegeList = new JList<>(auftraegeListModel);
    private final DefaultListModel<KombiVorschlag> kombiListModel = new DefaultListModel<>();
    private final JLabel statusLabel = new JLabel(" ");
    private final JPanel kombiKartenPanel = new VerticalStackPanel();

    /** Baut je Kombi-Gruppe eine Karte (Akzentstreifen, Badge mit Anzahl, Namen als Chips). */
    private void baueKombiKarten(java.util.List<java.util.List<Praxisauftrag>> gruppen) {
        kombiKartenPanel.removeAll();
        if (gruppen.isEmpty()) {
            JLabel leer = new JLabel("Keine Kombi-Vorschläge gefunden.");
            leer.setForeground(Theme.MUTED);
            leer.setBorder(new EmptyBorder(8, 8, 8, 8));
            kombiKartenPanel.add(leer);
        }
        for (java.util.List<Praxisauftrag> gruppe : gruppen) {
            final Color farbe = Theme.akzent(kombiKartenPanel.getComponentCount() / 2);
            JPanel karte = new JPanel(new BorderLayout(10, 0));
            karte.setBackground(Theme.CARD);
            karte.setAlignmentX(Component.LEFT_ALIGNMENT);
            karte.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 6, 0, 0, farbe),
                    new EmptyBorder(12, 12, 12, 12)));
            karte.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel badge = new JLabel(String.valueOf(gruppe.size()), SwingConstants.CENTER);
            badge.setOpaque(true);
            badge.setBackground(farbe);
            badge.setForeground(Theme.BG_APP);
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
                String titel = a.getTitel() == null || a.getTitel().isBlank() ? a.getDateiname() : a.getTitel();

                // Eigene Zeile je Auftrag: dunkler Untergrund, Nummer in der Gruppenfarbe,
                // Trennlinie nach oben - so ist klar erkennbar, welcher Auftrag welcher ist.
                JPanel zeile = new JPanel(new BorderLayout(10, 0));
                zeile.setBackground(Theme.SURFACE);
                zeile.setAlignmentX(Component.LEFT_ALIGNMENT);
                zeile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                zeile.setBorder(BorderFactory.createCompoundBorder(
                        nr > 1 ? BorderFactory.createMatteBorder(0, 0, 0, 0, Theme.CARD)
                               : BorderFactory.createEmptyBorder(),
                        new EmptyBorder(7, 10, 7, 10)));

                JLabel nummer = new JLabel(String.valueOf(nr), SwingConstants.CENTER);
                nummer.setOpaque(true);
                nummer.setBackground(Theme.CARD);
                nummer.setForeground(farbe);
                nummer.setFont(nummer.getFont().deriveFont(Font.BOLD, 12f));
                nummer.setPreferredSize(new Dimension(22, 22));
                nummer.setBorder(BorderFactory.createLineBorder(farbe, 1));
                JPanel nummerWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                nummerWrap.setOpaque(false);
                nummerWrap.add(nummer);

                JLabel l = new JLabel("<html><div style='width:200px'>" + titel.replace("<", "&lt;") + "</div></html>");
                l.setFont(l.getFont().deriveFont(Font.PLAIN, 12.5f));
                l.setForeground(Theme.TEXT);

                zeile.add(nummerWrap, BorderLayout.WEST);
                zeile.add(l, BorderLayout.CENTER);
                zeile.setMaximumSize(new Dimension(Integer.MAX_VALUE, zeile.getPreferredSize().height));

                // Klick auf einen einzelnen Auftrag oeffnet genau diesen
                final Praxisauftrag ziel = a;
                zeile.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseClicked(java.awt.event.MouseEvent e) {
                        onOpenPraxisauftrag(ziel);
                    }

                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        zeile.setBackground(Theme.CARD_SELECTED);
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        zeile.setBackground(Theme.SURFACE);
                    }
                });

                if (nr > 1) {
                    namen.add(Box.createVerticalStrut(5));
                }
                namen.add(zeile);
            }
            karte.add(badgeWrap, BorderLayout.WEST);
            karte.add(namen, BorderLayout.CENTER);
            karte.setMaximumSize(new Dimension(Integer.MAX_VALUE, karte.getPreferredSize().height));
            kombiKartenPanel.add(karte);
            kombiKartenPanel.add(Box.createVerticalStrut(10));
        }
        kombiKartenPanel.revalidate();
        kombiKartenPanel.repaint();
    }

    // CardLayout steuert, ob die Hauptansicht (Liste) oder die
    // Praxisauftrag-Detailansicht angezeigt wird - beides im selben
    // Anwendungsfenster, ohne separates Popup-Fenster.
    private static final String CARD_UEBERSICHT = "uebersicht";
    private static final String CARD_DETAIL = "detail";
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardsPanel = new JPanel(cardLayout);
    private JPanel aktuellesDetailPanel;

    public MainWindow() {
        super("Konvink Assistant");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 550));
        setLocationRelativeTo(null);
        // App soll immer maximiert starten, damit PDF/Chat/Optionen von
        // Anfang an genug Platz haben (wie im gewuenschten Screenshot).
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        auftraegeList.setCellRenderer(new PraxisauftragCellRenderer());
        auftraegeList.setFixedCellHeight(-1);
        auftraegeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        root.setBackground(BACKGROUND);
        root.add(buildCalendarPanel(), BorderLayout.WEST);
        root.add(buildPraxisauftragPanel(), BorderLayout.CENTER);

        cardsPanel.setBackground(BACKGROUND);
        cardsPanel.add(root, CARD_UEBERSICHT);
        setContentPane(cardsPanel);


        // Praxisaufträge direkt beim Start aus dem fest verbundenen Ordner
        // laden, damit die Liste permanent sichtbar ist.
        loadPraxisauftraegeFromFolder(PRAXISAUFTRAEGE_ORDNER);
    }

    private static final Color BACKGROUND = Theme.BG_APP;
    private static final Color ACCENT = Theme.ACCENT;

    private JPanel buildCalendarPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(titledBorder("Zeitblocker"));
        panel.setBackground(Theme.SURFACE);
        panel.setPreferredSize(new Dimension(300, 0));

        panel.add(formLabel("Betreff"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(styledField(subjectField));
        panel.add(Box.createVerticalStrut(12));

        panel.add(formLabel("Datum (dd.MM.yyyy)"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(styledField(dateField));
        panel.add(Box.createVerticalStrut(12));

        panel.add(formLabel("Uhrzeit (HH:mm)"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(styledField(timeField));
        panel.add(Box.createVerticalStrut(12));

        panel.add(formLabel("Dauer (Minuten)"));
        panel.add(Box.createVerticalStrut(4));
        durationSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        durationSpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        panel.add(durationSpinner);
        panel.add(Box.createVerticalStrut(20));

        JButton outlookButton = primaryButton("In Outlook eintragen");
        outlookButton.addActionListener(e -> onCreateOutlookEntry());
        outlookButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        outlookButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        panel.add(outlookButton);

        panel.add(Box.createVerticalStrut(20));
        panel.add(formLabel("Vorschlagstermine (Praxisaufträge)"));
        panel.add(Box.createVerticalStrut(6));
        calendarPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        calendarPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
        panel.add(calendarPanel);

        panel.add(Box.createVerticalGlue());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BACKGROUND);
        wrapper.add(panel, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildPraxisauftragPanel() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(BACKGROUND);

        // Der Ordner mit den Praxisaufträgen ist fest hinterlegt (siehe
        // PRAXISAUFTRAEGE_ORDNER) und wird automatisch beim Start geladen.
        // Die frueheren Lade-Buttons ("PDFs laden"/"Von Konvink laden")
        // werden daher nicht mehr gebraucht - stattdessen nur noch ein
        // kleiner, dezenter Link zum manuellen Neu-Laden/Ordnerwechsel.
        JButton reloadLink = new JButton("Ordner neu laden / wechseln");
        reloadLink.setFont(reloadLink.getFont().deriveFont(Font.PLAIN, 11f));
        reloadLink.setForeground(Theme.MUTED);
        reloadLink.setBorder(new EmptyBorder(2, 2, 2, 2));
        reloadLink.setContentAreaFilled(false);
        reloadLink.setFocusPainted(false);
        reloadLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        reloadLink.addActionListener(e -> onLoadPdfs());

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        topButtons.setBackground(BACKGROUND);
        topButtons.add(reloadLink);

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11.5f));
        statusLabel.setForeground(Theme.MUTED);
        statusLabel.setBorder(new EmptyBorder(6, 2, 0, 0));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(BACKGROUND);
        topPanel.add(topButtons, BorderLayout.CENTER);
        topPanel.add(statusLabel, BorderLayout.SOUTH);
        panel.add(topPanel, BorderLayout.NORTH);

        JPanel listsPanel = new JPanel(new GridLayout(1, 2, 14, 0));
        listsPanel.setBackground(BACKGROUND);

        JPanel auftraegePanel = new JPanel(new BorderLayout(8, 8));
        auftraegePanel.setBorder(titledBorder("Praxisaufträge"));
        auftraegePanel.setBackground(Theme.SURFACE);
        auftraegeList.setBackground(Theme.SURFACE);
        // Doppelklick auf einen Praxisauftrag oeffnet ihn in einem eigenen
        // Detail-Fenster innerhalb der Anwendung (siehe onOpenPraxisauftrag()).
        auftraegeList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = auftraegeList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        auftraegeList.setSelectedIndex(index);
                        onOpenPraxisauftrag(auftraegeListModel.getElementAt(index));
                    }
                }
            }
        });
        JScrollPane auftraegeScroll = new JScrollPane(auftraegeList);
        auftraegeScroll.setBorder(BorderFactory.createEmptyBorder());
        auftraegePanel.add(auftraegeScroll, BorderLayout.CENTER);
        // Hinweis: Der fruehere "KI-Hilfe beim Schreiben"-Button wurde hier
        // entfernt - die KI-Integration soll an anderer Stelle (anders)
        // eingebaut werden. Die Logik dazu steht weiterhin in onAiAssist()
        // bzw. AiAssist zur Verfuegung.

        JPanel kombiPanel = new JPanel(new BorderLayout(8, 8));
        kombiPanel.setBorder(titledBorder("Kombi-Vorschläge"));
        kombiPanel.setBackground(Theme.SURFACE);
        kombiKartenPanel.setBackground(Theme.SURFACE);
        JScrollPane kombiScroll = new JScrollPane(kombiKartenPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        kombiScroll.setBorder(BorderFactory.createEmptyBorder());
        kombiScroll.getVerticalScrollBar().setUnitIncrement(16);
        flatScrollBar(kombiScroll);
        kombiPanel.add(kombiScroll, BorderLayout.CENTER);

        listsPanel.add(auftraegePanel);
        listsPanel.add(kombiPanel);
        panel.add(listsPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Lädt alle Praxisauftrags-PDFs aus dem angegebenen Ordner im
     * Hintergrund und aktualisiert danach die Listen + Statuszeile.
     * Wird sowohl beim Start als auch beim Klick auf "PDFs laden" genutzt,
     * sodass der Ordner permanent mit dem Fenster verbunden bleibt.
     */
    private void loadPraxisauftraegeFromFolder(Path folder) {
        statusLabel.setText("Lade Praxisaufträge aus '" + folder + "' ...");

        new SwingWorker<Void, Void>() {
            private List<Praxisauftrag> auftraege = List.of();
            private List<KombiVorschlag> vorschlaege = List.of();
            private Map<LocalDate, Praxisauftrag> terminZuordnung = Map.of();
            private String fehler;

            @Override
            protected Void doInBackground() {
                if (!folder.toFile().isDirectory()) {
                    fehler = "Ordner nicht gefunden: " + folder.toAbsolutePath();
                    return null;
                }
                auftraege = PraxisauftragParser.parseAllPraxisauftraege(folder);
                vorschlaege = CombiSuggester.findCombiVorschlaege(auftraege);
                try {
                    // Greift dabei auf den Outlook-Kalender zu, um volle/freie Tage zu
                    // erkennen - daher bewusst im Hintergrundthread (nicht auf dem EDT).
                    // Bewusst robust: Falls die Outlook-Abfrage fehlschlaegt (z.B. Outlook
                    // nicht installiert/erreichbar), sollen die Praxisaufträge trotzdem
                    // geladen werden und es wird auf eine einfache Terminverteilung
                    // ohne Outlook-Abgleich zurueckgefallen.
                    terminZuordnung = berechneVorschlagstermine(auftraege);
                } catch (Throwable e) {
                    LOGGER.log(Level.WARNING, "Terminvorschlaege konnten nicht berechnet werden", e);
                    terminZuordnung = berechneEinfacheTerminverteilung(auftraege);
                }
                return null;
            }

            @Override
            protected void done() {
                if (fehler != null) {
                    statusLabel.setText(fehler);
                    LOGGER.warning(fehler);
                    return;
                }
                auftraegeListModel.clear();
                auftraege.forEach(auftraegeListModel::addElement);
                kombiListModel.clear();
                vorschlaege.forEach(kombiListModel::addElement);
                java.util.List<java.util.List<Praxisauftrag>> gruppen = findAlleKombiGruppen(auftraege, vorschlaege);
                baueKombiKarten(gruppen);
                statusLabel.setText(auftraege.size() + " Praxisaufträge geladen aus: " + folder);
                calendarPanel.setVorschlaege(terminZuordnung);
            }
        }.execute();
    }

    /**
     * Anzahl Wochen zwischen zwei Terminvorschlägen: 1 = woechentlich, 2 = alle zwei Wochen.
     * Kann bei Bedarf einfach angepasst werden.
     */
    private static final int VORSCHLAG_INTERVALL_WOCHEN = 1;

    /** Ab dieser Belegung (Minuten) im Outlook-Kalender gilt ein Tag als "voll". */
    private static final int BUSY_SCHWELLE_MINUTEN = 180;

    /**
     * Verteilt die geladenen Praxisauftraege auf einen Termin pro Woche
     * (bzw. alle zwei Wochen, siehe VORSCHLAG_INTERVALL_WOCHEN), beginnend ab
     * kommendem Werktag. Dabei wird fuer jeden Kandidaten-Tag der Outlook-Kalender
     * geprueft: an vollen Tagen (viel bereits verplant) wird ein kleinerer
     * Praxisauftrag vorgeschlagen, an freien Tagen ein groesserer. Die "Groesse"
     * eines Auftrags wird ueber die Laenge seines Rohtexts angenaehert.
     */
    private static Map<LocalDate, Praxisauftrag> berechneVorschlagstermine(List<Praxisauftrag> auftraege) {
        Map<LocalDate, Praxisauftrag> termine = new java.util.LinkedHashMap<>();
        if (auftraege.isEmpty()) {
            return termine;
        }

        // Nach Groesse (Laenge Rohtext) aufsteigend sortieren, damit wir je nach
        // Tagesbelegung vom kleinsten oder groessten Ende entnehmen koennen.
        java.util.LinkedList<Praxisauftrag> verbleibend = new java.util.LinkedList<>(auftraege);
        verbleibend.sort(java.util.Comparator.comparingInt(a -> a.getRohtext().length()));

        LocalDate datum = LocalDate.now().plusDays(1);
        long schrittTage = 7L * VORSCHLAG_INTERVALL_WOCHEN;

        while (!verbleibend.isEmpty()) {
            while (datum.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                    || datum.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                datum = datum.plusDays(1);
            }

            int belegteMinuten;
            try {
                belegteMinuten = OutlookIntegration.getBusyMinutesForDate(datum);
            } catch (Throwable e) {
                // Einzelnen Tag bei Outlook-Problemen einfach als "frei" behandeln,
                // statt die komplette Terminverteilung abzubrechen.
                LOGGER.log(Level.FINE, "Outlook-Abfrage fehlgeschlagen fuer " + datum, e);
                belegteMinuten = 0;
            }
            Praxisauftrag auftrag = belegteMinuten >= BUSY_SCHWELLE_MINUTEN
                    ? verbleibend.removeFirst()  // voller Tag -> kleiner Auftrag
                    : verbleibend.removeLast();  // freier Tag -> groesserer Auftrag

            termine.put(datum, auftrag);
            datum = datum.plusDays(schrittTage);
        }
        return termine;
    }

    /**
     * Einfacher Fallback ohne Outlook-Abgleich: verteilt die Praxisauftraege
     * (in geladener Reihenfolge) auf einen Termin pro Woche, beginnend ab
     * kommendem Werktag. Wird genutzt, falls berechneVorschlagstermine() aus
     * irgendeinem Grund fehlschlaegt (z.B. Outlook nicht verfuegbar).
     */
    private static Map<LocalDate, Praxisauftrag> berechneEinfacheTerminverteilung(List<Praxisauftrag> auftraege) {
        Map<LocalDate, Praxisauftrag> termine = new java.util.LinkedHashMap<>();
        LocalDate datum = LocalDate.now().plusDays(1);
        long schrittTage = 7L * VORSCHLAG_INTERVALL_WOCHEN;
        for (Praxisauftrag auftrag : auftraege) {
            while (datum.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                    || datum.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                datum = datum.plusDays(1);
            }
            termine.put(datum, auftrag);
            datum = datum.plusDays(schrittTage);
        }
        return termine;
    }

    // ---- kleine UI-Hilfsmethoden fuer ein konsistentes Erscheinungsbild ----


    private static javax.swing.border.Border titledBorder(String title) {
        var titled = BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(), title);
        titled.setTitleFont(titled.getTitleFont().deriveFont(Font.BOLD, 13f));
        titled.setTitleColor(Theme.TEXT);
        return new CompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                new CompoundBorder(new EmptyBorder(10, 14, 14, 14), titled));
    }

    private static JLabel formLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        label.setForeground(Theme.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JTextField styledField(JTextField field) {
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        field.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                new EmptyBorder(4, 8, 4, 8)));
        return field;
    }

    private static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(ACCENT);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 12.5f));
        button.setBorder(new EmptyBorder(8, 14, 8, 14));
        button.setOpaque(true);
        button.setBorderPainted(false);
        return button;
    }

    private static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(Theme.SURFACE);
        button.setForeground(Theme.TEXT);
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 12.5f));
        button.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                new EmptyBorder(8, 14, 8, 14)));
        return button;
    }


    private void onCreateOutlookEntry() {
        try {
            LocalDate date = LocalDate.parse(dateField.getText().strip(), DATE_FMT);
            LocalTime time = LocalTime.parse(timeField.getText().strip(), TIME_FMT);
            LocalDateTime start = LocalDateTime.of(date, time);
            int duration = (int) durationSpinner.getValue();
            String subject = subjectField.getText().strip();

            OutlookIntegration.createTimeBlockAndShow(subject, start, duration, "");
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Fehler beim Anlegen des Outlook-Termins", ex);
            JOptionPane.showMessageDialog(this,
                    "Fehler beim Anlegen des Termins: " + ex.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onLoadPdfs() {
        // Ordner-Dialog nur noch, um EINMALIG einen abweichenden Ordner zu
        // waehlen - danach bleibt der zuletzt gewaehlte Ordner nicht
        // dauerhaft gespeichert, der Standard-Ordner ist weiterhin
        // PRAXISAUFTRAEGE_ORDNER (siehe loadPraxisauftraegeFromFolder()).
        JFileChooser chooser = new JFileChooser(PRAXISAUFTRAEGE_ORDNER.toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        loadPraxisauftraegeFromFolder(chooser.getSelectedFile().toPath());
    }

    /**
     * Oeffnet einen Praxisauftrag in der Detailansicht - eingebettet im
     * selben Anwendungsfenster (kein separates Popup-Fenster), per
     * CardLayout-Wechsel. Layout: links das PDF (volle Hoehe), rechts oben
     * die vorgeschlagenen Themen/Kombi-Vorschlaege, rechts unten der KI-Chat.
     */
    private void onOpenPraxisauftrag(Praxisauftrag auftrag) {
        // Vorherige Detailansicht entfernen (z.B. alten Chat-Verlauf), damit
        // beim erneuten Oeffnen eine frische Ansicht mit neuem Kontext entsteht.
        if (aktuellesDetailPanel != null) {
            cardsPanel.remove(aktuellesDetailPanel);
        }

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BACKGROUND);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(BACKGROUND);
        headerPanel.setBorder(new EmptyBorder(10, 14, 0, 14));
        JButton zurueckButton = secondaryButton("← Zurück zur Übersicht");
        zurueckButton.addActionListener(e -> cardLayout.show(cardsPanel, CARD_UEBERSICHT));
        JLabel titelLabel = new JLabel(auftrag.getDateiname());
        titelLabel.setFont(titelLabel.getFont().deriveFont(Font.BOLD, 14f));
        titelLabel.setBorder(new EmptyBorder(0, 14, 0, 0));
        headerPanel.add(zurueckButton, BorderLayout.WEST);
        headerPanel.add(titelLabel, BorderLayout.CENTER);
        content.add(headerPanel, BorderLayout.NORTH);

        // ---- Links: PDF-Anzeige, auf Fensterhoehe skaliert ----
        JPanel pdfSide = new JPanel(new BorderLayout());
        pdfSide.setBackground(Theme.PDF_BG); // typisches PDF-Viewer-Grau

        JPanel pdfPagesPanel = new VerticalStackPanel();
        pdfPagesPanel.setBackground(pdfSide.getBackground());
        pdfPagesPanel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JScrollPane scrollPane = new JScrollPane(pdfPagesPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        // Vertikale Scrollbar IMMER anzeigen (statt AS_NEEDED): dadurch
        // bleibt die Breite des Viewports konstant, bevor/nachdem das Bild
        // skaliert wurde. Mit AS_NEEDED aendert sich die Viewport-Breite
        // naemlich GENAU in dem Moment, in dem die Scrollbar wegen des neu
        // gesetzten (zu hohen) Bildes erscheint - das fuehrte dazu, dass
        // die vorher berechnete Breite nicht mehr passte und die Seite
        // leicht abgeschnitten wirkte.
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        flatScrollBar(scrollPane);
        pdfSide.add(scrollPane, BorderLayout.CENTER);

        // ---- Rechts oben: vorgeschlagene Themen + Kombi-Vorschlaege ----
        JPanel optionsSide = buildPraxisauftragOptionsPanel(auftrag);

        // ---- Rechts unten: persistenter KI-Chat ----
        JPanel chatSide = buildKiChatPanel(auftrag);
        JPanel chatWrapper = new JPanel(new BorderLayout());
        chatWrapper.setBackground(Theme.SURFACE);
        chatWrapper.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        chatWrapper.add(chatSide, BorderLayout.CENTER);

        // Echter JSplitPane (statt BorderLayout) zwischen Themen/Kombi und
        // KI-Chat: dadurch bekommt der Nutzer einen per Maus verschiebbaren
        // Trenner (Drag-Handle), um selbst zu bestimmen, wie viel Platz der
        // obere (Themen/Kombi) bzw. untere (Chat) Bereich einnimmt.
        JSplitPane rightSide = new JSplitPane(JSplitPane.VERTICAL_SPLIT, optionsSide, chatWrapper);
        rightSide.setResizeWeight(0.4);
        rightSide.setDividerSize(8);
        rightSide.setBorder(BorderFactory.createEmptyBorder());
        rightSide.setBackground(Theme.SURFACE);

        // Fixer 50/50-Split: links PDF, rechts (Themen/Kombi + Chat
        // darunter) - beide Haelften gleich breit, und bleiben das auch
        // beim Fenster-Resize (resizeWeight = 0.5). Der Nutzer soll diesen
        // Trenner NICHT per Maus verschieben koennen (anders als den
        // Themen/Chat-Trenner darueber) - daher deaktiviert (setEnabled(false)),
        // was in Swing auch den Verschiebe-Cursor am Divider unterdrueckt.
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pdfSide, rightSide);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerSize(6);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setEnabled(false);

        content.add(splitPane, BorderLayout.CENTER);

        aktuellesDetailPanel = content;
        cardsPanel.add(content, CARD_DETAIL);
        cardLayout.show(cardsPanel, CARD_DETAIL);

        // Divider explizit auf die Mitte setzen, NACHDEM das Layout
        // berechnet wurde (setResizeWeight allein wirkt nur bei spaeteren
        // Groessenaenderungen, nicht auf die initiale Divider-Position).
        SwingUtilities.invokeLater(() -> {
            splitPane.setDividerLocation(0.5);
            rightSide.setDividerLocation(0.4);
        });


        if (auftrag.getPdfPfad() == null || !auftrag.getPdfPfad().toFile().isFile()) {
            pdfPagesPanel.add(new JLabel("PDF-Datei nicht gefunden: " + auftrag.getDateiname()));
            return;
        }

        // Rendern der PDF-Seiten als Bilder im Hintergrund, damit die UI nicht
        // blockiert (bei vielen/grossen Seiten kann das etwas dauern). Es wird
        // in hoher Aufloesung gerendert und anschliessend in der UI auf die
        // volle verfuegbare Fensterhoehe herunterskaliert, damit eine Seite
        // komplett auf einen Blick sichtbar ist (wie bei einem PDF-Viewer).
        // Es wird bewusst NUR die erste Seite angezeigt (Praxisauftraege
        // bestehen oft aus mehreren Seiten, aber fuer die Uebersicht reicht
        // die erste Seite mit der Aufgabenstellung).
        new SwingWorker<java.util.List<BufferedImage>, Void>() {
            private Exception fehler;

            @Override
            protected java.util.List<BufferedImage> doInBackground() {
                java.util.List<BufferedImage> seiten = new java.util.ArrayList<>();
                try (PDDocument document = PDDocument.load(auftrag.getPdfPfad().toFile())) {
                    PDFRenderer renderer = new PDFRenderer(document);
                    if (document.getNumberOfPages() > 0) {
                        seiten.add(renderer.renderImageWithDPI(0, 200));
                    }
                } catch (Exception e) {
                    fehler = e;
                }

                return seiten;
            }

            @Override
            protected void done() {
                if (fehler != null) {
                    LOGGER.log(Level.WARNING, "PDF konnte nicht gerendert werden: " + auftrag.getPdfPfad(), fehler);
                    pdfPagesPanel.add(new JLabel("Fehler beim Anzeigen des PDFs: " + fehler.getMessage()));
                    pdfPagesPanel.revalidate();
                    pdfPagesPanel.repaint();
                    return;
                }
                try {
                    java.util.List<BufferedImage> seiten = get();
                    java.util.List<JLabel> pageLabels = new java.util.ArrayList<>();
                    for (BufferedImage seite : seiten) {
                        JLabel pageLabel = new JLabel();
                        pageLabel.putClientProperty("originalImage", seite);
                        pageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                        pageLabel.setBorder(BorderFactory.createLineBorder(Theme.TEXT));
                        pageLabels.add(pageLabel);
                        pdfPagesPanel.add(pageLabel);
                        pdfPagesPanel.add(Box.createVerticalStrut(14));
                    }

                    // Skaliert die Seite so, dass sie IMMER komplett sichtbar
                    // ist (sowohl Breite als auch Hoehe passen in den
                    // sichtbaren Bereich, ohne Scrollbars) - es wird der
                    // kleinere der beiden Skalierungsfaktoren verwendet.
                    // Wird initial (nach vollstaendigem Layout) einmal
                    // ausgefuehrt und danach bei jeder Groessenaenderung erneut.
                    Runnable rescale = () -> {
                        // 32 = 2x16 Padding des pdfPagesPanel, +2 Puffer fuer
                        // den 1px-Rahmen des pageLabel selbst, damit die
                        // Seite WIRKLICH komplett passt (keine Rundungs-
                        // Ueberschreitung, die sonst eine Scrollbar ausloest).
                        int verfuegbareBreite = scrollPane.getViewport().getWidth() - 34;
                        int verfuegbareHoehe = scrollPane.getViewport().getHeight() - 34;
                        if (verfuegbareBreite <= 0 || verfuegbareHoehe <= 0) {
                            return;
                        }
                        for (JLabel pageLabel : pageLabels) {
                            BufferedImage original = (BufferedImage) pageLabel.getClientProperty("originalImage");
                            float skalierungHoehe = verfuegbareHoehe / (float) original.getHeight();
                            float skalierungBreite = verfuegbareBreite / (float) original.getWidth();
                            float skalierung = Math.min(skalierungHoehe, skalierungBreite);
                            int zielBreite = Math.max(1, Math.round(original.getWidth() * skalierung));
                            int zielHoehe = Math.max(1, Math.round(original.getHeight() * skalierung));
                            Image skaliert = original.getScaledInstance(zielBreite, zielHoehe, Image.SCALE_SMOOTH);
                            pageLabel.setIcon(new ImageIcon(skaliert));

                        }
                        pdfPagesPanel.revalidate();
                        pdfPagesPanel.repaint();
                    };

                    // Mehrfach verzoegert ausfuehren: beim ersten Anzeigen der
                    // Detailansicht ist die tatsaechliche Groesse der
                    // SplitPane/des Viewports oft noch nicht final (Swing
                    // braucht mehrere Layout-Durchlaeufe, besonders wenn das
                    // Fenster gleichzeitig maximiert wird) - mehrere
                    // zeitversetzte Versuche stellen zuverlaessig sicher,
                    // dass am Ende die tatsaechliche Endgroesse verwendet wird.
                    SwingUtilities.invokeLater(rescale);
                    new Timer(100, e -> rescale.run()) {{ setRepeats(false); }}.start();
                    new Timer(300, e -> rescale.run()) {{ setRepeats(false); }}.start();
                    new Timer(600, e -> rescale.run()) {{ setRepeats(false); }}.start();
                    scrollPane.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
                        @Override
                        public void componentResized(java.awt.event.ComponentEvent e) {
                            rescale.run();
                        }
                    });

                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "PDF-Seiten konnten nicht angezeigt werden", e);
                }
                pdfPagesPanel.revalidate();
                pdfPagesPanel.repaint();
            }
        }.execute();
    }

    /**
     * Baut die rechte Seitenleiste im Praxisauftrag-Detail-Fenster:
     * Titel/Meta-Infos, vorgeschlagene Themen (aus den Teilaufgaben des
     * PDFs abgeleitet), kompatible Praxisaufträge fuer einen Kombiauftrag
     * (aus den bereits berechneten Kombi-Vorschlägen) sowie unten rechts
     * KI-Optionen fuer diesen Auftrag.
     */
    private JPanel buildPraxisauftragOptionsPanel(Praxisauftrag auftrag) {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(Theme.SURFACE);
        panel.setBorder(new EmptyBorder(18, 16, 18, 16));

        // ---- Kopfbereich: Titel + Meta ----
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(Theme.SURFACE);

        String titel = auftrag.getTitel() == null || auftrag.getTitel().isBlank()
                ? auftrag.getDateiname() : auftrag.getTitel();
        JLabel titleLabel = new JLabel("<html><div style='width:260px;'>" + titel + "</div></html>");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        StringBuilder meta = new StringBuilder();
        if (auftrag.getLehrjahr() != null && !auftrag.getLehrjahr().isBlank()) {
            meta.append("Lehrjahr ").append(auftrag.getLehrjahr());
        }
        if (auftrag.getHandlung() != null && !auftrag.getHandlung().isBlank()) {
            if (meta.length() > 0) {
                meta.append("   •   ");
            }
            meta.append(auftrag.getHandlung());
        }
        JLabel metaLabel = formLabel(meta.toString());
        metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(titleLabel);
        header.add(Box.createVerticalStrut(8));
        header.add(metaLabel);
        panel.add(header, BorderLayout.NORTH);

        // ---- Mittelbereich: KI-Lösungsvorschläge + kompatible Auftraege ----
        JPanel middle = new VerticalStackPanel();
        middle.setBackground(Theme.SURFACE);

        middle.add(sectionHeading("Lösungsvorschläge (KI)"));
        middle.add(Box.createVerticalStrut(6));

        JLabel themenLadeLabel = formLabel("KI generiert Lösungsvorschläge …");
        themenLadeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        middle.add(themenLadeLabel);
        ladeLoesungsvorschlaege(auftrag, middle, themenLadeLabel);


        middle.add(Box.createVerticalStrut(18));

        // Ein Kombiauftrag besteht aus mindestens 2 Praxisaufträgen, kann aber
        // auch aus mehr bestehen, wenn alle zusammen passen (transitiv
        // verkettete Aehnlichkeit). Deshalb wird hier nicht nur der direkte
        // Partner aus EINEM Paar-Vorschlag angezeigt, sondern die komplette
        // zusammenhaengende Gruppe aller untereinander kompatiblen Aufträge.
        java.util.List<Praxisauftrag> gruppe = findKombiGruppe(auftrag);

        if (gruppe.size() < 2) {
            middle.add(sectionHeading("Kompatible Praxisaufträge (Kombi)"));
            middle.add(Box.createVerticalStrut(6));
            JLabel keineKombi = formLabel("Keine passenden Kombi-Vorschläge gefunden.");
            keineKombi.setAlignmentX(Component.LEFT_ALIGNMENT);
            middle.add(keineKombi);
        } else {
            middle.add(sectionHeading("Kombi-Gruppe (" + gruppe.size() + " Aufträge)"));
            middle.add(Box.createVerticalStrut(6));
            JLabel hinweis = formLabel("Diese Aufträge passen inhaltlich zusammen und können gemeinsam als Kombiauftrag bearbeitet werden:");
            hinweis.setAlignmentX(Component.LEFT_ALIGNMENT);
            middle.add(hinweis);
            middle.add(Box.createVerticalStrut(8));

            for (Praxisauftrag andererAuftrag : gruppe) {
                if (andererAuftrag == auftrag) {
                    continue;
                }
                String andererTitel = andererAuftrag.getTitel() == null || andererAuftrag.getTitel().isBlank()
                        ? andererAuftrag.getDateiname() : andererAuftrag.getTitel();

                double aehnlichkeit = bestAehnlichkeitZu(auftrag, andererAuftrag);

                JButton kombiButton = new JButton("<html><div style='width:230px;'>"
                        + andererTitel + "</div></html>");
                kombiButton.setHorizontalAlignment(SwingConstants.LEFT);
                kombiButton.setAlignmentX(Component.LEFT_ALIGNMENT);
                kombiButton.setFocusPainted(false);
                kombiButton.setFont(kombiButton.getFont().deriveFont(Font.PLAIN, 12f));
                kombiButton.setToolTipText(aehnlichkeit >= 0
                        ? "Ähnlichkeit: " + Math.round(aehnlichkeit * 100) + "% – klicken zum Öffnen"
                        : "Teil derselben Kombi-Gruppe – klicken zum Öffnen");
                kombiButton.addActionListener(e -> onOpenPraxisauftrag(andererAuftrag));
                middle.add(kombiButton);
                middle.add(Box.createVerticalStrut(4));
            }
        }

        JScrollPane middleScroll = new JScrollPane(middle,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        middleScroll.setBorder(BorderFactory.createEmptyBorder());
        flatScrollBar(middleScroll);
        panel.add(middleScroll, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Ermittelt die komplette zusammenhaengende Gruppe von Praxisaufträgen,
     * die untereinander (direkt oder transitiv ueber gemeinsame Nachbarn)
     * als kombinierbar erkannt wurden. Ein Kombiauftrag besteht damit immer
     * aus mindestens 2 Aufträgen - koennen aber auch mehr sein, sofern sie
     * alle zusammenpassen (z.B. A+B und B+C kompatibel => Gruppe {A,B,C}).
     * Enthaelt den uebergebenen Auftrag selbst immer als erstes Element.
     */
    private java.util.List<Praxisauftrag> findKombiGruppe(Praxisauftrag start) {
        java.util.LinkedHashSet<Praxisauftrag> gruppe = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<Praxisauftrag> offen = new java.util.ArrayDeque<>();
        gruppe.add(start);
        offen.add(start);

        while (!offen.isEmpty()) {
            Praxisauftrag aktuell = offen.poll();
            for (int i = 0; i < kombiListModel.size(); i++) {
                KombiVorschlag vorschlag = kombiListModel.getElementAt(i);
                Praxisauftrag nachbar = null;
                if (vorschlag.getAuftrag1() == aktuell) {
                    nachbar = vorschlag.getAuftrag2();
                } else if (vorschlag.getAuftrag2() == aktuell) {
                    nachbar = vorschlag.getAuftrag1();
                }
                if (nachbar != null && gruppe.add(nachbar)) {
                    offen.add(nachbar);
                }
            }
        }

        return new java.util.ArrayList<>(gruppe);
    }

    /**
     * Berechnet ALLE zusammenhaengenden Kombi-Gruppen ueber die komplette
     * Liste der Praxisaufträge (fuer die Uebersichtsseite). Jede Gruppe
     * besteht aus mindestens 2 Praxisaufträgen - kann aber auch mehr
     * enthalten, wenn mehrere Aufträge transitiv zueinander passen
     * (A+B und B+C kompatibel => eine gemeinsame Gruppe {A,B,C} statt
     * zwei separaten Paaren). Nutzt dieselbe Nachbarschaftslogik wie
     * {@link #findKombiGruppe(Praxisauftrag)}, vermeidet aber doppelte
     * Gruppen fuer bereits besuchte Aufträge.
     */
    private java.util.List<java.util.List<Praxisauftrag>> findAlleKombiGruppen(
            java.util.List<Praxisauftrag> alleAuftraege, java.util.List<KombiVorschlag> alleVorschlaege) {
        java.util.List<java.util.List<Praxisauftrag>> gruppen = new java.util.ArrayList<>();
        java.util.Set<Praxisauftrag> besucht = new java.util.HashSet<>();

        for (Praxisauftrag start : alleAuftraege) {
            if (besucht.contains(start)) {
                continue;
            }
            java.util.LinkedHashSet<Praxisauftrag> gruppe = new java.util.LinkedHashSet<>();
            java.util.ArrayDeque<Praxisauftrag> offen = new java.util.ArrayDeque<>();
            gruppe.add(start);
            offen.add(start);

            while (!offen.isEmpty()) {
                Praxisauftrag aktuell = offen.poll();
                for (KombiVorschlag vorschlag : alleVorschlaege) {
                    Praxisauftrag nachbar = null;
                    if (vorschlag.getAuftrag1() == aktuell) {
                        nachbar = vorschlag.getAuftrag2();
                    } else if (vorschlag.getAuftrag2() == aktuell) {
                        nachbar = vorschlag.getAuftrag1();
                    }
                    if (nachbar != null && gruppe.add(nachbar)) {
                        offen.add(nachbar);
                    }
                }
            }

            besucht.addAll(gruppe);
            if (gruppe.size() >= 2) {
                gruppen.add(new java.util.ArrayList<>(gruppe));
            }
        }

        return gruppen;
    }

    /**
     * Liefert die (hoechste) direkt gemessene Aehnlichkeit zwischen zwei
     * Aufträgen, falls dafuer ein Paar-Vorschlag existiert - sonst -1 (z.B.
     * wenn die beiden nur transitiv ueber einen dritten Auftrag verbunden sind).
     */
    private double bestAehnlichkeitZu(Praxisauftrag a, Praxisauftrag b) {
        double best = -1;
        for (int i = 0; i < kombiListModel.size(); i++) {
            KombiVorschlag vorschlag = kombiListModel.getElementAt(i);
            boolean passt = (vorschlag.getAuftrag1() == a && vorschlag.getAuftrag2() == b)
                    || (vorschlag.getAuftrag1() == b && vorschlag.getAuftrag2() == a);
            if (passt) {
                best = Math.max(best, vorschlag.getAehnlichkeit());
            }
        }
        return best;
    }

    /**
     * Zeigt einen In-App-Dialog mit dem fertigen KI-Prompt fuer den Auftrag
     * und mehreren Optionen, wie er weiterverwendet werden kann. Der frueher
     * automatisch geoeffnete Teams-Deeplink fuehrte in der BMW-Umgebung oft
     * nur zur BMW-Startseite (Redirect-Problem) - deshalb jetzt bewusst ohne
     * automatisches Oeffnen, sondern mit expliziter Auswahl durch den Nutzer.
     */
    /**
     * Baut ein persistentes KI-Chat-Panel (wie die Copilot-Chat-Seitenleiste
     * in VS Code), das direkt links im Praxisauftrag-Detail-Fenster sitzt.
     * Angebunden an ein lokal laufendes Ollama (http://localhost:11434,
     * Modell "llama3.2"). Fragen werden im Hintergrund-Thread gesendet
     * (damit die UI nicht blockiert) und die Antwort erscheint automatisch
     * als Sprechblase - vollstaendig automatisch, ohne manuellen Zwischenschritt.
     */
    private JPanel buildKiChatPanel(Praxisauftrag auftrag) {
        OllamaChatService chatService = new OllamaChatService();

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBackground(Theme.SURFACE);

        // ---- Kopfzeile wie bei einem Chat-Panel in VS Code ----
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.CARD);
        header.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(10, 14, 10, 14)));
        JLabel titel = new JLabel("KI-Chat");
        titel.setFont(titel.getFont().deriveFont(Font.BOLD, 13.5f));
        JLabel untertitel = new JLabel("Ollama · llama3.2 · lokal");
        untertitel.setFont(untertitel.getFont().deriveFont(Font.PLAIN, 10.5f));
        untertitel.setForeground(Theme.MUTED);
        JPanel titelBox = new JPanel();
        titelBox.setLayout(new BoxLayout(titelBox, BoxLayout.Y_AXIS));
        titelBox.setOpaque(false);
        titelBox.add(titel);
        titelBox.add(untertitel);
        header.add(titelBox, BorderLayout.WEST);
        content.add(header, BorderLayout.NORTH);

        JPanel verlaufPanel = new VerticalStackPanel();
        verlaufPanel.setBackground(Theme.SURFACE);
        verlaufPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        JScrollPane verlaufScroll = new JScrollPane(verlaufPanel);
        verlaufScroll.setBorder(BorderFactory.createEmptyBorder());
        verlaufScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        flatScrollBar(verlaufScroll);
        content.add(verlaufScroll, BorderLayout.CENTER);

        // Praxisauftrag-Kontext als Systemnachricht, damit das Modell direkt
        // weiss, worum es geht (wird nicht als sichtbare Sprechblase gezeigt).
        String kontext = "Du bist ein hilfreicher Assistent fuer kaufmaennische Lernende. "
                + "Hilf praegnant und auf Deutsch beim Beantworten/Formulieren fuer folgenden "
                + "Praxisauftrag:\n\n" + AiAssist.buildPraxisauftragPrompt(auftrag);
        chatService.setSystemKontext(kontext);

        addChatBubble(verlaufPanel, "System",
                "Kontext zum Praxisauftrag wurde geladen. Du kannst direkt loslegen, z.B. "
                        + "\"Hilf mir bei Teilaufgabe 1\".", false);

        JTextArea eingabeFeld = new JTextArea(2, 10);
        eingabeFeld.setFont(eingabeFeld.getFont().deriveFont(Font.PLAIN, 13f));
        eingabeFeld.setLineWrap(true);
        eingabeFeld.setWrapStyleWord(true);
        eingabeFeld.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane eingabeScroll = new JScrollPane(eingabeFeld,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        eingabeScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        JButton sendenButton = primaryButton("Senden");

        Runnable sendeNachricht = () -> {
            String frage = eingabeFeld.getText().strip();
            if (frage.isEmpty()) {
                return;
            }
            addChatBubble(verlaufPanel, "Du", frage, true);
            eingabeFeld.setText("");
            eingabeFeld.setEnabled(false);
            sendenButton.setEnabled(false);

            JPanel ladeBubble = addChatBubble(verlaufPanel, "KI", "… denkt nach …", false);
            verlaufPanel.revalidate();
            verlaufPanel.repaint();

            new SwingWorker<String, Void>() {
                private Exception fehler;

                @Override
                protected String doInBackground() {
                    try {
                        return chatService.sendeNachricht(frage);
                    } catch (Exception e) {
                        fehler = e;
                        return null;
                    }
                }

                @Override
                protected void done() {
                    verlaufPanel.remove(ladeBubble);
                    if (fehler != null) {
                        LOGGER.log(Level.WARNING, "Ollama-Anfrage fehlgeschlagen", fehler);
                        addChatBubble(verlaufPanel, "Fehler",
                                "Ollama ist evtl. nicht gestartet oder nicht erreichbar "
                                        + "(http://localhost:11434). Details: " + fehler.getMessage(), false);
                    } else {
                        try {
                            addChatBubble(verlaufPanel, "KI", get(), false);
                        } catch (Exception ignored) {
                            // kann hier nicht mehr auftreten, da bereits oben behandelt
                        }
                    }
                    eingabeFeld.setEnabled(true);
                    sendenButton.setEnabled(true);
                    eingabeFeld.requestFocusInWindow();
                    verlaufPanel.revalidate();
                    verlaufPanel.repaint();
                }
            }.execute();
        };

        sendenButton.addActionListener(e -> sendeNachricht.run());
        eingabeFeld.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    sendeNachricht.run();
                }
            }
        });

        JPanel eingabePanel = new JPanel(new BorderLayout(8, 0));
        eingabePanel.setBackground(Theme.SURFACE);
        eingabePanel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(10, 10, 10, 10)));
        eingabePanel.add(eingabeScroll, BorderLayout.CENTER);
        eingabePanel.add(sendenButton, BorderLayout.EAST);
        content.add(eingabePanel, BorderLayout.SOUTH);

        // Kurzer Erreichbarkeits-Check im Hintergrund, damit der Nutzer sofort
        // merkt, falls Ollama nicht laeuft (statt erst nach einer Frage).
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return chatService.istErreichbar();
            }

            @Override
            protected void done() {
                try {
                    if (!get()) {
                        addChatBubble(verlaufPanel, "Hinweis",
                                "Ollama scheint nicht erreichbar zu sein. Bitte pruefe, ob die "
                                        + "Ollama-App laeuft (Symbol in der Taskleiste).", false);
                        verlaufPanel.revalidate();
                        verlaufPanel.repaint();
                    }
                } catch (Exception ignored) {
                    // Erreichbarkeits-Check ist best-effort, Fehler hier ignorierbar
                }
            }
        }.execute();

        return content;
    }

    /** Fuegt eine Chat-Sprechblase (Absender fett, Text darunter) zum Verlaufs-Panel hinzu. */
    private static JPanel addChatBubble(JPanel verlaufPanel, String absender, String text, boolean eigeneNachricht) {
        JPanel bubble = new JPanel(new BorderLayout(0, 4));

        bubble.setBackground(eigeneNachricht ? Theme.CARD_SELECTED : Theme.CARD);
        bubble.setBorder(new CompoundBorder(
                new EmptyBorder(4, 4, 4, 4),
                new CompoundBorder(
                        BorderFactory.createLineBorder(Theme.BORDER),
                        new EmptyBorder(8, 10, 8, 10))));
        bubble.setAlignmentX(Component.LEFT_ALIGNMENT);
        bubble.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel absenderLabel = new JLabel(absender);
        absenderLabel.setFont(absenderLabel.getFont().deriveFont(Font.BOLD, 11f));
        absenderLabel.setForeground(Theme.TEXT);

        JTextArea textArea = buildWrappingText(text, bubble.getBackground());

        bubble.add(absenderLabel, BorderLayout.NORTH);
        bubble.add(textArea, BorderLayout.CENTER);

        verlaufPanel.add(Box.createVerticalStrut(8));
        verlaufPanel.add(bubble);
        return bubble;
    }

    /**
     * Baut ein nicht editierbares JTextArea, das sich immer an die
     * verfuegbare Breite des umgebenden Containers anpasst (Wortumbruch),
     * sodass NIE eine horizontale Scrollbar noetig ist - nur vertikal.
     * Markdown-Formatierungen (**fett**, Ueberschriften-Sternchen, etc.)
     * werden vorher in lesbaren Klartext umgewandelt.
     *
     * Wichtig: JTextArea mit setLineWrap(true) berechnet seine bevorzugte
     * Hoehe standardmaessig FALSCH (zu klein / abgeschnitten), solange die
     * tatsaechliche Breite des Containers noch nicht bekannt ist - ein
     * bekanntes Swing-Verhalten. Daher wird hier getPreferredSize()
     * ueberschrieben: sobald die Elternbreite bekannt ist, wird die
     * Textfluss-Breite darauf gesetzt und die Hoehe NEU anhand dieser
     * Breite berechnet, bevor der eigentliche Wert zurueckgegeben wird.
     * So wird IMMER der komplette Text angezeigt, nie abgeschnitten.
     */
    private static JTextArea buildWrappingText(String rawText, Color background) {
        JTextArea textArea = new JTextArea(cleanMarkdown(rawText)) {
            @Override
            public Dimension getPreferredSize() {
                Container parent = getParent();
                int parentWidth = parent != null ? parent.getWidth() : 0;
                if (parentWidth > 0 && getWidth() != parentWidth) {
                    setSize(parentWidth, Short.MAX_VALUE);
                }
                return super.getPreferredSize();
            }
        };
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(background != null);
        if (background != null) {
            textArea.setBackground(background);
            // Auswahlfarben explizit auf den Hintergrund setzen, damit
            // KEINE gruene/farbige Markierung erscheinen kann (manche
            // Look&Feels nutzen sonst eine grelle Standard-Selektionsfarbe,
            // selbst wenn der Text nicht editierbar/fokussierbar ist).
            textArea.setSelectionColor(background);
            textArea.setSelectedTextColor(Theme.TEXT);
            textArea.setHighlighter(null);
        }
        textArea.setFont(textArea.getFont().deriveFont(Font.PLAIN, 12.5f));
        textArea.setBorder(BorderFactory.createEmptyBorder());
        textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        return textArea;
    }

    /**
     * Entfernt/uebersetzt haeufige Markdown-Markierungen (die ein LLM gerne
     * verwendet) in sauberen, gut lesbaren Klartext fuer die Swing-UI:
     * "**fett**" -> "fett" (Sternchen weg, da JTextArea kein Rich-Text
     * darstellen kann), ueberfluessige Leerzeilen werden reduziert, und
     * "- " / "* " Aufzaehlungen werden auf ein einheitliches "• " vereinheitlicht.
     */
    private static String cleanMarkdown(String text) {
        if (text == null) {
            return "";
        }
        String ergebnis = text
                // **fett** oder __fett__ -> fett (Sternchen/Unterstriche entfernen)
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                // einzelne Sternchen/Unterstriche fuer Kursiv ebenfalls entfernen
                .replaceAll("(?<!\\*)\\*(?!\\*)([^*\\n]+?)\\*(?!\\*)", "$1")
                // Markdown-Ueberschriften "### Text" -> "Text"
                .replaceAll("(?m)^#{1,6}\\s*", "")
                // Aufzaehlungszeichen vereinheitlichen
                .replaceAll("(?m)^\\s*[-*]\\s+", "• ")
                // mehr als 2 aufeinanderfolgende Leerzeilen auf 1 reduzieren
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
        return ergebnis;
    }

    private static JLabel sectionHeading(String text) {
        JLabel label = new JLabel(text.toUpperCase(Locale.GERMAN));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setForeground(Theme.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * Macht die vertikale Scrollbar eines JScrollPane duenn und modern
     * (kein Hintergrundtrack, keine Pfeil-Buttons oben/unten) - einheitlicher
     * Scroll-Stil fuer die ganze App (Lösungsvorschläge, Chat-Verlauf, PDF).
     */
    private static void flatScrollBar(JScrollPane scrollPane) {
        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        vertical.setPreferredSize(new Dimension(10, 0));
        vertical.setUnitIncrement(14);
        vertical.setUI(new BasicScrollBarUI() {
            @Override
            protected JButton createDecreaseButton(int orientation) {
                return zeroSizeButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return zeroSizeButton();
            }

            private JButton zeroSizeButton() {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(0, 0));
                button.setMinimumSize(new Dimension(0, 0));
                button.setMaximumSize(new Dimension(0, 0));
                return button;
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
                // Kein sichtbarer Track-Hintergrund - nur der Thumb (Griff).
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
                if (thumbBounds.isEmpty() || !c.isEnabled()) {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(), Theme.ACCENT.getBlue(), 200));
                g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y, thumbBounds.width - 4,
                        thumbBounds.height, 8, 8);
                g2.dispose();
            }
        });
    }

    /**
     * Vertikaler Stapel-Container (BoxLayout Y_AXIS), der innerhalb eines
     * JScrollPane IMMER die Breite des Viewports uebernimmt (statt seine
     * eigene bevorzugte Breite einzufordern). Dadurch wird nie eine
     * horizontale Scrollbar benoetigt - Inhalte (Text, Bubbles, ...) brechen
     * stattdessen innerhalb der verfuegbaren Breite um, nur vertikales
     * Scrollen bleibt noetig.
     */
    private static class VerticalStackPanel extends JPanel implements Scrollable {
        VerticalStackPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 14;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 100;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /**
     * Fragt im Hintergrund das lokale Ollama-Modell nach konkreten
     * Lösungsvorschlägen fuer den Praxisauftrag (z.B. fertig formulierte
     * SMARTe Ziele statt nur der Aufgabenstellung selbst) und ersetzt das
     * "Laedt..."-Label durch die generierten Vorschläge, sobald die Antwort
     * da ist. Laeuft komplett asynchron, damit die UI nicht blockiert.
     */
    private void ladeLoesungsvorschlaege(Praxisauftrag auftrag, JPanel middle, JLabel ladeLabel) {
        String prompt = "Du bist ein Experte fuer kaufmaennische Ausbildung. Lies den folgenden "
                + "Praxisauftrag und erstelle KONKRETE, fertig formulierte Lösungsvorschläge "
                + "(z.B. bei 'SMARTe Ziele setzen': schlage 2-3 fertige, spezifische SMARTe Ziele "
                + "vor, keine Erklaerung was SMART bedeutet oder was zu tun ist - sondern direkt "
                + "nutzbare Beispielformulierungen). Antworte auf Deutsch, als kurze Liste mit "
                + "max. 5 Punkten, ohne Einleitung oder Schlusswort. Verwende KEINE "
                + "Markdown-Formatierung (kein **, kein #, keine Sternchen) - nur Klartext mit "
                + "Zeilenumbruechen.\n\n"
                + AiAssist.buildPraxisauftragPrompt(auftrag);

        new SwingWorker<String, Void>() {
            private Exception fehler;

            @Override
            protected String doInBackground() {
                try {
                    OllamaChatService service = new OllamaChatService();
                    return service.sendeNachricht(prompt);
                } catch (Exception e) {
                    fehler = e;
                    return null;
                }
            }

            @Override
            protected void done() {
                middle.remove(ladeLabel);
                if (fehler != null) {
                    LOGGER.log(Level.WARNING, "Loesungsvorschlaege konnten nicht generiert werden", fehler);
                    JLabel fehlerLabel = formLabel("Konnte keine Vorschläge laden (Ollama nicht erreichbar?).");
                    fehlerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    middle.add(fehlerLabel, 1);
                } else {
                    try {
                        JTextArea vorschlagText = buildWrappingText(get(), Theme.CARD);
                        middle.add(vorschlagText, 1);
                    } catch (Exception ignored) {
                        // kann hier nicht mehr auftreten, da bereits oben behandelt
                    }
                }
                middle.revalidate();
                middle.repaint();
                // Zweiter Validierungs-Durchlauf verzoegert, da beim ersten
                // Durchlauf die Breite des Containers (fuer den Textumbruch
                // der Loesungsvorschlaege) manchmal noch nicht final/bekannt
                // ist - dadurch koennte der Text sonst faelschlich
                // abgeschnitten wirken.
                Timer t = new Timer(150, e -> {
                    middle.revalidate();
                    middle.repaint();
                });
                t.setRepeats(false);
                t.start();
            }
        }.execute();
    }
}

