package com.konvink.assistant.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Laedt die config.json (gleiche Struktur wie im Python-Projekt). */
public class AppConfig {

    public KonvinkConfig konvink = new KonvinkConfig();
    public OutlookConfig outlook = new OutlookConfig();
    @SerializedName("ai_assist")
    public AiAssistConfig aiAssist = new AiAssistConfig();

    public static class KonvinkConfig {
        @SerializedName("login_url")
        public String loginUrl = "";
        public String username = "";
        @SerializedName("navigation_pfad")
        public List<String> navigationPfad = List.of();
        @SerializedName("filter_lehrjahr")
        public String filterLehrjahr = "";
    }

    public static class OutlookConfig {
        @SerializedName("standard_dauer_minuten")
        public int standardDauerMinuten = 60;
        @SerializedName("standard_betreff_praefix")
        public String standardBetreffPraefix = "";
    }

    public static class AiAssistConfig {
        @SerializedName("teams_deeplink")
        public String teamsDeeplink = "msteams://teams.microsoft.com/l/chat/0/0?users=28:copilot";
    }

    public static AppConfig load(Path configPath) throws IOException {
        Gson gson = new GsonBuilder().create();
        try (Reader reader = Files.newBufferedReader(configPath)) {
            AppConfig config = gson.fromJson(reader, AppConfig.class);
            return config != null ? config : new AppConfig();
        }
    }

    /**
     * Laedt das Konvink-Passwort aus der separaten secrets.json (liegt NICHT
     * im Git-Repo, siehe .gitignore). Erwartetes Format:
     * {"konvink_password": "..."}
     */
    public static String loadKonvinkPassword(Path secretsPath) throws IOException {
        Gson gson = new GsonBuilder().create();
        try (Reader reader = Files.newBufferedReader(secretsPath)) {
            Secrets secrets = gson.fromJson(reader, Secrets.class);
            return secrets != null ? secrets.konvinkPassword : "";
        }
    }

    private static class Secrets {
        String konvinkPassword = "";
    }
}
