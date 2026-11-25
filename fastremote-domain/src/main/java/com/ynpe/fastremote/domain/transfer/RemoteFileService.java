// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.transfer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface RemoteFileService {
    String canonicalPath(String path) throws IOException;

    List<RemoteFileEntry> list(String directory) throws IOException;

    RemoteFileAttributes readAttributes(String path) throws IOException;

    void setPermissions(String path, int permissions) throws IOException;

    void upload(Path localFile, String remoteFile) throws IOException;

    void download(String remoteFile, Path localFile) throws IOException;

    byte[] readFile(String remoteFile, int maximumBytes) throws IOException;

    void writeFileAtomic(String remoteFile, byte[] content, boolean overwrite) throws IOException;

    void uploadTree(Path localSource, String remoteTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts) throws IOException;

    default void uploadTree(Path localSource, String remoteTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts,
            TransferActivityListener activity) throws IOException {
        uploadTree(localSource, remoteTarget, control, progress, conflicts);
    }

    default void uploadTree(Path localSource, String remoteTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts,
            TransferActivityListener activity, FileTransferScheduler scheduler) throws IOException {
        uploadTree(localSource, remoteTarget, control, progress, conflicts, activity);
    }

    void downloadTree(String remoteSource, Path localTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts) throws IOException;

    default void downloadTree(String remoteSource, Path localTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts,
            TransferActivityListener activity) throws IOException {
        downloadTree(remoteSource, localTarget, control, progress, conflicts);
    }

    default void downloadTree(String remoteSource, Path localTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts,
            TransferActivityListener activity, FileTransferScheduler scheduler) throws IOException {
        downloadTree(remoteSource, localTarget, control, progress, conflicts, activity);
    }

    void createDirectory(String path) throws IOException;

    void createFile(String path) throws IOException;

    void delete(String path) throws IOException;

    default void delete(String path, TransferActivityListener activity) throws IOException {
        delete(path);
    }

    /**
     * Deletes a remote path using at most {@code parallelism} SFTP operations for this service.
     * Implementations that do not perform recursive deletion retain their regular delete behavior.
     */
    default void delete(String path, TransferActivityListener activity, int parallelism) throws IOException {
        delete(path, activity);
    }

    void rename(String source, String target) throws IOException;

}
