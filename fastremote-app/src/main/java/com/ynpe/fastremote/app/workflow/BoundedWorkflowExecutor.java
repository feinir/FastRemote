// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Serial, bounded workflow execution with an explicit disposition for abandoned work. */
final class BoundedWorkflowExecutor implements AutoCloseable {
    enum ClosePolicy { DRAIN, CANCEL_PENDING }

    @FunctionalInterface
    interface CheckedSupplier<T> { T get() throws Exception; }

    private final WorkflowThreadPool executor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ClosePolicy closePolicy;
    private final Supplier<String> closedMessage;
    private final Supplier<String> fullMessage;

    BoundedWorkflowExecutor(String threadPrefix, int capacity, ClosePolicy closePolicy,
            Supplier<String> closedMessage, Supplier<String> fullMessage) {
        this.closePolicy = closePolicy;
        this.closedMessage = closedMessage;
        this.fullMessage = fullMessage;
        executor = new WorkflowThreadPool(1, 1, 15L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity),
                Thread.ofPlatform().name(threadPrefix, 0).daemon(false).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
    }

    <T> CompletionStage<T> supply(CheckedSupplier<T> operation) {
        return supply(operation, () -> {});
    }

    <T> CompletionStage<T> supply(CheckedSupplier<T> operation, Runnable abandonedCleanup) {
        var task = new Task<>(operation, abandonedCleanup);
        if (closed.get()) {
            task.abandon(new IllegalStateException(closedMessage.get()));
        } else {
            try {
                executor.execute(task);
            } catch (RejectedExecutionException rejected) {
                task.abandon(new IllegalStateException(fullMessage.get(), rejected));
            }
        }
        return task.result;
    }

    CompletionStage<Void> stopped() { return executor.stopped(); }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (closePolicy == ClosePolicy.DRAIN) {
            executor.shutdown();
        } else {
            // shutdownNow returns tasks that never started; their secret buffers and futures
            // still belong to us. Running operations perform their own cleanup in finally.
            for (Runnable pending : executor.shutdownNow()) {
                ((Task<?>) pending).abandon(new IllegalStateException(closedMessage.get()));
            }
        }
    }

    private static final class Task<T> implements Runnable {
        private final CheckedSupplier<T> operation;
        private final Runnable abandonedCleanup;
        private final CompletableFuture<T> result = new CompletableFuture<>();

        Task(CheckedSupplier<T> operation, Runnable abandonedCleanup) {
            this.operation = operation;
            this.abandonedCleanup = abandonedCleanup;
        }

        @Override public void run() {
            if (result.isCancelled()) {
                abandon(new java.util.concurrent.CancellationException());
                return;
            }
            try {
                result.complete(operation.get());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        }

        void abandon(Throwable failure) {
            try {
                abandonedCleanup.run();
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            } finally {
                result.completeExceptionally(failure);
            }
        }
    }
}
