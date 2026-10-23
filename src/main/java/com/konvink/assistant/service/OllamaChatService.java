package com.konvink.assistant.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Echter Chat-Client fuer ein lokal laufendes Ollama (http://localhost:11434).
 * Schickt den kompletten Gespraechsverlauf bei jeder Anfrage mit (zustandslose
 * REST-API), damit das Modell den Kontext (Praxisauftrag + bisherige
 * Nachrichten) kennt.
 *
 * Voraussetzung: Ollama ist installiert, laeuft im Hintergrund (Standard nach
 * Installation) und das Modell wurde bereits per "ollama pull <model>"
 * heruntergeladen.
 */
public final class OllamaChatService {

    private static final Logger LOGGER = Logger.getLogger(OllamaChatService.class.getName());
    private static final String OLLAMA_CHAT_URL = "http://localhost:11434/api/chat";
    private static final String OLLAMA_TAGS_URL = "http://localhost:11434/api/tags";

    /** Aktuell gewaehltes Modell (aus den Einstellungen, jederzeit aenderbar). */
    public static String modell() {
        String m = com.konvink.assistant.store.AppStore.get().settings().modell;
        return m == null || m.isBlank() ? "llama3.2:1b" : m;
    }

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final Gson gson = new Gson();
    private int maxTokens = 300;

    /** Maximale Antwortlaenge in Tokens (Standard 300, fuer Text-Checks ggf. mehr). */
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    /** Einzelne Nachricht im Gespraechsverlauf (role: "system"/"user"/"assistant"). */
    public record ChatMessage(String role, String content) {
    }

    private final List<ChatMessage> verlauf = new ArrayList<>();

    /** Setzt/ueberschreibt die Systemnachricht (z.B. Praxisauftrag-Kontext) als erste Nachricht. */
    public void setSystemKontext(String kontext) {
        verlauf.removeIf(m -> m.role().equals("system"));
        verlauf.add(0, new ChatMessage("system", kontext));
    }

    /** Haengt eine bereits gespeicherte Nachricht an den Verlauf an, ohne etwas zu senden. */
    public void addHistorie(String role, String content) {
        verlauf.add(new ChatMessage(role, content));
    }

    /** Verwirft den Gespraechsverlauf (die Systemnachricht bleibt erhalten). */
    public void verlaufLeeren() {
        verlauf.removeIf(m -> !m.role().equals("system"));
    }

    /** Fragt die lokal installierten Modelle ab (leere Liste, falls Ollama nicht erreichbar ist). */
    public List<String> listeModelle() {
        List<String> namen = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_TAGS_URL))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonArray modelle = JsonParser.parseString(response.body())
                        .getAsJsonObject().getAsJsonArray("models");
                if (modelle != null) {
                    modelle.forEach(e -> namen.add(e.getAsJsonObject().get("name").getAsString()));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Modellliste nicht abrufbar: {0}", e.getMessage());
        }
        return namen;
    }

    /**
     * Schickt eine Nutzer-Nachricht an Ollama und gibt die Antwort zurueck
     * (blockierend - sollte daher nicht auf dem Swing-EDT aufgerufen werden).
     * Haengt sowohl die Frage als auch die Antwort an den Gespraechsverlauf
     * an, damit Folgefragen den Kontext kennen. Schlaegt die Anfrage fehl,
     * wird die Frage wieder aus dem Verlauf entfernt (damit ein erneuter
     * Versuch sie nicht doppelt enthaelt).
     */
    public String sendeNachricht(String nutzerText) throws IOException, InterruptedException {
        ChatMessage frage = new ChatMessage("user", nutzerText);
        verlauf.add(frage);
        try {
            return sendeVerlauf();
        } catch (IOException | InterruptedException | RuntimeException e) {
            verlauf.remove(frage);
            throw e;
        }
    }

    private String sendeVerlauf() throws IOException, InterruptedException {
        JsonObject body = baueBody(false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_CHAT_URL))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Ollama antwortete mit Status " + response.statusCode() + ": " + response.body());
        }

        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        String antwortText = responseJson.getAsJsonObject("message").get("content").getAsString();

        verlauf.add(new ChatMessage("assistant", antwortText));
        return antwortText;
    }

    private JsonObject baueBody(boolean stream) {
        JsonObject body = new JsonObject();
        body.addProperty("model", modell());
        body.addProperty("stream", stream);
        // Modell nach der Anfrage 30 Minuten im Speicher behalten, damit es
        // nicht bei jeder neuen Anfrage komplett neu geladen werden muss.
        body.addProperty("keep_alive", "30m");

        JsonObject options = new JsonObject();
        options.addProperty("num_predict", maxTokens);
        options.addProperty("num_ctx", 4096);
        body.add("options", options);

        JsonArray messagesJson = new JsonArray();
        for (ChatMessage m : verlauf) {
            JsonObject msg = new JsonObject();
            msg.addProperty("role", m.role());
            msg.addProperty("content", m.content());
            messagesJson.add(msg);
        }
        body.add("messages", messagesJson);
        return body;
    }

    private volatile boolean abbruch;

    /** Bricht eine laufende Streaming-Antwort ab (der bisher empfangene Text bleibt erhalten). */
    public void abbrechen() {
        abbruch = true;
    }

    /**
     * Wie {@link #sendeNachricht}, liefert die Antwort aber Stueck fuer Stueck an
     * {@code beiTeil} (Aufruf im Hintergrund-Thread!). Gibt den vollstaendigen Text zurueck.
     */
    public String sendeNachrichtStream(String nutzerText, java.util.function.Consumer<String> beiTeil)
            throws IOException, InterruptedException {
        abbruch = false;
        ChatMessage frage = new ChatMessage("user", nutzerText);
        verlauf.add(frage);
        StringBuilder gesamt = new StringBuilder();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_CHAT_URL))
                    .timeout(Duration.ofMinutes(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(baueBody(true))))
                    .build();
            HttpResponse<java.io.InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                if (response.statusCode() != 200) {
                    throw new IOException("Ollama antwortete mit Status " + response.statusCode());
                }
                String zeile;
                while (!abbruch && (zeile = reader.readLine()) != null) {
                    if (zeile.isBlank()) {
                        continue;
                    }
                    JsonObject json = JsonParser.parseString(zeile).getAsJsonObject();
                    if (json.has("error")) {
                        throw new IOException(json.get("error").getAsString());
                    }
                    if (json.has("message")) {
                        String teil = json.getAsJsonObject("message").get("content").getAsString();
                        if (!teil.isEmpty()) {
                            gesamt.append(teil);
                            beiTeil.accept(teil);
                        }
                    }
                    if (json.has("done") && json.get("done").getAsBoolean()) {
                        break;
                    }
                }
            }
            if (gesamt.length() == 0 && !abbruch) {
                throw new IOException("Leere Antwort von Ollama");
            }
            verlauf.add(new ChatMessage("assistant", gesamt.toString()));
            return gesamt.toString();
        } catch (IOException | InterruptedException | RuntimeException e) {
            verlauf.remove(frage);
            throw e;
        }
    }

    /**
     * Laedt ein Modell herunter (entspricht "ollama pull"). Meldet den Fortschritt
     * als Text an {@code status} (Aufruf im Hintergrund-Thread!).
     */
    public void ladeModell(String name, java.util.function.Consumer<String> status)
            throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("stream", true);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/pull"))
                .timeout(Duration.ofHours(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        HttpResponse<java.io.InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
            if (response.statusCode() != 200) {
                throw new IOException("Ollama antwortete mit Status " + response.statusCode());
            }
            String zeile;
            while ((zeile = reader.readLine()) != null) {
                if (zeile.isBlank()) {
                    continue;
                }
                JsonObject json = JsonParser.parseString(zeile).getAsJsonObject();
                if (json.has("error")) {
                    throw new IOException(json.get("error").getAsString());
                }
                String s = json.has("status") ? json.get("status").getAsString() : "";
                if (json.has("total") && json.has("completed") && json.get("total").getAsLong() > 0) {
                    long prozent = json.get("completed").getAsLong() * 100 / json.get("total").getAsLong();
                    s += " – " + prozent + " %";
                }
                status.accept(s);
            }
        }
    }

    /**
     * Prueft, ob Ollama erreichbar ist (schneller Verbindungstest), damit die
     * UI eine verstaendliche Fehlermeldung zeigen kann, statt lange zu haengen.
     */
    public boolean istErreichbar() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Ollama nicht erreichbar: {0}", e.getMessage());
            return false;
        }
    }
}
