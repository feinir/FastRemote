// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.stage.Window;

/** Window services needed by a library editor, independent of the main controller. */
interface LibraryEditorActions {
    Window owner();
    void refresh();
    void status(String message, boolean error);
}
