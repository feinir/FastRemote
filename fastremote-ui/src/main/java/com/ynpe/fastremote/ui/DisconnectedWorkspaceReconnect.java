// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.function.BooleanSupplier;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/** Reconnects from retained content or the workspace header without stealing other input. */
final class DisconnectedWorkspaceReconnect {
    private DisconnectedWorkspaceReconnect() { }

    static void install(Node content, BooleanSupplier disconnected, Runnable reconnect) {
        content.addEventFilter(KeyEvent.KEY_PRESSED, event -> reconnect(event, disconnected, reconnect));
    }

    static void installTabHeader(TabPane tabs, BooleanSupplier disconnected, Runnable reconnect) {
        tabs.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            // Clicking a tab leaves focus on TabPane, outside its selected content.
            if (event.getTarget() instanceof Node target && isHeaderTarget(tabs, target)) {
                reconnect(event, disconnected, reconnect);
            }
        });
    }

    private static boolean isHeaderTarget(TabPane tabs, Node target) {
        if (target == tabs) return true;
        for (Node node = target; node != null; node = node.getParent()) {
            if (node instanceof TabPane) return false;
            if (node.getStyleClass().contains("tab-header-area") && node.getParent() == tabs) return true;
        }
        return false;
    }

    private static void reconnect(KeyEvent event, BooleanSupplier disconnected, Runnable reconnect) {
        if (event.getCode() != KeyCode.ENTER || event.isShiftDown() || event.isControlDown()
                || event.isAltDown() || event.isMetaDown() || !disconnected.getAsBoolean()) return;
        event.consume();
        reconnect.run();
    }
}
