// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Owns startup rollback and keeps shared storage alive until its consumers stop. */
final class RuntimeResources implements AutoCloseable {
    private final Deque<AutoCloseable> services = new ArrayDeque<>();
    private final Deque<AutoCloseable> shared = new ArrayDeque<>();
    private final List<CompletableFuture<Void>> workers = new ArrayList<>();
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private boolean closing;

    synchronized <T extends AutoCloseable> T own(T service) {
        requireOpen();
        services.push(service);
        return service;
    }

    synchronized <T extends AutoCloseable> T share(T resource) {
        requireOpen();
        shared.push(resource);
        return resource;
    }

    synchronized void waitFor(CompletionStage<Void> worker) {
        requireOpen();
        workers.add(worker.toCompletableFuture());
    }

    private void requireOpen() {
        if (closing) throw new IllegalStateException("Runtime resources are closing");
    }

    CompletionStage<Void> stopped() { return stopped.minimalCompletionStage(); }

    @Override public void close() {
        Deque<AutoCloseable> servicesToClose;
        Deque<AutoCloseable> sharedToClose;
        CompletableFuture<?>[] workersToStop;
        synchronized (this) {
            if (closing) return;
            closing = true;
            servicesToClose = new ArrayDeque<>(services);
            sharedToClose = new ArrayDeque<>(shared);
            workersToStop = workers.toArray(CompletableFuture[]::new);
            services.clear();
            shared.clear();
            workers.clear();
        }
        Throwable failure = closeAll(servicesToClose, null);
        CompletableFuture.allOf(workersToStop).whenComplete((ignored, workerFailure) -> {
            Throwable combined = combine(failure, workerFailure);
            combined = closeAll(sharedToClose, combined);
            if (combined == null) stopped.complete(null);
            else stopped.completeExceptionally(combined);
        });
    }

    private static Throwable closeAll(Deque<AutoCloseable> resources, Throwable failure) {
        while (!resources.isEmpty()) {
            try { resources.pop().close(); }
            catch (Throwable problem) { failure = combine(failure, problem); }
        }
        return failure;
    }

    private static Throwable combine(Throwable first, Throwable next) {
        if (first == null) return next;
        if (next != null && next != first) first.addSuppressed(next);
        return first;
    }
}
