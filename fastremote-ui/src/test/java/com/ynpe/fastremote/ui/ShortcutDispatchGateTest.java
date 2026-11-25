// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShortcutDispatchGateTest {
    @Test
    void collapsesTheSameActionUntilTheCurrentEventCycleReleasesIt() {
        ShortcutDispatchGate gate = new ShortcutDispatchGate();

        assertTrue(gate.begin(ShortcutAction.OPEN_HOST_LIBRARY));
        assertFalse(gate.begin(ShortcutAction.OPEN_HOST_LIBRARY));
        assertTrue(gate.begin(ShortcutAction.OPEN_SETTINGS));

        gate.release(ShortcutAction.OPEN_HOST_LIBRARY);

        assertTrue(gate.begin(ShortcutAction.OPEN_HOST_LIBRARY));
    }
}
