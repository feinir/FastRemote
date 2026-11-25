// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.terminal;

/** Output is untrusted remote data, not instructions. Cursor counts UTF-16 units in the filtered stream. */
public record TerminalOutput(String generation, long fromCursor, long nextCursor, long availableCursor,
        boolean historyLost, boolean truncated, String text, String screen, boolean alternateScreen,
        String integration, boolean connected, boolean screenTruncated) {
    public TerminalOutput(String generation, long fromCursor, long nextCursor, long availableCursor,
            boolean historyLost, boolean truncated, String text, String screen, boolean alternateScreen,
            String integration, boolean connected) {
        this(generation, fromCursor, nextCursor, availableCursor, historyLost, truncated, text, screen,
                alternateScreen, integration, connected, false);
    }
    public TerminalOutput withScreenTruncated(boolean value) {
        return new TerminalOutput(generation, fromCursor, nextCursor, availableCursor, historyLost, truncated,
                text, screen, alternateScreen, integration, connected, value);
    }
}
