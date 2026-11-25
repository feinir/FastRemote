// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RuntimeResourcesTest {
    @Test void concurrentCloseFromAServiceDoesNotDeadlock() throws Exception {
        var resources = new RuntimeResources();
        resources.own(() -> {
            var concurrentClose = CompletableFuture.runAsync(resources::close);
            concurrentClose.get(5, TimeUnit.SECONDS);
        });
        resources.close();
        resources.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test void closesServicesInReverseOrderAndSharedResourcesAfterWorkers() throws Exception {
        var calls = new ArrayList<String>();
        var worker = new CompletableFuture<Void>();
        var resources = new RuntimeResources();
        resources.share(() -> calls.add("database"));
        resources.share(() -> calls.add("vault"));
        resources.own(() -> calls.add("catalog"));
        resources.own(() -> calls.add("assistant"));
        resources.waitFor(worker);
        resources.close();
        assertEquals(List.of("assistant", "catalog"), calls);
        assertFalse(resources.stopped().toCompletableFuture().isDone());
        worker.complete(null);
        resources.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(List.of("assistant", "catalog", "vault", "database"), calls);
        resources.close();
        assertEquals(4, calls.size());
    }

    @Test void partiallyConstructedRuntimeReleasesEverythingAlreadyRegistered() throws Exception {
        var calls = new ArrayList<String>();
        var resources = new RuntimeResources();
        assertThrows(IOException.class, () -> {
            try (resources) {
                resources.share(() -> calls.add("license"));
                resources.share(() -> calls.add("database"));
                resources.own(() -> calls.add("connection"));
                throw new IOException("constructor failure");
            }
        });
        resources.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(List.of("connection", "database", "license"), calls);
    }

    @Test void closeFailureDoesNotPreventRemainingCleanup() {
        var calls = new ArrayList<String>();
        var resources = new RuntimeResources();
        resources.share(() -> calls.add("database"));
        resources.own(() -> calls.add("catalog"));
        var first = new IOException("first");
        resources.own(() -> { throw first; });
        resources.share(() -> { throw new IOException("second"); });
        resources.close();
        var cause = assertThrows(ExecutionException.class,
                () -> resources.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS)).getCause();
        assertSame(first, cause);
        assertEquals("second", cause.getSuppressed()[0].getMessage());
        assertEquals(List.of("catalog", "database"), calls);
    }

    @Test void waitsForEveryConsumerEvenIfOneReportsFailure() throws Exception {
        var one = new CompletableFuture<Void>();
        var two = new CompletableFuture<Void>();
        var closed = new CompletableFuture<Void>();
        var resources = new RuntimeResources();
        resources.share(() -> closed.complete(null));
        resources.waitFor(one);
        resources.waitFor(two);
        resources.close();
        one.completeExceptionally(new IllegalStateException("worker"));
        assertFalse(closed.isDone());
        two.complete(null);
        closed.get(5, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> resources.stopped().toCompletableFuture().get());
    }

    @Test void registrationAfterShutdownIsRejected() {
        var resources = new RuntimeResources();
        resources.close();
        assertThrows(IllegalStateException.class, () -> resources.own(() -> {}));
        assertThrows(IllegalStateException.class, () -> resources.share(() -> {}));
        assertThrows(IllegalStateException.class, () -> resources.waitFor(new CompletableFuture<>()));
    }
}
