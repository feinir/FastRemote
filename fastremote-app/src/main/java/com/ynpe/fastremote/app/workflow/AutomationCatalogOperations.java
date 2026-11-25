// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.app.workflow.SqliteHostCatalogWorkflow.CredentialInput;
import com.ynpe.fastremote.domain.automation.AutomationCatalogTool;
import com.ynpe.fastremote.domain.automation.AutomationCommand;
import com.ynpe.fastremote.domain.automation.AutomationHostGroup;
import com.ynpe.fastremote.domain.automation.AutomationVariable;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.HostCatalogRepository;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.catalog.HostIcon;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.Username;
import com.ynpe.fastremote.domain.credential.CredentialGroup;
import com.ynpe.fastremote.domain.credential.CredentialGroupId;
import com.ynpe.fastremote.domain.credential.CredentialScope;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import com.ynpe.fastremote.domain.credential.GlobalCredentialRepository;
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
import com.ynpe.fastremote.domain.vault.SecretId;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;

final class AutomationCatalogOperations {
    private final HostCatalogRepository hosts;
    private final GlobalCredentialRepository credentials;
    private final SnippetRepository snippets;
    private final SqliteHostCatalogWorkflow workflow;

    AutomationCatalogOperations(HostCatalogRepository hosts, GlobalCredentialRepository credentials,
            SnippetRepository snippets, SqliteHostCatalogWorkflow workflow) {
        this.hosts = hosts;
        this.credentials = credentials;
        this.snippets = snippets;
        this.workflow = workflow;
    }

    java.util.concurrent.CompletionStage<Object> execute(AutomationCatalogTool tool, Map<String, Object> arguments) {
        return workflow.catalogTask(() -> {
            Args args = new Args(arguments);
            return switch (tool) {
                case GET_HOST -> describeHost(requireHost(args));
                case ADD_HOST -> saveHost(args, Optional.empty());
                case UPDATE_HOST -> saveHost(args, Optional.of(requireHost(args)));
                case DELETE_HOST -> {
                    SavedHost host = requireHost(args);
                    yield Map.of("deleted", true, "name", host.name(), "changedToDirect", workflow.deleteStoredHost(host));
                }
                case ADD_HOST_GROUP -> saveHostGroup(args, false);
                case UPDATE_HOST_GROUP -> saveHostGroup(args, true);
                case DELETE_HOST_GROUP -> {
                    var id = GroupId.parse(args.text("group_id").strip());
                    requireEntry(hosts.listGroups(), HostGroup::id, id, "Host group");
                    hosts.deleteGroup(id);
                    yield deleted();
                }
                case LIST_CREDENTIALS -> credentials.list().stream()
                        .filter(entry -> entry.scope() == CredentialScope.REUSABLE).map(this::describeCredential).toList();
                case LIST_CREDENTIAL_GROUPS -> credentials.listGroups();
                case ADD_CREDENTIAL_GROUP -> saveCredentialGroup(args, false);
                case UPDATE_CREDENTIAL_GROUP -> saveCredentialGroup(args, true);
                case DELETE_CREDENTIAL_GROUP -> {
                    var id = CredentialGroupId.parse(args.text("group_id").strip());
                    requireEntry(credentials.listGroups(), CredentialGroup::id, id, "Credential group");
                    credentials.deleteGroup(id);
                    yield deleted();
                }
                case ADD_CREDENTIAL -> saveCredential(args, Optional.empty());
                case UPDATE_CREDENTIAL -> saveCredential(args, Optional.of(reusable(SecretId.parse(args.text("credential_id").strip()))));
                case DELETE_CREDENTIAL -> {
                    workflow.deleteStoredCredential(reusable(SecretId.parse(args.text("credential_id").strip())));
                    yield deleted();
                }
                case UPDATE_COMMAND -> updateCommand(args);
                case UPDATE_VARIABLE -> updateVariable(args);
                case UPDATE_COMMAND_GROUP -> updateCommandGroup(args);
                case UPDATE_VARIABLE_GROUP -> updateVariableGroup(args);
                case DELETE_COMMAND_GROUP -> {
                    var id = SnippetGroupId.parse(args.text("group_id").strip());
                    requireEntry(snippets.listGroups(), SnippetGroup::id, id, "Command group");
                    snippets.deleteGroup(id);
                    yield deleted();
                }
                case DELETE_VARIABLE_GROUP -> {
                    var id = SnippetVariableGroupId.parse(args.text("group_id").strip());
                    requireEntry(snippets.listVariableGroups(), SnippetVariableGroup::id, id, "Variable group");
                    snippets.deleteVariableGroup(id);
                    yield deleted();
                }
            };
        });
    }

    private SavedHost requireHost(Args args) throws IOException {
        return hosts.findHost(HostId.parse(args.text("host_id").strip()))
                .orElseThrow(() -> missing("Saved host"));
    }

    private Object describeHost(SavedHost host) {
        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("id", host.id().toString());
        metadata.put("name", host.name());
        metadata.put("host", host.profile().host().value());
        metadata.put("port", host.profile().port().value());
        metadata.put("username", host.profile().username().value());
        metadata.put("authenticationType", host.profile().authenticationType());
        metadata.put("groupId", idText(host.groupId()));
        metadata.put("jumpHostId", idText(host.jumpHostId()));
        metadata.put("credentialId", idText(host.credentialSecretId()));
        metadata.put("favorite", host.favorite());
        metadata.put("icon", host.icon());
        return metadata;
    }

    private Object saveHost(Args args, Optional<SavedHost> current) throws Exception {
        var oldProfile = current.map(SavedHost::profile);
        var group = args.reference("group_id", current.flatMap(SavedHost::groupId), GroupId::parse);
        var jump = args.reference("jump_host_id", current.flatMap(SavedHost::jumpHostId), HostId::parse);
        var credential = args.reference("credential_id", current.flatMap(SavedHost::credentialSecretId), SecretId::parse);
        if (group.isPresent()) requireEntry(hosts.listGroups(), HostGroup::id, group.orElseThrow(), "Host group");
        if (jump.isPresent() && hosts.findHost(jump.orElseThrow()).isEmpty()) throw missing("Jump host");
        if (args.has("credential_id") && AutomationCatalogTool.SECRET_FIELDS.stream().anyMatch(args::has)) {
            throw new IllegalArgumentException("Choose either credential_id or inline authentication");
        }
        try (var inline = args.inlineCredential()) {
            var auth = oldProfile.map(ConnectionProfile::authenticationType).orElse(AuthenticationType.PASSWORD);
            var keyPath = oldProfile.flatMap(ConnectionProfile::privateKeyPath);
            if (inline != null) {
                auth = inline.authenticationType();
                keyPath = Optional.empty();
            } else if (args.has("credential_id")) {
                auth = credential.isPresent() ? reusable(credential.orElseThrow()).authenticationType() : auth;
                keyPath = Optional.empty();
            }
            var profile = new ConnectionProfile(
                    new Host(args.text("host", oldProfile.map(p -> p.host().value()).orElse(""))),
                    new Port(args.integer("port", oldProfile.map(p -> p.port().value()).orElse(22))),
                    new Username(args.text("username", oldProfile.map(p -> p.username().value()).orElse(""))),
                    auth, keyPath, oldProfile.map(ConnectionProfile::legacyAlgorithmsEnabled).orElse(false));
            int order = current.filter(host -> host.groupId().equals(group)).map(SavedHost::sortOrder).orElse(-1);
            if (order < 0) {
                order = nextOrder(hosts.listHosts().stream().filter(host -> host.groupId().equals(group)).toList(),
                        SavedHost::sortOrder);
            }
            Instant now = Instant.now();
            var saved = new SavedHost(current.map(SavedHost::id).orElseGet(HostId::create), group,
                    args.text("name", current.map(SavedHost::name).orElse("")), profile, jump, credential,
                    args.bool("favorite", current.map(SavedHost::favorite).orElse(false)), order,
                    current.map(SavedHost::createdAt).orElse(now), now,
                    args.choice("icon", current.map(SavedHost::icon).orElse(HostIcon.SERVER)));
            return describeHost(workflow.saveAutomationHost(saved, inline));
        }
    }

    private Object saveHostGroup(Args args, boolean edit) throws IOException {
        var groups = hosts.listGroups();
        var current = edit ? Optional.of(requireEntry(groups, HostGroup::id, GroupId.parse(args.text("group_id").strip()),
                "Host group")) : Optional.<HostGroup>empty();
        var parent = args.reference("parent_id", current.flatMap(HostGroup::parentId), GroupId::parse);
        if (parent.isPresent()) requireEntry(groups, HostGroup::id, parent.orElseThrow(), "Parent group");
        int order = current.filter(group -> group.parentId().equals(parent)).map(HostGroup::sortOrder)
                .orElseGet(() -> nextOrder(groups.stream().filter(group -> group.parentId().equals(parent)).toList(),
                        HostGroup::sortOrder));
        var group = new HostGroup(current.map(HostGroup::id).orElseGet(GroupId::create), parent,
                args.text("name", current.map(HostGroup::name).orElse("")), order,
                args.choice("color", current.map(HostGroup::color).orElse(GroupColor.BLUE)));
        hosts.saveGroup(group);
        return requireEntry(AutomationHostGroup.describe(hosts.listGroups()), AutomationHostGroup::id, group.id(), "Host group");
    }

    private GlobalCredential reusable(SecretId id) throws IOException {
        return credentials.find(id).filter(entry -> entry.scope() == CredentialScope.REUSABLE)
                .orElseThrow(() -> missing("Reusable credential"));
    }

    private Map<String, Object> describeCredential(GlobalCredential value) {
        return Map.of("id", value.id().toString(), "label", value.label(), "username", value.username(),
                "authenticationType", value.authenticationType(), "groupId", idText(value.groupId()));
    }

    private Object saveCredentialGroup(Args args, boolean edit) throws IOException {
        var groups = credentials.listGroups();
        var current = edit ? requireEntry(groups, CredentialGroup::id,
                CredentialGroupId.parse(args.text("group_id").strip()), "Credential group") : null;
        var group = current == null
                ? new CredentialGroup(CredentialGroupId.random(), args.text("name"),
                        nextOrder(groups, CredentialGroup::sortOrder), args.choice("color", GroupColor.BLUE))
                : new CredentialGroup(current.id(), args.text("name", current.name()), current.sortOrder(),
                        args.choice("color", current.color()));
        credentials.saveGroup(group);
        return group;
    }

    private Object saveCredential(Args args, Optional<GlobalCredential> current) throws Exception {
        var group = args.reference("group_id", current.flatMap(GlobalCredential::groupId), CredentialGroupId::parse);
        if (group.isPresent()) {
            requireEntry(credentials.listGroups(), CredentialGroup::id, group.orElseThrow(), "Credential group");
        }
        var label = args.text("label", current.map(GlobalCredential::label).orElse(""));
        var username = args.text("username", current.map(GlobalCredential::username).orElse(""));
        new Username(username);
        try (var inline = args.inlineCredential()) {
            if (inline == null && current.isEmpty()) {
                throw new IllegalArgumentException("Provide password or private_key");
            }
            return describeCredential(workflow.saveAutomationCredential(current, label, username, group, inline));
        }
    }

    private Object updateCommand(Args args) throws IOException {
        var entries = snippets.listSnippets();
        var groups = snippets.listGroups();
        var current = requireEntry(entries, Snippet::id, SnippetId.parse(args.text("command_id").strip()), "Command");
        var group = args.reference("group_id", current.groupId(), SnippetGroupId::parse);
        if (group.isPresent()) requireEntry(groups, SnippetGroup::id, group.orElseThrow(), "Command group");
        int order = group.equals(current.groupId()) ? current.sortOrder() : nextOrder(
                entries.stream().filter(entry -> entry.groupId().equals(group)).toList(), Snippet::sortOrder);
        var changed = new Snippet(current.id(), group, args.text("name", current.name()),
                args.text("description", current.description()), args.text("command", current.content()),
                order, current.createdAt(), Instant.now());
        snippets.saveSnippet(changed);
        return AutomationCommand.describe(changed, groups);
    }

    private Object updateVariable(Args args) throws IOException {
        var entries = snippets.listVariables();
        var groups = snippets.listVariableGroups();
        var current = requireEntry(entries, SnippetVariable::id, SnippetVariableId.parse(args.text("variable_id").strip()), "Variable");
        var group = args.reference("group_id", current.groupId(), SnippetVariableGroupId::parse);
        if (group.isPresent()) requireEntry(groups, SnippetVariableGroup::id, group.orElseThrow(), "Variable group");
        int order = group.equals(current.groupId()) ? current.sortOrder() : nextOrder(
                entries.stream().filter(entry -> entry.groupId().equals(group)).toList(), SnippetVariable::sortOrder);
        var changed = new SnippetVariable(current.id(), group, args.text("name", current.name()),
                args.text("variable_name", current.variableName()), args.choice("type", current.type()),
                args.text("delimiter", current.delimiter()), args.text("value", current.value()),
                args.text("description", current.description()), order, current.createdAt(), Instant.now());
        snippets.saveVariable(changed);
        return AutomationVariable.describe(changed, groups);
    }

    private Object updateCommandGroup(Args args) throws IOException {
        var current = requireEntry(snippets.listGroups(), SnippetGroup::id,
                SnippetGroupId.parse(args.text("group_id").strip()), "Command group");
        var changed = new SnippetGroup(current.id(), args.text("name", current.name()), current.sortOrder(),
                args.choice("color", current.color()));
        snippets.saveGroup(changed);
        return changed;
    }

    private Object updateVariableGroup(Args args) throws IOException {
        var current = requireEntry(snippets.listVariableGroups(), SnippetVariableGroup::id,
                SnippetVariableGroupId.parse(args.text("group_id").strip()), "Variable group");
        var changed = new SnippetVariableGroup(current.id(), args.text("name", current.name()), current.sortOrder(),
                args.choice("color", current.color()));
        snippets.saveVariableGroup(changed);
        return changed;
    }

    private static Map<String, Boolean> deleted() {
        return Map.of("deleted", true);
    }

    private static String idText(Optional<?> id) {
        return id.map(Object::toString).orElse("");
    }

    private static IllegalArgumentException missing(String entity) {
        return new IllegalArgumentException(entity + " does not exist");
    }

    private static <T, I> T requireEntry(List<T> entries, Function<T, I> idOf, I id, String entity) {
        return entries.stream().filter(entry -> idOf.apply(entry).equals(id)).findFirst()
                .orElseThrow(() -> missing(entity));
    }

    private static <T> int nextOrder(List<T> siblings, ToIntFunction<T> order) {
        return Math.addExact(siblings.stream().mapToInt(order).max().orElse(-10), 10);
    }

    private record Args(Map<String, Object> values) {
        @Override public String toString() { return "Catalog arguments"; }

        boolean has(String key) { return values.containsKey(key); }
        String text(String key) { return text(key, ""); }
        String text(String key, String fallback) { return (String) values.getOrDefault(key, fallback); }
        int integer(String key, int fallback) { return (Integer) values.getOrDefault(key, fallback); }
        boolean bool(String key, boolean fallback) { return (Boolean) values.getOrDefault(key, fallback); }

        CredentialInput inlineCredential() throws IOException {
            boolean password = has("password");
            boolean key = has("private_key");
            if (password && key) throw new IllegalArgumentException("Supply only one of password or private_key");
            if (!key && has("passphrase")) throw new IllegalArgumentException("passphrase requires private_key");
            if (!password && !key) {
                if (has("type")) throw new IllegalArgumentException("type requires password or private_key");
                return null;
            }
            var type = key ? AuthenticationType.PRIVATE_KEY : AuthenticationType.PASSWORD;
            if (choice("type", type) != type) {
                throw new IllegalArgumentException("type must match the supplied authentication field");
            }
            return CredentialInput.inline(type, text(key ? "passphrase" : "password"),
                    key ? Optional.of(text("private_key")) : Optional.empty());
        }

        <E extends Enum<E>> E choice(String key, E fallback) {
            try {
                return has(key) ? Enum.valueOf(fallback.getDeclaringClass(), text(key).strip().toUpperCase(Locale.ROOT))
                        : fallback;
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Invalid value for " + key);
            }
        }

        <I> Optional<I> reference(String key, Optional<I> fallback, Function<String, I> parser) {
            if (!has(key)) return fallback;
            return Optional.of(text(key).strip()).filter(value -> !value.isEmpty()).map(value -> {
                try {
                    if (!java.util.UUID.fromString(value).toString().equalsIgnoreCase(value)) {
                        throw new IllegalArgumentException();
                    }
                    return parser.apply(value);
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalArgumentException(key + " must be a UUID");
                }
            });
        }
    }
}
