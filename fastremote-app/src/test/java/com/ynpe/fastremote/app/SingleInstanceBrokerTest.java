// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleInstanceBrokerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void laterInvocationIsAuthenticatedForwardedAndDoesNotBecomePrimary() throws Exception {
        Path target = temporaryDirectory.resolve("target");
        java.nio.file.Files.createDirectory(target);
        LaunchRequest initial = LaunchRequest.parse(List.of());
        try (SingleInstanceBroker primary = SingleInstanceBroker.startOrForward(
                temporaryDirectory.resolve("state"), initial)) {
            assertNotNull(primary);
            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<LaunchRequest> forwarded = new AtomicReference<>();
            primary.installConsumer(request -> {
                forwarded.set(request);
                received.countDown();
            });

            SingleInstanceBroker secondary = SingleInstanceBroker.startOrForward(
                    temporaryDirectory.resolve("state"), LaunchRequest.parse(List.of(target.toString())));

            assertNull(secondary);
            assertTrue(received.await(3, TimeUnit.SECONDS));
            assertEquals(target.toAbsolutePath().normalize(),
                    forwarded.get().initialDirectory().orElseThrow());
            assertTrue(forwarded.get().openLocalTerminal());
        }
    }

    @Test
    void requestsReceivedDuringStartupRemainBoundedAndDrainWhenConsumerIsInstalled() throws Exception {
        Path state = temporaryDirectory.resolve("queued-state");
        try (SingleInstanceBroker primary = SingleInstanceBroker.startOrForward(
                state, LaunchRequest.parse(List.of()))) {
            assertNotNull(primary);
            assertNull(SingleInstanceBroker.startOrForward(state, LaunchRequest.parse(List.of("-n"))));
            AtomicReference<LaunchRequest> forwarded = new AtomicReference<>();
            primary.installConsumer(forwarded::set);
            assertTrue(forwarded.get().forceNewTerminal());
        }

        try (SingleInstanceBroker replacement = SingleInstanceBroker.startOrForward(
                state, LaunchRequest.parse(List.of()))) {
            assertNotNull(replacement, "closing the primary must release the process lock");
        }
    }

    @Test
    void laterInvocationForwardsEditorFilesToTheRunningInstance() throws Exception {
        Path file = java.nio.file.Files.writeString(temporaryDirectory.resolve("open.txt"), "content");
        Path state = temporaryDirectory.resolve("file-state");
        try (SingleInstanceBroker primary = SingleInstanceBroker.startOrForward(
                state, LaunchRequest.parse(List.of()))) {
            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<LaunchRequest> forwarded = new AtomicReference<>();
            primary.installConsumer(request -> {
                forwarded.set(request);
                received.countDown();
            });

            assertNull(SingleInstanceBroker.startOrForward(
                    state, LaunchRequest.parse(List.of("-f", file.toString()))));

            assertTrue(received.await(3, TimeUnit.SECONDS));
            assertEquals(List.of(file.toAbsolutePath().normalize()), forwarded.get().files());
            assertTrue(forwarded.get().opensWorkspace());
        }
    }
}
