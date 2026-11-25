// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.scene.Node;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/** Mouse gestures shared by workspace tab headers and their blank title area. */
final class WorkspaceTabHeaderGestures {
    private Node pressed;
    private Node previousClick;

    static void install(TabPane tabs, Runnable tabDoubleClick, Runnable blankDoubleClick) {
        new WorkspaceTabHeaderGestures(tabs, tabDoubleClick, blankDoubleClick);
    }

    private WorkspaceTabHeaderGestures(TabPane tabs, Runnable tabDoubleClick, Runnable blankDoubleClick) {
        tabs.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            pressed = event.getButton() == MouseButton.PRIMARY
                    && event.getTarget() instanceof Node target ? gestureRegion(tabs, target) : null;
            if (pressed == null) previousClick = null;
        });
        tabs.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> reset());
        tabs.getTabs().addListener((javafx.collections.ListChangeListener<javafx.scene.control.Tab>) change -> reset());
        tabs.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            Node origin = pressed;
            pressed = null;
            // TabPaneSkin removes the close-button's tab on MOUSE_PRESSED. A later
            // click may target the exposed header; it must not become a blank-area gesture.
            if (event.getButton() != MouseButton.PRIMARY || !event.isStillSincePress()
                    || !(event.getTarget() instanceof Node target)
                    || origin == null || origin != gestureRegion(tabs, target)) {
                previousClick = null;
                return;
            }
            boolean doubleClick = event.getClickCount() == 2 && previousClick == origin;
            previousClick = event.getClickCount() == 1 ? origin : null;
            if (!doubleClick) return;
            event.consume();
            if (origin.getStyleClass().contains("tab")) tabDoubleClick.run();
            else blankDoubleClick.run();
        });
    }

    private void reset() {
        pressed = null;
        previousClick = null;
    }

    private static Node gestureRegion(TabPane tabs, Node target) {
        for (Node node = target; node != null; node = node.getParent()) {
            if (node.getStyleClass().contains("tab-close-button")
                    || node.getStyleClass().contains("control-buttons-tab")
                    || node.getStyleClass().contains("tab-down-button")) return null;
            if (node.getStyleClass().contains("tab")
                    || node.getStyleClass().contains("tab-header-area")) return node;
            if (node == tabs) break;
        }
        return null;
    }

    static boolean blank(TabPane tabs, Node target) {
        boolean insideHeader = false;
        for (Node node = target; node != null; node = node.getParent()) {
            if (node.getStyleClass().contains("tab")) return false;
            if (node.getStyleClass().contains("control-buttons-tab")
                    || node.getStyleClass().contains("tab-down-button")) return false;
            if (node.getStyleClass().contains("tab-header-area")) insideHeader = true;
            if (node == tabs) break;
        }
        return insideHeader;
    }
}
