// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.automation.*;
import com.ynpe.fastremote.domain.terminal.TerminalObservation;
import java.util.*;
import java.util.function.BiFunction;
import javafx.application.Platform;

/** FX-confined directory of bounded observation handles, independent of tab focus or localized log text. */
final class WorkspaceObservations {
    private static final int LIMIT = 1024;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private record Entry(WorkspaceOperationReceipt receipt, BiFunction<Long, Integer, WorkspaceOperationSnapshot> read,
            long created) { }

    WorkspaceOperationReceipt terminal(String workspace, TerminalObservation terminal, String id) {
        var receipt = new WorkspaceOperationReceipt(id, workspace, terminal.generation(), true, "TERMINAL", terminal.origin(id));
        put(new Entry(receipt, (after, max) -> {
            var result = terminal.operation(id, after, max);
            return new WorkspaceOperationSnapshot(id, workspace, result.generation(), result.kind(), result.state(),
                    result.exitCode(), result.output(), 0, 0, List.of(), false, result.detail());
        }, System.nanoTime()));
        return receipt;
    }

    WorkspaceOperationReceipt transfers(String workspace, String generation, List<SftpTransferJob> jobs) {
        if (jobs.isEmpty()) throw new IllegalArgumentException("No transfer was enqueued");
        String id = jobs.size() == 1 ? jobs.getFirst().operationId : UUID.randomUUID().toString();
        var receipt = new WorkspaceOperationReceipt(id, workspace, generation, true, "SFTP", "WORKSPACE");
        put(new Entry(receipt, (after, max) -> transferSnapshot(receipt, jobs, after, max), System.nanoTime()));
        return receipt;
    }

    private WorkspaceOperationSnapshot transferSnapshot(WorkspaceOperationReceipt receipt, List<SftpTransferJob> jobs,
            long offset, int maximum) {
        boolean failed = false, canceled = false, pending = false, paused = false, conflict = false, skipped = false;
        long total = 0, transferred = 0, succeeded = 0, skippedCount = 0, failedCount = 0;
        List<WorkspaceFileResult> files = new ArrayList<>();
        boolean truncated = false;
        boolean conflictTimedOut = false;
        for (var job : jobs) {
            conflictTimedOut |= job.conflictTimedOut();
            failed |= job.state() == SftpTransferState.FAILED;
            canceled |= job.state() == SftpTransferState.CANCELLED;
            paused |= job.state() == SftpTransferState.PAUSED;
            pending |= job.state().canCancel(); conflict |= job.waitingConflict();
            total += job.totalBytesProperty().get(); transferred += job.transferredBytesProperty().get();
            succeeded += job.succeededEntries(); skippedCount += job.skippedEntries(); failedCount += job.failedEntries();
            skipped |= job.skippedEntries() > 0;
            var details = job.results();
            skipped |= details.stream().anyMatch(item -> item.state().equals("SKIPPED"));
            if (files.size() < 512) files.add(new WorkspaceFileResult(job.source(), job.target(), job.state().name(),
                    job.conflictTimedOut() ? AutomationExecutionGuidance.SFTP_CONFLICT_TIMEOUT
                    : job.failure() == null ? "" : "Transfer failed; consult the authorized SFTP log"));
            else truncated = true;
            for (var item : details) {
                if (files.size() >= 512) { truncated = true; break; }
                files.add(item);
            }
            truncated |= job.detailsTruncated();
        }
        String state = conflict ? "WAITING_INPUT" : paused ? "PAUSED" : pending ? "RUNNING"
                : failed ? "FAILED" : canceled ? "CANCELED" : skipped ? "PARTIAL" : "SUCCEEDED";
        // 'after' is a file index for SFTP, not a terminal byte cursor. Results are paged after terminal state.
        int from = (int) Math.min(files.size(), offset);
        int to = from, characters = 0;
        while (to < files.size() && to - from < 128) {
            var item = files.get(to);
            int size = item.source().length() + item.target().length() + item.detail().length() + 100;
            if (to > from && characters + size > maximum / 6) break;
            characters += size; to++;
        }
        return new WorkspaceOperationSnapshot(receipt.operationId(), receipt.workspaceId(), receipt.generation(),
                "SFTP", state, Optional.empty(), Optional.empty(), total, transferred,
                files.subList(from, to), truncated || to < files.size(),
                (conflictTimedOut ? AutomationExecutionGuidance.SFTP_CONFLICT_TIMEOUT + " " : "")
                        + "File offset=" + from + "; next=" + to + "; retained=" + files.size()
                        + ". API completion is not checksum verification. Live file pages may change; page after completion.",
                succeeded, skippedCount, failedCount, to, files.size());
    }

    WorkspaceOperationReceipt mutation(String workspace, String generation, String source, String target,
            java.util.concurrent.CompletionStage<Void> action) {
        String id = UUID.randomUUID().toString();
        var receipt = new WorkspaceOperationReceipt(id, workspace, generation, true, "SFTP", "WORKSPACE");
        var state = new java.util.concurrent.atomic.AtomicReference<>("RUNNING");
        action.whenComplete((v, failure) -> state.set(failure == null ? "SUCCEEDED" : "FAILED"));
        put(new Entry(receipt, (after, max) -> new WorkspaceOperationSnapshot(id, workspace, generation, "SFTP",
                state.get(), Optional.empty(), Optional.empty(), 0, 0,
                List.of(new WorkspaceFileResult(source, target, state.get(), "API acknowledgement; partial effects possible on failure")),
                false, ""), System.nanoTime()));
        return receipt;
    }

    private void put(Entry entry) {
        assertFx();
        expire();
        while (entries.size() >= LIMIT) entries.remove(entries.keySet().iterator().next());
        entries.put(entry.receipt.operationId(), entry);
    }
    List<WorkspaceOperationReceipt> list(String workspace) {
        assertFx(); expire();
        return entries.values().stream().map(Entry::receipt).filter(item -> item.workspaceId().equals(workspace)).toList();
    }
    WorkspaceOperationSnapshot read(String id, long after, int maximum) {
        assertFx(); expire();
        if (after < 0 || maximum < 1 || maximum > 65536) throw new IllegalArgumentException("Invalid result range");
        Entry entry = entries.get(id);
        if (entry == null) throw new IllegalArgumentException("Workspace operation not found or expired; do not repeat the operation");
        return entry.read.apply(after, maximum);
    }
    private void expire() {
        long cutoff = System.nanoTime() - java.util.concurrent.TimeUnit.HOURS.toNanos(2);
        entries.values().removeIf(entry -> entry.created < cutoff);
    }
    void clear() { entries.clear(); }
    private static void assertFx() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Workspace observation requires FX thread");
    }
}
