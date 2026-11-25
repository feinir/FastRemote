// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.List;

/** Non-blocking output port for the sanitized diagnostic stream. */
public interface DiagnosticLogOutput extends AutoCloseable {
    void configure(DiagnosticLogSettings settings);

    Path directory();

    Path defaultDirectory();

    Path currentFile();

    void append(LocalDateTime timestamp, String message);

    CompletionStage<Path> ensureCurrentFile();

    CompletionStage<LogFiles> listFiles();

    CompletionStage<LogFiles> deleteAllFiles();

    @Override
    void close();

    static DiagnosticLogOutput discarding() {
        return new DiagnosticLogOutput() {
            private final Path directory = Path.of("log").toAbsolutePath().normalize();
            @Override public void configure(DiagnosticLogSettings settings) { }
            @Override public Path directory() { return directory; }
            @Override public Path defaultDirectory() { return directory; }
            @Override public Path currentFile() { return directory.resolve("fastremote.log"); }
            @Override public void append(LocalDateTime timestamp, String message) { }
            @Override public CompletionStage<Path> ensureCurrentFile() {
                return CompletableFuture.completedFuture(currentFile());
            }
            @Override public CompletionStage<LogFiles> listFiles() {
                return CompletableFuture.completedFuture(new LogFiles(List.of(), 0L));
            }
            @Override public CompletionStage<LogFiles> deleteAllFiles() { return listFiles(); }
            @Override public void close() { }
        };
    }

    record LogFile(String fileName, long size) {
        public LogFile {
            fileName = java.util.Objects.requireNonNull(fileName, "fileName");
            if (fileName.isBlank()) throw new IllegalArgumentException("fileName is blank");
            if (size < 0L) throw new IllegalArgumentException("size is negative");
        }
    }

    record LogFiles(List<LogFile> files, long totalSize) {
        public LogFiles {
            files = List.copyOf(java.util.Objects.requireNonNull(files, "files"));
            if (totalSize < 0L) throw new IllegalArgumentException("totalSize is negative");
        }
    }
}
