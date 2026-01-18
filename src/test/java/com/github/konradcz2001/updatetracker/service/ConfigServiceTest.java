package com.github.konradcz2001.updatetracker.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class ConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void testSaveAndLoadConfig() {
        // Given a config service pointing to a temp directory
        ConfigService configService = new ConfigService(tempDir);

        // When we modify and save config
        configService.getConfig().setLanguage("pl");
        configService.getConfig().setDarkMode(true);
        configService.saveConfig();

        // Then reloading from the same directory should preserve values
        ConfigService newConfigService = new ConfigService(tempDir);
        Assertions.assertEquals("pl", newConfigService.getConfig().getLanguage());
        Assertions.assertTrue(newConfigService.getConfig().isDarkMode());
    }

    @Test
    void testDefaults() {
        ConfigService configService = new ConfigService(tempDir);
        Assertions.assertEquals("en", configService.getConfig().getLanguage());
        Assertions.assertFalse(configService.getConfig().isDarkMode());
    }
}