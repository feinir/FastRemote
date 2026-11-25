// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.security.HostKeyIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IncompatibleDataBackupTest {
    @TempDir Path temporaryDirectory;

    @Test
    void movesTheKnownIncompatibleYamlAndDatabaseGenerationWithoutRenamingFiles() throws Exception {
        Path yaml = temporaryDirectory.resolve("global-config.yaml");
        Files.writeString(yaml, """
                schemaVersion: 2
                settings:
                  workspace:
                    sessionLibraryDock: LEFT
                """);
        Path database = temporaryDirectory.resolve("fastremote.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE saved_sessions (id TEXT PRIMARY KEY)");
        }
        Files.writeString(temporaryDirectory.resolve("fastremote.db-wal"), "wal");
        Files.writeString(temporaryDirectory.resolve("fastremote.db-shm"), "shm");
        Files.writeString(temporaryDirectory.resolve("known_hosts"), "trusted host key");
        Files.writeString(temporaryDirectory.resolve("last-connection.properties"), "schemaVersion=1");

        Path backup = IncompatibleDataBackup.backupIfRequired(temporaryDirectory).orElseThrow();

        assertEquals("bak.1.0.3", backup.getFileName().toString());
        assertEquals(List.of("fastremote.db", "fastremote.db-shm", "fastremote.db-wal", "global-config.yaml",
                        "known_hosts", "last-connection.properties").stream().sorted().toList(),
                Files.list(backup).map(path -> path.getFileName().toString()).sorted().toList());
        assertFalse(Files.exists(yaml));
        assertFalse(Files.exists(database));
        assertFalse(Files.exists(temporaryDirectory.resolve("known_hosts")));
        assertFalse(Files.exists(temporaryDirectory.resolve("last-connection.properties")));
        assertEquals(List.of(backup), IncompatibleDataBackup.populatedBackupDirectories(temporaryDirectory));

        FileKnownHostRepository newKnownHosts = new FileKnownHostRepository(
                temporaryDirectory.resolve("known_hosts"));
        newKnownHosts.save(new HostKeyIdentity(new Host("new.example"), new Port(22),
                "ssh-ed25519", "SHA256:new", "ssh-ed25519 AAAA"));
        assertTrue(newKnownHosts.find(new Host("new.example"), new Port(22)).isPresent());
        assertTrue(Files.readString(backup.resolve("known_hosts")).contains("trusted host key"));
    }

    @Test
    void leavesCurrentOrMigratableDataInPlace() throws Exception {
        Path yaml = temporaryDirectory.resolve("global-config.yaml");
        Files.writeString(yaml, """
                schemaVersion: 1
                settings:
                  workspace:
                    hostLibraryDock: LEFT
                """);
        Path database = temporaryDirectory.resolve("fastremote.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE saved_hosts (id TEXT PRIMARY KEY)");
            statement.execute("PRAGMA user_version = 5");
        }

        assertTrue(IncompatibleDataBackup.backupIfRequired(temporaryDirectory).isEmpty());
        assertTrue(Files.exists(yaml));
        assertTrue(Files.exists(database));
        assertTrue(IncompatibleDataBackup.populatedBackupDirectories(temporaryDirectory).isEmpty());
    }

    @Test
    void detectsTheKnownIncompatibleDatabaseWithoutRelyingOnYaml() throws Exception {
        Path database = temporaryDirectory.resolve("fastremote.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE saved_sessions (id TEXT PRIMARY KEY)");
            statement.execute("PRAGMA user_version = 5");
        }

        Path backup = IncompatibleDataBackup.backupIfRequired(temporaryDirectory).orElseThrow();

        assertTrue(Files.exists(backup.resolve("fastremote.db")));
        assertFalse(Files.exists(database));
    }

    @Test
    void ignoresEmptyBackupDirectoriesSoTheNoticeStopsAfterFilesAreRemoved() throws Exception {
        Files.createDirectory(temporaryDirectory.resolve("bak.1.0.3"));
        Files.createDirectory(temporaryDirectory.resolve("unrelated"));
        Files.writeString(temporaryDirectory.resolve("unrelated/file.txt"), "keep");

        assertTrue(IncompatibleDataBackup.populatedBackupDirectories(temporaryDirectory).isEmpty());
    }
}
