// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.snakeyaml.engine.v2.api.*;
import org.snakeyaml.engine.v2.common.FlowStyle;

/** Strict sectioned UTF-8 interchange. Only column-zero #Tag and ## comments are structural. */
public final class ConfigurationTextFile {
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final Set<String> TAGS = Set.of("FastRemoteConfig", "ProgramConfig", "HostLibrary",
            "Commands", "Variables");
    private ConfigurationTextFile() { }

    public static Map<String, String> read(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) throw new IOException("Invalid configuration file size");
        return decode(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static Map<String, String> decode(String text) throws IOException {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Configuration exceeds 64 MiB");
        Map<String, StringBuilder> values = new LinkedHashMap<>();
        String current = null;
        for (String line : text.replaceFirst("^\ufeff", "").split("\\R", -1)) {
            if (line.startsWith("##") || (current == null && line.isBlank())) continue;
            if (line.startsWith("#")) {
                current = line.substring(1).strip();
                if (!TAGS.contains(current) || values.containsKey(current)) throw new IOException("Unknown or duplicate configuration section: " + current);
                values.put(current, new StringBuilder());
            } else {
                if (current == null) throw new IOException("Expected #FastRemoteConfig; legacy formats are not supported");
                values.get(current).append(line).append('\n');
            }
        }
        if (values.isEmpty() || !values.keySet().iterator().next().equals("FastRemoteConfig")
                || !values.get("FastRemoteConfig").toString().strip().equals("version: 1")) {
            throw new IOException("Unsupported configuration format version");
        }
        if (values.size() == 1) throw new IOException("Configuration contains no sections");
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, value) -> { if (!key.equals("FastRemoteConfig")) result.put(key, value.toString()); });
        return result;
    }

    public static void write(Path target, Map<String, String> sections) throws IOException {
        StringBuilder content = new StringBuilder("#FastRemoteConfig\nversion: 1\n## FastRemote configuration; credentials remain encrypted.\n");
        sections.forEach((key, value) -> content.append('\n').append('#').append(key).append('\n').append(value).append('\n'));
        String text = content.toString();
        decode(text);
        Path output = target.toAbsolutePath().normalize();
        Path temporary = Files.createTempFile(output.getParent(), ".fastremote-config-", ".tmp");
        try {
            if (Files.getFileStore(temporary).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    public static String yaml(Map<String, Object> document) {
        return new Dump(DumpSettings.builder().setDefaultFlowStyle(FlowStyle.BLOCK).build()).dumpToString(withoutAliases(document));
    }

    private static Object withoutAliases(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(key, withoutAliases(item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(withoutAliases(item)));
            return copy;
        }
        return value;
    }

    public static Map<String, Object> mapping(String value) throws IOException {
        try {
            Object decoded = new Load(LoadSettings.builder().setAllowDuplicateKeys(false)
                    .setMaxAliasesForCollections(0).setCodePointLimit(MAX_BYTES).build()).loadFromString(value);
            if (!(decoded instanceof Map<?, ?> map)) throw new IOException("Expected configuration mapping");
            Map<String, Object> result = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IOException("Invalid configuration key");
                result.put(key, entry.getValue());
            }
            return result;
        } catch (RuntimeException invalid) { throw new IOException("Invalid configuration section", invalid); }
    }
}
