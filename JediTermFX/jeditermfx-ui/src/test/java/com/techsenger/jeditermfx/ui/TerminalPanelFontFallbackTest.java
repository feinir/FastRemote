package com.techsenger.jeditermfx.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerminalPanelFontFallbackTest {
    @Test
    void routesNonAsciiGraphemesToTheConfiguredFallbackFont() {
        assertFalse(TerminalPanel.containsNonAsciiCodePoint("FastRemote 123".toCharArray(), 0, 14));
        assertTrue(TerminalPanel.containsNonAsciiCodePoint("路径".toCharArray(), 0, 2));

        char[] mixed = "A径B".toCharArray();
        assertTrue(TerminalPanel.containsNonAsciiCodePoint(mixed, 1, 2));
        assertFalse(TerminalPanel.containsNonAsciiCodePoint(mixed, 2, 3));
        assertEquals('径', TerminalPanel.firstNonAsciiCodePoint(mixed, 0, mixed.length));
        assertEquals(-1, TerminalPanel.firstNonAsciiCodePoint("ASCII".toCharArray(), 0, 5));
    }

    @Test
    void hyperlinkFollowingCanRequireThePlatformShortcutModifier() {
        assertTrue(TerminalPanel.shouldFollowHyperlink(true, true, false, false));
        assertFalse(TerminalPanel.shouldFollowHyperlink(false, true, true, false));
        assertTrue(TerminalPanel.shouldFollowHyperlink(false, true, false, true));
        assertFalse(TerminalPanel.shouldFollowHyperlink(false, false, false, true));
        assertTrue(TerminalPanel.shouldFollowHyperlink(false, false, true, false));
    }
}
