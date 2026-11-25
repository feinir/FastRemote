// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.scene.control.Dialog;
import javafx.scene.control.Label;

/** Keeps inline vault feedback and the action bar inside the visible window. */
final class VaultDialogFeedback {
    private VaultDialogFeedback() {}

    static void configure(Dialog<?> dialog, Label feedback, double width) {
        dialog.getDialogPane().setMinWidth(width);
        dialog.getDialogPane().setPrefWidth(width);
        dialog.getDialogPane().setMaxWidth(width);
        DialogSpacing.configureWrappingLabel(feedback);
        // Text changes also matter after the progress message has made the label managed:
        // a later failure can wrap onto several lines without another visibility change.
        feedback.textProperty().addListener((ignored, previous, current) ->
                DialogSpacing.growHeightToContent(dialog));
        feedback.managedProperty().addListener((ignored, previous, current) ->
                DialogSpacing.growHeightToContent(dialog));
    }
}
