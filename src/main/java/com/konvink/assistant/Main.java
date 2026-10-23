package com.konvink.assistant;

import com.konvink.assistant.ui.AppWindow;

import javax.swing.*;

/** Einstiegspunkt der Java-Version des Konvink Assistant. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        LogSetup.init();
        SwingUtilities.invokeLater(() -> {
            applyNimbusLookAndFeel();
            AppWindow window = new AppWindow();
            window.setVisible(true);
        });
    }

    private static void applyNimbusLookAndFeel() {
        try {
            // Flacher, moderner Look mit blauer Akzentfarbe und runden Ecken.
            UIManager.put("Component.arc", 14);
            UIManager.put("Button.arc", 14);
            UIManager.put("TextComponent.arc", 14);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("Component.focusColor", com.konvink.assistant.ui.Theme.CYAN);
            UIManager.put("Component.accentColor", com.konvink.assistant.ui.Theme.ACCENT);
            UIManager.put("Panel.background", com.konvink.assistant.ui.Theme.SURFACE);
            UIManager.put("TextArea.background", com.konvink.assistant.ui.Theme.CARD);
            UIManager.put("TextField.background", com.konvink.assistant.ui.Theme.CARD);
            UIManager.put("Button.background", com.konvink.assistant.ui.Theme.ACCENT);
            UIManager.put("Button.foreground", com.konvink.assistant.ui.Theme.aufAkzent());
            UIManager.put("ScrollPane.background", com.konvink.assistant.ui.Theme.SURFACE);
            UIManager.put("Viewport.background", com.konvink.assistant.ui.Theme.SURFACE);
            if (com.konvink.assistant.ui.Theme.DUNKEL
                    ? com.formdev.flatlaf.FlatDarkLaf.setup()
                    : com.formdev.flatlaf.FlatLightLaf.setup()) {
                return;
            }
        } catch (Throwable ignored) {
            // weiter mit Nimbus-Fallback
        }
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
        } catch (Exception ignored) {
            // Fallback: Standard-Look&Feel verwenden, falls Nimbus nicht verfuegbar ist.
        }
    }
}
