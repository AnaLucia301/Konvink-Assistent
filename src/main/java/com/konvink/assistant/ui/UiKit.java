package com.konvink.assistant.ui;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.View;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Locale;

/** Gemeinsame UI-Bausteine (Karten, Chips, Lade-Animation, Scrollbars, Buttons ...). */
public final class UiKit {

    private UiKit() {
    }

    // ------------------------------------------------------------ Container

    /**
     * Vertikaler Stapel (BoxLayout Y_AXIS), der in einem JScrollPane immer die
     * Breite des Viewports uebernimmt - so ist nie eine horizontale Scrollbar
     * noetig, Inhalte brechen stattdessen um.
     */
    public static class VerticalStackPanel extends JPanel implements Scrollable {
        public VerticalStackPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(0, 0, 8, 0));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
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
     * Mehrzeiliges, umbrechendes Label (HTML-basiert). Die Hoehe richtet sich
     * nach der tatsaechlichen Breite - nach dem ersten Layout wird einmal
     * automatisch neu validiert, damit nie Text abgeschnitten wird.
     */
    public static class WrapLabel extends JLabel {
        private int letzteBreite = -1;

        public WrapLabel(String text) {
            this(text, null);
        }

        public WrapLabel(String text, Color farbe) {
            super(toHtml(text));
            if (farbe != null) {
                setForeground(farbe);
            }
            setVerticalAlignment(SwingConstants.TOP);
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (getWidth() != letzteBreite) {
                        letzteBreite = getWidth();
                        revalidate();
                    }
                }
            });
        }

        public void setRohtext(String text) {
            setText(toHtml(text));
        }

        private static String toHtml(String text) {
            String t = text == null ? "" : text;
            t = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\r", "").replace("\n", "<br>");
            return "<html>" + t + "</html>";
        }

        @Override
        public Dimension getPreferredSize() {
            View view = (View) getClientProperty(BasicHTML.propertyKey);
            int w = getWidth();
            Insets in = getInsets();
            if (view != null && w > 0) {
                view.setSize(Math.max(1, w - in.left - in.right), 0);
                int h = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)) + in.top + in.bottom;
                return new Dimension(w, h);
            }
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.min(d.width, 120), d.height);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, getPreferredSize().height);
        }
    }

    /** Abgerundete Karte mit optionalem Farbstreifen links und Hover-Farbe. */
    public static class Karte extends JPanel {
        private Color bg;
        private final Color streifen;
        private final int arc = 14;

        public Karte(LayoutManager layout, Color bg, Color streifen) {
            super(layout);
            this.bg = bg;
            this.streifen = streifen;
            setOpaque(false);
            setBorder(new EmptyBorder(12, streifen != null ? 20 : 14, 12, 14));
        }

        public void setBg(Color color) {
            this.bg = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape form = new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc);
            g2.setColor(bg);
            g2.fill(form);
            if (streifen != null) {
                g2.clip(form);
                g2.setColor(streifen);
                g2.fillRect(0, 0, 6, getHeight());
            }
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** Kleiner abgerundeter Button (Schnellaktion), eingefaerbt in der gegebenen Farbe. */
    public static JButton chip(String text, Color farbe) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean hover = getModel().isRollover() || getModel().isPressed();
                g2.setColor(Theme.mitAlpha(farbe, hover ? 70 : 36));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g2.setColor(Theme.mitAlpha(farbe, hover ? 255 : 160));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setForeground(Theme.TEXT);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 11.5f));
        button.setBorder(new EmptyBorder(5, 12, 5, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setRolloverEnabled(true);
        return button;
    }

    /** Pulsierende Platzhalter-Balken, solange die KI antwortet. */
    public static class Skeleton extends JPanel {
        private final int zeilen;
        private float phase;
        private Timer timer;

        public Skeleton(int zeilen) {
            this.zeilen = zeilen;
            setOpaque(false);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            timer = new Timer(60, e -> {
                phase += 0.15f;
                repaint();
            });
            timer.start();
        }

        @Override
        public void removeNotify() {
            if (timer != null) {
                timer.stop();
            }
            super.removeNotify();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(120, zeilen * 18 + 4);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float[] anteile = {1f, 0.88f, 0.62f, 0.8f, 0.5f};
            for (int i = 0; i < zeilen; i++) {
                double puls = (Math.sin(phase - i * 0.7) + 1) / 2;
                g2.setColor(Theme.mitAlpha(Theme.MUTED, 35 + (int) (55 * puls)));
                int breite = (int) ((getWidth() - 2) * anteile[i % anteile.length]);
                g2.fillRoundRect(0, i * 18 + 2, breite, 10, 10, 10);
            }
            g2.dispose();
        }
    }

    /** Freundlicher Leerzustand mit Emoji, Titel und Hinweis. */
    public static JPanel leerZustand(String emoji, String titel, String hinweis) {
        JPanel panel = new JPanel();
        panel.setName("leer");
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(28, 16, 28, 16));

        JLabel e = new JLabel(emoji, SwingConstants.CENTER);
        e.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 38));
        e.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel t = new JLabel(titel, SwingConstants.CENTER);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 14f));
        t.setForeground(Theme.TEXT);
        t.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel h = new JLabel("<html><div style='text-align:center;width:220px'>" + hinweis + "</div></html>",
                SwingConstants.CENTER);
        h.setFont(h.getFont().deriveFont(Font.PLAIN, 12f));
        h.setForeground(Theme.MUTED);
        h.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(e);
        panel.add(Box.createVerticalStrut(8));
        panel.add(t);
        panel.add(Box.createVerticalStrut(4));
        panel.add(h);
        return panel;
    }

    // --------------------------------------------------------------- Stil

    public static javax.swing.border.Border titledBorder(String title) {
        var titled = BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), title);
        titled.setTitleFont(titled.getTitleFont().deriveFont(Font.BOLD, 13f));
        titled.setTitleColor(Theme.TEXT);
        return new CompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                new CompoundBorder(new EmptyBorder(10, 14, 14, 14), titled));
    }

    public static JLabel formLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        label.setForeground(Theme.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    public static JLabel sectionHeading(String text) {
        JLabel label = new JLabel(text.toUpperCase(Locale.GERMAN));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setForeground(Theme.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    public static JTextField styledField(JTextField field) {
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        field.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                new EmptyBorder(4, 8, 4, 8)));
        return field;
    }

    public static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(Theme.ACCENT);
        button.setForeground(Theme.aufAkzent());
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 12.5f));
        button.setBorder(new EmptyBorder(8, 14, 8, 14));
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    public static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(Theme.SURFACE);
        button.setForeground(Theme.TEXT);
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 12.5f));
        button.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                new EmptyBorder(8, 14, 8, 14)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /** Schlanker Icon-/Text-Button ohne Rahmen (z.B. Stern, Zoom). */
    public static JButton iconButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setForeground(Theme.TEXT);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 15f));
        button.setBorder(new EmptyBorder(4, 8, 4, 8));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /** Macht die vertikale Scrollbar duenn und modern (ohne Pfeil-Buttons und Track). */
    public static void flatScrollBar(JScrollPane scrollPane) {
        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        vertical.setPreferredSize(new Dimension(10, 0));
        vertical.setUnitIncrement(16);
        vertical.setUI(new BasicScrollBarUI() {
            @Override
            protected JButton createDecreaseButton(int orientation) {
                return nullButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return nullButton();
            }

            private JButton nullButton() {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(0, 0));
                button.setMinimumSize(new Dimension(0, 0));
                button.setMaximumSize(new Dimension(0, 0));
                return button;
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
                // kein Track-Hintergrund
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
                if (thumbBounds.isEmpty() || !c.isEnabled()) {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.mitAlpha(Theme.ACCENT, 200));
                g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y, thumbBounds.width - 4,
                        thumbBounds.height, 8, 8);
                g2.dispose();
            }
        });
    }

    /** Scrollbereich um eine Komponente (ohne Rand, mit flacher Scrollbar, ohne horizontales Scrollen). */
    public static JScrollPane scroll(Component inhalt) {
        JScrollPane pane = new JScrollPane(inhalt,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getViewport().setOpaque(false);
        // Blit-Scrolling hinterlaesst bei durchsichtigem Viewport Zeichenreste
        // (z.B. Kartenstreifen ausserhalb des Panels) - daher vollstaendig neu zeichnen.
        pane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        pane.setOpaque(false);
        flatScrollBar(pane);
        return pane;
    }

    /**
     * Wandelt haeufige Markdown-Markierungen eines LLM in lesbaren Klartext um
     * (**fett**, Ueberschriften, Aufzaehlungen).
     */
    public static String cleanMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                .replaceAll("(?<!\\*)\\*(?!\\*)([^*\\n]+?)\\*(?!\\*)", "$1")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[-*]\\s+", "• ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    /** Registriert ein Tastaturkuerzel an einer Komponente. */
    public static void bindKey(JComponent komponente, int bedingung, KeyStroke taste, String name, Runnable aktion) {
        komponente.getInputMap(bedingung).put(taste, name);
        komponente.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                aktion.run();
            }
        });
    }

    /** Anzeigename eines Auftrags: Titel, sonst Dateiname. */
    /** Stabile Farbe pro Tag-Name. */
    public static Color tagFarbe(String tag) {
        return Theme.akzent(Math.abs(tag.toLowerCase().hashCode()) % 6);
    }

    public static String name(com.konvink.assistant.model.Praxisauftrag a) {
        String t = a.getTitel();
        return t == null || t.isBlank() ? a.getDateiname() : t;
    }
}
