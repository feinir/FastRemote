// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.RecentConnection;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.catalog.HostIcon;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.Username;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;

class SqliteHostCatalogRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void restrictsDatabaseAndJournalPermissionsOnPosixFileSystems() throws Exception {
        Path databasePath = temporaryDirectory.resolve("permissions.db");
        try (var ignored = new FastRemoteDatabase(databasePath)) {
            if (Files.getFileAttributeView(databasePath, PosixFileAttributeView.class) == null) {
                return;
            }
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(temporaryDirectory));
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(databasePath));
            Path wal = Path.of(databasePath + "-wal");
            Path sharedMemory = Path.of(databasePath + "-shm");
            if (Files.exists(wal)) assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(wal));
            if (Files.exists(sharedMemory)) assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(sharedMemory));
        }
    }

    @Test
    void persistsGroupsAndSearchableHosts() throws Exception {
        try (var database = new FastRemoteDatabase(temporaryDirectory.resolve("catalog.db"))) {
            var repository = new SqliteHostCatalogRepository(database);
            HostGroup production = new HostGroup(
                    com.ynpe.fastremote.domain.catalog.GroupId.create(), Optional.empty(), "生产环境", 0, GroupColor.GREEN);
            repository.saveGroup(production);
            ConnectionProfile profile = new ConnectionProfile(
                    new Host("db-01.example.com"),
                    new Port(2222),
                    new Username("deploy"),
                    AuthenticationType.PASSWORD,
                    Optional.empty(),
                    true);
            SavedHost host = SavedHost.create(
                    "数据库主机", Optional.of(production.id()), profile, 0, HostIcon.DATABASE);
            repository.saveHost(host);

            assertEquals(1, repository.listGroups().size());
            assertEquals(host, repository.findHost(host.id()).orElseThrow());
            assertEquals(host, repository.searchHosts("db-01", 20).getFirst());
            assertEquals(host, repository.searchHosts("数据库", 20).getFirst());
            assertEquals(host, repository.searchHosts("DEPLOY", 20).getFirst());
            assertEquals(host, repository.searchHosts("2222", 20).getFirst());

            repository.deleteHost(host.id());
            assertTrue(repository.findHost(host.id()).isEmpty());
        }
    }

    @Test
    void rejectsJumpCyclesAndMakesDirectDependentsConnectDirectlyOnDeletion() throws Exception {
        try (var database = new FastRemoteDatabase(temporaryDirectory.resolve("jump-routes.db"))) {
            var repository = new SqliteHostCatalogRepository(database);
            ConnectionProfile profile = new ConnectionProfile(
                    new Host("host.example.com"), new Port(22), new Username("ops"),
                    AuthenticationType.PASSWORD, Optional.empty());
            SavedHost jump = SavedHost.create("jump", Optional.empty(), profile, 0);
            SavedHost target = SavedHost.create("target", Optional.empty(), profile, 10);
            repository.saveHost(jump);
            repository.saveHost(target);
            target = target.withJumpHost(Optional.of(jump.id()));
            repository.saveHost(target);

            assertEquals(Optional.of(jump.id()), repository.findHost(target.id()).orElseThrow().jumpHostId());
            SavedHost cyclicJump = jump.withJumpHost(Optional.of(target.id()));
            assertThrows(IOException.class, () -> repository.saveHost(cyclicJump));
            SavedHost downstream = SavedHost.create("downstream", Optional.empty(), profile, 20)
                    .withJumpHost(Optional.of(target.id()));
            repository.saveHost(downstream);

            assertEquals(List.of("target"), repository.deleteHost(jump.id()));

            assertTrue(repository.findHost(jump.id()).isEmpty());
            assertEquals(Optional.empty(), repository.findHost(target.id()).orElseThrow().jumpHostId());
            assertEquals(Optional.of(target.id()), repository.findHost(downstream.id()).orElseThrow().jumpHostId());
        }
    }

    @Test
    void rejectsGroupCycles() throws Exception {
        try (var database = new FastRemoteDatabase(temporaryDirectory.resolve("cycle.db"))) {
            var repository = new SqliteHostCatalogRepository(database);
            HostGroup parent = HostGroup.root("父组");
            HostGroup child = new HostGroup(
                    com.ynpe.fastremote.domain.catalog.GroupId.create(), Optional.of(parent.id()), "子组", 0);
            repository.saveGroup(parent);
            repository.saveGroup(child);

            HostGroup invalid = new HostGroup(parent.id(), Optional.of(child.id()), parent.name(), 0);
            assertThrows(IOException.class, () -> repository.saveGroup(invalid));
        }
    }

    @Test
    void limitsGroupsToTwoLevels() throws Exception {
        try (var database = new FastRemoteDatabase(temporaryDirectory.resolve("depth.db"))) {
            var repository = new SqliteHostCatalogRepository(database);
            HostGroup root = HostGroup.root("项目");
            HostGroup child = new HostGroup(
                    com.ynpe.fastremote.domain.catalog.GroupId.create(), Optional.of(root.id()), "数据库", 0);
            HostGroup grandchild = new HostGroup(
                    com.ynpe.fastremote.domain.catalog.GroupId.create(), Optional.of(child.id()), "生产", 0);
            repository.saveGroup(root);
            repository.saveGroup(child);

            IOException failure = assertThrows(IOException.class, () -> repository.saveGroup(grandchild));
            assertTrue(failure.getCause().getMessage().contains("at most two levels"));
        }
    }

    @Test
    void movesAndOrdersHostsWithinRootOrGroup() throws Exception {
        try (var database = new FastRemoteDatabase(temporaryDirectory.resolve("order.db"))) {
            var repository = new SqliteHostCatalogRepository(database);
            HostGroup group = HostGroup.root("K8s");
            repository.saveGroup(group);
            ConnectionProfile profile = new ConnectionProfile(
                    new Host("node.example.com"), new Port(22), new Username("ops"),
                    AuthenticationType.PASSWORD, Optional.empty());
            SavedHost first = SavedHost.create("A", Optional.empty(), profile, 0);
            SavedHost second = SavedHost.create("B", Optional.empty(), profile, 10);
            SavedHost third = SavedHost.create("C", Optional.empty(), profile, 20);
            repository.saveHost(first);
            repository.saveHost(second);
            repository.saveHost(third);

            repository.moveHost(third.id(), Optional.empty(), 0);
            assertEquals(java.util.List.of("C", "A", "B"),
                    repository.listHosts().stream().map(SavedHost::name).toList());

            repository.moveHost(first.id(), Optional.of(group.id()), 0);
            SavedHost moved = repository.findHost(first.id()).orElseThrow();
            assertEquals(Optional.of(group.id()), moved.groupId());
            assertEquals(0, moved.sortOrder());
        }
    }

    @Test
    void movesAndOrdersGroupsWithoutAllowingAThirdLevel() throws Exception {
        try (var database = new FastRemoteDatabase(temporaryDirectory.resolve("group-order.db"))) {
            var repository = new SqliteHostCatalogRepository(database);
            HostGroup first = new HostGroup(
                    com.ynpe.fastremote.domain.catalog.GroupId.create(), Optional.empty(), "A", 0);
            HostGroup second = new HostGroup(
                    com.ynpe.fastremote.domain.catalog.GroupId.create(), Optional.empty(), "B", 10);
            HostGroup child = new HostGroup(
                    com.ynpe.fastremote.domain.catalog.GroupId.create(), Optional.of(first.id()), "A1", 0);
            repository.saveGroup(first);
            repository.saveGroup(second);
            repository.saveGroup(child);

            repository.moveGroup(second.id(), Optional.empty(), 0);
            assertEquals(java.util.List.of("B", "A"), repository.listGroups().stream()
                    .filter(group -> group.parentId().isEmpty()).map(HostGroup::name).toList());

            IOException failure = assertThrows(
                    IOException.class, () -> repository.moveGroup(first.id(), Optional.of(second.id()), 0));
            assertTrue(failure.getCause().getMessage().contains("at most two levels"));
        }
    }

    @Test
    void deletesOnlyEmptyGroups() throws Exception {
        try (var database = new FastRemoteDatabase(temporaryDirectory.resolve("delete-group.db"))) {
            var repository = new SqliteHostCatalogRepository(database);
            HostGroup empty = HostGroup.root("空分组");
            HostGroup occupied = HostGroup.root("非空分组");
            repository.saveGroup(empty);
            repository.saveGroup(occupied);
            ConnectionProfile profile = new ConnectionProfile(
                    new Host("host.example.com"), new Port(22), new Username("ops"),
                    AuthenticationType.PASSWORD, Optional.empty());
            repository.saveHost(SavedHost.create("主机", Optional.of(occupied.id()), profile));

            repository.deleteGroup(empty.id());
            assertEquals(java.util.List.of("非空分组"), repository.listGroups().stream()
                    .map(HostGroup::name).toList());
            assertThrows(IOException.class, () -> repository.deleteGroup(occupied.id()));
        }
    }

    @Test
    void combinesSavedAndQuickConnectionsByTheirActualSuccessfulConnectionTime() throws Exception {
        try (var database = new FastRemoteDatabase(temporaryDirectory.resolve("history.db"))) {
            var repository = new SqliteHostCatalogRepository(database);
            ConnectionProfile savedProfile = new ConnectionProfile(
                    new Host("saved.example.com"), new Port(22), new Username("deploy"),
                    AuthenticationType.PASSWORD, Optional.empty());
            SavedHost saved = SavedHost.create("生产主机", Optional.empty(), savedProfile);
            repository.saveHost(saved);
            Instant savedTime = Instant.parse("2026-08-23T10:00:00Z");
            repository.recordSuccessfulConnection(Optional.of(saved.id()), savedProfile, savedTime);

            ConnectionProfile quickProfile = new ConnectionProfile(
                    new Host("quick.example.com"), new Port(2222), new Username("root"),
                    AuthenticationType.PRIVATE_KEY, Optional.empty());
            repository.recordSuccessfulConnection(
                    Optional.empty(), quickProfile, Instant.parse("2026-08-23T09:00:00Z"));
            Instant latest = Instant.parse("2026-08-23T11:00:00Z");
            repository.recordSuccessfulConnection(Optional.empty(), quickProfile, latest);

            var recent = repository.listRecentConnections(10);
            assertEquals(2, recent.size());
            assertEquals(RecentConnection.Source.QUICK_CONNECTION, recent.getFirst().source());
            assertEquals(latest, recent.getFirst().connectedAt());
            assertEquals(AuthenticationType.PRIVATE_KEY, recent.getFirst().authenticationType());
            assertTrue(recent.getFirst().hostId().isEmpty());
            assertEquals(RecentConnection.Source.SAVED_HOST, recent.getLast().source());
            assertEquals(Optional.of(saved.id()), recent.getLast().hostId());
            assertEquals(saved.updatedAt(), repository.findHost(saved.id()).orElseThrow().updatedAt());
        }
    }

    @Test
    void createsCurrentSchemaDirectlyWithOrderingAndIcons() throws Exception {
        try (var database = new FastRemoteDatabase(temporaryDirectory.resolve("schema.db"))) {
            int version = database.<Integer>query(connection -> {
                try (var statement = connection.createStatement();
                        var result = statement.executeQuery("PRAGMA user_version")) {
                    return result.getInt(1);
                }
            });
            assertEquals(10, version);
            java.util.Set<String> columns = database.<java.util.Set<String>>query(connection -> {
                var names = new java.util.HashSet<String>();
                try (var statement = connection.createStatement();
                        var result = statement.executeQuery("PRAGMA table_info(saved_hosts)")) {
                    while (result.next()) names.add(result.getString("name"));
                    return names;
                }
            });
            assertTrue(columns.containsAll(java.util.Set.of(
                    "sort_order", "icon", "jump_host_id", "last_connected_at")));
            assertEquals(1, database.<Integer>query(connection -> {
                try (var statement = connection.createStatement();
                        var result = statement.executeQuery(
                                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='quick_connection_history'")) {
                    return result.getInt(1);
                }
            }));
        }
    }

}
