// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FilePanelMoveSupportTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void dropCapturesSourcesAndWaitsForDragDoneThenContinuesAfterIndividualFailure() throws Exception {
        Fixture f = new Fixture();
        List<String> sources = new ArrayList<>(List.of("bad", "good"));
        onFx(() -> {
            assertTrue(f.support.drop(sources, "/target"));
            assertTrue(f.operations.isEmpty());
            sources.clear();
            f.pending.get().run();
            assertFalse(f.support.accepts(List.of("another"), "/other"));
        });
        assertTrue(f.refreshed.await(5, TimeUnit.SECONDS));
        onFx(() -> {
            assertEquals(List.of("bad->/target", "good->/target"), f.operations);
            assertEquals(List.of("bad"), f.failures);
            assertEquals(List.of("good"), f.moved);
            assertTrue(f.support.accepts(List.of("another"), "/other"));
        });
    }

    @Test
    void closingPanelBeforeDragDoneDiscardsDrop() throws Exception {
        Fixture f = new Fixture();
        onFx(() -> {
            assertTrue(f.support.drop(List.of("good"), "/target"));
            f.active.set(false);
            f.pending.get().run();
            assertTrue(f.operations.isEmpty());
        });
    }

    @Test
    void closeDuringMoveStopsRemainingItemsAndDoesNotRefreshDisposedView() throws Exception {
        AtomicBoolean active = new AtomicBoolean(true);
        CompletableFuture<Void> first = new CompletableFuture<>();
        AtomicReference<Runnable> pending = new AtomicReference<>();
        List<String> operations = new ArrayList<>();
        AtomicInteger updates = new AtomicInteger();
        var support = new FilePanelMoveSupport<String, String>(List::of, (entry, target) -> true,
                (entry, target) -> { operations.add(entry); return first; }, pending::set, active::get,
                (entry, error) -> updates.incrementAndGet(), (entry, target) -> updates.incrementAndGet(),
                updates::incrementAndGet);
        onFx(() -> {
            support.drop(List.of("first", "second"), "/target");
            pending.get().run();
            active.set(false);
            first.complete(null);
        });
        onFx(() -> {
            assertEquals(List.of("first"), operations);
            assertEquals(0, updates.get());
        });
    }

    @Test
    void rejectsInvalidTargetsWithoutSchedulingWork() throws Exception {
        Fixture f = new Fixture();
        onFx(() -> {
            assertFalse(f.support.drop(List.of("good"), null));
            assertFalse(f.support.drop(List.of(), "/target"));
            assertNull(f.pending.get());
        });
    }

    @Test
    void otherPayloadRetainsExistingTransferHandlers() throws Exception {
        onFx(() -> {
            Fixture f = new Fixture();
            Pane node = new Pane();
            AtomicInteger over = new AtomicInteger();
            AtomicInteger drop = new AtomicInteger();
            node.setOnDragOver(event -> over.incrementAndGet());
            node.setOnDragDropped(event -> drop.incrementAndGet());
            f.support.installTarget(node, () -> "/target");
            node.getOnDragOver().handle(event(javafx.scene.input.DragEvent.DRAG_OVER, node));
            node.getOnDragDropped().handle(event(javafx.scene.input.DragEvent.DRAG_DROPPED, node));
            assertEquals(1, over.get());
            assertEquals(1, drop.get());
        });
    }

    private static javafx.scene.input.DragEvent event(
            javafx.event.EventType<javafx.scene.input.DragEvent> type, Pane node) {
        return new javafx.scene.input.DragEvent(type, null, 1, 1, 1, 1,
                javafx.scene.input.TransferMode.COPY, node, node, null);
    }

    private static void onFx(Runnable action) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { action.run(); done.complete(null); }
            catch (Throwable error) { done.completeExceptionally(error); }
        });
        done.get(5, TimeUnit.SECONDS);
    }

    private static final class Fixture {
        final List<String> operations = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        final List<String> moved = new ArrayList<>();
        final AtomicReference<Runnable> pending = new AtomicReference<>();
        final AtomicBoolean active = new AtomicBoolean(true);
        final CountDownLatch refreshed = new CountDownLatch(1);
        final FilePanelMoveSupport<String, String> support = new FilePanelMoveSupport<>(List::of,
                (entry, target) -> true,
                (entry, target) -> {
                    operations.add(entry + "->" + target);
                    return entry.equals("bad") ? CompletableFuture.failedFuture(new IOException("denied"))
                            : CompletableFuture.completedFuture(null);
                }, pending::set, active::get, (entry, error) -> failures.add(entry),
                (entry, target) -> moved.add(entry), refreshed::countDown);
    }
}
