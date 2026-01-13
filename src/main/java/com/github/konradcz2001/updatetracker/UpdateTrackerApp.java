package com.github.konradcz2001.updatetracker;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class UpdateTrackerApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // Loading the main view from FXML
        FXMLLoader fxmlLoader = new FXMLLoader(UpdateTrackerApp.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 900, 600);

        stage.setTitle("Update Tracker");
        var iconStream = UpdateTrackerApp.class.getResourceAsStream("/app_icon.png");
        if (iconStream != null) {
            stage.getIcons().add(new javafx.scene.image.Image(iconStream));
        }
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        // Force kill all background threads (timers, scrapers, web engine)
        // This ensures the JVM process terminates completely and releases file locks
        System.exit(0);
    }
}