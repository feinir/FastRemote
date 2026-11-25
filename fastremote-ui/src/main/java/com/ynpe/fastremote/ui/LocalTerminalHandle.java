// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.settings.TerminalShortcutSettings;
import com.ynpe.fastremote.domain.terminal.TerminalTermination;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.Node;

/** UI-facing handle for a local PTY. It intentionally has no SSH/SFTP surface. */
public final class LocalTerminalHandle implements AutoCloseable {
    public enum ShellFamily {
        UNKNOWN,
        POSIX,
        POWERSHELL,
        COMMAND_PROMPT
    }

    private com.ynpe.fastremote.domain.terminal.TerminalObservation observation;
    public LocalTerminalHandle withObservation(com.ynpe.fastremote.domain.terminal.TerminalObservation value) {
        observation = Objects.requireNonNull(value); return this;
    }
    public com.ynpe.fastremote.domain.terminal.TerminalObservation observation() {
        if (observation == null) throw new IllegalStateException("Terminal observation unavailable");
        return observation;
    }
    private final Node node;
    private final Node focusTarget;
    private final Runnable closeAction;
    private final CompletionStage<TerminalTermination> termination;
    private final java.util.function.Consumer<TerminalPreferences> preferencesAction;
    private final java.util.function.Function<String, CompletionStage<Void>> inputAction;
    private final java.util.function.LongSupplier receivedBytes;
    private final java.util.function.LongSupplier sentBytes;
    private final java.util.function.Consumer<TerminalShortcutSettings> shortcutAction;
    private final java.util.function.Consumer<java.util.function.Consumer<String>> workingDirectoryListenerAction;
    private final java.util.function.Supplier<CompletionStage<Optional<Path>>> workingDirectoryResolver;
    private final ShellFamily shellFamily;
    private final java.util.function.Consumer<java.util.function.Supplier<java.util.List<TerminalContextMenuAction>>>
            contextMenuItemsAction;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Path workingDirectory;
    private volatile boolean directoryReported;

    public LocalTerminalHandle(Node node, Node focusTarget, Runnable closeAction,
            CompletionStage<TerminalTermination> termination,
            java.util.function.Consumer<TerminalPreferences> preferencesAction,
            java.util.function.Function<String, CompletionStage<Void>> inputAction) {
        this(node, focusTarget, closeAction, termination, preferencesAction, inputAction, () -> 0L, () -> 0L);
    }

    public LocalTerminalHandle(Node node, Node focusTarget, Runnable closeAction,
            CompletionStage<TerminalTermination> termination,
            java.util.function.Consumer<TerminalPreferences> preferencesAction,
            java.util.function.Function<String, CompletionStage<Void>> inputAction,
            java.util.function.LongSupplier receivedBytes,
            java.util.function.LongSupplier sentBytes) {
        this(node, focusTarget, closeAction, termination, preferencesAction, inputAction,
                receivedBytes, sentBytes, ignored -> {}, null, ignored -> {},
                () -> CompletableFuture.completedFuture(Optional.empty()));
    }

    public LocalTerminalHandle(Node node, Node focusTarget, Runnable closeAction,
            CompletionStage<TerminalTermination> termination,
            java.util.function.Consumer<TerminalPreferences> preferencesAction,
            java.util.function.Function<String, CompletionStage<Void>> inputAction,
            java.util.function.LongSupplier receivedBytes,
            java.util.function.LongSupplier sentBytes,
            java.util.function.Consumer<TerminalShortcutSettings> shortcutAction) {
        this(node, focusTarget, closeAction, termination, preferencesAction, inputAction,
                receivedBytes, sentBytes, shortcutAction, null, ignored -> {},
                () -> CompletableFuture.completedFuture(Optional.empty()));
    }

    public LocalTerminalHandle(Node node, Node focusTarget, Runnable closeAction,
            CompletionStage<TerminalTermination> termination,
            java.util.function.Consumer<TerminalPreferences> preferencesAction,
            java.util.function.Function<String, CompletionStage<Void>> inputAction,
            java.util.function.LongSupplier receivedBytes,
            java.util.function.LongSupplier sentBytes,
            java.util.function.Consumer<TerminalShortcutSettings> shortcutAction,
            Path initialWorkingDirectory,
            java.util.function.Consumer<java.util.function.Consumer<String>> workingDirectoryListenerAction) {
        this(node, focusTarget, closeAction, termination, preferencesAction, inputAction,
                receivedBytes, sentBytes, shortcutAction, initialWorkingDirectory,
                workingDirectoryListenerAction, () -> CompletableFuture.completedFuture(Optional.empty()),
                defaultShellFamily());
    }

    public LocalTerminalHandle(Node node, Node focusTarget, Runnable closeAction,
            CompletionStage<TerminalTermination> termination,
            java.util.function.Consumer<TerminalPreferences> preferencesAction,
            java.util.function.Function<String, CompletionStage<Void>> inputAction,
            java.util.function.LongSupplier receivedBytes,
            java.util.function.LongSupplier sentBytes,
            java.util.function.Consumer<TerminalShortcutSettings> shortcutAction,
            Path initialWorkingDirectory,
            java.util.function.Consumer<java.util.function.Consumer<String>> workingDirectoryListenerAction,
            java.util.function.Supplier<CompletionStage<Optional<Path>>> workingDirectoryResolver) {
        this(node, focusTarget, closeAction, termination, preferencesAction, inputAction,
                receivedBytes, sentBytes, shortcutAction, initialWorkingDirectory,
                workingDirectoryListenerAction, workingDirectoryResolver, defaultShellFamily());
    }

    public LocalTerminalHandle(Node node, Node focusTarget, Runnable closeAction,
            CompletionStage<TerminalTermination> termination,
            java.util.function.Consumer<TerminalPreferences> preferencesAction,
            java.util.function.Function<String, CompletionStage<Void>> inputAction,
            java.util.function.LongSupplier receivedBytes,
            java.util.function.LongSupplier sentBytes,
            java.util.function.Consumer<TerminalShortcutSettings> shortcutAction,
            Path initialWorkingDirectory,
            java.util.function.Consumer<java.util.function.Consumer<String>> workingDirectoryListenerAction,
            java.util.function.Supplier<CompletionStage<Optional<Path>>> workingDirectoryResolver,
            ShellFamily shellFamily) {
        this(node, focusTarget, closeAction, termination, preferencesAction, inputAction,
                receivedBytes, sentBytes, shortcutAction, initialWorkingDirectory,
                workingDirectoryListenerAction, workingDirectoryResolver, shellFamily, ignored -> {});
    }

    public LocalTerminalHandle(Node node, Node focusTarget, Runnable closeAction,
            CompletionStage<TerminalTermination> termination,
            java.util.function.Consumer<TerminalPreferences> preferencesAction,
            java.util.function.Function<String, CompletionStage<Void>> inputAction,
            java.util.function.LongSupplier receivedBytes,
            java.util.function.LongSupplier sentBytes,
            java.util.function.Consumer<TerminalShortcutSettings> shortcutAction,
            Path initialWorkingDirectory,
            java.util.function.Consumer<java.util.function.Consumer<String>> workingDirectoryListenerAction,
            java.util.function.Supplier<CompletionStage<Optional<Path>>> workingDirectoryResolver,
            ShellFamily shellFamily,
            java.util.function.Consumer<java.util.function.Supplier<java.util.List<TerminalContextMenuAction>>>
                    contextMenuItemsAction) {
        this.node = Objects.requireNonNull(node); this.focusTarget = Objects.requireNonNull(focusTarget);
        this.closeAction = Objects.requireNonNull(closeAction); this.termination = Objects.requireNonNull(termination);
        this.preferencesAction = Objects.requireNonNull(preferencesAction); this.inputAction = Objects.requireNonNull(inputAction);
        this.receivedBytes = Objects.requireNonNull(receivedBytes); this.sentBytes = Objects.requireNonNull(sentBytes);
        this.shortcutAction = Objects.requireNonNull(shortcutAction);
        this.workingDirectoryListenerAction = Objects.requireNonNull(
                workingDirectoryListenerAction, "workingDirectoryListenerAction");
        this.workingDirectoryResolver = Objects.requireNonNull(
                workingDirectoryResolver, "workingDirectoryResolver");
        this.shellFamily = Objects.requireNonNull(shellFamily, "shellFamily");
        this.contextMenuItemsAction = Objects.requireNonNull(contextMenuItemsAction, "contextMenuItemsAction");
        workingDirectory = normalize(initialWorkingDirectory);
        this.workingDirectoryListenerAction.accept(this::updateWorkingDirectory);
    }
    public Node node() { return node; }
    public CompletionStage<TerminalTermination> termination() { return termination; }
    public CompletionStage<Void> sendInput(String text) { return inputAction.apply(text); }
    public void applyPreferences(TerminalPreferences value) { if (!closed.get()) preferencesAction.accept(value); }
    public void applyKeyboardShortcuts(TerminalShortcutSettings value) {
        if (!closed.get()) shortcutAction.accept(Objects.requireNonNull(value));
    }
    public void setContextMenuItems(
            java.util.function.Supplier<java.util.List<TerminalContextMenuAction>> items) {
        if (!closed.get()) contextMenuItemsAction.accept(Objects.requireNonNull(items, "items"));
    }
    public void requestFocus() { if (!closed.get()) javafx.application.Platform.runLater(focusTarget::requestFocus); }
    public long receivedBytes() { return receivedBytes.getAsLong(); }
    public long sentBytes() { return sentBytes.getAsLong(); }
    public Optional<Path> workingDirectory() { return Optional.ofNullable(workingDirectory); }
    /** Excludes the launch directory until the shell has reported its working directory. */
    public Optional<Path> reportedWorkingDirectory() {
        return directoryReported ? workingDirectory() : Optional.empty();
    }
    public CompletionStage<Optional<Path>> resolveWorkingDirectory() {
        Optional<Path> fallback = workingDirectory();
        CompletionStage<Optional<Path>> resolving;
        try {
            resolving = workingDirectoryResolver.get();
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(fallback);
        }
        return resolving.exceptionally(ignored -> Optional.empty())
                .thenApply(resolved -> resolved.map(LocalTerminalHandle::normalize).or(() -> fallback));
    }
    public ShellFamily shellFamily() { return shellFamily; }

    private void updateWorkingDirectory(String value) {
        if (closed.get() || value == null || value.isBlank()) return;
        try {
            String candidate = value;
            if (isWindows() && candidate.matches("^/[A-Za-z]:/.*")) candidate = candidate.substring(1);
            workingDirectory = normalize(Path.of(candidate));
            directoryReported = true;
        } catch (InvalidPathException ignored) {
            // Prompt inference is best-effort; preserve the last valid directory.
        }
    }

    private static Path normalize(Path value) {
        return value == null ? null : value.toAbsolutePath().normalize();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static ShellFamily defaultShellFamily() {
        return isWindows() ? ShellFamily.POWERSHELL : ShellFamily.POSIX;
    }
    @Override public void close() { if (closed.compareAndSet(false, true)) closeAction.run(); }
}
