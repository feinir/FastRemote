// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Signals when workers have stopped using shared application resources. */
final class WorkflowThreadPool extends ThreadPoolExecutor {
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private final java.util.concurrent.atomic.AtomicInteger cancellations = new java.util.concurrent.atomic.AtomicInteger();

    WorkflowThreadPool(int core, int maximum, long keepAlive, TimeUnit unit,
            BlockingQueue<Runnable> queue, ThreadFactory factory, RejectedExecutionHandler rejected) {
        super(core, maximum, keepAlive, unit, queue, factory, rejected);
    }

    CompletionStage<Void> stopped() { return stopped.minimalCompletionStage(); }

    <T> CompletableFuture<T> supply(java.util.function.Supplier<T> operation, Runnable abandonedCleanup) {
        var result = new CompletableFuture<T>();
        executeCancellable(() -> {
            if (result.isCancelled()) { abandonedCleanup.run(); return; }
            try { result.complete(operation.get()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        }, () -> {
            var failure = new java.util.concurrent.CancellationException("Workflow closed before execution");
            try { abandonedCleanup.run(); }
            catch (Throwable cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            finally { result.completeExceptionally(failure); }
        });
        return result;
    }

    void executeCancellable(Runnable operation, Runnable abandoned) {
        execute(new CancellableTask(operation, abandoned));
    }

    @Override public java.util.List<Runnable> shutdownNow() {
        cancellations.incrementAndGet();
        try {
            var pending = super.shutdownNow();
            for (Runnable task : pending) {
                if (task instanceof CancellableTask cancellable) cancellable.abandoned().run();
            }
            return pending;
        } finally {
            if (cancellations.decrementAndGet() == 0 && isTerminated()) stopped.complete(null);
        }
    }

    private record CancellableTask(Runnable operation, Runnable abandoned) implements Runnable {
        @Override public void run() { operation.run(); }
    }

    @Override protected void terminated() {
        try { super.terminated(); }
        finally { if (cancellations.get() == 0) stopped.complete(null); }
    }
}
