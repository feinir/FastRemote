// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.sqlite.SQLiteConfig;

/** Moves the one known non-migratable data generation aside before runtime services open it. */
public final class IncompatibleDataBackup {
    static final String INCOMPATIBLE_VERSION = "1.0.3";
    private static final String BACKUP_DIRECTORY = "bak." + INCOMPATIBLE_VERSION;
    private static final int MAX_CONFIG_BYTES = 4 * 1024 * 1024;
    private static final Pattern LEGACY_HOST_LIBRARY_KEY = Pattern.compile(
            "(?m)^\\s*(sessionLibraryDock|collapsedSessionGroups|sessionTabTitle)\\s*:");
    private static final Pattern CURRENT_HOST_LIBRARY_KEY = Pattern.compile(
            "(?m)^\\s*(hostLibraryDock|collapsedHostGroups|hostTabTitle)\\s*:");
    private static final List<String> DATA_FILES = List.of(
            "global-config.yaml", "fastremote.db", "fastremote.db-wal", "fastremote.db-shm",
            "known_hosts", "last-connection.properties");

    private IncompatibleDataBackup() { }

    /**
     * Backs up only the pre-host-library generation. Lower schema versions that retain the current
     * host tables and keys remain available to the normal migration pipeline.
     */
    public static Optional<Path> backupIfRequired(Path dataDirectory) throws IOException {
        Path root = normalizedDirectory(dataDirectory);
        Path yaml = root.resolve("global-config.yaml");
        Path database = root.resolve("fastremote.db");
        if (!legacyYaml(yaml) && !legacyDatabase(database)) return Optional.empty();

        List<Path> sources = DATA_FILES.stream()
                .map(root::resolve)
                .filter(path -> Files.exists(path, LinkOption.NOFOLLOW_LINKS))
                .toList();
        if (sources.isEmpty()) return Optional.empty();
        Path backup = root.resolve(BACKUP_DIRECTORY);
        Files.createDirectories(backup);
        restrictBackupDirectory(backup);
        for (Path source : sources) {
            Path target = backup.resolve(source.getFileName());
            if (Files.exists(target)) {
                throw new IOException("Incompatible data backup already contains " + target.getFileName());
            }
        }

        List<Path> moved = new ArrayList<>();
        try {
            for (Path source : sources) {
                move(source, backup.resolve(source.getFileName()));
                moved.add(source);
            }
        } catch (IOException failure) {
            rollback(backup, moved, failure);
            throw failure;
        }
        return Optional.of(backup);
    }

    public static List<Path> populatedBackupDirectories(Path dataDirectory) throws IOException {
        Path root = normalizedDirectory(dataDirectory);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return List.of();
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().startsWith("bak."))
                    .filter(IncompatibleDataBackup::containsRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static boolean legacyYaml(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false;
        long size = Files.size(path);
        if (size < 1 || size > MAX_CONFIG_BYTES) return false;
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return LEGACY_HOST_LIBRARY_KEY.matcher(content).find()
                && !CURRENT_HOST_LIBRARY_KEY.matcher(content).find();
    }

    private static boolean legacyDatabase(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false;
        try {
            Class.forName("org.sqlite.JDBC");
            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);
            try (Connection connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + path.toAbsolutePath(), config.toProperties())) {
                return tableExists(connection, "saved_sessions")
                        && !tableExists(connection, "saved_hosts");
            }
        } catch (ClassNotFoundException | SQLException failure) {
            throw new IOException("Unable to inspect the FastRemote database before startup", failure);
        }
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean containsRegularFile(Path directory) {
        try (Stream<Path> children = Files.list(directory)) {
            return children.anyMatch(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS));
        } catch (IOException ignored) {
            return false;
        }
    }

    private static Path normalizedDirectory(Path dataDirectory) throws IOException {
        Path root = java.util.Objects.requireNonNull(dataDirectory, "dataDirectory")
                .toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }

    private static void restrictBackupDirectory(Path backup) throws IOException {
        try {
            Files.setPosixFilePermissions(backup, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows inherits the access controls of the private application-data directory.
        }
    }

    private static void rollback(Path backup, List<Path> moved, IOException original) {
        for (int index = moved.size() - 1; index >= 0; index--) {
            Path source = moved.get(index);
            Path target = backup.resolve(source.getFileName());
            try {
                if (Files.exists(target) && Files.notExists(source)) move(target, source);
            } catch (IOException rollbackFailure) {
                original.addSuppressed(rollbackFailure);
            }
        }
    }
}
