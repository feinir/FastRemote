// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.command.CommandPaletteUsage;
import com.ynpe.fastremote.domain.command.CommandPaletteUsageRepository;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SqliteCommandPaletteUsageRepository implements CommandPaletteUsageRepository {
    private static final int MAX_ENTRIES = 512;
    private final FastRemoteDatabase database;

    public SqliteCommandPaletteUsageRepository(FastRemoteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public List<CommandPaletteUsage> list() throws IOException {
        try {
            return database.query(connection -> {
                List<CommandPaletteUsage> usages = new ArrayList<>();
                try (var statement = connection.prepareStatement("""
                        SELECT entry_key, use_count, last_used_at
                        FROM command_palette_usage
                        ORDER BY use_count DESC, last_used_at DESC
                        LIMIT ?
                        """)) {
                    statement.setInt(1, MAX_ENTRIES);
                    try (var result = statement.executeQuery()) {
                        while (result.next()) {
                            usages.add(new CommandPaletteUsage(result.getString("entry_key"),
                                    result.getLong("use_count"),
                                    Instant.ofEpochMilli(result.getLong("last_used_at"))));
                        }
                    }
                }
                return List.copyOf(usages);
            });
        } catch (SQLException failure) {
            throw new IOException("Unable to load command palette usage", failure);
        }
    }

    @Override
    public CommandPaletteUsage recordUse(String entryKey, Instant usedAt) throws IOException {
        CommandPaletteUsage requested = new CommandPaletteUsage(entryKey, 1, usedAt);
        try {
            return database.transaction(connection -> {
                try (var statement = connection.prepareStatement("""
                        INSERT INTO command_palette_usage(entry_key, use_count, last_used_at)
                        VALUES (?, 1, ?)
                        ON CONFLICT(entry_key) DO UPDATE SET
                            use_count = min(1000000000, use_count + 1),
                            last_used_at = excluded.last_used_at
                        """)) {
                    statement.setString(1, requested.entryKey());
                    statement.setLong(2, requested.lastUsedAt().toEpochMilli());
                    statement.executeUpdate();
                }
                CommandPaletteUsage stored;
                try (var statement = connection.prepareStatement("""
                        SELECT use_count, last_used_at FROM command_palette_usage WHERE entry_key = ?
                        """)) {
                    statement.setString(1, requested.entryKey());
                    try (var result = statement.executeQuery()) {
                        if (!result.next()) throw new SQLException("Recorded command palette usage is unavailable");
                        stored = new CommandPaletteUsage(requested.entryKey(), result.getLong("use_count"),
                                Instant.ofEpochMilli(result.getLong("last_used_at")));
                    }
                }
                try (var prune = connection.prepareStatement("""
                        DELETE FROM command_palette_usage
                        WHERE entry_key IN (
                            SELECT entry_key FROM command_palette_usage
                            ORDER BY use_count ASC, last_used_at ASC
                            LIMIT max(0, (SELECT count(*) FROM command_palette_usage) - ?)
                        )
                        """)) {
                    prune.setInt(1, MAX_ENTRIES);
                    prune.executeUpdate();
                }
                return stored;
            });
        } catch (SQLException failure) {
            throw new IOException("Unable to record command palette usage", failure);
        }
    }
}
