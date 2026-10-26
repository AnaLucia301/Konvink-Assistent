package com.konvink.assistant.service;

import com.konvink.assistant.model.Praxisauftrag;
import com.konvink.assistant.store.AppStore;
import com.konvink.assistant.ui.UiKit;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sendet automatisch eine kurze Outlook-Mail an den Praxisbildner, sobald ein
 * Praxisauftrag als "begonnen" oder "abgeschlossen" markiert wird. Nur aktiv,
 * wenn in den Einstellungen eine Praxisbildner-E-Mail hinterlegt und die
 * Benachrichtigung eingeschaltet ist. Laeuft im Hintergrund (SwingWorker),
 * damit ein evtl. langsames/fehlendes Outlook die Oberflaeche nicht blockiert.
 */
public final class PraxisbildnerMailer {

    private static final Logger LOGGER = Logger.getLogger(PraxisbildnerMailer.class.getName());
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private PraxisbildnerMailer() {
    }

    public static void benachrichtigeBegonnen(Praxisauftrag auftrag) {
        sende("begonnen", "🚀 Praxisauftrag begonnen: " + UiKit.name(auftrag),
                "Hallo,\n\nich habe soeben mit dem Praxisauftrag \"" + UiKit.name(auftrag)
                        + "\" begonnen (" + LocalDate.now().format(DATUM) + ").\n\n"
                        + "Freundliche Grüsse");
    }

    public static void benachrichtigeAbgeschlossen(Praxisauftrag auftrag) {
        sende("abgeschlossen", "✅ Praxisauftrag abgeschlossen: " + UiKit.name(auftrag),
                "Hallo,\n\nich habe den Praxisauftrag \"" + UiKit.name(auftrag)
                        + "\" soeben abgeschlossen (" + LocalDate.now().format(DATUM) + ").\n\n"
                        + "Freundliche Grüsse");
    }

    private static void sende(String anlass, String betreff, String text) {
        AppStore.Settings s = AppStore.get().settings();
        if (!s.mailBenachrichtigung) {
            return;
        }
        String an = s.praxisbildnerEmail == null ? "" : s.praxisbildnerEmail.trim();
        if (an.isEmpty()) {
            return;
        }
        boolean anzeigen = s.mailVorAnzeigen;
        new SwingWorker<Void, Void>() {
            private String fehler;

            @Override
            protected Void doInBackground() {
                try {
                    OutlookIntegration.sendeMail(an, betreff, text, anzeigen);
                } catch (Throwable e) {
                    // Throwable statt Exception: COM/JACOB kann z.B. UnsatisfiedLinkError
                    // werfen, wenn Outlook nicht installiert ist - das darf die App nicht stoeren.
                    fehler = e.getMessage();
                    LOGGER.log(Level.WARNING, "Mail an Praxisbildner (" + anlass + ") fehlgeschlagen", e);
                }
                return null;
            }

            @Override
            protected void done() {
                if (fehler != null) {
                    JOptionPane.showMessageDialog(null,
                            "Mail an Praxisbildner konnte nicht gesendet werden.\n"
                            + "Läuft Outlook und ist ein Konto eingerichtet?\n(" + fehler + ")");
                }
            }
        }.execute();
    }
}
