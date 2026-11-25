// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WindowIconPolicyTest {
    @Test
    void windowsStagesUseTheBundledProductLogo() {
        assertTrue(FastRemoteApplication.usesProductWindowIcon("Windows 11"));
        assertTrue(FastRemoteApplication.usesProductWindowIcon("windows server 2025"));
        assertFalse(FastRemoteApplication.usesProductWindowIcon("Mac OS X"));
        assertFalse(FastRemoteApplication.usesProductWindowIcon("Linux"));
        assertNotNull(FastRemoteApplication.class.getResource(
                FastRemoteApplication.PRODUCT_WINDOW_ICON));
    }
}
