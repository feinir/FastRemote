// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.i18n.Texts;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Non-modal shell for the one live AI view; it owns no chat or request state. */
final class AiAssistantDialog {
    private final Stage stage = new Stage();
    private final VBox root = new VBox();

    AiAssistantDialog(Window owner, Runnable onHidden) {
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle(Texts.text("legacy.java.1b0049fcfd4163fe"));
        root.getStyleClass().addAll("host-manager-dialog", "component-page", "ai-dialog-shell");
        ThemeManager.style(root);
        Scene scene = new Scene(root, 750, 760);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && !event.isConsumed()) {
                close();
                event.consume();
            }
        });
        stage.setScene(scene);
        DialogSpacing.centerOnOwnerWhenShown(stage);
        stage.setMinWidth(480);
        stage.setMinHeight(420);
        stage.setOnHidden(event -> {
            root.getChildren().clear();
            onHidden.run();
        });
    }

    void show(Node view) {
        root.getChildren().add(view);
        VBox.setVgrow(view, Priority.ALWAYS);
        stage.show();
    }

    boolean isShowing() { return stage.isShowing(); }
    void close() { stage.close(); }
    void focus() {
        stage.toFront();
        stage.requestFocus();
    }
}
