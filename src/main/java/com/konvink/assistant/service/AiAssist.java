package com.konvink.assistant.service;

import com.konvink.assistant.model.Praxisauftrag;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bereitet einen Prompt-Text fuer die KI-Hilfe vor (Teams Copilot / ChatGPT
 * Web / Copilot Web), legt ihn in die Zwischenablage und oeffnet bei Bedarf
 * den jeweiligen Link im Standardbrowser bzw. in Teams.
 *
 * Java-Pendant zu ai_assist.py (dort via pyperclip + subprocess "start").
 */
public final class AiAssist {

    private static final Logger LOGGER = Logger.getLogger(AiAssist.class.getName());
    private static final String TEAMS_COPILOT_DEEPLINK =
            "msteams://teams.microsoft.com/l/chat/0/0?users=28:copilot";
    private static final String COPILOT_WEB_URL = "https://copilot.microsoft.com/";
    private static final String CHATGPT_WEB_URL = "https://chat.openai.com/";

    private AiAssist() {
    }

    /** Baut den strukturierten Prompt-Text fuer einen Praxisauftrag. */
    public static String buildPraxisauftragPrompt(Praxisauftrag auftrag) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hilf mir bitte beim Schreiben einer Antwort/Doku fuer folgenden Praxisauftrag:\n\n");
        sb.append("Titel: ").append(auftrag.getTitel()).append('\n');
        if (!auftrag.getHandlung().isEmpty()) {
            sb.append("Handlungskompetenz: ").append(auftrag.getHandlung()).append('\n');
        }
        if (!auftrag.getLehrjahr().isEmpty()) {
            sb.append("Lehrjahr: ").append(auftrag.getLehrjahr()).append('\n');
        }
        sb.append("\nAuszug aus dem Praxisauftrag:\n");
        String roh = auftrag.getRohtext();
        sb.append(roh.length() > 1500 ? roh.substring(0, 1500) + "..." : roh);
        return sb.toString();
    }

    /**
     * Haengt - falls in den Einstellungen hinterlegt - eine Musterloesung eines
     * frueheren, bereits erledigten Praxisauftrags als Stil-/Aufbau-Vorlage an
     * den Prompt an ("Few-Shot"-Beispiel). So orientiert sich die KI-Ausgabe am
     * gewuenschten Format (Abschnitte, Tonfall, Laenge), ohne dass ein eigenes
     * Modell trainiert werden muss - Ollama-Modelle koennen nicht nachtraeglich
     * trainiert werden, aber ein gutes Beispiel im Prompt wirkt sehr aehnlich.
     */
    public static String mitStilVorlage(String prompt) {
        String beispiel = com.konvink.assistant.store.AppStore.get().settings().beispielLoesung;
        if (beispiel == null || beispiel.isBlank()) {
            return prompt;
        }
        String gekuerzt = beispiel.length() > 3000 ? beispiel.substring(0, 3000) + "..." : beispiel;
        return prompt + "\n\n--- STIL- UND AUFBAUVORLAGE (Beispiel eines frueher fertig geschriebenen, "
                + "bereits bewerteten Praxisauftrags) ---\n"
                + "Orientiere dich bei Aufbau, Abschnitten, Tonfall und Detailtiefe an diesem Beispiel, "
                + "uebernimm aber NICHT dessen Inhalte 1:1, sondern schreibe passend zum aktuellen Auftrag:\n\n"
                + gekuerzt;
    }

    /** Kopiert den Prompt in die Zwischenablage und oeffnet Teams Copilot. */
    public static void prepareAiTextRequest(String promptText) {
        copyToClipboard(promptText);
        openTeamsCopilot();
    }

    /** Kopiert den Prompt in die Zwischenablage (ohne irgendetwas zu oeffnen). */
    public static void copyToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
    }

    /**
     * Oeffnet eine Web-URL im Standardbrowser. Wird fuer Copilot-Web und
     * ChatGPT-Web genutzt, da der Teams-Deeplink in manchen Firmen-Umgebungen
     * (z.B. BMW-Intranet-Redirects) nicht zuverlässig funktioniert.
     */
    public static void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (IOException | URISyntaxException | UnsupportedOperationException exc) {
            LOGGER.log(Level.WARNING, "Konnte URL nicht oeffnen ({0}): {1}", new Object[]{url, exc.getMessage()});
        }
    }

    public static void openCopilotWeb() {
        openUrl(COPILOT_WEB_URL);
    }

    public static void openChatGptWeb() {
        openUrl(CHATGPT_WEB_URL);
    }

    private static void openTeamsCopilot() {
        try {
            Desktop.getDesktop().browse(new URI(TEAMS_COPILOT_DEEPLINK));
        } catch (IOException | URISyntaxException | UnsupportedOperationException exc) {
            LOGGER.log(Level.WARNING, "Konnte Teams-Copilot-Deeplink nicht oeffnen: {0}", exc.getMessage());
        }
    }
}
