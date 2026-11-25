// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;

/** Atomic YAML storage for global preferences and encrypted credentials. */
public final class YamlGlobalConfigStore {
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_DOCUMENT_BYTES = 4 * 1024 * 1024;

    private final Path path;
    private final Load loader;
    private final Dump dumper;

    public YamlGlobalConfigStore(Path path) throws IOException {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        LoadSettings loadSettings = LoadSettings.builder()
                .setLabel("FastRemote global configuration")
                .setAllowDuplicateKeys(false)
                .setMaxAliasesForCollections(8)
                .setCodePointLimit(MAX_DOCUMENT_BYTES)
                .build();
        DumpSettings dumpSettings = DumpSettings.builder()
                .setDefaultFlowStyle(FlowStyle.BLOCK)
                .setIndent(2)
                // SnakeYAML requires the sequence indicator to remain inside the mapping indent.
                // Using the same value as indent produces invalid YAML for a list of mappings.
                .setIndicatorIndent(1)
                .setDumpComments(false)
                .build();
        loader = new Load(loadSettings);
        dumper = new Dump(dumpSettings);
        Path parent = this.path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
            restrict(parent, "rwx------");
        }
    }

    public static YamlGlobalConfigStore openDefault() throws IOException {
        return new YamlGlobalConfigStore(
                FastRemotePaths.applicationDataDirectory().resolve("global-config.yaml"));
    }

    public Path path() {
        return path;
    }

    public synchronized Map<String, Object> read() throws IOException {
        return deepCopy(load());
    }

    public synchronized <T> T update(Function<Map<String, Object>, T> mutation) throws IOException {
        Objects.requireNonNull(mutation, "mutation");
        Map<String, Object> document = load();
        T result = mutation.apply(document);
        document.put("schemaVersion", SCHEMA_VERSION);
        validate(document);
        write(document);
        return result;
    }

    private Map<String, Object> load() throws IOException {
        if (Files.notExists(path)) {
            return emptyDocument();
        }
        long size = Files.size(path);
        if (size > MAX_DOCUMENT_BYTES) {
            throw new IOException("Global configuration exceeds the 4 MiB safety limit");
        }
        Object loaded;
        try {
            loaded = loader.loadFromString(Files.readString(path, StandardCharsets.UTF_8));
        } catch (RuntimeException malformed) {
            throw new IOException("Global configuration is not valid safe YAML", malformed);
        }
        if (!(loaded instanceof Map<?, ?> raw)) {
            throw new IOException("Global configuration root must be a mapping");
        }
        Map<String, Object> document = stringMap(raw, "global configuration");
        boolean migrated = migrateTokenSecrets(document);
        document.put("schemaVersion", SCHEMA_VERSION);
        validate(document);
        if (migrated) {
            write(document);
        }
        return document;
    }

    private void write(Map<String, Object> document) throws IOException {
        Path parent = path.getParent();
        if (parent == null) {
            throw new IOException("Global configuration must have a parent directory");
        }
        String yaml = dumper.dumpToString(document);
        if (yaml.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) {
            throw new IOException("Global configuration exceeds the 4 MiB safety limit");
        }
        Path temporary = Files.createTempFile(parent, ".global-config-", ".tmp");
        boolean moved = false;
        try {
            restrict(temporary, "rw-------");
            Files.writeString(temporary, yaml, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, path,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            restrict(path, "rw-------");
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static Map<String, Object> emptyDocument() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("masterPasswordConfigured", false);
        settings.put("rememberUnlock", false);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("settings", settings);
        root.put("vault", new LinkedHashMap<String, Object>());
        root.put("credentials", new LinkedHashMap<String, Object>());
        root.put("tokens", new LinkedHashMap<String, Object>());
        root.put("credentialGroups", new LinkedHashMap<String, Object>());
        root.put("ai", new LinkedHashMap<String, Object>());
        return root;
    }

    private static void validate(Map<String, Object> document) throws IOException {
        Map<String, Object> settings = requireMap(document, "settings");
        requireBoolean(settings, "masterPasswordConfigured");
        requireBoolean(settings, "rememberUnlock");
        requireMap(document, "vault");
        validateSecretSection(document, "credentials", false);
        validateSecretSection(document, "tokens", true);
        requireMap(document, "credentialGroups");
    }

    private static void validateSecretSection(
            Map<String, Object> document, String sectionName, boolean tokensOnly) throws IOException {
        Map<String, Object> entries = requireMap(document, sectionName);
        for (Object value : entries.values()) {
            if (!(value instanceof Map<?, ?> entry)) {
                throw new IOException("Global configuration section '" + sectionName
                        + "' contains an invalid secret record");
            }
            boolean token = "TOKEN".equals(entry.get("type"));
            if (token != tokensOnly) {
                throw new IOException("Global configuration section '" + sectionName
                        + "' contains a secret of the wrong type");
            }
        }
    }

    private static boolean migrateTokenSecrets(Map<String, Object> document) throws IOException {
        Map<String, Object> credentials = requireMap(document, "credentials");
        Map<String, Object> tokens;
        Object currentTokens = document.get("tokens");
        boolean changed = false;
        if (currentTokens == null) {
            tokens = new LinkedHashMap<>();
            document.put("tokens", tokens);
            changed = true;
        } else {
            tokens = requireMap(document, "tokens");
        }
        var iterator = credentials.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> item = iterator.next();
            if (!(item.getValue() instanceof Map<?, ?> raw)) continue;
            Object type = raw.get("type");
            if (!"TOKEN".equals(type)) continue;
            Object existing = tokens.get(item.getKey());
            if (existing != null && !Objects.equals(existing, item.getValue())) {
                throw new IOException("Token migration found a conflicting SecretId");
            }
            tokens.put(item.getKey(), item.getValue());
            iterator.remove();
            changed = true;
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> requireMap(Map<String, Object> document, String key) throws IOException {
        Object value = document.get(key);
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IOException("Global configuration field '" + key + "' must be a mapping");
        }
        for (Object item : raw.keySet()) {
            if (!(item instanceof String)) {
                throw new IOException("Global configuration mapping '" + key + "' contains a non-string key");
            }
        }
        return (Map<String, Object>) raw;
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw, String name) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IOException(name + " contains a non-string key");
            }
            result.put(key, normalize(entry.getValue(), 0));
        }
        return result;
    }

    private static Object normalize(Object value, int depth) throws IOException {
        if (depth > 20) {
            throw new IOException("Global configuration nesting exceeds the safety limit");
        }
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IOException("Nested configuration mapping contains a non-string key");
                }
                result.put(key, normalize(entry.getValue(), depth + 1));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(normalize(item, depth + 1));
            }
            return normalized;
        }
        throw new IOException("Global configuration contains an unsupported YAML value");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copyValue(value)));
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put((String) key, copyValue(item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(YamlGlobalConfigStore::copyValue).toList();
        }
        return value;
    }

    private static boolean requireBoolean(Map<String, Object> values, String field) throws IOException {
        Object value = values.get(field);
        if (value instanceof Boolean bool) return bool;
        throw new IOException("Global configuration field 'settings." + field + "' must be true or false");
    }

    private static void restrict(Path target, String permissions) {
        try {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString(permissions));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows ACL hardening is handled by the native installer and application data directory.
        }
    }
}
