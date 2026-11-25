// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.transfer.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;

/** Decorates explicit file mutations; transfer queue results are collected independently per job. */
final class ObservedRemoteFiles implements RemoteFilesHandle {
    interface Recorder { void record(String source, String target, CompletionStage<Void> result); }
    private final RemoteFilesHandle delegate;
    private final Recorder recorder;
    ObservedRemoteFiles(RemoteFilesHandle delegate, Recorder recorder) { this.delegate = delegate; this.recorder = recorder; }
    private CompletionStage<Void> record(String source, String target, java.util.function.Supplier<CompletionStage<Void>> action) {
        CompletionStage<Void> result;
        try { result = action.get(); }
        catch (RuntimeException error) { result = java.util.concurrent.CompletableFuture.failedFuture(error); }
        recorder.record(source, target, result); return result;
    }
    @Override public CompletionStage<String> canonicalPath(String path) { return delegate.canonicalPath(path); }
    @Override public CompletionStage<List<RemoteFileEntry>> list(String path) { return delegate.list(path); }
    @Override public CompletionStage<RemoteFileAttributes> readAttributes(String path) { return delegate.readAttributes(path); }
    @Override public CompletionStage<byte[]> readFile(String path, int maximum) { return delegate.readFile(path, maximum); }
    @Override public CompletionStage<Void> createDirectory(String path) { return record("mkdir", path, () -> delegate.createDirectory(path)); }
    @Override public CompletionStage<Void> createFile(String path) { return record("create", path, () -> delegate.createFile(path)); }
    @Override public CompletionStage<Void> rename(String source, String target) { return record(source, target, () -> delegate.rename(source, target)); }
    @Override public CompletionStage<Void> setPermissions(String path, int mode) { return record("chmod", path, () -> delegate.setPermissions(path, mode)); }
    @Override public CompletionStage<Void> delete(String path) { return record(path, "delete", () -> delegate.delete(path)); }
    @Override public CompletionStage<Void> delete(String path, TransferActivityListener activity) { return record(path, "delete", () -> delegate.delete(path, activity)); }
    @Override public CompletionStage<Void> writeFileAtomic(String path, byte[] data, boolean overwrite) { return record("write", path, () -> delegate.writeFileAtomic(path, data, overwrite)); }
    @Override public CompletionStage<Void> upload(Path local, String remote) { return record(local.toString(), remote, () -> delegate.upload(local, remote)); }
    @Override public CompletionStage<Void> download(String remote, Path local) { return record(remote, local.toString(), () -> delegate.download(remote, local)); }
    @Override public CompletionStage<Void> uploadTree(Path local, String remote, TransferControl control, TransferProgressListener progress, TransferConflictResolver conflicts) {
        return delegate.uploadTree(local, remote, control, progress, conflicts);
    }
    @Override public CompletionStage<Void> uploadTree(Path local, String remote, TransferControl control, TransferProgressListener progress, TransferConflictResolver conflicts, TransferActivityListener activity, FileTransferScheduler scheduler) {
        return delegate.uploadTree(local, remote, control, progress, conflicts, activity, scheduler);
    }
    @Override public CompletionStage<Void> downloadTree(String remote, Path local, TransferControl control, TransferProgressListener progress, TransferConflictResolver conflicts) {
        return delegate.downloadTree(remote, local, control, progress, conflicts);
    }
    @Override public CompletionStage<Void> downloadTree(String remote, Path local, TransferControl control, TransferProgressListener progress, TransferConflictResolver conflicts, TransferActivityListener activity, FileTransferScheduler scheduler) {
        return delegate.downloadTree(remote, local, control, progress, conflicts, activity, scheduler);
    }
}
