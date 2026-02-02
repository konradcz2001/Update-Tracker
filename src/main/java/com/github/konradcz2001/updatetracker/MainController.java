package com.github.konradcz2001.updatetracker;

import com.github.konradcz2001.updatetracker.service.*;
import com.github.konradcz2001.updatetracker.ui.BrowserManager;
import com.github.konradcz2001.updatetracker.ui.ProgramTableManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import org.kordamp.ikonli.javafx.FontIcon;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static com.github.konradcz2001.updatetracker.ui.DialogUtils.styleDialog;

/**
 * Main Controller class handling UI interactions.
 * Connects the FXML views with backend services (Scan, Download, Storage).
 */
public class MainController implements Initializable {

    // --- Dependencies ---
    private final StorageService storageService = new StorageService();
    private final ScraperService scraperService = new ScraperService();
    private final DownloadService downloadService = new DownloadService();
    private final ConfigService configService = new ConfigService();
    private ResourceBundle resources;
    private ScanService scanService;
    private BrowserManager browserManager;
    private ProgramTableManager tableManager;

    // --- UI Elements ---
    @FXML private BorderPane dashboardView;
    @FXML private TableView<TrackedProgram> programTable;
    @FXML private TableColumn<TrackedProgram, String> colName;
    @FXML private TableColumn<TrackedProgram, String> colLastVersion;
    @FXML private TableColumn<TrackedProgram, String> colDateOld;
    @FXML private TableColumn<TrackedProgram, String> colDateNew;
    @FXML private TableColumn<TrackedProgram, String> colCurrentVersion;
    @FXML private javafx.scene.layout.HBox languageContainer;

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
    @FXML private Button btnThemeToggle;
    @FXML private FontIcon themeIcon;

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
        scanService = new ScanService(scraperService, resources, configService);

        browserManager = new BrowserManager(
                webView,
                urlField,
                selectElementBtn,
                selectDownloadBtn,
                instructionLabel,
                (Void) -> storageService.saveData(programList),
                resources,
                configService
        );

        // Initialize Table Manager
        tableManager = new ProgramTableManager(
                programTable,
                programList,
                resources,
                btnEditName,
                btnDelete,
                btnConfigure,
                btnDownload
        );

        tableManager.initializeTable(colName, colLastVersion, colDateOld, colDateNew, colCurrentVersion);

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
        Platform.runLater(() -> tableManager.requestFocus());

        updateLanguageStyles();
        boolean isDark = configService.getConfig().isDarkMode();
        updateThemeIcon(isDark);
    }

    private void loadData() {
        List<TrackedProgram> loaded = storageService.loadData();
        if (loaded != null) {
            programList.setAll(loaded);
            tableManager.refreshSort();
        }
    }

    @FXML
    private void onScanUpdatesClick() {
        Dialog<ButtonType> progressDialog = new Dialog<>();
        styleDialog(progressDialog, configService, resources);
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
                    tableManager.refreshSort();
                    progressDialog.setResult(ButtonType.CANCEL);
                    progressDialog.close();
                    tableManager.requestFocus();
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
        tableManager.requestFocus();
    }

    /**
     * Resolves the download URL via a headless browser interaction (simulating a click)
     * or by resolving a direct link.
     */
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

                    // Wait for page to be fully interactive
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

            // Script wrapped in an IIFE to simulate a click or extract the href
            String script =
                    "(function() { " +
                            "  var el = document.querySelector('" + selector.replace("'", "\\'") + "');" +
                            "  if(el) { " +
                            "    if(el.tagName === 'A' && el.href) return el.href; " +
                            "    el.click(); return 'CLICKED'; " +
                            "  } " +
                            "  return ''; " +
                            "})()";

            Object result = browserManager.getEngine().executeScript(script);
            String resultStr = (result != null) ? result.toString() : "";

            if ("CLICKED".equals(resultStr)) {
                System.out.println("Button clicked via JS simulation.");
            } else if (!resultStr.isEmpty()) {
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

            File userHome = new File(System.getProperty("user.home"));
            File downloadsDir = new File(userHome, "Downloads");

            if (downloadsDir.exists() && downloadsDir.isDirectory()) {
                fileChooser.setInitialDirectory(downloadsDir);
            } else {
                fileChooser.setInitialDirectory(userHome);
            }

            String proposedName = downloadService.suggestFilename(urlString, programName);
            fileChooser.setInitialFileName(proposedName);

            java.io.File destFile = fileChooser.showSaveDialog(dashboardView.getScene().getWindow());

            if (destFile != null) {
                Task<Void> downloadTask = downloadService.createDownloadTask(urlString, destFile, resources);

                downloadTask.setOnSucceeded(e -> {
                    Alert info = new Alert(Alert.AlertType.INFORMATION, resources.getString("dialog.download.success"));
                    styleDialog(info, configService, resources);
                    info.setHeaderText(null);
                    info.show();

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
            styleDialog(alert, configService, resources);
            alert.setTitle(resources.getString("dialog.download.fail.title"));
            alert.setHeaderText(resources.getString("dialog.download.fail.header"));
            alert.setContentText(resources.getString("dialog.download.fail.content"));

            alert.showAndWait();
        });
        tableManager.requestFocus();
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
        styleDialog(dialog, configService, resources);
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
        styleDialog(dialog, configService, resources);
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
        tableManager.requestFocus();
    }

    private boolean isNameDuplicate(String name) {
        return programList.stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(name));
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        styleDialog(alert, configService, resources);
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
        tableManager.requestFocus();
    }

    @FXML
    private void onEditSourceClick() {
        TrackedProgram selected = programTable.getSelectionModel().getSelectedItem();
        if (selected != null) switchToEditor(selected);
        tableManager.requestFocus();
    }

    @FXML
    private void onBackToDashboard() {
        browserManager.resetModes();
        switchToDashboard();
        tableManager.requestFocus();
    }

    private void switchToEditor(TrackedProgram program) {
        this.currentlyEditingProgram = program;
        editorProgramNameLabel.setText(program.getName());

        if (downloadUrlField != null) {
            downloadUrlField.setText(program.getDownloadUrl() != null ? program.getDownloadUrl() : "");
        }

        browserManager.loadProgram(program);
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
        styleDialog(alert, configService, resources);
        alert.setTitle(resources.getString("dialog.about.title"));

        alert.setHeaderText(String.format(resources.getString("dialog.about.header"), appVersion));

        String javaVersion = System.getProperty("java.version");
        String javafxVersion = System.getProperty("javafx.version");

        String content = String.format(resources.getString("dialog.about.content"), javaVersion, javafxVersion);

        alert.setContentText(content);
        alert.showAndWait();

        tableManager.requestFocus();
    }

    // --- Language Support ---
    @FXML private void onLanguagePlClick() { setLanguage("pl"); }
    @FXML private void onLanguageEnClick() { setLanguage("en"); }

    private void setLanguage(String langCode) {
        if (configService.getConfig().getLanguage().equals(langCode)) return;
        configService.getConfig().setLanguage(langCode);
        configService.saveConfig();
        updateLanguageStyles();
        reloadUI();
    }

    private void reloadUI() {
        try {
            Stage stage = (Stage) dashboardView.getScene().getWindow();
            Locale locale = configService.getConfig().getLocale();

            ResourceBundle bundle = ResourceBundle.getBundle("com.github.konradcz2001.updatetracker.messages", locale);

            FXMLLoader loader = new FXMLLoader(UpdateTrackerApp.class.getResource("main-view.fxml"), bundle);
            Scene scene = new Scene(loader.load(), 1100, 650);

            if (configService.getConfig().isDarkMode()) {
                scene.getRoot().getStyleClass().add("dark-mode");
            }

            stage.setScene(scene);
            stage.setTitle(bundle.getString("app.title"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateLanguageStyles() {
        String currentLang = configService.getConfig().getLanguage();

        if (languageContainer == null) return;

        for (javafx.scene.Node node : languageContainer.getChildren()) {
            if (node instanceof Button button) {
                Object userData = button.getUserData();

                button.getStyleClass().remove("active");

                if (userData != null && userData.toString().equals(currentLang)) {
                    button.getStyleClass().add("active");
                }
            }
        }
    }

    @FXML
    private void onToggleThemeClick() {
        boolean isDark = configService.getConfig().isDarkMode();
        boolean newMode = !isDark;

        configService.getConfig().setDarkMode(newMode);
        configService.saveConfig();

        applyThemeMode(newMode);
        updateThemeIcon(newMode);
    }

    private void applyThemeMode(boolean isDark) {
        Scene scene = dashboardView.getScene();
        if (scene != null) {
            if (isDark) {
                if (!scene.getRoot().getStyleClass().contains("dark-mode")) {
                    scene.getRoot().getStyleClass().add("dark-mode");
                }
            } else {
                scene.getRoot().getStyleClass().remove("dark-mode");
            }
        }
    }

    private void updateThemeIcon(boolean isDark) {
        if (isDark) {
            themeIcon.setIconLiteral("mdmz-wb_sunny");
        } else {
            themeIcon.setIconLiteral("mdal-brightness_3");
        }
    }
}