// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DiagnosticLogPlacementControllerTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    @Test
    void statusToggleClosesAnOpenWorkspaceAndLeavesAClosedWorkspaceForTheOpenPath() throws Exception {
        onFx(() -> {
            StackPane view = new StackPane();
            TabPane tabs = new TabPane();
            AtomicInteger changes = new AtomicInteger();
            DiagnosticLogPlacementController placement =
                    new DiagnosticLogPlacementController(view, tabs, changes::incrementAndGet);

            assertFalse(placement.closeWorkspaceIfOpen());
            placement.openWorkspace();
            assertTrue(placement.isWorkspaceOpen());
            assertEquals(1, tabs.getTabs().size());
            assertEquals(WorkspaceTabGraphics.ICON_SIZE,
                    ((LucideIconView) tabs.getTabs().getFirst().getGraphic()).getPrefWidth());

            assertTrue(placement.closeWorkspaceIfOpen());
            assertFalse(placement.isWorkspaceOpen());
            assertTrue(tabs.getTabs().isEmpty());
            assertEquals(2, changes.get());
        });
    }

    @Test
    void reopeningLogsKeepsTheUsersTabOrder() throws Exception {
        onFx(() -> {
            Tab terminal = new Tab("Terminal");
            TabPane tabs = new TabPane(terminal);
            var placement = new DiagnosticLogPlacementController(new StackPane(), tabs, () -> { });
            placement.openWorkspace();
            Tab log = tabs.getSelectionModel().getSelectedItem();
            assertEquals(java.util.List.of(terminal, log), tabs.getTabs());
            tabs.getTabs().setAll(log, terminal);
            tabs.getSelectionModel().select(terminal);
            placement.openWorkspace();
            assertEquals(java.util.List.of(log, terminal), tabs.getTabs());
            assertEquals(log, tabs.getSelectionModel().getSelectedItem());
        });
    }

    private static void onFx(Runnable action) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() instanceof Exception exception) throw exception;
        if (failure.get() instanceof Error error) throw error;
    }
}
