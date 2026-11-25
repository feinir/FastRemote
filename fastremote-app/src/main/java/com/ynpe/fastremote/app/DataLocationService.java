// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.storage.FastRemotePaths;
import com.ynpe.fastremote.ui.DataLocationSettings;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.prefs.Preferences;

/** Resolves the independent bootstrap preference before opening any application configuration. */
public final class DataLocationService {
    private static final String MANAGED = "fastremote.dataDirectory.managed";
    private DataLocationService() { }

    private static boolean mac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    static boolean managed() {
        return Boolean.getBoolean(MANAGED) || System.getProperty(FastRemotePaths.DATA_DIRECTORY_PROPERTY, "").isBlank();
    }

    private static DataLocationPreference preference() {
        return new DataLocationPreference(Preferences.userRoot().node("com/ynpe/fastremote/bootstrap"),
                FastRemotePaths.userDataDirectory());
    }

    static Path programDirectory(String os, String launcher) {
        if (os.toLowerCase(Locale.ROOT).contains("mac") || launcher.isBlank()) return null;
        Path executable = Path.of(launcher).toAbsolutePath().normalize();
        Path installation = executable.getParent();
        if (!os.toLowerCase(Locale.ROOT).contains("win") && installation.getFileName().toString().equals("bin")) {
            installation = installation.getParent();
        }
        return installation.resolve("config");
    }

    private static Path programDirectory() {
        return programDirectory(System.getProperty("os.name", ""), System.getProperty("jpackage.app-path", ""));
    }

    public static DataLocationSettings settings() throws IOException {
        Path current = FastRemotePaths.applicationDataDirectory();
        Path program = programDirectory();
        boolean allowed = managed();
        DataLocationPreference.State state = allowed ? preference().read()
                : new DataLocationPreference.State(current, null);
        Path selected = state.pending() == null ? state.current() : state.pending();
        return new DataLocationSettings(current, FastRemotePaths.userDataDirectory(), java.util.Optional.ofNullable(program),
                allowed, selected, state.pending() != null);
    }

    public static DataLocationSettings schedule(Path directory) throws IOException {
        DataLocationSettings settings = settings();
        if (!settings.changeAllowed()) throw new IOException("Data location is fixed for this launch");
        Path target = directory.toAbsolutePath().normalize();
        try (StartupLock ignored = startupLock()) {
            if (!target.equals(settings.current())) {
                validateDestination(target, mac());
                if (target.startsWith(settings.current()) || settings.current().startsWith(target)) {
                    throw new IOException("Data directories must not overlap");
                }
                DataDirectoryMigration.rejectLinkedAncestors(target);
                Files.createDirectories(target.getParent());
                Path probe = Files.createTempFile(target.getParent(), ".fastremote-write-test-", ".tmp");
                Files.delete(probe);
            }
            ProcessHandle process = ProcessHandle.current();
            preference().write(target.equals(settings.current())
                    ? new DataLocationPreference.State(settings.current(), null)
                    : new DataLocationPreference.State(settings.current(), target, process.pid(),
                            process.info().startInstant().orElseThrow(() -> new IOException("Cannot identify the source process"))
                                    .toEpochMilli()));
        }
        return settings();
    }

    static Path selectedDirectory() throws IOException {
        return !managed() ? FastRemotePaths.applicationDataDirectory() : preference().read().current();
    }

    static SingleInstanceBroker start(LaunchRequest request) throws IOException {
        try (StartupLock ignored = startupLock()) {
            if (!managed()) return SingleInstanceBroker.startOrForward(FastRemotePaths.applicationDataDirectory(), request);
            return startManaged(request, preference());
        }
    }

    static SingleInstanceBroker startManaged(LaunchRequest request, DataLocationPreference store) throws IOException {
        DataLocationPreference.State state = store.read();
        Path current = state.current();
        if ((!current.equals(FastRemotePaths.userDataDirectory().toAbsolutePath().normalize())
                || state.pending() != null) && !Files.isDirectory(current)) {
            throw new IOException("The configured data directory is missing: " + current);
        }
        if (state.pending() != null) {
            Files.createDirectories(current);
            try (FileChannel channel = FileChannel.open(current.resolve("instance.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                FileLock lock = tryLock(channel);
                if (lock == null) return SingleInstanceBroker.startOrForward(current, request);
                try (lock) {
                    if (sourceProcessAlive(state)) {
                        throw new IOException("FastRemote is still shutting down; wait for it to exit before migrating data");
                    }
                    validateDestination(state.pending(), mac());
                    assertInactive(state.pending());
                    DataDirectoryMigration.copyAndInstall(current, state.pending());
                    store.write(new DataLocationPreference.State(state.pending(), null));
                    current = state.pending();
                }
            }
        }
        System.setProperty(FastRemotePaths.DATA_DIRECTORY_PROPERTY, current.toString());
        System.setProperty(MANAGED, "true");
        return SingleInstanceBroker.startOrForward(current, request);
    }

    static boolean sourceProcessAlive(DataLocationPreference.State state) {
        if (state.sourcePid() <= 0) return false;
        return ProcessHandle.of(state.sourcePid()).filter(ProcessHandle::isAlive)
                .filter(process -> process.info().startInstant()
                        .map(start -> start.toEpochMilli() == state.sourceStartedMillis()).orElse(true)).isPresent();
    }

    static void validateDestination(Path directory, boolean mac) throws IOException {
        if (mac) {
            for (Path part : directory) {
                if (part.toString().toLowerCase(Locale.ROOT).endsWith(".app")) {
                    throw new IOException("Application bundles cannot store configuration data");
                }
            }
        }
        DataDirectoryMigration.rejectLinkedAncestors(directory);
        if (Files.exists(directory)) {
            if (!Files.isDirectory(directory)) throw new IOException("Destination is not a directory");
            try (var files = Files.list(directory)) {
                boolean nonEmpty = files.anyMatch(path -> !path.getFileName().toString().equals("instance.lock"));
                if (nonEmpty && !(Files.isRegularFile(directory.resolve("global-config.yaml"))
                        && Files.isRegularFile(directory.resolve("fastremote.db")))) {
                    throw new IOException("Select an empty directory or an existing FastRemote data directory");
                }
            }
        }
    }

    private static void assertInactive(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        DataDirectoryMigration.rejectLinkedAncestors(directory);
        try (FileChannel channel = FileChannel.open(directory.resolve("instance.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock lock = tryLock(channel)) {
            if (lock == null) throw new IOException("Close the FastRemote instance using the destination directory first");
        }
    }

    private static FileLock tryLock(FileChannel channel) throws IOException {
        try { return channel.tryLock(); }
        catch (OverlappingFileLockException busy) { return null; }
    }

    private static StartupLock startupLock() throws IOException {
        Path folder = FastRemotePaths.userDataDirectory().resolveSibling("FastRemote-bootstrap");
        Files.createDirectories(folder);
        FileChannel channel = FileChannel.open(folder.resolve("startup.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = tryLock(channel);
            if (lock == null) throw new IOException("Another FastRemote startup or data migration is in progress; try again");
            return new StartupLock(channel, lock);
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
    }

    private record StartupLock(FileChannel channel, FileLock lock) implements AutoCloseable {
        @Override public void close() throws IOException {
            try { lock.close(); } finally { channel.close(); }
        }
    }
}
