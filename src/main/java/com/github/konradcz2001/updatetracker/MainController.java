package com.github.konradcz2001.updatetracker;

import com.github.konradcz2001.updatetracker.service.ScanService;
import com.github.konradcz2001.updatetracker.service.ScraperService;
import com.github.konradcz2001.updatetracker.service.StorageService;
import com.github.konradcz2001.updatetracker.ui.BrowserManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.beans.binding.Bindings;

import java.util.Comparator;
import java.util.List;

public class MainController {

    static {
        java.net.CookieHandler.setDefault(null);
        java.util.logging.Logger.getLogger("java.net.CookieManager").setLevel(java.util.logging.Level.OFF);
    }

    // --- Dependencies ---
    private final StorageService storageService = new StorageService();
    private final ScraperService scraperService = new ScraperService();
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

    @FXML
    public void initialize() {
        // Initialize services
        scanService = new ScanService(scraperService);
        browserManager = new BrowserManager(
                webView,
                urlField,
                selectElementBtn,
                selectDownloadBtn,
                (Void) -> storageService.saveData(programList) // Callback on save
        );

        setupTable();
        loadData();

        // Save data whenever the list changes
        programList.addListener((javafx.collections.ListChangeListener<TrackedProgram>) c -> storageService.saveData(programList));
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
        programTable.setPlaceholder(new Label("No programs tracked yet. Click 'Add Program'."));
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
        btnDownload.disableProperty().bind(selectionModel.selectedItemProperty().isNull());
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
        progressDialog.setTitle("Scanning Updates");
        progressDialog.setHeaderText("Checking program versions...");

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);

        Label statusLabel = new Label("Initializing parallel scan...");
        statusLabel.setPrefWidth(300);

        VBox content = new VBox(10, statusLabel, progressBar);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);
        progressDialog.getDialogPane().setContent(content);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        progressDialog.getDialogPane().getButtonTypes().add(cancelButtonType);

        Task<Void> task = scanService.createScanTask(
                programList,
                statusLabel,
                () -> programTable.refresh(), // Update Callback
                () -> {                       // Finished Callback
                    FXCollections.sort(programList, createProgramComparator());
                    progressDialog.setResult(ButtonType.CANCEL);
                    progressDialog.close();
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
    private void onDownloadUpdateClick() {
        TrackedProgram selected = programTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        if (selected.getDownloadSelector() != null && !selected.getDownloadSelector().isEmpty()) {
            resolveAndDownload(selected);
        } else if (selected.getUrl() != null && !selected.getUrl().isEmpty()) {
            openSystemBrowser(selected.getUrl());
        }
    }

    private void resolveAndDownload(TrackedProgram program) {
        String mainUrl = program.getUrl();
        if (mainUrl == null || mainUrl.isEmpty()) return;
        if (!mainUrl.startsWith("http")) mainUrl = "https://" + mainUrl;

        // Use BrowserManager's engine temporarily to resolve link
        browserManager.getEngine().load(mainUrl);
        browserManager.getEngine().getLoadWorker().stateProperty().addListener(new javafx.beans.value.ChangeListener<>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends Worker.State> obs, Worker.State oldState, Worker.State newState) {
                if (newState == Worker.State.SUCCEEDED) {
                    browserManager.getEngine().getLoadWorker().stateProperty().removeListener(this);
                    try {
                        String selector = program.getDownloadSelector();
                        String script = "var el = document.querySelector('" + selector.replace("'", "\\'") + "');" +
                                "el ? el.href : '';";
                        Object result = browserManager.getEngine().executeScript(script);
                        String dynamicLink = (result != null) ? result.toString() : "";
                        if (!dynamicLink.isEmpty()) openSystemBrowser(dynamicLink);
                        else openSystemBrowser(program.getUrl());
                    } catch (Exception e) {
                        openSystemBrowser(program.getUrl());
                    }
                } else if (newState == Worker.State.FAILED) {
                    browserManager.getEngine().getLoadWorker().stateProperty().removeListener(this);
                    openSystemBrowser(program.getUrl());
                }
            }
        });
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
        dialog.setTitle("New Program");
        dialog.setHeaderText("Add New Software to Track");
        dialog.setContentText("Program Name:");

        dialog.showAndWait().ifPresent(name -> {
            String trimmedName = name.trim();
            if (!trimmedName.isEmpty()) {
                if (isNameDuplicate(trimmedName)) {
                    showError("Name already exists", "A program with this name is already on the list.");
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
        dialog.setTitle("Edit Program Name");
        dialog.setHeaderText("Rename " + selected.getName());
        dialog.setContentText("New Name:");

        dialog.showAndWait().ifPresent(newName -> {
            String trimmedName = newName.trim();
            if (!trimmedName.isEmpty() && !trimmedName.equals(selected.getName())) {
                if (isNameDuplicate(trimmedName)) {
                    showError("Name already exists", "A program with this name is already on the list.");
                    return;
                }
                selected.setName(trimmedName);
                programTable.refresh();
                storageService.saveData(programList);
            }
        });
    }

    private boolean isNameDuplicate(String name) {
        return programList.stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(name));
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void onDeleteProgramClick() {
        TrackedProgram selected = programTable.getSelectionModel().getSelectedItem();
        if (selected != null) programList.remove(selected);
    }

    @FXML
    private void onEditSourceClick() {
        TrackedProgram selected = programTable.getSelectionModel().getSelectedItem();
        if (selected != null) switchToEditor(selected);
    }

    @FXML
    private void onBackToDashboard() {
        browserManager.resetModes();
        switchToDashboard();
    }

    // --- View Navigation ---
    private void switchToEditor(TrackedProgram program) {
        this.currentlyEditingProgram = program;
        editorProgramNameLabel.setText(program.getName());
        browserManager.loadProgram(program); // Delegate loading
        dashboardView.setVisible(false);
        editorView.setVisible(true);
    }

    private void switchToDashboard() {
        this.currentlyEditingProgram = null;
        dashboardView.setVisible(true);
        editorView.setVisible(false);
    }
}