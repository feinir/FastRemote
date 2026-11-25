// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MainMenuClickSupportTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        org.junit.jupiter.api.Assumptions.assumeFalse(System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("mac"), "macOS uses the native system menu");
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void oneReleaseRecoversAPressAfterDragAndWindowActivation() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                Menu file = new Menu("File", null, new MenuItem("Open"));
                Menu edit = new Menu("Edit", null, new MenuItem("Copy"));
                Menu hidden = new Menu("Hidden");
                hidden.setVisible(false);
                MenuBar menuBar = new MenuBar(hidden, file, edit);
                MainMenuClickSupport.install(menuBar, true);
                stage.setScene(new Scene(new BorderPane(null, menuBar, null, null, null), 420, 180));
                stage.show();
                menuBar.applyCss();
                menuBar.layout();

                Node fileButton = menuBar.lookupAll(".menu-button").stream()
                        .min(java.util.Comparator.comparingDouble(node ->
                                node.localToScene(node.getBoundsInLocal()).getMinX()))
                        .orElseThrow();
                Bounds bounds = fileButton.localToScene(fileButton.getBoundsInLocal());
                assertSame(file, MainMenuClickSupport.menuAt(menuBar,
                        bounds.getCenterX(), bounds.getCenterY()));

                // Fire at the MenuBar filter without invoking the internal button handler. This
                // is the state left when MenuBarSkin rejects the press before GTK focuses its owner.
                menuBar.fireEvent(mouse(menuBar, MouseEvent.MOUSE_PRESSED,
                        bounds.getCenterX(), bounds.getCenterY()));
                menuBar.fireEvent(mouse(menuBar, MouseEvent.MOUSE_RELEASED,
                        bounds.getCenterX(), bounds.getCenterY()));
                assertFalse(file.isShowing());

                javafx.animation.PauseTransition afterFocusRecovery =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(260));
                afterFocusRecovery.setOnFinished(ignored -> {
                    try {
                        assertTrue(file.isShowing(),
                                "the release fallback must open the menu without extra clicks");
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        file.hide();
                        stage.hide();
                        completed.countDown();
                    }
                });
                afterFocusRecovery.play();
            } catch (Throwable error) {
                failure.set(error);
                stage.hide();
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void releaseFollowedByFocusCleanupRecoversButClickAgainStillCloses() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            Menu file = new Menu("File", null, new MenuItem("Open"));
            MenuBar bar = new MenuBar(file);
            var log = new javafx.scene.control.TextArea("Generated diagnostic log");
            log.setEditable(false);
            MainMenuClickSupport.install(bar, true);
            stage.setScene(new Scene(new BorderPane(log, bar, null, null, null), 420, 180));
            stage.show(); bar.applyCss(); bar.layout(); log.requestFocus(); log.selectAll();
            Node button = bar.lookup(".menu-button");
            Bounds bounds = button.localToScene(button.getBoundsInLocal());
            // Target the actual owner button, otherwise PopupWindow auto-hide treats a synthetic
            // event on the MenuBar itself as an outside press before our filter can see it.
            button.addEventFilter(MouseEvent.ANY, javafx.event.Event::consume);
            button.fireEvent(mouse(bar, MouseEvent.MOUSE_PRESSED, bounds.getCenterX(), bounds.getCenterY()).copyFor(button, button));
            file.show();
            button.fireEvent(mouse(bar, MouseEvent.MOUSE_RELEASED, bounds.getCenterX(), bounds.getCenterY()).copyFor(button, button));
            file.hide(); // Native/focus cleanup after the release filter has already observed showing=true.
            var recovered = new javafx.animation.PauseTransition(javafx.util.Duration.millis(260));
            recovered.setOnFinished(ignored -> {
                try {
                    assertTrue(file.isShowing(), "late cleanup must not swallow the opening click");
                    button.fireEvent(mouse(bar, MouseEvent.MOUSE_PRESSED, bounds.getCenterX(), bounds.getCenterY()).copyFor(button, button));
                    button.fireEvent(mouse(bar, MouseEvent.MOUSE_RELEASED, bounds.getCenterX(), bounds.getCenterY()).copyFor(button, button));
                    file.hide(); // Standard skin's click-again-to-close path.
                    var closed = new javafx.animation.PauseTransition(javafx.util.Duration.millis(260));
                    closed.setOnFinished(event -> {
                        try { assertFalse(file.isShowing(), "an intentional closing click must not reopen"); }
                        catch (Throwable error) { failure.set(error); }
                        finally { file.hide(); stage.close(); completed.countDown(); }
                    });
                    closed.play();
                } catch (Throwable error) {
                    failure.set(error); file.hide(); stage.close(); completed.countDown();
                }
            });
            recovered.play();
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test
    void aNewUserActionCancelsPendingRecovery() throws Exception {
        for (String cancellation : java.util.List.of("escape", "outside", "action")) {
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Platform.runLater(() -> {
                Stage stage = new Stage();
                Menu file = new Menu("File", null, new MenuItem("Open"));
                MenuBar bar = new MenuBar(file);
                MainMenuClickSupport.install(bar, true);
                var log = new javafx.scene.control.TextArea("Generated log");
                stage.setScene(new Scene(new BorderPane(log, bar, null, null, null), 420, 180));
                stage.show(); bar.applyCss(); bar.layout();
                Node button = bar.lookup(".menu-button");
                Bounds bounds = button.localToScene(button.getBoundsInLocal());
                button.addEventFilter(MouseEvent.ANY, javafx.event.Event::consume);
                button.fireEvent(mouse(bar, MouseEvent.MOUSE_PRESSED, bounds.getCenterX(), bounds.getCenterY()).copyFor(button, button));
                button.fireEvent(mouse(bar, MouseEvent.MOUSE_RELEASED, bounds.getCenterX(), bounds.getCenterY()).copyFor(button, button));
                switch (cancellation) {
                    case "escape" -> javafx.event.Event.fireEvent(stage.getScene(), new javafx.scene.input.KeyEvent(
                            javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.ESCAPE,
                            false, false, false, false));
                    case "outside" -> log.fireEvent(mouse(bar, MouseEvent.MOUSE_PRESSED, 200, 140).copyFor(log, log));
                    case "action" -> file.getItems().getFirst().fire();
                    default -> throw new AssertionError(cancellation);
                }
                var settled = new javafx.animation.PauseTransition(javafx.util.Duration.millis(260));
                settled.setOnFinished(event -> {
                    try { assertFalse(file.isShowing(), cancellation + " must cancel stale recovery"); }
                    catch (Throwable error) { failure.set(error); }
                    finally { file.hide(); stage.close(); completed.countDown(); }
                });
                settled.play();
            });
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError(failure.get());
        }
    }

    @Test
    void releaseOutsideThePressedMenuDoesNotOpenAnything() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                Menu file = new Menu("File", null, new MenuItem("Open"));
                Menu edit = new Menu("Edit", null, new MenuItem("Copy"));
                MenuBar menuBar = new MenuBar(file, edit);
                MainMenuClickSupport.install(menuBar, true);
                stage.setScene(new Scene(new BorderPane(null, menuBar, null, null, null), 420, 180));
                stage.show();
                menuBar.applyCss();
                menuBar.layout();
                java.util.List<Node> buttons = menuBar.lookupAll(".menu-button").stream()
                        .sorted(java.util.Comparator.comparingDouble(node ->
                                node.localToScene(node.getBoundsInLocal()).getMinX()))
                        .toList();
                Bounds first = buttons.getFirst().localToScene(buttons.getFirst().getBoundsInLocal());
                Bounds second = buttons.get(1).localToScene(buttons.get(1).getBoundsInLocal());
                menuBar.fireEvent(mouse(menuBar, MouseEvent.MOUSE_PRESSED,
                        first.getCenterX(), first.getCenterY()));
                menuBar.fireEvent(mouse(menuBar, MouseEvent.MOUSE_RELEASED,
                        second.getCenterX(), second.getCenterY()));

                javafx.animation.PauseTransition afterRecoveryWindow =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(260));
                afterRecoveryWindow.setOnFinished(ignored -> {
                    try {
                        assertFalse(file.isShowing());
                        assertFalse(edit.isShowing());
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        stage.hide();
                        completed.countDown();
                    }
                });
                afterRecoveryWindow.play();
            } catch (Throwable error) {
                failure.set(error);
                stage.hide();
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    private static MouseEvent mouse(
            MenuBar menuBar, javafx.event.EventType<MouseEvent> type, double sceneX, double sceneY) {
        javafx.geometry.Point2D local = menuBar.sceneToLocal(sceneX, sceneY);
        return new MouseEvent(type, local.getX(), local.getY(), sceneX, sceneY, MouseButton.PRIMARY, 1,
                false, false, false, false,
                type == MouseEvent.MOUSE_PRESSED, false, false,
                false, false, type != MouseEvent.MOUSE_PRESSED,
                new PickResult(menuBar, local.getX(), local.getY()));
    }
}
