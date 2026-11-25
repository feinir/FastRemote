// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.command.CommandPaletteUsage;
import com.ynpe.fastremote.domain.command.CommandPaletteUsageRepository;
import com.ynpe.fastremote.ui.CommandPaletteUsageWorkflow;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public final class SqliteCommandPaletteUsageWorkflow implements CommandPaletteUsageWorkflow {
    private final CommandPaletteUsageRepository repository;
    private final BoundedWorkflowExecutor executor;

    public SqliteCommandPaletteUsageWorkflow(CommandPaletteUsageRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        executor = new BoundedWorkflowExecutor("fastremote-command-usage-", 32,
                BoundedWorkflowExecutor.ClosePolicy.DRAIN,
                () -> "Command palette usage workflow is closed", () -> "Command palette usage queue is full");
    }

    @Override public CompletionStage<List<CommandPaletteUsage>> load() {
        return supply(repository::list);
    }

    @Override public CompletionStage<CommandPaletteUsage> recordUse(String entryKey, Instant usedAt) {
        Objects.requireNonNull(usedAt, "usedAt");
        return supply(() -> repository.recordUse(entryKey, usedAt));
    }

    public CompletionStage<Void> stopped() { return executor.stopped(); }

    private <T> CompletionStage<T> supply(BoundedWorkflowExecutor.CheckedSupplier<T> operation) {
        return executor.supply(operation);
    }

    @Override public void close() {
        executor.close();
    }

}
