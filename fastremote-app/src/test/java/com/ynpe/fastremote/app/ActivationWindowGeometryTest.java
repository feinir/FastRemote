// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

final class ActivationWindowGeometryTest {
    @Test
    void derivesNativeDecorationWithoutSubtractingItFromContentSize() {
        assertEquals(16.0, FastRemoteApplication.nativeDecorationExtent(736.0, 720.0));
        assertEquals(0.0, FastRemoteApplication.nativeDecorationExtent(700.0, 720.0));
    }

    @Test
    void activationWindowsUseFixedGeometry() {
        assertFalse(FastRemoteApplication.activationWindowResizable());
        assertEquals(540.0, FastRemoteApplication.ONLINE_ACTIVATION_WIDTH);
        assertEquals(380.0, FastRemoteApplication.ONLINE_ACTIVATION_HEIGHT);
        assertEquals(640.0, FastRemoteApplication.OFFLINE_ACTIVATION_WIDTH);
        assertEquals(700.0, FastRemoteApplication.OFFLINE_ACTIVATION_HEIGHT);
    }
}
