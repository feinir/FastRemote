// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeOpenRequestBridgeTest {
    @TempDir Path temporaryDirectory;

    @Test
    void openFileEventRequestsANewEditorWithoutRequiringN() throws Exception {
        Path file = Files.writeString(temporaryDirectory.resolve("sample.txt"), "content");

        LaunchRequest request = NativeOpenRequestBridge.requestForFiles(List.of(file.toFile()));

        assertFalse(request.openLocalTerminal());
        assertTrue(request.opensWorkspace());
        assertFalse(request.forceNewTerminal());
        assertEquals(List.of(file.toAbsolutePath().normalize()), request.files());
    }

    @Test
    void eventQueuedBeforeApplicationReadinessMergesWithCommandLineN() {
        NativeOpenRequestBridge bridge = new NativeOpenRequestBridge();
        bridge.acceptFiles(List.of(temporaryDirectory.toFile()));

        LaunchRequest merged = bridge.drainInitialRequest(LaunchRequest.parse(List.of("-n")));

        assertTrue(merged.forceNewTerminal());
        assertEquals(temporaryDirectory.toAbsolutePath().normalize(),
                merged.initialDirectory().orElseThrow());
    }

    @Test
    void eventsAfterReadinessAreDeliveredDirectly() {
        NativeOpenRequestBridge bridge = new NativeOpenRequestBridge();
        AtomicReference<LaunchRequest> delivered = new AtomicReference<>();
        bridge.installConsumer(delivered::set);

        bridge.acceptFiles(List.of(new File(temporaryDirectory.toString())));

        assertEquals(temporaryDirectory.toAbsolutePath().normalize(),
                delivered.get().initialDirectory().orElseThrow());
    }

    @Test
    void clearingConsumersReturnsLaterEventsToTheStartupQueue() {
        NativeOpenRequestBridge bridge = new NativeOpenRequestBridge();
        AtomicReference<LaunchRequest> delivered = new AtomicReference<>();
        bridge.installConsumer(delivered::set);
        bridge.clearConsumers();

        bridge.acceptFiles(List.of(temporaryDirectory.toFile()));

        assertNull(delivered.get());
        assertEquals(temporaryDirectory.toAbsolutePath().normalize(),
                bridge.drainInitialRequest(LaunchRequest.parse(List.of()))
                        .initialDirectory().orElseThrow());
    }

    @Test
    void dockQuitUsesTheApplicationLifecycleHandler() {
        NativeOpenRequestBridge bridge = new NativeOpenRequestBridge();
        AtomicBoolean requested = new AtomicBoolean();
        bridge.installQuitConsumer(() -> requested.set(true));

        assertTrue(bridge.notifyQuit());
        assertTrue(requested.get());

        bridge.clearConsumers();
        assertFalse(bridge.notifyQuit());
    }

    @Test
    void macActivationIsAlsoTreatedAsAReopenSignal() {
        AtomicInteger reopened = new AtomicInteger();
        NativeOpenRequestBridge.ForwardingGlassEventHandler handler =
                new NativeOpenRequestBridge.ForwardingGlassEventHandler(
                        null, ignored -> { }, reopened::incrementAndGet, () -> false);

        handler.handleDidBecomeActiveAction(null, 1L);

        assertEquals(1, reopened.get());
    }
}
