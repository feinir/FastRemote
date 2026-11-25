// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.snippet.*;
import com.ynpe.fastremote.storage.FastRemoteDatabase;
import com.ynpe.fastremote.storage.SqliteSnippetRepository;
import com.ynpe.fastremote.ui.ImportConflictPolicy;
import com.ynpe.fastremote.ui.SnippetSnapshot;
import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteSnippetWorkflowTest {
    @TempDir Path temporaryDirectory;

    @Test
    void rejectsLegacyCommandFormatAndImportsStrictTsv() throws Exception {
        Path legacy = write("legacy.tsv", "==巡检\n主机名\\thostname\\t说明");
        Path source = write("commands.tsv", """
                @FASTREMOTE-COMMANDS:1
                @group\t巡检\tGREEN
                @command\t巡检\t主机名\thostname\t显示主机名
                """);
        try (var database = database("strict.db"); var workflow = workflow(database)) {
            assertThrows(CompletionException.class,
                    () -> workflow.inspectText(legacy).toCompletableFuture().join());
            assertEquals(1, workflow.importText(source, ImportConflictPolicy.OVERWRITE)
                    .toCompletableFuture().join().commandCount());
            SnippetSnapshot snapshot = workflow.load().toCompletableFuture().join();
            assertEquals(List.of("巡检"), snapshot.groups().stream().map(SnippetGroup::name).toList());
            assertEquals("hostname", snapshot.snippets().getFirst().content());
        }
    }

    @Test
    void variableTsvRoundTripsGroupsTypesAndLiteralMultiCharacterDelimiter() throws Exception {
        Path source = write("variables.tsv", """
                @FASTREMOTE-VARIABLES:1
                @group\t生产环境\tBLUE
                @variable\t生产环境\t目标主机\tHosts\tARRAY\t||\ta.example||b.example\t部署目标
                @variable\t\t默认目录\tbase_dir\tSINGLE\t\t/srv/app\t
                """);
        Path target = temporaryDirectory.resolve("variables-export.tsv");
        try (var database = database("variables.db"); var workflow = workflow(database)) {
            assertEquals(2, workflow.importVariablesText(source, ImportConflictPolicy.OVERWRITE)
                    .toCompletableFuture().join().variableCount());
            SnippetVariable hosts = workflow.load().toCompletableFuture().join().variables().stream()
                    .filter(value -> value.variableName().equals("Hosts")).findFirst().orElseThrow();
            assertEquals(List.of("a.example", "b.example"), hosts.expandedValues());
            assertEquals("b.example", hosts.indexedValue(2));
            assertEquals(2, workflow.exportVariablesText(target).toCompletableFuture().join());
        }
        String exported = Files.readString(target, StandardCharsets.UTF_8);
        assertTrue(exported.startsWith("@FASTREMOTE-VARIABLES:1\n@group\t生产环境\tBLUE"));
        assertTrue(exported.contains("@variable\t生产环境\t目标主机\tHosts\tARRAY\t||"));
    }

    @Test
    void commandExportIncludesOnlyReferencedVariablesBeforeCommandSection() throws Exception {
        Path target = temporaryDirectory.resolve("share.tsv");
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        try (var database = database("export.db")) {
            var repository = new SqliteSnippetRepository(database);
            SnippetVariableGroup group = new SnippetVariableGroup(
                    SnippetVariableGroupId.create(), "环境", 0, GroupColor.BLUE);
            repository.saveVariableGroup(group);
            repository.saveVariables(List.of(
                    variable("目标", "Hosts", SnippetVariableType.ARRAY, ",", "a,b", Optional.of(group.id()), 0),
                    variable("未使用", "Unused", SnippetVariableType.SINGLE, "", "x", Optional.empty(), 0)));
            repository.saveSnippet(new Snippet(SnippetId.create(), Optional.empty(), "连接", "",
                    "ping {{input,主机,variable=Hosts}}; echo {{variable,Hosts,index=2}}", 0, now, now));
            try (var workflow = new SqliteSnippetWorkflow(repository)) {
                assertEquals(1, workflow.exportText(target).toCompletableFuture().join());
            }
        }
        String exported = Files.readString(target, StandardCharsets.UTF_8);
        assertTrue(exported.indexOf("@FASTREMOTE-VARIABLES:1") < exported.indexOf("@FASTREMOTE-COMMANDS:1"));
        assertTrue(exported.contains("\tHosts\tARRAY\t,\ta,b\t"));
        assertFalse(exported.contains("Unused"));
    }

    @Test
    void conflictPolicyIsBatchWideAndVariableNamesAreCaseSensitive() throws Exception {
        Path first = write("first.tsv", """
                @FASTREMOTE-VARIABLES:1
                @variable\t\t小写\thost\tSINGLE\t\tone\t
                @variable\t\t大写\tHost\tSINGLE\t\ttwo\t
                """);
        Path second = write("second.tsv", """
                @FASTREMOTE-VARIABLES:1
                @variable\t\t替换\thost\tSINGLE\t\tchanged\t
                """);
        try (var database = database("conflicts.db"); var workflow = workflow(database)) {
            workflow.importVariablesText(first, ImportConflictPolicy.OVERWRITE).toCompletableFuture().join();
            assertEquals(1, workflow.inspectVariablesText(second).toCompletableFuture().join()
                    .variableConflictCount());
            assertEquals(0, workflow.importVariablesText(second, ImportConflictPolicy.SKIP)
                    .toCompletableFuture().join().variableCount());
            workflow.importVariablesText(second, ImportConflictPolicy.OVERWRITE).toCompletableFuture().join();
            assertEquals(List.of("Host", "host"), workflow.load().toCompletableFuture().join().variables().stream()
                    .map(SnippetVariable::variableName).sorted().toList());
            assertEquals("changed", workflow.load().toCompletableFuture().join().variables().stream()
                    .filter(value -> value.variableName().equals("host")).findFirst().orElseThrow().value());
        }
    }

    @Test
    void curatedAndBundledTsvFilesAreImportable() throws Exception {
        Path example = projectFile("docs/examples/command-library-import.tsv");
        try (var database = database("examples.db"); var workflow = workflow(database)) {
            assertEquals(54, workflow.importText(example, ImportConflictPolicy.OVERWRITE)
                    .toCompletableFuture().join().commandCount());
            workflow.load().toCompletableFuture().join().snippets()
                    .forEach(command -> new SnippetTemplateEngine().analyze(command.content()));
        }
        try (var database = database("bundled.db"); var workflow = workflow(database)) {
            assertTrue(workflow.load().toCompletableFuture().join().snippets().isEmpty(),
                    "Opening a new database must not import bundled commands");
            assertEquals(new BuiltinSnippetImportResult(173, 0),
                    workflow.importBuiltinCommands(InterfaceLanguage.SIMPLIFIED_CHINESE)
                            .toCompletableFuture().join());
            SnippetSnapshot snapshot = workflow.load().toCompletableFuture().join();
            assertEquals(List.of("Linux常用", "Windows常用", "macOS常用"),
                    snapshot.groups().stream().map(SnippetGroup::name).toList());
            assertEquals(new BuiltinSnippetImportResult(0, 173),
                    workflow.importBuiltinCommands(InterfaceLanguage.SIMPLIFIED_CHINESE)
                            .toCompletableFuture().join());
            assertEquals(new BuiltinSnippetImportResult(0, 173, 0),
                    workflow.importBuiltinCommands(
                                    InterfaceLanguage.ENGLISH, BuiltinSnippetConflictPolicy.OVERWRITE)
                            .toCompletableFuture().join());
            assertEquals(List.of("Linux Essentials", "Windows Essentials", "macOS Essentials"),
                    workflow.load().toCompletableFuture().join().groups().stream()
                            .map(SnippetGroup::name).toList());
        }
    }

    private Path write(String name, String content) throws Exception {
        Path path = temporaryDirectory.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private FastRemoteDatabase database(String name) throws Exception {
        return new FastRemoteDatabase(temporaryDirectory.resolve(name));
    }

    private static SqliteSnippetWorkflow workflow(FastRemoteDatabase database) {
        return new SqliteSnippetWorkflow(new SqliteSnippetRepository(database));
    }

    private static SnippetVariable variable(
            String name, String variableName, SnippetVariableType type, String delimiter, String value,
            Optional<SnippetVariableGroupId> groupId, int order) {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        return new SnippetVariable(SnippetVariableId.create(), groupId, name, variableName,
                type, delimiter, value, "", order, now, now);
    }

    private static Path projectFile(String relative) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("找不到项目文件：" + relative);
    }
}
