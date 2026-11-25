// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.skin.TreeViewSkin;
import javafx.scene.control.skin.VirtualFlow;

/** Tree view that can keep a programmatically selected row near the viewport center. */
final class CenteredTreeView<T> extends TreeView<T> {
    void centerSelection() {
        TreeItem<T> item = getSelectionModel().getSelectedItem();
        if (item == null) return;
        int row = getRow(item);
        if (row < 0) return;
        Skin<?> skin = getSkin();
        if (!shouldRepositionSelection(focusWithinTree(), rowVisible(skin, row))) return;
        scrollTo(row);
        Platform.runLater(() -> {
            if (getSelectionModel().getSelectedItem() != item || focusWithinTree()) return;
            int currentRow = getRow(item);
            if (currentRow < 0) return;
            Skin<?> currentSkin = getSkin();
            if (currentSkin instanceof CenteredTreeViewSkin<?> centeredSkin) {
                centeredSkin.centerOn(currentRow);
            }
        });
    }

    private boolean focusWithinTree() {
        Node focused = getScene() == null ? null : getScene().getFocusOwner();
        for (Node node = focused; node != null; node = node.getParent()) {
            if (node == this) return true;
        }
        return false;
    }

    private static boolean rowVisible(Skin<?> skin, int row) {
        return skin instanceof CenteredTreeViewSkin<?> centeredSkin
                && centeredSkin.rowVisible(row);
    }

    static boolean shouldRepositionSelection(boolean focusWithinTree, boolean rowVisible) {
        return !focusWithinTree && !rowVisible;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new CenteredTreeViewSkin<>(this);
    }

    static double centeringDelta(double cellStart, double cellLength, double viewportLength) {
        return cellStart + cellLength / 2.0 - viewportLength / 2.0;
    }

    private static final class CenteredTreeViewSkin<T> extends TreeViewSkin<T> {
        private CenteredTreeViewSkin(TreeView<T> treeView) {
            super(treeView);
        }

        private void centerOn(int row) {
            VirtualFlow<TreeCell<T>> flow = getVirtualFlow();
            flow.scrollTo(row);
            flow.layout();
            TreeCell<T> cell = flow.getVisibleCell(row);
            if (cell == null) return;
            flow.scrollPixels(centeringDelta(cell.getLayoutY(), cell.getHeight(), flow.getHeight()));
        }

        private boolean rowVisible(int row) {
            return getVirtualFlow().getVisibleCell(row) != null;
        }
    }
}
