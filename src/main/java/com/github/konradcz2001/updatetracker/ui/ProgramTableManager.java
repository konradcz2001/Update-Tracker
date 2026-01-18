package com.github.konradcz2001.updatetracker.ui;

import com.github.konradcz2001.updatetracker.TrackedProgram;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.scene.control.*;

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
        colName.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        colLastVersion.setCellValueFactory(cellData -> cellData.getValue().lastDownloadedVersionProperty());
        colDateOld.setCellValueFactory(cellData -> cellData.getValue().dateFoundOldProperty());
        colDateNew.setCellValueFactory(cellData -> cellData.getValue().dateFoundNewProperty());
        colCurrentVersion.setCellValueFactory(cellData -> cellData.getValue().currentVersionProperty());

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

        btnDownload.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            TrackedProgram p = selectionModel.getSelectedItem();
            return p == null || "N/A".equals(p.getCurrentVersion()) || p.getDownloadSelector() == null || p.getDownloadSelector().isEmpty();
        }, selectionModel.selectedItemProperty()));
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