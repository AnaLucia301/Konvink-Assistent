package com.konvink.assistant.ui;

import com.konvink.assistant.service.OllamaChatService;
import com.konvink.assistant.store.AppStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

/** Einstellungen: Ordnerpfad, Ollama-Modell, Theme hell/dunkel, Akzentfarbe. */
public final class SettingsDialog {

    private SettingsDialog() {
    }

    /**
     * Zeigt den Dialog. Liefert true, wenn etwas geaendert wurde, das einen
     * Neustart erfordert (Theme/Akzent) bzw. ein Neuladen (Ordner).
     */
    public static void zeigen(JFrame parent, Runnable ordnerGeaendert) {
        AppStore.Settings s = AppStore.get().settings();

        JTextField ordner = new JTextField(s.ordner, 28);
        JButton waehlen = UiKit.secondaryButton("Wählen …");
        waehlen.addActionListener(e -> {
            JFileChooser c = new JFileChooser(new File(ordner.getText()));
            c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (c.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                ordner.setText(c.getSelectedFile().getPath());
            }
        });

        JComboBox<String> modell = new JComboBox<>();
        modell.setEditable(true);
        modell.addItem(s.modell);
        modell.setSelectedItem(s.modell);
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return new OllamaChatService().listeModelle();
            }

            @Override
            protected void done() {
                try {
                    Object aktuell = modell.getSelectedItem();
                    for (String m : get()) {
                        boolean da = false;
                        for (int i = 0; i < modell.getItemCount(); i++) {
                            da |= modell.getItemAt(i).equals(m);
                        }
                        if (!da) {
                            modell.addItem(m);
                        }
                    }
                    modell.setSelectedItem(aktuell);
                } catch (Exception ignored) {
                    // Ollama nicht erreichbar - Modell kann trotzdem eingetippt werden
                }
            }
        }.execute();

        JRadioButton dunkel = new JRadioButton("Dunkel", s.dunkel);
        JRadioButton hell = new JRadioButton("Hell", !s.dunkel);
        ButtonGroup gruppe = new ButtonGroup();
        gruppe.add(dunkel);
        gruppe.add(hell);
        JPanel themeZeile = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        themeZeile.add(dunkel);
        themeZeile.add(hell);

        JComboBox<String> akzent = new JComboBox<>(Theme.AKZENT_AUSWAHL.keySet().toArray(new String[0]));
        akzent.setSelectedItem(s.akzent);

        JTextField praxisbildnerMail = new JTextField(s.praxisbildnerEmail, 28);
        JCheckBox mailAktiv = new JCheckBox("Bei Beginn/Abschluss automatisch Outlook-Mail senden", s.mailBenachrichtigung);
        JCheckBox mailVorher = new JCheckBox("Mail vor dem Senden zur Kontrolle anzeigen (empfohlen)", s.mailVorAnzeigen);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(6, 6, 6, 6));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Ordner mit Praxisaufträgen"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(ordner, c);
        c.gridx = 2;
        c.weightx = 0;
        form.add(waehlen, c);
        c.gridx = 0;
        c.gridy = 1;
        form.add(new JLabel("Ollama-Modell"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        form.add(modell, c);
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = 2;
        form.add(new JLabel("Erscheinungsbild"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        form.add(themeZeile, c);
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = 3;
        form.add(new JLabel("Akzentfarbe"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        form.add(akzent, c);
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = 4;
        form.add(new JLabel("Praxisbildner E-Mail"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        form.add(praxisbildnerMail, c);
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = 5;
        form.add(new JLabel("Benachrichtigung"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        JPanel mailZeile = new JPanel();
        mailZeile.setOpaque(false);
        mailZeile.setLayout(new BoxLayout(mailZeile, BoxLayout.Y_AXIS));
        mailAktiv.setAlignmentX(Component.LEFT_ALIGNMENT);
        mailVorher.setAlignmentX(Component.LEFT_ALIGNMENT);
        mailZeile.add(mailAktiv);
        mailZeile.add(mailVorher);
        form.add(mailZeile, c);
        c.gridwidth = 1;
        c.gridy = 6;
        c.gridx = 1;
        JLabel hinweis = new JLabel("Theme und Akzentfarbe gelten nach einem Neustart.");
        hinweis.setForeground(Theme.MUTED);
        form.add(hinweis, c);

        JButton pull = UiKit.secondaryButton("⬇ Modell laden …");
        pull.addActionListener(e -> modellLaden(parent, modell));
        JButton sichern = UiKit.secondaryButton("💾 Sicherung erstellen");
        sichern.addActionListener(e -> sicherungErstellen(parent));
        JButton laden = UiKit.secondaryButton("📂 Sicherung laden");
        laden.addActionListener(e -> sicherungLaden(parent));
        JButton log = UiKit.secondaryButton("📄 Logdatei öffnen");
        log.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(com.konvink.assistant.LogSetup.logDatei().toFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, "Logdatei nicht verfügbar: " + ex.getMessage());
            }
        });
        JButton stilVorlage = UiKit.secondaryButton("🎓 KI-Stilvorlage bearbeiten …");
        stilVorlage.addActionListener(e -> stilVorlageBearbeiten(parent));
        JPanel werkzeuge = new JPanel(new GridLayout(0, 2, 6, 6));
        werkzeuge.add(pull);
        werkzeuge.add(log);
        werkzeuge.add(sichern);
        werkzeuge.add(laden);
        werkzeuge.add(stilVorlage);
        c.gridy = 7;
        c.gridx = 0;
        c.gridwidth = 3;
        form.add(werkzeuge, c);
        int antwort = JOptionPane.showConfirmDialog(parent, form, "Einstellungen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (antwort != JOptionPane.OK_OPTION) {
            return;
        }

        boolean ordnerNeu = !ordner.getText().trim().equals(s.ordner);
        boolean designNeu = dunkel.isSelected() != s.dunkel || !String.valueOf(akzent.getSelectedItem()).equals(s.akzent);
        s.ordner = ordner.getText().trim();
        s.praxisbildnerEmail = praxisbildnerMail.getText().trim();
        s.mailBenachrichtigung = mailAktiv.isSelected();
        s.mailVorAnzeigen = mailVorher.isSelected();
        Object m = modell.getSelectedItem();
        if (m != null && !m.toString().isBlank()) {
            s.modell = m.toString().trim();
        }
        s.dunkel = dunkel.isSelected();
        s.akzent = String.valueOf(akzent.getSelectedItem());
        AppStore.get().speichern();

        if (ordnerNeu && ordnerGeaendert != null) {
            ordnerGeaendert.run();
        }
        if (designNeu) {
            fragNachNeustart(parent);
        }
    }

    private static void sicherungErstellen(JFrame parent) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("konvink-sicherung-" + java.time.LocalDate.now() + ".json"));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            AppStore.get().exportiereNach(fc.getSelectedFile().toPath());
            JOptionPane.showMessageDialog(parent, "Sicherung gespeichert:\n" + fc.getSelectedFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Sicherung fehlgeschlagen: " + ex.getMessage());
        }
    }

    private static void sicherungLaden(JFrame parent) {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        if (JOptionPane.showConfirmDialog(parent,
                "Alle aktuellen Daten (Notizen, Chats, Fristen …) werden durch die Sicherung ersetzt. Fortfahren?",
                "Sicherung laden", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            AppStore.get().importiereVon(fc.getSelectedFile().toPath());
            JOptionPane.showMessageDialog(parent, "Sicherung geladen. Die App startet jetzt neu.");
            neustart(parent);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Die Datei ist keine gültige Sicherung: " + ex.getMessage());
        }
    }

    /** Laedt ein Ollama-Modell herunter und zeigt den Fortschritt. */
    private static void modellLaden(JFrame parent, JComboBox<String> modellBox) {
        Object wahl = JOptionPane.showInputDialog(parent,
                "Modell wählen oder eigenen Namen eintippen.\n"
                        + "llama3.2:3b = schnell & besser als 1b, qwen2.5:7b = deutlich schlauer (braucht ~8 GB RAM).",
                "Modell laden", JOptionPane.PLAIN_MESSAGE, null,
                new String[]{"llama3.2:3b", "qwen2.5:7b", "qwen2.5:3b", "mistral:7b"}, "llama3.2:3b");
        if (wahl == null || wahl.toString().isBlank()) {
            return;
        }
        String name = wahl.toString().trim();
        JDialog dlg = new JDialog(parent, "Lade " + name, false);
        JLabel status = new JLabel("Starte …");
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(new EmptyBorder(14, 14, 14, 14));
        p.add(status, BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);
        dlg.setContentPane(p);
        dlg.setSize(420, 130);
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
        new SwingWorker<Void, String>() {
            private String fehler;

            @Override
            protected Void doInBackground() {
                try {
                    new OllamaChatService().ladeModell(name, this::publish);
                } catch (Exception e) {
                    fehler = e.getMessage();
                }
                return null;
            }

            @Override
            protected void process(List<String> teile) {
                status.setText(teile.get(teile.size() - 1));
            }

            @Override
            protected void done() {
                dlg.dispose();
                if (fehler != null) {
                    JOptionPane.showMessageDialog(parent, "Download fehlgeschlagen: " + fehler
                            + "\nLäuft Ollama?");
                } else {
                    modellBox.addItem(name);
                    modellBox.setSelectedItem(name);
                    JOptionPane.showMessageDialog(parent, "Modell \"" + name + "\" ist bereit und ausgewählt.\n"
                            + "Bestätige mit OK, um es zu verwenden.");
                }
            }
        }.execute();
    }

    /**
     * Zeigt einen grossen Texteditor, in dem der Nutzer die Musterloesung eines
     * frueher bereits fertig geschriebenen Praxisauftrags einfuegen kann. Dieser
     * Text wird danach allen KI-Anfragen (Loesungsvorschlaege, Checkliste,
     * Kombi-Fusion) als Stil-/Aufbau-Vorlage mitgegeben, damit die Ausgaben
     * aehnlich aufgebaut sind wie das Beispiel.
     */
    private static void stilVorlageBearbeiten(JFrame parent) {
        AppStore.Settings s = AppStore.get().settings();
        JTextArea feld = new JTextArea(s.beispielLoesung, 18, 60);
        feld.setLineWrap(true);
        feld.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(feld);

        JPanel inhalt = new JPanel(new BorderLayout(0, 8));
        inhalt.add(new JLabel("<html><div style='width:480px'>Füge hier den Text eines bereits "
                + "fertig geschriebenen, bewerteten Praxisauftrags ein (z.B. per Kopieren aus Konvink). "
                + "Die KI orientiert sich künftig an Aufbau, Abschnitten und Tonfall dieses Beispiels, "
                + "ohne dessen Inhalte zu übernehmen.</div></html>"), BorderLayout.NORTH);
        inhalt.add(scroll, BorderLayout.CENTER);

        int antwort = JOptionPane.showConfirmDialog(parent, inhalt, "KI-Stilvorlage",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (antwort == JOptionPane.OK_OPTION) {
            s.beispielLoesung = feld.getText().trim();
            AppStore.get().speichern();
            JOptionPane.showMessageDialog(parent, s.beispielLoesung.isEmpty()
                    ? "Stilvorlage entfernt."
                    : "Stilvorlage gespeichert (" + s.beispielLoesung.length() + " Zeichen).");
        }
    }

    /** Fragt, ob die App jetzt neu gestartet werden soll (noetig fuer Theme-Wechsel). */
    public static void fragNachNeustart(JFrame parent) {
        int r = JOptionPane.showConfirmDialog(parent,
                "Das neue Erscheinungsbild gilt nach einem Neustart. Jetzt neu starten?",
                "Neustart", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) {
            neustart(parent);
        }
    }

    private static void neustart(JFrame parent) {
        try {
            String java = ProcessHandle.current().info().command().orElse("javaw");
            File jar = new File(SettingsDialog.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!jar.isFile() || !jar.getName().endsWith(".jar")) {
                JOptionPane.showMessageDialog(parent,
                        "Bitte starte die Anwendung manuell neu (Start nicht aus einer JAR-Datei).");
                return;
            }
            new ProcessBuilder(java, "-jar", jar.getAbsolutePath())
                    .directory(new File(System.getProperty("user.dir"))).start();
            System.exit(0);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent, "Neustart nicht möglich: " + e.getMessage()
                    + "\nBitte starte die Anwendung manuell neu.");
        }
    }
}
