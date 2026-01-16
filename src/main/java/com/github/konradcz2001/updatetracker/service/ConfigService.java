package com.github.konradcz2001.updatetracker.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class ConfigService {
    private static final String FILE_NAME = "settings.json";
    private static final String APP_FOLDER_NAME = "UpdateTracker";
    private final ObjectMapper objectMapper;
    private AppConfig config;

    public ConfigService() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        // Ignore unknown properties to prevent crashes if file structure changes
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        loadConfig();
    }

    private File getConfigFile() {
        String appData = System.getenv("APPDATA");
        Path folderPath = (appData != null)
                ? Paths.get(appData, APP_FOLDER_NAME)
                : Paths.get(System.getProperty("user.home"), "." + APP_FOLDER_NAME);

        if (!Files.exists(folderPath)) {
            try {
                Files.createDirectories(folderPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return folderPath.resolve(FILE_NAME).toFile();
    }

    public void loadConfig() {
        File file = getConfigFile();
        if (file.exists()) {
            try {
                config = objectMapper.readValue(file, AppConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
                config = new AppConfig();
            }
        } else {
            config = new AppConfig();
        }
    }

    public void saveConfig() {
        try {
            objectMapper.writeValue(getConfigFile(), config);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public AppConfig getConfig() {
        return config;
    }

    public static class AppConfig {
        private String language = "en";

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        @JsonIgnore
        public Locale getLocale() {
            return Locale.of(language);
        }
    }
}