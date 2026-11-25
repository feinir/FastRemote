// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.terminal.TerminalObservation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

/** Wait briefly for the initial prompt's shell detection without blocking FX or injecting POSIX code into an unknown shell. */
final class ObservedTerminalCommands {
    private ObservedTerminalCommands() { }
    static CompletionStage<String> execute(TerminalObservation terminal, String command, boolean owned,
            Supplier<LocalTerminalHandle.ShellFamily> family, LongSupplier sentBytes, Supplier<ServerOverview.SystemFamily> systemFamily) {
        CompletableFuture<String> result = new CompletableFuture<>();
        attempt(terminal, command, owned, family, sentBytes, systemFamily, result, 20);
        return result;
    }
    static LocalTerminalHandle.ShellFamily submissionFamily(LocalTerminalHandle.ShellFamily detected,
            Supplier<ServerOverview.SystemFamily> systemFamily) {
        if (detected == LocalTerminalHandle.ShellFamily.UNKNOWN
                && systemFamily.get() == ServerOverview.SystemFamily.WINDOWS) {
            return MainWindowController.commandShellFamily(ServerOverview.SystemFamily.WINDOWS, detected);
        }
        return detected;
    }

    private static void attempt(TerminalObservation terminal, String command, boolean owned,
            Supplier<LocalTerminalHandle.ShellFamily> family, LongSupplier sentBytes, Supplier<ServerOverview.SystemFamily> systemFamily,
            CompletableFuture<String> result, int remaining) {
        if (result.isDone()) return;
        try {
            var detected = family.get();
            if (owned && sentBytes.getAsLong() == 0 && detected == LocalTerminalHandle.ShellFamily.UNKNOWN && remaining > 0) {
                var pause = new PauseTransition(Duration.millis(150));
                pause.setOnFinished(event -> attempt(terminal, command, owned, family, sentBytes, systemFamily, result, remaining - 1));
                pause.play(); return;
            }
            terminal.execute(command, owned && sentBytes.getAsLong() == 0,
                    submissionFamily(detected, systemFamily).name()).whenComplete((id, failure) -> {
                if (failure == null) result.complete(id); else result.completeExceptionally(failure);
            });
        } catch (RuntimeException failure) { result.completeExceptionally(failure); }
    }
}
