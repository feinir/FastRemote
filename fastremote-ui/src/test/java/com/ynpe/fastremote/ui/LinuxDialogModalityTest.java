// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LinuxDialogModalityTest {
    @BeforeAll static void start() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException running) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
    }

    @Test void nestedInputBlocksRestoreIndependentlyWithoutChangingOwnerGeometryOrDisabledState() throws Exception {
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            Stage owner = new Stage(); Stage child = new Stage();
            try {
                var root = new StackPane(); owner.setScene(new Scene(root, 650, 450));
                child.initOwner(owner); child.setScene(new Scene(new StackPane(), 200, 100));
                owner.show(); child.show();
                var keys = new AtomicInteger();
                root.addEventHandler(KeyEvent.KEY_PRESSED, ignored -> keys.incrementAndGet());
                double width = owner.getWidth(); double height = owner.getHeight();
                Runnable first = LinuxDialogModality.block(owner, Modality.WINDOW_MODAL, child);
                Runnable second = LinuxDialogModality.block(owner, Modality.APPLICATION_MODAL, child);
                fire(root); assertEquals(0, keys.get());
                first.run(); fire(root); assertEquals(0, keys.get());
                second.run(); fire(root); assertEquals(1, keys.get());
                assertFalse(root.isDisable());
                assertEquals(width, owner.getWidth()); assertEquals(height, owner.getHeight());
                root.setDisable(true);
                var restore = LinuxDialogModality.block(owner, Modality.WINDOW_MODAL, child);
                restore.run(); assertTrue(root.isDisable());
                done.complete(null);
            } catch (Throwable e) { done.completeExceptionally(e); }
            finally { child.hide(); owner.hide(); }
        });
        done.get(10, TimeUnit.SECONDS);
    }
    @Test void modalDialogCanBeClosedAndReopenedWithInputBlockingRestored() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(LocalizedTextInputContextMenus.isLinux());
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            Stage owner = new Stage();
            var dialog = new javafx.scene.control.Dialog<Void>();
            try {
                var root = new StackPane(); owner.setScene(new Scene(root, 650, 450)); owner.show();
                var keys = new AtomicInteger();
                root.addEventHandler(KeyEvent.KEY_PRESSED, ignored -> keys.incrementAndGet());
                dialog.initOwner(owner);
                dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
                LinuxDialogModality.prepare(dialog);
                for (int i = 0; i < 2; i++) {
                    dialog.show();
                    assertTrue(dialog.isShowing());
                    assertEquals(Modality.NONE, dialog.getModality());
                    fire(root); assertEquals(i, keys.get());
                    dialog.close();
                    fire(root); assertEquals(i + 1, keys.get());
                }
                done.complete(null);
            } catch (Throwable e) { done.completeExceptionally(e); }
            finally { dialog.close(); owner.hide(); }
        });
        done.get(10, TimeUnit.SECONDS);
    }
    private static void fire(StackPane root) {
        root.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.A, false, false, false, false));
    }
}
