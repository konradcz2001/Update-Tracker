package com.github.konradcz2001.updatetracker.ui;

import com.github.konradcz2001.updatetracker.UpdateTrackerApp;
import com.github.konradcz2001.updatetracker.service.ConfigService;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.util.Objects;

/**
 * Utility class for applying common styles to dialogs across the application.
 */
public class DialogUtils {

    private DialogUtils() {
        // Prevent instantiation
    }

    public static void styleDialog(Dialog<?> dialog, ConfigService configService) {
        DialogPane pane = dialog.getDialogPane();
        String cssUrl = Objects.requireNonNull(UpdateTrackerApp.class
                        .getResource("style.css"))
                .toExternalForm();

        pane.getStylesheets().add(cssUrl);

        if (configService.getConfig().isDarkMode()) {
            pane.getStyleClass().add("dark-mode");
        }
    }
}