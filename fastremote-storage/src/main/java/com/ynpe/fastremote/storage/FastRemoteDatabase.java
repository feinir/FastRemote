// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.nio.file.attribute.PosixFilePermissions;

public final class FastRemoteDatabase implements AutoCloseable {
    private static final int SCHEMA_VERSION = 10;

    private final Path path;
    private final Connection connection;

    public FastRemoteDatabase(Path path) throws IOException {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        Connection openedConnection = null;
        try {
            Path parent = this.path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
                restrictPosix(parent, "rwx------");
            }
            Class.forName("org.sqlite.JDBC");
            openedConnection = DriverManager.getConnection("jdbc:sqlite:" + this.path);
            connection = openedConnection;
            configure();
            initializeSchema();
            restrictDatabaseFiles();
        } catch (ClassNotFoundException | SQLException failure) {
            closeAfterOpenFailure(openedConnection, failure);
            throw new IOException("Unable to open the FastRemote database at " + this.path, failure);
        }
    }

    public static FastRemoteDatabase openDefault() throws IOException {
        return new FastRemoteDatabase(FastRemotePaths.applicationDataDirectory().resolve("fastremote.db"));
    }

    public Path path() {
        return path;
    }

    synchronized <T> T query(SqlFunction<T> operation) throws SQLException {
        return operation.apply(connection);
    }

    synchronized <T> T transaction(SqlFunction<T> operation) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        if (!previousAutoCommit) return operation.apply(connection);
        connection.setAutoCommit(false);
        try {
            T result = operation.apply(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    /** One transaction across repository calls; nested repository operations join it. */
    public synchronized <T> T atomic(java.util.concurrent.Callable<T> operation) throws Exception {
        boolean previousAutoCommit = connection.getAutoCommit();
        if (!previousAutoCommit) return operation.call();
        connection.setAutoCommit(false);
        try {
            T result = operation.call();
            connection.commit();
            return result;
        } catch (Exception failure) {
            try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void configure() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = FULL");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA secure_delete = ON");
            statement.execute("PRAGMA wal_autocheckpoint = 1000");
        }
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA foreign_keys")) {
            if (!result.next() || result.getInt(1) != 1) {
                throw new SQLException("SQLite foreign keys could not be enabled");
            }
        }
    }

    private void initializeSchema() throws SQLException {
        transaction(connection -> {
            if (!tableExists(connection, "saved_hosts")) {
                createInitialSchema(connection);
            }
            if (!tableExists(connection, "ai_chat_sessions") || !tableExists(connection, "ai_chat_messages")) {
                createAiChatSchema(connection);
            }
            migrateAiChatModelSelection(connection);
            migrateAiChatAssociation(connection);
            if (!columnExists(connection, "ai_chat_sessions", "pinned")) {
                execute(connection, "ALTER TABLE ai_chat_sessions ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0 CHECK(pinned IN (0,1))");
            }
            execute(connection, """
                    CREATE TABLE IF NOT EXISTS ai_chat_runtime (
                        session_id TEXT PRIMARY KEY REFERENCES ai_chat_sessions(id) ON DELETE CASCADE,
                        pending BLOB,
                        codex_context TEXT,
                        codex_thread TEXT
                    ) WITHOUT ROWID
                    """);
            createAiChatImageSchema(connection);
            migrateAiChatThumbnails(connection);
            createBuiltinSnippetSchema(connection);
            createCommandPaletteUsageSchema(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
            }
            return null;
        });
    }

    private static void createInitialSchema(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE host_groups (
                    id TEXT PRIMARY KEY,
                    parent_id TEXT REFERENCES host_groups(id) ON DELETE RESTRICT,
                    name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 255),
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    icon_color TEXT NOT NULL DEFAULT 'BLUE',
                    CHECK (parent_id IS NULL OR parent_id <> id)
                ) WITHOUT ROWID
                """);
        execute(connection, "CREATE UNIQUE INDEX uq_groups_root_name ON host_groups(name COLLATE NOCASE) WHERE parent_id IS NULL");
        execute(connection, "CREATE UNIQUE INDEX uq_groups_child_name ON host_groups(parent_id, name COLLATE NOCASE) WHERE parent_id IS NOT NULL");
        execute(connection, "CREATE INDEX ix_groups_parent_sort ON host_groups(parent_id, sort_order, name COLLATE NOCASE)");
        execute(connection, """
                CREATE TABLE saved_hosts (
                    id TEXT PRIMARY KEY,
                    group_id TEXT REFERENCES host_groups(id) ON DELETE RESTRICT,
                    name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 255),
                    host TEXT NOT NULL,
                    port INTEGER NOT NULL CHECK (port BETWEEN 1 AND 65535),
                    username TEXT NOT NULL,
                    authentication TEXT NOT NULL CHECK (authentication IN ('PASSWORD', 'PRIVATE_KEY')),
                    private_key_path TEXT,
                    legacy_algorithms INTEGER NOT NULL DEFAULT 0 CHECK (legacy_algorithms IN (0, 1)),
                    jump_host_id TEXT REFERENCES saved_hosts(id) ON DELETE RESTRICT,
                    credential_secret_id TEXT,
                    favorite INTEGER NOT NULL DEFAULT 0 CHECK (favorite IN (0, 1)),
                    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    last_connected_at INTEGER,
                    icon TEXT NOT NULL DEFAULT 'SERVER'
                ) WITHOUT ROWID
                """);
        execute(connection, "CREATE UNIQUE INDEX uq_hosts_root_name ON saved_hosts(name COLLATE NOCASE) WHERE group_id IS NULL");
        execute(connection, "CREATE UNIQUE INDEX uq_hosts_group_name ON saved_hosts(group_id, name COLLATE NOCASE) WHERE group_id IS NOT NULL");
        execute(connection, "CREATE INDEX ix_hosts_group_name ON saved_hosts(group_id, name COLLATE NOCASE)");
        execute(connection, "CREATE INDEX ix_hosts_host ON saved_hosts(host COLLATE NOCASE, port)");
        execute(connection, "CREATE INDEX ix_hosts_jump ON saved_hosts(jump_host_id)");
        execute(connection, "CREATE INDEX ix_hosts_recent ON saved_hosts(last_connected_at DESC)");
        execute(connection, "CREATE INDEX ix_hosts_group_sort ON saved_hosts(group_id, sort_order, name COLLATE NOCASE)");
        createSnippetSchema(connection);
        createConnectionHistorySchema(connection);
        createAiChatSchema(connection);
    }

    private static void createAiChatSchema(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE IF NOT EXISTS ai_chat_sessions (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 120),
                    provider_id TEXT,
                    model TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL CHECK (updated_at >= created_at),
                    CHECK ((provider_id IS NULL AND model IS NULL)
                        OR (length(trim(provider_id)) BETWEEN 1 AND 64
                            AND length(trim(model)) BETWEEN 1 AND 200))
                ) WITHOUT ROWID
                """);
        execute(connection, "CREATE INDEX IF NOT EXISTS ix_ai_chat_sessions_recent "
                + "ON ai_chat_sessions(updated_at DESC, created_at DESC)");
        execute(connection, """
                CREATE TABLE IF NOT EXISTS ai_chat_messages (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL REFERENCES ai_chat_sessions(id) ON DELETE CASCADE,
                    role TEXT NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
                    content TEXT NOT NULL CHECK (length(content) BETWEEN 1 AND 524288),
                    sequence_number INTEGER NOT NULL CHECK (sequence_number >= 0),
                    created_at INTEGER NOT NULL,
                    UNIQUE(session_id, sequence_number)
                ) WITHOUT ROWID
                """);
        execute(connection, "CREATE INDEX IF NOT EXISTS ix_ai_chat_messages_session_sequence "
                + "ON ai_chat_messages(session_id, sequence_number)");
        createAiChatImageSchema(connection);
    }

    private static void createAiChatImageSchema(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE IF NOT EXISTS ai_chat_images (
                    id TEXT PRIMARY KEY,
                    message_id TEXT NOT NULL REFERENCES ai_chat_messages(id) ON DELETE CASCADE,
                    media_type TEXT NOT NULL CHECK (media_type IN ('image/png', 'image/jpeg', 'image/webp')),
                    image_data BLOB NOT NULL CHECK (length(image_data) BETWEEN 1 AND 5242880),
                    thumbnail_data BLOB NOT NULL CHECK (length(thumbnail_data) BETWEEN 1 AND 524288),
                    pixel_width INTEGER NOT NULL CHECK (pixel_width BETWEEN 1 AND 20000000),
                    pixel_height INTEGER NOT NULL CHECK (pixel_height BETWEEN 1 AND 20000000),
                    sort_order INTEGER NOT NULL CHECK (sort_order BETWEEN 0 AND 3),
                    UNIQUE(message_id, sort_order)
                ) WITHOUT ROWID
                """);
        execute(connection, "CREATE INDEX IF NOT EXISTS ix_ai_chat_images_message_order "
                + "ON ai_chat_images(message_id, sort_order)");
    }

    private static void migrateAiChatThumbnails(Connection connection) throws SQLException {
        if (!columnExists(connection, "ai_chat_images", "thumbnail_data")) {
            execute(connection, "ALTER TABLE ai_chat_images ADD COLUMN thumbnail_data BLOB");
            execute(connection, "UPDATE ai_chat_images SET thumbnail_data=image_data WHERE thumbnail_data IS NULL");
        }
    }

    private static void migrateAiChatAssociation(Connection connection) throws SQLException {
        for (String column : java.util.List.of("associated_host_id", "associated_host_name",
                "associated_username", "associated_address", "associated_port")) {
            if (!columnExists(connection, "ai_chat_sessions", column)) {
                execute(connection, "ALTER TABLE ai_chat_sessions ADD COLUMN " + column + " TEXT");
            }
        }
    }

    private static void migrateAiChatModelSelection(Connection connection) throws SQLException {
        if (!columnExists(connection, "ai_chat_sessions", "provider_id")) {
            execute(connection, "ALTER TABLE ai_chat_sessions ADD COLUMN provider_id TEXT");
        }
        if (!columnExists(connection, "ai_chat_sessions", "model")) {
            execute(connection, "ALTER TABLE ai_chat_sessions ADD COLUMN model TEXT");
        }
    }

    private static void createConnectionHistorySchema(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE quick_connection_history (
                    id TEXT PRIMARY KEY,
                    host TEXT NOT NULL CHECK (length(trim(host)) BETWEEN 1 AND 253),
                    port INTEGER NOT NULL CHECK (port BETWEEN 1 AND 65535),
                    username TEXT NOT NULL,
                    authentication TEXT NOT NULL CHECK (authentication IN ('PASSWORD', 'PRIVATE_KEY')),
                    connected_at INTEGER NOT NULL,
                    UNIQUE(host COLLATE NOCASE, port, username, authentication)
                ) WITHOUT ROWID
                """);
        execute(connection, "CREATE INDEX ix_quick_connection_recent ON quick_connection_history(connected_at DESC)");
    }

    private static void createSnippetSchema(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE snippet_groups (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL COLLATE NOCASE UNIQUE
                        CHECK (length(trim(name)) BETWEEN 1 AND 255),
                    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0)
                    ,icon_color TEXT NOT NULL DEFAULT 'BLUE'
                ) WITHOUT ROWID
                """);
        execute(connection, "CREATE INDEX ix_snippet_groups_sort ON snippet_groups(sort_order, name COLLATE NOCASE)");
        execute(connection, """
                CREATE TABLE snippets (
                    id TEXT PRIMARY KEY,
                    group_id TEXT REFERENCES snippet_groups(id) ON DELETE RESTRICT,
                    name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 255),
                    description TEXT NOT NULL DEFAULT '' CHECK (length(description) <= 1024),
                    content TEXT NOT NULL CHECK (length(content) BETWEEN 1 AND 262144),
                    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                ) WITHOUT ROWID
                """);
        execute(connection, "CREATE UNIQUE INDEX uq_snippets_root_name ON snippets(name COLLATE NOCASE) WHERE group_id IS NULL");
        execute(connection, "CREATE UNIQUE INDEX uq_snippets_group_name ON snippets(group_id, name COLLATE NOCASE) WHERE group_id IS NOT NULL");
        execute(connection, "CREATE INDEX ix_snippets_group_sort ON snippets(group_id, sort_order, name COLLATE NOCASE)");
        execute(connection, """
                CREATE TABLE snippet_variable_groups (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL COLLATE NOCASE UNIQUE
                        CHECK (length(trim(name)) BETWEEN 1 AND 255),
                    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
                    icon_color TEXT NOT NULL DEFAULT 'BLUE'
                ) WITHOUT ROWID
                """);
        execute(connection, "CREATE INDEX ix_snippet_variable_groups_sort "
                + "ON snippet_variable_groups(sort_order, name COLLATE NOCASE)");
        execute(connection, """
                CREATE TABLE snippet_variables (
                    id TEXT PRIMARY KEY,
                    group_id TEXT REFERENCES snippet_variable_groups(id) ON DELETE RESTRICT,
                    name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 255),
                    variable_name TEXT NOT NULL COLLATE BINARY UNIQUE
                        CHECK (length(variable_name) BETWEEN 1 AND 64),
                    value_type TEXT NOT NULL CHECK (value_type IN ('SINGLE', 'ARRAY')),
                    delimiter TEXT NOT NULL CHECK (length(delimiter) <= 64),
                    value TEXT NOT NULL CHECK (length(value) <= 65536),
                    description TEXT NOT NULL DEFAULT '' CHECK (length(description) <= 1024),
                    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    CHECK ((value_type = 'SINGLE' AND delimiter = '')
                        OR (value_type = 'ARRAY' AND length(delimiter) > 0))
                ) WITHOUT ROWID
                """);
        execute(connection, "CREATE INDEX ix_snippet_variables_group_sort "
                + "ON snippet_variables(group_id, sort_order, name COLLATE NOCASE)");
    }

    private static void createBuiltinSnippetSchema(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE IF NOT EXISTS builtin_snippet_groups (
                    builtin_id TEXT PRIMARY KEY,
                    group_id TEXT NOT NULL UNIQUE REFERENCES snippet_groups(id) ON DELETE CASCADE,
                    imported_language TEXT NOT NULL
                ) WITHOUT ROWID
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS builtin_snippets (
                    builtin_id TEXT PRIMARY KEY,
                    snippet_id TEXT NOT NULL UNIQUE REFERENCES snippets(id) ON DELETE CASCADE,
                    imported_language TEXT NOT NULL
                ) WITHOUT ROWID
                """);
    }

    private static void createCommandPaletteUsageSchema(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE IF NOT EXISTS command_palette_usage (
                    entry_key TEXT PRIMARY KEY CHECK (length(entry_key) BETWEEN 1 AND 256),
                    use_count INTEGER NOT NULL CHECK (use_count BETWEEN 1 AND 1000000000),
                    last_used_at INTEGER NOT NULL
                ) WITHOUT ROWID
                """);
        execute(connection, "CREATE INDEX IF NOT EXISTS ix_command_palette_usage_recent "
                + "ON command_palette_usage(use_count DESC, last_used_at DESC)");
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, table);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                if (column.equals(result.getString("name"))) return true;
            }
            return false;
        }
    }

    private void restrictDatabaseFiles() throws IOException {
        restrictPosix(path, "rw-------");
        Path wal = Path.of(path + "-wal");
        Path sharedMemory = Path.of(path + "-shm");
        if (Files.exists(wal)) restrictPosix(wal, "rw-------");
        if (Files.exists(sharedMemory)) restrictPosix(sharedMemory, "rw-------");
    }

    private static void restrictPosix(Path target, String permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString(permissions));
        } catch (UnsupportedOperationException ignored) {
            // Windows uses the account ACL of the application data directory.
        }
    }

    private static void closeAfterOpenFailure(Connection connection, Exception failure) {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (!connection.isClosed()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                }
                connection.close();
            }
        } catch (SQLException failure) {
            System.getLogger(FastRemoteDatabase.class.getName())
                    .log(System.Logger.Level.WARNING, "Unable to close FastRemote database", failure);
        }
    }

    @FunctionalInterface
    interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }
}
