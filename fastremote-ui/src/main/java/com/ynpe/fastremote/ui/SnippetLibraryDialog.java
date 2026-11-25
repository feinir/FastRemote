// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.KeyboardShortcutSettings;
import com.ynpe.fastremote.i18n.Texts;
import java.io.IOException;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Owns only the independent window; command-library behavior is shared with the dock. */
final class SnippetLibraryDialog {
    private final Dialog<Void> dialog = new Dialog<>();
    private final SnippetLibraryController controller;

    SnippetLibraryDialog(Window owner, KeyboardShortcutSettings shortcuts, Runnable importText, Runnable exportText) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("SnippetLibraryView.fxml"),
                Texts.localizer().bundle());
        final VBox library;
        try {
            library = loader.load();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load command library", failure);
        }
        controller = loader.getController();
        controller.onDefaultCommand(this::close);
        library.setId("snippetsPage");
        library.setMinHeight(0);
        library.setMinWidth(0);
        HBox toolbar = (HBox) controller.searchField().getParent();
        toolbar.getChildren().addAll(2, java.util.List.of(
                transferButton("legacy.java.576d81bb0631b165", LucideIconView.Icon.IMPORT, importText),
                transferButton("legacy.fxml.76420433f22d1c80", LucideIconView.Icon.EXPORT, exportText)));
        controller.searchField().setMinWidth(0);
        toolbar.getChildren().remove(controller.searchField());
        controller.helpLabel().getStyleClass().removeAll("component-help", "snippet-help");
        controller.helpLabel().getStyleClass().add("host-manager-hint");
        DialogSpacing.configureWrappingLabel(controller.helpLabel());
        library.getChildren().setAll(toolbar, controller.searchField(), controller.helpLabel(), controller.tree());
        library.setSpacing(8);
        library.getStyleClass().setAll("host-manager-dialog", "catalog-controls-shell", "component-page");
        VBox.setMargin(toolbar, new Insets(0, 10, 0, 10));
        VBox.setMargin(controller.searchField(), new Insets(0, 10, 0, 10));
        VBox.setMargin(controller.helpLabel(), new Insets(0, 10, 0, 10));
        controller.tree().setMinHeight(0);
        controller.tree().setPrefHeight(400);
        VBox.setVgrow(controller.tree(), Priority.ALWAYS);
        dialog.initOwner(owner);
        dialog.setTitle(Texts.text("library.commands"));
        dialog.setHeaderText(Texts.text("legacy.java.1cbb8f00f07db977"));
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        dialog.setResultConverter(DialogResultPolicy.explicitOnly());
        DialogSpacing.contentToButtonBar(dialog);
        dialog.getDialogPane().setContent(library);
        DialogSpacing.configureResizableWindowGeometry(dialog, 480, 680, 360, 360);
        KeyboardShortcutManager manager = new KeyboardShortcutManager(
                dialog.getDialogPane().getScene(), controller::shortcut);
        manager.apply(shortcuts);
        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && !event.isConsumed()) {
                close();
                event.consume();
            }
        });
        dialog.setOnShown(event -> Platform.runLater(() -> DialogSpacing.focusAtEnd(controller.searchField())));
    }

    private static Button transferButton(String key, LucideIconView.Icon icon, Runnable action) {
        Button button = new Button(Texts.text(key), new LucideIconView(icon));
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setAccessibleText(Texts.text(key));
        button.setTooltip(new Tooltip(Texts.text(key)));
        button.getStyleClass().addAll("icon-button", "catalog-create-button");
        button.getGraphic().getStyleClass().add("catalog-create-icon");
        button.setOnAction(event -> action.run());
        return button;
    }

    SnippetLibraryController controller() { return controller; }
    Window window() { return dialog.getDialogPane().getScene().getWindow(); }
    void onHidden(Runnable action) { dialog.setOnHidden(event -> action.run()); }
    void show() { dialog.show(); }
    void close() { dialog.close(); }
    void focus() {
        window().requestFocus();
        controller.searchField().requestFocus();
    }
}
