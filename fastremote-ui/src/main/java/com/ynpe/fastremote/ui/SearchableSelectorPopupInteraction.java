// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Popup;
import javafx.stage.WindowEvent;

/** One owner-scene click closes the popup; outside clicks continue to their intended control. */
final class SearchableSelectorPopupInteraction {
    private SearchableSelectorPopupInteraction() { }

    static void install(Popup popup, Node owner) {
        // Native auto-hide runs before the owner's press filter and can consume the press
        // or hide then reopen on the same click. Handle owner-scene presses exactly once.
        popup.setAutoHide(false);
        popup.setConsumeAutoHidingEvents(false);
        // The native popup rectangle includes its shadow, which can overlap the owner.
        // Such presses arrive in the popup scene rather than in the owner's scene.
        popup.getScene().addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            var bounds = owner.localToScreen(owner.getBoundsInLocal());
            if (popup.isShowing() && bounds != null && bounds.contains(event.getScreenX(), event.getScreenY())) {
                popup.hide();
                event.consume();
            }
        });
        popup.addEventHandler(WindowEvent.WINDOW_SHOWN, ignored -> {
            Scene scene = owner.getScene();
            if (scene == null) return;
            EventHandler<MouseEvent> pressed = event -> {
                if (!popup.isShowing()) return;
                boolean onOwner = false;
                for (Node node = event.getTarget() instanceof Node target ? target : null;
                        node != null; node = node.getParent()) {
                    if (node == owner) { onOwner = true; break; }
                }
                popup.hide();
                if (onOwner) event.consume();
            };
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, pressed);
            popup.addEventHandler(WindowEvent.WINDOW_HIDDEN, new EventHandler<>() {
                @Override public void handle(WindowEvent event) {
                    scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, pressed);
                    popup.removeEventHandler(WindowEvent.WINDOW_HIDDEN, this);
                }
            });
        });
    }
}
