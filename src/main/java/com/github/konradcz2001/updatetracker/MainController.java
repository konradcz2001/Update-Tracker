package com.github.konradcz2001.updatetracker;

import com.github.konradcz2001.updatetracker.service.*;
import com.github.konradcz2001.updatetracker.ui.BrowserManager;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    // --- Dependencies ---
    private final StorageService storageService = new StorageService();
    private final ScraperService scraperService = new ScraperService();
    private final DownloadService downloadService = new DownloadService();
    private final ConfigService configService = new ConfigService();
    private ResourceBundle resources;
    private ScanService scanService;
    private BrowserManager browserManager;

    // --- UI Elements ---
    @FXML private BorderPane dashboardView;
    @FXML private TableView<TrackedProgram> programTable;
    @FXML private TableColumn<TrackedProgram, String> colName;
    @FXML private TableColumn<TrackedProgram, String> colLastVersion;
    @FXML private TableColumn<TrackedProgram, String> colDateOld;
    @FXML private TableColumn<TrackedProgram, String> colDateNew;
    @FXML private TableColumn<TrackedProgram, String> colCurrentVersion;

    @FXML private BorderPane editorView;
    @FXML private TextField urlField;
    @FXML private WebView webView;
    @FXML private Label editorProgramNameLabel;
    @FXML private Button selectElementBtn;
    @FXML private Button selectDownloadBtn;
    @FXML private TextField downloadUrlField;
    @FXML private Label instructionLabel;

    @FXML private Button btnEditName;
    @FXML private Button btnDelete;
    @FXML private Button btnConfigure;
    @FXML private Button btnDownload;

    // --- Data ---
    private final ObservableList<TrackedProgram> programList = FXCollections.observableArrayList(
            program -> new javafx.beans.Observable[] {
                    program.currentVersionProperty(),
                    program.lastDownloadedVersionProperty()
            }
    );
    private TrackedProgram currentlyEditingProgram;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.resources = resources;

        // Initialize services with Resources
        scanService = new ScanService(scraperService, resources);
        browserManager = new BrowserManager(
                webView,
                urlField,
                selectElementBtn,
                selectDownloadBtn,
                instructionLabel,
                (Void) -> storageService.saveData(programList), // Callback on save
                resources
        );

        setupTable();
        loadData();

        // Save data whenever the list changes
        programList.addListener((javafx.collections.ListChangeListener<TrackedProgram>) c -> storageService.saveData(programList));

        // Save download URL when text changes
        if (downloadUrlField != null) {
            downloadUrlField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (currentlyEditingProgram != null) {
                    currentlyEditingProgram.setDownloadUrl(newVal);
                    storageService.saveData(programList);
                }
            });
        }

        // Ensure the table grabs focus immediately after startup and language switch
        Platform.runLater(() -> programTable.requestFocus());
    }

    private void setupTable() {
        colName.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        colLastVersion.setCellValueFactory(cellData -> cellData.getValue().lastDownloadedVersionProperty());
        colDateOld.setCellValueFactory(cellData -> cellData.getValue().dateFoundOldProperty());
        colDateNew.setCellValueFactory(cellData -> cellData.getValue().dateFoundNewProperty());
        colCurrentVersion.setCellValueFactory(cellData -> cellData.getValue().currentVersionProperty());

        SortedList<TrackedProgram> sortedList = new SortedList<>(programList);

        // Bind SortedList comparator to TableView comparator
        sortedList.comparatorProperty().bind(programTable.comparatorProperty());

        programTable.setItems(sortedList);
        programTable.setPlaceholder(new Label(resources.getString("table.placeholder")));
        programTable.setRowFactory(this::createRowFactory);

        FXCollections.sort(programList, createProgramComparator());

        programTable.getSortOrder().addListener((javafx.collections.ListChangeListener<TableColumn<TrackedProgram, ?>>) c -> {
            if (programTable.getSortOrder().isEmpty()) {
                FXCollections.sort(programList, createProgramComparator());
            }
        });

        // Disable buttons when no selection
        var selectionModel = programTable.getSelectionModel();
        btnEditName.disableProperty().bind(selectionModel.selectedItemProperty().isNull());
        btnDelete.disableProperty().bind(selectionModel.selectedItemProperty().isNull());
        btnConfigure.disableProperty().bind(selectionModel.selectedItemProperty().isNull());
        btnDownload.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            TrackedProgram p = selectionModel.getSelectedItem();
            return p == null || "N/A".equals(p.getCurrentVersion()) || p.getDownloadSelector() == null || p.getDownloadSelector().isEmpty();
        }, selectionModel.selectedItemProperty()));
    }

    private Comparator<TrackedProgram> createProgramComparator() {
        return (p1, p2) -> {
            boolean p1HasUpdate = !p1.getCurrentVersion().equals(p1.getLastDownloadedVersion())
                    && !p1.getCurrentVersion().equals("N/A");

            boolean p2HasUpdate = !p2.getCurrentVersion().equals(p2.getLastDownloadedVersion())
                    && !p2.getCurrentVersion().equals("N/A");

            if (p1HasUpdate && !p2HasUpdate) return -1;
            if (!p1HasUpdate && p2HasUpdate) return 1;

            return p1.getName().compareToIgnoreCase(p2.getName());
        };
    }

    private TableRow<TrackedProgram> createRowFactory(TableView<TrackedProgram> tv) {
        TableRow<TrackedProgram> row = new TableRow<>() {
            @Override
            protected void updateItem(TrackedProgram item, boolean empty) {
                super.updateItem(item, empty);
                updateRowStyle(this);
            }
        };

        row.selectedProperty().addListener((obs, wasSelected, isSelected) -> updateRowStyle(row));

        row.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
            if (!row.isEmpty() && event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                if (row.isSelected()) {
                    programTable.getSelectionModel().clearSelection();
                } else {
                    programTable.getSelectionModel().select(row.getItem());
                }
                event.consume();
            }
        });

        return row;
    }

    private void updateRowStyle(TableRow<TrackedProgram> row) {
        if (row.isEmpty() || row.getItem() == null) {
            row.setStyle("");
        } else {
            if (row.isSelected()) {
                row.setStyle("");
            } else {
                TrackedProgram item = row.getItem();
                String curr = item.getCurrentVersion();
                String last = item.getLastDownloadedVersion();
                boolean isOutdated = !curr.equals(last) && !curr.equals("N/A");
                row.setStyle(isOutdated ? "-fx-background-color: #ff8484;" : "");
            }
        }
    }

    private void loadData() {
        List<TrackedProgram> loaded = storageService.loadData();
        if (loaded != null) {
            programList.setAll(loaded);
            FXCollections.sort(programList, createProgramComparator());
        }
    }


    @FXML
    private void onScanUpdatesClick() {
        Dialog<ButtonType> progressDialog = new Dialog<>();
        progressDialog.setTitle(resources.getString("dialog.scan.title"));
        progressDialog.setHeaderText(resources.getString("dialog.scan.header"));

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);

        Label statusLabel = new Label(resources.getString("dialog.scan.status"));
        statusLabel.setPrefWidth(300);

        VBox content = new VBox(10, statusLabel, progressBar);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);
        progressDialog.getDialogPane().setContent(content);
        ButtonType cancelButtonType = new ButtonType(resources.getString("dialog.scan.btn_cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        progressDialog.getDialogPane().getButtonTypes().add(cancelButtonType);

        Task<Void> task = scanService.createScanTask(
                programList,
                statusLabel,
                () -> programTable.refresh(), // Update Callback
                () -> {                       // Finished Callback
                    FXCollections.sort(programList, createProgramComparator());
                    progressDialog.setResult(ButtonType.CANCEL);
                    progressDialog.close();
                    programTable.requestFocus();
                }
        );

        progressBar.progressProperty().bind(task.progressProperty());

        progressDialog.setOnCloseRequest(e -> {
            if (task.isRunning()) task.cancel();
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();

        progressDialog.show();
    }

    @FXML
    private void onSelectDownloadClick() {
        browserManager.toggleDownloadSelectionMode();
    }

    @FXML
    private void onSelectElementClick() {
        browserManager.toggleVersionSelectionMode();
    }

    @FXML
    private void onDownloadUpdateClick() {
        TrackedProgram selected = programTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        if (selected.getDownloadSelector() != null && !selected.getDownloadSelector().isEmpty()) {
            resolveAndDownload(selected);
        }
        else if (selected.getUrl() != null && !selected.getUrl().isEmpty()) {
            performInAppDownload(selected.getUrl(), selected.getName());
        } else {
            handleDownloadError(selected);
        }
        programTable.requestFocus();
    }

    private void resolveAndDownload(TrackedProgram program) {
        String targetPageUrl = program.getUrl();

        if (program.getDownloadUrl() != null && !program.getDownloadUrl().isEmpty()) {
            String version = program.getCurrentVersion();
            if ("N/A".equals(version)) version = "";
            targetPageUrl = downloadService.resolveDownloadUrl(program.getDownloadUrl(), version);
        }

        if (targetPageUrl == null || targetPageUrl.isEmpty()) return;
        if (!targetPageUrl.startsWith("http")) targetPageUrl = "https://" + targetPageUrl;

        System.out.println("Navigating to download page: " + targetPageUrl);

        browserManager.getEngine().load(targetPageUrl);

        browserManager.getEngine().getLoadWorker().stateProperty().addListener(new javafx.beans.value.ChangeListener<>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends Worker.State> obs, Worker.State oldState, Worker.State newState) {
                if (newState == Worker.State.SUCCEEDED) {
                    browserManager.getEngine().getLoadWorker().stateProperty().removeListener(this);

                    new java.util.Timer().schedule(new java.util.TimerTask() {
                        @Override
                        public void run() {
                            Platform.runLater(() -> executeSelectorLogic(program));
                        }
                    }, 1500);

                } else if (newState == Worker.State.FAILED) {
                    browserManager.getEngine().getLoadWorker().stateProperty().removeListener(this);
                    handleDownloadError(program);
                }
            }
        });
    }

    private void executeSelectorLogic(TrackedProgram program) {
        try {
            String selector = program.getDownloadSelector();

            // Script wrapped in an IIFE ((function(){...})()) to allow 'return' statements
            String script =
                    "(function() { " +
                            "  var el = document.querySelector('" + selector.replace("'", "\\'") + "');" +
                            "  if(el) { " +
                            "    if(el.tagName === 'A' && el.href) return el.href; " + // Return link URL
                            "    el.click(); return 'CLICKED'; " +                     // Click button
                            "  } " +
                            "  return ''; " +
                            "})()";

            Object result = browserManager.getEngine().executeScript(script);
            String resultStr = (result != null) ? result.toString() : "";

            if ("CLICKED".equals(resultStr)) {
                System.out.println("Button clicked via JS simulation.");
                // Note: If the click triggers a file download dialog natively, JavaFX might suppress it
                // unless a DownloadListener is attached to the engine (advanced topic).
                // For direct links masked as buttons, this usually works.
            } else if (!resultStr.isEmpty()) {
                // If the script returned a URL string
                performInAppDownload(resultStr, program.getName());
            } else {
                System.err.println("Selector element not found or invalid: " + selector);
                handleDownloadError(program);
            }
        } catch (Exception e) {
            e.printStackTrace();
            handleDownloadError(program);
        }
    }

    private void performInAppDownload(String urlString, String programName) {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle(String.format(resources.getString("filechooser.save.title"), programName));

            // --- Set default directory to Downloads ---
            File userHome = new File(System.getProperty("user.home"));
            File downloadsDir = new File(userHome, "Downloads");

            if (downloadsDir.exists() && downloadsDir.isDirectory()) {
                fileChooser.setInitialDirectory(downloadsDir);
            } else {
                fileChooser.setInitialDirectory(userHome);
            }
            // ------------------------------------------

            // Use the service to suggest a safe filename
            String proposedName = downloadService.suggestFilename(urlString, programName);
            fileChooser.setInitialFileName(proposedName);

            java.io.File destFile = fileChooser.showSaveDialog(dashboardView.getScene().getWindow());

            if (destFile != null) {
                // Delegate the background task creation to the service
                Task<Void> downloadTask = downloadService.createDownloadTask(urlString, destFile, resources);

                downloadTask.setOnSucceeded(e -> {
                    Alert info = new Alert(Alert.AlertType.INFORMATION, resources.getString("dialog.download.success"));
                    info.setHeaderText(null);
                    info.show();

                    // Auto-update 'Last Downloaded Version'
                    TrackedProgram p = programList.stream()
                            .filter(prog -> prog.getName().equals(programName))
                            .findFirst().orElse(null);

                    if (p != null) {
                        p.setLastDownloadedVersion(p.getCurrentVersion());
                        programTable.refresh();
                        storageService.saveData(programList);
                    }
                });

                downloadTask.setOnFailed(e -> {
                    TrackedProgram p = programList.stream()
                            .filter(prog -> prog.getName().equals(programName))
                            .findFirst().orElse(null);
                    handleDownloadError(p);
                });

                Thread t = new Thread(downloadTask);
                t.setDaemon(true);
                t.start();
            }
        } catch (Exception e) {
            TrackedProgram p = programList.stream()
                    .filter(prog -> prog.getName().equals(programName))
                    .findFirst().orElse(null);
            handleDownloadError(p);
        }
    }

    private void handleDownloadError(TrackedProgram program) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(resources.getString("dialog.download.fail.title"));
            alert.setHeaderText(resources.getString("dialog.download.fail.header"));
            alert.setContentText(resources.getString("dialog.download.fail.content"));

            alert.showAndWait();

            if (program != null) {
                switchToEditor(program);
            }
        });
        programTable.requestFocus();
    }

    private void openSystemBrowser(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception e) {
            System.err.println("Failed to open browser: " + e.getMessage());
        }
    }

    // --- Browser Navigation Delegated to BrowserManager ---
    @FXML private void onBrowserBack() { browserManager.goBack(); }
    @FXML private void onBrowserForward() { browserManager.goForward(); }
    @FXML private void onBrowserReload() { browserManager.reload(); }
    @FXML private void onGoClick() { browserManager.loadUrl(urlField.getText()); }

    // --- UI Event Handlers ---
    @FXML
    private void onAddProgramClick() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(resources.getString("dialog.add.title"));
        dialog.setHeaderText(resources.getString("dialog.add.header"));
        dialog.setContentText(resources.getString("dialog.add.content"));

        dialog.showAndWait().ifPresent(name -> {
            String trimmedName = name.trim();
            if (!trimmedName.isEmpty()) {
                if (isNameDuplicate(trimmedName)) {
                    showError(resources.getString("dialog.error.duplicate.title"),
                            resources.getString("dialog.error.duplicate.content"));
                    return;
                }
                TrackedProgram p = new TrackedProgram(trimmedName);
                programList.add(p);
                switchToEditor(p);
            }
        });
    }

    @FXML
    private void onEditNameClick() {
        TrackedProgram selected = programTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        TextInputDialog dialog = new TextInputDialog(selected.getName());
        dialog.setTitle(resources.getString("dialog.edit.title"));
        dialog.setHeaderText(String.format(resources.getString("dialog.edit.header"), selected.getName()));
        dialog.setContentText(resources.getString("dialog.edit.content"));

        dialog.showAndWait().ifPresent(newName -> {
            String trimmedName = newName.trim();
            if (!trimmedName.isEmpty() && !trimmedName.equals(selected.getName())) {
                if (isNameDuplicate(trimmedName)) {
                    showError(resources.getString("dialog.error.duplicate.title"),
                            resources.getString("dialog.error.duplicate.content"));
                    return;
                }
                selected.setName(trimmedName);
                programTable.refresh();
                storageService.saveData(programList);
            }
        });
        programTable.requestFocus();
    }

    private boolean isNameDuplicate(String name) {
        return programList.stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(name));
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(resources.getString("dialog.error.title"));
        alert.setHeaderText(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void onDeleteProgramClick() {
        TrackedProgram selected = programTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            programList.remove(selected);
            if (!programList.isEmpty()) {
                programTable.getSelectionModel().select(0);
            }
        }
        programTable.requestFocus();
    }

    @FXML
    private void onEditSourceClick() {
        TrackedProgram selected = programTable.getSelectionModel().getSelectedItem();
        if (selected != null) switchToEditor(selected);
        programTable.requestFocus();
    }

    @FXML
    private void onBackToDashboard() {
        browserManager.resetModes();
        switchToDashboard();
        programTable.requestFocus();
    }

    // --- View Navigation ---
    private void switchToEditor(TrackedProgram program) {
        this.currentlyEditingProgram = program;
        editorProgramNameLabel.setText(program.getName());

        if (downloadUrlField != null) {
            downloadUrlField.setText(program.getDownloadUrl() != null ? program.getDownloadUrl() : "");
        }

        browserManager.loadProgram(program); // Delegate loading
        dashboardView.setVisible(false);
        editorView.setVisible(true);
    }

    private void switchToDashboard() {
        this.currentlyEditingProgram = null;
        dashboardView.setVisible(true);
        editorView.setVisible(false);
    }

    @FXML
    private void onAboutClick() {
        String appVersion = "?";
        try (java.io.InputStream input = getClass().getResourceAsStream("/com/github/konradcz2001/updatetracker/app.properties")) {
            if (input != null) {
                java.util.Properties prop = new java.util.Properties();
                prop.load(input);
                appVersion = prop.getProperty("version", "?");
            }
        } catch (java.io.IOException ex) {
            ex.printStackTrace();
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(resources.getString("dialog.about.title"));

        // Use the loaded version string
        alert.setHeaderText(String.format(resources.getString("dialog.about.header"), appVersion));

        String javaVersion = System.getProperty("java.version");
        String javafxVersion = System.getProperty("javafx.version");

        String content = String.format(resources.getString("dialog.about.content"), javaVersion, javafxVersion);

        alert.setContentText(content);
        alert.showAndWait();

        programTable.requestFocus();
    }

    // --- Language Support ---
    @FXML private void onLanguagePlClick() { setLanguage("pl"); }
    @FXML private void onLanguageEnClick() { setLanguage("en"); }

    private void setLanguage(String langCode) {
        if (configService.getConfig().getLanguage().equals(langCode)) return;
        configService.getConfig().setLanguage(langCode);
        configService.saveConfig();
        reloadUI();
    }

    private void reloadUI() {
        try {
            Stage stage = (Stage) dashboardView.getScene().getWindow();
            Locale locale = configService.getConfig().getLocale();

            // Standard ResourceBundle loading
            ResourceBundle bundle = ResourceBundle.getBundle("com.github.konradcz2001.updatetracker.messages", locale);

            FXMLLoader loader = new FXMLLoader(UpdateTrackerApp.class.getResource("main-view.fxml"), bundle);
            Scene scene = new Scene(loader.load(), 900, 600);

            stage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}