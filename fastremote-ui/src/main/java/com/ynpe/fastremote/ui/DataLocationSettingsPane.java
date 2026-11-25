// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.i18n.Localizer;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Independent startup preference; persistence is delegated to the background workflow. */
final class DataLocationSettingsPane extends VBox {
    @javafx.fxml.FXML private ComboBox<String> locationSelector;
    @javafx.fxml.FXML private Label current;
    @javafx.fxml.FXML private Label target;
    @javafx.fxml.FXML private Label status;
    @javafx.fxml.FXML private Label title;
    @javafx.fxml.FXML private Label note;
    @javafx.fxml.FXML private Button apply;
    @javafx.fxml.FXML private Button browse;
    private final Localizer localizer;
    private DataLocationSettings settings;
    private Path customDirectory;

    DataLocationSettingsPane(Localizer localizer, SettingsWorkflow workflow) {
        this.localizer = localizer;
        var loader = new javafx.fxml.FXMLLoader(DataLocationSettingsPane.class.getResource("DataLocationSettingsPane.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try { loader.load(); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        title.setText(localizer.text("dataLocation.title"));
        apply.setText(localizer.text("dataLocation.apply"));
        browse.setText(localizer.text("dataLocation.browse"));
        locationSelector.setAccessibleText(localizer.text("dataLocation.title"));
        note.setText(localizer.text("dataLocation.note"));
        locationSelector.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, value) -> updateTarget());
        browse.setOnAction(event -> {
            var chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle(localizer.text("dataLocation.custom"));
            var selected = PlatformFileChooser.showDialog(chooser, getScene() == null ? null : getScene().getWindow());
            if (selected != null) { customDirectory = selected.toPath(); updateTarget(); }
        });
        apply.setOnAction(event -> {
            Path selected = selectedDirectory();
            if (selected == null) return;
            locationSelector.setDisable(true);
            browse.setDisable(true);
            apply.setDisable(true);
            workflow.saveDataLocation(selected).whenComplete((result, failure) -> Platform.runLater(() -> {
                if (failure == null) show(result);
                else {
                    status.setText(localizer.text("dataLocation.error"));
                    locationSelector.setDisable(false);
                    browse.setDisable(false);
                    updateTarget();
                }
            }));
        });
        workflow.loadDataLocation().whenComplete((result, failure) -> Platform.runLater(() -> {
            if (failure == null) show(result);
            else status.setText(localizer.text("dataLocation.error"));
        }));
    }

    private Path selectedDirectory() {
        if (settings == null) return null;
        int index = locationSelector.getSelectionModel().getSelectedIndex();
        if (index == 0) return settings.userDirectory();
        if (index == 1 && settings.programDirectory().isPresent()) return settings.programDirectory().orElseThrow();
        return customDirectory;
    }

    private void updateTarget() {
        if (settings == null) return;
        boolean custom = locationSelector.getSelectionModel().getSelectedIndex() == (settings.programDirectory().isPresent() ? 2 : 1);
        browse.setVisible(settings.changeAllowed() && custom);
        browse.setManaged(settings.changeAllowed() && custom);
        Path selected = selectedDirectory();
        target.setText(selected == null ? localizer.text("dataLocation.custom") : localizer.text("dataLocation.target", selected));
        apply.setDisable(!settings.changeAllowed() || selected == null);
    }

    private void show(DataLocationSettings value) {
        settings = value;
        current.setText(localizer.text("dataLocation.current", value.current()));
        locationSelector.getItems().setAll(localizer.text("dataLocation.user"));
        if (value.programDirectory().isPresent()) locationSelector.getItems().add(localizer.text("dataLocation.program"));
        locationSelector.getItems().add(localizer.text("dataLocation.custom"));
        int selection;
        if (value.selectedDirectory().equals(value.userDirectory())) selection = 0;
        else if (value.programDirectory().filter(value.selectedDirectory()::equals).isPresent()) selection = 1;
        else { customDirectory = value.selectedDirectory(); selection = locationSelector.getItems().size() - 1; }
        locationSelector.getSelectionModel().select(selection);
        locationSelector.setVisible(value.changeAllowed());
        locationSelector.setManaged(value.changeAllowed());
        apply.setVisible(value.changeAllowed());
        apply.setManaged(value.changeAllowed());
        locationSelector.setDisable(!value.changeAllowed());
        browse.setDisable(!value.changeAllowed());
        updateTarget();
        status.setText(localizer.text(value.pending() ? "dataLocation.pending"
                : value.changeAllowed() ? "dataLocation.ready" : "dataLocation.fixed"));
    }
}
