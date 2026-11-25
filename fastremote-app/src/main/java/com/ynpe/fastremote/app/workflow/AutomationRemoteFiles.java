// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;
import com.ynpe.fastremote.domain.automation.RemoteTextWrite;
import static com.ynpe.fastremote.app.workflow.AutomationLocalFiles.MAX_LOCAL_ENTRIES;
import static com.ynpe.fastremote.app.workflow.AutomationLibraryOperations.summary;
import static com.ynpe.fastremote.app.workflow.AutomationDiagnostics.*;
import com.ynpe.fastremote.app.workflow.AutomationExecution.CheckedSupplier;
import com.ynpe.fastremote.domain.ai.AiSettings;
import com.ynpe.fastremote.domain.ai.AiSettingsRepository;
import com.ynpe.fastremote.domain.automation.AutomationAuditPhase;
import com.ynpe.fastremote.domain.automation.AutomationCapability;
import com.ynpe.fastremote.domain.automation.AutomationOperationId;
import com.ynpe.fastremote.domain.automation.AutomationOperationStatus;
import com.ynpe.fastremote.domain.automation.AutomationSource;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.connection.SessionConnector;
import com.ynpe.fastremote.domain.security.HostKeyPrompt;
import com.ynpe.fastremote.domain.settings.GlobalSettingsRepository;
import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;
import com.ynpe.fastremote.domain.transfer.RemotePathSyntax;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class AutomationRemoteFiles {
    interface Access {
        SavedHost requireHost(HostId id) throws Exception;
        AutomationRoute resolveRoute(SavedHost target) throws Exception;
        void emit(AutomationSource source, AutomationAuditPhase phase, AutomationCapability capability,
                String action, String target, String detail, Duration duration);
    }
    private final AutomationExecution execution;
    private final Access access;
    private final SessionConnector connector;
    private final GlobalSettingsRepository globalSettings;
    private final AiSettingsRepository aiSettings;
    private final java.util.function.Supplier<HostKeyPrompt> hostKeyPrompt;
    private final java.util.function.Supplier<com.ynpe.fastremote.domain.automation.AutomationUiOperationHandler> uiOperations;
    private final AutomationLocalFiles localFiles;
    private final WorkflowThreadPool executor;
    private final AtomicBoolean closed;
    private final ConcurrentHashMap<AutomationOperationId, MutableOperation> operations = new ConcurrentHashMap<>();
    private final AtomicLong receivedBytes = new AtomicLong();
    private final AtomicLong sentBytes = new AtomicLong();
    private static final int MAX_OPERATIONS = 200;
    private static final int MAX_TEXT_BYTES = 256 * 1024;
    AutomationRemoteFiles(AutomationExecution execution, Access access, SessionConnector connector,
            GlobalSettingsRepository globalSettings,AiSettingsRepository aiSettings,
            java.util.function.Supplier<HostKeyPrompt> hostKeyPrompt,
            java.util.function.Supplier<com.ynpe.fastremote.domain.automation.AutomationUiOperationHandler> uiOperations,
            AutomationLocalFiles localFiles,WorkflowThreadPool executor,AtomicBoolean closed) {
        this.execution=execution;this.access=access;this.connector=connector;this.globalSettings=globalSettings;
        this.aiSettings=aiSettings;this.hostKeyPrompt=hostKeyPrompt;this.uiOperations=uiOperations;
        this.localFiles=localFiles;this.executor=executor;this.closed=closed;
    }
    long receivedBytes() { return receivedBytes.get(); }
    long sentBytes() { return sentBytes.get(); }
    void close() { operations.values().forEach(MutableOperation::cancel); }
    
    public CompletionStage<Boolean> changeSftpRemoteDirectory(
            AutomationSource source, HostId hostId, String directory) {
        String safeDirectory = boundedPath(directory, "directory");
        return execution.submit(() -> {
            SavedHost target = access.requireHost(hostId);
            return execution.audited(source, AutomationCapability.READ_REMOTE, target,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.3b47442afe82c480"), safeDirectory, () -> {
                        boolean changed = uiOperations.get()
                                .changeSftpRemoteDirectory(target.id(), safeDirectory)
                                .toCompletableFuture().get(35, TimeUnit.SECONDS);
                        if (!changed) throw new IllegalStateException(
                                "FastRemote could not change the visible SFTP remote directory");
                        return true;
                    });
        });
    }

    
    public CompletionStage<Boolean> changeSftpLocalDirectory(
            AutomationSource source, HostId hostId, Path directory) {
        Path allowed = requireAllowedLocal(directory, true);
        if (!Files.isDirectory(allowed, LinkOption.NOFOLLOW_LINKS)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Local path is not a directory"));
        }
        return execution.submit(() -> {
            SavedHost target = access.requireHost(hostId);
            return execution.audited(source, AutomationCapability.READ_LOCAL, target,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.1075d40cfacf0f10"), allowed.toString(), () -> {
                        boolean changed = uiOperations.get()
                                .changeSftpLocalDirectory(target.id(), allowed)
                                .toCompletableFuture().get(35, TimeUnit.SECONDS);
                        if (!changed) throw new IllegalStateException(
                                "FastRemote could not change the visible SFTP local directory");
                        return true;
                    });
        });
    }

    
    public CompletionStage<List<RemoteFileEntry>> listRemote(
            AutomationSource source, HostId hostId, String directory) {
        String safeDirectory = boundedPath(directory, "directory");
        return execution.submit(() -> {
            SavedHost target = access.requireHost(hostId);
            return execution.audited(source, AutomationCapability.READ_REMOTE, target,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.82ec97875aff1c2c"), safeDirectory, () -> {
                        try (AutomationRoute route = access.resolveRoute(target);
                             var connection = connector.connectSftp(route.hops(), hostKeyPrompt.get(), ignored -> {},
                                     globalSettings.load().connectionSettings())) {
                            return connection.remoteFiles().list(safeDirectory);
                        }
                    });
        });
    }

     public CompletionStage<String> readRemoteText(
            AutomationSource source, HostId hostId, String path) {
        String allowed = requireAllowedRemote(path, false);
        return execution.submit(() -> {
            SavedHost target = access.requireHost(hostId);
            return execution.audited(source, AutomationCapability.READ_REMOTE, target, com.ynpe.fastremote.i18n.Texts.text("legacy.java.a2f46b00bbecb31a"), allowed, () -> {
                try (AutomationRoute route = access.resolveRoute(target);
                     var connection = connector.connectSftp(route.hops(), hostKeyPrompt.get(), ignored -> {},
                             globalSettings.load().connectionSettings())) {
                    byte[] bytes = connection.remoteFiles().readFile(allowed, MAX_TEXT_BYTES);
                    try { return new String(bytes, java.nio.charset.StandardCharsets.UTF_8); }
                    finally { Arrays.fill(bytes, (byte) 0); }
                }
            });
        });
    }

     public CompletionStage<Boolean> writeRemoteText(
            AutomationSource source, HostId hostId, String path, String content, boolean overwrite) {
        String allowed = requireAllowedRemote(path, true);
        byte[] bytes;
        try {
            bytes = RemoteTextWrite.encode(content);
        } catch (IllegalArgumentException invalid) {
            return CompletableFuture.failedFuture(invalid);
        }
        return execution.submit(() -> {
            SavedHost target = access.requireHost(hostId);
            try {
                AutomationCapability capability = overwrite
                        ? AutomationCapability.EDIT_REMOTE : AutomationCapability.CREATE_REMOTE;
                return execution.audited(source, capability, target, com.ynpe.fastremote.i18n.Texts.text("legacy.java.583a2e532b789e08"),
                        allowed + com.ynpe.fastremote.i18n.Texts.text("legacy.java.46df11261e505363") + overwrite + com.ynpe.fastremote.i18n.Texts.text("legacy.java.e82f91a21402e70c") + bytes.length, () -> {
                            if (source == AutomationSource.BUILT_IN_AI) {
                                boolean completed = uiOperations.get().writeRemoteText(
                                        target.id(), allowed, bytes, overwrite).toCompletableFuture()
                                        .get(35, TimeUnit.SECONDS);
                                if (!completed) throw new IllegalStateException(
                                        "FastRemote could not complete the visible remote write");
                                return true;
                            }
                            try (AutomationRoute route = access.resolveRoute(target);
                                 var connection = connector.connectSftp(route.hops(), hostKeyPrompt.get(), ignored -> {},
                                         globalSettings.load().connectionSettings())) {
                                if (overwrite && connection.remoteFiles().readAttributes(allowed).directory()) {
                                    throw new IllegalArgumentException("Remote target is not a regular file");
                                }
                                connection.remoteFiles().writeFileAtomic(allowed, bytes, overwrite);
                                return true;
                            }
                        });
            } finally { Arrays.fill(bytes, (byte) 0); }
        }).whenComplete((value, failure) -> Arrays.fill(bytes, (byte) 0));
    }

    
    public CompletionStage<AutomationOperationId> upload(
            AutomationSource source, HostId hostId, Path localSource, String remoteTarget) {
        Path local = requireAllowedLocal(localSource, true);
        String remote = boundedPath(remoteTarget, "remoteTarget");
        return startTransfer(source, hostId, AutomationCapability.UPLOAD,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.8069cbeee431a090"), local + " → " + remote,
                (files, operation) -> {
                    ensureLocalTransferWithinLimit(List.of(local), operation.maxBytes());
                    files.uploadTree(local, remote, operation::checkpoint,
                            operation.progressSegment(), conflict -> TransferConflictChoice.CANCEL);
                });
    }

    
    public CompletionStage<AutomationOperationId> uploadIntoDirectory(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory) {
        return uploadIntoDirectory(source, hostId, localSources, remoteDirectory, TransferConflictChoice.CANCEL);
    }

    
    public CompletionStage<AutomationOperationId> uploadIntoDirectory(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory,
            TransferConflictChoice conflictChoice) {
        List<Path> requested = List.copyOf(Objects.requireNonNull(localSources, "localSources"));
        Objects.requireNonNull(conflictChoice, "conflictChoice");
        if (requested.isEmpty() || requested.size() > MAX_LOCAL_ENTRIES) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Upload requires 1 to " + MAX_LOCAL_ENTRIES + " local sources"));
        }
        List<Path> local = requested.stream().map(path -> requireAllowedLocal(path, true)).toList();
        String remote = conflictChoice == TransferConflictChoice.OVERWRITE
                ? requireAllowedRemote(remoteDirectory, false)
                : boundedPath(remoteDirectory, "remoteDirectory");
        return startTransfer(source, hostId, AutomationCapability.UPLOAD,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.8069cbeee431a090"), local.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.4c54e5cb422f358e") + remote,
                conflictChoice == TransferConflictChoice.OVERWRITE ? AutomationCapability.EDIT_REMOTE : null,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.7c3a09ade47457b4"),
                (files, operation) -> {
                    ensureLocalTransferWithinLimit(local, operation.maxBytes());
                    for (Path sourcePath : local) {
                        operation.checkpoint();
                        files.uploadTree(sourcePath, joinRemote(remote, sourcePath.getFileName().toString()),
                                operation::checkpoint, operation.progressSegment(),
                                conflict -> conflictChoice);
                    }
                });
    }

    
    public CompletionStage<Boolean> uploadInSftp(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory) {
        return uploadInSftp(source, hostId, localSources, remoteDirectory, null);
    }

    
    public CompletionStage<Boolean> uploadInSftp(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory,
            TransferConflictChoice conflictChoice) {
        return uploadInSftp(source, hostId, localSources, remoteDirectory, conflictChoice, false);
    }

    
    public CompletionStage<Boolean> uploadInSftp(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory,
            TransferConflictChoice conflictChoice, boolean reuseExistingTab) {
        List<Path> requested = List.copyOf(Objects.requireNonNull(localSources, "localSources"));
        if (requested.isEmpty() || requested.size() > MAX_LOCAL_ENTRIES) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Upload requires 1 to " + MAX_LOCAL_ENTRIES + " local sources"));
        }
        List<Path> local = requested.stream().map(path -> requireAllowedLocal(path, true)).toList();
        String remote = conflictChoice == TransferConflictChoice.OVERWRITE
                ? requireAllowedRemote(remoteDirectory, false)
                : boundedPath(remoteDirectory, "remoteDirectory");
        return execution.submit(() -> {
            SavedHost target = access.requireHost(hostId);
            String detail = local.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.4c54e5cb422f358e") + remote + com.ynpe.fastremote.i18n.Texts.text("legacy.java.a4378ae1b7a4678e")
                    + (conflictChoice == null ? "PROMPT" : conflictChoice.name());
            CheckedSupplier<Boolean> upload = () -> execution.audited(source, AutomationCapability.UPLOAD, target,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.254a871e0a27ab72"), detail, () -> {
                        long maximumBytes = aiSettings.load().maxTransferBytes();
                        ensureLocalTransferWithinLimit(local, maximumBytes);
                        boolean submitted = uiOperations.get().uploadInSftp(
                                target.id(), local, remote, conflictChoice, maximumBytes, reuseExistingTab)
                                .toCompletableFuture().get(35, TimeUnit.SECONDS);
                        if (!submitted) throw new IllegalStateException(
                                "FastRemote could not add the upload to the SFTP queue");
                        return true;
                    });
            if (conflictChoice != TransferConflictChoice.OVERWRITE) return upload.get();
            return execution.audited(source, AutomationCapability.EDIT_REMOTE, target,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.7c3a09ade47457b4"), detail, upload);
        });
    }

    
    public CompletionStage<AutomationOperationId> download(
            AutomationSource source, HostId hostId, String remoteSource, Path localTarget) {
        String remote = boundedPath(remoteSource, "remoteSource");
        Path local = requireAllowedLocal(localTarget, false);
        if (Files.exists(local)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Download target already exists"));
        }
        return startTransfer(source, hostId, AutomationCapability.DOWNLOAD,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.35f6d4560ec7873e"), remote + " → " + local,
                (files, operation) -> files.downloadTree(remote, local, operation::checkpoint,
                        operation.progressSegment(), conflict -> TransferConflictChoice.CANCEL));
    }

    
    public CompletionStage<Boolean> downloadInSftp(
            AutomationSource source, HostId hostId, String remoteSource, Path localDirectory) {
        return downloadInSftp(source, hostId, remoteSource, localDirectory, false);
    }

    
    public CompletionStage<Boolean> downloadInSftp(
            AutomationSource source, HostId hostId, String remoteSource, Path localDirectory,
            boolean reuseExistingTab) {
        String remote = boundedPath(remoteSource, "remoteSource");
        Path local = requireAllowedLocal(localDirectory, true);
        if (!Files.isDirectory(local)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Local download destination is not a directory"));
        }
        return execution.submit(() -> {
            SavedHost target = access.requireHost(hostId);
            return execution.audited(source, AutomationCapability.DOWNLOAD, target,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.e3ee0b57380377c6"), remote + " → " + local, () -> {
                        boolean submitted = uiOperations.get().downloadInSftp(
                                        target.id(), remote, local, aiSettings.load().maxTransferBytes(),
                                        reuseExistingTab)
                                .toCompletableFuture().get(35, TimeUnit.SECONDS);
                        if (!submitted) throw new IllegalStateException("FastRemote could not add the download to the SFTP queue");
                        return true;
                    });
        });
    }

     public CompletionStage<Boolean> createRemoteDirectory(
            AutomationSource source, HostId hostId, String path) {
        String allowed = requireAllowedRemote(path, false);
        return mutateRemote(source, hostId, AutomationCapability.CREATE_REMOTE,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.2d5a8e9c45578560"), allowed,
                files -> files.createDirectory(allowed),
                (ui, id) -> ui.createRemoteDirectory(id, allowed));
    }

     public CompletionStage<Boolean> createRemoteFile(
            AutomationSource source, HostId hostId, String path) {
        String allowed = requireAllowedRemote(path, false);
        return mutateRemote(source, hostId, AutomationCapability.CREATE_REMOTE,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.282faef733944044"), allowed,
                files -> files.createFile(allowed),
                (ui, id) -> ui.createRemoteFile(id, allowed));
    }

     public CompletionStage<Boolean> renameRemote(
            AutomationSource source, HostId hostId, String sourcePath, String targetPath) {
        String from = requireAllowedRemote(sourcePath, true);
        String to = requireAllowedRemote(targetPath, false);
        return mutateRemote(source, hostId, AutomationCapability.RENAME_REMOTE,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.07cd353eea890f32"), from + " → " + to,
                files -> files.rename(from, to), (ui, id) -> ui.renameRemote(id, from, to));
    }

     public CompletionStage<Boolean> deleteRemote(
            AutomationSource source, HostId hostId, String path, boolean recursive) {
        String allowed = requireAllowedRemote(path, true);
        return mutateRemote(source, hostId, AutomationCapability.DELETE_REMOTE,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.ac90599ff3c95d42"),
                allowed + com.ynpe.fastremote.i18n.Texts.text("legacy.java.dc6e94d3032f664c") + recursive, files -> {
                    if (files.readAttributes(allowed).directory() && !recursive) {
                        throw new IllegalArgumentException("Remote directory deletion requires recursive=true");
                    }
                    files.delete(allowed);
                }, (ui, id) -> ui.deleteRemote(id, allowed, recursive));
    }

     public CompletionStage<Boolean> setRemotePermissions(
            AutomationSource source, HostId hostId, String path, int permissions) {
        if (permissions < 0 || permissions > 0777) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Permissions must be between 000 and 777"));
        }
        String allowed = requireAllowedRemote(path, false);
        return mutateRemote(source, hostId, AutomationCapability.EDIT_REMOTE,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.aea1f48541fb952e"),
                allowed + " → " + String.format(java.util.Locale.ROOT, "%03o", permissions),
                files -> files.setPermissions(allowed, permissions),
                (ui, id) -> ui.setRemotePermissions(id, allowed, permissions));
    }

    private CompletionStage<Boolean> mutateRemote(
            AutomationSource source, HostId hostId, AutomationCapability capability,
            String summary, String detail,
            RemoteMutation mutation, VisibleRemoteMutation visibleMutation) {
        return execution.submit(() -> {
            SavedHost target = access.requireHost(hostId);
            String checkedDetail = detail.contains(" → ") ? detail : requireAllowedRemote(detail.split("，", 2)[0], false)
                    + (detail.contains("，") ? detail.substring(detail.indexOf('，')) : "");
            return execution.audited(source, capability, target, summary, checkedDetail, () -> {
                if (source == AutomationSource.BUILT_IN_AI) {
                    boolean completed = visibleMutation.run(uiOperations.get(), target.id()).toCompletableFuture()
                            .get(35, TimeUnit.SECONDS);
                    if (!completed) throw new IllegalStateException("FastRemote could not complete the visible remote operation");
                    return true;
                }
                try (AutomationRoute route = access.resolveRoute(target);
                     var connection = connector.connectSftp(route.hops(), hostKeyPrompt.get(), ignored -> {},
                             globalSettings.load().connectionSettings())) {
                    mutation.run(connection.remoteFiles());
                    return true;
                }
            });
        });
    }

    private CompletionStage<AutomationOperationId> startTransfer(
            AutomationSource source, HostId hostId, AutomationCapability capability,
            String summary, String detail, TransferAction action) {
        return startTransfer(source, hostId, capability, summary, detail, null, "", action);
    }

    private CompletionStage<AutomationOperationId> startTransfer(
            AutomationSource source, HostId hostId, AutomationCapability capability,
            String summary, String detail, AutomationCapability additionalCapability,
            String additionalSummary, TransferAction action) {
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("Automation gateway is closed"));
        trimOperations();
        AutomationOperationId id = AutomationOperationId.create();
        AtomicLong trafficBytes = capability == AutomationCapability.DOWNLOAD ? receivedBytes : sentBytes;
        MutableOperation operation = new MutableOperation(id, trafficBytes);
        CompletableFuture<AutomationOperationId> accepted = new CompletableFuture<>();
        operations.put(id, operation);
        try {
            executor.executeCancellable(() -> {
                long started = System.nanoTime();
                SavedHost target = null;
                try {
                    target = access.requireHost(hostId);
                    execution.requirePermission(source, capability, target, summary, detail);
                    if (additionalCapability != null) {
                        execution.requirePermission(source, additionalCapability, target, additionalSummary, detail);
                    }
                    operation.setMaxBytes(aiSettings.load().maxTransferBytes());
                    operation.state.set(AutomationOperationStatus.State.RUNNING);
                    accepted.complete(id);
                    access.emit(source, AutomationAuditPhase.STARTED, capability, summary, auditTarget(target),
                            detail + com.ynpe.fastremote.i18n.Texts.text("legacy.java.d31ba9b4d63295d4") + id, null);
                    try (AutomationRoute route = access.resolveRoute(target);
                         var connection = connector.connectSftp(route.hops(), hostKeyPrompt.get(), ignored -> {},
                                 globalSettings.load().connectionSettings())) {
                        operation.connection.set(connection);
                        action.run(connection.remoteFiles(), operation);
                    } finally {
                        operation.connection.set(null);
                    }
                    operation.state.compareAndSet(AutomationOperationStatus.State.RUNNING,
                            AutomationOperationStatus.State.SUCCEEDED);
                    access.emit(source, AutomationAuditPhase.SUCCEEDED, capability, summary, auditTarget(target),
                            detail + com.ynpe.fastremote.i18n.Texts.text("legacy.java.d31ba9b4d63295d4") + id + com.ynpe.fastremote.i18n.Texts.text("legacy.java.45da232b16722afe") + operation.transferred.get() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.f5d87a8b74a0ce77"),
                            Duration.ofNanos(System.nanoTime() - started));
                } catch (java.util.concurrent.CancellationException canceled) {
                    operation.state.set(AutomationOperationStatus.State.CANCELED);
                    accepted.completeExceptionally(canceled);
                    access.emit(source, AutomationAuditPhase.CANCELED, capability, summary, auditTarget(target),
                            detail + com.ynpe.fastremote.i18n.Texts.text("legacy.java.d31ba9b4d63295d4") + id, Duration.ofNanos(System.nanoTime() - started));
                } catch (Exception failure) {
                    if (operation.canceled.get()) operation.state.set(AutomationOperationStatus.State.CANCELED);
                    else {
                        operation.error.set(sanitizedMessage(failure));
                        operation.state.set(AutomationOperationStatus.State.FAILED);
                    }
                    accepted.completeExceptionally(failure);
                    access.emit(source, AutomationAuditPhase.FAILED, capability, summary, auditTarget(target),
                            detail + com.ynpe.fastremote.i18n.Texts.text("legacy.java.d31ba9b4d63295d4") + id + com.ynpe.fastremote.i18n.Texts.text("legacy.java.552a208b8ec13b5c")
                                    + com.ynpe.fastremote.i18n.Texts.translateExternalText(sanitizedMessage(failure)),
                            Duration.ofNanos(System.nanoTime() - started));
                }
            }, () -> {
                operation.cancel();
                accepted.completeExceptionally(new java.util.concurrent.CancellationException(
                        "Automation gateway closed before transfer started"));
            });
            return accepted;
        } catch (RejectedExecutionException rejected) {
            operations.remove(id);
            return CompletableFuture.failedFuture(new IllegalStateException("Automation queue is full", rejected));
        }
    }

    
    public CompletionStage<Optional<AutomationOperationStatus>> operation(AutomationOperationId id) {
        MutableOperation value = operations.get(Objects.requireNonNull(id, "id"));
        return CompletableFuture.completedFuture(Optional.ofNullable(value).map(MutableOperation::snapshot));
    }

     public CompletionStage<Optional<AutomationOperationStatus>> operation(
            AutomationSource source, AutomationOperationId id) {
        access.emit(source, AutomationAuditPhase.REQUESTED, AutomationCapability.READ_HOST_METADATA,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.30ec6e8ec6cf70d0"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.63d6b47116de363c"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.3490a66cae9fb0ff") + id, null);
        Optional<AutomationOperationStatus> value = operations.containsKey(id)
                ? Optional.of(localizedOperationStatus(source, operations.get(id).snapshot())) : Optional.empty();
        access.emit(source, AutomationAuditPhase.SUCCEEDED, AutomationCapability.READ_HOST_METADATA,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.30ec6e8ec6cf70d0"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.63d6b47116de363c"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.3490a66cae9fb0ff") + id + com.ynpe.fastremote.i18n.Texts.text("legacy.java.70288af6989e8d76") + value.isPresent(), null);
        return CompletableFuture.completedFuture(value);
    }

    
    public CompletionStage<Boolean> cancel(AutomationOperationId id) {
        MutableOperation value = operations.get(Objects.requireNonNull(id, "id"));
        if (value == null) return CompletableFuture.completedFuture(false);
        value.cancel();
        return CompletableFuture.completedFuture(true);
    }

     public CompletionStage<Boolean> cancel(AutomationSource source, AutomationOperationId id) {
        access.emit(source, AutomationAuditPhase.REQUESTED, AutomationCapability.UPLOAD,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.d353e52d746f9baa"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.63d6b47116de363c"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.3490a66cae9fb0ff") + id, null);
        MutableOperation value = operations.get(Objects.requireNonNull(id, "id"));
        boolean canceled = value != null;
        if (value != null) value.cancel();
        access.emit(source, canceled ? AutomationAuditPhase.CANCELED : AutomationAuditPhase.FAILED,
                AutomationCapability.UPLOAD, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d353e52d746f9baa"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.63d6b47116de363c"),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.3490a66cae9fb0ff") + id + com.ynpe.fastremote.i18n.Texts.text("legacy.java.70288af6989e8d76") + canceled, null);
        return CompletableFuture.completedFuture(canceled);
    }

    private Path requireAllowedLocal(Path path, boolean mustExist) {
        return localFiles.requireAllowedLocal(path, mustExist);
    }

    private String requireAllowedRemote(String value, boolean mutatingExisting) {
        String path = boundedPath(value, "remotePath");
        String normalized;
        try {
            normalized = RemotePathSyntax.normalizeAbsolute(path);
        } catch (IllegalArgumentException invalid) {
            throw new SecurityException("Remote automation path must be absolute", invalid);
        }
        List<String> roots;
        try { roots = aiSettings.load().allowedRemoteRoots(); }
        catch (IOException failure) { throw new IllegalStateException("AI settings cannot be loaded", failure); }
        boolean allowed = roots.stream().anyMatch(root -> RemotePathSyntax.isWithin(normalized, root));
        if (!allowed) throw new SecurityException("Remote path is outside the configured automation roots");
        if (mutatingExisting && roots.contains(normalized)) {
            throw new SecurityException("Configured remote root itself cannot be renamed or deleted");
        }
        return normalized;
    }

    private static String joinRemote(String directory, String name) {
        return directory.endsWith("/") ? directory + name : directory + '/' + name;
    }

    private static String boundedPath(String value, String name) {
        String safe = Objects.requireNonNull(value, name).strip();
        if (safe.isEmpty() || safe.length() > 16 * 1024 || safe.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return safe;
    }

    private static void ensureLocalTransferWithinLimit(List<Path> sources, long maximumBytes)
            throws IOException {
        long total = 0L;
        for (Path source : sources) {
            try (var paths = Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)
                    ? Files.walk(source) : java.util.stream.Stream.of(source)) {
                var iterator = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .iterator();
                while (iterator.hasNext()) {
                    long size = Files.size(iterator.next());
                    if (size > maximumBytes - total) throw transferTooLarge(maximumBytes);
                    total += size;
                }
            }
        }
    }

    private static IllegalArgumentException transferTooLarge(long maximumBytes) {
        return new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text(
                "ai.error.transferTooLarge", maximumBytes / AiSettings.MEBIBYTE));
    }

    private void trimOperations() {
        if (operations.size() < MAX_OPERATIONS) return;
        operations.entrySet().stream()
                .filter(entry -> switch (entry.getValue().state.get()) {
                    case SUCCEEDED, FAILED, CANCELED -> true;
                    default -> false;
                }).limit(operations.size() - MAX_OPERATIONS + 1L)
                .map(Map.Entry::getKey).forEach(operations::remove);
    }

    private static AutomationOperationStatus localizedOperationStatus(
            AutomationSource source, AutomationOperationStatus status) {
        com.ynpe.fastremote.i18n.Localizer localizer = source == AutomationSource.MCP
                ? com.ynpe.fastremote.i18n.Localizer.forLanguage(
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.ENGLISH)
                : com.ynpe.fastremote.i18n.Texts.localizer();
        return new AutomationOperationStatus(status.id(), status.state(), status.discoveredBytes(),
                status.transferredBytes(), status.error().map(localizer::translateExternalText));
    }
    @FunctionalInterface private interface TransferAction {
        void run(com.ynpe.fastremote.domain.transfer.RemoteFileService files, MutableOperation operation) throws Exception;
    }
    @FunctionalInterface private interface RemoteMutation {
        void run(com.ynpe.fastremote.domain.transfer.RemoteFileService files) throws Exception;
    }
    @FunctionalInterface private interface VisibleRemoteMutation {
        CompletionStage<Boolean> run(
                com.ynpe.fastremote.domain.automation.AutomationUiOperationHandler handler, HostId hostId);
    }

    private static final class MutableOperation {
        private final AutomationOperationId id;
        private final AtomicLong trafficBytes;
        private final AtomicReference<AutomationOperationStatus.State> state =
                new AtomicReference<>(AutomationOperationStatus.State.QUEUED);
        private final AtomicLong discovered = new AtomicLong();
        private final AtomicLong transferred = new AtomicLong();
        private final AtomicReference<String> error = new AtomicReference<>();
        private final AtomicBoolean canceled = new AtomicBoolean();
        private final AtomicReference<AutoCloseable> connection = new AtomicReference<>();
        private final AtomicLong maxBytes = new AtomicLong(AiSettings.DEFAULT_MAX_TRANSFER_BYTES);

        private MutableOperation(AutomationOperationId id, AtomicLong trafficBytes) {
            this.id = id;
            this.trafficBytes = trafficBytes;
        }
        private void progress(long discoveredBytes, long transferredBytes) {
            if (discoveredBytes > maxBytes.get() || transferredBytes > maxBytes.get()) {
                throw transferTooLarge(maxBytes.get());
            }
            discovered.set(Math.max(0, discoveredBytes));
            long normalizedTransferred = Math.max(0, transferredBytes);
            long previous = transferred.getAndSet(normalizedTransferred);
            trafficBytes.addAndGet(Math.max(0L, normalizedTransferred - previous));
        }
        private com.ynpe.fastremote.domain.transfer.TransferProgressListener progressSegment() {
            long discoveredBase = discovered.get();
            long transferredBase = transferred.get();
            return (segmentDiscovered, segmentTransferred) -> progress(
                    discoveredBase + Math.max(0L, segmentDiscovered),
                    transferredBase + Math.max(0L, segmentTransferred));
        }
        private long maxBytes() { return maxBytes.get(); }
        private void setMaxBytes(long value) { maxBytes.set(value); }
        private void checkpoint() {
            if (canceled.get()) throw new java.util.concurrent.CancellationException("Transfer canceled");
        }
        private void cancel() {
            canceled.set(true);
            state.set(AutomationOperationStatus.State.CANCELED);
            AutoCloseable active = connection.getAndSet(null);
            if (active != null) try { active.close(); } catch (Exception ignored) { }
        }
        private AutomationOperationStatus snapshot() {
            return new AutomationOperationStatus(id, state.get(), discovered.get(), transferred.get(),
                    Optional.ofNullable(error.get()));
        }
    }

    public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> startSftpUpload(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory,
            TransferConflictChoice conflictChoice, boolean reuseExistingTab) {
        List<Path> requested = List.copyOf(Objects.requireNonNull(localSources, "localSources"));
        if (requested.isEmpty() || requested.size() > MAX_LOCAL_ENTRIES) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Upload requires 1 to " + MAX_LOCAL_ENTRIES + " local sources"));
        }
        List<Path> local = requested.stream().map(path -> requireAllowedLocal(path, true)).toList();
        String remote = conflictChoice == TransferConflictChoice.OVERWRITE
                ? requireAllowedRemote(remoteDirectory, false)
                : boundedPath(remoteDirectory, "remoteDirectory");
        return execution.submit(() -> {
            SavedHost target = access.requireHost(hostId);
            String detail = local.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.4c54e5cb422f358e") + remote + com.ynpe.fastremote.i18n.Texts.text("legacy.java.a4378ae1b7a4678e")
                    + (conflictChoice == null ? "PROMPT" : conflictChoice.name());
            CheckedSupplier<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> upload = () -> execution.audited(source, AutomationCapability.UPLOAD, target,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.254a871e0a27ab72"), detail, () -> {
                        long maximumBytes = aiSettings.load().maxTransferBytes();
                        ensureLocalTransferWithinLimit(local, maximumBytes);
                        var receipt = uiOperations.get().startSftpUpload(
                                target.id(), local, remote, conflictChoice, maximumBytes, reuseExistingTab)
                                .toCompletableFuture().get(35, TimeUnit.SECONDS);
                        return receipt;
                    });
            if (conflictChoice != TransferConflictChoice.OVERWRITE) return upload.get();
            return execution.audited(source, AutomationCapability.EDIT_REMOTE, target,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.7c3a09ade47457b4"), detail, upload);
        });
    }

    

    public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> startSftpDownload(
            AutomationSource source, HostId hostId, String remoteSource, Path localDirectory,
            boolean reuseExistingTab) {
        String remote = boundedPath(remoteSource, "remoteSource");
        Path local = requireAllowedLocal(localDirectory, true);
        if (!Files.isDirectory(local)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Local download destination is not a directory"));
        }
        return execution.submit(() -> {
            SavedHost target = access.requireHost(hostId);
            return execution.audited(source, AutomationCapability.DOWNLOAD, target,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.e3ee0b57380377c6"), remote + " → " + local, () -> {
                        var receipt = uiOperations.get().startSftpDownload(
                                        target.id(), remote, local, aiSettings.load().maxTransferBytes(),
                                        reuseExistingTab)
                                .toCompletableFuture().get(35, TimeUnit.SECONDS);
                        return receipt;
                    });
        });
    }


}
