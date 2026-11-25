// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.SettingsPageLayout.page;
import static com.ynpe.fastremote.ui.SettingsPageLayout.card;
import static com.ynpe.fastremote.ui.SettingsPageLayout.labeled;
import com.ynpe.fastremote.i18n.Localizer;
import com.ynpe.fastremote.domain.settings.DiagnosticLogOutput;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

final class DiagnosticSettingsPage {
    private DiagnosticSettingsPage() {}

    static final double DIAGNOSTIC_DIRECTORY_CONTROL_HEIGHT = 34.0;
    static Node diagnosticLogPage(CheckBox enabled, Node directory, Node files) {
        return page(com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.settingsTitle"),
                com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.settingsDescription"),
                card(com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.fileCard"),
                        com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.fileDescription"), enabled,
                        labeled(com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.directory"), directory)),
                files);
    }

    static HBox diagnosticLogDirectoryRow(TextField directory, Button chooser) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(chooser, "chooser");
        for (Region control : List.of(directory, chooser)) {
            control.setMinHeight(DIAGNOSTIC_DIRECTORY_CONTROL_HEIGHT);
            control.setPrefHeight(DIAGNOSTIC_DIRECTORY_CONTROL_HEIGHT);
            control.setMaxHeight(DIAGNOSTIC_DIRECTORY_CONTROL_HEIGHT);
        }
        HBox row = new HBox(6.0, directory, chooser);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFillHeight(false);
        row.setMinHeight(DIAGNOSTIC_DIRECTORY_CONTROL_HEIGHT);
        row.setPrefHeight(DIAGNOSTIC_DIRECTORY_CONTROL_HEIGHT);
        row.setMaxHeight(DIAGNOSTIC_DIRECTORY_CONTROL_HEIGHT);
        row.setMinWidth(390.0);
        row.setPrefWidth(470.0);
        row.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(directory, Priority.ALWAYS);
        return row;
    }

    static final class DiagnosticLogFilesPane {
        private final Window owner;
        private final DiagnosticLogOutput output;
        private final Localizer localizer;
        private final ListView<DiagnosticLogOutput.LogFile> files = new ListView<>();
        private final Label total = new Label();
        private final Button deleteAll = new Button();
        private final VBox node;

        DiagnosticLogFilesPane(Window owner, DiagnosticLogOutput output, Localizer localizer,
                java.util.function.BiFunction<Path, Boolean, CompletionStage<Void>> reveal) {
            this.owner = owner;
            this.output = output;
            this.localizer = localizer;
            files.setPrefHeight(150.0);
            files.setPlaceholder(new Label(localizer.text("diagnostic.log.filesEmpty")));
            files.setCellFactory(ignored -> new ListCell<>() {
                @Override protected void updateItem(DiagnosticLogOutput.LogFile file, boolean empty) {
                    super.updateItem(file, empty);
                    setText(empty || file == null ? null : file.fileName());
                }
            });
            deleteAll.setText(localizer.text("diagnostic.log.deleteAll"));
            deleteAll.setOnAction(ignored -> confirmDeleteAll());
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            files.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
            Button openFolder = new Button(localizer.text("diagnostic.log.openFolder"));
            openFolder.setOnAction(ignored -> {
                DiagnosticLogOutput.LogFile selected = files.getSelectionModel().getSelectedItems().stream()
                        .findFirst().orElse(null);
                Path target = selected == null ? output.directory() : output.directory().resolve(selected.fileName());
                openFolder.setDisable(true);
                reveal.apply(target, selected != null).whenComplete((result, error) -> Platform.runLater(() -> {
                    openFolder.setDisable(false);
                    if (error != null) {
                        Alert failure = new Alert(Alert.AlertType.ERROR);
                        failure.initOwner(owner);
                        failure.setTitle(localizer.text("diagnostic.log.revealFailed"));
                        failure.setHeaderText(null);
                        failure.setContentText(localizer.text("diagnostic.log.revealFailed"));
                        DialogSpacing.contentToButtonBar(failure);
                        failure.show();
                    }
                }));
            });
            HBox footer = new HBox(10.0, total, spacer, openFolder, deleteAll);
            footer.setAlignment(Pos.CENTER_LEFT);
            node = card(localizer.text("diagnostic.log.filesCard"),
                    localizer.text("diagnostic.log.filesDescription"), files, footer);
            refresh();
        }

        Node node() { return node; }

        void refresh() {
            deleteAll.setDisable(true);
            output.listFiles().whenComplete((snapshot, error) -> Platform.runLater(() -> {
                if (error != null) {
                    files.getItems().clear();
                    total.setText(localizer.text("diagnostic.log.filesLoadFailed"));
                    return;
                }
                files.getItems().setAll(snapshot.files());
                total.setText(localizer.text("diagnostic.log.totalSize",
                        localizer.formatBytes(snapshot.totalSize())));
                deleteAll.setDisable(snapshot.files().isEmpty());
            }));
        }

        private void confirmDeleteAll() {
            ButtonType delete = new ButtonType(localizer.text("diagnostic.log.deleteAll"),
                    ButtonBar.ButtonData.OK_DONE);
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.initOwner(owner);
            confirmation.setTitle(localizer.text("diagnostic.log.deleteConfirmTitle"));
            confirmation.setHeaderText(localizer.text("diagnostic.log.deleteConfirmHeader"));
            confirmation.setContentText(localizer.text("diagnostic.log.deleteConfirmBody"));
            confirmation.getButtonTypes().setAll(ButtonType.CANCEL, delete);
            if (confirmation.showAndWait().filter(delete::equals).isEmpty()) return;
            deleteAll.setDisable(true);
            output.deleteAllFiles().whenComplete((snapshot, error) -> Platform.runLater(() -> {
                if (error != null) {
                    Alert failure = new Alert(Alert.AlertType.ERROR);
                    failure.initOwner(owner);
                    failure.setTitle(localizer.text("diagnostic.log.deleteFailed"));
                    failure.setHeaderText(localizer.text("diagnostic.log.deleteFailed"));
                    failure.show();
                    refresh();
                    return;
                }
                files.getItems().setAll(snapshot.files());
                total.setText(localizer.text("diagnostic.log.totalSize",
                        localizer.formatBytes(snapshot.totalSize())));
                deleteAll.setDisable(snapshot.files().isEmpty());
            }));
        }
    }
}
