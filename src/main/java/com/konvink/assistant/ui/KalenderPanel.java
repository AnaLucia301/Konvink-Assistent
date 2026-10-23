package com.konvink.assistant.ui;

import com.konvink.assistant.model.Praxisauftrag;
import com.konvink.assistant.store.AppStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Monats-Kalender (eigene Zeichnung). Zeigt Terminvorschlaege (farbiger
 * Kreis) und Abgabefristen (rosa Punkt). Terminvorschlaege lassen sich per
 * Drag &amp; Drop auf einen anderen Tag verschieben; ein Klick zeigt ein Popup
 * mit dem zugeordneten Auftrag (mit "Öffnen"-Link).
 */
public class KalenderPanel extends JPanel {

    private static final Locale LOCALE = Locale.GERMAN;

    private YearMonth currentMonth = YearMonth.now();
    private Map<LocalDate, Praxisauftrag> auftragByDate = new HashMap<>();
    private Map<LocalDate, List<Praxisauftrag>> fristen = new HashMap<>();
    private final JLabel monthLabel = new JLabel();
    private final CalendarGrid grid = new CalendarGrid();

    private BiConsumer<LocalDate, LocalDate> verschiebeListener;
    private Consumer<Praxisauftrag> oeffneListener;

    public KalenderPanel() {
        setLayout(new BorderLayout(0, 6));
        setOpaque(false);

        JButton prevButton = navButton("\u2039");
        JButton nextButton = navButton("\u203a");
        prevButton.addActionListener(e -> {
            currentMonth = currentMonth.minusMonths(1);
            refresh();
        });
        nextButton.addActionListener(e -> {
            currentMonth = currentMonth.plusMonths(1);
            refresh();
        });

        monthLabel.setHorizontalAlignment(SwingConstants.CENTER);
        monthLabel.setFont(monthLabel.getFont().deriveFont(Font.BOLD, 13f));
        monthLabel.setForeground(Theme.TEXT);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(prevButton, BorderLayout.WEST);
        header.add(monthLabel, BorderLayout.CENTER);
        header.add(nextButton, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        refresh();
    }

    private static JButton navButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorder(new EmptyBorder(2, 8, 2, 8));
        button.setForeground(Theme.TEXT);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
        return button;
    }

    /** Wird mit (alterTag, neuerTag) aufgerufen, wenn ein Termin per Drag&amp;Drop verschoben wurde. */
    public void setVerschiebeListener(BiConsumer<LocalDate, LocalDate> listener) {
        this.verschiebeListener = listener;
    }

    /** Wird aufgerufen, wenn im Popup ein Auftrag zum Oeffnen gewaehlt wird. */
    public void setOeffneListener(Consumer<Praxisauftrag> listener) {
        this.oeffneListener = listener;
    }

    /** Setzt die Terminvorschlaege (Tag -> Auftrag) und springt zum Monat des fruehesten. */
    public void setVorschlaege(Map<LocalDate, Praxisauftrag> zuordnung) {
        this.auftragByDate = new HashMap<>(zuordnung);
        auftragByDate.keySet().stream().min(LocalDate::compareTo)
                .ifPresent(first -> this.currentMonth = YearMonth.from(first));
        refresh();
    }

    /** Aktualisiert die Terminvorschlaege, ohne den angezeigten Monat zu wechseln. */
    public void aktualisiereVorschlaege(Map<LocalDate, Praxisauftrag> zuordnung) {
        this.auftragByDate = new HashMap<>(zuordnung);
        refresh();
    }

    /** Setzt die Abgabefristen (Tag -> Auftraege). */
    public void setFristen(Map<LocalDate, List<Praxisauftrag>> fristen) {
        this.fristen = new HashMap<>(fristen);
        refresh();
    }

    private List<AppStore.Block> bloecke = new ArrayList<>();
    private Consumer<AppStore.Block> blockLoeschListener;

    /** Setzt die eingeplanten Zeitbloecke. */
    public void setBloecke(List<AppStore.Block> bloecke) {
        this.bloecke = new ArrayList<>(bloecke);
        refresh();
    }

    public void setBlockLoeschListener(Consumer<AppStore.Block> l) {
        this.blockLoeschListener = l;
    }

    private List<AppStore.Block> bloeckeAm(LocalDate d) {
        List<AppStore.Block> res = new ArrayList<>();
        for (AppStore.Block b : bloecke) {
            if (d.toString().equals(b.datum)) {
                res.add(b);
            }
        }
        res.sort((x, y) -> String.valueOf(x.zeit).compareTo(String.valueOf(y.zeit)));
        return res;
    }

    private void refresh() {
        String m = currentMonth.getMonth().getDisplayName(TextStyle.FULL, LOCALE);
        monthLabel.setText(Character.toUpperCase(m.charAt(0)) + m.substring(1) + " " + currentMonth.getYear());
        grid.repaint();
    }

    private class CalendarGrid extends JPanel {
        private static final int ROWS = 7;
        private static final int COLS = 7;

        private LocalDate dragVon;
        private Point dragPunkt;
        private Point startPunkt;
        private boolean dragAktiv;

        CalendarGrid() {
            setOpaque(false);
            setPreferredSize(new Dimension(260, 190));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    LocalDate d = dateAtPoint(e.getX(), e.getY());
                    startPunkt = e.getPoint();
                    dragAktiv = false;
                    dragVon = d != null && auftragByDate.containsKey(d) ? d : null;
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    LocalDate ziel = dateAtPoint(e.getX(), e.getY());
                    if (dragAktiv && dragVon != null && ziel != null && !ziel.equals(dragVon)
                            && verschiebeListener != null) {
                        verschiebeListener.accept(dragVon, ziel);
                    }
                    dragVon = null;
                    dragAktiv = false;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    LocalDate d = dateAtPoint(e.getX(), e.getY());
                    if (d != null && (auftragByDate.containsKey(d) || fristen.containsKey(d)
                            || !bloeckeAm(d).isEmpty())) {
                        zeigePopup(e, d);
                    }
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragVon != null && startPunkt != null && startPunkt.distance(e.getPoint()) > 5) {
                        dragAktiv = true;
                        dragPunkt = e.getPoint();
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        repaint();
                    }
                }
            });
        }

        private LocalDate dateAtPoint(int px, int py) {
            double cellW = getWidth() / (double) COLS;
            double cellH = getHeight() / (double) ROWS;
            int col = (int) (px / cellW);
            int row = (int) (py / cellH);
            if (row < 1 || row >= ROWS || col < 0 || col >= COLS) {
                return null;
            }
            int firstColumn = currentMonth.atDay(1).getDayOfWeek().getValue() - 1;
            int index = (row - 1) * COLS + col - firstColumn;
            if (index < 0 || index >= currentMonth.lengthOfMonth()) {
                return null;
            }
            return currentMonth.atDay(index + 1);
        }

        private void zeigePopup(MouseEvent e, LocalDate datum) {
            JPanel inhalt = new JPanel();
            inhalt.setLayout(new BoxLayout(inhalt, BoxLayout.Y_AXIS));
            inhalt.setBorder(new EmptyBorder(8, 10, 8, 10));
            JLabel kopf = new JLabel(datum.getDayOfMonth() + "." + datum.getMonthValue() + "." + datum.getYear());
            kopf.setFont(kopf.getFont().deriveFont(Font.BOLD));
            kopf.setForeground(Theme.TEXT);
            inhalt.setBackground(Theme.SURFACE);
            inhalt.add(kopf);

            JPopupMenu popup = new JPopupMenu();
            Praxisauftrag termin = auftragByDate.get(datum);
            if (termin != null) {
                inhalt.add(Box.createVerticalStrut(4));
                inhalt.add(popupZeile("📌 Termin: ", termin, popup));
            }
            for (Praxisauftrag f : fristen.getOrDefault(datum, List.of())) {
                inhalt.add(Box.createVerticalStrut(4));
                inhalt.add(popupZeile("⏰ Frist: ", f, popup));
            }
            for (AppStore.Block b : bloeckeAm(datum)) {
                inhalt.add(Box.createVerticalStrut(4));
                JPanel zeile = new JPanel(new BorderLayout(6, 0));
                zeile.setOpaque(false);
                JLabel zeitLabel = new JLabel("🕒 " + b.zeit + " · " + b.dauer + " Min · " + b.betreff);
                zeitLabel.setForeground(Theme.TEXT);
                zeile.add(zeitLabel, BorderLayout.CENTER);
                JButton weg = UiKit.iconButton("🗑", "Zeitblock löschen");
                weg.addActionListener(ev -> {
                    popup.setVisible(false);
                    if (blockLoeschListener != null) {
                        blockLoeschListener.accept(b);
                    }
                });
                zeile.add(weg, BorderLayout.EAST);
                inhalt.add(zeile);
            }
            popup.setBackground(Theme.SURFACE);
            popup.setBorder(BorderFactory.createLineBorder(Theme.ACCENT, 1));
            popup.add(inhalt);
            popup.show(this, e.getX(), e.getY());
        }

        private JComponent popupZeile(String praefix, Praxisauftrag a, JPopupMenu popup) {
            String name = UiKit.name(a);
            String kurz = name.length() > 48 ? name.substring(0, 45) + "…" : name;
            JButton b = new JButton(praefix + kurz);
            b.setForeground(Theme.TEXT);
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.setFocusPainted(false);
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setToolTipText("Auftrag öffnen");
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(ev -> {
                popup.setVisible(false);
                if (oeffneListener != null) {
                    oeffneListener.accept(a);
                }
            });
            return b;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double cellW = getWidth() / (double) COLS;
            double cellH = getHeight() / (double) ROWS;

            g2.setFont(getFont().deriveFont(Font.PLAIN, 10.5f));
            DayOfWeek[] wd = DayOfWeek.values();
            for (int c = 0; c < COLS; c++) {
                g2.setColor(Theme.MUTED);
                drawCentered(g2, wd[c].getDisplayName(TextStyle.SHORT, LOCALE),
                        (int) (c * cellW), 0, (int) cellW, (int) cellH);
            }

            int firstColumn = currentMonth.atDay(1).getDayOfWeek().getValue() - 1;
            LocalDate today = LocalDate.now();
            LocalDate dropZiel = dragAktiv && dragPunkt != null ? dateAtPoint(dragPunkt.x, dragPunkt.y) : null;

            for (int day = 1; day <= currentMonth.lengthOfMonth(); day++) {
                int index = firstColumn + day - 1;
                int col = index % COLS;
                int row = 1 + index / COLS;
                LocalDate cellDate = currentMonth.atDay(day);
                int x = (int) (col * cellW);
                int y = (int) (row * cellH);
                int w = (int) cellW;
                int h = (int) cellH;

                if (cellDate.equals(today)) {
                    g2.setColor(Theme.CARD_SELECTED);
                    g2.fillOval(x + 3, y + 2, w - 6, h - 4);
                }
                if (cellDate.equals(dropZiel)) {
                    g2.setColor(Theme.mitAlpha(Theme.CYAN, 90));
                    g2.fillRoundRect(x + 2, y + 1, w - 4, h - 2, 8, 8);
                }

                boolean beimZiehen = dragAktiv && cellDate.equals(dragVon);
                boolean termin = auftragByDate.containsKey(cellDate) && !beimZiehen;
                boolean frist = fristen.containsKey(cellDate);
                if (termin) {
                    g2.setColor(Theme.ACCENT);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawOval(x + 3, y + 2, w - 6, h - 4);
                    g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
                } else {
                    g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
                }
                g2.setColor(termin ? Theme.ACCENT : (col >= 5 ? Theme.MUTED : Theme.TEXT));
                drawCentered(g2, String.valueOf(day), x, y, w, h);

                boolean block = !bloeckeAm(cellDate).isEmpty();
                if (frist) {
                    g2.setColor(Theme.PINK);
                    g2.fillOval(x + w / 2 - 2 + (block ? 4 : 0), y + h - 5, 5, 5);
                }
                if (block) {
                    g2.setColor(Theme.CYAN);
                    g2.fillOval(x + w / 2 - 2 - (frist ? 4 : 0), y + h - 5, 5, 5);
                }
            }

            if (dragAktiv && dragPunkt != null) {
                g2.setColor(Theme.mitAlpha(Theme.ACCENT, 200));
                g2.fillOval(dragPunkt.x - 11, dragPunkt.y - 11, 22, 22);
                g2.setColor(Theme.aufAkzent());
                g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
                drawCentered(g2, String.valueOf(dragVon.getDayOfMonth()), dragPunkt.x - 11, dragPunkt.y - 11, 22, 22);
            }
            g2.dispose();
        }

        private void drawCentered(Graphics2D g2, String text, int x, int y, int w, int h) {
            FontMetrics fm = g2.getFontMetrics();
            int tx = x + (w - fm.stringWidth(text)) / 2;
            int ty = y + (h + fm.getAscent()) / 2 - 2;
            g2.drawString(text, tx, ty);
        }
    }
}
