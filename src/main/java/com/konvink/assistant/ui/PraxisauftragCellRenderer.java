package com.konvink.assistant.ui;

import com.konvink.assistant.model.Praxisauftrag;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Zeigt einen Praxisauftrag als kleine Karte (Titel fett, Handlung/Lehrjahr darunter) statt Rohtext. */
public class PraxisauftragCellRenderer extends JPanel implements ListCellRenderer<Praxisauftrag> {

    private final JLabel titelLabel = new JLabel();
    private final JLabel handlungLabel = new JLabel();
    private final JLabel metaLabel = new JLabel();
    private final JPanel zeile = new JPanel();

    private static final Color ACCENT = Theme.ACCENT;
    private static final Color CARD_BG = Theme.CARD;
    private static final Color CARD_BG_SELECTED = Theme.CARD_SELECTED;
    private static final Color BORDER_COLOR = Theme.BORDER;

    public PraxisauftragCellRenderer() {
        setLayout(new BorderLayout(2, 2));
        setOpaque(true);

        titelLabel.setFont(titelLabel.getFont().deriveFont(Font.BOLD, 14f));
        handlungLabel.setFont(handlungLabel.getFont().deriveFont(Font.PLAIN, 12f));
        handlungLabel.setForeground(Theme.TEXT);
        metaLabel.setFont(metaLabel.getFont().deriveFont(Font.ITALIC, 11f));
        metaLabel.setForeground(Theme.MUTED);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(titelLabel);
        textPanel.add(handlungLabel);
        textPanel.add(metaLabel);

        // Gleicher Aufbau wie bei den Kombi-Kacheln: aeussere Karte (Farbstreifen,
        // Kartenfarbe) mit einer dunkleren, eigenen Inhaltszeile darin.
        zeile.setLayout(new BorderLayout());
        zeile.setOpaque(true);
        zeile.setBorder(new EmptyBorder(8, 12, 8, 12));
        zeile.add(textPanel, BorderLayout.CENTER);

        add(zeile, BorderLayout.CENTER);
        setBorder(new CompoundBorder(
                new EmptyBorder(5, 0, 5, 0),
                new CompoundBorder(
                        BorderFactory.createMatteBorder(0, 6, 0, 0, Theme.ACCENT),
                        new EmptyBorder(10, 10, 10, 10))));
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends Praxisauftrag> list, Praxisauftrag value, int index,
            boolean isSelected, boolean cellHasFocus) {

        titelLabel.setText(value.getTitel().isBlank() ? value.getDateiname() : value.getTitel());
        handlungLabel.setText(value.getHandlung().isBlank() ? "(keine Handlungskompetenz erkannt)" : value.getHandlung());

        String meta = value.getLehrjahr().isBlank() ? value.getDateiname() : value.getLehrjahr() + " \u00b7 " + value.getDateiname();
        metaLabel.setText(meta);

        // Aeussere Karte: Kartenfarbe; innere Zeile: dunkler (bei Auswahl hervorgehoben)
        setBackground(isSelected ? CARD_BG_SELECTED : Theme.CARD);
        zeile.setBackground(isSelected ? Theme.CARD : Theme.SURFACE);
        titelLabel.setForeground(isSelected ? Theme.CYAN : Theme.TEXT);
        Color streifen = isSelected ? Theme.CYAN : Theme.akzent(index);
        setBorder(new CompoundBorder(
                new EmptyBorder(5, 0, 5, 0),
                new CompoundBorder(
                        BorderFactory.createMatteBorder(0, 6, 0, 0, streifen),
                        new EmptyBorder(10, 10, 10, 10))));

        return this;
    }
}
