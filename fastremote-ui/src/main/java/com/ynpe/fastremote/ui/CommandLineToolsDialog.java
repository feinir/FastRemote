// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.i18n.Texts;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Keeps filesystem installation and its asynchronous UI feedback out of the main controller. */
final class CommandLineToolsDialog {
    private CommandLineToolsDialog() { }

    static void install(Window owner, MenuItem action, LocalTerminalWorkflow workflow) {
        action.setDisable(true);
        workflow.installCommandLineTools().whenComplete((path, error) -> Platform.runLater(() -> {
            action.setDisable(false);
            Alert alert = new Alert(error == null ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
            alert.initOwner(owner);
            alert.setTitle(Texts.text("action.installCommandLineTools"));
            alert.setHeaderText(null);
            String status;
            if (error == null) {
                status = Texts.text("cli.install.success", path);
            } else {
                Throwable cause = error;
                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                status = Texts.text("cli.install.failed") + "\n" + cause.getMessage();
            }
            alert.setGraphic(null);
            alert.getDialogPane().setContent(content(status));
            DialogSpacing.contentToButtonBar(alert);
            alert.show();
        }));
    }

    static VBox content(String status) {
        VBox content = new VBox(12.0, card(Texts.text("cli.install.instructions")), card(status));
        content.setPrefWidth(520.0);
        return content;
    }

    private static VBox card(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        VBox card = new VBox(label);
        card.getStyleClass().add("settings-card");
        return card;
    }
}
