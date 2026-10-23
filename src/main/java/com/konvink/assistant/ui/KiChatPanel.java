package com.konvink.assistant.ui;

import com.konvink.assistant.model.Praxisauftrag;
import com.konvink.assistant.service.AiAssist;
import com.konvink.assistant.service.OllamaChatService;
import com.konvink.assistant.store.AppStore;
import com.konvink.assistant.store.AppStore.ChatEintrag;
import com.konvink.assistant.ui.UiKit.Karte;
import com.konvink.assistant.ui.UiKit.VerticalStackPanel;
import com.konvink.assistant.ui.UiKit.WrapLabel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * KI-Chat zu einem Praxisauftrag: Schnellaktionen, Text-Check, gespeicherter
 * Verlauf pro Auftrag, Modellwahl, freundliche Fehlermeldung mit "Neu versuchen"
 * und Skeleton-Ladeanimation.
 */
public class KiChatPanel extends JPanel {

    private final Praxisauftrag auftrag;
    private final OllamaChatService service = new OllamaChatService();
    private final AppStore.AuftragInfo info;

    private final VerticalStackPanel verlaufPanel = new VerticalStackPanel();
    private final JScrollPane verlaufScroll;
    private final JTextArea eingabe = new JTextArea(2, 10);
    private final JButton sendenButton = UiKit.primaryButton("Senden ➤");
    private final JComboBox<String> modellBox = new JComboBox<>();
    private boolean beschaeftigt;
    private boolean modellBoxAktualisiert;
    private java.util.function.Consumer<String> einfuegeListener;
    private final JTextField suchFeld = new JTextField();

    /** Wird aufgerufen, wenn der Nutzer eine KI-Antwort in die Notizen uebernehmen will. */
    public void setEinfuegeListener(java.util.function.Consumer<String> l) {
        this.einfuegeListener = l;
    }
    public KiChatPanel(Praxisauftrag auftrag) {
        super(new BorderLayout(0, 8));
        this.auftrag = auftrag;
        this.info = AppStore.get().info(auftrag.getDateiname());
        setBackground(Theme.CARD);
        setBorder(new EmptyBorder(12, 12, 12, 12));

        service.setSystemKontext("Du bist ein hilfreicher Assistent fuer kaufmaennische Lernende. "
                + "Hilf praegnant und auf Deutsch beim Beantworten/Formulieren fuer folgenden "
                + "Praxisauftrag. Verwende kein Markdown, nur Klartext.\n\n"
                + AiAssist.buildPraxisauftragPrompt(auftrag));

        add(baueKopf(), BorderLayout.NORTH);

        verlaufPanel.setOpaque(false);
        verlaufScroll = UiKit.scroll(verlaufPanel);
        add(verlaufScroll, BorderLayout.CENTER);
        add(baueUnterteil(), BorderLayout.SOUTH);

        ladeVerlauf();
        ladeModelle();
    }

    // ------------------------------------------------------------- Aufbau

    private JComponent baueKopf() {
        JPanel kopf = new JPanel(new BorderLayout(8, 0));
        kopf.setOpaque(false);
        JLabel titel = new JLabel("✨ KI-Chat");
        titel.setFont(titel.getFont().deriveFont(Font.BOLD, 15f));
        titel.setForeground(Theme.TEXT);
        kopf.add(titel, BorderLayout.WEST);

        JPanel rechts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rechts.setOpaque(false);
        modellBox.setToolTipText("Ollama-Modell");
        modellBox.addItem(OllamaChatService.modell());
        modellBox.setSelectedItem(OllamaChatService.modell());
        modellBox.addActionListener(e -> {
            Object sel = modellBox.getSelectedItem();
            if (modellBoxAktualisiert && sel != null) {
                AppStore.get().settings().modell = sel.toString();
                AppStore.get().speichern();
            }
        });
        JButton leeren = UiKit.iconButton("🗑", "Chatverlauf dieses Auftrags löschen");
        leeren.addActionListener(e -> {
            info.chat.clear();
            AppStore.get().speichern();
            service.verlaufLeeren();
            verlaufPanel.removeAll();
            zeigeWillkommen();
            verlaufPanel.revalidate();
            verlaufPanel.repaint();
        });
        rechts.add(modellBox);
        JButton export = UiKit.iconButton("💾", "Chatverlauf als Textdatei exportieren");
        export.addActionListener(e -> exportiere());
        rechts.add(export);
        rechts.add(leeren);
        kopf.add(rechts, BorderLayout.EAST);

        suchFeld.putClientProperty("JTextField.placeholderText", "Im Chat suchen …");
        suchFeld.putClientProperty("JTextField.showClearButton", true);
        suchFeld.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filtere(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filtere(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filtere(); }
        });
        JPanel huelle = new JPanel(new BorderLayout(0, 6));
        huelle.setOpaque(false);
        huelle.add(kopf, BorderLayout.NORTH);
        huelle.add(suchFeld, BorderLayout.SOUTH);
        return huelle;
    }

    /** Blendet Nachrichten aus, die den Suchbegriff nicht enthalten. */
    private void filtere() {
        String q = suchFeld.getText().trim().toLowerCase();
        for (Component c : verlaufPanel.getComponents()) {
            Object t = c instanceof JComponent jc ? jc.getClientProperty("text") : null;
            if (t != null) {
                c.setVisible(q.isEmpty() || t.toString().toLowerCase().contains(q));
            }
        }
        verlaufPanel.revalidate();
        verlaufPanel.repaint();
    }

    private void exportiere() {
        if (info.chat.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Es gibt noch keinen Chatverlauf zum Exportieren.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("Chat - " + auftrag.getDateiname().replaceAll("[^\\w\\-. ]", "_") + ".txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        StringBuilder sb = new StringBuilder("Chatverlauf: " + UiKit.name(auftrag) + "\n\n");
        for (ChatEintrag e : info.chat) {
            sb.append("user".equals(e.rolle) ? "Du:\n" : "KI:\n")
                    .append("user".equals(e.rolle) ? e.text : UiKit.cleanMarkdown(e.text)).append("\n\n");
        }
        try {
            java.nio.file.Files.writeString(fc.getSelectedFile().toPath(), sb.toString());
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, "Export fehlgeschlagen: " + ex.getMessage());
        }
    }

    private JComponent baueUnterteil() {
        JPanel unten = new JPanel();
        unten.setOpaque(false);
        unten.setLayout(new BoxLayout(unten, BoxLayout.Y_AXIS));

        JPanel chips = new JPanel(new GridLayout(0, 2, 6, 6));
        chips.setOpaque(false);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        chips.add(chipAktion("💡 Erkläre einfacher", Theme.CYAN,
                () -> sende("Erkläre mir diesen Praxisauftrag bitte in einfacheren Worten.")));
        chips.add(chipAktion("📌 Gib mir ein Beispiel", Theme.LIME,
                () -> sende("Gib mir ein konkretes Beispiel, wie eine gute Antwort auf diesen Praxisauftrag aussehen könnte.")));
        chips.add(chipAktion("📝 Prüfe meinen Text", Theme.PINK, this::pruefeText));
        chips.add(chipAktion("✅ Erstelle Checkliste", Theme.ORANGE,
                () -> sende("Erstelle mir eine kurze Checkliste mit den Schritten, die ich für diesen Praxisauftrag abarbeiten muss.")));
        unten.add(chips);
        unten.add(Box.createVerticalStrut(8));

        eingabe.setFont(eingabe.getFont().deriveFont(Font.PLAIN, 13f));
        eingabe.setLineWrap(true);
        eingabe.setWrapStyleWord(true);
        eingabe.setBorder(new EmptyBorder(6, 8, 6, 8));
        eingabe.setToolTipText("Enter oder Strg+Enter = senden, Shift+Enter = neue Zeile");
        UiKit.bindKey(eingabe, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                "senden", this::sendeEingabe);
        UiKit.bindKey(eingabe, JComponent.WHEN_FOCUSED,
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "sendenStrg", this::sendeEingabe);
        UiKit.bindKey(eingabe, JComponent.WHEN_FOCUSED,
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "neueZeile",
                () -> eingabe.replaceSelection("\n"));

        JScrollPane eingabeScroll = new JScrollPane(eingabe,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        eingabeScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        eingabeScroll.setPreferredSize(new Dimension(100, 62));

        sendenButton.addActionListener(e -> {
            if (beschaeftigt) {
                service.abbrechen();
            } else {
                sendeEingabe();
            }
        });
        JPanel zeile = new JPanel(new BorderLayout(8, 0));
        zeile.setOpaque(false);
        zeile.setAlignmentX(Component.LEFT_ALIGNMENT);
        zeile.add(eingabeScroll, BorderLayout.CENTER);
        zeile.add(sendenButton, BorderLayout.EAST);
        unten.add(zeile);
        return unten;
    }

    private JButton chipAktion(String text, Color farbe, Runnable aktion) {
        JButton chip = UiKit.chip(text, farbe);
        chip.addActionListener(e -> {
            if (!beschaeftigt) {
                aktion.run();
            }
        });
        return chip;
    }

    /** Fokus ins Eingabefeld (fuer Strg+F aus dem Hauptfenster nicht noetig, aber praktisch). */
    public void fokusEingabe() {
        eingabe.requestFocusInWindow();
    }

    // ---------------------------------------------------------- Verlauf

    private void ladeVerlauf() {
        zeigeWillkommen();
        for (ChatEintrag e : info.chat) {
            boolean eigene = "user".equals(e.rolle);
            service.addHistorie(e.rolle, e.text);
            Karte b = fuegeBlase(eigene ? "Du" : "KI", eigene ? e.text : UiKit.cleanMarkdown(e.text), eigene);
            if (!eigene) {
                fuegeAktionen(b);
            }
        }
    }

    private void zeigeWillkommen() {
        if (info.chat.isEmpty()) {
            verlaufPanel.add(UiKit.leerZustand("💬", "Noch keine Nachrichten",
                    "Stell eine Frage oder nutze eine der Schnellaktionen unten."));
        }
    }

    private void entferneLeerzustand() {
        for (Component c : verlaufPanel.getComponents()) {
            if ("leer".equals(c.getName())) {
                verlaufPanel.remove(c);
            }
        }
    }

    private Karte fuegeBlase(String absender, String text, boolean eigene) {
        entferneLeerzustand();
        Karte blase = new Karte(new BorderLayout(0, 4),
                eigene ? Theme.mitAlpha(Theme.ACCENT, 60) : Theme.SURFACE, eigene ? Theme.ACCENT : Theme.CYAN);
        JLabel name = new JLabel(absender, avatar(eigene ? "D" : "✨", eigene ? Theme.ACCENT : Theme.CYAN),
                SwingConstants.LEFT);
        name.setIconTextGap(8);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 11f));
        name.setForeground(eigene ? Theme.ACCENT : Theme.CYAN);
        blase.add(name, BorderLayout.NORTH);
        WrapLabel inhalt = new WrapLabel(text, Theme.TEXT);
        blase.add(inhalt, BorderLayout.CENTER);
        blase.putClientProperty("text", text);
        blase.putClientProperty("label", inhalt);
        verlaufPanel.add(blase);
        verlaufPanel.add(Box.createVerticalStrut(8));
        verlaufPanel.revalidate();
        zumEnde();
        return blase;
    }

    private static Icon avatar(String zeichen, Color farbe) {
        return new Icon() {
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(farbe);
                g2.fillOval(x, y, 20, 20);
                g2.setColor(Theme.aufAkzent());
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, 11f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(zeichen, x + (20 - fm.stringWidth(zeichen)) / 2, y + (20 + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }

            public int getIconWidth() {
                return 20;
            }

            public int getIconHeight() {
                return 20;
            }
        };
    }

    /** Haengt "Kopieren" und "In Notizen einfuegen" an eine fertige KI-Antwort. */
    private void fuegeAktionen(Karte blase) {
        JPanel leiste = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leiste.setOpaque(false);
        JButton kopieren = UiKit.secondaryButton("📋 Kopieren");
        kopieren.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection((String) blase.getClientProperty("text")), null);
            kopieren.setText("✔ Kopiert");
        });
        JButton einfuegen = UiKit.secondaryButton("📥 In Notizen einfügen");
        einfuegen.addActionListener(e -> {
            if (einfuegeListener != null) {
                einfuegeListener.accept((String) blase.getClientProperty("text"));
                einfuegen.setText("✔ Eingefügt");
            }
        });
        leiste.add(kopieren);
        leiste.add(einfuegen);
        leiste.setPreferredSize(new Dimension(10, kopieren.getPreferredSize().height + 2));
        kopieren.setVisible(false);
        einfuegen.setVisible(false);
        java.awt.event.MouseAdapter hover = new java.awt.event.MouseAdapter() {
            private void pruefe() {
                Point p = MouseInfo.getPointerInfo().getLocation();
                SwingUtilities.convertPointFromScreen(p, blase);
                boolean drin = blase.contains(p);
                kopieren.setVisible(drin);
                einfuegen.setVisible(drin);
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                pruefe();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                pruefe();
            }
        };
        blase.addMouseListener(hover);
        for (Component c : blase.getComponents()) {
            c.addMouseListener(hover);
        }
        kopieren.addMouseListener(hover);
        einfuegen.addMouseListener(hover);
        blase.add(leiste, BorderLayout.SOUTH);
        blase.revalidate();
    }

    private void zumEnde() {
        Timer t = new Timer(80, e -> {
            JScrollBar bar = verlaufScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
        t.setRepeats(false);
        t.start();
    }

    // ----------------------------------------------------------- Senden

    private void sendeEingabe() {
        String text = eingabe.getText().trim();
        if (text.isEmpty() || beschaeftigt) {
            return;
        }
        eingabe.setText("");
        sende(text);
    }

    private void pruefeText() {
        String entwurf = eingabe.getText().trim();
        if (entwurf.isEmpty()) {
            fuegeBlase("Tipp", "Füge zuerst deinen Entwurf ins Eingabefeld ein (z.B. dein SMARTes Ziel) "
                    + "und klicke dann auf \"Prüfe meinen Text\".", false);
            eingabe.requestFocusInWindow();
            return;
        }
        eingabe.setText("");
        sende("Prüfe bitte diesen Entwurf zum Praxisauftrag auf (1) Rechtschreibung und Grammatik, "
                + "(2) Stil und Verständlichkeit und (3) ob die SMART-Kriterien erfüllt sind "
                + "(Spezifisch, Messbar, Attraktiv, Realistisch, Terminiert). Nenne konkret, was "
                + "fehlt, und schlage eine verbesserte Fassung vor.\n\nEntwurf:\n" + entwurf, 700);
    }

    private void sende(String text) {
        sende(text, 350);
    }

    private void sende(String text, int maxTokens) {
        fuegeBlase("Du", text, true);
        info.chat.add(new ChatEintrag("user", text));
        AppStore.get().speichern();
        anfrage(text, maxTokens);
    }

    private void anfrage(String text, int maxTokens) {
        setBeschaeftigt(true);
        Karte laden = new Karte(new BorderLayout(), Theme.SURFACE, Theme.CYAN);
        laden.add(new UiKit.Skeleton(3), BorderLayout.CENTER);
        verlaufPanel.add(laden);
        verlaufPanel.revalidate();
        zumEnde();

        new SwingWorker<String, String>() {
            private Exception fehler;
            private Karte bubble;
            private final StringBuilder acc = new StringBuilder();

            @Override
            protected String doInBackground() {
                try {
                    if (!service.istErreichbar()) {
                        throw new java.io.IOException("Ollama nicht erreichbar");
                    }
                    service.setMaxTokens(maxTokens);
                    return service.sendeNachrichtStream(text, this::publish);
                } catch (Exception e) {
                    fehler = e;
                    java.util.logging.Logger.getLogger("KiChat").log(java.util.logging.Level.WARNING,
                            "KI-Anfrage fehlgeschlagen", e);
                    return null;
                }
            }

            @Override
            protected void process(List<String> teile) {
                if (bubble == null) {
                    verlaufPanel.remove(laden);
                    bubble = fuegeBlase("KI", "", false);
                }
                teile.forEach(acc::append);
                ((WrapLabel) bubble.getClientProperty("label")).setRohtext(acc.toString());
                bubble.putClientProperty("text", acc.toString());
                verlaufPanel.revalidate();
                zumEnde();
            }

            @Override
            protected void done() {
                verlaufPanel.remove(laden);
                setBeschaeftigt(false);
                String antwort;
                try {
                    antwort = get();
                } catch (Exception e) {
                    antwort = null;
                }
                if (antwort == null) {
                    if (bubble != null) {
                        verlaufPanel.remove(bubble);
                    }
                    zeigeFehler(text, maxTokens);
                } else if (bubble != null) {
                    String sauber = UiKit.cleanMarkdown(antwort);
                    ((WrapLabel) bubble.getClientProperty("label")).setRohtext(sauber);
                    bubble.putClientProperty("text", sauber);
                    info.chat.add(new ChatEintrag("assistant", antwort));
                    AppStore.get().speichern();
                    fuegeAktionen(bubble);
                }
                verlaufPanel.revalidate();
                verlaufPanel.repaint();
                zumEnde();
            }
        }.execute();
    }

    private void zeigeFehler(String text, int maxTokens) {
        Karte karte = new Karte(new BorderLayout(0, 8), Theme.mitAlpha(Theme.PINK, 40), Theme.PINK);
        karte.add(new WrapLabel("🔌 Die KI ist gerade nicht erreichbar.\n"
                + "Bitte prüfe, ob Ollama läuft und das Modell \"" + OllamaChatService.modell()
                + "\" installiert ist (ollama pull " + OllamaChatService.modell() + ").", Theme.TEXT),
                BorderLayout.CENTER);
        JButton nochmal = UiKit.primaryButton("↻ Neu versuchen");
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.setOpaque(false);
        south.add(nochmal);
        karte.add(south, BorderLayout.SOUTH);
        nochmal.addActionListener(e -> {
            verlaufPanel.remove(karte);
            anfrage(text, maxTokens);
        });
        verlaufPanel.add(karte);
        verlaufPanel.add(Box.createVerticalStrut(8));
        zumEnde();
    }

    private void setBeschaeftigt(boolean b) {
        beschaeftigt = b;
        sendenButton.setText(b ? "■ Stopp" : "Senden ➤");
    }

    private void ladeModelle() {
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return new OllamaChatService().listeModelle();
            }

            @Override
            protected void done() {
                try {
                    List<String> modelle = new ArrayList<>(get());
                    String aktuell = OllamaChatService.modell();
                    if (!modelle.contains(aktuell)) {
                        modelle.add(0, aktuell);
                    }
                    modellBoxAktualisiert = false;
                    modellBox.removeAllItems();
                    modelle.forEach(modellBox::addItem);
                    modellBox.setSelectedItem(aktuell);
                } catch (Exception ignored) {
                    // Ollama nicht erreichbar: aktuelles Modell bleibt stehen
                } finally {
                    modellBoxAktualisiert = true;
                }
            }
        }.execute();
    }
}
