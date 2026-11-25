// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.transfer.FileTransferScheduler;
import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;
import com.ynpe.fastremote.domain.transfer.TransferControl;
import com.ynpe.fastremote.domain.transfer.TransferActivityListener;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

final class SftpTransferJob {
    enum Direction { UPLOAD, DOWNLOAD }

    final String operationId = java.util.UUID.randomUUID().toString();
    private final java.util.LinkedHashMap<String, com.ynpe.fastremote.domain.automation.WorkspaceFileResult> results = new java.util.LinkedHashMap<>();
    private boolean detailsTruncated;
    private long succeededEntries, skippedEntries, failedEntries;
    synchronized long succeededEntries() { return succeededEntries; }
    synchronized long skippedEntries() { return skippedEntries; }
    synchronized long failedEntries() { return failedEntries; }
    private volatile boolean waitingConflict;
    private volatile boolean conflictTimedOut;

    boolean conflictTimedOut() { return conflictTimedOut; }
    synchronized java.util.List<com.ynpe.fastremote.domain.automation.WorkspaceFileResult> results() { return java.util.List.copyOf(results.values()); }
    synchronized boolean detailsTruncated() { return detailsTruncated; }
    boolean waitingConflict() { return waitingConflict; }
    private synchronized void recordResult(String source, String target, String state, String detail) {
        String key = source + "\n" + target;
        int retained = results.values().stream().mapToInt(item -> item.source().length() + item.target().length() + item.detail().length()).sum();
        if ((results.size() >= 128 && !results.containsKey(key))
                || retained + source.length() + target.length() + detail.length() > 16 * 1024) {
            detailsTruncated = true; return;
        }
        results.put(key, new com.ynpe.fastremote.domain.automation.WorkspaceFileResult(source, target, state, detail));
    }
    private final Direction direction;
    private final String source;
    private final String target;
    private final Path localPath;
    private final String remotePath;
    private final RemoteFilesHandle files;
    private final Function<com.ynpe.fastremote.domain.transfer.TransferConflict, SftpConflictDecision> conflictResolver;
    private final long maximumBytes;
    private final TransferActivityListener activity;
    private final Gate control = new Gate();
    private final ReadOnlyObjectWrapper<SftpTransferState> state =
            new ReadOnlyObjectWrapper<>(SftpTransferState.WAITING);
    private final ReadOnlyLongWrapper totalBytes = new ReadOnlyLongWrapper();
    private final ReadOnlyLongWrapper transferredBytes = new ReadOnlyLongWrapper();
    private final ReadOnlyLongWrapper elapsedNanos = new ReadOnlyLongWrapper();
    private final ReadOnlyLongWrapper currentBytesPerSecond = new ReadOnlyLongWrapper();
    private final AtomicBoolean uiUpdatePending = new AtomicBoolean();
    private volatile long observedTotal;
    private volatile long observedTransferred;
    private long accumulatedRunNanos;
    private long runStartedNanos;
    private long speedSampleNanos;
    private long speedSampleBytes;
    private TransferConflictChoice applyToRemaining;
    private volatile Throwable failure;
    private volatile CompletableFuture<Void> runningOperation;
    private boolean slotHeld;
    private boolean started;

    SftpTransferJob(Direction direction, String source, String target, Path localPath, String remotePath,
            RemoteFilesHandle files,
            Function<com.ynpe.fastremote.domain.transfer.TransferConflict, SftpConflictDecision> conflictResolver) {
        this(direction, source, target, localPath, remotePath, files, conflictResolver, Long.MAX_VALUE);
    }

    SftpTransferJob(Direction direction, String source, String target, Path localPath, String remotePath,
            RemoteFilesHandle files,
            Function<com.ynpe.fastremote.domain.transfer.TransferConflict, SftpConflictDecision> conflictResolver,
            long maximumBytes) {
        this(direction, source, target, localPath, remotePath, files, conflictResolver, maximumBytes,
                TransferActivityListener.NONE);
    }

    SftpTransferJob(Direction direction, String source, String target, Path localPath, String remotePath,
            RemoteFilesHandle files,
            Function<com.ynpe.fastremote.domain.transfer.TransferConflict, SftpConflictDecision> conflictResolver,
            long maximumBytes, TransferActivityListener activity) {
        this.direction = Objects.requireNonNull(direction, "direction");
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        this.localPath = Objects.requireNonNull(localPath, "localPath");
        this.remotePath = Objects.requireNonNull(remotePath, "remotePath");
        this.files = Objects.requireNonNull(files, "files");
        this.conflictResolver = Objects.requireNonNull(conflictResolver, "conflictResolver");
        Objects.requireNonNull(activity, "activity");
        this.activity = new TransferActivityListener() {
            @Override public void processing(com.ynpe.fastremote.domain.transfer.TransferActivity item) {
                recordResult(item.source(), item.target(), "PROCESSING", ""); activity.processing(item);
            }
            @Override public void completed(String source, String target, String state, String detail) {
                synchronized (SftpTransferJob.this) {
                    switch (state) {
                        case "SUCCEEDED" -> succeededEntries++;
                        case "SKIPPED" -> skippedEntries++;
                        case "FAILED" -> failedEntries++;
                        default -> { }
                    }
                    results.remove(source + "\n" + (direction == Direction.UPLOAD ? remotePath : localPath));
                    results.entrySet().removeIf(entry -> entry.getValue().source().equals(source)
                            && entry.getValue().state().equals("PROCESSING"));
                }
                recordResult(source, target, state, detail);
                activity.completed(source, target, state, detail);
            }
        };
        if (maximumBytes <= 0L) throw new IllegalArgumentException("maximumBytes must be positive");
        this.maximumBytes = maximumBytes;
    }

    Direction direction() { return direction; }
    String source() { return source; }
    String target() { return target; }
    SftpTransferState state() { return state.get(); }
    ReadOnlyObjectProperty<SftpTransferState> stateProperty() { return state.getReadOnlyProperty(); }
    ReadOnlyLongProperty totalBytesProperty() { return totalBytes.getReadOnlyProperty(); }
    ReadOnlyLongProperty transferredBytesProperty() { return transferredBytes.getReadOnlyProperty(); }
    ReadOnlyLongProperty elapsedNanosProperty() { return elapsedNanos.getReadOnlyProperty(); }
    ReadOnlyLongProperty currentBytesPerSecondProperty() { return currentBytesPerSecond.getReadOnlyProperty(); }
    long averageBytesPerSecond() {
        long elapsed = elapsedNanos.get();
        return elapsed <= 0 ? 0 : Math.round(transferredBytes.get() / (elapsed / 1_000_000_000.0));
    }
    boolean slotHeld() { return slotHeld; }
    boolean started() { return started; }
    Throwable failure() { return failure; }
    void setSlotHeld(boolean value) { slotHeld = value; }

    SftpTransferJob retry(RemoteFilesHandle currentFiles) {
        return retry(currentFiles, conflictResolver);
    }

    SftpTransferJob retry(RemoteFilesHandle currentFiles,
            Function<com.ynpe.fastremote.domain.transfer.TransferConflict, SftpConflictDecision> currentConflicts) {
        if (!state.get().canRetry()) throw new IllegalStateException("Transfer is not retryable");
        return new SftpTransferJob(direction, source, target, localPath, remotePath,
                Objects.requireNonNull(currentFiles, "currentFiles"),
                Objects.requireNonNull(currentConflicts, "currentConflicts"), maximumBytes, activity);
    }

    java.util.concurrent.CompletionStage<Void> start(FileTransferScheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        started = true;
        transitionToRunning();
        var operation = direction == Direction.UPLOAD
                ? files.uploadTree(localPath, remotePath, control, this::progress, this::resolveConflict, activity, scheduler)
                : files.downloadTree(remotePath, localPath, control, this::progress, this::resolveConflict, activity, scheduler);
        runningOperation = operation.toCompletableFuture();
        return runningOperation.whenComplete((ignored, error) -> Platform.runLater(() -> {
            flushProgress();
            stopClock();
            if (state.get() == SftpTransferState.CANCELLED || state.get() == SftpTransferState.FAILED) return;
            failure = error;
            state.set(error == null ? SftpTransferState.COMPLETED : SftpTransferState.FAILED);
        }));
    }

    void pause() {
        if (!state.get().canPause()) return;
        if (state.get() == SftpTransferState.WAITING) {
            state.set(SftpTransferState.PAUSED);
            control.pause();
            return;
        }
        stopClock();
        state.set(SftpTransferState.PAUSED);
        control.pause();
    }

    void resume() {
        if (!state.get().canResume()) return;
        transitionToRunning();
        control.resume();
    }

    void returnToWaiting() {
        if (!started && state.get() == SftpTransferState.PAUSED) {
            state.set(SftpTransferState.WAITING);
            control.resume();
        }
    }

    void cancel() {
        if (!state.get().canCancel()) return;
        stopClock();
        state.set(SftpTransferState.CANCELLED);
        control.cancel();
        CompletableFuture<Void> operation = runningOperation;
        if (operation != null) operation.cancel(true);
    }

    void failBecauseConnectionClosed() {
        if (!state.get().canCancel()) return;
        stopClock();
        failure = new IOException("SSH/SFTP connection closed");
        state.set(SftpTransferState.FAILED);
        control.cancel();
        CompletableFuture<Void> operation = runningOperation;
        if (operation != null) operation.cancel(true);
    }

    private void transitionToRunning() {
        state.set(SftpTransferState.RUNNING);
        runStartedNanos = System.nanoTime();
        speedSampleNanos = runStartedNanos;
        speedSampleBytes = observedTransferred;
    }

    private void stopClock() {
        if (runStartedNanos != 0L) {
            accumulatedRunNanos += Math.max(0L, System.nanoTime() - runStartedNanos);
            runStartedNanos = 0L;
            elapsedNanos.set(accumulatedRunNanos);
        }
        currentBytesPerSecond.set(0L);
    }

    private void progress(long discovered, long transferred) {
        if (discovered > maximumBytes || transferred > maximumBytes) {
            throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text(
                    "ai.error.transferTooLarge", maximumBytes / (1024L * 1024L)));
        }
        observedTotal = discovered;
        observedTransferred = transferred;
        long now = System.nanoTime();
        if (now - speedSampleNanos >= 250_000_000L && uiUpdatePending.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                flushProgress();
                uiUpdatePending.set(false);
            });
        }
    }

    private void flushProgress() {
        long now = System.nanoTime();
        long interval = now - speedSampleNanos;
        long bytes = observedTransferred;
        if (interval > 0L) {
            currentBytesPerSecond.set(Math.max(0L,
                    Math.round((bytes - speedSampleBytes) / (interval / 1_000_000_000.0))));
        }
        speedSampleNanos = now;
        speedSampleBytes = bytes;
        totalBytes.set(observedTotal);
        transferredBytes.set(bytes);
        if (runStartedNanos != 0L) elapsedNanos.set(accumulatedRunNanos + now - runStartedNanos);
    }

    private TransferConflictChoice resolveConflict(com.ynpe.fastremote.domain.transfer.TransferConflict conflict) {
        TransferConflictChoice remembered = applyToRemaining;
        if (remembered != null) return remembered;
        SftpConflictDecision decision;
        waitingConflict = true;
        try {
            decision = conflictResolver.apply(conflict);
            conflictTimedOut |= decision.timedOut();
        }
        finally { waitingConflict = false; }
        if (decision.applyToRemaining()) applyToRemaining = decision.choice();
        if (decision.choice() == TransferConflictChoice.CANCEL) {
            control.cancel();
            Platform.runLater(this::cancel);
        }
        return decision.choice();
    }

    private static final class Gate implements TransferControl {
        private boolean paused;
        private boolean cancelled;

        @Override
        public void checkpoint() throws IOException { checkpoint(() -> {}); }

        @Override
        public void checkpoint(Runnable beforePause) throws IOException {
            while (true) {
                synchronized (this) {
                    if (cancelled || Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("Transfer cancelled");
                    }
                    if (!paused) return;
                }
                beforePause.run();
                synchronized (this) {
                    if (!paused || cancelled) continue;
                    try { wait(); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
            }
        }

        synchronized void pause() { paused = true; }
        synchronized void resume() { paused = false; notifyAll(); }
        synchronized void cancel() { cancelled = true; paused = false; notifyAll(); }
    }
}
