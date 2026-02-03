package com.github.konradcz2001.updatetracker.ui;

import com.github.konradcz2001.updatetracker.TrackedProgram;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.Comparator;
import java.util.ResourceBundle;

/**
 * Manages the TableView displaying tracked programs.
 * Handles sorting, row styling (highlighting updates), and button state binding based on selection.
 */
public class ProgramTableManager {

    private static final PseudoClass OUTDATED_PSEUDO_CLASS = PseudoClass.getPseudoClass("outdated");

    private final TableView<TrackedProgram> programTable;
    private final ObservableList<TrackedProgram> programList;
    private final ResourceBundle resources;

    // Buttons controlled by table selection
    private final Button btnEditName;
    private final Button btnDelete;
    private final Button btnConfigure;
    private final Button btnDownload;

    public ProgramTableManager(
            TableView<TrackedProgram> programTable,
            ObservableList<TrackedProgram> programList,
            ResourceBundle resources,
            Button btnEditName,
            Button btnDelete,
            Button btnConfigure,
            Button btnDownload
    ) {
        this.programTable = programTable;
        this.programList = programList;
        this.resources = resources;
        this.btnEditName = btnEditName;
        this.btnDelete = btnDelete;
        this.btnConfigure = btnConfigure;
        this.btnDownload = btnDownload;
    }

    public void initializeTable(
            TableColumn<TrackedProgram, String> colName,
            TableColumn<TrackedProgram, String> colLastVersion,
            TableColumn<TrackedProgram, String> colDateOld,
            TableColumn<TrackedProgram, String> colDateNew,
            TableColumn<TrackedProgram, String> colCurrentVersion
    ) {
        // Data bindings
        colName.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        colLastVersion.setCellValueFactory(cellData -> cellData.getValue().lastDownloadedVersionProperty());
        colDateOld.setCellValueFactory(cellData -> cellData.getValue().dateFoundOldProperty());
        colDateNew.setCellValueFactory(cellData -> cellData.getValue().dateFoundNewProperty());
        colCurrentVersion.setCellValueFactory(cellData -> cellData.getValue().currentVersionProperty());

        // Apply auto-scrolling (marquee) cell factory to all text columns
        makeAutoScrollable(colName);
        makeAutoScrollable(colLastVersion);
        makeAutoScrollable(colDateOld);
        makeAutoScrollable(colDateNew);
        makeAutoScrollable(colCurrentVersion);

        SortedList<TrackedProgram> sortedList = new SortedList<>(programList);
        sortedList.comparatorProperty().bind(programTable.comparatorProperty());

        programTable.setItems(sortedList);
        programTable.setPlaceholder(new Label(resources.getString("table.placeholder")));
        programTable.setRowFactory(this::createRowFactory);

        // Initial sort logic
        FXCollections.sort(programList, createProgramComparator());

        programTable.getSortOrder().addListener((javafx.collections.ListChangeListener<TableColumn<TrackedProgram, ?>>) c -> {
            if (programTable.getSortOrder().isEmpty()) {
                FXCollections.sort(programList, createProgramComparator());
            }
        });

        setupSelectionBindings();
    }

    /**
     * Configures a column to use an auto-scrolling (marquee) animation on hover.
     * If text is longer than the cell, hovering over it will scroll it back and forth.
     */
    private void makeAutoScrollable(TableColumn<TrackedProgram, String> col) {
        col.setCellFactory(column -> new TableCell<>() {
            private final Label label = new Label();
            private final ScrollPane scrollPane = new ScrollPane(label);
            private final Timeline timeline = new Timeline();
            private final Rectangle clipRect = new Rectangle();

            {
                // Setup ScrollPane to be invisible (no bars, transparent)
                scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                scrollPane.setPannable(false); // No manual dragging
                scrollPane.setFitToHeight(true);
                scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
                label.setStyle("-fx-padding: 0 5 0 0; -fx-background-color: transparent;");

                setGraphic(scrollPane);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

                // --- Fade Effect Setup ---
                // Bind clip size to scrollPane size
                clipRect.widthProperty().bind(scrollPane.widthProperty());
                clipRect.heightProperty().bind(scrollPane.heightProperty());

                // Create a gradient mask: Visible (Black) -> Transparent
                // The fade happens in the last 15% of the width
                LinearGradient mask = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                        new Stop(0.0, Color.BLACK),
                        new Stop(0.85, Color.BLACK),
                        new Stop(1.0, Color.TRANSPARENT)
                );
                clipRect.setFill(mask);
                scrollPane.setClip(clipRect);
                // -------------------------

                // Setup Hover Logic
                scrollPane.setOnMouseEntered(e -> startScrolling());
                scrollPane.setOnMouseExited(e -> stopScrolling());
            }

            private void startScrolling() {
                // Only scroll if text is wider than the viewport
                double contentWidth = label.getLayoutBounds().getWidth();
                double viewportWidth = scrollPane.getViewportBounds().getWidth();

                if (contentWidth > viewportWidth) {
                    stopScrolling(); // Ensure clean state

                    // Calculate duration based on length to keep consistent speed (e.g., 20ms per pixel)
                    double distance = contentWidth - viewportWidth;
                    double durationMillis = distance * 20;

                    // Create animation: Pause -> Scroll Right -> Pause -> Scroll Back
                    timeline.getKeyFrames().setAll(
                            new KeyFrame(Duration.ZERO, new KeyValue(scrollPane.hvalueProperty(), 0)),
                            new KeyFrame(Duration.millis(500), new KeyValue(scrollPane.hvalueProperty(), 0)), // Pause at start
                            new KeyFrame(Duration.millis(500 + durationMillis), new KeyValue(scrollPane.hvalueProperty(), 1.0)), // Scroll to end
                            new KeyFrame(Duration.millis(1500 + durationMillis), new KeyValue(scrollPane.hvalueProperty(), 1.0)) // Pause at end
                    );

                    timeline.setAutoReverse(true);
                    timeline.setCycleCount(Timeline.INDEFINITE);
                    timeline.play();
                }
            }

            private void stopScrolling() {
                timeline.stop();
                scrollPane.setHvalue(0); // Reset to start
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    label.setText(item);
                    // Ensure text color matches row state (selected/unselected)
                    label.textFillProperty().bind(textFillProperty());
                    setGraphic(scrollPane);
                }
            }
        });
    }

    public void refreshSort() {
        FXCollections.sort(programList, createProgramComparator());
    }

    public void requestFocus() {
        programTable.requestFocus();
    }

    private void setupSelectionBindings() {
        var selectionModel = programTable.getSelectionModel();
        btnEditName.disableProperty().bind(selectionModel.selectedItemProperty().isNull());
        btnDelete.disableProperty().bind(selectionModel.selectedItemProperty().isNull());
        btnConfigure.disableProperty().bind(selectionModel.selectedItemProperty().isNull());

        // Update button is now available whenever a program is selected
        btnDownload.disableProperty().bind(selectionModel.selectedItemProperty().isNull());
    }

    /**
     * Custom comparator that prioritizes programs with available updates (Outdated > Up-to-date),
     * then sorts alphabetically by name.
     */
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
            // Allow deselecting by clicking on the selected row again
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

                // Apply 'outdated' CSS pseudo-class for visual highlighting
                row.pseudoClassStateChanged(OUTDATED_PSEUDO_CLASS, isOutdated);
            }
        }
    }
}