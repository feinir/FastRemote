// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.ynpe.fastremote.domain.transfer.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorkspaceObservationsTest {
    @BeforeAll static void startToolkit() throws Exception {
        var latch = new CountDownLatch(1);
        try { Platform.startup(latch::countDown); } catch (IllegalStateException started) { latch.countDown(); }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
    private static <T> T fx(Callable<T> callable) throws Exception {
        var task = new FutureTask<T>(callable); Platform.runLater(task); return task.get(5, TimeUnit.SECONDS);
    }
    @Test void queuedTransferIsNotSuccessAndSkippedFilesRemainVisible() throws Exception {
        var pending = new CompletableFuture<Void>();
        var activity = new AtomicReference<TransferActivityListener>();
        RemoteFilesHandle files = (RemoteFilesHandle) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{RemoteFilesHandle.class}, (proxy, method, args) -> {
                    if (method.getName().equals("uploadTree")) {
                        activity.set((TransferActivityListener) args[5]); return pending;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        var registry = new WorkspaceObservations();
        var queue = new SftpTransferQueue(files, () -> 1, conflict -> new SftpConflictDecision(TransferConflictChoice.SKIP, false));
        var receipt = fx(() -> {
            var jobs = queue.upload(List.of(new LocalFileEntry("input", Path.of("/tmp/input"), false, false, 4, Optional.of(Instant.EPOCH))), "/remote");
            return registry.transfers("pane-a", "generation-a", jobs);
        });
        assertEquals("RUNNING", fx(() -> registry.read(receipt.operationId(), 0, 65536)).state());
        assertTrue(fx(() -> registry.list("pane-b")).isEmpty());
        activity.get().completed("/tmp/input", "/remote/input", "SKIPPED", "Conflict policy");
        pending.complete(null);
        var result = fx(() -> registry.read(receipt.operationId(), 0, 65536));
        assertEquals("PARTIAL", result.state());
        assertTrue(result.files().stream().anyMatch(item -> item.state().equals("SKIPPED")));
        fx(() -> { queue.close(); return null; });
    }
    @Test void mutationFailureAndHistoryEvictionAreExplicit() throws Exception {
        var registry = new WorkspaceObservations();
        var pending = new CompletableFuture<Void>();
        var receipt = fx(() -> registry.mutation("one", "generation", "/old", "/new", pending));
        assertEquals("RUNNING", fx(() -> registry.read(receipt.operationId(), 0, 1000)).state());
        pending.completeExceptionally(new java.io.IOException("remote rejected"));
        assertEquals("FAILED", fx(() -> registry.read(receipt.operationId(), 0, 1000)).state());
        fx(() -> {
            for (int i=0; i<1024; i++) registry.mutation("one", "generation", "create", "/new", CompletableFuture.completedFuture(null));
            assertThrows(IllegalArgumentException.class, () -> registry.read(receipt.operationId(), 0, 1000)); return null;
        });
    }
    @Test void closedQueueCannotClaimThatAnUploadWasQueued() throws Exception {
        RemoteFilesHandle files = (RemoteFilesHandle) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{RemoteFilesHandle.class}, (p, m, a) -> CompletableFuture.completedFuture(null));
        fx(() -> {
            var queue = new SftpTransferQueue(files, () -> 1, c -> new SftpConflictDecision(TransferConflictChoice.SKIP, false));
            queue.close();
            assertThrows(IllegalStateException.class, () -> queue.upload(List.of(
                    new LocalFileEntry("a", Path.of("/tmp/a"), false, false, 1, Optional.of(Instant.EPOCH))), "/target")); return null;
        });
    }
}
