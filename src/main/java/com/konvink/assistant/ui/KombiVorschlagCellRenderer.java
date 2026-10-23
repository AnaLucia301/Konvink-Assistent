package com.konvink.assistant.ui;

import com.konvink.assistant.model.KombiVorschlag;
import com.konvink.assistant.model.Praxisauftrag;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Zeigt einen Kombi-Vorschlag uebersichtlich als zwei UNTEREINANDER
 * liegende Auftrags-Bloecke (Titel + Lehrjahr/Dateiname), verbunden durch
 * einen Pfeil nach unten, sowie darunter die Aehnlichkeit als einfaches,
 * farbiges Text-Label - damit auf einen Blick klar ist, WELCHE zwei
 * Auftraege zusammen gemacht werden koennten.
 */
public class KombiVorschlagCellRenderer extends JPanel implements ListCellRenderer<KombiVorschlag> {

    private final JLabel titel1Label = new JLabel();
    private final JLabel meta1Label = new JLabel();
    private final JLabel titel2Label = new JLabel();
    private final JLabel meta2Label = new JLabel();
    private final JLabel aehnlichkeitLabel = new JLabel();

    private static final Color BORDER_COLOR = Theme.BORDER;
    private static final Color CARD_BG = Theme.CARD;
    private static final Color CARD_BG_SELECTED = Theme.CARD_SELECTED;
    private static final Color BLOCK_BG = Theme.CARD;
    private static final Color TITEL_COLOR = Theme.TEXT;
    private static final Color META_COLOR = Theme.MUTED;
    private static final Color HIGH = new Color(0x1E9E5E);
    private static final Color MID = new Color(0xE08A00);

    public KombiVorschlagCellRenderer() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(true);

        JPanel block1 = buildBlock(titel1Label, meta1Label, "1");
        JPanel block2 = buildBlock(titel2Label, meta2Label, "2");

        JLabel arrow = new JLabel("\u2193 kombinieren mit \u2193");
        arrow.setFont(arrow.getFont().deriveFont(Font.PLAIN, 11f));
        arrow.setForeground(Theme.MUTED);
        arrow.setAlignmentX(Component.LEFT_ALIGNMENT);
        arrow.setBorder(new EmptyBorder(4, 2, 4, 0));

        aehnlichkeitLabel.setFont(aehnlichkeitLabel.getFont().deriveFont(Font.BOLD, 11.5f));
        aehnlichkeitLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        aehnlichkeitLabel.setBorder(new EmptyBorder(6, 2, 0, 0));

        add(block1);
        add(arrow);
        add(block2);
        add(aehnlichkeitLabel);

        setBorder(new CompoundBorder(
                new EmptyBorder(6, 0, 6, 0),
                new CompoundBorder(
                        BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                        new EmptyBorder(10, 12, 10, 12))));
    }

    private static JPanel buildBlock(JLabel titelLabel, JLabel metaLabel, String nummer) {
        JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setOpaque(true);
        block.setBackground(BLOCK_BG);
        block.setBorder(new EmptyBorder(8, 10, 8, 10));
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        titelLabel.setFont(titelLabel.getFont().deriveFont(Font.BOLD, 12.5f));
        titelLabel.setForeground(TITEL_COLOR);
        titelLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        metaLabel.setFont(metaLabel.getFont().deriveFont(Font.PLAIN, 11f));
        metaLabel.setForeground(META_COLOR);
        metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        block.add(titelLabel);
        block.add(Box.createVerticalStrut(2));
        block.add(metaLabel);
        return block;
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends KombiVorschlag> list, KombiVorschlag value, int index,
            boolean isSelected, boolean cellHasFocus) {

        Praxisauftrag a1 = value.getAuftrag1();
        Praxisauftrag a2 = value.getAuftrag2();

        // feste Breite via HTML-div erzwingt sauberen Zeilenumbruch statt
        // Abschneiden des Titels - Breite grob an typische Listenbreite
        // angepasst (JList ruft getPreferredSize erneut ab, sobald sich
        // die tatsaechliche Listenbreite aendert, siehe MainWindow).
        int wrapWidth = Math.max(220, list.getWidth() - 60);
        titel1Label.setText(htmlWrap(a1.getTitel(), wrapWidth));
        meta1Label.setText(metaText(a1));
        titel2Label.setText(htmlWrap(a2.getTitel(), wrapWidth));
        meta2Label.setText(metaText(a2));

        int prozent = Math.round((float) (value.getAehnlichkeit() * 100));
        aehnlichkeitLabel.setText("Ähnlichkeit: " + prozent + "%");
        aehnlichkeitLabel.setForeground(prozent >= 90 ? HIGH : MID);

        setBackground(isSelected ? CARD_BG_SELECTED : CARD_BG);

        return this;
    }

    private static String htmlWrap(String text, int widthPx) {
        return "<html><div style='width:" + widthPx + "px'>" + text + "</div></html>";
    }

    private static String metaText(Praxisauftrag auftrag) {
        return auftrag.getLehrjahr().isBlank()
                ? auftrag.getDateiname()
                : auftrag.getLehrjahr() + " \u00b7 " + auftrag.getDateiname();
    }
}
