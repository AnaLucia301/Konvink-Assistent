package com.konvink.assistant.ui;package com.konvink.assistant.ui;package com.konvink.assistant.ui;



import com.konvink.assistant.model.Praxisauftrag;



import javax.swing.*;import com.konvink.assistant.model.Praxisauftrag;import com.konvink.assistant.model.Praxisauftrag;

import javax.swing.border.EmptyBorder;

import java.awt.*;

import java.awt.event.MouseAdapter;

import java.awt.event.MouseEvent;import javax.swing.*;import javax.swing.*;

import java.time.DayOfWeek;

import java.time.LocalDate;import javax.swing.border.EmptyBorder;import javax.swing.border.EmptyBorder;

import java.time.YearMonth;

import java.time.format.TextStyle;import java.awt.*;import java.awt.*;

import java.util.HashMap;

import java.util.List;import java.awt.event.MouseAdapter;import java.awt.event.MouseAdapter;

import java.util.Locale;

import java.util.Map;import java.awt.event.MouseEvent;import java.awt.event.MouseEvent;

import java.util.function.BiConsumer;

import java.util.function.Consumer;import java.time.DayOfWeek;import java.time.DayOfWeek;



/**import java.time.LocalDate;import java.time.LocalDate;

 * Monats-Mini-Kalender (eigene Zeichnung). Zeigt Terminvorschlaege (farbiger

 * Kreis) und Abgabefristen (rosa Punkt). Terminvorschlaege lassen sich perimport java.time.YearMonth;import java.time.YearMonth;

 * Drag &amp; Drop auf einen anderen Tag verschieben; ein Klick zeigt ein Popup

 * mit dem zugeordneten Auftrag (mit "Öffnen"-Link).import java.time.format.TextStyle;import java.time.format.TextStyle;

 */

public class MiniCalendarPanel extends JPanel {import java.util.HashMap;import java.util.HashMap;



    private static final Locale LOCALE = Locale.GERMAN;import java.util.List;import java.util.HashSet;



    private YearMonth currentMonth = YearMonth.now();import java.util.Locale;import java.util.Locale;

    private Map<LocalDate, Praxisauftrag> auftragByDate = new HashMap<>();

    private Map<LocalDate, List<Praxisauftrag>> fristen = new HashMap<>();import java.util.Map;import java.util.Map;

    private final JLabel monthLabel = new JLabel();

    private final CalendarGrid grid = new CalendarGrid();import java.util.function.BiConsumer;import java.util.Set;



    private BiConsumer<LocalDate, LocalDate> verschiebeListener;import java.util.function.Consumer;

    private Consumer<Praxisauftrag> oeffneListener;

/**

    public MiniCalendarPanel() {

        setLayout(new BorderLayout(0, 6));/** * Einfacher Monats-Mini-Kalender (eigene Zeichnung, keine Drittbibliothek).

        setOpaque(false);

 * Monats-Mini-Kalender (eigene Zeichnung). Zeigt Terminvorschlaege (farbiger * Zeigt den aktuellen Monat als Raster (Mo-So) an. Tage, die in

        JButton prevButton = navButton("\u2039");

        JButton nextButton = navButton("\u203a"); * Kreis) und Abgabefristen (rosa Punkt). Terminvorschlaege lassen sich per * {@link #setVorschlaege(Map)} enthalten sind, werden mit einem farbigen

        prevButton.addActionListener(e -> {

            currentMonth = currentMonth.minusMonths(1); * Drag &amp; Drop auf einen anderen Tag verschieben; ein Klick zeigt ein Popup * Kreis umrandet - gedacht als Vorschlag, an welchem Tag ein Praxisauftrag

            refresh();

        }); * mit dem zugeordneten Auftrag (mit "Öffnen"-Link). * erledigt werden koennte. Klickt man auf ein markiertes Datum, erscheint

        nextButton.addActionListener(e -> {

            currentMonth = currentMonth.plusMonths(1); */ * ein kleines Popup mit dem Namen des zugeordneten Praxisauftrags.

            refresh();

        });public class MiniCalendarPanel extends JPanel { * Der heutige Tag wird zusaetzlich grau hinterlegt.



        monthLabel.setHorizontalAlignment(SwingConstants.CENTER); */

        monthLabel.setFont(monthLabel.getFont().deriveFont(Font.BOLD, 13f));

        monthLabel.setForeground(Theme.TEXT);    private static final Locale LOCALE = Locale.GERMAN;public class MiniCalendarPanel extends JPanel {



        JPanel header = new JPanel(new BorderLayout());

        header.setOpaque(false);

        header.add(prevButton, BorderLayout.WEST);    private YearMonth currentMonth = YearMonth.now();    private static final Locale LOCALE = Locale.GERMAN;

        header.add(monthLabel, BorderLayout.CENTER);

        header.add(nextButton, BorderLayout.EAST);    private Map<LocalDate, Praxisauftrag> auftragByDate = new HashMap<>();    private static final Color MARK_COLOR = Theme.ACCENT;



        add(header, BorderLayout.NORTH);    private Map<LocalDate, List<Praxisauftrag>> fristen = new HashMap<>();    private static final Color TODAY_BG = Theme.CARD_SELECTED;

        add(grid, BorderLayout.CENTER);

        refresh();    private final JLabel monthLabel = new JLabel();    private static final Color HEADER_COLOR = Theme.MUTED;

    }

    private final CalendarGrid grid = new CalendarGrid();    private static final Color WEEKEND_COLOR = Theme.MUTED;

    private static JButton navButton(String text) {

        JButton button = new JButton(text);

        button.setFocusPainted(false);

        button.setContentAreaFilled(false);    private BiConsumer<LocalDate, LocalDate> verschiebeListener;    private YearMonth currentMonth = YearMonth.now();

        button.setBorder(new EmptyBorder(2, 8, 2, 8));

        button.setForeground(Theme.TEXT);    private Consumer<Praxisauftrag> oeffneListener;    private Set<LocalDate> markedDates = new HashSet<>();

        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));

        return button;    private Map<LocalDate, Praxisauftrag> auftragByDate = new HashMap<>();

    }

    public MiniCalendarPanel() {    private final JLabel monthLabel = new JLabel();

    /** Wird mit (alterTag, neuerTag) aufgerufen, wenn ein Termin per Drag&amp;Drop verschoben wurde. */

    public void setVerschiebeListener(BiConsumer<LocalDate, LocalDate> listener) {        setLayout(new BorderLayout(0, 6));    private final CalendarGrid grid = new CalendarGrid();

        this.verschiebeListener = listener;

    }        setOpaque(false);



    /** Wird aufgerufen, wenn im Popup ein Auftrag zum Oeffnen gewaehlt wird. */

    public void setOeffneListener(Consumer<Praxisauftrag> listener) {

        this.oeffneListener = listener;        JButton prevButton = navButton("\u2039");    public MiniCalendarPanel() {

    }

        JButton nextButton = navButton("\u203a");        setLayout(new BorderLayout(0, 6));

    /** Setzt die Terminvorschlaege (Tag -> Auftrag) und springt zum Monat des fruehesten. */

    public void setVorschlaege(Map<LocalDate, Praxisauftrag> zuordnung) {        prevButton.addActionListener(e -> {        setOpaque(false);

        this.auftragByDate = new HashMap<>(zuordnung);

        auftragByDate.keySet().stream().min(LocalDate::compareTo)            currentMonth = currentMonth.minusMonths(1);

                .ifPresent(first -> this.currentMonth = YearMonth.from(first));

        refresh();            refresh();        JButton prevButton = navButton("\u2039");

    }

        });        JButton nextButton = navButton("\u203a");

    /** Aktualisiert die Terminvorschlaege, ohne den angezeigten Monat zu wechseln. */

    public void aktualisiereVorschlaege(Map<LocalDate, Praxisauftrag> zuordnung) {        nextButton.addActionListener(e -> {        prevButton.addActionListener(e -> {

        this.auftragByDate = new HashMap<>(zuordnung);

        refresh();            currentMonth = currentMonth.plusMonths(1);            currentMonth = currentMonth.minusMonths(1);

    }

            refresh();            refresh();

    /** Setzt die Abgabefristen (Tag -> Auftraege). */

    public void setFristen(Map<LocalDate, List<Praxisauftrag>> fristen) {        });        });

        this.fristen = new HashMap<>(fristen);

        refresh();        nextButton.addActionListener(e -> {

    }

        monthLabel.setHorizontalAlignment(SwingConstants.CENTER);            currentMonth = currentMonth.plusMonths(1);

    private void refresh() {

        String m = currentMonth.getMonth().getDisplayName(TextStyle.FULL, LOCALE);        monthLabel.setFont(monthLabel.getFont().deriveFont(Font.BOLD, 13f));            refresh();

        monthLabel.setText(Character.toUpperCase(m.charAt(0)) + m.substring(1) + " " + currentMonth.getYear());

        grid.repaint();        monthLabel.setForeground(Theme.TEXT);        });

    }



    private class CalendarGrid extends JPanel {

        private static final int ROWS = 7;        JPanel header = new JPanel(new BorderLayout());        monthLabel.setHorizontalAlignment(SwingConstants.CENTER);

        private static final int COLS = 7;

        header.setOpaque(false);        monthLabel.setFont(monthLabel.getFont().deriveFont(Font.BOLD, 13f));

        private LocalDate dragVon;

        private Point dragPunkt;        header.add(prevButton, BorderLayout.WEST);

        private Point startPunkt;

        private boolean dragAktiv;        header.add(monthLabel, BorderLayout.CENTER);        JPanel header = new JPanel(new BorderLayout());



        CalendarGrid() {        header.add(nextButton, BorderLayout.EAST);        header.setOpaque(false);

            setOpaque(false);

            setPreferredSize(new Dimension(260, 190));        header.add(prevButton, BorderLayout.WEST);



            addMouseListener(new MouseAdapter() {        add(header, BorderLayout.NORTH);        header.add(monthLabel, BorderLayout.CENTER);

                @Override

                public void mousePressed(MouseEvent e) {        add(grid, BorderLayout.CENTER);        header.add(nextButton, BorderLayout.EAST);

                    LocalDate d = dateAtPoint(e.getX(), e.getY());

                    startPunkt = e.getPoint();        refresh();

                    dragAktiv = false;

                    dragVon = d != null && auftragByDate.containsKey(d) ? d : null;    }        add(header, BorderLayout.NORTH);

                }

        add(grid, BorderLayout.CENTER);

                @Override

                public void mouseReleased(MouseEvent e) {    private static JButton navButton(String text) {

                    LocalDate ziel = dateAtPoint(e.getX(), e.getY());

                    if (dragAktiv && dragVon != null && ziel != null && !ziel.equals(dragVon)        JButton button = new JButton(text);        refresh();

                            && verschiebeListener != null) {

                        verschiebeListener.accept(dragVon, ziel);        button.setFocusPainted(false);    }

                    }

                    dragVon = null;        button.setContentAreaFilled(false);

                    dragAktiv = false;

                    setCursor(Cursor.getDefaultCursor());        button.setBorder(new EmptyBorder(2, 8, 2, 8));    private static JButton navButton(String text) {

                    repaint();

                }        button.setForeground(Theme.TEXT);        JButton button = new JButton(text);



                @Override        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));        button.setFocusPainted(false);

                public void mouseClicked(MouseEvent e) {

                    LocalDate d = dateAtPoint(e.getX(), e.getY());        return button;        button.setBorder(new EmptyBorder(2, 8, 2, 8));

                    if (d != null && (auftragByDate.containsKey(d) || fristen.containsKey(d))) {

                        zeigePopup(e, d);    }        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));

                    }

                }        return button;

            });

            addMouseMotionListener(new MouseAdapter() {    /** Wird mit (alterTag, neuerTag) aufgerufen, wenn ein Termin per Drag&amp;Drop verschoben wurde. */    }

                @Override

                public void mouseDragged(MouseEvent e) {    public void setVerschiebeListener(BiConsumer<LocalDate, LocalDate> listener) {

                    if (dragVon != null && startPunkt != null && startPunkt.distance(e.getPoint()) > 5) {

                        dragAktiv = true;        this.verschiebeListener = listener;    /** Setzt die Tage, die mit einem Kreis als Vorschlagstermin markiert werden sollen. */

                        dragPunkt = e.getPoint();

                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));    }    public void setMarkedDates(Set<LocalDate> dates) {

                        repaint();

                    }        setVorschlaege(dates.stream().collect(HashMap::new, (m, d) -> m.put(d, null), HashMap::putAll));

                }

            });    /** Wird aufgerufen, wenn im Popup ein Auftrag zum Oeffnen gewaehlt wird. */    }

        }

    public void setOeffneListener(Consumer<Praxisauftrag> listener) {

        private LocalDate dateAtPoint(int px, int py) {

            double cellW = getWidth() / (double) COLS;        this.oeffneListener = listener;    /**

            double cellH = getHeight() / (double) ROWS;

            int col = (int) (px / cellW);    }     * Setzt die Terminvorschlaege inkl. zugeordnetem Praxisauftrag. Beim Klick

            int row = (int) (py / cellH);

            if (row < 1 || row >= ROWS || col < 0 || col >= COLS) {     * auf ein markiertes Datum wird der Titel des zugeordneten Auftrags in

                return null;

            }    /** Setzt die Terminvorschlaege (Tag -> Auftrag) und springt zum Monat des fruehesten. */     * einem kleinen Popup angezeigt.

            int firstColumn = currentMonth.atDay(1).getDayOfWeek().getValue() - 1;

            int index = (row - 1) * COLS + col - firstColumn;    public void setVorschlaege(Map<LocalDate, Praxisauftrag> zuordnung) {     */

            if (index < 0 || index >= currentMonth.lengthOfMonth()) {

                return null;        this.auftragByDate = new HashMap<>(zuordnung);    public void setVorschlaege(Map<LocalDate, Praxisauftrag> zuordnung) {

            }

            return currentMonth.atDay(index + 1);        auftragByDate.keySet().stream().min(LocalDate::compareTo)        this.auftragByDate = new HashMap<>(zuordnung);

        }

                .ifPresent(first -> this.currentMonth = YearMonth.from(first));        this.markedDates = new HashSet<>(zuordnung.keySet());

        private void zeigePopup(MouseEvent e, LocalDate datum) {

            JPanel inhalt = new JPanel();        refresh();        // Beim Setzen neuer Vorschlaege direkt zum Monat des ersten

            inhalt.setLayout(new BoxLayout(inhalt, BoxLayout.Y_AXIS));

            inhalt.setBorder(new EmptyBorder(8, 10, 8, 10));    }        // Vorschlags springen, damit der Nutzer ihn sofort sieht.

            JLabel kopf = new JLabel(datum.getDayOfMonth() + "." + datum.getMonthValue() + "." + datum.getYear());

            kopf.setFont(kopf.getFont().deriveFont(Font.BOLD));        markedDates.stream().min(LocalDate::compareTo)

            inhalt.add(kopf);

    /** Aktualisiert die Terminvorschlaege, ohne den angezeigten Monat zu wechseln. */                .ifPresent(first -> this.currentMonth = YearMonth.from(first));

            JPopupMenu popup = new JPopupMenu();

            Praxisauftrag termin = auftragByDate.get(datum);    public void aktualisiereVorschlaege(Map<LocalDate, Praxisauftrag> zuordnung) {        refresh();

            if (termin != null) {

                inhalt.add(Box.createVerticalStrut(4));        this.auftragByDate = new HashMap<>(zuordnung);    }

                inhalt.add(popupZeile("📌 Termin: ", termin, popup));

            }        refresh();

            for (Praxisauftrag f : fristen.getOrDefault(datum, List.of())) {

                inhalt.add(Box.createVerticalStrut(4));    }    private void refresh() {

                inhalt.add(popupZeile("⏰ Frist: ", f, popup));

            }        monthLabel.setText(capitalize(currentMonth.getMonth().getDisplayName(TextStyle.FULL, LOCALE))

            popup.setBorder(BorderFactory.createLineBorder(Theme.ACCENT, 1));

            popup.add(inhalt);    /** Setzt die Abgabefristen (Tag -> Auftraege). */                + " " + currentMonth.getYear());

            popup.show(this, e.getX(), e.getY());

        }    public void setFristen(Map<LocalDate, List<Praxisauftrag>> fristen) {        grid.repaint();



        private JComponent popupZeile(String praefix, Praxisauftrag a, JPopupMenu popup) {        this.fristen = new HashMap<>(fristen);    }

            String name = UiKit.name(a);

            String kurz = name.length() > 48 ? name.substring(0, 45) + "…" : name;        refresh();

            JButton b = new JButton(praefix + kurz);

            b.setBorderPainted(false);    }    private static String capitalize(String text) {

            b.setContentAreaFilled(false);

            b.setFocusPainted(false);        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);

            b.setHorizontalAlignment(SwingConstants.LEFT);

            b.setToolTipText("Auftrag öffnen");    private void refresh() {    }

            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            b.addActionListener(ev -> {        String m = currentMonth.getMonth().getDisplayName(TextStyle.FULL, LOCALE);

                popup.setVisible(false);

                if (oeffneListener != null) {        monthLabel.setText(Character.toUpperCase(m.charAt(0)) + m.substring(1) + " " + currentMonth.getYear());    /** Innere Komponente, die das eigentliche Tagesraster zeichnet. */

                    oeffneListener.accept(a);

                }        grid.repaint();    private class CalendarGrid extends JPanel {

            });

            return b;    }

        }

        CalendarGrid() {

        @Override

        protected void paintComponent(Graphics g) {    private class CalendarGrid extends JPanel {            setOpaque(false);

            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();        private static final int ROWS = 7;            setPreferredSize(new Dimension(260, 190));

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        private static final int COLS = 7;            setToolTipText(""); // aktiviert getToolTipText()-Aufrufe

            double cellW = getWidth() / (double) COLS;

            double cellH = getHeight() / (double) ROWS;



            g2.setFont(getFont().deriveFont(Font.PLAIN, 10.5f));        private LocalDate dragVon;            addMouseListener(new MouseAdapter() {

            DayOfWeek[] wd = DayOfWeek.values();

            for (int c = 0; c < COLS; c++) {        private Point dragPunkt;                @Override

                g2.setColor(Theme.MUTED);

                drawCentered(g2, wd[c].getDisplayName(TextStyle.SHORT, LOCALE),        private Point startPunkt;                public void mouseClicked(MouseEvent e) {

                        (int) (c * cellW), 0, (int) cellW, (int) cellH);

            }        private boolean dragAktiv;                    LocalDate clicked = dateAtPoint(e.getX(), e.getY());



            int firstColumn = currentMonth.atDay(1).getDayOfWeek().getValue() - 1;                    if (clicked == null || !markedDates.contains(clicked)) {

            LocalDate today = LocalDate.now();

            LocalDate dropZiel = dragAktiv && dragPunkt != null ? dateAtPoint(dragPunkt.x, dragPunkt.y) : null;        CalendarGrid() {                        return;



            for (int day = 1; day <= currentMonth.lengthOfMonth(); day++) {            setOpaque(false);                    }

                int index = firstColumn + day - 1;

                int col = index % COLS;            setPreferredSize(new Dimension(260, 190));                    Praxisauftrag auftrag = auftragByDate.get(clicked);

                int row = 1 + index / COLS;

                LocalDate cellDate = currentMonth.atDay(day);                    zeigeTerminPopup(e, clicked, auftrag);

                int x = (int) (col * cellW);

                int y = (int) (row * cellH);            addMouseListener(new MouseAdapter() {                }

                int w = (int) cellW;

                int h = (int) cellH;                @Override            });



                if (cellDate.equals(today)) {                public void mousePressed(MouseEvent e) {        }

                    g2.setColor(Theme.CARD_SELECTED);

                    g2.fillOval(x + 3, y + 2, w - 6, h - 4);                    LocalDate d = dateAtPoint(e.getX(), e.getY());

                }

                if (cellDate.equals(dropZiel)) {                    startPunkt = e.getPoint();        /** Ermittelt das Datum unter dem angeklickten Punkt, oder null falls ausserhalb des Rasters. */

                    g2.setColor(Theme.mitAlpha(Theme.CYAN, 90));

                    g2.fillRoundRect(x + 2, y + 1, w - 4, h - 2, 8, 8);                    dragAktiv = false;        private LocalDate dateAtPoint(int px, int py) {

                }

                    dragVon = d != null && auftragByDate.containsKey(d) ? d : null;            int cols = 7;

                boolean beimZiehen = dragAktiv && cellDate.equals(dragVon);

                boolean termin = auftragByDate.containsKey(cellDate) && !beimZiehen;                }            int rows = 7;

                boolean frist = fristen.containsKey(cellDate);

                if (termin) {            double cellW = getWidth() / (double) cols;

                    g2.setColor(Theme.ACCENT);

                    g2.setStroke(new BasicStroke(2f));                @Override            double cellH = getHeight() / (double) rows;

                    g2.drawOval(x + 3, y + 2, w - 6, h - 4);

                    g2.setFont(getFont().deriveFont(Font.BOLD, 11f));                public void mouseReleased(MouseEvent e) {

                } else {

                    g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));                    LocalDate ziel = dateAtPoint(e.getX(), e.getY());            int col = (int) (px / cellW);

                }

                g2.setColor(termin ? Theme.ACCENT : (col >= 5 ? Theme.MUTED : Theme.TEXT));                    if (dragAktiv && dragVon != null && ziel != null && !ziel.equals(dragVon)            int row = (int) (py / cellH);

                drawCentered(g2, String.valueOf(day), x, y, w, h);

                            && verschiebeListener != null) {            if (row < 1 || row >= rows || col < 0 || col >= cols) {

                if (frist) {

                    g2.setColor(Theme.PINK);                        verschiebeListener.accept(dragVon, ziel);                return null;

                    g2.fillOval(x + w / 2 - 2, y + h - 5, 5, 5);

                }                    }            }

            }

                    dragVon = null;

            if (dragAktiv && dragPunkt != null) {

                g2.setColor(Theme.mitAlpha(Theme.ACCENT, 200));                    dragAktiv = false;            LocalDate firstOfMonth = currentMonth.atDay(1);

                g2.fillOval(dragPunkt.x - 11, dragPunkt.y - 11, 22, 22);

                g2.setColor(Theme.aufAkzent());                    setCursor(Cursor.getDefaultCursor());            int firstColumn = firstOfMonth.getDayOfWeek().getValue() - 1; // Montag = 0

                g2.setFont(getFont().deriveFont(Font.BOLD, 11f));

                drawCentered(g2, String.valueOf(dragVon.getDayOfMonth()), dragPunkt.x - 11, dragPunkt.y - 11, 22, 22);                    repaint();            int daysInMonth = currentMonth.lengthOfMonth();

            }

            g2.dispose();                }

        }

            int dayCounter = 1;

        private void drawCentered(Graphics2D g2, String text, int x, int y, int w, int h) {

            FontMetrics fm = g2.getFontMetrics();                @Override            for (int r = 1; r < rows && dayCounter <= daysInMonth; r++) {

            int tx = x + (w - fm.stringWidth(text)) / 2;

            int ty = y + (h + fm.getAscent()) / 2 - 2;                public void mouseClicked(MouseEvent e) {                for (int c = 0; c < cols && dayCounter <= daysInMonth; c++) {

            g2.drawString(text, tx, ty);

        }                    LocalDate d = dateAtPoint(e.getX(), e.getY());                    if (r == 1 && c < firstColumn) {

    }

}                    if (d != null && (auftragByDate.containsKey(d) || fristen.containsKey(d))) {                        continue;


                        zeigePopup(e, d);                    }

                    }                    if (r == row && c == col) {

                }                        return currentMonth.atDay(dayCounter);

            });                    }

            addMouseMotionListener(new MouseAdapter() {                    dayCounter++;

                @Override                }

                public void mouseDragged(MouseEvent e) {            }

                    if (dragVon != null && startPunkt != null && startPunkt.distance(e.getPoint()) > 5) {            return null;

                        dragAktiv = true;        }

                        dragPunkt = e.getPoint();

                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));        private void zeigeTerminPopup(MouseEvent e, LocalDate datum, Praxisauftrag auftrag) {

                        repaint();            String titel = auftrag != null ? auftrag.getTitel() : null;

                    }            if (titel == null || titel.isBlank()) {

                }                titel = "(kein Praxisauftrag zugeordnet)";

            });            }

        }            JLabel label = new JLabel("<html><div style='padding:4px 8px;'>"

                    + "<b>" + datum.getDayOfMonth() + "." + datum.getMonthValue() + "." + datum.getYear() + "</b><br>"

        private LocalDate dateAtPoint(int px, int py) {                    + titel + "</div></html>");

            double cellW = getWidth() / (double) COLS;            label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));

            double cellH = getHeight() / (double) ROWS;

            int col = (int) (px / cellW);            JPopupMenu popup = new JPopupMenu();

            int row = (int) (py / cellH);            popup.setBorder(BorderFactory.createLineBorder(MARK_COLOR, 1));

            if (row < 1 || row >= ROWS || col < 0 || col >= COLS) {            popup.add(label);

                return null;            popup.show(this, e.getX(), e.getY());

            }        }

            int firstColumn = currentMonth.atDay(1).getDayOfWeek().getValue() - 1;

            int index = (row - 1) * COLS + col - firstColumn;        @Override

            if (index < 0 || index >= currentMonth.lengthOfMonth()) {        protected void paintComponent(Graphics g) {

                return null;            super.paintComponent(g);

            }            Graphics2D g2 = (Graphics2D) g.create();

            return currentMonth.atDay(index + 1);            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        }

            int width = getWidth();

        private void zeigePopup(MouseEvent e, LocalDate datum) {            int height = getHeight();

            JPanel inhalt = new JPanel();            int cols = 7;

            inhalt.setLayout(new BoxLayout(inhalt, BoxLayout.Y_AXIS));            int rows = 7; // 1 Kopfzeile (Wochentage) + bis zu 6 Wochen

            inhalt.setBorder(new EmptyBorder(8, 10, 8, 10));            double cellW = width / (double) cols;

            JLabel kopf = new JLabel(datum.getDayOfMonth() + "." + datum.getMonthValue() + "." + datum.getYear());            double cellH = height / (double) rows;

            kopf.setFont(kopf.getFont().deriveFont(Font.BOLD));

            inhalt.add(kopf);            g2.setFont(getFont().deriveFont(Font.PLAIN, 10.5f));

            DayOfWeek[] weekDays = {

            JPopupMenu popup = new JPopupMenu();                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,

            Praxisauftrag termin = auftragByDate.get(datum);                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY

            if (termin != null) {            };

                inhalt.add(Box.createVerticalStrut(4));            for (int c = 0; c < cols; c++) {

                inhalt.add(popupZeile("📌 Termin: ", termin, popup));                String label = weekDays[c].getDisplayName(TextStyle.SHORT, LOCALE);

            }                g2.setColor(c >= 5 ? WEEKEND_COLOR : HEADER_COLOR);

            for (Praxisauftrag f : fristen.getOrDefault(datum, List.of())) {                drawCentered(g2, label, (int) (c * cellW), 0, (int) cellW, (int) cellH);

                inhalt.add(Box.createVerticalStrut(4));            }

                inhalt.add(popupZeile("⏰ Frist: ", f, popup));

            }            LocalDate firstOfMonth = currentMonth.atDay(1);

            popup.setBorder(BorderFactory.createLineBorder(Theme.ACCENT, 1));            int firstColumn = firstOfMonth.getDayOfWeek().getValue() - 1; // Montag = 0

            popup.add(inhalt);            int daysInMonth = currentMonth.lengthOfMonth();

            popup.show(this, e.getX(), e.getY());            LocalDate today = LocalDate.now();

        }

            int dayCounter = 1;

        private JComponent popupZeile(String praefix, Praxisauftrag a, JPopupMenu popup) {            for (int row = 1; row < rows && dayCounter <= daysInMonth; row++) {

            String name = UiKit.name(a);                for (int col = 0; col < cols && dayCounter <= daysInMonth; col++) {

            String kurz = name.length() > 48 ? name.substring(0, 45) + "…" : name;                    if (row == 1 && col < firstColumn) {

            JButton b = new JButton(praefix + kurz);                        continue;

            b.setBorderPainted(false);                    }

            b.setContentAreaFilled(false);                    LocalDate cellDate = currentMonth.atDay(dayCounter);

            b.setFocusPainted(false);                    int x = (int) (col * cellW);

            b.setHorizontalAlignment(SwingConstants.LEFT);                    int y = (int) (row * cellH);

            b.setToolTipText("Auftrag öffnen");

            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));                    if (cellDate.equals(today)) {

            b.addActionListener(ev -> {                        g2.setColor(TODAY_BG);

                popup.setVisible(false);                        g2.fillOval(x + 3, y + 2, (int) cellW - 6, (int) cellH - 4);

                if (oeffneListener != null) {                    }

                    oeffneListener.accept(a);

                }                    if (markedDates.contains(cellDate)) {

            });                        g2.setColor(MARK_COLOR);

            return b;                        g2.setStroke(new BasicStroke(2f));

        }                        g2.drawOval(x + 3, y + 2, (int) cellW - 6, (int) cellH - 4);

                    }

        @Override

        protected void paintComponent(Graphics g) {                    g2.setColor(col >= 5 ? WEEKEND_COLOR : Theme.TEXT);

            super.paintComponent(g);                    if (markedDates.contains(cellDate)) {

            Graphics2D g2 = (Graphics2D) g.create();                        g2.setColor(MARK_COLOR);

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);                        g2.setFont(getFont().deriveFont(Font.BOLD, 11f));

                    } else {

            double cellW = getWidth() / (double) COLS;                        g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));

            double cellH = getHeight() / (double) ROWS;                    }

                    drawCentered(g2, String.valueOf(dayCounter), x, y, (int) cellW, (int) cellH);

            g2.setFont(getFont().deriveFont(Font.PLAIN, 10.5f));

            DayOfWeek[] wd = DayOfWeek.values();                    dayCounter++;

            for (int c = 0; c < COLS; c++) {                }

                g2.setColor(Theme.MUTED);            }

                drawCentered(g2, wd[c].getDisplayName(TextStyle.SHORT, LOCALE),            g2.dispose();

                        (int) (c * cellW), 0, (int) cellW, (int) cellH);        }

            }

        private void drawCentered(Graphics2D g2, String text, int x, int y, int w, int h) {

            int firstColumn = currentMonth.atDay(1).getDayOfWeek().getValue() - 1;            FontMetrics fm = g2.getFontMetrics();

            LocalDate today = LocalDate.now();            int tx = x + (w - fm.stringWidth(text)) / 2;

            LocalDate dropZiel = dragAktiv && dragPunkt != null ? dateAtPoint(dragPunkt.x, dragPunkt.y) : null;            int ty = y + (h + fm.getAscent()) / 2 - 2;

            g2.drawString(text, tx, ty);

            for (int day = 1; day <= currentMonth.lengthOfMonth(); day++) {        }

                int index = firstColumn + day - 1;    }

                int col = index % COLS;}

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

                if (frist) {
                    g2.setColor(Theme.PINK);
                    g2.fillOval(x + w / 2 - 2, y + h - 5, 5, 5);
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
