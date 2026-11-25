// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.storage.*;
import com.ynpe.fastremote.domain.snippet.SnippetRepository;
import com.ynpe.fastremote.ui.ConfigurationSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Unified archive orchestration; no raw vault, remembered unlock key, or license data is exported. */
final class ConfigurationArchive {
    private static final List<String> SETTINGS = List.of("workspace", "terminal", "sftp", "connection", "diagnosticLog", "softwareUpdate");
    private final com.ynpe.fastremote.domain.vault.ManagedSecretVault vault;
    private final FastRemoteDatabase database;
    private final YamlGlobalConfigStore store;
    private final HostCatalogArchive catalog;
    private final SnippetRepository snippets;
    private final SqliteSnippetWorkflow library;

    ConfigurationArchive(FastRemoteDatabase database, YamlGlobalConfigStore store,
            HostCatalogArchive catalog, SnippetRepository snippets, SqliteSnippetWorkflow library,
            com.ynpe.fastremote.domain.vault.ManagedSecretVault vault) {
        this.vault = vault;
        this.database = database; this.store = store; this.catalog = catalog;
        this.snippets = snippets; this.library = library;
    }

    void exportTo(Path path, ConfigurationSelection selection, char[] password) throws Exception {
        if (selection.hostCredentials() && !selection.hosts()) throw new IOException("Host credentials require the host library");
        if (selection.isEmpty()) throw new IOException("Select at least one configuration section");
        Map<String, String> sections = new LinkedHashMap<>();
        if (selection.program()) {
            // Materialize defaults before exporting so a fresh installation also round-trips.
            var settings = new YamlGlobalSettingsRepository(store);
            var document = store.read();
            Map<String, Object> program = YamlGlobalSettingsRepository.encodeSettings(settings.load());
            program.put("softwareUpdate", Map.of("automaticChecksEnabled", new YamlSoftwareUpdateRepository(store).load().automaticChecksEnabled()));
            program.put("ai", portableAi(document.get("ai")));
            sections.put("ProgramConfig", ConfigurationTextFile.yaml(program));
        }
        if (selection.hosts() || selection.credentials() || selection.hostCredentials()) {
            var document = catalog.exportDocument(new HostCatalogArchive.ExportSelection(
                    selection.credentials(), selection.hostCredentials()), password);
            if (!selection.credentials()) document.put("credentialGroups", List.of());
            if (!selection.hosts()) { document.put("groups", List.of()); document.put("hosts", List.of()); }
            sections.put("HostLibrary", ConfigurationTextFile.yaml(document));
        }
        if (selection.commands()) {
            Map<com.ynpe.fastremote.domain.snippet.SnippetGroupId, String> groups = new HashMap<>();
            snippets.listGroups().forEach(group -> groups.put(group.id(), group.name()));
            sections.put("Commands", ConfigurationTextFile.yaml(Map.of(
                    "groups", snippets.listGroups().stream().map(group -> Map.of("name", group.name(), "color", group.color().name())).toList(),
                    "entries", snippets.listSnippets().stream().map(command -> Map.of(
                            "group", command.groupId().map(groups::get).orElse(""), "name", command.name(),
                            "content", command.content(), "description", command.description())).toList())));
        }
        if (selection.variables()) {
            Map<com.ynpe.fastremote.domain.snippet.SnippetVariableGroupId, String> groups = new HashMap<>();
            snippets.listVariableGroups().forEach(group -> groups.put(group.id(), group.name()));
            sections.put("Variables", ConfigurationTextFile.yaml(Map.of(
                    "groups", snippets.listVariableGroups().stream().map(group -> Map.of("name", group.name(), "color", group.color().name())).toList(),
                    "entries", snippets.listVariables().stream().map(variable -> Map.of(
                            "group", variable.groupId().map(groups::get).orElse(""), "name", variable.name(),
                            "key", variable.variableName(), "type", variable.type().name(), "delimiter", variable.delimiter(),
                            "value", variable.value(), "description", variable.description())).toList())));
        }
        ConfigurationTextFile.write(path, sections);
    }

    HostCatalogArchive.ArchiveInfo inspect(Path path) throws Exception {
        var sections = ConfigurationTextFile.read(path);
        validate(sections);
        return sections.containsKey("HostLibrary") ? catalog.inspect(ConfigurationTextFile.mapping(sections.get("HostLibrary")))
                : new HostCatalogArchive.ArchiveInfo(false, false, 0, 0, 0);
    }

    void verifyPassword(Path path, char[] password) throws Exception {
        var sections = ConfigurationTextFile.read(path);
        validate(sections);
        if (sections.containsKey("HostLibrary")) catalog.verifyPassword(ConfigurationTextFile.mapping(sections.get("HostLibrary")), password);
    }

    HostCatalogArchive.ImportResult importFrom(Path path, char[] password) throws Exception {
        var sections = ConfigurationTextFile.read(path);
        validate(sections);
        synchronized (vault) {
        synchronized (store) {
            var before = store.read();
            try {
                return database.atomic(() -> {
                    if (sections.containsKey("ProgramConfig")) applyProgram(ConfigurationTextFile.mapping(sections.get("ProgramConfig")));
                    if (sections.containsKey("Commands") || sections.containsKey("Variables")) {
                        library.importArchive(libraryLines(sections), sections.containsKey("Commands"));
                    }
                    return sections.containsKey("HostLibrary")
                            ? catalog.importDocument(ConfigurationTextFile.mapping(sections.get("HostLibrary")), password)
                            : new HostCatalogArchive.ImportResult(0, 0, 0);
                });
            } catch (Exception failure) {
                try { store.update(document -> { document.clear(); document.putAll(before); return null; }); }
                catch (Exception rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
        }
    }

    void resetProgram() throws Exception {
        synchronized (vault) {
        synchronized (store) {
            var before = store.read();
            try {
                new YamlGlobalSettingsRepository(store).save(com.ynpe.fastremote.domain.settings.GlobalSettings.defaults());
                new YamlAiSettingsRepository(store).save(com.ynpe.fastremote.domain.ai.AiSettings.defaults());
                store.update(document -> {
                    @SuppressWarnings("unchecked") var settings = (Map<String, Object>) document.get("settings");
                    settings.remove("softwareUpdate");
                    settings.put("rememberUnlock", false);
                    ((Map<?, ?>) document.get("vault")).remove("rememberedKek");
                    return null;
                });
            } catch (Exception failure) {
                try { store.update(document -> { document.clear(); document.putAll(before); return null; }); }
                catch (Exception rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
        }
    }

    private void validate(Map<String, String> sections) throws Exception {
        if (sections.containsKey("ProgramConfig")) {
            var program = ConfigurationTextFile.mapping(sections.get("ProgramConfig"));
            if (!(program.get("workspace") instanceof Map<?, ?>)) throw new IOException("Program configuration requires workspace settings");
            for (String key : program.keySet()) if (!SETTINGS.contains(key) && !key.equals("ai")) throw new IOException("Unknown program setting: " + key);
            YamlGlobalSettingsRepository.decodeSettings(program);
            if (program.containsKey("ai")) YamlAiSettingsRepository.decodeSettings(portableAi(program.get("ai")));
            if (program.containsKey("softwareUpdate")) {
                Object value = program.get("softwareUpdate");
                if (!(value instanceof Map<?, ?> map) || !(map.get("automaticChecksEnabled") instanceof Boolean))
                    throw new IOException("Invalid software update preferences");
            }
        }
        if (sections.containsKey("HostLibrary")) catalog.inspect(ConfigurationTextFile.mapping(sections.get("HostLibrary")));
        if (sections.containsKey("Commands") || sections.containsKey("Variables"))
            library.validateArchive(libraryLines(sections), sections.containsKey("Commands"));
    }

    private void applyProgram(Map<String, Object> program) throws Exception {
        new YamlGlobalSettingsRepository(store).save(YamlGlobalSettingsRepository.decodeSettings(program));
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings = (Map<String, Object>) document.get("settings");
            if (program.containsKey("softwareUpdate")) settings.put("softwareUpdate", program.get("softwareUpdate"));
            if (program.containsKey("ai")) document.put("ai", portableAi(program.get("ai")));
            return null;
        });
    }

    private static List<String> libraryLines(Map<String, String> sections) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String tag : List.of("Variables", "Commands")) {
            if (!sections.containsKey(tag)) continue;
            boolean variables = tag.equals("Variables");
            var section = ConfigurationTextFile.mapping(sections.get(tag));
            if (!section.keySet().equals(Set.of("groups", "entries"))) throw new IOException("Expected groups and entries in " + tag);
            lines.add(variables ? SnippetLibraryTextCodec.VARIABLES_HEADER : SnippetLibraryTextCodec.COMMANDS_HEADER);
            for (var group : records(section.get("groups")))
                lines.add(SnippetLibraryTextCodec.record("@group", text(group, "name"), text(group, "color")));
            for (var entry : records(section.get("entries"))) {
                if (variables) lines.add(SnippetLibraryTextCodec.record("@variable", text(entry, "group"),
                        text(entry, "name"), text(entry, "key"), text(entry, "type"), text(entry, "delimiter"),
                        text(entry, "value"), text(entry, "description")));
                else lines.add(SnippetLibraryTextCodec.record("@command", text(entry, "group"),
                        text(entry, "name"), text(entry, "content"), text(entry, "description")));
            }
        }
        return lines;
    }

    private static List<Map<?, ?>> records(Object value) throws IOException {
        if (!(value instanceof List<?> list) || list.size() > 10_000) throw new IOException("Invalid library record list");
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) throw new IOException("Invalid library record");
            result.add(map);
        }
        return result;
    }

    private static String text(Map<?, ?> record, String key) throws IOException {
        if (!(record.get(key) instanceof String value)) throw new IOException("Expected text field: " + key);
        return value;
    }

    private static Map<String, Object> portableAi(Object raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> source)) return result;
        for (var entry : source.entrySet()) {
            String key = entry.getKey().toString();
            if (Set.of("apiKeySecretId", "tokenSecretId", "tokenHash").contains(key)) continue;
            Object value = entry.getValue();
            if (value instanceof Map<?, ?>) value = portableAi(value);
            else if (value instanceof List<?> list) value = list.stream().map(item -> item instanceof Map<?, ?> ? portableAi(item) : item).toList();
            result.put(key, value);
        }
        if (result.get("mcpServer") instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked") var mcp = (Map<String, Object>) map;
            mcp.put("enabled", false);
        }
        return result;
    }
}
