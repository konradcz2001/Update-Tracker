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
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);

        stage.setTitle("Software Update Tracker");
        stage.setScene(scene);
        stage.show();
    }
}