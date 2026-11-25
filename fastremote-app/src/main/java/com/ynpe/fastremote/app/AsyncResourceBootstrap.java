// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/** Starts one closeable process resource early and transfers its ownership exactly once. */
final class AsyncResourceBootstrap<T extends AutoCloseable> implements AutoCloseable {
    @FunctionalInterface
    interface Factory<T> {
        T create() throws Exception;
    }

    private final CompletableFuture<T> result;
    private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);

    private AsyncResourceBootstrap(String threadName, Factory<T> factory) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
        result = CompletableFuture.supplyAsync(() -> {
            try {
                return factory.create();
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }, executor);
        result.whenComplete((ignored, failure) -> executor.shutdown());
    }

    static <T extends AutoCloseable> AsyncResourceBootstrap<T> start(
            String threadName, Factory<T> factory) {
        return new AsyncResourceBootstrap<>(Objects.requireNonNull(threadName, "threadName"),
                Objects.requireNonNull(factory, "factory"));
    }

    T claim() throws IOException {
        if (!state.compareAndSet(State.PENDING, State.CLAIMED)) {
            throw new IllegalStateException("Startup resource has already been claimed or closed");
        }
        try {
            return result.get();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while initializing application services", failure);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof CompletionException completion && completion.getCause() != null) {
                cause = completion.getCause();
            }
            if (cause instanceof IOException ioFailure) throw ioFailure;
            if (cause instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            throw new IOException("Unable to initialize application services", cause);
        }
    }

    @Override
    public void close() {
        if (!state.compareAndSet(State.PENDING, State.CLOSED)) return;
        result.whenComplete((resource, failure) -> {
            if (resource == null) return;
            try {
                resource.close();
            } catch (Exception closeFailure) {
                System.getLogger(AsyncResourceBootstrap.class.getName()).log(
                        System.Logger.Level.WARNING, "Unable to close unclaimed startup resource", closeFailure);
            }
        });
    }

    private enum State { PENDING, CLAIMED, CLOSED }
}
