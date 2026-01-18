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

/**
 * Manages application configuration (settings.json).
 * Handles loading and saving user preferences like language and theme.
 */
public class ConfigService {
    private static final String FILE_NAME = "settings.json";
    private static final String APP_FOLDER_NAME = "UpdateTracker";
    private final ObjectMapper objectMapper;
    private AppConfig config;
    private final Path configFolder;

    /**
     * Default constructor uses the system's AppData/Home directory.
     */
    public ConfigService() {
        this(getDefaultConfigPath());
    }

    /**
     * Constructor for testing purposes or custom paths.
     * @param configFolder The folder where settings.json will be stored.
     */
    public ConfigService(Path configFolder) {
        this.configFolder = configFolder;
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        loadConfig();
    }

    private static Path getDefaultConfigPath() {
        String appData = System.getenv("APPDATA");
        // Windows uses AppData/Roaming, Linux/Mac uses ~/.UpdateTracker
        return (appData != null)
                ? Paths.get(appData, APP_FOLDER_NAME)
                : Paths.get(System.getProperty("user.home"), "." + APP_FOLDER_NAME);
    }

    private File getConfigFile() {
        if (!Files.exists(configFolder)) {
            try {
                Files.createDirectories(configFolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return configFolder.resolve(FILE_NAME).toFile();
    }

    /**
     * Loads the configuration from disk.
     * Creates a default configuration if the file does not exist or is corrupted.
     */
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

    /**
     * Persists the current configuration to disk.
     */
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

    /**
     * Inner class representing the structure of the settings file.
     */
    public static class AppConfig {
        private String language = "en";
        private boolean darkMode = false;

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public boolean isDarkMode() { return darkMode; }
        public void setDarkMode(boolean darkMode) { this.darkMode = darkMode; }

        @JsonIgnore
        public Locale getLocale() {
            return Locale.of(language);
        }
    }
}