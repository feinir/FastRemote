// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.automation.AutomationSource;
import com.ynpe.fastremote.domain.automation.AutomationCapability;
import com.ynpe.fastremote.domain.automation.AutomationUiOperationHandler;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.terminal.RemoteCommandResult;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Command validation and UI dispatch behind the shared execution and audit boundary. */
final class AutomationCommands {
    interface Commands {
        SavedHost requireHost(HostId id) throws Exception;
        RemoteCommandResult execute(SavedHost host, String command) throws Exception;
    }
    private final LocalBackgroundCommands localBackground = new LocalBackgroundCommands();
    void close() { localBackground.close(); }
    private final AutomationExecution execution;
    private final Commands commands;
    private final Supplier<AutomationUiOperationHandler> uiOperations;
    AutomationCommands(AutomationExecution execution, Commands commands,
            Supplier<AutomationUiOperationHandler> uiOperations) {
        this.execution = execution;
        this.commands = commands;
        this.uiOperations = uiOperations;
    }

    public CompletionStage<com.ynpe.fastremote.domain.terminal.LocalCommandResult> runLocalCommand(
            AutomationSource source, String command, java.util.Optional<java.nio.file.Path> workingDirectory) {
        String text = Objects.requireNonNull(command, "command");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        if (text.isBlank() || text.length() > 65536 || text.indexOf('\0') >= 0)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Command must contain 1 to 65536 characters without NUL"));
        if (workingDirectory.isPresent() && !workingDirectory.orElseThrow().isAbsolute())
            return CompletableFuture.failedFuture(new IllegalArgumentException("working_directory must be an absolute path"));
        String detail = "Working directory: " + workingDirectory.map(java.nio.file.Path::toString)
                .orElseGet(() -> System.getProperty("user.home")) + "\n" + text;
        var canceled = new java.util.concurrent.atomic.AtomicBoolean();
        var result = execution.submit(() -> execution.auditedGlobal(source, AutomationCapability.RUN_COMMAND,
                "Local background process (not displayed in a terminal Tab)",
                com.ynpe.fastremote.i18n.Texts.text("automation.localBackground"), detail, () ->
                    localBackground.execute(text, workingDirectory, java.time.Duration.ofSeconds(60), 256 * 1024, canceled::get)))
                .toCompletableFuture();
        result.whenComplete((value, error) -> { if (result.isCancelled()) canceled.set(true); });
        return result;
    }

    public CompletionStage<RemoteCommandResult> runCommand(
            AutomationSource source, HostId hostId, String command) {
        String safeCommand = Objects.requireNonNull(command, "command");
        if (safeCommand.isBlank() || safeCommand.length() > 64 * 1024) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Command must contain 1 to 65536 characters"));
        }
        return execution.submit(() -> {
            SavedHost target = commands.requireHost(hostId);
            return execution.audited(source, AutomationCapability.RUN_COMMAND, target,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.90d54b03d595bbf9"), safeCommand, () -> {
                        return commands.execute(target, safeCommand);
                    });
        });
    }

    public CompletionStage<Boolean> runTerminalCommand(
            AutomationSource source, HostId hostId, String command) {
        return runTerminalCommand(source, hostId, command, false);
    }

    public CompletionStage<Boolean> runTerminalCommand(
            AutomationSource source, HostId hostId, String command, boolean reuseExistingTab) {
        String safeCommand = Objects.requireNonNull(command, "command");
        if (safeCommand.isBlank() || safeCommand.length() > 64 * 1024) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Command must contain 1 to 65536 characters"));
        }
        return execution.submit(() -> {
            SavedHost target = commands.requireHost(hostId);
            return execution.audited(source, AutomationCapability.RUN_COMMAND, target,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.b83a9eb5904496a1"), safeCommand, () -> {
                        boolean submitted = uiOperations.get().runTerminalCommand(
                                        target.id(), safeCommand, reuseExistingTab)
                                .toCompletableFuture().get(35, TimeUnit.SECONDS);
                        if (!submitted) throw new IllegalStateException("FastRemote could not send the command to a visible terminal");
                        return true;
                    });
        });
    }

    public CompletionStage<Boolean> runLocalTerminalCommand(
            AutomationSource source, String command) {
        return runLocalTerminalCommand(source, command, false);
    }

    public CompletionStage<Boolean> runLocalTerminalCommand(
            AutomationSource source, String command, boolean reuseExistingTab) {
        String safeCommand = Objects.requireNonNull(command, "command");
        if (safeCommand.isBlank() || safeCommand.length() > 64 * 1024) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Command must contain 1 to 65536 characters"));
        }
        return execution.submit(() -> execution.auditedGlobal(source, AutomationCapability.RUN_COMMAND, com.ynpe.fastremote.i18n.Texts.text("legacy.java.51b72143be70e638"),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.96e9c05e17f90065"), safeCommand, () -> {
                    boolean submitted = uiOperations.get().runLocalTerminalCommand(
                                    safeCommand, reuseExistingTab)
                            .toCompletableFuture().get(35, TimeUnit.SECONDS);
                    if (!submitted) {
                        throw new IllegalStateException(
                                "FastRemote could not send the command to a visible local terminal");
                    }
                    return true;
                }));
    }

    public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> startTerminalCommand(
            AutomationSource source, HostId hostId, String command, boolean reuseExistingTab) {
        String safeCommand = Objects.requireNonNull(command, "command");
        if (safeCommand.isBlank() || safeCommand.length() > 64 * 1024) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Command must contain 1 to 65536 characters"));
        }
        return execution.submit(() -> {
            SavedHost target = commands.requireHost(hostId);
            return execution.audited(source, AutomationCapability.RUN_COMMAND, target,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.b83a9eb5904496a1"), safeCommand, () -> {
                        var receipt = uiOperations.get().startTerminalCommand(
                                        target.id(), safeCommand, reuseExistingTab)
                                .toCompletableFuture().get(35, TimeUnit.SECONDS);
                        return receipt;
                    });
        });
    }
    public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> startLocalTerminalCommand(
            AutomationSource source, String command, boolean reuseExistingTab) {
        String safeCommand = Objects.requireNonNull(command, "command");
        if (safeCommand.isBlank() || safeCommand.length() > 64 * 1024) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Command must contain 1 to 65536 characters"));
        }
        return execution.submit(() -> execution.auditedGlobal(source, AutomationCapability.RUN_COMMAND, com.ynpe.fastremote.i18n.Texts.text("legacy.java.51b72143be70e638"),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.96e9c05e17f90065"), safeCommand, () -> {
                    var receipt = uiOperations.get().startLocalTerminalCommand(
                                    safeCommand, reuseExistingTab)
                            .toCompletableFuture().get(35, TimeUnit.SECONDS);
                    return receipt;
                }));
    }
}
