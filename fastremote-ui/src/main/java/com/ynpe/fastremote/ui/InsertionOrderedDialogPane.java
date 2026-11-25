// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.DialogPane;

/** A dialog pane whose buttons keep their declared left-to-right order on every platform. */
final class InsertionOrderedDialogPane extends DialogPane {
    @Override
    protected Node createButtonBar() {
        Node node = super.createButtonBar();
        if (node instanceof ButtonBar buttonBar) {
            buttonBar.setButtonOrder(ButtonBar.BUTTON_ORDER_NONE);
        }
        return node;
    }
}
