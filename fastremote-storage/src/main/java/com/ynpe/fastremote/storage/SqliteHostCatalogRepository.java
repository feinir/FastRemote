// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.catalog.RecentConnection;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostCatalogRepository;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.catalog.HostIcon;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.Username;
import com.ynpe.fastremote.domain.vault.SecretId;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SqliteHostCatalogRepository implements HostCatalogRepository {
    private static final String HOST_COLUMNS = """
            id, group_id, name, host, port, username, authentication,
            private_key_path, legacy_algorithms, jump_host_id, credential_secret_id, favorite, sort_order,
            created_at, updated_at, icon
            """;

    private final FastRemoteDatabase database;

    public SqliteHostCatalogRepository(FastRemoteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public List<HostGroup> listGroups() throws IOException {
        try {
            return database.query(connection -> {
                List<HostGroup> groups = new ArrayList<>();
                try (var statement = connection.prepareStatement(
                        "SELECT id, parent_id, name, sort_order, icon_color FROM host_groups ORDER BY sort_order, name COLLATE NOCASE");
                     ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        groups.add(new HostGroup(
                                GroupId.parse(result.getString("id")),
                                optional(result.getString("parent_id")).map(GroupId::parse),
                                result.getString("name"),
                                result.getInt("sort_order"),
                                GroupColor.valueOf(result.getString("icon_color"))));
                    }
                }
                return List.copyOf(groups);
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to load host groups", failure);
        }
    }

    @Override
    public List<SavedHost> searchHosts(String query, int limit) throws IOException {
        String normalized = Objects.requireNonNull(query, "query").strip().toLowerCase(java.util.Locale.ROOT);
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Host search limit must be between 1 and 1000");
        }
        String pattern = '%' + escapeLike(normalized) + '%';
        try {
            return database.query(connection -> {
                List<SavedHost> hosts = new ArrayList<>();
                String sql = "SELECT " + HOST_COLUMNS + " FROM saved_hosts "
                        + "WHERE ? = '' OR lower(name) LIKE ? ESCAPE '\\' OR lower(host) LIKE ? ESCAPE '\\' "
                        + "OR lower(username) LIKE ? ESCAPE '\\' OR CAST(port AS TEXT) LIKE ? ESCAPE '\\' "
                        + "ORDER BY sort_order, name COLLATE NOCASE LIMIT ?";
                try (var statement = connection.prepareStatement(sql)) {
                    statement.setString(1, normalized);
                    statement.setString(2, pattern);
                    statement.setString(3, pattern);
                    statement.setString(4, pattern);
                    statement.setString(5, pattern);
                    statement.setInt(6, limit);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            hosts.add(readHost(result));
                        }
                    }
                }
                return List.copyOf(hosts);
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to search saved hosts", failure);
        }
    }

    @Override
    public List<SavedHost> listHosts() throws IOException {
        try {
            return database.query(connection -> {
                List<SavedHost> hosts = new ArrayList<>();
                String sql = "SELECT " + HOST_COLUMNS + " FROM saved_hosts "
                        + "ORDER BY sort_order, name COLLATE NOCASE";
                try (var statement = connection.prepareStatement(sql);
                        ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        hosts.add(readHost(result));
                    }
                }
                return List.copyOf(hosts);
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to load hosts", failure);
        }
    }

    @Override
    public Optional<SavedHost> findHost(HostId id) throws IOException {
        Objects.requireNonNull(id, "id");
        try {
            return database.query(connection -> {
                try (var statement = connection.prepareStatement(
                        "SELECT " + HOST_COLUMNS + " FROM saved_hosts WHERE id = ?")) {
                    statement.setString(1, id.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        return result.next() ? Optional.of(readHost(result)) : Optional.empty();
                    }
                }
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to load saved host", failure);
        }
    }

    @Override
    public List<RecentConnection> listRecentConnections(int limit) throws IOException {
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("Recent connection limit must be between 1 and 1000");
        }
        try {
            return database.query(connection -> {
                List<RecentConnection> recent = new ArrayList<>();
                String sql = """
                        SELECT source, source_id, host_id, name, host, port, username,
                               authentication, connected_at, icon
                        FROM (
                            SELECT 'SAVED_HOST' AS source, id AS source_id, id AS host_id,
                                   name, host, port, username, authentication, last_connected_at AS connected_at,
                                   icon
                            FROM saved_hosts
                            WHERE last_connected_at IS NOT NULL
                            UNION ALL
                            SELECT 'QUICK_CONNECTION' AS source, id AS source_id, NULL AS host_id,
                                   host AS name, host, port, username, authentication, connected_at,
                                   'SERVER' AS icon
                            FROM quick_connection_history
                        )
                        ORDER BY connected_at DESC, name COLLATE NOCASE
                        LIMIT ?
                        """;
                try (var statement = connection.prepareStatement(sql)) {
                    statement.setInt(1, limit);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            RecentConnection.Source source = RecentConnection.Source.valueOf(result.getString("source"));
                            recent.add(new RecentConnection(
                                    source,
                                    result.getString("source_id"),
                                    optional(result.getString("host_id")).map(HostId::parse),
                                    result.getString("name"),
                                    new Host(result.getString("host")),
                                    new Port(result.getInt("port")),
                                    new Username(result.getString("username")),
                                    AuthenticationType.valueOf(result.getString("authentication")),
                                    Instant.ofEpochMilli(result.getLong("connected_at")),
                                    HostIcon.valueOf(result.getString("icon"))));
                        }
                    }
                }
                return List.copyOf(recent);
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to load recent connections", failure);
        }
    }

    @Override
    public void recordSuccessfulConnection(
            Optional<HostId> hostId, ConnectionProfile profile, Instant connectedAt) throws IOException {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(connectedAt, "connectedAt");
        try {
            database.transaction(connection -> {
                if (hostId.isPresent()) {
                    try (var statement = connection.prepareStatement(
                            "UPDATE saved_hosts SET last_connected_at = ? WHERE id = ?")) {
                        statement.setLong(1, connectedAt.toEpochMilli());
                        statement.setString(2, hostId.orElseThrow().toString());
                        if (statement.executeUpdate() != 1) {
                            throw new SQLException("Saved host does not exist");
                        }
                    }
                } else {
                    try (var statement = connection.prepareStatement("""
                            INSERT INTO quick_connection_history(
                                id, host, port, username, authentication, connected_at)
                            VALUES(?, ?, ?, ?, ?, ?)
                            ON CONFLICT DO UPDATE SET
                                host = excluded.host,
                                connected_at = excluded.connected_at
                            """)) {
                        statement.setString(1, UUID.randomUUID().toString());
                        statement.setString(2, profile.host().value());
                        statement.setInt(3, profile.port().value());
                        statement.setString(4, profile.username().value());
                        statement.setString(5, profile.authenticationType().name());
                        statement.setLong(6, connectedAt.toEpochMilli());
                        statement.executeUpdate();
                    }
                }
                return null;
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to record successful connection", failure);
        }
    }

    @Override
    public void saveGroup(HostGroup group) throws IOException {
        Objects.requireNonNull(group, "group");
        try {
            database.transaction(connection -> {
                if (group.parentId().isPresent()
                        && (!isRootGroup(connection, group.parentId().get()) || hasChildren(connection, group.id()))) {
                    throw new SQLException("Host groups support at most two levels");
                }
                if (group.parentId().isPresent() && createsCycle(connection, group.id(), group.parentId().get())) {
                    throw new SQLException("Moving the group would create a cycle");
                }
                long now = System.currentTimeMillis();
                try (var statement = connection.prepareStatement("""
                        INSERT INTO host_groups(id, parent_id, name, sort_order, created_at, updated_at, icon_color)
                        VALUES(?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            parent_id = excluded.parent_id,
                            name = excluded.name,
                            sort_order = excluded.sort_order,
                            icon_color = excluded.icon_color,
                            updated_at = excluded.updated_at
                        """)) {
                    statement.setString(1, group.id().toString());
                    statement.setString(2, group.parentId().map(GroupId::toString).orElse(null));
                    statement.setString(3, group.name());
                    statement.setInt(4, group.sortOrder());
                    statement.setLong(5, now);
                    statement.setLong(6, now);
                    statement.setString(7, group.color().name());
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to save host group", failure);
        }
    }

    @Override
    public void saveHost(SavedHost host) throws IOException {
        Objects.requireNonNull(host, "host");
        try {
            database.transaction(connection -> {
                try (var statement = connection.prepareStatement("""
                        INSERT INTO saved_hosts(
                            id, group_id, name, host, port, username, authentication,
                            private_key_path, legacy_algorithms, jump_host_id, credential_secret_id, favorite,
                            sort_order, created_at, updated_at, icon)
                        VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            group_id = excluded.group_id,
                            name = excluded.name,
                            host = excluded.host,
                            port = excluded.port,
                            username = excluded.username,
                            authentication = excluded.authentication,
                            private_key_path = excluded.private_key_path,
                            legacy_algorithms = excluded.legacy_algorithms,
                            jump_host_id = excluded.jump_host_id,
                            credential_secret_id = excluded.credential_secret_id,
                            favorite = excluded.favorite,
                            sort_order = excluded.sort_order,
                            icon = excluded.icon,
                            updated_at = excluded.updated_at
                        """)) {
                    ConnectionProfile profile = host.profile();
                    statement.setString(1, host.id().toString());
                    statement.setString(2, host.groupId().map(GroupId::toString).orElse(null));
                    statement.setString(3, host.name());
                    statement.setString(4, profile.host().value());
                    statement.setInt(5, profile.port().value());
                    statement.setString(6, profile.username().value());
                    statement.setString(7, profile.authenticationType().name());
                    statement.setString(8, profile.privateKeyPath().map(Path::toString).orElse(null));
                    statement.setInt(9, profile.legacyAlgorithmsEnabled() ? 1 : 0);
                    statement.setString(10, host.jumpHostId().map(HostId::toString).orElse(null));
                    statement.setString(11, host.credentialSecretId().map(SecretId::toString).orElse(null));
                    statement.setInt(12, host.favorite() ? 1 : 0);
                    statement.setInt(13, host.sortOrder());
                    statement.setLong(14, host.createdAt().toEpochMilli());
                    statement.setLong(15, host.updatedAt().toEpochMilli());
                    statement.setString(16, host.icon().name());
                    statement.executeUpdate();
                }
                validateJumpChain(connection, host.id());
                return null;
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to save host", failure);
        }
    }

    @Override
    public void moveGroup(GroupId id, Optional<GroupId> parentId, int targetIndex) throws IOException {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parentId, "parentId");
        if (targetIndex < 0) throw new IllegalArgumentException("Target index must not be negative");
        try {
            database.transaction(connection -> {
                if (parentId.isPresent()
                        && (!isRootGroup(connection, parentId.get()) || hasChildren(connection, id))) {
                    throw new SQLException("Host groups support at most two levels");
                }
                if (parentId.isPresent() && createsCycle(connection, id, parentId.get())) {
                    throw new SQLException("Moving the group would create a cycle");
                }
                List<String> siblingIds = new ArrayList<>();
                String sql = parentId.isPresent()
                        ? "SELECT id FROM host_groups WHERE parent_id = ? AND id <> ? ORDER BY sort_order, name COLLATE NOCASE"
                        : "SELECT id FROM host_groups WHERE parent_id IS NULL AND id <> ? ORDER BY sort_order, name COLLATE NOCASE";
                try (var statement = connection.prepareStatement(sql)) {
                    int offset = 1;
                    if (parentId.isPresent()) statement.setString(offset++, parentId.get().toString());
                    statement.setString(offset, id.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) siblingIds.add(result.getString(1));
                    }
                }
                siblingIds.add(Math.min(targetIndex, siblingIds.size()), id.toString());
                try (var statement = connection.prepareStatement(
                        "UPDATE host_groups SET parent_id = ?, sort_order = ?, updated_at = ? WHERE id = ?")) {
                    long now = System.currentTimeMillis();
                    for (int index = 0; index < siblingIds.size(); index++) {
                        statement.setString(1, parentId.map(GroupId::toString).orElse(null));
                        statement.setInt(2, index * 10);
                        statement.setLong(3, now);
                        statement.setString(4, siblingIds.get(index));
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return null;
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to move host group", failure);
        }
    }

    @Override
    public void moveHost(HostId id, Optional<GroupId> groupId, int targetIndex) throws IOException {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(groupId, "groupId");
        if (targetIndex < 0) throw new IllegalArgumentException("Target index must not be negative");
        try {
            database.transaction(connection -> {
                List<String> siblingIds = new ArrayList<>();
                String sql = groupId.isPresent()
                        ? "SELECT id FROM saved_hosts WHERE group_id = ? AND id <> ? ORDER BY sort_order, name COLLATE NOCASE"
                        : "SELECT id FROM saved_hosts WHERE group_id IS NULL AND id <> ? ORDER BY sort_order, name COLLATE NOCASE";
                try (var statement = connection.prepareStatement(sql)) {
                    int offset = 1;
                    if (groupId.isPresent()) statement.setString(offset++, groupId.get().toString());
                    statement.setString(offset, id.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) siblingIds.add(result.getString(1));
                    }
                }
                siblingIds.add(Math.min(targetIndex, siblingIds.size()), id.toString());
                try (var statement = connection.prepareStatement(
                        "UPDATE saved_hosts SET group_id = ?, sort_order = ?, updated_at = ? WHERE id = ?")) {
                    long now = System.currentTimeMillis();
                    for (int index = 0; index < siblingIds.size(); index++) {
                        statement.setString(1, groupId.map(GroupId::toString).orElse(null));
                        statement.setInt(2, index * 10);
                        statement.setLong(3, now);
                        statement.setString(4, siblingIds.get(index));
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return null;
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to move saved host", failure);
        }
    }

    @Override
    public void deleteGroup(GroupId id) throws IOException {
        Objects.requireNonNull(id, "id");
        try {
            database.transaction(connection -> {
                if (hasChildren(connection, id)) {
                    throw new SQLException("Host group still contains child groups");
                }
                try (var containsHosts = connection.prepareStatement(
                        "SELECT 1 FROM saved_hosts WHERE group_id = ? LIMIT 1")) {
                    containsHosts.setString(1, id.toString());
                    try (ResultSet result = containsHosts.executeQuery()) {
                        if (result.next()) throw new SQLException("Host group still contains hosts");
                    }
                }
                try (var statement = connection.prepareStatement("DELETE FROM host_groups WHERE id = ?")) {
                    statement.setString(1, id.toString());
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to delete host group", failure);
        }
    }

    @Override
    public List<String> deleteHost(HostId id) throws IOException {
        Objects.requireNonNull(id, "id");
        try {
            return database.transaction(connection -> {
                List<String> directHosts = new ArrayList<>();
                try (var statement = connection.prepareStatement(
                        "UPDATE saved_hosts SET jump_host_id = NULL WHERE jump_host_id = ? RETURNING name")) {
                    statement.setString(1, id.toString());
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) directHosts.add(rows.getString(1));
                    }
                }
                try (var statement = connection.prepareStatement("DELETE FROM saved_hosts WHERE id = ?")) {
                    statement.setString(1, id.toString());
                    statement.executeUpdate();
                }
                directHosts.sort(String.CASE_INSENSITIVE_ORDER);
                return List.copyOf(directHosts);
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to delete saved host", failure);
        }
    }

    private static boolean createsCycle(java.sql.Connection connection, GroupId group, GroupId proposedParent)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                WITH RECURSIVE ancestors(id, parent_id) AS (
                    SELECT id, parent_id FROM host_groups WHERE id = ?
                    UNION ALL
                    SELECT g.id, g.parent_id FROM host_groups g JOIN ancestors a ON g.id = a.parent_id
                )
                SELECT 1 FROM ancestors WHERE id = ? LIMIT 1
                """)) {
            statement.setString(1, proposedParent.toString());
            statement.setString(2, group.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean isRootGroup(java.sql.Connection connection, GroupId group) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT parent_id FROM host_groups WHERE id = ?")) {
            statement.setString(1, group.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getString(1) == null;
            }
        }
    }

    private static boolean hasChildren(java.sql.Connection connection, GroupId group) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM host_groups WHERE parent_id = ? LIMIT 1")) {
            statement.setString(1, group.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static SavedHost readHost(ResultSet result) throws SQLException {
        AuthenticationType authentication = AuthenticationType.valueOf(result.getString("authentication"));
        String keyPath = result.getString("private_key_path");
        ConnectionProfile profile = new ConnectionProfile(
                new Host(result.getString("host")),
                new Port(result.getInt("port")),
                new Username(result.getString("username")),
                authentication,
                optional(keyPath).map(Path::of),
                result.getInt("legacy_algorithms") == 1);
        return new SavedHost(
                HostId.parse(result.getString("id")),
                optional(result.getString("group_id")).map(GroupId::parse),
                result.getString("name"),
                profile,
                optional(result.getString("jump_host_id")).map(HostId::parse),
                optional(result.getString("credential_secret_id")).map(SecretId::parse),
                result.getInt("favorite") == 1,
                result.getInt("sort_order"),
                Instant.ofEpochMilli(result.getLong("created_at")),
                Instant.ofEpochMilli(result.getLong("updated_at")),
                HostIcon.valueOf(result.getString("icon")));
    }

    private static void validateJumpChain(java.sql.Connection connection, HostId start) throws SQLException {
        java.util.Set<String> visited = new java.util.HashSet<>();
        String current = start.toString();
        for (int depth = 0; depth <= 5; depth++) {
            if (!visited.add(current)) throw new SQLException("Jump host chain contains a cycle");
            try (var statement = connection.prepareStatement(
                    "SELECT jump_host_id FROM saved_hosts WHERE id = ?")) {
                statement.setString(1, current);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new SQLException("Jump host host does not exist");
                    String next = result.getString(1);
                    if (next == null) return;
                    current = next;
                }
            }
        }
        throw new SQLException("Jump host chain exceeds the maximum of 5 hops");
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static IOException storageFailure(String message, SQLException cause) {
        return new IOException(message, cause);
    }
}
