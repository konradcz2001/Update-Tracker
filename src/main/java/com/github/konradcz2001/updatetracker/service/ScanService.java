package com.github.konradcz2001.updatetracker.service;

import com.github.konradcz2001.updatetracker.TrackedProgram;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class ScanService {

    private final ScraperService scraperService;
    private final ResourceBundle resources;

    public ScanService(ScraperService scraperService, ResourceBundle resources) {
        this.scraperService = scraperService;
        this.resources = resources;
    }

    public Task<Void> createScanTask(List<TrackedProgram> programList, Label statusLabel, Runnable onUpdateCallback, Runnable onScanFinishedCallback) {
        return new Task<>() {
            @Override
            protected Void call() {
                AtomicInteger updatesFound = new AtomicInteger(0);
                AtomicInteger processedCount = new AtomicInteger(0);
                List<TrackedProgram> failedPrograms = Collections.synchronizedList(new ArrayList<>());
                int total = programList.size();

                ExecutorService executor = Executors.newFixedThreadPool(6);
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (TrackedProgram program : programList) {
                    if (isCancelled()) break;

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        if (isCancelled()) return;

                        boolean success = checkProgramUpdate(program, onUpdateCallback);

                        if (success) {
                            String curr = program.getCurrentVersion();
                            String last = program.getLastDownloadedVersion();
                            if (!curr.equals(last) && !curr.equals("N/A")) {
                                updatesFound.incrementAndGet();
                            }
                        } else {
                            failedPrograms.add(program);
                        }

                        int current = processedCount.incrementAndGet();
                        updateProgress(current, total);
                        Platform.runLater(() -> statusLabel.setText(String.format(resources.getString("dialog.scan.progress"), current, total, program.getName())));
                    }, executor);

                    futures.add(future);
                }

                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } catch (Exception e) {
                    if (!isCancelled()) e.printStackTrace();
                } finally {
                    executor.shutdownNow();
                }

                updateProgress(total, total);
                updateMessage(resources.getString("dialog.scan.finalizing"));

                Platform.runLater(() -> {
                    if (onScanFinishedCallback != null) onScanFinishedCallback.run();
                    showScanResults(failedPrograms, updatesFound.get());
                });
                return null;
            }
        };
    }

    private boolean checkProgramUpdate(TrackedProgram program, Runnable onUpdateCallback) {
        if (program.getUrl() == null || program.getUrl().isEmpty()) return true;

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            String fullText = scraperService.fetchText(program);

            if (fullText.isEmpty()) {
                System.out.println("Jsoup empty for " + program.getName() + ". Switching to Browser...");
                checkUpdateWithBrowser(program, future, onUpdateCallback);
            } else {
                boolean regexResult = processScrapedText(program, fullText, onUpdateCallback);
                future.complete(regexResult);
            }

        } catch (Exception e) {
            System.err.println("Jsoup error for " + program.getName() + ". Switching to Browser...");
            checkUpdateWithBrowser(program, future, onUpdateCallback);
        }

        try {
            return future.get(45, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    private void checkUpdateWithBrowser(TrackedProgram program, CompletableFuture<Boolean> future, Runnable onUpdateCallback) {
        Platform.runLater(() -> {
            WebView hiddenBrowser = new WebView();
            WebEngine webEngine = hiddenBrowser.getEngine();
            webEngine.setCreatePopupHandler(null);
            webEngine.setUserStyleSheetLocation("data:text/css," +
                    "img, video, canvas, svg, object, iframe, .ads, .ad {" +
                    "   display: none !important;" +
                    "   visibility: hidden !important;" +
                    "}");
            webEngine.getHistory().setMaxSize(0);
            System.setProperty("com.sun.webkit.useHTTP2Loader", "false");

            Timer timeoutTimer = new Timer();
            timeoutTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> {
                        if (!future.isDone()) {
                            System.out.println("Browser Timeout for " + program.getName() + ". Attempting forced extraction...");
                            webEngine.getLoadWorker().cancel();
                            extractTextFromBrowser(webEngine, program, future, onUpdateCallback);
                            hiddenBrowser.setPageFill(null);
                        }
                    });
                }
            }, 25000);

            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    timeoutTimer.cancel();
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            Platform.runLater(() -> {
                                if (!future.isDone()) {
                                    extractTextFromBrowser(webEngine, program, future, onUpdateCallback);
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

    private void extractTextFromBrowser(WebEngine webEngine, TrackedProgram program, CompletableFuture<Boolean> future, Runnable onUpdateCallback) {
        try {
            String jsScript = "var el = document.querySelector('" + program.getCssSelector() + "'); el ? el.textContent : null;";
            Object result = webEngine.executeScript(jsScript);

            if (result != null) {
                String fullText = result.toString().replace('\u00A0', ' ').trim();
                boolean regexResult = processScrapedText(program, fullText, onUpdateCallback);
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

    private boolean processScrapedText(TrackedProgram program, String fullText, Runnable onUpdateCallback) {
        if (program.getVersionRegex() != null && !program.getVersionRegex().isEmpty()) {
            try {
                String flexibleRegex = program.getVersionRegex().replace("•", ".");
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
                    Platform.runLater(() -> {
                        // Check if version actually changed to update dates logic
                        if (!versionToSave.equals(program.getCurrentVersion())) {
                            program.setDateFoundOld(program.getDateFoundNew());
                            program.setDateFoundNew(java.time.LocalDate.now().toString());
                        }

                        program.setCurrentVersion(versionToSave);
                        if (onUpdateCallback != null) onUpdateCallback.run();
                    });
                    System.out.println(">>> SUCCESS: " + program.getName() + " -> " + versionToSave);
                    return true;
                } else {
                    System.err.println("Mismatch for " + program.getName());
                    return false;
                }
            } catch (Exception e) {
                System.err.println("Regex error for " + program.getName() + ": " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    private void showScanResults(List<TrackedProgram> failedPrograms, int updatesFound) {
        if (!failedPrograms.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            styleDialog(alert);
            alert.setTitle(resources.getString("dialog.scan.error.title"));
            alert.setHeaderText(String.format(resources.getString("dialog.scan.error.header"), failedPrograms.size()));

            StringBuilder sb = new StringBuilder(resources.getString("dialog.scan.error.content") + "\n");
            synchronized (failedPrograms) {
                failedPrograms.sort((p1, p2) -> p1.getName().compareToIgnoreCase(p2.getName()));
                for (TrackedProgram p : failedPrograms) sb.append("- ").append(p.getName()).append("\n");
            }
            alert.setContentText(sb.toString());
            alert.showAndWait();
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            styleDialog(alert);
            alert.setTitle(resources.getString("dialog.scan.complete.title"));
            alert.setContentText(String.format(resources.getString("dialog.scan.complete.content"), updatesFound));
            alert.showAndWait();
        }
    }

    // --- Helper Method for Dialog Styling ---
    private void styleDialog(Dialog<?> dialog) {
        try {
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource("style.css").toExternalForm()
            );
        } catch (Exception e) {
            System.err.println("CSS Error: " + e.getMessage());
        }
    }
}