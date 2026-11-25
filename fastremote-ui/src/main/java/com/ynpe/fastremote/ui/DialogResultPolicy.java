// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.scene.control.ButtonType;
import javafx.util.Callback;

/** Prevents JavaFX from casting a cancel ButtonType to a dialog's domain result type. */
final class DialogResultPolicy {
    private DialogResultPolicy() {}

    static <T> Callback<ButtonType, T> explicitOnly() {
        return ignored -> null;
    }
}
