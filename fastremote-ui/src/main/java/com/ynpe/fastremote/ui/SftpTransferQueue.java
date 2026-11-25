// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.transfer.FileTransferScheduler;
import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import com.ynpe.fastremote.domain.transfer.TransferConflict;
import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;
import com.ynpe.fastremote.domain.transfer.TransferActivityListener;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntSupplier;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

final class SftpTransferQueue implements AutoCloseable {
    private static final int MAX_RECORDS = 1_000;
    private RemoteFilesHandle files;
    private final IntSupplier concurrency;
    private final Function<TransferConflict, SftpConflictDecision> conflicts;
    private final TransferActivityListener activity;
    private final ObservableList<SftpTransferJob> jobs = FXCollections.observableArrayList();
    private final ArrayDeque<SftpTransferJob> pending = new ArrayDeque<>();
    private final ArrayDeque<SftpTransferJob> pendingResumes = new ArrayDeque<>();
    private FileTransferScheduler scheduler =
            new FileTransferScheduler(1);
    private int active;
    private boolean closed;

    SftpTransferQueue(RemoteFilesHandle files, IntSupplier concurrency,
            Function<TransferConflict, SftpConflictDecision> conflicts) {
        this(files, concurrency, conflicts, TransferActivityListener.NONE);
    }

    SftpTransferQueue(RemoteFilesHandle files, IntSupplier concurrency,
            Function<TransferConflict, SftpConflictDecision> conflicts, TransferActivityListener activity) {
        this.files = Objects.requireNonNull(files, "files");
        this.concurrency = Objects.requireNonNull(concurrency, "concurrency");
        this.conflicts = Objects.requireNonNull(conflicts, "conflicts");
        this.activity = Objects.requireNonNull(activity, "activity");
    }

    ObservableList<SftpTransferJob> jobs() { return jobs; }

    boolean hasUnfinishedTransfers() {
        return jobs.stream().anyMatch(job -> job.state().canCancel());
    }

    boolean closed() { return closed; }

    void reconnect(RemoteFilesHandle files) {
        scheduler.close();
        scheduler = new FileTransferScheduler(1);
        this.files = Objects.requireNonNull(files, "files");
        closed = false;
        active = 0;
    }

    void connectionLost() {
        closed = true;
        scheduler.close();
        for (SftpTransferJob job : ListCopy.of(jobs)) {
            if (job.slotHeld()) release(job);
            job.failBecauseConnectionClosed();
        }
        pending.clear();
        pendingResumes.clear();
    }

    java.util.List<SftpTransferJob> upload(Collection<LocalFileEntry> sources, String remoteDirectory) {
        return upload(sources, remoteDirectory, conflicts);
    }

    java.util.List<SftpTransferJob> upload(Collection<LocalFileEntry> sources, String remoteDirectory, long maximumBytes) {
        return upload(sources, remoteDirectory, conflicts, maximumBytes);
    }

    java.util.List<SftpTransferJob> upload(Collection<LocalFileEntry> sources, String remoteDirectory,
            TransferConflictChoice conflictChoice) {
        return upload(sources, remoteDirectory, conflictChoice, Long.MAX_VALUE);
    }

    java.util.List<SftpTransferJob> upload(Collection<LocalFileEntry> sources, String remoteDirectory,
            TransferConflictChoice conflictChoice, long maximumBytes) {
        Objects.requireNonNull(conflictChoice, "conflictChoice");
        return upload(sources, remoteDirectory, ignored -> new SftpConflictDecision(conflictChoice, true), maximumBytes);
    }

    private java.util.List<SftpTransferJob> upload(Collection<LocalFileEntry> sources, String remoteDirectory,
            Function<TransferConflict, SftpConflictDecision> conflictResolver) {
        return upload(sources, remoteDirectory, conflictResolver, Long.MAX_VALUE);
    }

    java.util.List<SftpTransferJob> upload(Collection<LocalFileEntry> sources, String remoteDirectory,
            Function<TransferConflict, SftpConflictDecision> conflictResolver, long maximumBytes) {
        ensureCapacity(sources.size());
        java.util.List<SftpTransferJob> added = new java.util.ArrayList<>();
        Function<TransferConflict, SftpConflictDecision> batchConflicts = batchConflicts(conflictResolver);
        for (LocalFileEntry source : sources) {
            added.add(new SftpTransferJob(SftpTransferJob.Direction.UPLOAD,
                    source.path().toString(), joinRemote(remoteDirectory, source.name()), source.path(),
                    joinRemote(remoteDirectory, source.name()), files, batchConflicts, maximumBytes, activity));
        }
        added.forEach(this::add);
        dispatch();
        return java.util.List.copyOf(added);
    }

    java.util.List<SftpTransferJob> download(Collection<RemoteFileEntry> sources, Path localDirectory) {
        return download(sources, localDirectory, Long.MAX_VALUE);
    }

    java.util.List<SftpTransferJob> download(Collection<RemoteFileEntry> sources, Path localDirectory, long maximumBytes) {
        return download(sources, localDirectory, conflicts, maximumBytes);
    }

    java.util.List<SftpTransferJob> download(Collection<RemoteFileEntry> sources, Path localDirectory,
            Function<TransferConflict, SftpConflictDecision> conflictResolver, long maximumBytes) {
        ensureCapacity(sources.size());
        java.util.List<SftpTransferJob> added = new java.util.ArrayList<>();
        Function<TransferConflict, SftpConflictDecision> batchConflicts = batchConflicts(conflictResolver);
        for (RemoteFileEntry source : sources) {
            Path target = localDirectory.resolve(source.name());
            added.add(new SftpTransferJob(SftpTransferJob.Direction.DOWNLOAD,
                    source.path(), target.toString(), target, source.path(), files, batchConflicts, maximumBytes, activity));
        }
        added.forEach(this::add);
        dispatch();
        return java.util.List.copyOf(added);
    }

    void pause(Collection<SftpTransferJob> selected) {
        for (SftpTransferJob job : selected) {
            if (!job.state().canPause()) continue;
            if (job.state() == SftpTransferState.WAITING) pending.remove(job);
            if (job.slotHeld()) release(job);
            job.pause();
        }
        dispatch();
    }

    void resume(Collection<SftpTransferJob> selected) {
        for (SftpTransferJob job : selected) {
            if (!job.state().canResume()) continue;
            if (!job.started()) {
                job.returnToWaiting();
                pending.add(job);
            } else if (!pendingResumes.contains(job)) pendingResumes.add(job);
        }
        dispatch();
    }

    void cancel(Collection<SftpTransferJob> selected) {
        for (SftpTransferJob job : selected) {
            if (!job.state().canCancel()) continue;
            pending.remove(job);
            pendingResumes.remove(job);
            if (job.slotHeld()) release(job);
            job.cancel();
        }
        dispatch();
    }

    void retry(Collection<SftpTransferJob> selected) {
        if (closed) return;
        Function<TransferConflict, SftpConflictDecision> batchConflicts = batchConflicts(conflicts);
        for (SftpTransferJob job : ListCopy.of(selected)) {
            if (!job.state().canRetry()) continue;
            add(job.retry(files, batchConflicts));
        }
        dispatch();
    }

    void delete(Collection<SftpTransferJob> selected) {
        for (SftpTransferJob job : ListCopy.of(selected)) {
            if (!job.state().canDelete()) continue;
            if (job.state() == SftpTransferState.PAUSED) job.cancel();
            pendingResumes.remove(job);
            jobs.remove(job);
        }
    }

    private void ensureCapacity(int count) {
        if (closed) throw new IllegalStateException("SFTP queue is closed");
        long activeRecords = jobs.stream().filter(job -> !job.state().canDelete()).count();
        if (count < 1 || count + activeRecords > MAX_RECORDS) throw new IllegalStateException("SFTP queue capacity exceeded");
    }

    private void add(SftpTransferJob job) {
        if (closed) return;
        if (jobs.size() >= MAX_RECORDS) {
            jobs.stream().filter(existing -> existing.state().canDelete()).findFirst().ifPresent(jobs::remove);
            if (jobs.size() >= MAX_RECORDS) return;
        }
        jobs.add(job);
        pending.add(job);
    }

    private void dispatch() {
        if (closed) return;
        int limit = Math.max(1, Math.min(16, concurrency.getAsInt()));
        scheduler.setParallelism(limit);
        while (active < limit) {
            SftpTransferJob resumed = pendingResumes.poll();
            if (resumed != null) {
                if (!resumed.state().canResume()) continue;
                acquire(resumed);
                resumed.resume();
                continue;
            }
            SftpTransferJob next = pending.poll();
            if (next == null) break;
            if (next.state() != SftpTransferState.WAITING) continue;
            acquire(next);
            next.start(scheduler).whenComplete((ignored, error) -> javafx.application.Platform.runLater(() -> {
                if (next.slotHeld()) release(next);
                dispatch();
            }));
        }
    }

    private void acquire(SftpTransferJob job) { active++; job.setSlotHeld(true); }
    private void release(SftpTransferJob job) { active = Math.max(0, active - 1); job.setSlotHeld(false); }

    @Override
    public void close() {
        closed = true;
        scheduler.close();
        cancel(ListCopy.of(jobs));
        pending.clear();
        pendingResumes.clear();
    }

    private static String joinRemote(String directory, String name) {
        return directory.endsWith("/") ? directory + name : directory + "/" + name;
    }

    private static Function<TransferConflict, SftpConflictDecision> batchConflicts(
            Function<TransferConflict, SftpConflictDecision> conflicts) {
        return new BatchConflictResolver(conflicts);
    }

    private static final class BatchConflictResolver implements Function<TransferConflict, SftpConflictDecision> {
        private final Function<TransferConflict, SftpConflictDecision> delegate;
        private SftpConflictDecision remembered;

        private BatchConflictResolver(Function<TransferConflict, SftpConflictDecision> delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public synchronized SftpConflictDecision apply(TransferConflict conflict) {
            if (remembered != null) return remembered;
            SftpConflictDecision decision = Objects.requireNonNull(delegate.apply(conflict), "decision");
            if (decision.timedOut()
                    || (decision.applyToRemaining() && decision.choice() != TransferConflictChoice.CANCEL)) {
                remembered = decision;
            }
            return decision;
        }
    }

    private static final class ListCopy {
        private static <T> java.util.List<T> of(Collection<T> values) { return java.util.List.copyOf(values); }
    }
}
