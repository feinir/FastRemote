// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.domain.settings.DiagnosticLogOutput;
import com.ynpe.fastremote.domain.settings.DiagnosticLogSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Writes the sanitized diagnostic stream to one UTF-8 file per local calendar date. */
public final class DailyFileDiagnosticLogOutput implements DiagnosticLogOutput {
    static final int MAX_PENDING_WRITES = 2_048;
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final java.util.regex.Pattern OWNED_LOG_FILE =
            java.util.regex.Pattern.compile("fastremote-\\d{4}-\\d{2}-\\d{2}\\.log");

    private final Path defaultDirectory;
    private final Clock clock;
    private final ThreadPoolExecutor writer;
    private final AtomicReference<DiagnosticLogSettings> settings =
            new AtomicReference<>(DiagnosticLogSettings.defaults());
    private final AtomicLong droppedWrites = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    public DailyFileDiagnosticLogOutput(Path globalConfigPath) {
        this(globalConfigPath, Clock.systemDefaultZone());
    }

    DailyFileDiagnosticLogOutput(Path globalConfigPath, Clock clock) {
        Path absoluteConfig = Objects.requireNonNull(globalConfigPath, "globalConfigPath")
                .toAbsolutePath().normalize();
        Path parent = absoluteConfig.getParent();
        this.defaultDirectory = (parent == null ? Path.of(".") : parent).resolve("log")
                .toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_WRITES), runnable -> {
                    Thread thread = new Thread(runnable, "fastremote-diagnostic-log-writer");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void configure(DiagnosticLogSettings value) {
        settings.set(Objects.requireNonNull(value, "value"));
        if (value.enabled()) enqueue(() -> createCurrentFile(value));
    }

    @Override
    public Path directory() {
        return resolveDirectory(settings.get());
    }

    @Override
    public Path defaultDirectory() {
        return defaultDirectory;
    }

    @Override
    public Path currentFile() {
        return fileFor(directory(), LocalDate.now(clock));
    }

    @Override
    public void append(LocalDateTime timestamp, String message) {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(message, "message");
        DiagnosticLogSettings snapshot = settings.get();
        if (!snapshot.enabled() || closed.get()) return;
        enqueue(() -> write(snapshot, timestamp, message));
    }

    @Override
    public CompletionStage<Path> ensureCurrentFile() {
        CompletableFuture<Path> result = new CompletableFuture<>();
        DiagnosticLogSettings snapshot = settings.get();
        if (!enqueue(() -> {
            try {
                Path file = fileFor(resolveDirectory(snapshot), LocalDate.now(clock));
                if (snapshot.enabled()) createFile(file);
                else Files.createDirectories(file.getParent());
                result.complete(file);
            } catch (IOException failure) {
                result.completeExceptionally(failure);
            }
        })) {
            result.completeExceptionally(new IOException("Diagnostic log queue is full"));
        }
        return result;
    }

    @Override
    public CompletionStage<LogFiles> listFiles() {
        CompletableFuture<LogFiles> result = new CompletableFuture<>();
        Path directory = directory();
        if (!enqueue(() -> completeLogFileOperation(result, () -> scanLogFiles(directory)))) {
            result.completeExceptionally(new IOException("Diagnostic log queue is full"));
        }
        return result;
    }

    @Override
    public CompletionStage<LogFiles> deleteAllFiles() {
        CompletableFuture<LogFiles> result = new CompletableFuture<>();
        Path directory = directory();
        if (!enqueue(() -> completeLogFileOperation(result, () -> {
            LogFiles files = scanLogFiles(directory);
            for (LogFile file : files.files()) {
                Files.deleteIfExists(directory.resolve(file.fileName()));
            }
            return scanLogFiles(directory);
        }))) {
            result.completeExceptionally(new IOException("Diagnostic log queue is full"));
        }
        return result;
    }

    private void write(DiagnosticLogSettings snapshot, LocalDateTime timestamp, String message) {
        try {
            Path file = fileFor(resolveDirectory(snapshot), timestamp.toLocalDate());
            createFile(file);
            long dropped = droppedWrites.getAndSet(0L);
            StringBuilder line = new StringBuilder();
            if (dropped > 0L) {
                line.append('[').append(LINE_TIME.format(timestamp)).append("] ")
                        .append("[FastRemote] Dropped ").append(dropped)
                        .append(" diagnostic entries because the disk queue was full")
                        .append(System.lineSeparator());
            }
            line.append('[').append(LINE_TIME.format(timestamp)).append("] ")
                    .append(message).append(System.lineSeparator());
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            System.getLogger(DailyFileDiagnosticLogOutput.class.getName()).log(
                    System.Logger.Level.WARNING, "Unable to persist the diagnostic log", failure);
        }
    }

    private void createCurrentFile(DiagnosticLogSettings snapshot) {
        try {
            createFile(fileFor(resolveDirectory(snapshot), LocalDate.now(clock)));
        } catch (IOException failure) {
            System.getLogger(DailyFileDiagnosticLogOutput.class.getName()).log(
                    System.Logger.Level.WARNING, "Unable to prepare the diagnostic log file", failure);
        }
    }

    private static void createFile(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        if (Files.notExists(file)) Files.createFile(file);
    }

    private Path resolveDirectory(DiagnosticLogSettings snapshot) {
        if (snapshot.directory().isBlank()) return defaultDirectory;
        try {
            Path configured = Path.of(snapshot.directory());
            return (configured.isAbsolute() ? configured : defaultDirectory.getParent().resolve(configured))
                    .toAbsolutePath().normalize();
        } catch (RuntimeException invalid) {
            return defaultDirectory;
        }
    }

    private static Path fileFor(Path directory, LocalDate date) {
        return directory.resolve("fastremote-" + FILE_DATE.format(date) + ".log");
    }

    private static LogFiles scanLogFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return new LogFiles(List.of(), 0L);
        List<LogFile> files;
        try (var entries = Files.list(directory)) {
            files = entries.filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> OWNED_LOG_FILE.matcher(path.getFileName().toString()).matches())
                    .map(path -> {
                        try {
                            return new LogFile(path.getFileName().toString(), Files.size(path));
                        } catch (IOException failure) {
                            throw new java.io.UncheckedIOException(failure);
                        }
                    })
                    .sorted(Comparator.comparing(LogFile::fileName).reversed())
                    .toList();
        } catch (java.io.UncheckedIOException failure) {
            throw failure.getCause();
        }
        long total = 0L;
        for (LogFile file : files) total = Math.addExact(total, file.size());
        return new LogFiles(files, total);
    }

    private static <T> void completeLogFileOperation(
            CompletableFuture<T> result, IoSupplier<T> operation) {
        try {
            result.complete(operation.get());
        } catch (IOException | RuntimeException failure) {
            result.completeExceptionally(failure);
        }
    }

    private boolean enqueue(Runnable action) {
        if (closed.get()) return false;
        try {
            writer.execute(action);
            return true;
        } catch (RejectedExecutionException full) {
            droppedWrites.incrementAndGet();
            return false;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException interrupted) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
