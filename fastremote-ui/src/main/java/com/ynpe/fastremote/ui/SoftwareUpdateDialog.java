// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.update.SoftwareRelease;
import java.net.URI;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Version notifications with an explicit user action to visit the official download page. */
public final class SoftwareUpdateDialog {
    public enum Choice { SKIP, LATER, OPEN_WEBSITE }
    static final double CHECKING_INITIAL_WIDTH = 400.0;
    static final double CHECKING_MINIMUM_WIDTH = 400.0;

    private SoftwareUpdateDialog() { }

    public static Dialog<Void> showChecking(Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(text("update.title"));
        dialog.setHeaderText(text("update.checking"));
        DialogSpacing.prepare(dialog);
        configureCheckingGeometry(dialog);
        ProgressBar progress = new ProgressBar();
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progress.setMaxWidth(Double.MAX_VALUE);
        dialog.getDialogPane().setContent(progress);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        dialog.show();
        return dialog;
    }

    public static Choice showAvailable(Window owner, String currentVersion, SoftwareRelease release, URI downloadPage) {
        return availableDialog(owner, currentVersion, release, downloadPage).showAndWait().orElse(Choice.LATER);
    }

    static Dialog<Choice> availableDialog(Window owner, String currentVersion, SoftwareRelease release, URI downloadPage) {
        Objects.requireNonNull(downloadPage, "downloadPage");
        Objects.requireNonNull(release, "release");
        Dialog<Choice> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(text("update.title"));
        dialog.setHeaderText(null);
        dialog.setResizable(true);
        DialogSpacing.prepare(dialog);
        DialogSpacing.configureResizableWindowGeometry(dialog, 760.0, 520.0, 620.0, 400.0);

        StackPane emblem = new StackPane(new LucideIconView(LucideIconView.Icon.PACKAGE));
        emblem.getStyleClass().add("software-update-emblem");
        Label heading = new Label(text("update.available.heading"));
        heading.getStyleClass().add("software-update-heading");
        Label summary = new Label(java.text.MessageFormat.format(text("update.available.summary"),
                release.version(), currentVersion));
        summary.setWrapText(true);
        VBox introduction = new VBox(5.0, heading, summary);
        HBox hero = new HBox(14.0, emblem, introduction);
        hero.setAlignment(Pos.CENTER_LEFT);

        Label notesTitle = new Label(text("update.releaseNotes"));
        notesTitle.getStyleClass().add("software-update-section-title");
        VBox noteLines = new VBox(6.0);
        noteLines.getStyleClass().add("software-update-release-notes");
        for (String line : releaseNoteLines(release.releaseNotes())) {
            Label noteLine = new Label(line.isEmpty() ? " " : line);
            noteLine.setWrapText(true);
            noteLine.setMaxWidth(Double.MAX_VALUE);
            noteLine.getStyleClass().add(line.isEmpty()
                    ? "software-update-release-note-gap" : "software-update-release-note-line");
            noteLines.getChildren().add(noteLine);
        }
        ScrollPane notes = new ScrollPane(noteLines);
        notes.setFitToWidth(true);
        notes.setPannable(true);
        notes.setMinSize(0.0, 180.0);
        notes.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(notes, Priority.ALWAYS);
        Label packageInfo = new Label(java.text.MessageFormat.format(text("update.packageInfo"),
                release.platform(), release.architecture(), humanBytes(release.fileSize())));
        packageInfo.getStyleClass().add("secondary-label");
        VBox content = new VBox(10.0, hero, notesTitle, notes, packageInfo);
        Label instruction = new Label(text("update.website.detail"));
        instruction.setWrapText(true);
        Hyperlink link = new Hyperlink(downloadPage.toString());
        link.setOnAction(event -> {
            dialog.setResult(Choice.OPEN_WEBSITE);
            dialog.close();
        });
        content.getChildren().addAll(instruction, link);
        content.setPadding(new Insets(6.0));
        content.setPrefSize(720.0, 430.0);
        dialog.getDialogPane().setContent(content);

        ButtonType skip = new ButtonType(text("update.skip"), ButtonBar.ButtonData.LEFT);
        ButtonType later = new ButtonType(text("update.later"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType visitWebsite = new ButtonType(text("update.website.open"),
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(skip, later, visitWebsite);
        dialog.setResultConverter(button -> button == skip ? Choice.SKIP
                : button == visitWebsite ? Choice.OPEN_WEBSITE : Choice.LATER);
        return dialog;
    }

    static void configureCheckingGeometry(javafx.scene.control.Dialog<?> dialog) {
        Objects.requireNonNull(dialog, "dialog");
        DialogSpacing.configureInitialWindowWidth(
                dialog, CHECKING_INITIAL_WIDTH, CHECKING_MINIMUM_WIDTH);
    }

    static List<String> releaseNoteLines(String releaseNotes) {
        String normalized = Objects.requireNonNullElse(releaseNotes, "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        if (normalized.isEmpty()) return List.of(text("update.releaseNotes.empty"));
        return Arrays.asList(normalized.split("\n", -1));
    }

    public static void showNoUpdate(Window owner, String currentVersion) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle(text("update.title"));
        alert.setHeaderText(text("update.current"));
        alert.setContentText(java.text.MessageFormat.format(text("update.current.detail"), currentVersion));
        alert.showAndWait();
    }

    public static void showOfflineDisabled(Window owner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle(text("update.title"));
        alert.setHeaderText(text("update.offline.heading"));
        alert.setContentText(text("update.offline.detail"));
        alert.showAndWait();
    }

    public static void showError(Window owner) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle(text("update.title"));
        alert.setHeaderText(text("update.error.heading"));
        alert.setContentText(text("update.error.detail"));
        alert.showAndWait();
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        int unit = -1;
        do { value /= 1024.0; unit++; } while (value >= 1024.0 && unit < units.length - 1);
        return NumberFormat.getNumberInstance().format(Math.round(value * 10.0) / 10.0) + ' ' + units[unit];
    }

    private static String text(String key) { return com.ynpe.fastremote.i18n.Texts.text(key); }

}
