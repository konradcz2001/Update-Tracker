package com.github.konradcz2001.updatetracker.ui;

import com.github.konradcz2001.updatetracker.TrackedProgram;
import com.github.konradcz2001.updatetracker.util.VersionRegexUtils;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import java.util.Optional;
import java.util.function.Consumer;

public class BrowserManager {

    private final WebView webView;
    private final WebEngine engine;
    private final TextField urlField;
    private final Button selectElementBtn;
    private final Button selectDownloadBtn;
    private final Label instructionLabel;
    private final Consumer<Void> onSaveCallback;

    private boolean isVersionSelectionMode = false;
    private boolean isDownloadSelectionMode = false;
    private TrackedProgram currentProgram;

    // Strong reference to bridge to prevent GC
    private final JavaBridge bridge = new JavaBridge();

    public BrowserManager(WebView webView, TextField urlField, Button selectElementBtn, Button selectDownloadBtn, Label instructionLabel, Consumer<Void> onSaveCallback) {
        this.webView = webView;
        this.engine = webView.getEngine();
        this.urlField = urlField;
        this.selectElementBtn = selectElementBtn;
        this.selectDownloadBtn = selectDownloadBtn;
        this.instructionLabel = instructionLabel;
        this.onSaveCallback = onSaveCallback;

        initialize();
    }

    private void initialize() {
        engine.setCreatePopupHandler(null);
        engine.getHistory().setMaxSize(10);
        engine.setUserStyleSheetLocation("data:text/css," +
                "img, video, canvas, svg, object, iframe, .ads, .ad {" +
                "   display: none !important;" +
                "   visibility: hidden !important;" +
                "}");

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                urlField.setText(engine.getLocation());

                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("javaApp", bridge);

                if (isVersionSelectionMode || isDownloadSelectionMode) {
                    injectSelectorScript();
                }
            }
        });

        engine.setOnAlert(event -> System.out.println("JS Alert: " + event.getData()));
    }

    public void loadProgram(TrackedProgram program) {
        this.currentProgram = program;
        String url = program.getUrl();
        urlField.setText(url);

        if (url == null || url.trim().isEmpty()) {
            engine.loadContent("");
            selectElementBtn.setDisable(true);
            selectDownloadBtn.setDisable(true);
        } else {
            if (!url.startsWith("http")) url = "https://" + url;
            engine.load(url);
            selectElementBtn.setDisable(false);
            selectDownloadBtn.setDisable(false);
        }
    }

    public void loadUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            if (!url.startsWith("http")) url = "https://" + url;
            if (isVersionSelectionMode) toggleVersionSelectionMode();
            engine.load(url);
            selectElementBtn.setDisable(false);
            selectDownloadBtn.setDisable(false);
        }
    }

    public void goBack() {
        WebHistory history = engine.getHistory();
        if (history.getCurrentIndex() > 0) history.go(-1);
    }

    public void goForward() {
        WebHistory history = engine.getHistory();
        if (history.getCurrentIndex() < history.getEntries().size() - 1) history.go(1);
    }

    public void reload() {
        engine.reload();
    }

    public void toggleVersionSelectionMode() {
        isVersionSelectionMode = !isVersionSelectionMode;
        if (isVersionSelectionMode) {
            isDownloadSelectionMode = false;
            selectElementBtn.setText("Exit Version Selection");
            selectDownloadBtn.setDisable(true);
            instructionLabel.setText("Hold CTRL and click the version number.");
            instructionLabel.setStyle("-fx-text-fill: #000000; -fx-font-weight: bold;");
            injectSelectorScript();
        } else {
            selectElementBtn.setText("Select Version Element");
            selectDownloadBtn.setDisable(false);
            instructionLabel.setText("Navigate to page, then click below:");
            instructionLabel.setStyle("-fx-text-fill: #666666;");
        }
    }

    public void toggleDownloadSelectionMode() {
        isDownloadSelectionMode = !isDownloadSelectionMode;
        if (isDownloadSelectionMode) {
            isVersionSelectionMode = false;
            selectDownloadBtn.setText("Exit Link Selection");
            selectElementBtn.setDisable(true);
            instructionLabel.setText("Hold CTRL and click the download link.");
            instructionLabel.setStyle("-fx-text-fill: #000000; -fx-font-weight: bold;");
            injectSelectorScript();
        } else {
            selectDownloadBtn.setText("Select Download Link");
            selectElementBtn.setDisable(false);
            instructionLabel.setText("Navigate to page, then click below:");
            instructionLabel.setStyle("-fx-text-fill: #666666;");
        }
    }

    public void resetModes() {
        if (isVersionSelectionMode) toggleVersionSelectionMode();
        if (isDownloadSelectionMode) toggleDownloadSelectionMode();
    }

    public WebEngine getEngine() {
        return engine;
    }

    private void injectSelectorScript() {
        String script = """
        (function() {
            if (!document.getElementById('tracker-style')) {
                var style = document.createElement('style');
                style.id = 'tracker-style';
                style.innerHTML = '.tracker-highlight { outline: 3px solid red !important; cursor: crosshair !important; box-shadow: 0 0 5px rgba(255,0,0,0.5); }';
                document.head.appendChild(style);
            }

            function clearHighlights() {
                var highlighted = document.querySelectorAll('.tracker-highlight');
                for (var i = 0; i < highlighted.length; i++) {
                    highlighted[i].classList.remove('tracker-highlight');
                }
            }

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
                        // Filter out 'tracker-highlight' so it doesn't get saved in the selector
                        var validClasses = className.split(/\\s+/).filter(function(c) {
                            return c.length > 2 &&
                                   !c.startsWith('_') &&
                                   !c.startsWith('rs-') &&
                                   c !== 'tracker-highlight';
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
                    clearHighlights();
                    return;
                }
        
                clearHighlights();
                e.target.classList.add('tracker-highlight');
                e.stopPropagation();
            }, true);
        
            document.addEventListener('keyup', function(e) {
                if (e.key === 'Control') clearHighlights();
            });

            document.addEventListener('click', function(e) {
                if (!e.ctrlKey) return;
                e.preventDefault();
                e.stopPropagation();
        
                var target = e.target;
                if(!target) return false;

                var cssSelector = getCssPath(target);
                if (cssSelector) {
                    cssSelector = cssSelector.replace(/div\\./g, '.');
                }
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

    public class JavaBridge {
        public void onElementSelected(String cssSelector, String textContent) {
            Platform.runLater(() -> {
                if (isDownloadSelectionMode && currentProgram != null) {

                    String currentPageUrl = engine.getLocation();

                    Dialog<String> dialog = new Dialog<>();
                    dialog.setTitle("Download Configuration");
                    dialog.setHeaderText("Download Element Selected");

                    ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
                    dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

                    VBox content = new VBox(10);
                    Label label = new Label("Please confirm the page URL where this element is located.\n" +
                            "If the page URL changes with the version, use {version} placeholder.");

                    TextField urlField = new TextField(currentPageUrl);
                    urlField.setPromptText("https://example.com/v{version}/downloads");
                    urlField.setPrefWidth(450);

                    content.getChildren().addAll(label, urlField);
                    dialog.getDialogPane().setContent(content);
                    Platform.runLater(urlField::requestFocus);

                    dialog.setResultConverter(btn -> btn == saveButtonType ? urlField.getText() : null);
                    Optional<String> result = dialog.showAndWait();

                    result.ifPresent(inputPageUrl -> {
                        String finalPageUrl = inputPageUrl.trim();

                        currentProgram.setDownloadSelector(cssSelector);

                        if (!finalPageUrl.isEmpty()) {
                            currentProgram.setDownloadUrl(finalPageUrl);
                        } else {
                            currentProgram.setDownloadUrl(null);
                        }

                        onSaveCallback.accept(null);

                        Alert info = new Alert(Alert.AlertType.INFORMATION, "Configuration saved!\nPage: " + (finalPageUrl.isEmpty() ? "(Default)" : finalPageUrl));
                        info.setHeaderText(null);
                        info.showAndWait();

                        toggleDownloadSelectionMode();
                    });

                } else if (currentProgram != null) {
                    String safeText = (textContent != null) ? textContent.trim() : "";
                    Dialog<String> dialog = new Dialog<>();
                    dialog.setTitle("Detected Version");
                    dialog.setHeaderText("Confirm Version Number");

                    ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
                    dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

                    VBox content = new VBox(10);
                    Label topLabel = new Label("Found text:");
                    TextArea textArea = new TextArea(safeText);
                    textArea.setWrapText(true);
                    textArea.setPrefRowCount(5);
                    textArea.setPrefWidth(400);
                    Label bottomLabel = new Label("Keep ONLY the version number");

                    content.getChildren().addAll(topLabel, textArea, bottomLabel);
                    dialog.getDialogPane().setContent(content);

                    Platform.runLater(textArea::requestFocus);

                    dialog.setResultConverter(dialogButton -> {
                        if (dialogButton == okButtonType) return textArea.getText();
                        return null;
                    });

                    Optional<String> result = dialog.showAndWait();

                    result.ifPresent(cleanVersion -> {
                        cleanVersion = cleanVersion.trim();
                        String regex = VersionRegexUtils.createRegexFromSelection(safeText, cleanVersion);

                        currentProgram.setCssSelector(cssSelector);
                        currentProgram.setVersionRegex(regex);
                        currentProgram.setCurrentVersion(cleanVersion);
                        currentProgram.setLastDownloadedVersion(cleanVersion);
                        currentProgram.setUrl(engine.getLocation());

                        onSaveCallback.accept(null);

                        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Version saved successfully!");
                        alert.setHeaderText(null);
                        alert.showAndWait();
                        toggleVersionSelectionMode();
                    });
                }
            });
        }
    }
}