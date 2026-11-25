// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.InputMethodRequests;
import javafx.stage.Popup;
import javafx.stage.WindowEvent;

/** Bridges JavaFX 21's non-focusable popup to the native input method of its owner. */
final class SearchableSelectorInputMethod {
    private SearchableSelectorInputMethod() { }

    static void install(Popup popup, Node owner, TextField search) {
        popup.addEventHandler(WindowEvent.WINDOW_SHOWN, ignored -> {
            InputMethodRequests previousRequests = owner.getInputMethodRequests();
            EventHandler<? super InputMethodEvent> previousHandler = owner.getOnInputMethodTextChanged();
            InputMethodRequests requests = new InputMethodRequests() {
                @Override public Point2D getTextLocation(int offset) {
                    return search.getInputMethodRequests().getTextLocation(offset);
                }
                @Override public int getLocationOffset(int x, int y) {
                    return search.getInputMethodRequests().getLocationOffset(x, y);
                }
                @Override public void cancelLatestCommittedText() {
                    search.getInputMethodRequests().cancelLatestCommittedText();
                }
                @Override public String getSelectedText() {
                    return search.getInputMethodRequests().getSelectedText();
                }
            };
            EventHandler<InputMethodEvent> handler = event -> {
                if (popup.getScene().getFocusOwner() == search) {
                    Event.fireEvent(search, event.copyFor(search, search));
                    event.consume();
                }
            };
            // Popup search has JavaFX focus, but the MenuButton retains native window focus.
            // Enable composition there and route both candidate positioning and text to search.
            owner.setInputMethodRequests(requests);
            owner.setOnInputMethodTextChanged(handler);
            refreshNativeFocus(owner);
            popup.addEventHandler(WindowEvent.WINDOW_HIDDEN, new EventHandler<>() {
                @Override public void handle(WindowEvent event) {
                    popup.removeEventHandler(WindowEvent.WINDOW_HIDDEN, this);
                    if (owner.getOnInputMethodTextChanged() == handler) {
                        owner.setOnInputMethodTextChanged(previousHandler);
                    }
                    if (owner.getInputMethodRequests() == requests) owner.setInputMethodRequests(previousRequests);
                    refreshNativeFocus(owner);
                }
            });
        });
    }
    private static void refreshNativeFocus(Node owner) {
        // JavaFX 21 reevaluates native IME enablement only when the scene focus owner changes.
        if (owner.getScene() != null && owner.getScene().getFocusOwner() == owner) {
            // Keep the temporary focus inside its dock: focusing the scene root closes
            // auto-hiding side panels while their selector is opening.
            Node reset = owner.getParent() == null ? owner.getScene().getRoot() : owner.getParent();
            reset.requestFocus();
            owner.requestFocus();
        }
    }
}
