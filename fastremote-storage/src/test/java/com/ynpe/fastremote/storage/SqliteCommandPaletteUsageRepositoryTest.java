// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteCommandPaletteUsageRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void recordsFrequencyAndRecencyAcrossRepositoryInstances() throws Exception {
        Path path = temporaryDirectory.resolve("usage.db");
        Instant first = Instant.parse("2026-09-13T10:00:00Z");
        Instant second = Instant.parse("2026-09-13T11:00:00Z");
        try (var database = new FastRemoteDatabase(path)) {
            var repository = new SqliteCommandPaletteUsageRepository(database);
            repository.recordUse("command:ping", first);
            assertEquals(2, repository.recordUse("command:ping", second).useCount());
            repository.recordUse("command:disk", first);
        }

        try (var database = new FastRemoteDatabase(path)) {
            var usage = new SqliteCommandPaletteUsageRepository(database).list();
            assertEquals(2, usage.size());
            assertEquals("command:ping", usage.getFirst().entryKey());
            assertEquals(2, usage.getFirst().useCount());
            assertEquals(second, usage.getFirst().lastUsedAt());
        }
    }

    @Test
    void createsUsageTableWhenOpeningAVersionSixDatabase() throws Exception {
        Path path = temporaryDirectory.resolve("version-six.db");
        try (var database = new FastRemoteDatabase(path)) {
            database.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("DROP TABLE command_palette_usage");
                    statement.execute("PRAGMA user_version = 6");
                }
                return null;
            });
        }

        Instant usedAt = Instant.parse("2026-09-13T12:00:00Z");
        try (var database = new FastRemoteDatabase(path)) {
            var repository = new SqliteCommandPaletteUsageRepository(database);
            assertEquals(1, repository.recordUse("command:settings", usedAt).useCount());
            assertEquals("command:settings", repository.list().getFirst().entryKey());
            assertEquals(10, database.<Integer>query(connection -> {
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("PRAGMA user_version")) {
                    return result.next() ? result.getInt(1) : -1;
                }
            }));
        }
    }
}
