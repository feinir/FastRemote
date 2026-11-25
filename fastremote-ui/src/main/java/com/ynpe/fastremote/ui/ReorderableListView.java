// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.TransferMode;

final class ReorderableListView {
    private static final DataFormat SOURCE_INDEX = new DataFormat("application/x-fastremote-list-reorder-index");
    private static final WritableImage TRANSPARENT_DRAG_VIEW = new WritableImage(1, 1);
    private static final String DROP_BEFORE = "bookmark-drop-before";
    private static final String DROP_AFTER = "bookmark-drop-after";

    private ReorderableListView() {}

    static <T> ListView<T> create(List<T> values) {
        ListView<T> list = new ListView<>(FXCollections.observableArrayList(values));
        list.setCellFactory(ignored -> new ListCell<>() {
            {
                setOnDragDetected(event -> {
                    if (getItem() == null) return;
                    ClipboardContent content = new ClipboardContent();
                    content.put(SOURCE_INDEX, Integer.toString(getIndex()));
                    var dragboard = startDragAndDrop(TransferMode.MOVE);
                    dragboard.setDragView(TRANSPARENT_DRAG_VIEW);
                    dragboard.setContent(content);
                    event.consume();
                });
                setOnDragOver(event -> {
                    if (dragComesFromThisList(event.getGestureSource(), getListView())
                            && event.getDragboard().hasContent(SOURCE_INDEX)) {
                        event.acceptTransferModes(TransferMode.MOVE);
                        showDropMarker(event.getY() < getHeight() / 2.0);
                    }
                    event.consume();
                });
                setOnDragExited(event -> clearDropMarker());
                setOnDragDropped(event -> {
                    if (dragComesFromThisList(event.getGestureSource(), getListView())
                            && event.getDragboard().hasContent(SOURCE_INDEX)) {
                        int from = Integer.parseInt((String) event.getDragboard().getContent(SOURCE_INDEX));
                        int insertionIndex = getIndex() + (event.getY() < getHeight() / 2.0 ? 0 : 1);
                        if (from >= 0 && from < getListView().getItems().size()) {
                            int to = reorder(getListView(), from, insertionIndex);
                            getListView().getSelectionModel().select(to);
                            event.setDropCompleted(true);
                        }
                    }
                    clearDropMarkers(getListView());
                    event.consume();
                });
                setOnDragDone(event -> clearDropMarkers(getListView()));
            }
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                if (empty) clearDropMarker();
            }

            private void showDropMarker(boolean before) {
                clearDropMarkers(getListView());
                getStyleClass().add(before ? DROP_BEFORE : DROP_AFTER);
            }

            private void clearDropMarker() {
                getStyleClass().removeAll(DROP_BEFORE, DROP_AFTER);
            }
        });
        return list;
    }

    private static boolean dragComesFromThisList(Object source, ListView<?> list) {
        return source instanceof ListCell<?> cell && cell.getListView() == list;
    }

    private static void clearDropMarkers(ListView<?> list) {
        list.lookupAll(".list-cell").forEach(node -> node.getStyleClass().removeAll(DROP_BEFORE, DROP_AFTER));
    }

    static <T> int reorder(ListView<T> list, int from, int insertionIndex) {
        var items = list.getItems();
        if (from < 0 || from >= items.size()) throw new IllegalArgumentException("Invalid source index");
        int target = Math.max(0, Math.min(insertionIndex, items.size()));
        if (from < target) target--;
        if (target != from) {
            var reordered = new ArrayList<>(items);
            reordered.add(target, reordered.remove(from));
            items.setAll(reordered);
        }
        return target;
    }
}
