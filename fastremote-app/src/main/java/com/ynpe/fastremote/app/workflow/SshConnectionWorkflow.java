// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.ConnectionHop;
import com.ynpe.fastremote.domain.connection.Credentials;
import com.ynpe.fastremote.domain.connection.SessionConnector;
import com.ynpe.fastremote.domain.connection.ConnectionProgress;
import com.ynpe.fastremote.domain.security.HostKeyPrompt;
import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.settings.ConnectionSettings;
import com.ynpe.fastremote.domain.settings.SftpSettings;
import com.ynpe.fastremote.domain.profile.ConnectionProfileRepository;
import com.ynpe.fastremote.terminal.FastRemoteTerminal;
import com.ynpe.fastremote.ui.ConnectionWorkflow;
import com.ynpe.fastremote.ui.ConnectionAttempt;
import com.ynpe.fastremote.ui.ConnectionTestAttempt;
import com.ynpe.fastremote.ui.LocalFileEntry;
import com.ynpe.fastremote.ui.TerminalHandle;
import com.ynpe.fastremote.ui.ServerOverview;
import com.ynpe.fastremote.ui.RemoteFilesHandle;
import com.ynpe.fastremote.ui.SftpConnectionAttempt;
import com.ynpe.fastremote.ui.SftpHandle;
import com.ynpe.fastremote.domain.transfer.SftpConnection;
import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import com.ynpe.fastremote.domain.transfer.RemoteFileAttributes;
import com.ynpe.fastremote.domain.transfer.RemoteFileService;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;

public final class SshConnectionWorkflow implements ConnectionWorkflow {
    private static final int MAX_LOCAL_DIRECTORY_ENTRIES = 20_000;
    private final SessionConnector connector;
    private final ConnectionProfileRepository profiles;
    private final ThreadPoolExecutor executor;
    private final ThreadPoolExecutor fileExecutor;
    private final ThreadPoolExecutor deleteExecutor;
    private final Set<AttemptState> attempts = ConcurrentHashMap.newKeySet();
    private final Set<TestAttemptState> testAttempts = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Consumer<Runnable> uiDispatcher;
    private final BooleanSupplier uiThread;
    private final AtomicReference<TerminalPreferences> terminalPreferences =
            new AtomicReference<>(TerminalPreferences.defaults());
    private final AtomicReference<ConnectionSettings> connectionSettings =
            new AtomicReference<>(ConnectionSettings.defaults());
    private final AtomicReference<SftpSettings> sftpSettings =
            new AtomicReference<>(SftpSettings.defaults());

    public SshConnectionWorkflow(SessionConnector connector, ConnectionProfileRepository profiles) {
        this(connector, profiles, Platform::runLater, Platform::isFxApplicationThread);
    }

    SshConnectionWorkflow(
            SessionConnector connector,
            ConnectionProfileRepository profiles,
            Consumer<Runnable> uiDispatcher,
            BooleanSupplier uiThread) {
        this.connector = Objects.requireNonNull(connector, "connector");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.uiDispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher");
        this.uiThread = Objects.requireNonNull(uiThread, "uiThread");
        executor = new ThreadPoolExecutor(
                2,
                4,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(16),
                Thread.ofPlatform().name("fastremote-connection-", 0).daemon(false).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        fileExecutor = new ThreadPoolExecutor(
                0,
                32,
                30L,
                TimeUnit.SECONDS,
                new java.util.concurrent.SynchronousQueue<>(),
                Thread.ofPlatform().name("fastremote-file-", 0).daemon(false).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        deleteExecutor = new ThreadPoolExecutor(
                0, 32, 30L, TimeUnit.SECONDS, new java.util.concurrent.SynchronousQueue<>(),
                Thread.ofPlatform().name("fastremote-delete-", 0).daemon(false).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void updateTerminalPreferences(TerminalPreferences preferences) {
        terminalPreferences.set(Objects.requireNonNull(preferences, "preferences"));
    }

    @Override
    public void updateConnectionSettings(ConnectionSettings settings) {
        connectionSettings.set(Objects.requireNonNull(settings, "settings"));
    }

    @Override
    public void updateSftpSettings(SftpSettings settings) {
        sftpSettings.set(Objects.requireNonNull(settings, "settings"));
    }

    @Override
    public CompletionStage<Optional<ConnectionProfile>> loadLastProfile() {
        if (closed.get()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return profiles.loadLastProfile();
            } catch (java.io.IOException failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }, executor);
    }

    @Override
    public CompletionStage<List<LocalFileEntry>> localRoots() {
        return submitLocalFileTask(() -> {
            java.io.File[] roots = java.io.File.listRoots();
            if (roots == null) return List.of();
            return localRootEntries(java.util.Arrays.stream(roots)
                    .map(java.io.File::toPath)
                    .toList());
        });
    }

    static List<LocalFileEntry> localRootEntries(Iterable<Path> roots) {
        List<LocalFileEntry> entries = new ArrayList<>();
        for (Path root : roots) {
            Path normalized = root.toAbsolutePath().normalize();
            String name = normalized.toString();
            if (name.isBlank()) name = root.toString();
            if (name.isBlank()) continue;
            // Do not read attributes here. Windows can report removable or mapped drive roots
            // whose media is not ready; probing any one of them must not hide every local drive.
            entries.add(new LocalFileEntry(name, normalized, true, false, 0L,
                    Optional.empty(), Optional.empty(), false));
        }
        return List.copyOf(entries);
    }

    @Override
    public CompletionStage<List<LocalFileEntry>> listLocal(Path directory) {
        Objects.requireNonNull(directory, "directory");
        return submitLocalFileTask(() -> {
            List<LocalFileEntry> entries = new ArrayList<>();
            try (var children = Files.list(directory)) {
                var iterator = children.iterator();
                while (iterator.hasNext() && entries.size() < MAX_LOCAL_DIRECTORY_ENTRIES) {
                    Path child = iterator.next();
                    try {
                        entries.add(localEntry(child));
                    } catch (java.io.IOException ignored) {
                        // An unreadable child must not make the whole directory unusable.
                    }
                }
            }
            entries.sort(Comparator.comparing(LocalFileEntry::directory).reversed()
                    .thenComparing(LocalFileEntry::name, String.CASE_INSENSITIVE_ORDER));
            return List.copyOf(entries);
        });
    }

    @Override
    public CompletionStage<Void> createLocalDirectory(Path path) {
        return submitLocalFileTask(() -> { Files.createDirectory(path); return null; });
    }

    @Override
    public CompletionStage<Void> createLocalFile(Path path) {
        return submitLocalFileTask(() -> { Files.createFile(path); return null; });
    }

    @Override
    public CompletionStage<Void> deleteLocal(Path path) {
        return deleteLocal(path, com.ynpe.fastremote.domain.transfer.TransferActivityListener.NONE);
    }

    @Override
    public CompletionStage<Void> deleteLocal(Path path,
            com.ynpe.fastremote.domain.transfer.TransferActivityListener activity) {
        return submitLocalFileTask(() -> { deleteLocalRecursively(path, activity); return null; });
    }

    @Override
    public CompletionStage<Void> renameLocal(Path source, Path target) {
        return submitLocalFileTask(() -> { Files.move(source, target); return null; });
    }

    @Override
    public CompletionStage<Void> copyLocal(Path source, Path target, boolean replaceExisting) {
        return submitLocalFileTask(() -> { copyLocalRecursively(source, target, replaceExisting); return null; });
    }

    @Override
    public CompletionStage<Void> moveLocal(Path source, Path target, boolean replaceExisting) {
        return submitLocalFileTask(() -> {
            // Resolve the destination parent as well: a symlink must not bypass the subtree guard.
            Path realParent = target.toAbsolutePath().getParent().toRealPath();
            if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)
                    && realParent.startsWith(source.toRealPath())) {
                throw new IOException("A directory cannot be moved into itself");
            }
            if (!replaceExisting && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new java.nio.file.FileAlreadyExistsException(target.toString());
            }
            try {
                if (replaceExisting) Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(source, target);
            } catch (java.nio.file.FileSystemException crossFileSystem) {
                // Permission errors and name conflicts must never start a copy/delete fallback.
                if (Files.getFileStore(source).equals(Files.getFileStore(realParent))) throw crossFileSystem;
                copyLocalRecursively(source, target, replaceExisting);
                deleteLocalRecursively(source,
                        com.ynpe.fastremote.domain.transfer.TransferActivityListener.NONE);
            }
            return null;
        });
    }

    @Override
    public CompletionStage<Boolean> moveLocalToTrash(Path path) {
        return submitLocalFileTask(() -> {
            if (!java.awt.Desktop.isDesktopSupported()) return false;
            return java.awt.Desktop.getDesktop().moveToTrash(path.toFile());
        });
    }

    @Override
    public CompletionStage<Void> openLocal(Path path) {
        return submitLocalFileTask(() -> {
            if (!java.awt.Desktop.isDesktopSupported()) throw new IOException("Desktop integration is unavailable");
            java.awt.Desktop.getDesktop().open(path.toFile());
            return null;
        });
    }

    @Override
    public CompletionStage<byte[]> readLocalFile(Path path, int maximumBytes) {
        if (maximumBytes < 0) return CompletableFuture.failedFuture(
                new IllegalArgumentException("maximumBytes must not be negative"));
        return submitLocalFileTask(() -> {
            try (var input = Files.newInputStream(path)) {
                byte[] content = input.readNBytes(maximumBytes + 1);
                if (content.length > maximumBytes) throw new IOException("File exceeds editor size limit");
                return content;
            }
        });
    }

    @Override
    public CompletionStage<Void> writeLocalFileAtomic(Path path, byte[] content) {
        byte[] copy = content.clone();
        return submitLocalFileTask(() -> {
            Path absolute = path.toAbsolutePath().normalize();
            Path parent = absolute.getParent();
            if (parent == null) throw new IOException("File has no parent directory");
            Path temporary = Files.createTempFile(parent, ".fastremote-editor-", ".tmp");
            try {
                Files.write(temporary, copy);
                try {
                    Files.move(temporary, absolute, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, absolute, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return null;
            } finally {
                java.util.Arrays.fill(copy, (byte) 0);
                Files.deleteIfExists(temporary);
            }
        });
    }

    @Override
    public CompletionStage<Path> createEditorTemporaryFile(byte[] content) {
        byte[] copy = java.util.Objects.requireNonNull(content, "content").clone();
        return submitLocalFileTask(() -> {
            Path temporary = Files.createTempFile("fastremote-remote-editor-", ".tmp");
            try {
                Files.write(temporary, copy, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                return temporary;
            } catch (IOException failure) {
                Files.deleteIfExists(temporary);
                throw failure;
            } finally {
                java.util.Arrays.fill(copy, (byte) 0);
            }
        });
    }

    @Override
    public CompletionStage<Void> deleteEditorTemporaryFile(Path path) {
        return submitLocalFileTask(() -> { Files.deleteIfExists(path); return null; });
    }

    @Override
    public CompletionStage<Void> revealLocal(Path path, boolean selectFile) {
        return submitLocalFileTask(() -> {
            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            List<String> command;
            if (os.contains("mac")) {
                command = selectFile ? List.of("open", "-R", path.toString()) : List.of("open", path.toString());
            } else if (os.contains("win")) {
                command = selectFile ? List.of("explorer.exe", "/select," + path)
                        : List.of("explorer.exe", path.toString());
            } else {
                Path target = selectFile && path.getParent() != null ? path.getParent() : path;
                command = List.of("xdg-open", target.toString());
            }
            new ProcessBuilder(command).start();
            return null;
        });
    }

    private static void deleteLocalRecursively(Path path,
            com.ynpe.fastremote.domain.transfer.TransferActivityListener activity) throws java.io.IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        com.ynpe.fastremote.domain.transfer.TransferActivity.ObjectType type = attributes.isSymbolicLink()
                ? com.ynpe.fastremote.domain.transfer.TransferActivity.ObjectType.SYMBOLIC_LINK
                : attributes.isDirectory()
                        ? com.ynpe.fastremote.domain.transfer.TransferActivity.ObjectType.DIRECTORY
                        : com.ynpe.fastremote.domain.transfer.TransferActivity.ObjectType.FILE;
        activity.processing(new com.ynpe.fastremote.domain.transfer.TransferActivity(
                com.ynpe.fastremote.domain.transfer.TransferActivity.Operation.DELETE,
                type, path.toString(), path.toString()));
        if (attributes.isDirectory() && !attributes.isSymbolicLink()) {
            try (var children = Files.list(path)) {
                for (Path child : children.toList()) deleteLocalRecursively(child, activity);
            }
        }
        Files.delete(path);
    }

    private static void copyLocalRecursively(Path source, Path target, boolean replaceExisting)
            throws IOException {
        Path absoluteSource = source.toAbsolutePath().normalize();
        Path absoluteTarget = target.toAbsolutePath().normalize();
        if (Files.isDirectory(absoluteSource, LinkOption.NOFOLLOW_LINKS)
                && absoluteTarget.startsWith(absoluteSource)) {
            throw new IOException("A directory cannot be copied into itself");
        }
        if (replaceExisting && Files.exists(absoluteTarget, LinkOption.NOFOLLOW_LINKS)) {
            deleteLocalRecursively(absoluteTarget,
                    com.ynpe.fastremote.domain.transfer.TransferActivityListener.NONE);
        }
        java.nio.file.CopyOption[] options = replaceExisting
                ? new java.nio.file.CopyOption[] {StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS}
                : new java.nio.file.CopyOption[] {StandardCopyOption.COPY_ATTRIBUTES,
                    LinkOption.NOFOLLOW_LINKS};
        if (!Files.isDirectory(absoluteSource, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(absoluteSource)) {
            Files.copy(absoluteSource, absoluteTarget, options);
            return;
        }
        Files.walkFileTree(absoluteSource, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class),
                Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                            throws IOException {
                        Path destination = absoluteTarget.resolve(absoluteSource.relativize(directory));
                        if (replaceExisting) Files.copy(directory, destination, options);
                        else Files.createDirectory(destination);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Files.copy(file, absoluteTarget.resolve(absoluteSource.relativize(file)), options);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    private static LocalFileEntry localEntry(Path path) throws java.io.IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Path fileName = path.getFileName();
        String name = fileName == null ? path.toString() : fileName.toString();
        if (name.isBlank()) name = path.toAbsolutePath().toString();
        Instant modified = attributes.lastModifiedTime().toInstant();
        return new LocalFileEntry(name, path.toAbsolutePath().normalize(),
                attributes.isDirectory(), attributes.isSymbolicLink(),
                attributes.isDirectory() ? 0L : attributes.size(),
                Optional.of(modified), Optional.of(attributes.creationTime().toInstant()),
                Files.isHidden(path));
    }

    private <T> CompletionStage<T> submitLocalFileTask(FileTask<T> task) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Application is shutting down"));
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return task.run();
                } catch (java.io.IOException failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            }, executor);
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.failedFuture(new IllegalStateException("File operation queue is full", rejected));
        }
    }

    @Override
    public ConnectionAttempt connect(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(hostKeyPrompt, "hostKeyPrompt");
        Objects.requireNonNull(progress, "progress");
        if (!uiThread.getAsBoolean()) {
            credentials.close();
            return failedAttempt(new IllegalStateException(
                    "Connection workflow must be started on the JavaFX thread"));
        }
        if (closed.get()) {
            credentials.close();
            return failedAttempt(new IllegalStateException("Application is shutting down"));
        }

        AttemptState attempt = new AttemptState();
        attempts.add(attempt);
        attempt.result.whenComplete((ignored, failure) -> attempts.remove(attempt));
        try {
            Future<?> task = executor.submit(() -> connectInBackground(
                    profile, credentials, hostKeyPrompt, progress, attempt));
            attempt.installTask(task);
        } catch (RejectedExecutionException rejected) {
            credentials.close();
            attempt.result.completeExceptionally(new IllegalStateException("Connection queue is full", rejected));
        }
        return new ConnectionAttempt(attempt.result, attempt::cancel);
    }

    @Override
    public ConnectionAttempt connect(
            List<ConnectionHop> route, HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress) {
        List<ConnectionHop> safeRoute = List.copyOf(Objects.requireNonNull(route, "route"));
        Objects.requireNonNull(hostKeyPrompt, "hostKeyPrompt");
        Objects.requireNonNull(progress, "progress");
        if (!uiThread.getAsBoolean() || closed.get()) {
            closeRouteCredentials(safeRoute);
            return failedAttempt(new IllegalStateException("Routed connection cannot be started"));
        }
        AttemptState attempt = new AttemptState();
        attempts.add(attempt);
        attempt.result.whenComplete((ignored, failure) -> attempts.remove(attempt));
        try {
            Future<?> task = executor.submit(() -> connectRouteInBackground(
                    safeRoute, hostKeyPrompt, progress, attempt));
            attempt.installTask(task);
        } catch (RejectedExecutionException rejected) {
            closeRouteCredentials(safeRoute);
            attempt.result.completeExceptionally(new IllegalStateException("Connection queue is full", rejected));
        }
        return new ConnectionAttempt(attempt.result, attempt::cancel);
    }

    private static ConnectionAttempt failedAttempt(Throwable failure) {
        return new ConnectionAttempt(CompletableFuture.failedFuture(failure), () -> {});
    }

    @Override
    public ConnectionTestAttempt testConnection(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(hostKeyPrompt, "hostKeyPrompt");
        Objects.requireNonNull(progress, "progress");
        if (!uiThread.getAsBoolean() || closed.get()) {
            credentials.close();
            return failedTestAttempt(new IllegalStateException("Connection test cannot be started"));
        }
        TestAttemptState attempt = new TestAttemptState(credentials::close);
        testAttempts.add(attempt);
        attempt.result.whenComplete((ignored, failure) -> testAttempts.remove(attempt));
        try {
            Future<?> task = executor.submit(() -> runConnectionTest(
                    profile, credentials, hostKeyPrompt, progress, attempt));
            attempt.installTask(task);
        } catch (RejectedExecutionException rejected) {
            credentials.close();
            attempt.result.completeExceptionally(new IllegalStateException("Connection queue is full", rejected));
        }
        return new ConnectionTestAttempt(attempt.result, attempt::cancel);
    }

    @Override
    public ConnectionTestAttempt testConnection(
            List<ConnectionHop> route,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress) {
        List<ConnectionHop> safeRoute = List.copyOf(Objects.requireNonNull(route, "route"));
        Objects.requireNonNull(hostKeyPrompt, "hostKeyPrompt");
        Objects.requireNonNull(progress, "progress");
        if (!uiThread.getAsBoolean() || closed.get()) {
            closeRouteCredentials(safeRoute);
            return failedTestAttempt(new IllegalStateException("Routed connection test cannot be started"));
        }
        TestAttemptState attempt = new TestAttemptState(() -> closeRouteCredentials(safeRoute));
        testAttempts.add(attempt);
        attempt.result.whenComplete((ignored, failure) -> testAttempts.remove(attempt));
        try {
            Future<?> task = executor.submit(() -> runRoutedConnectionTest(
                    safeRoute, hostKeyPrompt, progress, attempt));
            attempt.installTask(task);
        } catch (RejectedExecutionException rejected) {
            closeRouteCredentials(safeRoute);
            attempt.result.completeExceptionally(new IllegalStateException("Connection queue is full", rejected));
        }
        return new ConnectionTestAttempt(attempt.result, attempt::cancel);
    }

    private static ConnectionTestAttempt failedTestAttempt(Throwable failure) {
        return new ConnectionTestAttempt(CompletableFuture.failedFuture(failure), () -> {});
    }

    private void runConnectionTest(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            TestAttemptState attempt) {
        Throwable outcomeFailure = null;
        try {
            connector.testConnection(profile, credentials, hostKeyPrompt,
                    milestone -> uiDispatcher.accept(() -> progress.accept(milestone)), connectionSettings.get());
        } catch (com.ynpe.fastremote.domain.connection.ConnectionException | RuntimeException failure) {
            outcomeFailure = failure;
        } finally {
            credentials.close();
        }
        Throwable completedFailure = outcomeFailure;
        uiDispatcher.accept(() -> {
            if (completedFailure == null) attempt.result.complete(null);
            else attempt.result.completeExceptionally(completedFailure);
        });
    }

    private void runRoutedConnectionTest(
            List<ConnectionHop> route,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            TestAttemptState attempt) {
        Throwable outcomeFailure = null;
        try {
            connector.testConnection(route, hostKeyPrompt,
                    milestone -> uiDispatcher.accept(() -> progress.accept(milestone)), connectionSettings.get());
        } catch (com.ynpe.fastremote.domain.connection.ConnectionException | RuntimeException failure) {
            outcomeFailure = failure;
        } finally {
            closeRouteCredentials(route);
        }
        Throwable completedFailure = outcomeFailure;
        uiDispatcher.accept(() -> {
            if (completedFailure == null) attempt.result.complete(null);
            else attempt.result.completeExceptionally(completedFailure);
        });
    }

    @Override
    public SftpConnectionAttempt connectSftp(ConnectionProfile profile, Credentials credentials,
            HostKeyPrompt hostKeyPrompt, Consumer<ConnectionProgress> progress) {
        if (!uiThread.getAsBoolean() || closed.get()) {
            credentials.close();
            return new SftpConnectionAttempt(CompletableFuture.failedFuture(
                    new IllegalStateException("SFTP connection cannot be started")), () -> {});
        }
        SftpAttemptState attempt = new SftpAttemptState();
        try {
            Future<?> task = executor.submit(() -> {
                SftpConnection connection = null;
                try {
                    connection = connector.connectSftp(profile, credentials, hostKeyPrompt,
                            milestone -> uiDispatcher.accept(() -> progress.accept(milestone)),
                            connectionSettings.get());
                    SftpConnection connected = connection;
                    uiDispatcher.accept(() -> {
                        if (attempt.result.isDone()) connected.close();
                        else attempt.result.complete(new SftpHandle(
                                new AsyncRemoteFiles(connected.remoteFiles()), connected::close,
                                connected.termination()));
                    });
                    connection = null;
                } catch (RuntimeException | com.ynpe.fastremote.domain.connection.ConnectionException failure) {
                    uiDispatcher.accept(() -> attempt.result.completeExceptionally(failure));
                } finally {
                    if (connection != null) connection.close();
                    credentials.close();
                }
            });
            attempt.installTask(task);
        } catch (RejectedExecutionException rejected) {
            credentials.close();
            attempt.result.completeExceptionally(new IllegalStateException("Connection queue is full", rejected));
        }
        return new SftpConnectionAttempt(attempt.result, attempt::cancel);
    }

    @Override
    public SftpConnectionAttempt connectSftp(
            List<ConnectionHop> route, HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress) {
        List<ConnectionHop> safeRoute = List.copyOf(Objects.requireNonNull(route, "route"));
        if (!uiThread.getAsBoolean() || closed.get()) {
            closeRouteCredentials(safeRoute);
            return new SftpConnectionAttempt(CompletableFuture.failedFuture(
                    new IllegalStateException("Routed SFTP connection cannot be started")), () -> {});
        }
        SftpAttemptState attempt = new SftpAttemptState();
        try {
            Future<?> task = executor.submit(() -> {
                SftpConnection connection = null;
                try {
                    connection = connector.connectSftp(safeRoute, hostKeyPrompt,
                            milestone -> uiDispatcher.accept(() -> progress.accept(milestone)),
                            connectionSettings.get());
                    SftpConnection connected = connection;
                    uiDispatcher.accept(() -> {
                        if (attempt.result.isDone()) connected.close();
                        else attempt.result.complete(new SftpHandle(
                                new AsyncRemoteFiles(connected.remoteFiles()), connected::close,
                                connected.termination()));
                    });
                    connection = null;
                } catch (RuntimeException | com.ynpe.fastremote.domain.connection.ConnectionException failure) {
                    uiDispatcher.accept(() -> attempt.result.completeExceptionally(failure));
                } finally {
                    if (connection != null) connection.close();
                    closeRouteCredentials(safeRoute);
                }
            });
            attempt.installTask(task);
        } catch (RejectedExecutionException rejected) {
            closeRouteCredentials(safeRoute);
            attempt.result.completeExceptionally(new IllegalStateException("Connection queue is full", rejected));
        }
        return new SftpConnectionAttempt(attempt.result, attempt::cancel);
    }

    private void connectInBackground(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            AttemptState attempt) {
        TerminalConnection connection = null;
        try {
            TerminalPreferences preferences = terminalPreferences.get();
            connection = connector.connect(
                    profile,
                    credentials,
                    hostKeyPrompt,
                    milestone -> uiDispatcher.accept(() -> progress.accept(milestone)),
                    connectionSettings.get(), preferences);
            try {
                profiles.saveLastProfile(profile);
            } catch (java.io.IOException storageFailure) {
                System.getLogger(SshConnectionWorkflow.class.getName())
                        .log(System.Logger.Level.WARNING, "Unable to save the last connection profile", storageFailure);
            }
            TerminalConnection connected = connection;
            uiDispatcher.accept(() -> finishOnFxThread(connected, attempt, preferences));
            connection = null;
        } catch (com.ynpe.fastremote.domain.connection.ConnectionException | RuntimeException failure) {
            Throwable reported = failure;
            uiDispatcher.accept(() -> attempt.result.completeExceptionally(reported));
        } finally {
            if (connection != null) {
                connection.close();
            }
            credentials.close();
        }
    }

    private void connectRouteInBackground(
            List<ConnectionHop> route,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            AttemptState attempt) {
        TerminalConnection connection = null;
        try {
            TerminalPreferences preferences = terminalPreferences.get();
            connection = connector.connect(
                    route, hostKeyPrompt,
                    milestone -> uiDispatcher.accept(() -> progress.accept(milestone)),
                    connectionSettings.get(), preferences);
            TerminalConnection connected = connection;
            uiDispatcher.accept(() -> finishOnFxThread(connected, attempt, preferences));
            connection = null;
        } catch (com.ynpe.fastremote.domain.connection.ConnectionException | RuntimeException failure) {
            uiDispatcher.accept(() -> attempt.result.completeExceptionally(failure));
        } finally {
            if (connection != null) connection.close();
            closeRouteCredentials(route);
        }
    }

    private static void closeRouteCredentials(List<ConnectionHop> route) {
        route.forEach(hop -> hop.credentials().close());
    }

    private void finishOnFxThread(
            TerminalConnection connection,
            AttemptState attempt,
            TerminalPreferences preferences) {
        if (attempt.result.isDone()) {
            connection.close();
            return;
        }
        FastRemoteTerminal terminal = new FastRemoteTerminal(preferences,
                com.ynpe.fastremote.ui.TerminalContextMenuPreparation::prepare);
        try {
            terminal.connect(connection);
            attempt.result.complete(new TerminalHandle(
                    terminal.node(),
                    terminal.focusTarget(),
                    terminal::close,
                    new AsyncRemoteFiles(connection.remoteFiles()),
                    terminal.termination(),
                    terminal::applyPreferences,
                    () -> loadServerOverview(connection),
                    terminal::sendInput,
                    terminal::receivedBytes,
                    terminal::sentBytes,
                    terminal::setWorkingDirectoryListener,
                    terminal::applyKeyboardShortcuts,
                    terminal::setInputEnabled,
                    () -> switch (terminal.detectedShellFamily()) {
                        case POWERSHELL -> com.ynpe.fastremote.ui.LocalTerminalHandle.ShellFamily.POWERSHELL;
                        case COMMAND_PROMPT -> com.ynpe.fastremote.ui.LocalTerminalHandle.ShellFamily.COMMAND_PROMPT;
                        case POSIX -> com.ynpe.fastremote.ui.LocalTerminalHandle.ShellFamily.POSIX;
                        default -> com.ynpe.fastremote.ui.LocalTerminalHandle.ShellFamily.UNKNOWN;
                    }, items -> terminal.setContextMenuItems(
                            TerminalContextMenuBridge.adapt(items))).withObservation(terminal.observation()));
        } catch (RuntimeException failure) {
            connection.close();
            terminal.close();
            attempt.result.completeExceptionally(failure);
        }
    }

    private CompletionStage<ServerOverview> loadServerOverview(
            TerminalConnection connection) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Application is shutting down"));
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    var probeSettings = terminalPreferences.get().remoteProbeSettings();
                    ServerOverview overview;
                    if (probeSettings.probeServerInformation()) {
                        RemoteSystemProbe.ProbeResult result = RemoteSystemProbe.probe(connection, probeSettings);
                        overview = result.overview();
                    } else {
                        overview = new ServerOverview(
                                com.ynpe.fastremote.i18n.Texts.text("legacy.java.bdb3f3a6a13cdfd6"), ServerOverview.SystemFamily.UNKNOWN);
                    }
                    return overview;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new java.util.concurrent.CompletionException(interrupted);
                } catch (java.io.IOException failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            }, executor);
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Connection task queue is full", rejected));
        }
    }

    private final class AsyncRemoteFiles implements RemoteFilesHandle {
        private final RemoteFileService delegate;

        private AsyncRemoteFiles(RemoteFileService delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletionStage<String> canonicalPath(String path) {
            return submitFileTask(() -> delegate.canonicalPath(path));
        }

        @Override
        public CompletionStage<List<RemoteFileEntry>> list(String directory) {
            return submitFileTask(() -> delegate.list(directory));
        }

        @Override
        public CompletionStage<RemoteFileAttributes> readAttributes(String path) {
            return submitFileTask(() -> delegate.readAttributes(path));
        }

        @Override
        public CompletionStage<Void> setPermissions(String path, int permissions) {
            return submitFileTask(() -> { delegate.setPermissions(path, permissions); return null; });
        }

        @Override
        public CompletionStage<Void> upload(Path localFile, String remoteFile) {
            return submitFileTask(() -> {
                delegate.upload(localFile, remoteFile);
                return null;
            });
        }

        @Override
        public CompletionStage<Void> download(String remoteFile, Path localFile) {
            return submitFileTask(() -> {
                delegate.download(remoteFile, localFile);
                return null;
            });
        }

        @Override public CompletionStage<byte[]> readFile(String remoteFile, int maximumBytes) {
            return submitFileTask(() -> delegate.readFile(remoteFile, maximumBytes));
        }

        @Override public CompletionStage<Void> writeFileAtomic(String remoteFile, byte[] content, boolean overwrite) {
            byte[] copy = java.util.Objects.requireNonNull(content, "content").clone();
            return submitFileTask(() -> {
                try { delegate.writeFileAtomic(remoteFile, copy, overwrite); return null; }
                finally { java.util.Arrays.fill(copy, (byte) 0); }
            });
        }

        @Override
        public CompletionStage<Void> uploadTree(Path localSource, String remoteTarget,
                com.ynpe.fastremote.domain.transfer.TransferControl control,
                com.ynpe.fastremote.domain.transfer.TransferProgressListener progress,
                com.ynpe.fastremote.domain.transfer.TransferConflictResolver conflicts) {
            return submitFileTask(() -> {
                delegate.uploadTree(localSource, remoteTarget, control, progress, conflicts);
                return null;
            });
        }

        @Override
        public CompletionStage<Void> uploadTree(Path localSource, String remoteTarget,
                com.ynpe.fastremote.domain.transfer.TransferControl control,
                com.ynpe.fastremote.domain.transfer.TransferProgressListener progress,
                com.ynpe.fastremote.domain.transfer.TransferConflictResolver conflicts,
                com.ynpe.fastremote.domain.transfer.TransferActivityListener activity) {
            return submitFileTask(() -> {
                delegate.uploadTree(localSource, remoteTarget, control, progress, conflicts, activity);
                return null;
            });
        }

        @Override
        public CompletionStage<Void> downloadTree(String remoteSource, Path localTarget,
                com.ynpe.fastremote.domain.transfer.TransferControl control,
                com.ynpe.fastremote.domain.transfer.TransferProgressListener progress,
                com.ynpe.fastremote.domain.transfer.TransferConflictResolver conflicts) {
            return submitFileTask(() -> {
                delegate.downloadTree(remoteSource, localTarget, control, progress, conflicts);
                return null;
            });
        }

        @Override
        public CompletionStage<Void> downloadTree(String remoteSource, Path localTarget,
                com.ynpe.fastremote.domain.transfer.TransferControl control,
                com.ynpe.fastremote.domain.transfer.TransferProgressListener progress,
                com.ynpe.fastremote.domain.transfer.TransferConflictResolver conflicts,
                com.ynpe.fastremote.domain.transfer.TransferActivityListener activity) {
            return submitFileTask(() -> {
                delegate.downloadTree(remoteSource, localTarget, control, progress, conflicts, activity);
                return null;
            });
        }

        @Override
        public CompletionStage<Void> uploadTree(Path localSource, String remoteTarget,
                com.ynpe.fastremote.domain.transfer.TransferControl control,
                com.ynpe.fastremote.domain.transfer.TransferProgressListener progress,
                com.ynpe.fastremote.domain.transfer.TransferConflictResolver conflicts,
                com.ynpe.fastremote.domain.transfer.TransferActivityListener activity,
                com.ynpe.fastremote.domain.transfer.FileTransferScheduler scheduler) {
            return submitFileTask(() -> {
                delegate.uploadTree(localSource, remoteTarget, control, progress, conflicts, activity, scheduler);
                return null;
            });
        }

        @Override
        public CompletionStage<Void> downloadTree(String remoteSource, Path localTarget,
                com.ynpe.fastremote.domain.transfer.TransferControl control,
                com.ynpe.fastremote.domain.transfer.TransferProgressListener progress,
                com.ynpe.fastremote.domain.transfer.TransferConflictResolver conflicts,
                com.ynpe.fastremote.domain.transfer.TransferActivityListener activity,
                com.ynpe.fastremote.domain.transfer.FileTransferScheduler scheduler) {
            return submitFileTask(() -> {
                delegate.downloadTree(remoteSource, localTarget, control, progress, conflicts, activity, scheduler);
                return null;
            });
        }

        @Override
        public CompletionStage<Void> createDirectory(String path) {
            return submitFileTask(() -> { delegate.createDirectory(path); return null; });
        }

        @Override
        public CompletionStage<Void> createFile(String path) {
            return submitFileTask(() -> { delegate.createFile(path); return null; });
        }

        @Override
        public CompletionStage<Void> delete(String path) {
            return delete(path, com.ynpe.fastremote.domain.transfer.TransferActivityListener.NONE);
        }

        @Override
        public CompletionStage<Void> delete(String path,
                com.ynpe.fastremote.domain.transfer.TransferActivityListener activity) {
            return submitTask(() -> {
                delegate.delete(path, activity, sftpSettings.get().concurrentTransfers());
                return null;
            }, deleteExecutor);
        }

        @Override
        public CompletionStage<Void> rename(String source, String target) {
            return submitFileTask(() -> { delegate.rename(source, target); return null; });
        }

        private <T> CompletionStage<T> submitFileTask(FileTask<T> task) {
            return submitTask(task, fileExecutor);
        }

        private <T> CompletionStage<T> submitTask(FileTask<T> task, ThreadPoolExecutor worker) {
            if (closed.get()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Application is shutting down"));
            }
            CompletableFuture<T> result = new CompletableFuture<>();
            try {
                Future<?> submitted = worker.submit(() -> {
                    try {
                        result.complete(task.run());
                    } catch (java.io.IOException failure) {
                        result.completeExceptionally(failure);
                    } catch (RuntimeException failure) {
                        result.completeExceptionally(failure);
                    }
                });
                result.whenComplete((ignored, failure) -> {
                    if (result.isCancelled()) submitted.cancel(true);
                });
                return result;
            } catch (RejectedExecutionException rejected) {
                return CompletableFuture.failedFuture(new IllegalStateException("File operation queue is full", rejected));
            }
        }
    }

    @FunctionalInterface
    private interface FileTask<T> {
        T run() throws java.io.IOException;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        attempts.forEach(AttemptState::cancel);
        attempts.clear();
        testAttempts.forEach(TestAttemptState::cancel);
        testAttempts.clear();
        executor.shutdownNow();
        fileExecutor.shutdownNow();
        deleteExecutor.shutdownNow();
        Thread.ofPlatform()
                .name("fastremote-connector-close")
                .daemon(false)
                .start(connector::close);
    }

    private final class AttemptState {
        private final CompletableFuture<TerminalHandle> result = new CompletableFuture<>();
        private final AtomicReference<Future<?>> task = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private void installTask(Future<?> submitted) {
            task.set(submitted);
            if (cancelled.get()) submitted.cancel(true);
        }

        private void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            result.completeExceptionally(new java.util.concurrent.CancellationException("Connection was cancelled"));
            Future<?> running = task.get();
            if (running != null) running.cancel(true);
        }
    }

    private static final class SftpAttemptState {
        private final CompletableFuture<SftpHandle> result = new CompletableFuture<>();
        private final AtomicReference<Future<?>> task = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private void installTask(Future<?> value) { task.set(value); if (cancelled.get()) value.cancel(true); }
        private void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            result.completeExceptionally(new java.util.concurrent.CancellationException("Connection was cancelled"));
            Future<?> value = task.get();
            if (value != null) value.cancel(true);
        }
    }

    private static final class TestAttemptState {
        private final CompletableFuture<Void> result = new CompletableFuture<>();
        private final AtomicReference<Future<?>> task = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final Runnable cleanup;
        private TestAttemptState(Runnable cleanup) {
            this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
        }
        private void installTask(Future<?> value) { task.set(value); if (cancelled.get()) value.cancel(true); }
        private void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            result.completeExceptionally(new java.util.concurrent.CancellationException("Connection test was cancelled"));
            Future<?> value = task.get();
            if (value != null) value.cancel(true);
            cleanup.run();
        }
    }
}
