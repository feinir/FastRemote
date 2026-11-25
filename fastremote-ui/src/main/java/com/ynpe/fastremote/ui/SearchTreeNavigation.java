// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.application.Platform;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/** Keyboard loop over the tree's currently expanded, filtered rows. */
final class SearchTreeNavigation {
    static <T> void install(TextField search, TreeView<T> tree, Runnable flushSearch) {
        search.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!plainArrow(event)) return;
            flushSearch.run();
            if (tree.getExpandedItemCount() > 0 && tree.isVisible() && !tree.isDisabled()) {
                int row = event.getCode() == KeyCode.UP ? tree.getExpandedItemCount() - 1 : 0;
                tree.requestFocus();
                tree.getSelectionModel().clearAndSelect(row);
                tree.getFocusModel().focus(row);
                tree.scrollTo(row);
            }
            event.consume();
        });
        tree.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!plainArrow(event)) return;
            int row = tree.getFocusModel().getFocusedIndex();
            if (row < 0) row = tree.getSelectionModel().getSelectedIndex();
            int count = tree.getExpandedItemCount();
            if (count == 0 || (event.getCode() == KeyCode.UP && row == 0)
                    || (event.getCode() == KeyCode.DOWN && row == count - 1)) {
                search.requestFocus();
                search.positionCaret(search.getLength());
                // TextField's focus behavior can select all later in this pulse.
                Platform.runLater(() -> {
                    if (search.isFocused()) search.positionCaret(search.getLength());
                });
                event.consume();
            }
        });
    }

    private static boolean plainArrow(KeyEvent event) {
        return (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN)
                && !event.isShiftDown() && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown();
    }

    private SearchTreeNavigation() { }
}
