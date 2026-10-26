package com.konvink.assistant.ui;

import com.konvink.assistant.model.KombiVorschlag;
import com.konvink.assistant.model.Praxisauftrag;
import com.konvink.assistant.service.AiAssist;
import com.konvink.assistant.service.KombiGruppen;
import com.konvink.assistant.service.OllamaChatService;
import com.konvink.assistant.store.AppStore;
import com.konvink.assistant.store.AppStore.AuftragInfo;
import com.konvink.assistant.ui.UiKit.Karte;
import com.konvink.assistant.ui.UiKit.VerticalStackPanel;
import com.konvink.assistant.ui.UiKit.WrapLabel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detailansicht eines Praxisauftrags: links das PDF, rechts oben Tabs
 * (Loesungen, Kombi, Checkliste, Notizen), rechts unten der KI-Chat.
 */
public class DetailView extends JPanel {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final Praxisauftrag auftrag;
    private final List<KombiVorschlag> vorschlaege;
    private final List<Praxisauftrag> alleAuftraege;
    private final Consumer<Praxisauftrag> oeffne;
    private final Runnable beiAenderung;
    private final AuftragInfo info;
    private final PdfPanel pdfPanel;
    private final KiChatPanel chatPanel;
    private final JButton fristButton = UiKit.secondaryButton("");
    private final JButton planButton = UiKit.secondaryButton("🗓 Planen");
    private final JButton tagButton = UiKit.secondaryButton("🏷 Tags");
    private final JButton erledigtButton = UiKit.secondaryButton("");
    private final JButton begonnenButton = UiKit.secondaryButton("");
    private final JButton sternButton = UiKit.iconButton("☆", "Als Favorit markieren");
    private boolean trennerBereit;
    private JTextArea notizFeld;

    public DetailView(Praxisauftrag auftrag, List<KombiVorschlag> vorschlaege,
                      Consumer<Praxisauftrag> oeffne, Runnable zurueck, Runnable beiAenderung) {
        this(auftrag, vorschlaege, List.of(), oeffne, zurueck, beiAenderung);
    }

    public DetailView(Praxisauftrag auftrag, List<KombiVorschlag> vorschlaege, List<Praxisauftrag> alleAuftraege,
                      Consumer<Praxisauftrag> oeffne, Runnable zurueck, Runnable beiAenderung) {
        super(new BorderLayout());
        this.auftrag = auftrag;
        this.alleAuftraege = alleAuftraege;
        this.vorschlaege = vorschlaege;
        this.oeffne = oeffne;
        this.beiAenderung = beiAenderung;
        this.info = AppStore.get().info(auftrag.getDateiname());
        setBackground(Theme.BG_APP);

        add(baueKopf(zurueck), BorderLayout.NORTH);

        pdfPanel = new PdfPanel(auftrag.getPdfPfad());
        chatPanel = new KiChatPanel(auftrag);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("💡 Lösungen", baueLoesungen());
        tabs.addTab("🤝 Kombi", baueKombi());
        tabs.addTab("✅ Checkliste", baueCheckliste());
        tabs.addTab("🗒 Notizen", baueNotizen());
        chatPanel.setEinfuegeListener(text -> {
            String alt = notizFeld.getText();
            notizFeld.setText(alt.isBlank() ? text : alt + "\n\n" + text);
            tabs.setSelectedIndex(tabs.getTabCount() - 1);
        });

        JPanel oben = new JPanel(new BorderLayout());
        oben.setBackground(Theme.SURFACE);
        oben.add(tabs, BorderLayout.CENTER);

        JSplitPane rechts = new JSplitPane(JSplitPane.VERTICAL_SPLIT, oben, chatPanel);
        rechts.setResizeWeight(0.5);
        rechts.setDividerSize(8);
        rechts.setBorder(BorderFactory.createEmptyBorder());
        rechts.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            int total = rechts.getHeight() - rechts.getDividerSize();
            if (trennerBereit && total > 100) {
                AppStore.get().fenster().trennerChat =
                        Math.max(0.15, Math.min(0.85, 1.0 - rechts.getDividerLocation() / (double) total));
                AppStore.get().speichern();
            }
        });

        JSplitPane haupt = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pdfPanel, rechts);
        haupt.setResizeWeight(0.5);
        haupt.setDividerSize(6);
        haupt.setBorder(BorderFactory.createEmptyBorder());
        add(haupt, BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> {
            haupt.setDividerLocation(0.5);
            rechts.setDividerLocation(1.0 - AppStore.get().fenster().trennerChat);
            trennerBereit = true;
        });
    }

    /** Fokus in die PDF-Suche (Strg+F). */
    public void fokusSuche() {
        pdfPanel.fokusSuche();
    }

    public void schliessen() {
        pdfPanel.schliessen();
    }

    // -------------------------------------------------------------- Kopf

    private JComponent baueKopf(Runnable zurueck) {
        JPanel kopf = new JPanel(new BorderLayout(12, 0));
        kopf.setBackground(Theme.BG_APP);
        kopf.setBorder(new EmptyBorder(10, 14, 10, 14));

        JButton back = UiKit.secondaryButton("← Übersicht (Esc)");
        back.addActionListener(e -> zurueck.run());
        kopf.add(back, BorderLayout.WEST);

        JPanel mitte = new JPanel();
        mitte.setOpaque(false);
        mitte.setLayout(new BoxLayout(mitte, BoxLayout.Y_AXIS));
        WrapLabel titel = new WrapLabel(UiKit.name(auftrag), Theme.TEXT);
        titel.setFont(titel.getFont().deriveFont(Font.BOLD, 16f));
        titel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mitte.add(titel);
        StringBuilder meta = new StringBuilder();
        if (auftrag.getLehrjahr() != null && !auftrag.getLehrjahr().isBlank()) {
            meta.append("Lehrjahr ").append(auftrag.getLehrjahr());
        }
        if (auftrag.getHandlung() != null && !auftrag.getHandlung().isBlank()) {
            meta.append(meta.length() > 0 ? "   •   " : "").append(auftrag.getHandlung());
        }
        if (meta.length() > 0) {
            WrapLabel m = new WrapLabel(meta.toString(), Theme.MUTED);
            m.setAlignmentX(Component.LEFT_ALIGNMENT);
            m.setFont(m.getFont().deriveFont(Font.PLAIN, 12f));
            mitte.add(Box.createVerticalStrut(2));
            mitte.add(m);
        }
        kopf.add(mitte, BorderLayout.CENTER);

        JPanel rechts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rechts.setOpaque(false);
        sternButton.addActionListener(e -> {
            info.favorit = !info.favorit;
            AppStore.get().speichern();
            aktualisiereStern();
            beiAenderung.run();
        });
        fristButton.addActionListener(e -> fristWaehlen());
        erledigtButton.addActionListener(e -> {
            info.erledigt = !info.erledigt;
            AppStore.get().speichern();
            aktualisiereErledigt();
            if (info.erledigt && !info.mailAbgeschlossenGesendet) {
                info.mailAbgeschlossenGesendet = true;
                AppStore.get().speichern();
                com.konvink.assistant.service.PraxisbildnerMailer.benachrichtigeAbgeschlossen(auftrag);
            }
            beiAenderung.run();
        });
        begonnenButton.addActionListener(e -> {
            info.begonnen = !info.begonnen;
            AppStore.get().speichern();
            aktualisiereBegonnen();
            if (info.begonnen && !info.mailBegonnenGesendet) {
                info.mailBegonnenGesendet = true;
                AppStore.get().speichern();
                com.konvink.assistant.service.PraxisbildnerMailer.benachrichtigeBegonnen(auftrag);
            }
            beiAenderung.run();
        });
        rechts.add(planButton);
        rechts.add(tagButton);
        rechts.add(begonnenButton);
        rechts.add(erledigtButton);
        JButton tagsButton = UiKit.iconButton("⋯", "Mehr: Tags, Planen");
        tagsButton.setFont(tagsButton.getFont().deriveFont(Font.BOLD, 20f));
        tagsButton.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            menu.setBackground(Theme.SURFACE);
            JMenuItem tags = new JMenuItem("🏷 Tags bearbeiten …");
            tags.addActionListener(ev -> tagsBearbeiten(tagsButton));
            JMenuItem plan = new JMenuItem("🗓 Mit KI planen …");
            plan.addActionListener(ev -> planen(new JButton()));
            for (JMenuItem mi : new JMenuItem[]{tags, plan}) {
                mi.setBackground(Theme.SURFACE);
                mi.setForeground(Theme.TEXT);
                menu.add(mi);
            }
            menu.show(tagsButton, 0, tagsButton.getHeight());
        });
        rechts.add(tagsButton);
        rechts.add(fristButton);
        rechts.add(sternButton);
        kopf.add(rechts, BorderLayout.EAST);
        planButton.addActionListener(e -> planeMitKi());
        tagButton.addActionListener(e -> tagsBearbeiten());
        aktualisiereTags();
        aktualisiereStern();
        aktualisiereErledigt();
        aktualisiereBegonnen();
        aktualisiereFrist();
        return kopf;
    }

    private void tagsBearbeiten(JButton quelle) {
        String aktuell = String.join(", ", info.tags);
        String eingabe = (String) JOptionPane.showInputDialog(this,
                "Tags (kommagetrennt), z.B. schwierig, mit Chef klären:", "Tags",
                JOptionPane.PLAIN_MESSAGE, null, null, aktuell);
        if (eingabe == null) {
            return;
        }
        info.tags.clear();
        for (String t : eingabe.split(",")) {
            String s = t.trim();
            if (!s.isEmpty() && !info.tags.contains(s)) {
                info.tags.add(s);
            }
        }
        AppStore.get().speichern();
        beiAenderung.run();
    }

    /** Laesst die KI den Auftrag in Teilschritte teilen und legt sie als Zeitbloecke an. */
    private void planen(JButton knopf) {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate ende = fristVon(info) != null ? fristVon(info) : start.plusDays(6);
        JTextField von = new JTextField(start.format(DATUM));
        JTextField bis = new JTextField(ende.format(DATUM));
        JTextField zeit = new JTextField("15:00");
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Start (dd.MM.yyyy)"));
        form.add(von);
        form.add(new JLabel("Ende (dd.MM.yyyy)"));
        form.add(bis);
        form.add(new JLabel("Uhrzeit (HH:mm)"));
        form.add(zeit);
        if (JOptionPane.showConfirmDialog(this, form, "Auftrag planen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        final LocalDate s;
        final LocalDate e;
        final java.time.LocalTime uhr;
        try {
            s = LocalDate.parse(von.getText().trim(), DATUM);
            e = LocalDate.parse(bis.getText().trim(), DATUM);
            uhr = java.time.LocalTime.parse(zeit.getText().trim());
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "Bitte Datum als dd.MM.yyyy und Uhrzeit als HH:mm eingeben.");
            return;
        }
        List<LocalDate> tage = new ArrayList<>();
        for (LocalDate d = s; !d.isAfter(e) && tage.size() < 6; d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() <= 5) {
                tage.add(d);
            }
        }
        if (tage.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Im gewählten Zeitraum gibt es keinen Werktag.");
            return;
        }
        knopf.setEnabled(false);
        knopf.setText("KI plant …");
        int n = tage.size();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                OllamaChatService o = new OllamaChatService();
                o.setMaxTokens(400);
                return o.sendeNachricht("Teile den folgenden Praxisauftrag in genau " + n
                        + " aufeinanderfolgende Arbeitsschritte. Pro Zeile ein kurzer Schritt (max. 12 Woerter), "
                        + "ohne Einleitung.\n\n" + AiAssist.buildPraxisauftragPrompt(auftrag));
            }

            @Override
            protected void done() {
                knopf.setEnabled(true);
                knopf.setText("🗓 Planen");
                List<String> schritte = new ArrayList<>();
                try {
                    for (String z : UiKit.cleanMarkdown(get()).split("\\R")) {
                        String t = z.replaceFirst("^[•\\-\\d.)\\s]+", "").trim();
                        if (t.length() >= 4) {
                            schritte.add(t);
                        }
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DetailView.this,
                            "Die KI ist gerade nicht erreichbar. Bitte prüfe, ob Ollama läuft.");
                    return;
                }
                while (schritte.size() > n) {
                    schritte.remove(schritte.size() - 1);
                }
                if (schritte.isEmpty()) {
                    JOptionPane.showMessageDialog(DetailView.this, "Die KI hat keine Schritte geliefert.");
                    return;
                }
                StringBuilder vorschau = new StringBuilder();
                for (int i = 0; i < schritte.size(); i++) {
                    vorschau.append(tage.get(i).format(DATUM)).append("  ").append(schritte.get(i)).append("\n");
                }
                if (JOptionPane.showConfirmDialog(DetailView.this, vorschau + "\nAls Zeitblöcke eintragen?",
                        "Vorschau", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
                    return;
                }
                for (int i = 0; i < schritte.size(); i++) {
                    AppStore.Block b = new AppStore.Block();
                    b.datum = tage.get(i).toString();
                    b.zeit = uhr.format(DateTimeFormatter.ofPattern("HH:mm"));
                    b.dauer = 60;
                    b.betreff = (i + 1) + "/" + schritte.size() + ": " + schritte.get(i);
                    b.auftrag = auftrag.getDateiname();
                    AppStore.get().state().bloecke.add(b);
                }
                AppStore.get().speichern();
                beiAenderung.run();
            }
        }.execute();
    }

    private void aktualisiereErledigt() {
        erledigtButton.setText(info.erledigt ? "✔ Erledigt" : "☐ Als erledigt markieren");
        erledigtButton.setForeground(info.erledigt ? Theme.LIME : Theme.TEXT);
    }

    private void aktualisiereBegonnen() {
        begonnenButton.setText(info.begonnen ? "▶ Begonnen" : "○ Als begonnen markieren");
        begonnenButton.setForeground(info.begonnen ? Theme.ORANGE : Theme.TEXT);
    }

    private void aktualisiereStern() {
        sternButton.setText(info.favorit ? "★" : "☆");
        sternButton.setForeground(info.favorit ? Theme.ORANGE : Theme.TEXT);
        sternButton.setFont(sternButton.getFont().deriveFont(Font.BOLD, 20f));
    }

    private void aktualisiereFrist() {
        LocalDate frist = fristVon(info);
        if (frist == null) {
            fristButton.setText("📅 Frist setzen");
            return;
        }
        long tage = ChronoUnit.DAYS.between(LocalDate.now(), frist);
        String rest = tage < 0 ? "überfällig" : tage == 0 ? "heute" : tage == 1 ? "morgen" : "in " + tage + " Tagen";
        fristButton.setText("📅 " + frist.format(DATUM) + " (" + rest + ")");
    }

    /** Liest die Frist eines Auftrags (oder null). */
    public static LocalDate fristVon(AuftragInfo info) {
        if (info == null || info.frist == null || info.frist.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(info.frist);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void aktualisiereTags() {
        tagButton.setText(info.tags.isEmpty() ? "🏷 Tags" : "🏷 " + String.join(", ", info.tags));
    }

    private void tagsBearbeiten() {
        String eingabe = (String) JOptionPane.showInputDialog(this,
                "Tags, durch Komma getrennt (z.B. schwierig, mit Chef klären, später):", "Tags",
                JOptionPane.PLAIN_MESSAGE, null, null, String.join(", ", info.tags));
        if (eingabe == null) {
            return;
        }
        info.tags.clear();
        for (String t : eingabe.split(",")) {
            String s = t.trim();
            if (!s.isEmpty() && !info.tags.contains(s)) {
                info.tags.add(s);
            }
        }
        AppStore.get().speichern();
        aktualisiereTags();
        beiAenderung.run();
    }

    /** Laesst die KI den Auftrag in Teilschritte zerlegen und legt sie als Zeitbloecke an. */
    private void planeMitKi() {
        LocalDate frist = fristVon(info);
        JTextField start = new JTextField(LocalDate.now().format(DATUM));
        JTextField ende = new JTextField((frist != null && !frist.isBefore(LocalDate.now())
                ? frist : LocalDate.now().plusDays(7)).format(DATUM));
        JTextField zeit = new JTextField("15:00");
        JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
        p.add(new JLabel("Start (dd.MM.yyyy):"));
        p.add(start);
        p.add(new JLabel("Ende (dd.MM.yyyy):"));
        p.add(ende);
        p.add(new JLabel("Uhrzeit (HH:mm):"));
        p.add(zeit);
        if (JOptionPane.showConfirmDialog(this, p, "In Teilschritte planen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        final LocalDate s;
        final LocalDate e;
        final java.time.LocalTime t;
        try {
            s = LocalDate.parse(start.getText().strip(), DATUM);
            e = LocalDate.parse(ende.getText().strip(), DATUM);
            t = java.time.LocalTime.parse(zeit.getText().strip());
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "Bitte Datum als dd.MM.yyyy und Uhrzeit als HH:mm eingeben.");
            return;
        }
        if (e.isBefore(s)) {
            JOptionPane.showMessageDialog(this, "Das Ende liegt vor dem Start.");
            return;
        }
        final int tage = (int) ChronoUnit.DAYS.between(s, e) + 1;
        final int n = Math.max(2, Math.min(6, tage));
        planButton.setEnabled(false);
        planButton.setText("KI plant …");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                OllamaChatService svc = new OllamaChatService();
                svc.setMaxTokens(400);
                return svc.sendeNachricht("Teile den folgenden Praxisauftrag in genau " + n
                        + " sinnvolle Arbeitsschritte, die nacheinander an verschiedenen Tagen erledigt werden. "
                        + "Pro Zeile ein Schritt (max. 12 Woerter), ohne Einleitung und ohne Erklaerung.\n\n"
                        + AiAssist.buildPraxisauftragPrompt(auftrag));
            }

            @Override
            protected void done() {
                planButton.setEnabled(true);
                planButton.setText("🗓 Planen");
                List<String> schritte = new ArrayList<>();
                try {
                    for (String z : UiKit.cleanMarkdown(get()).split("\\R")) {
                        String x = z.replaceFirst("(?i)^\\s*(tag|schritt)\\s*\\d+\\s*[:.\\-–]\\s*", "")
                                .replaceFirst("^[•\\-*\\d.)\\s]+", "").trim();
                        if (x.length() >= 5 && schritte.size() < n) {
                            schritte.add(x);
                        }
                    }
                } catch (Exception ex) {
                    java.util.logging.Logger.getLogger("Planung").log(java.util.logging.Level.WARNING, "KI-Planung", ex);
                }
                if (schritte.size() < 2) {
                    JOptionPane.showMessageDialog(DetailView.this,
                            "Die KI hat keinen brauchbaren Plan geliefert. Läuft Ollama?", "Planen",
                            JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                List<AppStore.Block> neu = new ArrayList<>();
                StringBuilder vorschau = new StringBuilder();
                for (int i = 0; i < schritte.size(); i++) {
                    LocalDate d = s.plusDays(Math.round(i * (tage - 1) / (double) (schritte.size() - 1)));
                    while (d.getDayOfWeek().getValue() >= 6) {
                        d = d.plusDays(1); // Wochenende ueberspringen
                    }
                    AppStore.Block b = new AppStore.Block();
                    b.datum = d.toString();
                    b.zeit = t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                    b.dauer = 60;
                    b.betreff = (i + 1) + "/" + schritte.size() + ": " + schritte.get(i);
                    b.auftrag = auftrag.getDateiname();
                    neu.add(b);
                    vorschau.append(d.format(DATUM)).append("  ").append(schritte.get(i)).append("\n");
                }
                if (JOptionPane.showConfirmDialog(DetailView.this,
                        "Diese Zeitblöcke im Kalender anlegen?\n\n" + vorschau, "Plan übernehmen",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
                    AppStore.get().state().bloecke.addAll(neu);
                    AppStore.get().speichern();
                    beiAenderung.run();
                }
            }
        }.execute();
    }

    private void fristWaehlen() {
        LocalDate aktuell = fristVon(info);
        String eingabe = (String) JOptionPane.showInputDialog(this,
                "Abgabefrist (dd.MM.yyyy) – leer lassen zum Entfernen:", "Frist",
                JOptionPane.PLAIN_MESSAGE, null, null, aktuell == null ? "" : aktuell.format(DATUM));
        if (eingabe == null) {
            return;
        }
        eingabe = eingabe.trim();
        if (eingabe.isEmpty()) {
            info.frist = "";
        } else {
            try {
                info.frist = LocalDate.parse(eingabe, DATUM).toString();
            } catch (DateTimeParseException e) {
                JOptionPane.showMessageDialog(this, "Bitte im Format dd.MM.yyyy eingeben, z.B. 15.06.2025.",
                        "Ungültiges Datum", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        AppStore.get().speichern();
        aktualisiereFrist();
        beiAenderung.run();
    }

    // -------------------------------------------------------------- Tabs

    private static VerticalStackPanel neuerStapel() {
        VerticalStackPanel p = new VerticalStackPanel();
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(14, 14, 14, 14));
        return p;
    }

    private JComponent baueLoesungen() {
        VerticalStackPanel p = neuerStapel();
        p.add(UiKit.sectionHeading("Lösungsvorschläge (KI)"));
        p.add(Box.createVerticalStrut(8));
        String prompt = "Du bist ein Experte fuer kaufmaennische Ausbildung. Lies den folgenden "
                + "Praxisauftrag und erstelle KONKRETE, fertig formulierte Lösungsvorschläge "
                + "(z.B. bei 'SMARTe Ziele setzen': schlage 2-3 fertige, spezifische SMARTe Ziele "
                + "vor, keine Erklaerung was SMART bedeutet oder was zu tun ist - sondern direkt "
                + "nutzbare Beispielformulierungen). Antworte auf Deutsch, als kurze Liste mit "
                + "max. 5 Punkten, ohne Einleitung oder Schlusswort. Verwende KEINE "
                + "Markdown-Formatierung - nur Klartext mit Zeilenumbruechen.\n\n"
                + AiAssist.buildPraxisauftragPrompt(auftrag);
        prompt = AiAssist.mitStilVorlage(prompt);
        KiBlock block = new KiBlock(prompt, 400, text -> {
            info.loesungen = text;
            AppStore.get().speichern();
        });
        p.add(block);
        if (info.loesungen != null && !info.loesungen.isBlank()) {
            block.zeigeText(info.loesungen);
        } else {
            block.start();
        }
        return UiKit.scroll(p);
    }

    private JComponent baueKombi() {
        VerticalStackPanel p = neuerStapel();
        List<Praxisauftrag> gruppe = KombiGruppen.gruppeVon(auftrag, vorschlaege);
        if (gruppe.size() < 2) {
            p.add(UiKit.leerZustand("🧩", "Keine Kombi-Partner",
                    "Zu diesem Auftrag passt aktuell kein weiterer Auftrag inhaltlich."));
            p.add(Box.createVerticalStrut(14));
            p.add(baueManuellKombinierenZeile());
            return UiKit.scroll(p);
        }
        p.add(UiKit.sectionHeading("Kombi-Gruppe (" + gruppe.size() + " Aufträge)"));
        p.add(Box.createVerticalStrut(8));
        int nr = 0;
        for (Praxisauftrag a : gruppe) {
            nr++;
            if (a == auftrag) {
                continue;
            }
            Color farbe = Theme.akzent(nr);
            Karte karte = new Karte(new BorderLayout(), Theme.CARD, farbe);
            karte.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            double aehnlich = KombiGruppen.aehnlichkeit(auftrag, a, vorschlaege);
            String text = UiKit.name(a) + (aehnlich >= 0 ? "\nÄhnlichkeit: " + Math.round(aehnlich * 100) + " %" : "");
            karte.add(new WrapLabel(text, Theme.TEXT), BorderLayout.CENTER);
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
            p.add(karte);
            p.add(Box.createVerticalStrut(8));
        }

        p.add(Box.createVerticalStrut(6));
        JButton warum = UiKit.primaryButton("✨ Warum passen die zusammen? (KI)");
        warum.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel zeile = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        zeile.setOpaque(false);
        zeile.setAlignmentX(Component.LEFT_ALIGNMENT);
        zeile.add(warum);
        zeile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        p.add(zeile);
        p.add(Box.createVerticalStrut(10));
        JPanel platz = new JPanel(new BorderLayout());
        platz.setOpaque(false);
        platz.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(platz);


        warum.addActionListener(e -> {
            StringBuilder sb = new StringBuilder("Du bist Ausbildungsberater. Die folgenden Praxisauftraege sollen "
                    + "als Kombi-Auftrag gemeinsam bearbeitet werden. Erklaere kurz auf Deutsch (max. 8 Saetze, "
                    + "ohne Markdown): 1. Warum passen sie inhaltlich zusammen? 2. Wie koennte man sie in EINER "
                    + "konkreten Arbeitssituation im Betrieb sinnvoll verbinden?\n\n");
            int i = 0;
            for (Praxisauftrag a : gruppe) {
                String roh = AiAssist.buildPraxisauftragPrompt(a);
                sb.append("Auftrag ").append(++i).append(":\n")
                        .append(roh.length() > 1000 ? roh.substring(0, 1000) : roh).append("\n\n");
            }
            KiBlock block = new KiBlock(sb.toString(), 450, null);
            platz.removeAll();
            platz.add(block, BorderLayout.CENTER);
            block.start();
            platz.revalidate();
        });
        p.add(Box.createVerticalStrut(18));
        p.add(new JSeparator());
        p.add(Box.createVerticalStrut(10));
        p.add(baueManuellKombinierenZeile());
        return UiKit.scroll(p);
    }

    /** Zeile mit Button, um manuell beliebige Aufträge per Dialog zu einem Kombi-Auftrag zu fusionieren. */
    private JComponent baueManuellKombinierenZeile() {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(UiKit.sectionHeading("Eigenen Kombi-Auftrag erstellen"));
        box.add(Box.createVerticalStrut(6));
        WrapLabel hinweis = new WrapLabel(
                "Wähle selbst beliebige Praxisaufträge aus allen PDFs aus und lass die KI daraus "
                        + "einen gemeinsamen Kombi-Auftrag formulieren.", Theme.MUTED);
        hinweis.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(hinweis);
        box.add(Box.createVerticalStrut(8));
        JButton manuell = UiKit.primaryButton("🔀 Aufträge auswählen & fusionieren …");
        manuell.setAlignmentX(Component.LEFT_ALIGNMENT);
        manuell.addActionListener(e -> manuellKombinieren());
        JPanel zeile2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        zeile2.setOpaque(false);
        zeile2.setAlignmentX(Component.LEFT_ALIGNMENT);
        zeile2.add(manuell);
        box.add(zeile2);
        return box;
    }

    /** Dialog: alle verfügbaren Aufträge als Checkboxen, danach KI-Fusion der gewählten. */
    private void manuellKombinieren() {
        if (alleAuftraege == null || alleAuftraege.size() < 2) {
            JOptionPane.showMessageDialog(this,
                    "Es sind nicht genügend Praxisaufträge geladen, um welche zu kombinieren.");
            return;
        }
        List<JCheckBox> boxen = new ArrayList<>();
        VerticalStackPanel liste = neuerStapel();
        for (Praxisauftrag a : alleAuftraege) {
            JCheckBox box = new JCheckBox(UiKit.name(a), a == auftrag);
            box.setOpaque(false);
            box.setForeground(Theme.TEXT);
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.putClientProperty("auftrag", a);
            boxen.add(box);
            liste.add(box);
            liste.add(Box.createVerticalStrut(4));
        }
        JScrollPane scroll = UiKit.scroll(liste);
        scroll.setPreferredSize(new Dimension(420, 380));

        JPanel inhalt = new JPanel(new BorderLayout(0, 8));
        inhalt.add(new WrapLabel("Wähle mindestens 2 Praxisaufträge aus, die zu einem Kombi-Auftrag "
                + "zusammengeführt werden sollen:", Theme.TEXT), BorderLayout.NORTH);
        inhalt.add(scroll, BorderLayout.CENTER);

        int antwort = JOptionPane.showConfirmDialog(this, inhalt, "Kombi-Auftrag erstellen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (antwort != JOptionPane.OK_OPTION) {
            return;
        }

        List<Praxisauftrag> gewaehlt = new ArrayList<>();
        for (JCheckBox box : boxen) {
            if (box.isSelected()) {
                gewaehlt.add((Praxisauftrag) box.getClientProperty("auftrag"));
            }
        }
        if (gewaehlt.size() < 2) {
            JOptionPane.showMessageDialog(this, "Bitte wähle mindestens 2 Praxisaufträge aus.");
            return;
        }
        zeigeFusionsDialog(gewaehlt);
    }

    /** Baut den Fusions-Prompt, startet die KI-Anfrage und zeigt das Ergebnis in einem Dialog. */
    private void zeigeFusionsDialog(List<Praxisauftrag> gewaehlt) {
        StringBuilder sb = new StringBuilder("Du bist Ausbildungsberater fuer die kaufmaennische Grundbildung. "
                + "Die folgenden " + gewaehlt.size() + " Praxisauftraege sollen zu EINEM gemeinsamen Kombi-Auftrag "
                + "verschmolzen werden. Erstelle daraus einen neuen, zusammenhaengenden Praxisauftrag-Text auf "
                + "Deutsch mit folgendem Aufbau: Titel, Ausgangslage (2-3 Saetze), Aufgabenstellung als "
                + "nummerierte Teilaufgaben (die Inhalte aller Ausgangsauftraege sinnvoll kombiniert und nicht "
                + "einfach nur aneinandergereiht). Keine Markdown-Formatierung, nur Klartext mit Zeilenumbruechen.\n\n");
        int i = 0;
        for (Praxisauftrag a : gewaehlt) {
            String roh = AiAssist.buildPraxisauftragPrompt(a);
            sb.append("--- Ausgangsauftrag ").append(++i).append(" ---\n")
                    .append(roh.length() > 1200 ? roh.substring(0, 1200) : roh).append("\n\n");
        }
        String fusionsPrompt = AiAssist.mitStilVorlage(sb.toString());

        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Kombi-Auftrag wird erstellt …", true);
        JPanel inhalt = neuerStapel();
        inhalt.add(UiKit.sectionHeading("Neuer Kombi-Auftrag aus " + gewaehlt.size() + " Aufträgen"));
        inhalt.add(Box.createVerticalStrut(8));
        String[] ergebnis = new String[1];
        KiBlock block = new KiBlock(fusionsPrompt, 700, text -> ergebnis[0] = text);
        inhalt.add(block);
        JScrollPane scroll = UiKit.scroll(inhalt);
        scroll.setPreferredSize(new Dimension(620, 480));

        JButton kopieren = UiKit.secondaryButton("📋 In Notizen übernehmen");
        kopieren.addActionListener(e -> {
            if (ergebnis[0] != null && notizFeld != null) {
                String alt = notizFeld.getText();
                notizFeld.setText(alt.isBlank() ? ergebnis[0] : alt + "\n\n" + ergebnis[0]);
                JOptionPane.showMessageDialog(dlg, "In die Notizen dieses Auftrags übernommen.");
            } else {
                JOptionPane.showMessageDialog(dlg, "Noch kein Ergebnis vorhanden – bitte warten, bis die KI fertig ist.");
            }
        });
        JButton schliessen = UiKit.secondaryButton("Schliessen");
        schliessen.addActionListener(e -> dlg.dispose());
        JPanel suedZeile = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        suedZeile.add(kopieren);
        suedZeile.add(schliessen);

        JPanel wurzel = new JPanel(new BorderLayout());
        wurzel.add(scroll, BorderLayout.CENTER);
        wurzel.add(suedZeile, BorderLayout.SOUTH);
        dlg.setContentPane(wurzel);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        block.start();
        dlg.setVisible(true);
    }

    private JComponent baueCheckliste() {
        JPanel huelle = new JPanel(new BorderLayout());
        huelle.setOpaque(false);
        VerticalStackPanel liste = neuerStapel();
        JLabel fortschritt = new JLabel(" ");
        fortschritt.setForeground(Theme.MUTED);

        Runnable neuZeichnen = new Runnable() {
            @Override
            public void run() {
                liste.removeAll();
                if (info.checkliste.isEmpty()) {
                    liste.add(UiKit.leerZustand("📋", "Noch keine Checkliste",
                            "Klicke unten auf \"Aus PDF erzeugen\", um die Teilaufgaben abzuhaken."));
                }
                for (Map.Entry<String, Boolean> e : info.checkliste.entrySet()) {
                    JCheckBox box = new JCheckBox("<html><div style='width:300px'>"
                            + e.getKey().replace("&", "&amp;").replace("<", "&lt;") + "</div></html>", e.getValue());
                    box.setOpaque(false);
                    box.setForeground(Theme.TEXT);
                    box.setAlignmentX(Component.LEFT_ALIGNMENT);
                    box.addActionListener(ev -> {
                        info.checkliste.put(e.getKey(), box.isSelected());
                        boolean alleFertig = !info.checkliste.isEmpty()
                                && info.checkliste.values().stream().allMatch(b -> b);
                        if (alleFertig != info.erledigt) {
                            info.erledigt = alleFertig; // automatisch erledigt, sobald alles abgehakt ist
                            aktualisiereErledigt();
                            if (info.erledigt && !info.mailAbgeschlossenGesendet) {
                                info.mailAbgeschlossenGesendet = true;
                                com.konvink.assistant.service.PraxisbildnerMailer.benachrichtigeAbgeschlossen(auftrag);
                            }
                            beiAenderung.run();
                        }
                        AppStore.get().speichern();
                        aktualisiere();
                    });
                    liste.add(box);
                    liste.add(Box.createVerticalStrut(6));
                }
                aktualisiere();
                liste.revalidate();
                liste.repaint();
            }

            private void aktualisiere() {
                long fertig = info.checkliste.values().stream().filter(b -> b).count();
                fortschritt.setText(info.checkliste.isEmpty() ? " "
                        : fertig + " von " + info.checkliste.size() + " erledigt");
            }
        };

        JButton erzeugen = UiKit.primaryButton("✨ Aus PDF erzeugen");
        JButton leeren = UiKit.secondaryButton("Leeren");
        erzeugen.addActionListener(e -> erzeugeCheckliste(erzeugen, neuZeichnen));
        leeren.addActionListener(e -> {
            info.checkliste.clear();
            AppStore.get().speichern();
            neuZeichnen.run();
        });
        JPanel unten = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        unten.setOpaque(false);
        unten.add(erzeugen);
        unten.add(leeren);
        unten.add(fortschritt);
        huelle.add(UiKit.scroll(liste), BorderLayout.CENTER);
        huelle.add(unten, BorderLayout.SOUTH);
        neuZeichnen.run();
        return huelle;
    }

    private void erzeugeCheckliste(JButton knopf, Runnable fertig) {
        List<String> aufgaben = extrahiereAufgaben(auftrag.getRohtext());
        if (aufgaben.size() >= 2) {
            uebernehmeAufgaben(aufgaben);
            fertig.run();
            return;
        }
        // Keine erkennbare Struktur im PDF-Text: KI soll die Teilaufgaben auflisten.
        knopf.setEnabled(false);
        knopf.setText("KI arbeitet …");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                OllamaChatService s = new OllamaChatService();
                s.setMaxTokens(400);
                return s.sendeNachricht(AiAssist.mitStilVorlage(
                        "Liste die einzelnen Teilaufgaben des folgenden Praxisauftrags auf. "
                        + "Pro Zeile genau eine kurze Aufgabe (max. 15 Woerter), ohne Nummerierung und ohne "
                        + "Einleitung.\n\n" + AiAssist.buildPraxisauftragPrompt(auftrag)));
            }

            @Override
            protected void done() {
                knopf.setEnabled(true);
                knopf.setText("✨ Aus PDF erzeugen");
                try {
                    List<String> zeilen = new ArrayList<>();
                    for (String z : UiKit.cleanMarkdown(get()).split("\\R")) {
                        String t = z.replaceFirst("^[•\\-\\d.)\\s]+", "").trim();
                        if (t.length() >= 5) {
                            zeilen.add(t);
                        }
                    }
                    uebernehmeAufgaben(zeilen);
                    fertig.run();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(DetailView.this,
                            "Die KI ist gerade nicht erreichbar. Bitte prüfe, ob Ollama läuft.",
                            "Checkliste", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }.execute();
    }

    private void uebernehmeAufgaben(List<String> aufgaben) {
        Map<String, Boolean> neu = new LinkedHashMap<>();
        for (String a : aufgaben) {
            neu.put(a, info.checkliste.getOrDefault(a, false));
        }
        info.checkliste.clear();
        info.checkliste.putAll(neu);
        AppStore.get().speichern();
    }

    private static final Pattern AUFGABE = Pattern.compile(
            "^\\s*((?:Teilaufgabe\\s*\\d+|\\d{1,2}[.)]|[a-zA-Z][.)]|[-•*–])\\s*.{10,})$");

    /** Erkennt nummerierte/aufgezaehlte Zeilen im PDF-Text als Teilaufgaben. */
    static List<String> extrahiereAufgaben(String rohtext) {
        List<String> ergebnis = new ArrayList<>();
        if (rohtext == null) {
            return ergebnis;
        }
        for (String zeile : rohtext.split("\\R")) {
            Matcher m = AUFGABE.matcher(zeile);
            if (m.matches()) {
                String t = m.group(1).trim();
                if (t.length() > 160) {
                    t = t.substring(0, 157) + "…";
                }
                if (!ergebnis.contains(t) && ergebnis.size() < 15) {
                    ergebnis.add(t);
                }
            }
        }
        return ergebnis;
    }

    private JComponent baueNotizen() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(14, 14, 14, 14));
        JLabel hinweis = new JLabel("Deine Notizen zu diesem Auftrag (werden automatisch gespeichert)");
        hinweis.setForeground(Theme.MUTED);
        JTextArea feld = new JTextArea(info.notiz == null ? "" : info.notiz);
        notizFeld = feld;
        feld.setLineWrap(true);
        feld.setWrapStyleWord(true);
        feld.setBorder(new EmptyBorder(8, 10, 8, 10));
        Timer speichern = new Timer(600, e -> {
            info.notiz = feld.getText();
            AppStore.get().speichern();
        });
        speichern.setRepeats(false);
        feld.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                speichern.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                speichern.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                speichern.restart();
            }
        });
        JScrollPane sp = new JScrollPane(feld);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        UiKit.flatScrollBar(sp);
        p.add(hinweis, BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }
}
