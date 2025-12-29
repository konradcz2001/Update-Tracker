package com.github.konradcz2001.updatetracker;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.util.Optional;

public class MainController {

    // --- Dashboard UI ---
    @FXML private BorderPane dashboardView;
    @FXML private TableView<TrackedProgram> programTable;
    @FXML private TableColumn<TrackedProgram, String> colName;
    @FXML private TableColumn<TrackedProgram, String> colLastVersion;
    @FXML private TableColumn<TrackedProgram, String> colDate;
    @FXML private TableColumn<TrackedProgram, String> colCurrentVersion;

    // --- Editor UI ---
    @FXML private BorderPane editorView;
    @FXML private TextField urlField;
    @FXML private WebView webView;
    @FXML private Label editorProgramNameLabel;
    @FXML private Button selectElementBtn; // Will be used in next steps
    @FXML private Button saveConfigBtn;    // Will be used in next steps

    // --- Data ---
    private final ObservableList<TrackedProgram> programList = FXCollections.observableArrayList();
    private WebEngine engine;
    private TrackedProgram currentlyEditingProgram; // Holds the program currently being edited

    @FXML
    public void initialize() {
        // Initialize Web Engine
        engine = webView.getEngine();

        // 1. Bind Table Columns to TrackedProgram properties
        colName.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        colLastVersion.setCellValueFactory(cellData -> cellData.getValue().lastDownloadedVersionProperty());
        colDate.setCellValueFactory(cellData -> cellData.getValue().lastCheckDateProperty());
        colCurrentVersion.setCellValueFactory(cellData -> cellData.getValue().currentVersionProperty());

        // 2. Load data into table
        programTable.setItems(programList);

        // Setup placeholder for empty table
        programTable.setPlaceholder(new Label("No programs tracked yet. Click 'Add Program'."));
    }

    // --- Actions: Dashboard ---

    @FXML
    private void onAddProgramClick() {
        // Simple Input Dialog to get the name first
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Program");
        dialog.setHeaderText("Add New Software to Track");
        dialog.setContentText("Program Name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                TrackedProgram newProgram = new TrackedProgram(name);
                programList.add(newProgram);

                // Immediately switch to editor for this new program
                switchToEditor(newProgram);
            }
        });
    }

    @FXML
    private void onDeleteProgramClick() {
        TrackedProgram selected = programTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            programList.remove(selected);
        } else {
            showAlert("No Selection", "Please select a program to delete.");
        }
    }

    @FXML
    private void onEditSourceClick() {
        TrackedProgram selected = programTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            switchToEditor(selected);
        } else {
            showAlert("No Selection", "Please select a program to configure.");
        }
    }

    // --- Actions: Editor ---

    @FXML
    private void onGoClick() {
        String url = urlField.getText();
        if (url != null && !url.trim().isEmpty()) {
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }
            engine.load(url);
            // Enable save buttons just for demo purposes now (in reality, enable after selection)
            saveConfigBtn.setDisable(false);
            selectElementBtn.setDisable(false);
        }
    }

    @FXML
    private void onSaveConfigClick() {
        if (currentlyEditingProgram != null) {
            // Save logic
            currentlyEditingProgram.setUrl(urlField.getText());

            // NOTE: In the future, we will save the CSS Selector here too.
            // For now, let's simulate that we found a version
            String mockedVersion = "v1.0.5 (Demo)";

            currentlyEditingProgram.setCurrentVersion(mockedVersion);
            // As requested: Last downloaded becomes current when setting source
            currentlyEditingProgram.setLastDownloadedVersion(mockedVersion);

            System.out.println("Saved config for: " + currentlyEditingProgram.getName());
        }
        switchToDashboard();
    }

    @FXML
    private void onBackToDashboard() {
        switchToDashboard();
    }

    // --- Helpers ---

    private void switchToEditor(TrackedProgram program) {
        this.currentlyEditingProgram = program;
        editorProgramNameLabel.setText(program.getName());
        urlField.setText(program.getUrl()); // Load existing URL if present

        // Reset View
        if(program.getUrl() == null || program.getUrl().isEmpty()) {
            engine.loadContent(""); // Clear browser
            saveConfigBtn.setDisable(true);
        } else {
            onGoClick(); // Reload page
        }

        dashboardView.setVisible(false);
        editorView.setVisible(true);
    }

    private void switchToDashboard() {
        this.currentlyEditingProgram = null;
        dashboardView.setVisible(true);
        editorView.setVisible(false);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}