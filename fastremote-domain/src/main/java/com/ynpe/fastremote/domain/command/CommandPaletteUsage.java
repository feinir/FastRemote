// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.command;

import java.time.Instant;
import java.util.Objects;

public record CommandPaletteUsage(String entryKey, long useCount, Instant lastUsedAt) {
    public CommandPaletteUsage {
        entryKey = Objects.requireNonNull(entryKey, "entryKey").strip();
        lastUsedAt = Objects.requireNonNull(lastUsedAt, "lastUsedAt");
        if (entryKey.isEmpty() || entryKey.length() > 256) {
            throw new IllegalArgumentException("Command palette entry key must contain 1 to 256 characters");
        }
        if (useCount < 1 || useCount > 1_000_000_000L) {
            throw new IllegalArgumentException("Command palette use count must be between 1 and 1000000000");
        }
    }
}
