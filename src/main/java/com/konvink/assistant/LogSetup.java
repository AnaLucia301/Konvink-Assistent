package com.konvink.assistant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Schreibt alle Log-Meldungen und unbehandelten Fehler in eine Logdatei im Benutzerordner. */
public final class LogSetup {

    private static final Path LOG_DATEI = Paths.get(System.getProperty("user.home"),
            ".konvink-assistant", "konvink.log");

    private LogSetup() {
    }

    public static Path logDatei() {
        return LOG_DATEI;
    }

    public static void init() {
        try {
            Files.createDirectories(LOG_DATEI.getParent());
            // 1 MB pro Datei, 3 Dateien rotierend, anhaengend
            FileHandler handler = new FileHandler(LOG_DATEI.toString(), 1_000_000, 3, true);
            handler.setEncoding("UTF-8");
            handler.setFormatter(new SimpleFormatter() {
                @Override
                public synchronized String format(LogRecord r) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(java.time.LocalDateTime.ofInstant(r.getInstant(), java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    sb.append(' ').append(String.format("%-7s", r.getLevel().getName()));
                    sb.append(' ').append(r.getLoggerName() == null ? "" : r.getLoggerName());
                    sb.append(" - ").append(formatMessage(r)).append('\n');
                    if (r.getThrown() != null) {
                        java.io.StringWriter sw = new java.io.StringWriter();
                        r.getThrown().printStackTrace(new java.io.PrintWriter(sw));
                        sb.append(sw);
                    }
                    return sb.toString();
                }
            });
            handler.setLevel(Level.INFO);
            Logger root = Logger.getLogger("");
            root.addHandler(handler);
            root.setLevel(Level.INFO);
        } catch (IOException | SecurityException e) {
            System.err.println("Logdatei konnte nicht angelegt werden: " + e.getMessage());
        }

        Thread.setDefaultUncaughtExceptionHandler((t, e) ->
                Logger.getLogger("Unbehandelt").log(Level.SEVERE, "Unbehandelter Fehler in Thread " + t.getName(), e));
        Logger.getLogger(LogSetup.class.getName()).info("Konvink Assistant gestartet");
    }
}
