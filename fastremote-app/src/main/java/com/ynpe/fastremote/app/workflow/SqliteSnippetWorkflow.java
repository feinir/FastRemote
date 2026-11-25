// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.snippet.Snippet;
import com.ynpe.fastremote.domain.snippet.SnippetGroup;
import com.ynpe.fastremote.domain.snippet.SnippetGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetId;
import com.ynpe.fastremote.domain.snippet.SnippetRepository;
import com.ynpe.fastremote.domain.snippet.SnippetTemplateEngine;
import com.ynpe.fastremote.domain.snippet.SnippetVariable;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroup;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetVariableId;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippet;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippetConflictPolicy;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippetGroup;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippetImportResult;
import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.ui.ImportConflictPolicy;
import com.ynpe.fastremote.ui.LibraryImportPreview;
import com.ynpe.fastremote.ui.LibraryImportResult;
import com.ynpe.fastremote.ui.SnippetSnapshot;
import com.ynpe.fastremote.ui.SnippetWorkflow;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public final class SqliteSnippetWorkflow implements SnippetWorkflow {
    private static final long MAX_IMPORT_BYTES = 4L * 1024 * 1024;
    private final SnippetRepository repository;
    private final SnippetTemplateEngine templateEngine = new SnippetTemplateEngine();
    private final BoundedWorkflowExecutor executor;

    public SqliteSnippetWorkflow(SnippetRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        executor = new BoundedWorkflowExecutor("fastremote-snippets-", 32,
                BoundedWorkflowExecutor.ClosePolicy.CANCEL_PENDING,
                () -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.2f9923db8e658705"), () -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.85cc351e980aa7ad"));
    }

    @Override
    public CompletionStage<SnippetSnapshot> load() {
        return supply(() -> new SnippetSnapshot(
                repository.listGroups(), repository.listSnippets(),
                repository.listVariableGroups(), repository.listVariables()));
    }

    @Override
    public CompletionStage<BuiltinSnippetImportResult> importBuiltinCommands(
            InterfaceLanguage language, BuiltinSnippetConflictPolicy conflictPolicy) {
        InterfaceLanguage selected = Objects.requireNonNull(language, "language").resolved();
        BuiltinSnippetConflictPolicy selectedPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        return supply(() -> {
            String resourceName = "/com/ynpe/fastremote/app/defaults/commands_"
                    + selected.persistedTag().replace('-', '_') + ".tsv";
            URL source = Objects.requireNonNull(SqliteSnippetWorkflow.class.getResource(resourceName),
                    "Missing built-in command catalog: " + resourceName);
            byte[] bytes;
            try (var input = source.openStream()) {
                bytes = input.readNBytes((int) MAX_IMPORT_BYTES + 1);
            }
            if (bytes.length > MAX_IMPORT_BYTES) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.88a6aa57148632ad"));
            SnippetLibraryTextCodec.BuiltinDecoded decoded = SnippetLibraryTextCodec.decodeBuiltin(
                    new String(bytes, StandardCharsets.UTF_8).lines().toList());
            List<BuiltinSnippetGroup> groups = new ArrayList<>();
            for (int index = 0; index < decoded.groups().size(); index++) {
                var group = decoded.groups().get(index);
                groups.add(new BuiltinSnippetGroup(group.builtinId(), group.name(), index * 10, group.color()));
            }
            List<BuiltinSnippet> commands = new ArrayList<>();
            Map<String, Integer> groupOrders = new HashMap<>();
            for (var command : decoded.commands()) {
                templateEngine.analyze(command.content());
                int order = groupOrders.merge(command.groupBuiltinId(), 0, (previous, ignored) -> previous + 10);
                commands.add(new BuiltinSnippet(command.builtinId(),
                        command.groupBuiltinId().isBlank() ? Optional.empty()
                                : Optional.of(command.groupBuiltinId()),
                        command.name(), command.description(), command.content(), order));
            }
            return repository.importBuiltinSnippets(
                    selected.persistedTag(), selectedPolicy, groups, commands);
        });
    }

    @Override
    public List<InterfaceLanguage> builtinCommandLanguages() {
        return InterfaceLanguage.publishedLanguages().stream()
                .filter(language -> SqliteSnippetWorkflow.class.getResource(
                        "/com/ynpe/fastremote/app/defaults/commands_"
                                + language.persistedTag().replace('-', '_') + ".tsv") != null)
                .toList();
    }

    @Override public CompletionStage<SnippetGroup> saveGroup(SnippetGroup group) {
        return supply(() -> { repository.saveGroup(group); return group; });
    }

    @Override public CompletionStage<Void> deleteGroup(SnippetGroupId id) {
        return supply(() -> { repository.deleteGroup(id); return null; });
    }

    @Override public CompletionStage<Snippet> saveSnippet(Snippet snippet) {
        return supply(() -> { repository.saveSnippet(snippet); return snippet; });
    }

    @Override public CompletionStage<LibraryImportPreview> inspectText(Path file) {
        Path source = source(file);
        return supply(() -> preview(prepare(read(source, true), ImportConflictPolicy.OVERWRITE)));
    }

    @Override public CompletionStage<LibraryImportResult> importText(
            Path file, ImportConflictPolicy conflictPolicy) {
        Path source = source(file);
        Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        return supply(() -> {
            PreparedImport prepared = prepare(read(source, true), conflictPolicy);
            save(prepared);
            return new LibraryImportResult(prepared.snippets().size(), prepared.variables().size());
        });
    }

    @Override public CompletionStage<Integer> exportText(Path file) {
        Path target = target(file);
        return supply(() -> {
            List<SnippetGroup> groups = sortedGroups(repository.listGroups());
            List<Snippet> snippets = sortedSnippets(groups, repository.listSnippets());
            if (snippets.isEmpty()) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.cf88dc0dbfaeaf7e"));
            Set<String> referencedNames = new LinkedHashSet<>();
            for (Snippet snippet : snippets) referencedNames.addAll(templateEngine.analyze(snippet.content()).variables());
            Map<String, SnippetVariable> variablesByName = new LinkedHashMap<>();
            repository.listVariables().forEach(variable -> variablesByName.put(variable.variableName(), variable));
            List<String> missing = referencedNames.stream().filter(name -> !variablesByName.containsKey(name)).toList();
            if (!missing.isEmpty()) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.25bf814cde5b0442") + String.join("、", missing));
            List<SnippetVariable> variables = referencedNames.stream().map(variablesByName::get).toList();
            Set<SnippetVariableGroupId> usedVariableGroups = variables.stream()
                    .flatMap(variable -> variable.groupId().stream()).collect(java.util.stream.Collectors.toSet());
            List<SnippetVariableGroup> variableGroups = repository.listVariableGroups().stream()
                    .filter(group -> usedVariableGroups.contains(group.id()))
                    .sorted(Comparator.comparingInt(SnippetVariableGroup::sortOrder)
                            .thenComparing(SnippetVariableGroup::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            Set<SnippetGroupId> usedCommandGroups = snippets.stream().flatMap(snippet -> snippet.groupId().stream())
                    .collect(java.util.stream.Collectors.toSet());
            List<SnippetGroup> exportedGroups = groups.stream()
                    .filter(group -> usedCommandGroups.contains(group.id())).toList();
            write(target, SnippetLibraryTextCodec.encodeCommands(
                    variableGroups, variables, exportedGroups, snippets));
            return snippets.size();
        });
    }

    @Override public CompletionStage<Void> deleteSnippet(SnippetId id) {
        return supply(() -> { repository.deleteSnippet(id); return null; });
    }

    @Override public CompletionStage<SnippetVariableGroup> saveVariableGroup(SnippetVariableGroup group) {
        return supply(() -> { repository.saveVariableGroup(group); return group; });
    }

    @Override public CompletionStage<Void> deleteVariableGroup(SnippetVariableGroupId id) {
        return supply(() -> { repository.deleteVariableGroup(id); return null; });
    }

    @Override public CompletionStage<SnippetVariable> saveVariable(SnippetVariable variable) {
        return supply(() -> { repository.saveVariable(variable); return variable; });
    }

    @Override public CompletionStage<LibraryImportPreview> inspectVariablesText(Path file) {
        Path source = source(file);
        return supply(() -> preview(prepare(read(source, false), ImportConflictPolicy.OVERWRITE)));
    }

    @Override public CompletionStage<LibraryImportResult> importVariablesText(
            Path file, ImportConflictPolicy conflictPolicy) {
        Path source = source(file);
        Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        return supply(() -> {
            PreparedImport prepared = prepare(read(source, false), conflictPolicy);
            save(prepared);
            return new LibraryImportResult(0, prepared.variables().size());
        });
    }

    @Override public CompletionStage<Integer> exportVariablesText(Path file) {
        Path target = target(file);
        return supply(() -> {
            List<SnippetVariableGroup> groups = repository.listVariableGroups().stream()
                    .sorted(Comparator.comparingInt(SnippetVariableGroup::sortOrder)
                            .thenComparing(SnippetVariableGroup::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            List<SnippetVariable> variables = sortedVariables(groups, repository.listVariables());
            if (variables.isEmpty()) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3ea279413ae38604"));
            write(target, SnippetLibraryTextCodec.encodeVariables(groups, variables));
            return variables.size();
        });
    }

    @Override public CompletionStage<Void> deleteVariable(SnippetVariableId id) {
        return supply(() -> { repository.deleteVariable(id); return null; });
    }

    private PreparedImport prepare(
            SnippetLibraryTextCodec.Decoded decoded, ImportConflictPolicy policy) throws Exception {
        List<SnippetGroup> existingGroups = repository.listGroups();
        List<Snippet> existingSnippets = repository.listSnippets();
        List<SnippetVariableGroup> existingVariableGroups = repository.listVariableGroups();
        List<SnippetVariable> existingVariables = repository.listVariables();
        GroupPreparation<SnippetGroupId, SnippetGroup> commandGroups = commandGroups(
                decoded.commandGroups(), existingGroups);
        GroupPreparation<SnippetVariableGroupId, SnippetVariableGroup> variableGroups = variableGroups(
                decoded.variableGroups(), existingVariableGroups);
        Instant now = Instant.now();

        Map<String, SnippetVariable> existingVariablesByName = new HashMap<>();
        existingVariables.forEach(variable -> existingVariablesByName.put(variable.variableName(), variable));
        Map<Optional<SnippetVariableGroupId>, Integer> lastVariableOrders = new HashMap<>();
        existingVariables.forEach(variable -> lastVariableOrders.merge(variable.groupId(), variable.sortOrder(), Math::max));
        int variableConflicts = 0;
        List<SnippetVariable> importedVariables = new ArrayList<>();
        for (SnippetLibraryTextCodec.VariableLine line : decoded.variables()) {
            Optional<SnippetVariableGroupId> groupId = groupId(line.groupName(), variableGroups.ids());
            SnippetVariable existing = existingVariablesByName.get(line.variableName());
            if (existing != null) variableConflicts++;
            if (existing != null && policy == ImportConflictPolicy.SKIP) continue;
            int sortOrder = existing == null
                    ? lastVariableOrders.merge(groupId, 0, (previous, ignored) -> previous + 10)
                    : existing.sortOrder();
            try {
                importedVariables.add(new SnippetVariable(
                        existing == null ? SnippetVariableId.create() : existing.id(), groupId,
                        line.name(), line.variableName(), line.type(), line.delimiter(), line.value(),
                        line.description(), sortOrder, existing == null ? now : existing.createdAt(), now));
            } catch (IllegalArgumentException invalid) {
                throw lineFailure(line.lineNumber(), invalid);
            }
        }

        Map<String, Snippet> existingCommands = new HashMap<>();
        Map<SnippetGroupId, String> existingGroupNames = new HashMap<>();
        existingGroups.forEach(group -> existingGroupNames.put(group.id(), group.name()));
        existingSnippets.forEach(snippet -> existingCommands.put(commandKey(
                snippet.groupId().map(existingGroupNames::get).orElse(""), snippet.name()), snippet));
        Map<Optional<SnippetGroupId>, Integer> lastCommandOrders = new HashMap<>();
        existingSnippets.forEach(snippet -> lastCommandOrders.merge(snippet.groupId(), snippet.sortOrder(), Math::max));
        int commandConflicts = 0;
        List<Snippet> importedCommands = new ArrayList<>();
        for (SnippetLibraryTextCodec.CommandLine line : decoded.commands()) {
            Optional<SnippetGroupId> groupId = groupId(line.groupName(), commandGroups.ids());
            Snippet existing = existingCommands.get(commandKey(line.groupName(), line.name()));
            if (existing != null) commandConflicts++;
            if (existing != null && policy == ImportConflictPolicy.SKIP) continue;
            int sortOrder = existing == null
                    ? lastCommandOrders.merge(groupId, 0, (previous, ignored) -> previous + 10)
                    : existing.sortOrder();
            try {
                templateEngine.analyze(line.content());
                importedCommands.add(new Snippet(
                        existing == null ? SnippetId.create() : existing.id(), groupId, line.name(),
                        line.description(), line.content(), sortOrder,
                        existing == null ? now : existing.createdAt(), now));
            } catch (IllegalArgumentException invalid) {
                throw lineFailure(line.lineNumber(), invalid);
            }
        }
        return new PreparedImport(
                commandGroups.created(), importedCommands, variableGroups.created(), importedVariables,
                decoded.commands().size(), decoded.variables().size(), commandConflicts, variableConflicts);
    }

    private GroupPreparation<SnippetGroupId, SnippetGroup> commandGroups(
            List<SnippetLibraryTextCodec.GroupLine> lines, List<SnippetGroup> existing) {
        Map<String, SnippetGroupId> ids = new LinkedHashMap<>();
        existing.forEach(group -> ids.put(normalize(group.name()), group.id()));
        int order = existing.stream().mapToInt(SnippetGroup::sortOrder).max().orElse(-10) + 10;
        List<SnippetGroup> created = new ArrayList<>();
        for (SnippetLibraryTextCodec.GroupLine line : lines) {
            if (ids.containsKey(normalize(line.name()))) continue;
            SnippetGroup group = new SnippetGroup(SnippetGroupId.create(), line.name(), order, line.color());
            order += 10;
            ids.put(normalize(group.name()), group.id());
            created.add(group);
        }
        return new GroupPreparation<>(Map.copyOf(ids), List.copyOf(created));
    }

    private GroupPreparation<SnippetVariableGroupId, SnippetVariableGroup> variableGroups(
            List<SnippetLibraryTextCodec.GroupLine> lines, List<SnippetVariableGroup> existing) {
        Map<String, SnippetVariableGroupId> ids = new LinkedHashMap<>();
        existing.forEach(group -> ids.put(normalize(group.name()), group.id()));
        int order = existing.stream().mapToInt(SnippetVariableGroup::sortOrder).max().orElse(-10) + 10;
        List<SnippetVariableGroup> created = new ArrayList<>();
        for (SnippetLibraryTextCodec.GroupLine line : lines) {
            if (ids.containsKey(normalize(line.name()))) continue;
            SnippetVariableGroup group = new SnippetVariableGroup(
                    SnippetVariableGroupId.create(), line.name(), order, line.color());
            order += 10;
            ids.put(normalize(group.name()), group.id());
            created.add(group);
        }
        return new GroupPreparation<>(Map.copyOf(ids), List.copyOf(created));
    }

    void validateArchive(List<String> lines, boolean commands) throws Exception {
        prepare(SnippetLibraryTextCodec.decode(lines, commands, true), ImportConflictPolicy.OVERWRITE);
    }

    void importArchive(List<String> lines, boolean commands) throws Exception {
        save(prepare(SnippetLibraryTextCodec.decode(lines, commands, true), ImportConflictPolicy.OVERWRITE));
    }

    private void save(PreparedImport prepared) throws Exception {
        repository.saveLibraryImport(
                prepared.commandGroups(), prepared.snippets(), prepared.variableGroups(), prepared.variables());
    }

    private static LibraryImportPreview preview(PreparedImport prepared) {
        return new LibraryImportPreview(
                prepared.sourceCommandCount(), prepared.sourceVariableCount(),
                prepared.commandConflicts(), prepared.variableConflicts());
    }

    private static <I> Optional<I> groupId(String name, Map<String, I> ids) {
        if (name.isBlank()) return Optional.empty();
        I id = ids.get(normalize(name));
        if (id == null) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.23ba9dd7764f066a") + name);
        return Optional.of(id);
    }

    private static String commandKey(String groupName, String commandName) {
        return normalize(groupName) + '\0' + normalize(commandName);
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static IllegalArgumentException lineFailure(int line, IllegalArgumentException invalid) {
        return new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text(
                "error.line", line, com.ynpe.fastremote.i18n.Texts.translateExternalText(invalid.getMessage())), invalid);
    }

    private SnippetLibraryTextCodec.Decoded read(Path source, boolean commandsRequired) throws Exception {
        long size = Files.size(source);
        if (size > MAX_IMPORT_BYTES) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7c4a80c66dafe597"));
        return SnippetLibraryTextCodec.decode(Files.readAllLines(source, StandardCharsets.UTF_8), commandsRequired);
    }

    private static List<SnippetGroup> sortedGroups(List<SnippetGroup> groups) {
        return groups.stream().sorted(Comparator.comparingInt(SnippetGroup::sortOrder)
                .thenComparing(SnippetGroup::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static List<Snippet> sortedSnippets(List<SnippetGroup> groups, List<Snippet> snippets) {
        Map<SnippetGroupId, Integer> groupOrder = new HashMap<>();
        for (int index = 0; index < groups.size(); index++) groupOrder.put(groups.get(index).id(), index);
        return snippets.stream().sorted(Comparator.comparingInt((Snippet snippet) -> snippet.groupId()
                        .map(id -> groupOrder.getOrDefault(id, Integer.MAX_VALUE)).orElse(-1))
                .thenComparingInt(Snippet::sortOrder)
                .thenComparing(Snippet::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static List<SnippetVariable> sortedVariables(
            List<SnippetVariableGroup> groups, List<SnippetVariable> variables) {
        Map<SnippetVariableGroupId, Integer> groupOrder = new HashMap<>();
        for (int index = 0; index < groups.size(); index++) groupOrder.put(groups.get(index).id(), index);
        return variables.stream().sorted(Comparator.comparingInt((SnippetVariable variable) -> variable.groupId()
                        .map(id -> groupOrder.getOrDefault(id, Integer.MAX_VALUE)).orElse(-1))
                .thenComparingInt(SnippetVariable::sortOrder)
                .thenComparing(SnippetVariable::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static Path source(Path file) {
        return Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    private static Path target(Path file) {
        return Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    private static void write(Path target, List<String> lines) throws Exception {
        Path parent = target.getParent();
        if (parent == null) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ce79d53c9ab8822a"));
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + target.getFileName(), ".tmp");
        try {
            Files.writeString(temporary, String.join("\n", lines) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public CompletionStage<Void> stopped() { return executor.stopped(); }

    private <T> CompletionStage<T> supply(BoundedWorkflowExecutor.CheckedSupplier<T> operation) {
        return executor.supply(operation);
    }

    @Override public void close() {
        executor.close();
    }

    private record GroupPreparation<I, G>(Map<String, I> ids, List<G> created) {}

    private record PreparedImport(
            List<SnippetGroup> commandGroups,
            List<Snippet> snippets,
            List<SnippetVariableGroup> variableGroups,
            List<SnippetVariable> variables,
            int sourceCommandCount,
            int sourceVariableCount,
            int commandConflicts,
            int variableConflicts) {}

}
