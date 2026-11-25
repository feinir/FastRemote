// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class AiTurnInboxTest {
    @Test void finishingAndAcceptingCannotLoseAnInput() throws Exception {
        for (int n = 0; n < 300; n++) {
            var inbox = new AiTurnInbox();
            var input = new AiSteeringInput(UUID.randomUUID(), "Continue", List.of());
            var gate = new CountDownLatch(1);
            var accepted = CompletableFuture.supplyAsync(() -> { await(gate); return inbox.offer(input); });
            var finished = CompletableFuture.supplyAsync(() -> { await(gate); return inbox.finishIfEmpty(); });
            gate.countDown();
            assertNotEquals(accepted.get(), finished.get());
            assertEquals(accepted.get() ? List.of(input) : List.of(), inbox.drain());
        }
    }
    @Test void duplicateDeliveryIsIdempotentAndQueueIsBounded() {
        var inbox = new AiTurnInbox();
        var input = new AiSteeringInput(UUID.randomUUID(), "One", List.of());
        assertTrue(inbox.offer(input)); assertTrue(inbox.offer(input));
        assertEquals(List.of(input), inbox.drain());
        assertTrue(inbox.offer(input)); assertTrue(inbox.drain().isEmpty());
        for (int i = 0; i < 8; i++) assertTrue(inbox.offer(new AiSteeringInput(UUID.randomUUID(), "More", List.of())));
        assertThrows(IllegalStateException.class, () -> inbox.offer(new AiSteeringInput(UUID.randomUUID(), "Too many", List.of())));
        inbox.close(); assertFalse(inbox.offer(new AiSteeringInput(UUID.randomUUID(), "Closed", List.of())));
    }
    private static void await(CountDownLatch gate) {
        try { gate.await(); } catch (InterruptedException error) { throw new AssertionError(error); }
    }
}
