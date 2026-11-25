// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;
import javafx.util.Duration;

/** Preserves a window-menu click across JavaFX focus activation and popup cleanup. */
final class MainMenuClickSupport {
    private static final Object INSTALLED = new Object();

    private MainMenuClickSupport() { }

    static void install(MenuBar menuBar) {
        // macOS uses the system menu bar; the in-window Windows/Linux menus share this event path.
        install(menuBar, !System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac"));
    }

    static void install(MenuBar menuBar, boolean enabled) {
        Objects.requireNonNull(menuBar, "menuBar");
        if (!enabled || menuBar.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null) return;

        AtomicReference<Menu> pressedMenu = new AtomicReference<>();
        AtomicBoolean closingMenu = new AtomicBoolean();
        AtomicLong clickSequence = new AtomicLong();
        // Recovery belongs to one gesture only. A later outside click, key or menu action
        // cancels it, so a quickly dismissed/activated menu is never reopened by the callback.
        javafx.event.EventHandler<javafx.scene.input.KeyEvent> cancelKey = event -> clickSequence.incrementAndGet();
        javafx.event.EventHandler<MouseEvent> cancelOutside = event -> {
            if (menuAt(menuBar, event.getSceneX(), event.getSceneY()) == null) clickSequence.incrementAndGet();
        };
        javafx.beans.value.ChangeListener<javafx.scene.Scene> sceneChanged = (ignored, before, after) -> {
            clickSequence.incrementAndGet();
            if (before != null) {
                before.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, cancelKey);
                before.removeEventFilter(MouseEvent.MOUSE_PRESSED, cancelOutside);
            }
            if (after != null) {
                after.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, cancelKey);
                after.addEventFilter(MouseEvent.MOUSE_PRESSED, cancelOutside);
            }
        };
        menuBar.sceneProperty().addListener(sceneChanged);
        sceneChanged.changed(menuBar.sceneProperty(), null, menuBar.getScene());
        javafx.event.EventHandler<javafx.event.ActionEvent> cancelAction = event -> clickSequence.incrementAndGet();
        menuBar.getMenus().forEach(menu -> menu.addEventHandler(javafx.event.ActionEvent.ACTION, cancelAction));
        menuBar.getMenus().addListener((javafx.collections.ListChangeListener<Menu>) change -> {
            while (change.next()) {
                change.getRemoved().forEach(menu -> menu.removeEventHandler(javafx.event.ActionEvent.ACTION, cancelAction));
                change.getAddedSubList().forEach(menu -> menu.addEventHandler(javafx.event.ActionEvent.ACTION, cancelAction));
            }
        });
        menuBar.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            clickSequence.incrementAndGet();
            // At MOUSE_PRESSED this flag can still describe the preceding drag/selection.
            // Only MOUSE_RELEASED tells us whether this new click actually moved.
            if (event.getButton() != MouseButton.PRIMARY) {
                pressedMenu.set(null);
                return;
            }
            pressedMenu.set(menuAt(menuBar, event.getSceneX(), event.getSceneY()));
            closingMenu.set(pressedMenu.get() != null && pressedMenu.get().isShowing());
        });
        menuBar.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            Menu pressed = pressedMenu.getAndSet(null);
            if (pressed == null || event.getButton() != MouseButton.PRIMARY
                    || !event.isStillSincePress()
                    || pressed != menuAt(menuBar, event.getSceneX(), event.getSceneY())
                    || closingMenu.get()) return;
            long sequence = clickSequence.get();

            // MenuBarSkin only opens on MOUSE_PRESSED when the owner Window is already focused.
            // A native peer may deliver that press before asynchronous activation completes. Waiting one
            // FX turn lets the skin finish the ordinary path first and only repairs a missed click.
            Platform.runLater(() -> openAfterFocus(menuBar, pressed, clickSequence, sequence, 5));
        });
    }

    private static void openAfterFocus(
            MenuBar menuBar, Menu menu, AtomicLong clickSequence, long sequence, int attemptsLeft) {
        Window window = menuBar.getScene() == null ? null : menuBar.getScene().getWindow();
        if (window == null || !window.isShowing() || clickSequence.get() != sequence
                || !menu.isVisible() || menu.isDisable()) return;
        if (window.isFocused()) {
            // Focus notification can precede MenuBarSkin's cleanup for the old focus state.
            // Also check clicks whose menu is still showing at release: the peer can hide that popup
            // afterwards. Only the pre-press state distinguishes an intentional close from this
            // dropped opening; testing isShowing in the release filter misses the latter.
            PauseTransition afterFocusSettles = new PauseTransition(Duration.millis(40));
            afterFocusSettles.setOnFinished(ignored -> showIfStillCurrent(
                    menuBar, menu, clickSequence, sequence));
            afterFocusSettles.play();
            return;
        }
        if (attemptsLeft <= 0) return;
        window.requestFocus();
        PauseTransition retry = new PauseTransition(Duration.millis(40));
        retry.setOnFinished(ignored ->
                openAfterFocus(menuBar, menu, clickSequence, sequence, attemptsLeft - 1));
        retry.play();
    }

    private static void showIfStillCurrent(
            MenuBar menuBar, Menu menu, AtomicLong clickSequence, long sequence) {
        Window window = menuBar.getScene() == null ? null : menuBar.getScene().getWindow();
        if (window != null && window.isShowing() && window.isFocused()
                && clickSequence.get() == sequence && !anyMenuShowing(menuBar)
                && menu.isVisible() && !menu.isDisable()) {
            menu.show();
        }
    }

    private static boolean anyMenuShowing(MenuBar menuBar) {
        return menuBar.getMenus().stream().anyMatch(Menu::isShowing);
    }

    static Menu menuAt(MenuBar menuBar, double sceneX, double sceneY) {
        Node containerNode = menuBar.lookup(".container");
        if (!(containerNode instanceof Parent container)) return null;
        List<Node> buttons = container.getChildrenUnmodifiable().stream()
                .filter(node -> node.getStyleClass().contains("menu-button"))
                .filter(Node::isVisible)
                .toList();
        List<Menu> menus = menuBar.getMenus().stream().filter(Menu::isVisible).toList();
        for (int index = 0; index < buttons.size() && index < menus.size(); index++) {
            Node button = buttons.get(index);
            javafx.geometry.Bounds bounds = button.localToScene(button.getBoundsInLocal());
            if (bounds != null && bounds.contains(sceneX, sceneY)) return menus.get(index);
        }
        return null;
    }
}
