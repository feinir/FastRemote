// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.transfer.FileTransferScheduler;
import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import com.ynpe.fastremote.domain.transfer.RemoteFileAttributes;
import com.ynpe.fastremote.domain.transfer.TransferConflictResolver;
import com.ynpe.fastremote.domain.transfer.TransferControl;
import com.ynpe.fastremote.domain.transfer.TransferActivityListener;
import com.ynpe.fastremote.domain.transfer.TransferProgressListener;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface RemoteFilesHandle {
    CompletionStage<String> canonicalPath(String path);

    CompletionStage<List<RemoteFileEntry>> list(String directory);

    CompletionStage<RemoteFileAttributes> readAttributes(String path);

    CompletionStage<Void> setPermissions(String path, int permissions);

    CompletionStage<Void> upload(Path localFile, String remoteFile);

    CompletionStage<Void> download(String remoteFile, Path localFile);

    CompletionStage<byte[]> readFile(String remoteFile, int maximumBytes);

    CompletionStage<Void> writeFileAtomic(String remoteFile, byte[] content, boolean overwrite);

    CompletionStage<Void> uploadTree(Path localSource, String remoteTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts);

    default CompletionStage<Void> uploadTree(Path localSource, String remoteTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts,
            TransferActivityListener activity) {
        return uploadTree(localSource, remoteTarget, control, progress, conflicts);
    }

    default CompletionStage<Void> uploadTree(Path localSource, String remoteTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts,
            TransferActivityListener activity, FileTransferScheduler scheduler) {
        return uploadTree(localSource, remoteTarget, control, progress, conflicts, activity);
    }

    CompletionStage<Void> downloadTree(String remoteSource, Path localTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts);

    default CompletionStage<Void> downloadTree(String remoteSource, Path localTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts,
            TransferActivityListener activity) {
        return downloadTree(remoteSource, localTarget, control, progress, conflicts);
    }

    default CompletionStage<Void> downloadTree(String remoteSource, Path localTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts,
            TransferActivityListener activity, FileTransferScheduler scheduler) {
        return downloadTree(remoteSource, localTarget, control, progress, conflicts, activity);
    }

    CompletionStage<Void> createDirectory(String path);

    CompletionStage<Void> createFile(String path);

    CompletionStage<Void> delete(String path);

    default CompletionStage<Void> delete(String path, TransferActivityListener activity) {
        return delete(path);
    }

    CompletionStage<Void> rename(String source, String target);

}
