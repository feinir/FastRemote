// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.settings.DiagnosticLogSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DailyFileDiagnosticLogOutputTest {
    @TempDir Path temporaryDirectory;

    @Test
    void defaultsToLogDirectoryBesideConfigAndWritesOneFilePerDate() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        var output = new DailyFileDiagnosticLogOutput(
                temporaryDirectory.resolve("global-config.yaml"), clock);
        try {
            output.append(LocalDateTime.parse("2026-08-31T12:01:02"), "[AI][SUCCEEDED] inspected files");
            output.append(LocalDateTime.parse("2026-09-01T00:00:01"), "[MCP][FAILED] command failed");
            output.ensureCurrentFile().toCompletableFuture().join();

            assertEquals(temporaryDirectory.resolve("log").toAbsolutePath().normalize(), output.directory());
            assertEquals(output.directory(), output.defaultDirectory());
            Path first = output.directory().resolve("fastremote-2026-08-31.log");
            Path second = output.directory().resolve("fastremote-2026-09-01.log");
            assertTrue(Files.readString(first).contains("[AI][SUCCEEDED] inspected files"));
            assertTrue(Files.readString(second).contains("[MCP][FAILED] command failed"));
        } finally {
            output.close();
        }
    }

    @Test
    void configuredDirectoryIsUsedAndDisabledLoggingDoesNotAppend() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        var output = new DailyFileDiagnosticLogOutput(
                temporaryDirectory.resolve("global-config.yaml"), clock);
        Path custom = temporaryDirectory.resolve("audit logs");
        try {
            output.configure(new DiagnosticLogSettings(true, custom.toString()));
            output.append(LocalDateTime.parse("2026-08-31T12:05:00"), "persisted");
            output.ensureCurrentFile().toCompletableFuture().join();
            Path file = custom.resolve("fastremote-2026-08-31.log");
            assertTrue(Files.readString(file).contains("persisted"));

            output.configure(new DiagnosticLogSettings(false, custom.toString()));
            output.append(LocalDateTime.parse("2026-08-31T12:06:00"), "must-not-be-written");
            output.ensureCurrentFile().toCompletableFuture().join();
            assertFalse(Files.readString(file).contains("must-not-be-written"));
        } finally {
            output.close();
        }
    }

    @Test
    void listsAndDeletesOnlyFastRemoteOwnedLogFiles() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-09-01T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        var output = new DailyFileDiagnosticLogOutput(
                temporaryDirectory.resolve("global-config.yaml"), clock);
        try {
            output.append(LocalDateTime.parse("2026-08-31T12:00:00"), "first");
            output.append(LocalDateTime.parse("2026-09-01T12:00:00"), "second");
            Path unrelated = output.directory().resolve("keep.txt");
            Files.createDirectories(unrelated.getParent());
            Files.writeString(unrelated, "not a FastRemote log");

            var listed = output.listFiles().toCompletableFuture().join();
            assertEquals(java.util.List.of(
                    "fastremote-2026-09-01.log", "fastremote-2026-08-31.log"),
                    listed.files().stream().map(value -> value.fileName()).toList());
            assertEquals(listed.files().stream().mapToLong(value -> value.size()).sum(), listed.totalSize());

            var remaining = output.deleteAllFiles().toCompletableFuture().join();
            assertTrue(remaining.files().isEmpty());
            assertEquals(0L, remaining.totalSize());
            assertTrue(Files.exists(unrelated));
        } finally {
            output.close();
        }
    }
}
