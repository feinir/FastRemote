// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.transfer.TransferActivityListener;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Bounded local-filesystem operations used by local and SFTP file views. */
public interface LocalFileWorkflow {
    CompletionStage<List<LocalFileEntry>> localRoots();

    CompletionStage<List<LocalFileEntry>> listLocal(Path directory);

    CompletionStage<Void> createLocalDirectory(Path path);

    CompletionStage<Void> createLocalFile(Path path);

    CompletionStage<Void> deleteLocal(Path path);

    default CompletionStage<Void> deleteLocal(Path path, TransferActivityListener activity) {
        return deleteLocal(path);
    }

    CompletionStage<Void> renameLocal(Path source, Path target);

    default CompletionStage<Void> copyLocal(Path source, Path target, boolean replaceExisting) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Local copy is unavailable"));
    }

    default CompletionStage<Void> moveLocal(Path source, Path target, boolean replaceExisting) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Local move is unavailable"));
    }

    default CompletionStage<Boolean> moveLocalToTrash(Path path) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("System trash is unavailable"));
    }

    default CompletionStage<Void> openLocal(Path path) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Opening local files is unavailable"));
    }

    default CompletionStage<byte[]> readLocalFile(Path path, int maximumBytes) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Local file editing is unavailable"));
    }

    default CompletionStage<Void> writeLocalFileAtomic(Path path, byte[] content) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Local file editing is unavailable"));
    }

    default CompletionStage<Path> createEditorTemporaryFile(byte[] content) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Editor temporary storage is unavailable"));
    }

    default CompletionStage<Void> deleteEditorTemporaryFile(Path path) {
        return CompletableFuture.completedFuture(null);
    }

    CompletionStage<Void> revealLocal(Path path, boolean selectFile);
}
