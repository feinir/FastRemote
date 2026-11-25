// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletionStage;
import com.ynpe.fastremote.domain.terminal.TerminalTermination;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.settings.TerminalShortcutSettings;
import javafx.scene.Node;

public final class TerminalHandle implements AutoCloseable {
    private com.ynpe.fastremote.domain.terminal.TerminalObservation observation;
    public TerminalHandle withObservation(com.ynpe.fastremote.domain.terminal.TerminalObservation value) {
        observation = Objects.requireNonNull(value); return this;
    }
    public com.ynpe.fastremote.domain.terminal.TerminalObservation observation() {
        if (observation == null) throw new IllegalStateException("Terminal observation unavailable");
        return observation;
    }
    private final Node node;
    private final Runnable closeAction;
    private final RemoteFilesHandle remoteFiles;
    private final Node focusTarget;
    private final CompletionStage<TerminalTermination> termination;
    private final java.util.function.Consumer<TerminalPreferences> preferencesAction;
    private final java.util.function.Supplier<CompletionStage<ServerOverview>> serverOverviewAction;
    private final java.util.function.Function<String, CompletionStage<Void>> inputAction;
    private final java.util.function.LongSupplier receivedBytes;
    private final java.util.function.LongSupplier sentBytes;
    private final java.util.function.Consumer<java.util.function.Consumer<String>> workingDirectoryListenerAction;
    private final java.util.function.Consumer<TerminalShortcutSettings> shortcutAction;
    private final java.util.function.Consumer<Boolean> inputEnabledAction;
    private final java.util.function.Supplier<LocalTerminalHandle.ShellFamily> shellFamilyAction;
    private final java.util.function.Consumer<java.util.function.Supplier<java.util.List<TerminalContextMenuAction>>>
            contextMenuItemsAction;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean attachmentFocusPending = new AtomicBoolean();

    public TerminalHandle(
            Node node,
            Node focusTarget,
            Runnable closeAction,
            RemoteFilesHandle remoteFiles,
            CompletionStage<TerminalTermination> termination,
            java.util.function.Consumer<TerminalPreferences> preferencesAction,
            java.util.function.Supplier<CompletionStage<ServerOverview>> serverOverviewAction,
            java.util.function.Function<String, CompletionStage<Void>> inputAction) {
        this(node, focusTarget, closeAction, remoteFiles, termination, preferencesAction,
                serverOverviewAction, inputAction, () -> 0L, () -> 0L, ignored -> {});
    }

    public TerminalHandle(
            Node node, Node focusTarget, Runnable closeAction, RemoteFilesHandle remoteFiles,
            CompletionStage<TerminalTermination> termination,
            java.util.function.Consumer<TerminalPreferences> preferencesAction,
            java.util.function.Supplier<CompletionStage<ServerOverview>> serverOverviewAction,
            java.util.function.Function<String, CompletionStage<Void>> inputAction,
            java.util.function.LongSupplier receivedBytes,
            java.util.function.LongSupplier sentBytes) {
        this(node, focusTarget, closeAction, remoteFiles, termination, preferencesAction,
                serverOverviewAction, inputAction, receivedBytes, sentBytes, ignored -> {});
    }

    public TerminalHandle(
            Node node, Node focusTarget, Runnable closeAction, RemoteFilesHandle remoteFiles,
            CompletionStage<TerminalTermination> termination,
            java.util.function.Consumer<TerminalPreferences> preferencesAction,
            java.util.function.Supplier<CompletionStage<ServerOverview>> serverOverviewAction,
            java.util.function.Function<String, CompletionStage<Void>> inputAction,
            java.util.function.LongSupplier receivedBytes,
            java.util.function.LongSupplier sentBytes,
            java.util.function.Consumer<java.util.function.Consumer<String>> workingDirectoryListenerAction) {
        this(node, focusTarget, closeAction, remoteFiles, termination, preferencesAction,
                serverOverviewAction, inputAction, receivedBytes, sentBytes,
                workingDirectoryListenerAction, ignored -> {}, ignored -> {});
    }

    public TerminalHandle(
            Node node, Node focusTarget, Runnable closeAction, RemoteFilesHandle remoteFiles,
            CompletionStage<TerminalTermination> termination,
            java.util.function.Consumer<TerminalPreferences> preferencesAction,
            java.util.function.Supplier<CompletionStage<ServerOverview>> serverOverviewAction,
            java.util.function.Function<String, CompletionStage<Void>> inputAction,
            java.util.function.LongSupplier receivedBytes,
            java.util.function.LongSupplier sentBytes,
            java.util.function.Consumer<java.util.function.Consumer<String>> workingDirectoryListenerAction,
            java.util.function.Consumer<TerminalShortcutSettings> shortcutAction) {
        this(node, focusTarget, closeAction, remoteFiles, termination, preferencesAction,
                serverOverviewAction, inputAction, receivedBytes, sentBytes,
                workingDirectoryListenerAction, shortcutAction, ignored -> {});
    }

    public TerminalHandle(
            Node node, Node focusTarget, Runnable closeAction, RemoteFilesHandle remoteFiles,
            CompletionStage<TerminalTermination> termination,
            java.util.function.Consumer<TerminalPreferences> preferencesAction,
            java.util.function.Supplier<CompletionStage<ServerOverview>> serverOverviewAction,
            java.util.function.Function<String, CompletionStage<Void>> inputAction,
            java.util.function.LongSupplier receivedBytes,
            java.util.function.LongSupplier sentBytes,
            java.util.function.Consumer<java.util.function.Consumer<String>> workingDirectoryListenerAction,
            java.util.function.Consumer<TerminalShortcutSettings> shortcutAction,
            java.util.function.Consumer<Boolean> inputEnabledAction) {
        this(node, focusTarget, closeAction, remoteFiles, termination, preferencesAction,
                serverOverviewAction, inputAction, receivedBytes, sentBytes,
                workingDirectoryListenerAction, shortcutAction, inputEnabledAction,
                () -> LocalTerminalHandle.ShellFamily.UNKNOWN);
    }

    public TerminalHandle(
            Node node, Node focusTarget, Runnable closeAction, RemoteFilesHandle remoteFiles,
            CompletionStage<TerminalTermination> termination,
            java.util.function.Consumer<TerminalPreferences> preferencesAction,
            java.util.function.Supplier<CompletionStage<ServerOverview>> serverOverviewAction,
            java.util.function.Function<String, CompletionStage<Void>> inputAction,
            java.util.function.LongSupplier receivedBytes,
            java.util.function.LongSupplier sentBytes,
            java.util.function.Consumer<java.util.function.Consumer<String>> workingDirectoryListenerAction,
            java.util.function.Consumer<TerminalShortcutSettings> shortcutAction,
            java.util.function.Consumer<Boolean> inputEnabledAction,
            java.util.function.Supplier<LocalTerminalHandle.ShellFamily> shellFamilyAction) {
        this(node, focusTarget, closeAction, remoteFiles, termination, preferencesAction,
                serverOverviewAction, inputAction, receivedBytes, sentBytes,
                workingDirectoryListenerAction, shortcutAction, inputEnabledAction, shellFamilyAction,
                ignored -> {});
    }

    public TerminalHandle(
            Node node, Node focusTarget, Runnable closeAction, RemoteFilesHandle remoteFiles,
            CompletionStage<TerminalTermination> termination,
            java.util.function.Consumer<TerminalPreferences> preferencesAction,
            java.util.function.Supplier<CompletionStage<ServerOverview>> serverOverviewAction,
            java.util.function.Function<String, CompletionStage<Void>> inputAction,
            java.util.function.LongSupplier receivedBytes,
            java.util.function.LongSupplier sentBytes,
            java.util.function.Consumer<java.util.function.Consumer<String>> workingDirectoryListenerAction,
            java.util.function.Consumer<TerminalShortcutSettings> shortcutAction,
            java.util.function.Consumer<Boolean> inputEnabledAction,
            java.util.function.Supplier<LocalTerminalHandle.ShellFamily> shellFamilyAction,
            java.util.function.Consumer<java.util.function.Supplier<java.util.List<TerminalContextMenuAction>>>
                    contextMenuItemsAction) {
        this.node = Objects.requireNonNull(node, "node");
        this.focusTarget = Objects.requireNonNull(focusTarget, "focusTarget");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
        this.remoteFiles = Objects.requireNonNull(remoteFiles, "remoteFiles");
        this.termination = Objects.requireNonNull(termination, "termination");
        this.preferencesAction = Objects.requireNonNull(preferencesAction, "preferencesAction");
        this.serverOverviewAction = Objects.requireNonNull(serverOverviewAction, "serverOverviewAction");
        this.inputAction = Objects.requireNonNull(inputAction, "inputAction");
        this.receivedBytes = Objects.requireNonNull(receivedBytes, "receivedBytes");
        this.sentBytes = Objects.requireNonNull(sentBytes, "sentBytes");
        this.workingDirectoryListenerAction = Objects.requireNonNull(
                workingDirectoryListenerAction, "workingDirectoryListenerAction");
        this.shortcutAction = Objects.requireNonNull(shortcutAction, "shortcutAction");
        this.inputEnabledAction = Objects.requireNonNull(inputEnabledAction, "inputEnabledAction");
        this.shellFamilyAction = Objects.requireNonNull(shellFamilyAction, "shellFamilyAction");
        this.contextMenuItemsAction = Objects.requireNonNull(contextMenuItemsAction, "contextMenuItemsAction");
    }

    public Node node() {
        return node;
    }

    public RemoteFilesHandle remoteFiles() {
        return remoteFiles;
    }

    public void requestFocus() {
        if (closed.get()) return;
        if (focusTarget.getScene() != null) {
            focusTarget.requestFocus();
            javafx.application.Platform.runLater(() -> {
                if (!closed.get() && focusTarget.getScene() != null) focusTarget.requestFocus();
            });
            return;
        }
        if (!attachmentFocusPending.compareAndSet(false, true)) return;
        javafx.beans.value.ChangeListener<javafx.scene.Scene> listener =
                new javafx.beans.value.ChangeListener<>() {
                    @Override
                    public void changed(
                            javafx.beans.value.ObservableValue<? extends javafx.scene.Scene> observable,
                            javafx.scene.Scene previous,
                            javafx.scene.Scene attached) {
                        if (attached == null) return;
                        observable.removeListener(this);
                        attachmentFocusPending.set(false);
                        javafx.application.Platform.runLater(() -> {
                            if (!closed.get() && focusTarget.getScene() != null) focusTarget.requestFocus();
                        });
                    }
                };
        focusTarget.sceneProperty().addListener(listener);
        if (focusTarget.getScene() != null) {
            focusTarget.sceneProperty().removeListener(listener);
            attachmentFocusPending.set(false);
            javafx.application.Platform.runLater(() -> {
                if (!closed.get()) focusTarget.requestFocus();
            });
        }
    }

    public CompletionStage<TerminalTermination> termination() {
        return termination;
    }

    public void applyPreferences(TerminalPreferences preferences) {
        if (!closed.get()) preferencesAction.accept(Objects.requireNonNull(preferences, "preferences"));
    }

    public void applyKeyboardShortcuts(TerminalShortcutSettings shortcuts) {
        if (!closed.get()) shortcutAction.accept(Objects.requireNonNull(shortcuts, "shortcuts"));
    }

    public void setContextMenuItems(
            java.util.function.Supplier<java.util.List<TerminalContextMenuAction>> items) {
        if (!closed.get()) contextMenuItemsAction.accept(Objects.requireNonNull(items, "items"));
    }

    public void setInputEnabled(boolean enabled) {
        if (!closed.get()) inputEnabledAction.accept(enabled);
    }

    public CompletionStage<ServerOverview> loadServerOverview() {
        if (closed.get()) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalStateException("Terminal connection is closed"));
        }
        return serverOverviewAction.get();
    }

    public CompletionStage<Void> sendInput(String text) {
        Objects.requireNonNull(text, "text");
        if (closed.get()) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalStateException("Terminal connection is closed"));
        }
        return inputAction.apply(text);
    }

    public long receivedBytes() { return receivedBytes.getAsLong(); }

    public long sentBytes() { return sentBytes.getAsLong(); }

    public void observeWorkingDirectory(java.util.function.Consumer<String> listener) {
        if (!closed.get()) {
            workingDirectoryListenerAction.accept(Objects.requireNonNull(listener, "listener"));
        }
    }

    public LocalTerminalHandle.ShellFamily shellFamily() {
        return shellFamilyAction.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeAction.run();
        }
    }
}
