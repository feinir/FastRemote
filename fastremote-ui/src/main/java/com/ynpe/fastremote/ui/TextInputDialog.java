// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

/** Ensures standard text-input dialogs use the same bundled UI font as the main scene. */
final class TextInputDialog extends javafx.scene.control.TextInputDialog {
    TextInputDialog(String defaultValue) {
        super(defaultValue);
        DialogSpacing.prepare(this);
    }
}
