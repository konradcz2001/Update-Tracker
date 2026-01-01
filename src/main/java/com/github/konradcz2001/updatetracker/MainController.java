package com.github.konradcz2001.updatetracker;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.util.Optional;
import java.util.regex.Pattern;

public class MainController {

    private boolean isSelectionMode = false;

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

    // --- Data & Logic ---
    private final ObservableList<TrackedProgram> programList = FXCollections.observableArrayList();
    private WebEngine engine;
    private TrackedProgram currentlyEditingProgram;

    // Strong reference to prevent GC
    private final JavaBridge bridge = new JavaBridge();

    @FXML
    public void initialize() {
        engine = webView.getEngine();

        colName.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        colLastVersion.setCellValueFactory(cellData -> cellData.getValue().lastDownloadedVersionProperty());
        colDate.setCellValueFactory(cellData -> cellData.getValue().lastCheckDateProperty());
        colCurrentVersion.setCellValueFactory(cellData -> cellData.getValue().currentVersionProperty());

        programTable.setItems(programList);
        programTable.setPlaceholder(new Label("No programs tracked yet. Click 'Add Program'."));

        selectElementBtn.setOnAction(e -> toggleSelectionMode());

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                urlField.setText(engine.getLocation());

                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("javaApp", bridge);

                if (isSelectionMode) {
                    injectSelectorScript();
                }
            }
        });

        engine.setOnAlert(event -> System.out.println("JS Alert: " + event.getData()));
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
                var style = document.createElement('style');
                style.innerHTML = '.highlight-hover { outline: 3px solid red !important; cursor: context-menu !important; }';
                document.head.appendChild(style);

                function getCssPath(el) {
                    if (!(el instanceof Element)) return;
                    var path = [];
                    while (el.nodeType === Node.ELEMENT_NODE) {
                        var selector = el.nodeName.toLowerCase();
                        if (el.id) {
                            selector += '#' + el.id;
                            path.unshift(selector);
                            break;
                        } else {
                            var sib = el, nth = 1;
                            while (sib = sib.previousElementSibling) {
                                if (sib.nodeName.toLowerCase() == selector) nth++;
                            }
                            if (nth != 1) selector += ":nth-of-type("+nth+")";
                        }
                        path.unshift(selector);
                        el = el.parentNode;
                    }
                    return path.join(" > ");
                }
                
                function getElementUnderMouse(e) {
                    var el = document.elementFromPoint(e.clientX, e.clientY);
                    var current = el;
                    for(var i=0; i<5; i++) {
                        if(!current || current === document.body) break;
                        if(current.tagName.toLowerCase() === 'a') return current;
                        current = current.parentElement;
                    }
                    return el;
                }

                document.addEventListener('mousemove', function(e) {
                    if(!window.javaApp) return;
                    
                    var target = getElementUnderMouse(e);
                    var prev = document.querySelector('.highlight-hover');
                    
                    if (prev && prev !== target) prev.classList.remove('highlight-hover');
                    if (target) target.classList.add('highlight-hover');
                }, true);

                document.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopImmediatePropagation();
                    e.stopPropagation();
                    
                    var target = getElementUnderMouse(e);
                    if(!target) return false;
                    
                    var textContent = (target.innerText || target.textContent || "").trim();
                    var cssSelector = getCssPath(target);
                    
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
            System.err.println("Failed to inject selector script: " + ex.getMessage());
        }
    }

    // --- Inner Class: Bridge ---
    public class JavaBridge {
        public void onElementSelected(String cssSelector, String textContent) {
            javafx.application.Platform.runLater(() -> {
                if (currentlyEditingProgram != null) {
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

                        toggleSelectionMode();
                        switchToDashboard();
                    });
                }
            });
        }
    }

    private String createRegexFromSelection(String fullText, String selectedVersion) {
        if (fullText.equals(selectedVersion)) return "(.*)";
        int index = fullText.indexOf(selectedVersion);
        if (index == -1) return "(.*)";
        String prefix = fullText.substring(0, index);
        String suffix = fullText.substring(index + selectedVersion.length());
        return Pattern.quote(prefix) + "(.*?)" + Pattern.quote(suffix);
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
        this.currentlyEditingProgram = null;
        dashboardView.setVisible(true);
        editorView.setVisible(false);
    }
}