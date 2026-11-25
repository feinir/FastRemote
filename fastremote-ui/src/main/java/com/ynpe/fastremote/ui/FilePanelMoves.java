// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Pure drop policy plus asynchronous remote move orchestration through the file port. */
final class FilePanelMoves {
    private FilePanelMoves() { }

    static Path localTarget(LocalFileEntry entry, Path currentDirectory) {
        if (entry == null || SftpBrowserModel.blankTableItem(entry)) return currentDirectory;
        return entry.directory() ? entry.path() : null;
    }

    static String remoteTarget(RemoteFileEntry entry, String currentDirectory) {
        if (entry == null || SftpBrowserModel.blankTableItem(entry)) return currentDirectory;
        return entry.directory() ? entry.path() : null;
    }

    static boolean localAllowed(LocalFileEntry entry, Path directory) {
        Path source = entry.path().toAbsolutePath().normalize();
        Path destination = directory.toAbsolutePath().normalize();
        return !SftpBrowserModel.navigationEntry(entry.name()) && !SftpBrowserModel.blankTableItem(entry)
                && source.getParent() != null && !source.getParent().equals(destination)
                && !source.equals(destination)
                && (!entry.directory() || entry.symbolicLink() || !destination.startsWith(source));
    }

    static boolean remoteAllowed(RemoteFileEntry entry, String directory) {
        String source = normalized(entry.path());
        String destination = normalized(directory);
        return validName(entry.name()) && !SftpBrowserModel.blankTableItem(entry)
                && !source.equals("/") && !source.matches("/?[A-Za-z]:")
                && !normalized(SftpBrowserModel.parentRemotePath(source)).equals(destination)
                && !source.equals(destination)
                && (!entry.directory() || entry.symbolicLink() || !contains(source, destination));
    }

    static CompletionStage<Void> moveRemote(RemoteFilesHandle files, RemoteFileEntry entry, String directory) {
        if (!remoteAllowed(entry, directory)) {
            return CompletableFuture.failedFuture(new IOException("Invalid move destination"));
        }
        return files.canonicalPath(directory).thenCompose(destination -> {
            CompletionStage<String> source = entry.directory() && !entry.symbolicLink()
                    ? files.canonicalPath(entry.path()) : CompletableFuture.completedFuture(null);
            return source.thenCompose(canonicalSource -> {
                if (canonicalSource != null && contains(normalized(canonicalSource), normalized(destination))) {
                    return CompletableFuture.failedFuture(new IOException("A directory cannot be moved into itself"));
                }
                String target = SftpBrowserModel.joinRemotePath(destination, entry.name());
                // Listing also proves that the target is a directory, and includes hidden names.
                return files.list(destination).thenCompose(entries -> {
                    if (entries.stream().anyMatch(existing -> existing.name().equals(entry.name()))) {
                        return CompletableFuture.failedFuture(new FileAlreadyExistsException(target));
                    }
                    // Standard SFTP rename does not request overwrite. No download/delete fallback.
                    return files.rename(entry.path(), target);
                });
            });
        });
    }

    static Path localLocationAfterMove(Path current, LocalFileEntry entry, Path directory) {
        Path source = entry.path().toAbsolutePath().normalize();
        Path location = current.toAbsolutePath().normalize();
        return location.startsWith(source)
                ? directory.resolve(source.getFileName()).resolve(source.relativize(location)) : current;
    }

    static String remoteLocationAfterMove(String current, RemoteFileEntry entry, String directory) {
        String source = normalized(entry.path());
        String location = normalized(current);
        return contains(source, location)
                ? SftpBrowserModel.joinRemotePath(directory, entry.name()) + location.substring(source.length())
                : current;
    }

    static String failureMessage(String name, Throwable error) {
        Throwable cause = error;
        while (cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) {
            if (cause.getCause() == null) break;
            cause = cause.getCause();
        }
        return com.ynpe.fastremote.i18n.Texts.text(cause instanceof FileAlreadyExistsException
                ? "fileMove.conflict" : "fileMove.failed") + " " + name;
    }

    private static boolean validName(String name) {
        return !name.isBlank() && !name.equals(".") && !name.equals("..")
                && name.indexOf('/') < 0 && name.indexOf('\\') < 0 && name.indexOf('\0') < 0;
    }

    private static boolean contains(String parent, String child) {
        return parent.equals("/") || parent.equals(child) || child.startsWith(parent + "/");
    }

    static String normalized(String path) {
        ArrayDeque<String> segments = new ArrayDeque<>();
        for (String segment : path.replace('\\', '/').split("/")) {
            if (segment.isEmpty() || segment.equals(".")) continue;
            if (segment.equals("..")) { if (!segments.isEmpty()) segments.removeLast(); }
            else segments.add(segment);
        }
        return "/" + String.join("/", segments);
    }
}
