// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.transfer.TransferConflict;
import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TransferConflictDialogQueueTest {
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
    void nativeAlertIsNonBlockingAndCompletesWhenHidden() throws Exception {
        AtomicReference<CompletableFuture<SftpConflictDecision>> answer = new AtomicReference<>();
        AtomicReference<TransferConflictDialogQueue> prompts = new AtomicReference<>();
        AtomicReference<Stage> owner = new AtomicReference<>();
        onFx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 800, 600));
            stage.show();
            owner.set(stage);
            TransferConflictDialogQueue queue = new TransferConflictDialogQueue(stage);
            prompts.set(queue);
            answer.set(queue.request(new TransferConflict("/source/file", "/target/file", false)));
            assertTrue(queue.isShowing());
            assertFalse(answer.get().isDone());

            Alert alert = queue.activeAlert();
            assertEquals(Modality.NONE, alert.getModality());
            assertTrue(stage.getScene().getRoot().isDisable());
            assertTrue(alert.getDialogPane().getStylesheets().stream()
                    .anyMatch(stylesheet -> stylesheet.endsWith("/main.css")));
            Button overwrite = alert.getButtonTypes().stream()
                    .filter(type -> type.getButtonData() == javafx.scene.control.ButtonBar.ButtonData.YES)
                    .map(type -> (Button) alert.getDialogPane().lookupButton(type))
                    .findFirst().orElseThrow();
            overwrite.fire();
        });

        SftpConflictDecision decision = answer.get().get(5, TimeUnit.SECONDS);
        assertEquals(TransferConflictChoice.OVERWRITE, decision.choice());
        assertFalse(decision.applyToRemaining());
        onFx(() -> {
            assertFalse(prompts.get().isShowing());
            assertFalse(owner.get().getScene().getRoot().isDisable());
            owner.get().close();
        });
    }

    @Test
    void concurrentConflictsUseOneNativeDialogAtATime() throws Exception {
        AtomicReference<CompletableFuture<SftpConflictDecision>> first = new AtomicReference<>();
        AtomicReference<CompletableFuture<SftpConflictDecision>> second = new AtomicReference<>();
        AtomicReference<TransferConflictDialogQueue> prompts = new AtomicReference<>();
        AtomicReference<Stage> owner = new AtomicReference<>();
        onFx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 800, 600));
            stage.show();
            owner.set(stage);
            TransferConflictDialogQueue queue = new TransferConflictDialogQueue(stage);
            prompts.set(queue);
            first.set(queue.request(new TransferConflict("first", "target", false)));
            second.set(queue.request(new TransferConflict("second", "target", false)));
            assertEquals(1, queue.pendingCount());
            closeWith(queue.activeAlert(), ButtonType.CANCEL);
            assertTrue(queue.isShowing());
            assertTrue(stage.getScene().getRoot().isDisable());
            assertEquals(0, queue.pendingCount());
            closeWith(queue.activeAlert(), ButtonType.CANCEL);
        });

        assertEquals(TransferConflictChoice.CANCEL, first.get().get(5, TimeUnit.SECONDS).choice());
        assertEquals(TransferConflictChoice.CANCEL, second.get().get(5, TimeUnit.SECONDS).choice());
        onFx(() -> {
            assertFalse(prompts.get().isShowing());
            assertFalse(owner.get().getScene().getRoot().isDisable());
            owner.get().close();
        });
    }

    @Test
    void automatedConflictTimesOutAndReleasesTheOwner() throws Exception {
        AtomicReference<CompletableFuture<SftpConflictDecision>> answer = new AtomicReference<>();
        AtomicReference<Stage> owner = new AtomicReference<>();
        AtomicReference<TransferConflictDialogQueue> prompts = new AtomicReference<>();
        onFx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 800, 600));
            stage.show();
            owner.set(stage);
            var queue = new TransferConflictDialogQueue(stage);
            prompts.set(queue);
            answer.set(queue.request(new TransferConflict("source", "existing target", false), true));
            assertTrue(queue.isShowing());
            assertFalse(answer.get().isDone());
        });
        try {
            var decision = answer.get().get(6, TimeUnit.SECONDS);
            assertEquals(TransferConflictChoice.CANCEL, decision.choice());
            assertTrue(decision.timedOut());
            onFx(() -> {
                assertFalse(prompts.get().isShowing());
                assertFalse(owner.get().getScene().getRoot().isDisable());
            });
        } finally {
            onFx(() -> {
                prompts.get().close();
                owner.get().close();
            });
        }
    }

    private static void closeWith(Alert alert, ButtonType type) {
        ((Button) alert.getDialogPane().lookupButton(type)).fire();
    }

    private static void onFx(Runnable action) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable problem) {
                failure.set(problem);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
