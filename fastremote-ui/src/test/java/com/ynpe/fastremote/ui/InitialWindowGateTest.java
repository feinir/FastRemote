// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InitialWindowGateTest {
    @Test
    void doesNotReleaseTheWindowBeforePersistedLayoutIsApplied() {
        InitialWindowGate gate = new InitialWindowGate();
        AtomicInteger shows = new AtomicInteger();
        gate.ready().thenRun(shows::incrementAndGet);

        assertEquals(0, shows.get());
        gate.markReady();
        gate.markReady();

        assertEquals(1, shows.get());
    }
}
