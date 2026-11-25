// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BoundedWorkflowExecutorTest {
    private BoundedWorkflowExecutor executor(BoundedWorkflowExecutor.ClosePolicy policy) {
        return new BoundedWorkflowExecutor("workflow-test-", 1, policy, () -> "closed", () -> "full");
    }

    @Test void fullQueueRejectsWithoutExecutingAndCleansSecrets() throws Exception {
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        try (var executor = executor(BoundedWorkflowExecutor.ClosePolicy.DRAIN)) {
            var active = executor.supply(() -> { started.countDown(); release.await(); return 1; });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var queued = executor.supply(() -> 2);
            var cleaned = new AtomicInteger();
            var rejected = executor.supply(() -> fail("rejected task executed"), cleaned::incrementAndGet);
            assertEquals("full", assertThrows(ExecutionException.class,
                    () -> rejected.toCompletableFuture().get(5, TimeUnit.SECONDS)).getCause().getMessage());
            assertEquals(1, cleaned.get());
            release.countDown();
            assertEquals(1, active.toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertEquals(2, queued.toCompletableFuture().get(5, TimeUnit.SECONDS));
        } finally { release.countDown(); }
    }

    @Test void immediateCloseCompletesDiscardedFuturesAndCleansOnce() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var cleanup = new AtomicInteger();
        try (var executor = executor(BoundedWorkflowExecutor.ClosePolicy.CANCEL_PENDING)) {
            var active = executor.supply(() -> { started.countDown(); release.await(); return 1; });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var queued = executor.supply(() -> fail("discarded task executed"), cleanup::incrementAndGet);
            executor.close();
            executor.close();
            assertEquals("closed", assertThrows(ExecutionException.class,
                    () -> queued.toCompletableFuture().get(5, TimeUnit.SECONDS)).getCause().getMessage());
            assertInstanceOf(InterruptedException.class, assertThrows(ExecutionException.class,
                    () -> active.toCompletableFuture().get(5, TimeUnit.SECONDS)).getCause());
            assertEquals(1, cleanup.get());
        } finally { release.countDown(); }
    }

    @Test void gracefulCloseDrainsInOrderAndRejectsNewTasks() throws Exception {
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var order = new AtomicInteger();
        try (var executor = executor(BoundedWorkflowExecutor.ClosePolicy.DRAIN)) {
            var active = executor.supply(() -> { started.countDown(); release.await(); return order.incrementAndGet(); });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var queued = executor.supply(order::incrementAndGet);
            executor.close();
            assertThrows(ExecutionException.class, () -> executor.supply(() -> 3).toCompletableFuture().get());
            release.countDown();
            assertEquals(1, active.toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertEquals(2, queued.toCompletableFuture().get(5, TimeUnit.SECONDS));
        } finally { release.countDown(); }
    }

    @Test void cancelledQueuedTaskReleasesSecretsWithoutRunning() throws Exception {
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var cleaned = new CountDownLatch(1);
        try (var executor = executor(BoundedWorkflowExecutor.ClosePolicy.DRAIN)) {
            executor.supply(() -> { started.countDown(); release.await(); return null; });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var queued = executor.supply(() -> fail("cancelled task executed"), cleaned::countDown);
            queued.toCompletableFuture().cancel(false);
            release.countDown();
            assertTrue(cleaned.await(5, TimeUnit.SECONDS));
        } finally { release.countDown(); }
    }

    @Test void operationFailureRetainsOriginalCause() throws Exception {
        try (var executor = executor(BoundedWorkflowExecutor.ClosePolicy.DRAIN)) {
            var failure = new IOException("failure");
            var future = executor.supply(() -> { throw failure; });
            assertSame(failure, assertThrows(ExecutionException.class,
                    () -> future.toCompletableFuture().get(5, TimeUnit.SECONDS)).getCause());
            assertEquals(7, executor.supply(() -> 7).toCompletableFuture().get(5, TimeUnit.SECONDS));
        }
    }

    @Test void stoppingSignalWaitsForActiveOperationCleanup() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var exited = new AtomicInteger();
        try (var executor = executor(BoundedWorkflowExecutor.ClosePolicy.CANCEL_PENDING)) {
            executor.supply(() -> {
                started.countDown();
                try {
                    while (true) {
                        try { release.await(); break; }
                        catch (InterruptedException ignored) { /* Simulate a driver completing cleanup. */ }
                    }
                } finally { exited.incrementAndGet(); }
                return null;
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            executor.close();
            assertFalse(executor.stopped().toCompletableFuture().isDone());
            release.countDown();
            executor.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, exited.get());
        } finally { release.countDown(); }
    }

    @Test void cleanupFailureDoesNotLeaveFuturePending() throws Exception {
        var executor = executor(BoundedWorkflowExecutor.ClosePolicy.CANCEL_PENDING);
        executor.close();
        var failed = executor.supply(() -> 1, () -> { throw new IllegalStateException("cleanup"); });
        var cause = assertThrows(ExecutionException.class,
                () -> failed.toCompletableFuture().get(5, TimeUnit.SECONDS)).getCause();
        assertEquals("closed", cause.getMessage());
        assertEquals("cleanup", cause.getSuppressed()[0].getMessage());
    }
}
