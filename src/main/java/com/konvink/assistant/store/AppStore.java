package com.konvink.assistant.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lokaler Speicher (JSON-Datei im Benutzerordner) fuer alles, was die App
 * ueber Neustarts hinweg behalten soll: Einstellungen, Fensterlage,
 * zuletzt geoeffneter Auftrag, Favoriten, Notizen, Fristen, Checklisten,
 * Chatverlaeufe und die vom Nutzer verschobenen Termine.
 */
public final class AppStore {

    private static final Logger LOGGER = Logger.getLogger(AppStore.class.getName());
    private static final Path DATEI = Paths.get(System.getProperty("user.home"),
            ".konvink-assistant", "state.json");
    private static final AppStore INSTANCE = new AppStore();

    public static AppStore get() {
        return INSTANCE;
    }

    // ---------------------------------------------------------------- Daten

    public static final class Settings {
        public String ordner = "downloads/Praxisauftraege_Lehrjahr3";
        public String modell = "llama3.2:1b";
        public boolean dunkel = true;
        public String akzent = "Violett";
        /** E-Mail-Adresse des Praxisbildners/der Praxisbildnerin fuer automatische Benachrichtigungen. */
        public String praxisbildnerEmail = "";
        /** Ob bei Beginn/Abschluss eines Auftrags automatisch eine Outlook-Mail gesendet wird. */
        public boolean mailBenachrichtigung = false;
        /** Ob die Mail vor dem Versand zur Kontrolle angezeigt wird (statt direkt zu senden). */
        public boolean mailVorAnzeigen = true;
    }

    public static final class Fenster {
        public int x = -1;
        public int y = -1;
        public int breite = 1280;
        public int hoehe = 800;
        public boolean maximiert = true;
        /** Lage des Trenners Themen/Chat in der Detailansicht (0..1). */
        public double trennerChat = 0.45;
    }

    public static final class ChatEintrag {
        public String rolle = "user";
        public String text = "";

        public ChatEintrag() {
        }

        public ChatEintrag(String rolle, String text) {
            this.rolle = rolle;
            this.text = text;
        }
    }

    public static final class AuftragInfo {
        public boolean favorit;
        public boolean erledigt;
        /** Ob der Auftrag als "begonnen" markiert wurde (fuer Mail-Benachrichtigung). */
        public boolean begonnen;
        /** Verhindert doppelte Mails, wenn der Auftrag mehrfach umgeschaltet wird. */
        public boolean mailBegonnenGesendet;
        public boolean mailAbgeschlossenGesendet;
        /** Eigene Kategorien, z.B. "schwierig" oder "mit Chef klaeren". */
        public List<String> tags = new ArrayList<>();
        public String notiz = "";
        /** Zwischengespeicherte KI-Loesungsvorschlaege. */
        public String loesungen = "";
        /** Abgabefrist als ISO-Datum (yyyy-MM-dd) oder leer. */
        public String frist = "";
        /** Checkliste: Aufgabentext -> erledigt. */
        public Map<String, Boolean> checkliste = new LinkedHashMap<>();
        public List<ChatEintrag> chat = new ArrayList<>();
    }

    public static final class Block {
        /** ISO-Datum. */
        public String datum = "";
        /** Uhrzeit HH:mm. */
        public String zeit = "";
        public int dauer = 60;
        public String betreff = "";
        /** Optional: Dateiname des verknuepften Auftrags. */
        public String auftrag = "";
    }

    public static final class State {
        public List<Block> bloecke = new ArrayList<>();
        public Settings settings = new Settings();
        public Fenster fenster = new Fenster();
        public String letzterAuftrag = "";
        public String letzteErinnerung = "";
        /** Vom Nutzer angepasste Terminplanung: ISO-Datum -> Dateiname. */
        public Map<String, String> termine = new LinkedHashMap<>();
        public Map<String, AuftragInfo> auftraege = new LinkedHashMap<>();
    }

    // ------------------------------------------------------------- Zugriff

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private State state = new State();

    private AppStore() {
        laden();
    }

    public State state() {
        return state;
    }

    public Settings settings() {
        return state.settings;
    }

    public Fenster fenster() {
        return state.fenster;
    }

    /** Liefert die (ggf. neu angelegte) Info zu einem Auftrag, Schluessel = Dateiname. */
    public AuftragInfo info(String dateiname) {
        return state.auftraege.computeIfAbsent(dateiname, k -> new AuftragInfo());
    }

    private void normalisiere() {
        if (state.settings == null) state.settings = new Settings();
        if (state.fenster == null) state.fenster = new Fenster();
        if (state.termine == null) state.termine = new LinkedHashMap<>();
        if (state.bloecke == null) state.bloecke = new ArrayList<>();
        if (state.auftraege == null) state.auftraege = new LinkedHashMap<>();
        state.auftraege.values().forEach(a -> {
            if (a.tags == null) a.tags = new ArrayList<>();
            if (a.chat == null) a.chat = new ArrayList<>();
            if (a.checkliste == null) a.checkliste = new LinkedHashMap<>();
        });
    }

    /** Pfad der Zustandsdatei (fuer Anzeige/Backup). */
    public static Path dateiPfad() {
        return DATEI;
    }

    /** Speichert eine Kopie des kompletten Zustands (Backup). */
    public synchronized void exportiereNach(Path ziel) throws IOException {
        speichern();
        Files.copy(DATEI, ziel, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Ersetzt den kompletten Zustand durch eine Sicherung. Wirft IOException bei ungueltiger Datei. */
    public synchronized void importiereVon(Path quelle) throws IOException {
        State neu;
        try (Reader reader = Files.newBufferedReader(quelle, StandardCharsets.UTF_8)) {
            neu = gson.fromJson(reader, State.class);
        } catch (com.google.gson.JsonParseException e) {
            throw new IOException("Das ist keine gültige Sicherung: " + e.getMessage(), e);
        }
        if (neu == null) {
            throw new IOException("Die Datei enthält keine Daten.");
        }
        state = neu;
        normalisiere();
        speichern();
    }

    private void laden() {
        if (!Files.isRegularFile(DATEI)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(DATEI, StandardCharsets.UTF_8)) {
            State geladen = gson.fromJson(reader, State.class);
            if (geladen != null) {
                state = geladen;
                normalisiere();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Gespeicherter Zustand konnte nicht gelesen werden", e);
        }
    }

    public synchronized void speichern() {
        try {
            Files.createDirectories(DATEI.getParent());
            Path tmp = DATEI.resolveSibling("state.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(state, writer);
            }
            Files.move(tmp, DATEI, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Zustand konnte nicht gespeichert werden", e);
        }
    }
}
