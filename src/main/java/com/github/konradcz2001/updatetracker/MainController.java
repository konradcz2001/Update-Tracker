package com.github.konradcz2001.updatetracker;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.transformation.SortedList;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Optional;

public class MainController {

    static {
        java.net.CookieHandler.setDefault(null);
        java.util.logging.Logger.getLogger("java.net.CookieManager").setLevel(java.util.logging.Level.OFF);
    }

    // --- UI Elements ---
    @FXML private BorderPane dashboardView;
    @FXML private TableView<TrackedProgram> programTable;
    @FXML private TableColumn<TrackedProgram, String> colName;
    @FXML private TableColumn<TrackedProgram, String> colLastVersion;
    @FXML private TableColumn<TrackedProgram, String> colDate;
    @FXML private TableColumn<TrackedProgram, String> colCurrentVersion;

    @FXML private BorderPane editorView;
    @FXML private TextField urlField;
    @FXML private WebView webView;
    @FXML private Label editorProgramNameLabel;
    @FXML private Button selectElementBtn;
    private boolean isSelectionMode = false;
    @FXML private Button selectDownloadBtn;
    private boolean isDownloadSelectionMode = false;

    // --- Data & Logic ---
    private final ObservableList<TrackedProgram> programList = FXCollections.observableArrayList(
            program -> new javafx.beans.Observable[] {
                    program.currentVersionProperty(),
                    program.lastDownloadedVersionProperty()
            }
    );
    private WebEngine engine;
    private TrackedProgram currentlyEditingProgram;
    private static final String DATA_FILE = "tracked_programs.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Strong reference to prevent GC
    private final JavaBridge bridge = new JavaBridge();

    @FXML
    public void initialize() {
        engine = webView.getEngine();

        // Optimization
        engine.setCreatePopupHandler(null); // Prevent popups to improve stability
        engine.getHistory().setMaxSize(10);
        engine.setUserStyleSheetLocation("data:text/css," +
                "img, video, canvas, svg, object, iframe, .ads, .ad {" +
                "   display: none !important;" +
                "   visibility: hidden !important;" +
                "}");

        loadData();
        programTable.refresh();

        colName.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        colLastVersion.setCellValueFactory(cellData -> cellData.getValue().lastDownloadedVersionProperty());
        colDate.setCellValueFactory(cellData -> cellData.getValue().lastCheckDateProperty());
        colCurrentVersion.setCellValueFactory(cellData -> cellData.getValue().currentVersionProperty());

        SortedList<TrackedProgram> sortedList = new SortedList<>(programList);

        // Define Comparator: Updates first, then Alphabetical by Name
        sortedList.setComparator((p1, p2) -> {
            boolean p1HasUpdate = !p1.getCurrentVersion().equals(p1.getLastDownloadedVersion())
                    && !p1.getCurrentVersion().equals("N/A");

            boolean p2HasUpdate = !p2.getCurrentVersion().equals(p2.getLastDownloadedVersion())
                    && !p2.getCurrentVersion().equals("N/A");

            // -1 means p1 comes first, 1 means p2 comes first
            if (p1HasUpdate && !p2HasUpdate) return -1;
            if (!p1HasUpdate && p2HasUpdate) return 1;

            return p1.getName().compareToIgnoreCase(p2.getName());
        });

        programTable.setItems(sortedList);
        programTable.setPlaceholder(new Label("No programs tracked yet. Click 'Add Program'."));

        programList.addListener((javafx.collections.ListChangeListener<TrackedProgram>) c -> saveData());

        // Custom RowFactory for highlighting and deselection
        programTable.setRowFactory(tv -> {
            TableRow<TrackedProgram> row = new TableRow<>() {
                @Override
                protected void updateItem(TrackedProgram item, boolean empty) {
                    super.updateItem(item, empty);

                    if (empty || item == null) {
                        setStyle("");
                    } else {
                        String curr = item.getCurrentVersion();
                        String last = item.getLastDownloadedVersion();

                        // Logic to determine if outdated (same as in sorting/scanning)
                        boolean isOutdated = !curr.equals(last)
                                && !curr.equals("N/A");

                        if (isOutdated) {
                            // Light red background for outdated items
                            setStyle("-fx-background-color: #ff8484;");
                        } else {
                            setStyle("");
                        }
                    }
                }
            };

            // Deselection logic
            row.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
                if (!row.isEmpty() && event.isPrimaryButtonDown() && event.getClickCount() == 1) {
                    if (programTable.getSelectionModel().getSelectedItem() == row.getItem()) {
                        programTable.getSelectionModel().clearSelection();
                        event.consume();
                    }
                }
            });

            return row;
        });

        selectElementBtn.setOnAction(e -> toggleSelectionMode());

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                urlField.setText(engine.getLocation());

                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("javaApp", bridge);

                if (isSelectionMode|| isDownloadSelectionMode) {
                    injectSelectorScript();
                }
            }
        });

        engine.setOnAlert(event -> System.out.println("JS Alert: " + event.getData()));
    }

    @FXML
    private void onScanUpdatesClick() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                int[] updatesFound = {0};
                List<TrackedProgram> failedPrograms = new ArrayList<>();

                for (TrackedProgram program : programList) {
                    if (isCancelled()) break;

                    boolean success = checkProgramUpdate(program);

                    if (success) {
                        String curr = program.getCurrentVersion();
                        String last = program.getLastDownloadedVersion();
                        if (!curr.equals(last) && !curr.equals("N/A")) {
                            updatesFound[0]++;
                        }
                    } else {
                        System.err.println("Critical error for program: " + program.getName());
                        failedPrograms.add(program);
                    }

                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }

                javafx.application.Platform.runLater(() -> {
                    if (!failedPrograms.isEmpty()) {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Scan Completed with Errors");
                        alert.setHeaderText("Failed to check " + failedPrograms.size() + " programs");

                        StringBuilder content = new StringBuilder("Could not check:\n");
                        for (TrackedProgram p : failedPrograms) {
                            content.append("- ").append(p.getName()).append("\n");
                        }
                        content.append("\nDo you want to fix the first one now?");
                        alert.setContentText(content.toString());

                        // Logic to show failed programs and ask to fix
                        ButtonType fixButton = new ButtonType("Fix First Failed");
                        ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
                        alert.getButtonTypes().setAll(fixButton, closeButton);

                        Optional<ButtonType> result = alert.showAndWait();
                        if (result.isPresent() && result.get() == fixButton) {
                            switchToEditor(failedPrograms.get(0));
                        }
                    } else {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Scan Completed");
                        alert.setContentText("Scanning finished. Updates found: " + updatesFound[0]);
                        alert.showAndWait();
                    }
                });
                return null;
            }
        };
        new Thread(task).start();
    }

    private boolean checkProgramUpdate(TrackedProgram program) {
        if (program.getUrl() == null || program.getUrl().isEmpty()) return true;

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(program.getUrl())
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(15000)
                    .get();

            String fullText = "";
            if (program.getCssSelector() != null && !program.getCssSelector().isEmpty()) {
                org.jsoup.select.Elements elements = doc.select(program.getCssSelector());
                if (!elements.isEmpty()) {
                    fullText = elements.first().text().replace('\u00A0', ' ').trim();
                }
            } else {
                fullText = doc.body().text().replace('\u00A0', ' ').trim();
            }

            if (fullText.isEmpty()) {
                System.out.println("Jsoup empty for " + program.getName() + ". Switching to Browser...");
                checkUpdateWithBrowser(program, future);
            } else {
                boolean regexResult = processScrapedText(program, fullText);
                future.complete(regexResult);
            }

        } catch (Exception e) {
            System.err.println("Jsoup error for " + program.getName() + ". Switching to Browser...");
            checkUpdateWithBrowser(program, future);
        }

        try {
            // Main thread waits here (max 45s)
            return future.get(45, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    // --- BROWSER FALLBACK LOGIC ---
    private void checkUpdateWithBrowser(TrackedProgram program, CompletableFuture<Boolean> future) {
        javafx.application.Platform.runLater(() -> {
            WebView hiddenBrowser = new WebView();
            // Optimization
            WebEngine webEngine = hiddenBrowser.getEngine();
            webEngine.setCreatePopupHandler(null);
            webEngine.setUserStyleSheetLocation("data:text/css," +
                    "img, video, canvas, svg, object, iframe, .ads, .ad {" +
                    "   display: none !important;" +
                    "   visibility: hidden !important;" +
                    "}");
            webEngine.getHistory().setMaxSize(0);
            System.setProperty("com.sun.webkit.useHTTP2Loader", "false");

            // Watchdog timer to prevent indefinite hanging
            Timer timeoutTimer = new Timer();
            timeoutTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    javafx.application.Platform.runLater(() -> {
                        if (!future.isDone()) {
                            System.out.println("Browser Timeout for " + program.getName() + ". Attempting forced extraction...");
                            webEngine.getLoadWorker().cancel();
                            extractTextFromBrowser(webEngine, program, future);
                            hiddenBrowser.setPageFill(null);
                        }
                    });
                }
            }, 25000); // 25 seconds timeout

            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    timeoutTimer.cancel();
                    // Wait additional time for JS rendering
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            javafx.application.Platform.runLater(() -> {
                                if (!future.isDone()) {
                                    extractTextFromBrowser(webEngine, program, future);
                                    hiddenBrowser.setPageFill(null);
                                }
                            });
                        }
                    }, 4000);
                } else if (newState == Worker.State.FAILED) {
                    timeoutTimer.cancel();
                    System.err.println("Browser failed to load: " + program.getUrl());
                    future.complete(false);
                }
            });

            webEngine.load(program.getUrl());
        });
    }

    private void extractTextFromBrowser(WebEngine webEngine, TrackedProgram program, CompletableFuture<Boolean> future) {
        try {
            // Try to get text content via JS
            String jsScript = "var el = document.querySelector('" + program.getCssSelector() + "'); el ? el.textContent : null;";
            Object result = webEngine.executeScript(jsScript);

            if (result != null) {
                String fullText = result.toString().replace('\u00A0', ' ').trim();
                System.out.println("Browser (" + program.getName() + ") found: " + fullText);

                boolean regexResult = processScrapedText(program, fullText);
                future.complete(regexResult);
            } else {
                System.err.println("Browser selector returned null for " + program.getName());
                future.complete(false);
            }
        } catch (Exception e) {
            System.err.println("Browser JS error for " + program.getName() + ": " + e.getMessage());
            future.complete(false);
        }
    }

    private boolean processScrapedText(TrackedProgram program, String fullText) {
        if (program.getVersionRegex() != null && !program.getVersionRegex().isEmpty()) {
            try {
                String flexibleRegex = program.getVersionRegex().replace("•", "."); // Handle bullets
                Pattern pattern = Pattern.compile(flexibleRegex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
                java.util.regex.Matcher matcher = pattern.matcher(fullText);

                if (matcher.find()) {
                    String finalVersion;
                    if (matcher.groupCount() >= 1) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 1; i <= matcher.groupCount(); i++) {
                            String g = matcher.group(i);
                            if (g != null && !g.trim().isEmpty()) {
                                if (sb.length() > 0) sb.append(" ");
                                sb.append(g.trim());
                            }
                        }
                        finalVersion = sb.toString().trim();
                    } else {
                        finalVersion = matcher.group(0).trim();
                    }

                    String versionToSave = finalVersion;
                    javafx.application.Platform.runLater(() -> {
                        program.setCurrentVersion(versionToSave);
                        program.setLastCheckDate(java.time.LocalDate.now().toString());
                        programTable.refresh();
                    });
                    System.out.println(">>> SUCCESS: " + program.getName() + " -> " + versionToSave);
                    return true;
                } else {
                    System.err.println("Mismatch for " + program.getName() + ": Text '" + fullText + "' does not match the regex.");
                    return false;
                }
            } catch (Exception e) {
                System.err.println("Regex error for " + program.getName() + ": " + e.getMessage());
                return false;
            }
        }
        return true;
    }


    @FXML
    private void onSelectDownloadClick() {
        isDownloadSelectionMode = !isDownloadSelectionMode;
        if (isDownloadSelectionMode) {
            isSelectionMode = false; // Disable other mode
            selectDownloadBtn.setText("Exit Link Selection");
            selectElementBtn.setDisable(true);
            injectSelectorScript();
        } else {
            selectDownloadBtn.setText("Select Download Link");
            selectElementBtn.setDisable(false);
            engine.reload();
        }
    }

    @FXML
    private void onDownloadUpdateClick() {
        TrackedProgram selected = programTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        // Check if we have a saved selector for the download button position
        if (selected.getDownloadSelector() != null && !selected.getDownloadSelector().isEmpty()) {
            resolveAndDownload(selected);
        }
        // Fallback: if no position saved, just open the main website
        else if (selected.getUrl() != null && !selected.getUrl().isEmpty()) {
            openSystemBrowser(selected.getUrl());
        }
    }

    // Helper method to resolve dynamic link using the saved CSS selector
    private void resolveAndDownload(TrackedProgram program) {
        String mainUrl = program.getUrl();
        if (mainUrl == null || mainUrl.isEmpty()) return;
        if (!mainUrl.startsWith("http")) mainUrl = "https://" + mainUrl;

        // Load the page in the existing WebView (hidden in dashboard mode)
        engine.load(mainUrl);

        // Add a one-time listener to wait for the page to load
        engine.getLoadWorker().stateProperty().addListener(new javafx.beans.value.ChangeListener<>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends Worker.State> obs, Worker.State oldState, Worker.State newState) {
                if (newState == Worker.State.SUCCEEDED) {
                    // Remove listener to prevent memory leaks and repeated triggers
                    engine.getLoadWorker().stateProperty().removeListener(this);

                    try {
                        String selector = program.getDownloadSelector();
                        // JS: Find element by selector and extract the current 'href'
                        String script = "var el = document.querySelector('" + selector.replace("'", "\\'") + "');" +
                                "el ? el.href : '';";

                        Object result = engine.executeScript(script);
                        String dynamicLink = (result != null) ? result.toString() : "";

                        if (!dynamicLink.isEmpty()) {
                            openSystemBrowser(dynamicLink);
                        } else {
                            System.err.println("Element not found via selector, opening main URL.");
                            openSystemBrowser(program.getUrl());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        openSystemBrowser(program.getUrl());
                    }
                } else if (newState == Worker.State.FAILED) {
                    engine.getLoadWorker().stateProperty().removeListener(this);
                    openSystemBrowser(program.getUrl());
                }
            }
        });
    }

    // Helper method to open URL in default system browser
    private void openSystemBrowser(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception e) {
            System.err.println("Failed to open browser: " + e.getMessage());
        }
    }

    private void saveData() {
        try {
            // Use ArrayList copy to avoid serialization issues with ObservableList
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(DATA_FILE), new ArrayList<>(programList));
        } catch (IOException e) {
            System.err.println("Failed to save data: " + e.getMessage());
        }
    }

    private void loadData() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try {
                List<TrackedProgram> loaded = objectMapper.readValue(file, new TypeReference<>() {});
                programList.setAll(loaded);
            } catch (IOException e) {
                System.err.println("Failed to load data: " + e.getMessage());
            }
        }
    }

    // --- Browser Navigation ---

    @FXML private void onBrowserBack() {
        WebHistory history = engine.getHistory();
        if (history.getCurrentIndex() > 0) history.go(-1);
    }

    @FXML private void onBrowserForward() {
        WebHistory history = engine.getHistory();
        if (history.getCurrentIndex() < history.getEntries().size() - 1) history.go(1);
    }

    @FXML private void onBrowserReload() {
        engine.reload();
    }

    // --- Core Logic ---

    private void toggleSelectionMode() {
        isSelectionMode = !isSelectionMode;
        if (isSelectionMode) {
            selectElementBtn.setText("Exit Selection Mode");
            injectSelectorScript();
        } else {
            selectElementBtn.setText("Select Version Element");
            engine.reload();
        }
    }

    private void injectSelectorScript() {
        String script = """
        (function() {
            var lastTarget = null;
            var lastOutline = '';

            function getCssPath(el) {
                if (!(el instanceof Element)) return;
                if (el.id) return '#' + el.id;

                var path = [];
                var current = el;
                
                while (current && current.nodeType === Node.ELEMENT_NODE) {
                    var selector = current.nodeName.toLowerCase();
                    if (current.id) {
                        selector = '#' + current.id;
                        path.unshift(selector);
                        break; 
                    }
                    var className = current.getAttribute("class");
                    if (className && className.trim().length > 0) {
                        var validClasses = className.split(/\\s+/).filter(function(c) {
                            return c.length > 2 && !c.startsWith('_') && !c.startsWith('rs-');
                        });
                        if (validClasses.length > 0) {
                            selector += '.' + validClasses.join('.');
                        }
                    }
                    path.unshift(selector);
                    var looseSelector = path.join(' ');
                    if (document.querySelectorAll(looseSelector).length === 1) {
                        return looseSelector;
                    }
                    current = current.parentNode;
                }
                return path.join(' ');
            }

            document.addEventListener('mouseover', function(e) {
                if (!e.ctrlKey) {
                    if (lastTarget) {
                        lastTarget.style.outline = lastOutline;
                        lastTarget = null;
                    }
                    return;
                }
                var target = e.target;
                if (target === lastTarget) return;

                if (lastTarget) lastTarget.style.outline = lastOutline;
                lastTarget = target;
                lastOutline = target.style.outline;
                target.style.outline = "3px solid red";
                e.stopPropagation();
            }, true);

            document.addEventListener('click', function(e) {
                if (!e.ctrlKey) return;
                e.preventDefault();
                e.stopPropagation();
                
                var target = e.target;
                if(!target) return false;

                var cssSelector = getCssPath(target);
                cssSelector = cssSelector.replace(/div\\./g, '.'); // clean up
                var textContent = (target.innerText || target.textContent || "").replace(/\\s+/g, ' ').trim();
                
                if(window.javaApp) {
                    window.javaApp.onElementSelected(cssSelector, textContent);
                }
                return false;
            }, true);
        })();
        """;

        try {
            engine.executeScript(script);
        } catch (Exception ex) {
            System.err.println("Script inject failed: " + ex.getMessage());
        }
    }

    // --- Inner Class: Bridge ---
    public class JavaBridge {
        public void onElementSelected(String cssSelector, String textContent) {
            javafx.application.Platform.runLater(() -> {

                // CASE 1: Selecting Download Link
                if (isDownloadSelectionMode && currentlyEditingProgram != null) {
                    currentlyEditingProgram.setDownloadSelector(cssSelector);
                    saveData(); // Important: Save immediately

                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Download link saved!");
                    alert.setHeaderText(null);
                    alert.showAndWait();

                    // Exit download selection mode using the controller's method
                    onSelectDownloadClick();

                }
                // CASE 2: Selecting Version Number (Standard Mode)
                else if (currentlyEditingProgram != null) {
                    String safeText = (textContent != null) ? textContent.trim() : "";

                    Dialog<String> dialog = new Dialog<>();
                    dialog.setTitle("Detected Version");
                    dialog.setHeaderText("Confirm Version Number");

                    ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
                    dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

                    VBox content = new VBox(10);

                    // UI Label 1: Top
                    Label topLabel = new Label("Found text:");

                    // TextArea configuration
                    TextArea textArea = new TextArea(safeText);
                    textArea.setWrapText(true);
                    textArea.setPrefRowCount(5);
                    textArea.setPrefWidth(400);

                    // UI Label 2: Bottom
                    Label bottomLabel = new Label("Keep ONLY the version number");

                    content.getChildren().addAll(topLabel, textArea, bottomLabel);
                    dialog.getDialogPane().setContent(content);

                    javafx.application.Platform.runLater(textArea::requestFocus);

                    dialog.setResultConverter(dialogButton -> {
                        if (dialogButton == okButtonType) {
                            return textArea.getText();
                        }
                        return null;
                    });

                    Optional<String> result = dialog.showAndWait();

                    result.ifPresent(cleanVersion -> {
                        cleanVersion = cleanVersion.trim();
                        String regex = createRegexFromSelection(safeText, cleanVersion);

                        // Auto-Save Data
                        currentlyEditingProgram.setCssSelector(cssSelector);
                        currentlyEditingProgram.setVersionRegex(regex);
                        currentlyEditingProgram.setCurrentVersion(cleanVersion);
                        currentlyEditingProgram.setLastDownloadedVersion(cleanVersion);
                        currentlyEditingProgram.setUrl(engine.getLocation());

                        saveData();

                        // 1. Show confirmation
                        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Version saved successfully!");
                        alert.setHeaderText(null);
                        alert.showAndWait();

                        // 2. Exit selection mode (refreshes page, removes red outlines)
                        toggleSelectionMode();
                    });
                }
            });
        }
    }

    // --- UI Event Handlers ---
    @FXML
    private void onAddProgramClick() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Program");
        dialog.setHeaderText("Add New Software to Track");
        dialog.setContentText("Program Name:");

        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                TrackedProgram p = new TrackedProgram(name);
                programList.add(p);
                switchToEditor(p);
            }
        });
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
    private void onGoClick() {
        String url = urlField.getText();
        if (url != null && !url.trim().isEmpty()) {
            if (!url.startsWith("http")) url = "https://" + url;

            if (isSelectionMode) toggleSelectionMode();

            engine.load(url);
            selectElementBtn.setDisable(false);
        }
    }

    @FXML
    private void onBackToDashboard() {
        if (isSelectionMode) toggleSelectionMode();
        switchToDashboard();
    }

    // --- View Navigation ---

    private void switchToEditor(TrackedProgram program) {
        this.currentlyEditingProgram = program;
        editorProgramNameLabel.setText(program.getName());

        String savedUrl = program.getUrl();
        urlField.setText(savedUrl);

        if (savedUrl == null || savedUrl.trim().isEmpty()) {
            engine.loadContent("");
            selectElementBtn.setDisable(true);
        } else {
            if (!savedUrl.startsWith("http")) savedUrl = "https://" + savedUrl;
            engine.load(savedUrl);
            selectElementBtn.setDisable(false);
        }

        dashboardView.setVisible(false);
        editorView.setVisible(true);
    }

    private void switchToDashboard() {
        if (isSelectionMode) toggleSelectionMode();
        if (isDownloadSelectionMode) onSelectDownloadClick();
        this.currentlyEditingProgram = null;
        dashboardView.setVisible(true);
        editorView.setVisible(false);
    }

    // --- Regex ---
    private String makeSafeRegex(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c) || " :.-()[]".indexOf(c) != -1) {
                sb.append(Pattern.quote(String.valueOf(c)));
            } else {
                // Replace special characters (bullets, nbsp, etc.) with a wildcard
                sb.append(".");
            }
        }
        return sb.toString();
    }

    private String generateSmartPrefix(String prefix) {
        int lastDigitIndex = -1;
        for (int i = prefix.length() - 1; i >= 0; i--) {
            if (Character.isDigit(prefix.charAt(i))) {
                lastDigitIndex = i;
                break;
            }
        }
        String anchor = (lastDigitIndex != -1) ? prefix.substring(lastDigitIndex + 1) : prefix;
        return ".*?" + makeSafeRegex(anchor);
    }

    private String createMultiPartRegex(String fullText, String selectedVersion) {
        String[] parts = selectedVersion.split("\\s+");
        if (parts.length < 2) return "(.*)";

        StringBuilder regexBuilder = new StringBuilder();
        String firstPart = parts[0];
        int firstIndex = fullText.indexOf(firstPart);
        if (firstIndex == -1) return "(.*)";

        String prefix = fullText.substring(0, firstIndex);
        regexBuilder.append(generateSmartPrefix(prefix));

        int currentPos = firstIndex;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            regexBuilder.append("(").append(makeSafeRegex(part)).append(")");

            if (i < parts.length - 1) {
                String nextPart = parts[i + 1];
                int nextIndex = fullText.indexOf(nextPart, currentPos + part.length());
                if (nextIndex != -1) {
                    regexBuilder.append(".*?");
                    currentPos = nextIndex;
                } else {
                    regexBuilder.append(".*?");
                }
            }
        }

        String lastPart = parts[parts.length - 1];
        int lastIndex = fullText.lastIndexOf(lastPart);
        if (lastIndex != -1) {
            String suffix = fullText.substring(lastIndex + lastPart.length());
            regexBuilder.append(suffix.isEmpty() ? "(.*)" : makeSafeRegex(suffix));
        } else {
            regexBuilder.append("(.*)");
        }
        return regexBuilder.toString();
    }

    private String createSmartSelfHealingRegex(String fullText, String selectedVersion) {
        int index = fullText.indexOf(selectedVersion);
        if (index == -1) return "(.*)";

        String prefix = fullText.substring(0, index);
        String suffix = fullText.substring(index + selectedVersion.length());

        String regexPrefix = generateSmartPrefix(prefix);
        String regexSuffix = suffix.isEmpty() ? "(.*)" : "(.*?)" + makeSafeRegex(suffix);

        String candidateRegex = regexPrefix + regexSuffix;

        if (!testRegex(fullText, candidateRegex, selectedVersion)) {
            regexPrefix = makeSafeRegex(prefix);
        }
        return regexPrefix + regexSuffix;
    }

    private boolean testRegex(String fullText, String regex, String expectedValue) {
        try {
            Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(fullText);
            if (m.find()) {
                String captured;
                if (m.groupCount() >= 1) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i <= m.groupCount(); i++) {
                        String g = m.group(i);
                        if (g != null && !g.trim().isEmpty()) {
                            if (sb.length() > 0) sb.append(" ");
                            sb.append(g.trim());
                        }
                    }
                    captured = sb.toString().trim();
                } else {
                    captured = m.group(0).trim();
                }
                return captured.equalsIgnoreCase(expectedValue.trim());
            }
        } catch (Exception e) { return false; }
        return false;
    }

    private String createRegexFromSelection(String fullText, String selectedVersion) {
        if (fullText.contains(selectedVersion)) {
            return createSmartSelfHealingRegex(fullText, selectedVersion);
        }
        return createMultiPartRegex(fullText, selectedVersion);
    }
}