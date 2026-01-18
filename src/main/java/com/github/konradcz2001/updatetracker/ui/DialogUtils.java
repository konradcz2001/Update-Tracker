package com.github.konradcz2001.updatetracker.ui;

import com.github.konradcz2001.updatetracker.UpdateTrackerApp;
import com.github.konradcz2001.updatetracker.service.ConfigService;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Utility class for applying common styles to dialogs across the application.
 * Ensures consistent theme application (Light/Dark mode).
 */
public class DialogUtils {

    private DialogUtils() {
        // Prevent instantiation
    }

    public static void styleDialog(Dialog<?> dialog, ConfigService configService, ResourceBundle resources) {
        DialogPane pane = dialog.getDialogPane();
        String cssUrl = Objects.requireNonNull(UpdateTrackerApp.class
                        .getResource("style.css"))
                .toExternalForm();

        pane.getStylesheets().add(cssUrl);

        if (configService.getConfig().isDarkMode()) {
            pane.getStyleClass().add("dark-mode");
        }

        localizeButton(pane, ButtonType.OK, resources.getString("dialog.btn.ok"));
        localizeButton(pane, ButtonType.CANCEL, resources.getString("dialog.btn.cancel"));
        localizeButton(pane, ButtonType.YES, resources.getString("dialog.btn.yes"));
        localizeButton(pane, ButtonType.NO, resources.getString("dialog.btn.no"));
        localizeButton(pane, ButtonType.CLOSE, resources.getString("dialog.btn.close"));
        localizeButton(pane, ButtonType.APPLY, resources.getString("dialog.btn.apply"));
        localizeButton(pane, ButtonType.FINISH, resources.getString("dialog.btn.finish"));
        localizeButton(pane, ButtonType.NEXT, resources.getString("dialog.btn.next"));
        localizeButton(pane, ButtonType.PREVIOUS, resources.getString("dialog.btn.previous"));
    }

    private static void localizeButton(DialogPane pane, ButtonType type, String text) {
        Node node = pane.lookupButton(type);
        if (node instanceof Button) {
            ((Button) node).setText(text);
        }
    }
}