// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.ynpe.fastremote.domain.ai.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AiPromptQueueTest {
    @Test void restoresPausedAndNeverAutomaticallyRetriesUncertainSteer() {
        var queue = new AiPromptQueue(); UUID id = UUID.randomUUID();
        queue.restore(List.of(new AiPendingPrompt(id, "Stop deleting", List.of(), true)));
        assertTrue(queue.paused); assertTrue(queue.next().isEmpty());
        queue.paused = false; assertTrue(queue.next().isEmpty());
        queue.edit(id, "List only"); assertEquals("List only", queue.next().orElseThrow().text());
    }
    @Test void completionConsumesOnlyAcceptedInputsAndKeepsQueueOnFailure() {
        var queue = new AiPromptQueue();
        var first = queue.add("Queued", List.of()); var second = queue.add("Steer", List.of());
        queue.state(second.id(), AiPromptQueue.State.ACCEPTED); queue.finish(true);
        assertEquals(List.of(first), queue.entries()); assertTrue(queue.paused); assertTrue(queue.next().isEmpty());
        queue.paused = false; assertEquals(first, queue.next().orElseThrow());
    }
    @Test void pendingNativeDeliveryBecomesUncertainAtTheFinishBoundary() {
        var queue = new AiPromptQueue(); var entry = queue.add("Update", List.of());
        queue.state(entry.id(), AiPromptQueue.State.WAITING); queue.finish(false);
        assertEquals(AiPromptQueue.State.UNCERTAIN, queue.entries().getFirst().state());
        assertTrue(queue.paused);
    }
}
