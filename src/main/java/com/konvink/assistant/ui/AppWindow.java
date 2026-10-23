package com.konvink.assistant.ui;

import com.konvink.assistant.model.Praxisauftrag;
import com.konvink.assistant.store.AppStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.util.List;

/**
 * Hauptfenster: schlanke Huelle mit Kopfzeile (Theme-Umschalter, Einstellungen)
 * und einem CardLayout zwischen Uebersicht und Detailansicht.
 */
public class AppWindow extends JFrame {

    private static final String CARD_UEBERSICHT = "uebersicht";
    private static final String CARD_DETAIL = "detail";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final UebersichtPanel uebersicht;
    private DetailView detail;
    private boolean imDetail;

    public AppWindow() {
        super("Konvink Assistant");
        setIconImages(java.util.List.of(appIcon(16), appIcon(32), appIcon(64), appIcon(128)));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 550));
        fensterZustandHerstellen();

        uebersicht = new UebersichtPanel(this::oeffne);
        cards.setBackground(Theme.BG_APP);
        cards.add(uebersicht, CARD_UEBERSICHT);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG_APP);
        root.add(baueKopfzeile(), BorderLayout.NORTH);
        root.add(cards, BorderLayout.CENTER);
        setContentPane(root);

        tastaturkuerzel();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                fensterZustandSpeichern();
            }
        });

        uebersicht.setNachLaden(this::nachDemLaden);
        uebersicht.laden();
    }

    // ----------------------------------------------------------- Kopfzeile

    private JComponent baueKopfzeile() {
        JPanel kopf = new JPanel(new BorderLayout());
        kopf.setBackground(Theme.SURFACE);
        kopf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER), new EmptyBorder(8, 18, 8, 12)));

        JLabel titel = new JLabel("✨ Konvink Assistant");
        titel.setFont(titel.getFont().deriveFont(Font.BOLD, 16f));
        titel.setForeground(Theme.ACCENT);
        kopf.add(titel, BorderLayout.WEST);

        JPanel rechts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rechts.setOpaque(false);
        JButton themeToggle = UiKit.iconButton(Theme.DUNKEL ? "☀" : "🌙",
                Theme.DUNKEL ? "Zum hellen Design wechseln" : "Zum dunklen Design wechseln");
        themeToggle.addActionListener(e -> {
            AppStore.get().settings().dunkel = !Theme.DUNKEL;
            AppStore.get().speichern();
            SettingsDialog.fragNachNeustart(this);
        });
        JButton einstellungen = UiKit.iconButton("⚙", "Einstellungen");
        einstellungen.addActionListener(e -> SettingsDialog.zeigen(this, uebersicht::laden));
        JButton konvink = UiKit.secondaryButton("🌐 Konvink öffnen");
        konvink.setToolTipText("app.konvink.ch im Browser öffnen");
        konvink.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(java.net.URI.create("https://app.konvink.ch"));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Browser konnte nicht geöffnet werden: " + ex.getMessage());
            }
        });
        rechts.add(konvink);
        rechts.add(themeToggle);
        rechts.add(einstellungen);
        kopf.add(rechts, BorderLayout.EAST);
        return kopf;
    }

    // ------------------------------------------------------- Tastaturkuerzel

    /** Zeichnet das App-Icon: abgerundetes Quadrat im Violett/Cyan-Verlauf mit Haken. */
    private static java.awt.Image appIcon(int s) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(s, s, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setPaint(new GradientPaint(0, 0, new Color(0x8B6CF0), s, s, new Color(0x2CC4E0)));
        g.fillRoundRect(0, 0, s, s, s * 5 / 14, s * 5 / 14);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(s * 0.12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) (s * 0.27), (int) (s * 0.53), (int) (s * 0.44), (int) (s * 0.70));
        g.drawLine((int) (s * 0.44), (int) (s * 0.70), (int) (s * 0.74), (int) (s * 0.32));
        g.dispose();
        return img;
    }

    private void tastaturkuerzel() {
        JRootPane rp = getRootPane();
        UiKit.bindKey(rp, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke("ESCAPE"), "zurueck", () -> {
            if (imDetail) {
                zurueck();
            }
        });
        UiKit.bindKey(rp, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke("control F"), "suche", () -> {
            if (imDetail && detail != null) {
                detail.fokusSuche();
            } else {
                uebersicht.fokusSuche();
            }
        });
    }

    // ---------------------------------------------------------- Navigation

    private void oeffne(Praxisauftrag auftrag) {
        if (detail != null) {
            detail.schliessen();
            cards.remove(detail);
        }
        detail = new DetailView(auftrag, uebersicht.getVorschlaege(), this::oeffne, this::zurueck,
                uebersicht::aktualisiereFristen);
        cards.add(detail, CARD_DETAIL);
        cardLayout.show(cards, CARD_DETAIL);
        imDetail = true;
        AppStore.get().state().letzterAuftrag = auftrag.getDateiname();
        AppStore.get().speichern();
    }

    private void zurueck() {
        cardLayout.show(cards, CARD_UEBERSICHT);
        imDetail = false;
        if (detail != null) {
            detail.schliessen();
            cards.remove(detail);
            detail = null;
        }
        uebersicht.aktualisiereFristen();
        AppStore.get().state().letzterAuftrag = "";
        AppStore.get().speichern();
    }

    /** Nach dem Laden: letzten Auftrag wieder oeffnen oder (einmal pro Tag) an Fristen erinnern. */
    private void nachDemLaden() {
        String letzter = AppStore.get().state().letzterAuftrag;
        if (letzter != null && !letzter.isBlank() && !imDetail) {
            for (Praxisauftrag a : uebersicht.getAuftraege()) {
                if (letzter.equals(a.getDateiname())) {
                    oeffne(a);
                    break;
                }
            }
        }
        String heute = LocalDate.now().toString();
        if (!heute.equals(AppStore.get().state().letzteErinnerung)) {
            List<String> faellig = uebersicht.anstehendeFristen(3);
            if (!faellig.isEmpty()) {
                StringBuilder sb = new StringBuilder("Diese Abgaben stehen bald an:\n\n");
                faellig.forEach(f -> sb.append("• ").append(f).append('\n'));
                JOptionPane.showMessageDialog(this, sb.toString(), "⏰ Erinnerung", JOptionPane.INFORMATION_MESSAGE);
            }
            AppStore.get().state().letzteErinnerung = heute;
            AppStore.get().speichern();
        }
    }

    // ------------------------------------------------------ Fensterzustand

    private void fensterZustandHerstellen() {
        AppStore.Fenster f = AppStore.get().fenster();
        setSize(Math.max(900, f.breite), Math.max(550, f.hoehe));
        if (f.x >= 0 && f.y >= 0) {
            setLocation(f.x, f.y);
        } else {
            setLocationRelativeTo(null);
        }
        if (f.maximiert) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
    }

    private void fensterZustandSpeichern() {
        AppStore.Fenster f = AppStore.get().fenster();
        boolean max = (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        f.maximiert = max;
        if (!max) {
            f.x = getX();
            f.y = getY();
            f.breite = getWidth();
            f.hoehe = getHeight();
        }
        AppStore.get().speichern();
    }
}
