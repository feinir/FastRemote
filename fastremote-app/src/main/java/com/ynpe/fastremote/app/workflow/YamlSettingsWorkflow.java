// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.settings.GlobalSettings;
import com.ynpe.fastremote.domain.settings.GlobalSettingsRepository;
import com.ynpe.fastremote.ui.SettingsWorkflow;
import com.ynpe.fastremote.domain.settings.TerminalColorPalette;
import com.ynpe.fastremote.storage.TerminalColorSchemeImporter;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public final class YamlSettingsWorkflow implements SettingsWorkflow {
    private final GlobalSettingsRepository repository;
    private final BoundedWorkflowExecutor executor;
    private final TerminalColorSchemeImporter colorSchemeImporter = new TerminalColorSchemeImporter();
    private volatile Consumer<GlobalSettings> savedListener = ignored -> { };

    public YamlSettingsWorkflow(GlobalSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        executor = new BoundedWorkflowExecutor("fastremote-settings-", 8,
                BoundedWorkflowExecutor.ClosePolicy.DRAIN,
                () -> "Settings workflow is closed", () -> "Settings queue is full");
    }

    @Override
    public CompletionStage<GlobalSettings> load() {
        return supply(repository::load);
    }

    @Override
    public CompletionStage<Void> save(GlobalSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return supply(() -> {
            repository.save(settings);
            savedListener.accept(settings);
            return null;
        });
    }

    @Override
    public CompletionStage<com.ynpe.fastremote.ui.DataLocationSettings> loadDataLocation() {
        return supply(com.ynpe.fastremote.app.DataLocationService::settings);
    }

    @Override
    public CompletionStage<com.ynpe.fastremote.ui.DataLocationSettings> saveDataLocation(Path directory) {
        return supply(() -> com.ynpe.fastremote.app.DataLocationService.schedule(directory));
    }

    public void setSavedListener(Consumer<GlobalSettings> listener) {
        savedListener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public CompletionStage<List<TerminalColorPalette>> importTerminalColorSchemes(Path source) {
        Objects.requireNonNull(source, "source");
        return supply(() -> colorSchemeImporter.importFile(source));
    }

    public CompletionStage<Void> stopped() { return executor.stopped(); }

    private <T> CompletionStage<T> supply(BoundedWorkflowExecutor.CheckedSupplier<T> operation) {
        return executor.supply(operation);
    }

    @Override
    public void close() {
        executor.close();
    }

}
