// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ActivationHistoryTest {
    @Test
    void closingCurrentItemReturnsMostRecentlyActiveRemainingItem() {
        ActivationHistory<String> history = new ActivationHistory<>();
        history.activated("one");
        history.activated("two");
        history.activated("three");
        history.activated("one");

        assertEquals("three", history.previous("one", List.of("one", "two", "three")).orElseThrow());
        history.removed("three");
        assertEquals("two", history.previous("one", List.of("one", "two")).orElseThrow());
        history.removed("two");
        assertTrue(history.previous("one", List.of("one")).isEmpty());
    }
}
