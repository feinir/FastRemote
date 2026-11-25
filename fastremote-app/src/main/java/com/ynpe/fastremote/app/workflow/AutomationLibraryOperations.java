// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.automation.AutomationCapability;
import com.ynpe.fastremote.domain.automation.AutomationHost;
import com.ynpe.fastremote.domain.automation.AutomationHostGroup;
import com.ynpe.fastremote.domain.automation.AutomationCommand;
import com.ynpe.fastremote.domain.automation.AutomationVariable;
import com.ynpe.fastremote.domain.automation.AutomationSource;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostCatalogRepository;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.snippet.Snippet;
import com.ynpe.fastremote.domain.snippet.SnippetGroup;
import com.ynpe.fastremote.domain.snippet.SnippetGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetId;
import com.ynpe.fastremote.domain.snippet.SnippetRepository;
import com.ynpe.fastremote.domain.snippet.SnippetVariable;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroup;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetVariableId;
import com.ynpe.fastremote.domain.snippet.SnippetVariableType;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

final class AutomationLibraryOperations {
    private final AutomationExecution execution;
    private final HostCatalogRepository hosts;
    private final SnippetRepository snippets;
    private final Runnable libraryChanged;

    AutomationLibraryOperations(AutomationExecution execution, HostCatalogRepository hosts,
            SnippetRepository snippets, Runnable libraryChanged) {
        this.execution = execution;
        this.hosts = hosts;
        this.snippets = snippets;
        this.libraryChanged = libraryChanged;
    }

    public CompletionStage<List<AutomationHost>> listHosts(AutomationSource source) {
        return execution.submit(() -> {
            var groups = AutomationHostGroup.describe(hosts.listGroups());
            List<SavedHost> values = hosts.listHosts();
            return execution.audited(source, AutomationCapability.READ_HOST_METADATA, null,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.fec16d11291b3284"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.946449a3eae084a0") + values.size(),
                    () -> values.stream().map(host -> summary(host, groups)).toList());
        });
    }

    public CompletionStage<List<AutomationHost>> listHosts(
            AutomationSource source, Optional<String> query, int offset, int limit) {
        Objects.requireNonNull(query, "query");
        if (offset < 0) throw new IllegalArgumentException("Host offset must not be negative");
        if (limit < 1) throw new IllegalArgumentException("Host limit must be positive");
        String expected = query.map(String::strip).orElse("").toLowerCase(Locale.ROOT);
        return execution.submit(() -> {
            var groups = AutomationHostGroup.describe(hosts.listGroups());
            List<AutomationHost> values = hosts.listHosts().stream()
                    .map(host -> summary(host, groups))
                    .filter(host -> expected.isEmpty()
                            || host.id().toString().toLowerCase(Locale.ROOT).contains(expected)
                            || host.name().toLowerCase(Locale.ROOT).contains(expected)
                            || host.username().toLowerCase(Locale.ROOT).contains(expected)
                            || host.host().toLowerCase(Locale.ROOT).contains(expected)
                            || host.endpoint().toLowerCase(Locale.ROOT).contains(expected)
                            || host.groupPath().toLowerCase(Locale.ROOT).contains(expected))
                    .skip(offset)
                    .limit(limit)
                    .toList();
            return execution.audited(source, AutomationCapability.READ_HOST_METADATA, null,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.fec16d11291b3284"),
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.946449a3eae084a0") + values.size(),
                    () -> values);
        });
    }

    public CompletionStage<List<AutomationHostGroup>> listHostGroups(AutomationSource source) {
        return execution.submit(() -> execution.audited(source, AutomationCapability.READ_HOST_METADATA, null,
                "Read host groups", "Hosts library", () -> AutomationHostGroup.describe(hosts.listGroups())));
    }

    public CompletionStage<List<AutomationCommand>> listCommandEntries(AutomationSource source) {
        return execution.submit(() -> execution.audited(source, AutomationCapability.READ_LIBRARY, null,
                "Read commands", "Command library", () -> {
                    var groups = snippets.listGroups();
                    return snippets.listSnippets().stream().map(value -> AutomationCommand.describe(value, groups)).toList();
                }));
    }

    public CompletionStage<List<AutomationVariable>> listVariableEntries(AutomationSource source) {
        return execution.submit(() -> execution.audited(source, AutomationCapability.READ_LIBRARY, null,
                "Read variables", "Variable library", () -> {
                    var groups = snippets.listVariableGroups();
                    return snippets.listVariables().stream().map(value -> AutomationVariable.describe(value, groups)).toList();
                }));
    }

    public CompletionStage<List<Snippet>> listCommands(AutomationSource source) {
        return execution.submit(() -> execution.audited(source, AutomationCapability.READ_LIBRARY, null,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.753bb41b7c22adc1"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.adc650c64a290062"), snippets::listSnippets));
    }

    public CompletionStage<List<SnippetGroup>> listCommandGroups(AutomationSource source) {
        return execution.submit(() -> execution.audited(source, AutomationCapability.READ_LIBRARY, null,
                "Read command groups", "Command library", snippets::listGroups));
    }

    public CompletionStage<SnippetGroup> addCommandGroup(
            AutomationSource source, String name, GroupColor color) {
        return execution.submit(() -> execution.audited(source, AutomationCapability.MANAGE_LIBRARY, null,
                "Add command group", Objects.requireNonNull(name, "name").strip(), () -> {
                    int order = snippets.listGroups().stream().mapToInt(SnippetGroup::sortOrder).max().orElse(-10) + 10;
                    SnippetGroup group = new SnippetGroup(SnippetGroupId.create(), name, order,
                            Objects.requireNonNull(color, "color"));
                    snippets.saveGroup(group);
                    libraryChanged.run();
                    return group;
                }));
    }

    public CompletionStage<Snippet> addCommand(
            AutomationSource source, String name, String command, String description) {
        return addCommand(source, name, command, description, Optional.empty());
    }

    public CompletionStage<Snippet> addCommand(
            AutomationSource source, String name, String command, String description,
            Optional<SnippetGroupId> groupId) {
        return addCommandResult(source, name, command, description, groupId, value -> value);
    }

    public CompletionStage<AutomationCommand> addCommandEntry(AutomationSource source, String name,
            String command, String description, Optional<SnippetGroupId> groupId) {
        return addCommandResult(source, name, command, description, groupId,
                value -> AutomationCommand.describe(value, snippets.listGroups()));
    }

    private <T> CompletionStage<T> addCommandResult(AutomationSource source, String name, String command,
            String description, Optional<SnippetGroupId> groupId, LibraryResult<Snippet, T> result) {
        return execution.submit(() -> execution.audited(source, AutomationCapability.MANAGE_LIBRARY, null,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.cc4361edeeb19ff3"), Objects.requireNonNull(name, "name").strip(), () -> {
                    Optional<SnippetGroupId> selected = Objects.requireNonNull(groupId, "groupId");
                    if (selected.isPresent() && snippets.listGroups().stream().noneMatch(group -> group.id().equals(selected.get()))) {
                        throw new IllegalArgumentException("Command group does not exist");
                    }
                    Instant now = Instant.now();
                    int order = snippets.listSnippets().stream().filter(item -> item.groupId().equals(selected))
                            .mapToInt(Snippet::sortOrder).max().orElse(-10) + 10;
                    Snippet value = new Snippet(SnippetId.create(), selected, name, description,
                            command, order, now, now);
                    T response = result.apply(value);
                    snippets.saveSnippet(value);
                    libraryChanged.run();
                    return response;
                }));
    }

    public CompletionStage<Boolean> deleteCommand(AutomationSource source, String commandId) {
        SnippetId id = SnippetId.parse(Objects.requireNonNull(commandId, "commandId"));
        return execution.submit(() -> execution.audited(source, AutomationCapability.MANAGE_LIBRARY, null,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.c1ef0f57300a7785"), id.toString(), () -> {
                    snippets.deleteSnippet(id);
                    libraryChanged.run();
                    return true;
                }));
    }

    public CompletionStage<List<SnippetVariable>> listVariables(AutomationSource source) {
        return execution.submit(() -> execution.audited(source, AutomationCapability.READ_LIBRARY, null,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.297f7dc8b6b7a916"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.6fad359e2c4a6e60"), snippets::listVariables));
    }

    public CompletionStage<List<SnippetVariableGroup>> listVariableGroups(AutomationSource source) {
        return execution.submit(() -> execution.audited(source, AutomationCapability.READ_LIBRARY, null,
                "Read variable groups", "Variable library", snippets::listVariableGroups));
    }

    public CompletionStage<SnippetVariableGroup> addVariableGroup(
            AutomationSource source, String name, GroupColor color) {
        return execution.submit(() -> execution.audited(source, AutomationCapability.MANAGE_LIBRARY, null,
                "Add variable group", Objects.requireNonNull(name, "name").strip(), () -> {
                    int order = snippets.listVariableGroups().stream()
                            .mapToInt(SnippetVariableGroup::sortOrder).max().orElse(-10) + 10;
                    SnippetVariableGroup group = new SnippetVariableGroup(SnippetVariableGroupId.create(), name,
                            order, Objects.requireNonNull(color, "color"));
                    snippets.saveVariableGroup(group);
                    libraryChanged.run();
                    return group;
                }));
    }

    public CompletionStage<SnippetVariable> addVariable(AutomationSource source, String name,
            String variableName, SnippetVariableType type, String delimiter, String value, String description) {
        return addVariable(source, name, variableName, type, delimiter, value, description, Optional.empty());
    }

    public CompletionStage<SnippetVariable> addVariable(AutomationSource source, String name,
            String variableName, SnippetVariableType type, String delimiter, String value, String description,
            Optional<SnippetVariableGroupId> groupId) {
        return addVariableResult(source, name, variableName, type, delimiter, value, description, groupId, entry -> entry);
    }

    public CompletionStage<AutomationVariable> addVariableEntry(AutomationSource source, String name,
            String variableName, SnippetVariableType type, String delimiter, String value, String description,
            Optional<SnippetVariableGroupId> groupId) {
        return addVariableResult(source, name, variableName, type, delimiter, value, description, groupId,
                entry -> AutomationVariable.describe(entry, snippets.listVariableGroups()));
    }

    private <T> CompletionStage<T> addVariableResult(AutomationSource source, String name, String variableName,
            SnippetVariableType type, String delimiter, String value, String description,
            Optional<SnippetVariableGroupId> groupId, LibraryResult<SnippetVariable, T> result) {
        return execution.submit(() -> execution.audited(source, AutomationCapability.MANAGE_LIBRARY, null,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.99410be9e8cff3ab"), Objects.requireNonNull(name, "name").strip(), () -> {
                    Optional<SnippetVariableGroupId> selected = Objects.requireNonNull(groupId, "groupId");
                    if (selected.isPresent() && snippets.listVariableGroups().stream()
                            .noneMatch(group -> group.id().equals(selected.get()))) {
                        throw new IllegalArgumentException("Variable group does not exist");
                    }
                    Instant now = Instant.now();
                    int order = snippets.listVariables().stream().filter(item -> item.groupId().equals(selected))
                            .mapToInt(SnippetVariable::sortOrder).max().orElse(-10) + 10;
                    SnippetVariable variable = new SnippetVariable(SnippetVariableId.create(), selected, name,
                            variableName, type, delimiter, value, description, order, now, now);
                    T response = result.apply(variable);
                    snippets.saveVariable(variable);
                    libraryChanged.run();
                    return response;
                }));
    }

    public CompletionStage<Boolean> deleteVariable(AutomationSource source, String variableId) {
        SnippetVariableId id = SnippetVariableId.parse(Objects.requireNonNull(variableId, "variableId"));
        return execution.submit(() -> execution.audited(source, AutomationCapability.MANAGE_LIBRARY, null,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.9a043a9276757dc1"), id.toString(), () -> {
                    snippets.deleteVariable(id);
                    libraryChanged.run();
                    return true;
                }));
    }

    static AutomationHost summary(SavedHost host, List<AutomationHostGroup> groups) {
        var group = groups.stream().filter(item -> host.groupId().filter(item.id()::equals).isPresent()).findFirst();
        return new AutomationHost(host.id(), host.name(), host.profile().username().value(),
                host.profile().host().value(), host.profile().port().value(), host.groupId(),
                group.map(AutomationHostGroup::name).orElse(""), group.map(AutomationHostGroup::path).orElse(""),
                group.map(AutomationHostGroup::groupIds).orElse(List.of()));
    }

    @FunctionalInterface private interface LibraryResult<V, T> { T apply(V value) throws Exception; }
}
