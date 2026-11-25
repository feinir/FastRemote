// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/** Accepts the clicked row directly, even while focus is still in the search field. */
final class SearchableSelectorMouseSupport {
    private SearchableSelectorMouseSupport() { }

    static <T> void install(ListView<T> results, Consumer<T> accept) {
        results.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || results.isDisabled()) return;
            if (!(event.getTarget() instanceof Node target)) return;
            for (Node node = target; node != null && node != results; node = node.getParent()) {
                if (node instanceof ListCell<?> cell && cell.getListView() == results) {
                    int index = cell.getIndex();
                    if (cell.isEmpty() || index < 0 || index >= results.getItems().size()) return;
                    T item = results.getItems().get(index);
                    results.getSelectionModel().select(index);
                    accept.accept(item);
                    event.consume();
                    return;
                }
            }
        });
    }
}
