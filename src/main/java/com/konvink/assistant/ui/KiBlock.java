package com.konvink.assistant.ui;

import com.konvink.assistant.service.OllamaChatService;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Zeigt das Ergebnis einer einmaligen KI-Anfrage: erst eine Skeleton-
 * Ladeanimation, dann den Text - oder eine freundliche Fehlerkarte mit
 * "Neu versuchen"-Button, falls Ollama nicht erreichbar ist.
 */
public class KiBlock extends JPanel {

    private final String prompt;
    private final int maxTokens;
    private final Consumer<String> beiFertig;

    public KiBlock(String prompt, int maxTokens, Consumer<String> beiFertig) {
        super(new BorderLayout());
        this.prompt = prompt;
        this.maxTokens = maxTokens;
        this.beiFertig = beiFertig;
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    /** Zeigt direkt einen bereits bekannten Text (z.B. aus dem Cache). */
    public void zeigeText(String text) {
        removeAll();
        JPanel zeile = new JPanel(new BorderLayout(0, 6));
        zeile.setOpaque(false);
        zeile.add(new UiKit.WrapLabel(UiKit.cleanMarkdown(text), Theme.TEXT), BorderLayout.CENTER);
        JButton neu = UiKit.chip("↻ Neu generieren", Theme.CYAN);
        neu.addActionListener(e -> start());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        south.setOpaque(false);
        south.add(neu);
        zeile.add(south, BorderLayout.SOUTH);
        add(zeile, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    public void start() {
        removeAll();
        add(new UiKit.Skeleton(4), BorderLayout.CENTER);
        revalidate();
        repaint();

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                OllamaChatService service = new OllamaChatService();
                if (!service.istErreichbar()) {
                    throw new java.io.IOException("Ollama nicht erreichbar");
                }
                service.setMaxTokens(maxTokens);
                return service.sendeNachricht(prompt);
            }

            @Override
            protected void done() {
                try {
                    String text = get();
                    zeigeText(text);
                    if (beiFertig != null) {
                        beiFertig.accept(text);
                    }
                } catch (Exception e) {
                    zeigeFehler();
                }
            }
        }.execute();
    }

    private void zeigeFehler() {
        removeAll();
        UiKit.Karte karte = new UiKit.Karte(new BorderLayout(0, 8), Theme.mitAlpha(Theme.PINK, 40), Theme.PINK);
        karte.add(new UiKit.WrapLabel("🔌 Die KI ist gerade nicht erreichbar.\n"
                + "Bitte prüfe, ob Ollama läuft und das Modell \"" + OllamaChatService.modell()
                + "\" installiert ist.", Theme.TEXT), BorderLayout.CENTER);
        JButton nochmal = UiKit.primaryButton("↻ Neu versuchen");
        nochmal.addActionListener(e -> start());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.setOpaque(false);
        south.add(nochmal);
        karte.add(south, BorderLayout.SOUTH);
        add(karte, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
