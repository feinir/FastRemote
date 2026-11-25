// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.scene.control.ButtonType;

/** Ensures standard alerts use the same bundled UI font and styling as the main scene. */
final class Alert extends javafx.scene.control.Alert {
    Alert(AlertType alertType) {
        super(alertType);
        DialogSpacing.prepare(this);
    }

    Alert(AlertType alertType, String contentText, ButtonType... buttons) {
        super(alertType, contentText, buttons);
        DialogSpacing.prepare(this);
    }
}
