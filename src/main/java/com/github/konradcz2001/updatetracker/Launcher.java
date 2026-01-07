package com.github.konradcz2001.updatetracker;

import javafx.application.Application;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Launcher {
    public static void main(String[] args) {
        Logger logger = Logger.getLogger("java.net.CookieManager");
        logger.setLevel(Level.WARNING);
        logger.setUseParentHandlers(false);
        Application.launch(UpdateTrackerApp.class, args);
    }
}