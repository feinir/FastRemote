// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;

public interface LocalTerminalWorkflow extends AutoCloseable {
    CompletionStage<LocalTerminalHandle> open(TerminalPreferences preferences);

    default CompletionStage<LocalTerminalHandle> openAutomation(
            TerminalPreferences preferences, Path initialDirectory) {
        return open(preferences, initialDirectory);
    }

    default CompletionStage<LocalTerminalHandle> open(
            TerminalPreferences preferences, Path initialDirectory) {
        return open(preferences);
    }

    default CompletionStage<Path> installCommandLineTools() {
        return java.util.concurrent.CompletableFuture.failedFuture(
                new IllegalStateException("Command-line installation is unavailable."));
    }

    @Override void close();
}
