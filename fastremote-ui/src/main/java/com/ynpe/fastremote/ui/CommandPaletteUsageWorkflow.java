// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.command.CommandPaletteUsage;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface CommandPaletteUsageWorkflow extends AutoCloseable {
    CompletionStage<List<CommandPaletteUsage>> load();

    CompletionStage<CommandPaletteUsage> recordUse(String entryKey, Instant usedAt);

    @Override
    void close();

    static CommandPaletteUsageWorkflow unavailable() {
        return new CommandPaletteUsageWorkflow() {
            @Override public CompletionStage<List<CommandPaletteUsage>> load() {
                return CompletableFuture.completedFuture(List.of());
            }

            @Override public CompletionStage<CommandPaletteUsage> recordUse(String entryKey, Instant usedAt) {
                return CompletableFuture.completedFuture(new CommandPaletteUsage(entryKey, 1, usedAt));
            }

            @Override public void close() { }
        };
    }
}
