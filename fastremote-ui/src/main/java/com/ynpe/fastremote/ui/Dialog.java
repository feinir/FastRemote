// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

/** Ensures every application dialog receives bundled localization, theme, spacing, and sizing preparation. */
final class Dialog<R> extends javafx.scene.control.Dialog<R> {
    Dialog() {
        DialogSpacing.prepare(this);
    }
}
