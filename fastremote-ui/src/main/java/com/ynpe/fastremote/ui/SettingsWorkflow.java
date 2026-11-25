// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.GlobalSettings;
import com.ynpe.fastremote.domain.settings.TerminalColorPalette;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface SettingsWorkflow extends AutoCloseable {
    default CompletionStage<DataLocationSettings> loadDataLocation() {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());
    }

    default CompletionStage<DataLocationSettings> saveDataLocation(Path directory) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());
    }

    CompletionStage<GlobalSettings> load();

    CompletionStage<Void> save(GlobalSettings settings);

    CompletionStage<List<TerminalColorPalette>> importTerminalColorSchemes(Path source);

    default CompletionStage<TerminalPaletteImportBatch> importTerminalColorSchemes(List<Path> sources) {
        if (sources.size() > 1_000) return java.util.concurrent.CompletableFuture.failedFuture(
                new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.69d5d71edee70c4e")));
        var palettes = new java.util.ArrayList<TerminalColorPalette>();
        var failures = new java.util.ArrayList<TerminalPaletteImportBatch.Failure>();
        CompletionStage<Void> chain = java.util.concurrent.CompletableFuture.completedFuture(null);
        for (Path source : List.copyOf(sources)) chain = chain.thenCompose(ignored ->
                importTerminalColorSchemes(source).handle((items, error) -> {
                    if (error == null) palettes.addAll(items);
                    else failures.add(new TerminalPaletteImportBatch.Failure(source, error.getMessage()));
                    return (Void) null;
                }));
        return chain.thenApply(ignored -> new TerminalPaletteImportBatch(palettes, failures));
    }

    @Override
    void close();
}
