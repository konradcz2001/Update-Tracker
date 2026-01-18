package com.github.konradcz2001.updatetracker;

import javafx.application.Application;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for the application.
 * This wrapper class is necessary to properly launch JavaFX when packaging as a fat JAR.
 */
public class Launcher {
    public static void main(String[] args) {
        // Suppress specific warnings from third-party libraries or internal JDK logging
        Logger logger = Logger.getLogger("java.net.CookieManager");
        logger.setLevel(Level.WARNING);
        logger.setUseParentHandlers(false);
        java.net.CookieHandler.setDefault(null);

        Application.launch(UpdateTrackerApp.class, args);
    }
}