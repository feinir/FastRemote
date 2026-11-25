// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowThreadPoolTest {
    @Test void closeCompletesQueuedResultsAndRunsCleanupOnce() throws Exception {
        var pool = new WorkflowThreadPool(1,1,1,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),
                Thread.ofPlatform().daemon(true).factory(),new ThreadPoolExecutor.AbortPolicy());
        var started = new CountDownLatch(1);
        var cleaned = new AtomicInteger();
        var running = pool.supply(() -> {
            started.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return 1;
        }, () -> {});
        assertTrue(started.await(2,TimeUnit.SECONDS));
        var queued = pool.supply(() -> { fail("Queued operation ran after close"); return 2; },cleaned::incrementAndGet);
        var transferAccepted = new CompletableFuture<Void>();
        pool.executeCancellable(() -> fail("Queued transfer ran"), () -> transferAccepted.cancel(false));
        pool.shutdownNow();
        pool.shutdownNow();
        pool.stopped().toCompletableFuture().get(2,TimeUnit.SECONDS);
        assertTrue(running.isDone());
        assertTrue(queued.isCompletedExceptionally());
        assertTrue(transferAccepted.isCancelled());
        assertEquals(1,cleaned.get());
    }
    @Test void cleanupFailureDoesNotStrandOtherQueuedResults() throws Exception {
        var pool = new WorkflowThreadPool(1,1,1,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),
                Thread.ofPlatform().daemon(true).factory(),new ThreadPoolExecutor.AbortPolicy());
        var started=new CountDownLatch(1);
        pool.execute(()->{started.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException ignored){Thread.currentThread().interrupt();}});
        assertTrue(started.await(2,TimeUnit.SECONDS));
        var first=pool.supply(()->1,()->{throw new IllegalStateException("cleanup failure");});
        var second=pool.supply(()->2,()->{});
        pool.shutdownNow();pool.stopped().toCompletableFuture().get(2,TimeUnit.SECONDS);
        assertTrue(first.isCompletedExceptionally());assertTrue(second.isCompletedExceptionally());
    }
}
