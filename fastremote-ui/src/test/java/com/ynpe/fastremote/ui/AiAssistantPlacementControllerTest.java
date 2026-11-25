// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.i18n.Texts;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AiAssistantPlacementControllerTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
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
    void statusToggleClosesAiEvenWhenInactiveAndReopeningKeepsTheDraft() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                TextArea draft = new TextArea("Unsent prompt");
                StackPane view = new StackPane(draft);
                StackPane dock = new StackPane();
                Tab terminal = new Tab("Terminal");
                TabPane tabs = new TabPane(terminal);
                AiAssistantPlacementController placement = new AiAssistantPlacementController(view, tabs, () -> { });

                placement.attachDock(dock);
                assertFalse(placement.closeWorkspaceIfOpen());
                assertSame(dock, view.getParent());
                placement.openWorkspace();
                assertTrue(dock.getChildren().isEmpty());
                assertSame(view, placement.workspaceTab().orElseThrow().getContent());
                tabs.getSelectionModel().select(terminal);

                assertTrue(placement.closeWorkspaceIfOpen());
                assertFalse(placement.isWorkspaceOpen());
                assertEquals(java.util.List.of(terminal), tabs.getTabs());
                assertTrue(dock.getChildren().isEmpty());
                assertFalse(placement.closeWorkspaceIfOpen());

                placement.openWorkspace();
                assertSame(view, placement.workspaceTab().orElseThrow().getContent());
                assertSame(placement.workspaceTab().orElseThrow(), tabs.getSelectionModel().getSelectedItem());
                assertEquals("Unsent prompt", draft.getText());
                assertEquals(2, tabs.getTabs().size());
                placement.closeWorkspace();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void workspaceAiTabKeepsItsPositionWhenReopened() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                TabPane tabs = new TabPane(new Tab("Terminal"), new Tab("SFTP"));
                AiAssistantPlacementController placement = new AiAssistantPlacementController(
                        new StackPane(), tabs, () -> { });

                placement.openWorkspace();
                Tab ai = placement.workspaceTab().orElseThrow();
                assertSame(ai, tabs.getTabs().getLast());
                assertSame(ai, tabs.getSelectionModel().getSelectedItem());
                assertEquals(WorkspaceTabGraphics.ICON_SIZE,
                        ((LucideIconView) ai.getGraphic()).getPrefWidth());

                tabs.getTabs().addFirst(new Tab("Later tab"));
                placement.openWorkspace();
                assertSame(ai, tabs.getTabs().getLast());
                assertSame(ai, tabs.getSelectionModel().getSelectedItem());
                assertEquals(4, tabs.getTabs().size());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void dialogMovesTheOneLiveViewAndRestoresItsPreviousPlacement() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Stage owner = new Stage();
            try {
                TextArea draft = new TextArea("Unsent prompt");
                StackPane view = new StackPane(draft);
                StackPane dock = new StackPane();
                Tab terminal = new Tab("Terminal");
                TabPane tabs = new TabPane(terminal);
                owner.setScene(new Scene(new StackPane(dock, tabs), 700, 500));
                owner.show();
                AiAssistantPlacementController placement = new AiAssistantPlacementController(view, tabs, () -> { });

                placement.attachDock(dock);
                placement.openDialog(owner);
                assertTrue(placement.isDialogOpen());
                assertTrue(dock.getChildren().isEmpty());
                assertSame(view.getScene().getWindow(), draft.getScene().getWindow());
                assertEquals("Unsent prompt", draft.getText());
                placement.openDialog(owner);
                draft.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false));
                assertFalse(placement.isDialogOpen());
                assertSame(dock, view.getParent());

                placement.openWorkspace();
                placement.openDialog(owner);
                assertEquals(java.util.List.of(terminal), tabs.getTabs());
                ((Stage) view.getScene().getWindow()).close();
                assertTrue(placement.isWorkspaceOpen());
                assertSame(view, placement.workspaceTab().orElseThrow().getContent());
                assertEquals("Unsent prompt", draft.getText());
                placement.closeWorkspace();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                owner.close();
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void dialogUsesNativeTitleTwoSessionActionsAndTallerButtonlessContent() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Stage owner = new Stage();
            try {
                owner.setScene(new Scene(new StackPane(), 600, 400));
                owner.setX(120);
                owner.setY(260);
                owner.show();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("AiAssistantView.fxml"),
                        Texts.localizer().bundle());
                loader.setClassLoader(getClass().getClassLoader());
                VBox view = loader.load();
                AiAssistantController controller = loader.getController();
                controller.setDialogPresentation(true);
                AiAssistantDialog dialog = new AiAssistantDialog(owner, () -> { });
                dialog.show(view);
                VBox shell = (VBox) view.getParent();
                assertEquals(1, shell.getChildren().size());
                assertSame(view, shell.getChildren().getFirst());
                assertFalse(controller.placementButton().isVisible());
                assertTrue(controller.sessionList().isVisible());
                assertTrue(controller.newChatButton().isVisible());
                assertTrue(((Stage) view.getScene().getWindow()).isResizable());
                assertEquals(750, view.getScene().getWidth(), 1.0);
                assertEquals(760, view.getScene().getHeight(), 1.0);
                assertEquals(2, controller.promptField().getPrefRowCount());
                assertEquals((controller.promptField().getMinHeight() - 14) * 2 + 14,
                        controller.promptField().getPrefHeight(), 1.0);
                Platform.runLater(() -> {
                    try {
                        var window = view.getScene().getWindow();
                        var screen = javafx.stage.Screen.getScreensForRectangle(
                                owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight()).getFirst().getVisualBounds();
                        var expected = DialogSpacing.centeredPosition(new javafx.geometry.Rectangle2D(
                                owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight()),
                                screen, window.getWidth(), window.getHeight());
                        assertEquals(expected.getX(), window.getX(), 1.0);
                        assertEquals(expected.getY(), window.getY(), 1.0);
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        dialog.close();
                        owner.close();
                        completed.countDown();
                    }
                });
            } catch (Throwable error) {
                failure.set(error);
                owner.close();
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }
}
