// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SearchableSelectorInputMethodTest {
    @BeforeAll static void toolkit() throws Exception {
        var latch = new CountDownLatch(1);
        try { Platform.startup(latch::countDown); }
        catch (IllegalStateException started) { latch.countDown(); }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test void commitsChineseThroughTheOwnerAndRestoresItsInputMethodOnEachClose() throws Exception {
        onFx(() -> {
            Stage stage = new Stage();
            Popup popup = new Popup();
            try {
                MenuButton owner = new MenuButton("Search");
                TextField search = new TextField();
                var previousHandler = new javafx.event.EventHandler<InputMethodEvent>() {
                    @Override public void handle(InputMethodEvent event) { }
                };
                owner.setOnInputMethodTextChanged(previousHandler);
                StackPane dock = new StackPane(owner);
                StackPane root = new StackPane(dock);
                stage.setScene(new Scene(root, 300, 150));
                var escapedDock = new java.util.ArrayList<javafx.scene.Node>();
                stage.getScene().focusOwnerProperty().addListener((ignored, previous, focused) -> {
                    if (focused == root) escapedDock.add(focused);
                });
                stage.show();
                popup.getContent().add(search);
                SearchableSelectorInputMethod.install(popup, owner, search);
                for (int i = 0; i < 2; i++) {
                    owner.requestFocus();
                    popup.show(owner, stage.getX(), stage.getY());
                    search.requestFocus();
                    search.clear();
                    assertNotNull(owner.getInputMethodRequests());
                    assertEquals(search.getInputMethodRequests().getTextLocation(0),
                            owner.getInputMethodRequests().getTextLocation(0));
                    Event.fireEvent(owner, new InputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                            List.of(), "你好", 0));
                    assertEquals("你好", search.getText());
                    popup.hide();
                    assertSame(previousHandler, owner.getOnInputMethodTextChanged());
                    assertNull(owner.getInputMethodRequests());
                    assertTrue(escapedDock.isEmpty(), "IME focus refresh must stay inside its side dock");
                }
            } finally { popup.hide(); stage.close(); }
        });
    }

    @Test void ownerPressClosesPopupEvenWhenFocusChangeHidesItDuringThePress() throws Exception {
        onFx(() -> {
            Stage stage = new Stage();
            var node = new MenuButton("Hosts");
            var selector = new SearchableJumpHostSelector(node, List.of(), List.of(), java.util.Optional.empty(), java.util.Optional.empty());
            StackPane root = new StackPane(node);
            try {
                stage.setScene(new Scene(root, 400, 200));
                stage.show(); root.applyCss(); root.layout();
                for (int i = 0; i < 2; i++) {
                    node.fire();
                    assertTrue(selector.popup().isShowing());
                    root.requestFocus();
                    var listener = new javafx.beans.value.ChangeListener<Boolean>() {
                        @Override public void changed(javafx.beans.value.ObservableValue<? extends Boolean> value, Boolean previous, Boolean focused) {
                            if (focused) selector.popup().hide();
                        }
                    };
                    node.focusedProperty().addListener(listener);
                    try {
                        node.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                                1, 1, 1, 1, javafx.scene.input.MouseButton.PRIMARY, 1,
                                false, false, false, false, true, false, false, false, false, true, null));
                        assertFalse(selector.popup().isShowing(), "Owner press must not reopen a popup hidden by focus");
                    } finally { node.focusedProperty().removeListener(listener); }
                }
            } finally { selector.popup().hide(); stage.close(); }
        });
    }

    @Test void outsidePressReachesInputAndOwnerShadowPressClosesPopup() throws Exception {
        onFx(() -> {
            Stage stage = new Stage();
            MenuButton owner = new MenuButton("Hosts");
            TextField input = new TextField();
            Popup popup = new Popup();
            try {
                stage.setScene(new Scene(new javafx.scene.layout.VBox(owner, input), 400, 200));
                stage.show(); stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                popup.getContent().add(new TextField());
                SearchableSelectorPopupInteraction.install(popup, owner);
                var delivered = new java.util.concurrent.atomic.AtomicInteger();
                input.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
                    delivered.incrementAndGet(); input.requestFocus();
                });
                for (int i = 0; i < 2; i++) {
                    popup.show(owner, stage.getX(), stage.getY());
                    input.fireEvent(press(1, 1));
                    assertFalse(popup.isShowing());
                    assertEquals(i + 1, delivered.get(), "Outside press must reach the input on the first click");
                    assertSame(input, stage.getScene().getFocusOwner());
                    popup.show(owner, stage.getX(), stage.getY());
                    var bounds = owner.localToScreen(owner.getBoundsInLocal());
                    Event.fireEvent(popup.getScene(), press(bounds.getCenterX(), bounds.getCenterY()));
                    assertFalse(popup.isShowing(), "Press delivered to the popup shadow over its owner must close it");
                }
            } finally { popup.hide(); stage.close(); }
        });
    }

    private static javafx.scene.input.MouseEvent press(double screenX, double screenY) {
        return new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                1, 1, screenX, screenY, javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, true, null);
    }

    static void onFx(Runnable action) throws Exception {
        var latch = new CountDownLatch(1);
        var error = new AtomicReference<Throwable>();
        Platform.runLater(() -> {
            try { action.run(); } catch (Throwable failure) { error.set(failure); }
            finally { latch.countDown(); }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (error.get() != null) throw new AssertionError(error.get());
    }
}
