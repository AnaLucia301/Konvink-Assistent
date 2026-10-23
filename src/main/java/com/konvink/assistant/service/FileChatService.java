package com.konvink.assistant.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Datei-basierter "Relay-Chat" mit einem KI-Assistenten (z.B. GitHub Copilot
 * im VS-Code-Chat), der keinen direkten API-Zugriff hat: Fragen werden in
 * eine Datei geschrieben, die der Nutzer/die KI manuell liest; Antworten
 * werden in eine zweite Datei geschrieben, die hier im Hintergrund per
 * {@link WatchService} ueberwacht wird. Sobald sich die Antwort-Datei
 * aendert, wird der Callback (im Swing-EDT) mit dem neuen Inhalt aufgerufen.
 *
 * Ordnerstruktur (relativ zum Arbeitsverzeichnis der App):
 *   ki_chat/frage.txt    - zuletzt gestellte Frage (von der App geschrieben)
 *   ki_chat/antwort.txt  - Antwort (wird von aussen/manuell befuellt)
 */
public final class FileChatService {

    private static final Logger LOGGER = Logger.getLogger(FileChatService.class.getName());

    private static final Path CHAT_ORDNER = Paths.get("ki_chat");
    private static final Path FRAGE_DATEI = CHAT_ORDNER.resolve("frage.txt");
    private static final Path ANTWORT_DATEI = CHAT_ORDNER.resolve("antwort.txt");

    private Thread watcherThread;
    private volatile boolean stopRequested;

    public FileChatService() {
        try {
            Files.createDirectories(CHAT_ORDNER);
            if (!Files.exists(FRAGE_DATEI)) {
                Files.writeString(FRAGE_DATEI, "", StandardCharsets.UTF_8);
            }
            if (!Files.exists(ANTWORT_DATEI)) {
                Files.writeString(ANTWORT_DATEI, "", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Konnte ki_chat-Ordner/Dateien nicht anlegen", e);
        }
    }

    /** Schreibt eine neue Frage in die Frage-Datei (ueberschreibt den bisherigen Inhalt). */
    public void sendeFrage(String frageText) {
        try {
            Files.writeString(FRAGE_DATEI, frageText, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Konnte Frage nicht schreiben: " + FRAGE_DATEI, e);
        }
    }

    /**
     * Baut einen fertigen Hinweistext (fuer die Zwischenablage), der dem
     * Nutzer sagt, was er GitHub Copilot im Chat schreiben soll, damit
     * dieser die Frage liest und die Antwort in die Antwort-Datei schreibt.
     */
    public static String buildCopilotHinweis() {
        return "Bitte lies die Datei '" + FRAGE_DATEI.toAbsolutePath()
                + "', beantworte die darin enthaltene Frage zum Praxisauftrag "
                + "und schreibe deine Antwort (nur den Antworttext) in die Datei '"
                + ANTWORT_DATEI.toAbsolutePath() + "'.";
    }

    public Path getFrageDatei() {
        return FRAGE_DATEI;
    }

    public Path getAntwortDatei() {
        return ANTWORT_DATEI;
    }

    /**
     * Startet einen Hintergrund-Thread, der die Antwort-Datei auf Aenderungen
     * ueberwacht. Bei jeder Aenderung wird onAntwort (im Swing-EDT) mit dem
     * aktuellen Dateiinhalt aufgerufen. Mehrfaches Starten ist unschaedlich
     * (vorheriger Watcher wird zuerst gestoppt).
     */
    public void startWatching(Consumer<String> onAntwort) {
        stopWatching();
        stopRequested = false;

        watcherThread = new Thread(() -> watchLoop(onAntwort), "ki-chat-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    public void stopWatching() {
        stopRequested = true;
        if (watcherThread != null) {
            watcherThread.interrupt();
            watcherThread = null;
        }
    }

    private void watchLoop(Consumer<String> onAntwort) {
        try (WatchService watchService = CHAT_ORDNER.getFileSystem().newWatchService()) {
            CHAT_ORDNER.toAbsolutePath().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            while (!stopRequested) {
                WatchKey key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    Object context = event.context();
                    if (context != null && context.toString().equals(ANTWORT_DATEI.getFileName().toString())) {
                        String inhalt = liesAntwortDatei();
                        if (!inhalt.isBlank()) {
                            javax.swing.SwingUtilities.invokeLater(() -> onAntwort.accept(inhalt));
                        }
                    }
                }
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.FINE, "KI-Chat-Watcher beendet: " + e.getMessage());
        }
    }

    private String liesAntwortDatei() {
        try {
            return Files.readString(ANTWORT_DATEI, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Konnte Antwort-Datei nicht lesen: " + ANTWORT_DATEI, e);
            return "";
        }
    }
}
