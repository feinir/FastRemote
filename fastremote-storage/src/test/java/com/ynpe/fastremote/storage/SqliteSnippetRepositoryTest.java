// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ynpe.fastremote.domain.snippet.Snippet;
import com.ynpe.fastremote.domain.snippet.SnippetGroup;
import com.ynpe.fastremote.domain.snippet.SnippetGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetId;
import com.ynpe.fastremote.domain.snippet.SnippetVariable;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroup;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetVariableId;
import com.ynpe.fastremote.domain.snippet.SnippetVariableType;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippet;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippetConflictPolicy;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippetGroup;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippetImportResult;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import java.util.List;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteSnippetRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void persistsGroupsSnippetsAndPlainLocalVariables() throws Exception {
        try (var database = new FastRemoteDatabase(temporaryDirectory.resolve("snippets.db"))) {
            var repository = new SqliteSnippetRepository(database);
            SnippetGroup group = new SnippetGroup(SnippetGroupId.create(), "部署", 0);
            repository.saveGroup(group);
            Instant now = Instant.parse("2026-08-22T00:00:00Z");
            Snippet snippet = new Snippet(SnippetId.create(), Optional.of(group.id()), "发布", "发布备份服务", """
                    cd "{{variable, backup_dir}}"
                    ./deploy.sh \\
                      "{{input, version}}"
                    """, 0, now, now);
            repository.saveSnippet(snippet);
            SnippetVariableGroup variableGroup = new SnippetVariableGroup(
                    SnippetVariableGroupId.create(), "路径", 0,
                    com.ynpe.fastremote.domain.catalog.GroupColor.GREEN);
            repository.saveVariableGroup(variableGroup);
            SnippetVariable variable = new SnippetVariable(
                    SnippetVariableId.create(), Optional.of(variableGroup.id()), "备份目录", "backup_dir",
                    SnippetVariableType.SINGLE, "", "/srv/backup", "备份目录", 0, now, now);
            repository.saveVariable(variable);

            assertEquals(java.util.List.of(group), repository.listGroups());
            assertEquals(java.util.List.of(snippet), repository.listSnippets());
            assertEquals("发布备份服务", repository.listSnippets().getFirst().description());
            assertEquals("/srv/backup", repository.listVariables().getFirst().value());
            assertEquals(java.util.List.of(variableGroup), repository.listVariableGroups());
            assertThrows(IOException.class, () -> repository.deleteGroup(group.id()));
            assertThrows(IOException.class, () -> repository.deleteVariableGroup(variableGroup.id()));

            repository.deleteSnippet(snippet.id());
            repository.deleteGroup(group.id());
            repository.deleteVariable(variable.id());
            repository.deleteVariableGroup(variableGroup.id());
            assertEquals(java.util.List.of(), repository.listGroups());
            assertEquals(java.util.List.of(), repository.listSnippets());
            assertEquals(java.util.List.of(), repository.listVariables());
            assertEquals(java.util.List.of(), repository.listVariableGroups());
        }
    }

    @Test
    void ignoresStoredDatabaseVersionAndUsesCurrentSchema() throws Exception {
        Path path = temporaryDirectory.resolve("schema-version.db");
        try (var database = new FastRemoteDatabase(path)) {
            database.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("PRAGMA user_version=99");
                }
                return null;
            });
        }

        try (var database = new FastRemoteDatabase(path)) {
            var repository = new SqliteSnippetRepository(database);
            repository.saveGroup(new SnippetGroup(SnippetGroupId.create(), "首发分组", 0));
            assertEquals("首发分组", repository.listGroups().getFirst().name());
            assertEquals(10, database.<Integer>query(connection -> {
                try (var statement = connection.createStatement();
                        var result = statement.executeQuery("PRAGMA user_version")) {
                    return result.getInt(1);
                }
            }));
        }
    }

    @Test
    void migratesSchemaTwoDatabaseToBuiltinCommandTracking() throws Exception {
        Path path = temporaryDirectory.resolve("schema-two.db");
        try (var database = new FastRemoteDatabase(path)) {
            database.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("DROP TABLE builtin_snippets");
                    statement.execute("DROP TABLE builtin_snippet_groups");
                    statement.execute("PRAGMA user_version=2");
                }
                return null;
            });
        }

        try (var database = new FastRemoteDatabase(path)) {
            var repository = new SqliteSnippetRepository(database);
            var groups = List.of(new BuiltinSnippetGroup(
                    "core.group.linux", "Linux 常用", 0, GroupColor.BLUE));
            var commands = List.of(new BuiltinSnippet(
                    "core.command.uname", Optional.of("core.group.linux"),
                    "系统版本", "显示系统版本", "uname -a", 0));

            assertEquals(new com.ynpe.fastremote.domain.snippet.BuiltinSnippetImportResult(1, 0),
                    repository.importBuiltinSnippets("zh-CN", groups, commands));
            assertEquals(10, database.<Integer>query(connection -> {
                try (var statement = connection.createStatement();
                        var result = statement.executeQuery("PRAGMA user_version")) {
                    return result.getInt(1);
                }
            }));
        }
    }

    @Test
    void builtinImportOnlyAppendsPreviouslyUnseenStableIds() throws Exception {
        try (var database = new FastRemoteDatabase(temporaryDirectory.resolve("builtins.db"))) {
            var repository = new SqliteSnippetRepository(database);
            var groups = List.of(new BuiltinSnippetGroup("core.group.linux", "Linux 常用", 0, GroupColor.BLUE));
            var first = List.of(new BuiltinSnippet("core.command.uname", Optional.of("core.group.linux"),
                    "系统版本", "显示系统版本", "uname -a", 0));

            assertEquals(new com.ynpe.fastremote.domain.snippet.BuiltinSnippetImportResult(1, 0),
                    repository.importBuiltinSnippets("zh-CN", groups, first));
            assertEquals(new com.ynpe.fastremote.domain.snippet.BuiltinSnippetImportResult(0, 1),
                    repository.importBuiltinSnippets("en", List.of(
                            new BuiltinSnippetGroup("core.group.linux", "Linux Essentials", 0, GroupColor.BLUE)),
                            List.of(new BuiltinSnippet("core.command.uname", Optional.of("core.group.linux"),
                                    "System version", "Show system version", "uname -a", 0))));

            var withNewVersionCommand = List.of(
                    first.getFirst(),
                    new BuiltinSnippet("core.command.uptime", Optional.of("core.group.linux"),
                            "在线时长", "显示在线时长", "uptime", 10));
            assertEquals(new com.ynpe.fastremote.domain.snippet.BuiltinSnippetImportResult(1, 1),
                    repository.importBuiltinSnippets("zh-CN", groups, withNewVersionCommand));
            assertEquals(List.of("系统版本", "在线时长"), repository.listSnippets().stream()
                    .map(Snippet::name).toList());
            assertEquals("Linux 常用", repository.listGroups().getFirst().name());
        }
    }

    @Test
    void sameNameCommandIsClaimedAsExistingAndNeverReimportedAfterRename() throws Exception {
        try (var database = new FastRemoteDatabase(temporaryDirectory.resolve("builtin-name-collision.db"))) {
            var repository = new SqliteSnippetRepository(database);
            var group = new SnippetGroup(SnippetGroupId.create(), "Linux 常用", 0);
            repository.saveGroup(group);
            Instant now = Instant.parse("2026-08-26T00:00:00Z");
            var existing = new Snippet(SnippetId.create(), Optional.of(group.id()), "系统版本",
                    "用户已有的命令", "uname -a", 0, now, now);
            repository.saveSnippet(existing);
            var groups = List.of(new BuiltinSnippetGroup(
                    "core.group.linux", "Linux 常用", 0, GroupColor.BLUE));
            var commands = List.of(new BuiltinSnippet("core.command.uname", Optional.of("core.group.linux"),
                    "系统版本", "内置说明", "uname -a", 0));

            assertEquals(new com.ynpe.fastremote.domain.snippet.BuiltinSnippetImportResult(0, 1),
                    repository.importBuiltinSnippets("zh-CN", groups, commands));
            repository.saveSnippet(new Snippet(existing.id(), existing.groupId(), "自定义系统版本",
                    existing.description(), existing.content(), existing.sortOrder(), existing.createdAt(), now));
            assertEquals(new com.ynpe.fastremote.domain.snippet.BuiltinSnippetImportResult(0, 1),
                    repository.importBuiltinSnippets("zh-CN", groups, commands));
            assertEquals(List.of("自定义系统版本"), repository.listSnippets().stream().map(Snippet::name).toList());
        }
    }

    @Test
    void overwriteBuiltinImportReplacesUserChangesByStableIdAndKeepsDatabaseIds() throws Exception {
        try (var database = new FastRemoteDatabase(temporaryDirectory.resolve("builtin-overwrite.db"))) {
            var repository = new SqliteSnippetRepository(database);
            var chineseGroups = List.of(new BuiltinSnippetGroup(
                    "core.group.linux", "Linux 常用", 0, GroupColor.BLUE));
            var chineseCommands = List.of(new BuiltinSnippet(
                    "core.command.uname", Optional.of("core.group.linux"),
                    "系统版本", "显示系统版本", "uname -a", 0));
            repository.importBuiltinSnippets("zh-CN", chineseGroups, chineseCommands);

            SnippetGroup originalGroup = repository.listGroups().getFirst();
            Snippet originalCommand = repository.listSnippets().getFirst();
            repository.saveGroup(new SnippetGroup(
                    originalGroup.id(), "用户修改的分组", 70, GroupColor.RED));
            repository.saveSnippet(new Snippet(
                    originalCommand.id(), Optional.empty(), "用户修改的命令", "用户说明", "echo changed",
                    80, originalCommand.createdAt(), Instant.parse("2026-09-04T00:00:00Z")));

            var englishGroups = List.of(new BuiltinSnippetGroup(
                    "core.group.linux", "Linux Essentials", 10, GroupColor.GREEN));
            var englishCommands = List.of(new BuiltinSnippet(
                    "core.command.uname", Optional.of("core.group.linux"),
                    "System version", "Show system version", "uname -a", 20));
            assertEquals(new BuiltinSnippetImportResult(0, 1, 0),
                    repository.importBuiltinSnippets("en", BuiltinSnippetConflictPolicy.OVERWRITE,
                            englishGroups, englishCommands));

            SnippetGroup replacedGroup = repository.listGroups().getFirst();
            Snippet replacedCommand = repository.listSnippets().getFirst();
            assertEquals(originalGroup.id(), replacedGroup.id());
            assertEquals("Linux Essentials", replacedGroup.name());
            assertEquals(10, replacedGroup.sortOrder());
            assertEquals(GroupColor.GREEN, replacedGroup.color());
            assertEquals(originalCommand.id(), replacedCommand.id());
            assertEquals(Optional.of(originalGroup.id()), replacedCommand.groupId());
            assertEquals("System version", replacedCommand.name());
            assertEquals("Show system version", replacedCommand.description());
            assertEquals("uname -a", replacedCommand.content());
            assertEquals(20, replacedCommand.sortOrder());
            assertEquals(List.of("en", "en"), database.<List<String>>query(connection -> {
                List<String> languages = new java.util.ArrayList<>();
                try (var statement = connection.createStatement();
                        var result = statement.executeQuery("""
                                SELECT imported_language FROM builtin_snippet_groups
                                UNION ALL
                                SELECT imported_language FROM builtin_snippets
                                """)) {
                    while (result.next()) languages.add(result.getString(1));
                }
                return languages;
            }));
        }
    }
}
