package com.github.konradcz2001.updatetracker;

import com.github.konradcz2001.updatetracker.service.ConfigService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.ResourceBundle;

public class UpdateTrackerApp extends Application {

    @Override
    public void start(Stage stage) {
        try {
            ConfigService configService = new ConfigService();

            // Use standard ResourceBundle loading (Environment handles UTF-8)
            ResourceBundle bundle = ResourceBundle.getBundle(
                    "com.github.konradcz2001.updatetracker.messages",
                    configService.getConfig().getLocale()
            );

            FXMLLoader fxmlLoader = new FXMLLoader(
                    UpdateTrackerApp.class.getResource("main-view.fxml"),
                    bundle
            );

            Scene scene = new Scene(fxmlLoader.load(), 900, 600);

            stage.setTitle("Update Tracker");
            var iconStream = UpdateTrackerApp.class.getResourceAsStream("/app_icon.png");
            if (iconStream != null) {
                stage.getIcons().add(new javafx.scene.image.Image(iconStream));
            }
            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            // Log critical startup errors
            System.err.println("CRITICAL ERROR IN START METHOD:");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        System.exit(0);
    }
}