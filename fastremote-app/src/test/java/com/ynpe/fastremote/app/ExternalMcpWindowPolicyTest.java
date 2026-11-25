// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ExternalMcpWindowPolicyTest {
    @Test
    void createsMainWindowOnlyWhenNoWindowExists() {
        assertTrue(FastRemoteApplication.shouldCreateMainWindowForExternalMcpRequest(
                FastRemoteApplication.WindowState.CLOSED));
        assertFalse(FastRemoteApplication.shouldCreateMainWindowForExternalMcpRequest(
                FastRemoteApplication.WindowState.OPENING));
        assertFalse(FastRemoteApplication.shouldCreateMainWindowForExternalMcpRequest(
                FastRemoteApplication.WindowState.OPEN));
        assertFalse(FastRemoteApplication.shouldCreateMainWindowForExternalMcpRequest(
                FastRemoteApplication.WindowState.CLOSING));
        assertFalse(FastRemoteApplication.shouldCreateMainWindowForExternalMcpRequest(
                FastRemoteApplication.WindowState.EXITING));
    }

    @Test
    void directoryRequestReusesOnlyAWindowThatIsStillOpening() {
        assertTrue(FastRemoteApplication.shouldReuseOpeningWindowForLaunchRequest(
                FastRemoteApplication.WindowState.OPENING, true));
        assertFalse(FastRemoteApplication.shouldReuseOpeningWindowForLaunchRequest(
                FastRemoteApplication.WindowState.CLOSED, true));
        assertFalse(FastRemoteApplication.shouldReuseOpeningWindowForLaunchRequest(
                FastRemoteApplication.WindowState.OPEN, true));
        assertFalse(FastRemoteApplication.shouldReuseOpeningWindowForLaunchRequest(
                FastRemoteApplication.WindowState.CLOSING, true));
        assertFalse(FastRemoteApplication.shouldReuseOpeningWindowForLaunchRequest(
                FastRemoteApplication.WindowState.OPENING, false));
    }

    @Test
    void externalMcpRequestRevealsAnExistingHiddenWindow() {
        assertTrue(FastRemoteApplication.shouldRevealHiddenMainWindowForExternalMcpRequest(
                FastRemoteApplication.WindowState.OPEN, true, false));
        assertFalse(FastRemoteApplication.shouldRevealHiddenMainWindowForExternalMcpRequest(
                FastRemoteApplication.WindowState.OPEN, true, true));
        assertFalse(FastRemoteApplication.shouldRevealHiddenMainWindowForExternalMcpRequest(
                FastRemoteApplication.WindowState.OPEN, false, false));
        assertFalse(FastRemoteApplication.shouldRevealHiddenMainWindowForExternalMcpRequest(
                FastRemoteApplication.WindowState.CLOSED, true, false));
    }

    @Test
    void onlyTraylessLinuxRetainsItsHiddenWindow() {
        assertTrue(FastRemoteApplication.shouldRetainHiddenMainWindow(true, false));
        assertFalse(FastRemoteApplication.shouldRetainHiddenMainWindow(true, true));
        assertFalse(FastRemoteApplication.shouldRetainHiddenMainWindow(false, false));
    }
}
