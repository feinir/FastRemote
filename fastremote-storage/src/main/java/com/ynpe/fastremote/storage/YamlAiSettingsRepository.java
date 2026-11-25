// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.ai.AiSettings;
import com.ynpe.fastremote.domain.ai.AiSettingsRepository;
import com.ynpe.fastremote.domain.ai.McpServerSettings;
import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.ModelProviderType;
import com.ynpe.fastremote.domain.automation.AutomationCapability;
import com.ynpe.fastremote.domain.automation.AutomationPermission;
import com.ynpe.fastremote.domain.automation.AutomationPermissions;
import com.ynpe.fastremote.domain.vault.SecretId;
import java.io.IOException;
import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Stores non-secret model, automation and MCP network preferences in global YAML. */
public final class YamlAiSettingsRepository implements AiSettingsRepository {
    private final YamlGlobalConfigStore store;

    public YamlAiSettingsRepository(YamlGlobalConfigStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public AiSettings load() throws IOException {
        return decodeSettings(store.read().get("ai"));
    }

    public static AiSettings decodeSettings(Object raw) throws IOException {
        if (!(raw instanceof Map<?, ?> values)) return AiSettings.defaults();
        try {
            List<ModelProviderConfig> providers = decodeProviders(values.get("providers"));
            Optional<String> selected = optionalText(values.get("selectedProviderId"));
            AutomationPermissions builtIn = decodePermissions(
                    map(values.get("builtInPermissions")), AutomationPermissions.builtInDefaults());
            AutomationPermissions mcp = decodePermissions(
                    map(values.get("mcpPermissions")), AutomationPermissions.mcpDefaults());
            List<Path> roots = decodePaths(values.get("allowedLocalRoots"));
            List<String> remoteRoots = decodeStrings(values.get("allowedRemoteRoots"));
            long maxTransferBytes = longValue(values.get("maxTransferBytes"),
                    AiSettings.DEFAULT_MAX_TRANSFER_BYTES);
            int maxToolRounds = integerValue(values.get("maxToolRounds"), AiSettings.DEFAULT_MAX_TOOL_ROUNDS);
            Map<?, ?> mcpValues = map(values.get("mcpServer"));
            McpServerSettings mcpServer = new McpServerSettings(
                    booleanValue(mcpValues.get("enabled"), false),
                    booleanValue(mcpValues.get("allowRemoteAccess"), false),
                    integerValue(mcpValues.get("port"), McpServerSettings.DEFAULT_PORT),
                    optionalText(mcpValues.get("tokenHash")),
                    optionalText(mcpValues.get("tokenSecretId")).map(SecretId::parse));
            return new AiSettings(providers, selected, builtIn, mcp, roots, remoteRoots,
                    maxTransferBytes, maxToolRounds, mcpServer);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("AI configuration is invalid", invalid);
        }
    }

    @Override
    public void save(AiSettings settings) throws IOException {
        AiSettings safe = Objects.requireNonNull(settings, "settings");
        store.update(document -> {
            Map<String, Object> ai = new LinkedHashMap<>();
            ai.put("providers", encodeProviders(safe.providers()));
            ai.put("selectedProviderId", safe.selectedProviderId().orElse(null));
            ai.put("builtInPermissions", encodePermissions(safe.builtInPermissions()));
            ai.put("mcpPermissions", encodePermissions(safe.mcpPermissions()));
            ai.put("allowedLocalRoots", safe.allowedLocalRoots().stream().map(Path::toString).toList());
            ai.put("allowedRemoteRoots", safe.allowedRemoteRoots());
            ai.put("maxTransferBytes", safe.maxTransferBytes());
            ai.put("maxToolRounds", safe.maxToolRounds());
            Map<String, Object> mcp = new LinkedHashMap<>();
            mcp.put("enabled", safe.mcpServer().enabled());
            mcp.put("allowRemoteAccess", safe.mcpServer().allowRemoteAccess());
            mcp.put("port", safe.mcpServer().port());
            mcp.put("tokenHash", safe.mcpServer().tokenHash().orElse(null));
            mcp.put("tokenSecretId", safe.mcpServer().tokenSecretId().map(SecretId::toString).orElse(null));
            ai.put("mcpServer", mcp);
            document.put("ai", ai);
            return null;
        });
    }

    private static List<ModelProviderConfig> decodeProviders(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        if (list.size() > 32) throw new IllegalArgumentException("Too many model providers");
        List<ModelProviderConfig> providers = new ArrayList<>();
        for (Object item : list) {
            Map<?, ?> value = map(item);
            providers.add(new ModelProviderConfig(
                    requiredText(value.get("id")),
                    requiredText(value.get("name")),
                    ModelProviderType.valueOf(requiredText(value.get("type"))),
                    URI.create(requiredText(value.get("baseUrl"))),
                    optionalText(value.get("model")).orElse(""),
                    optionalText(value.get("apiKeySecretId")).map(SecretId::parse),
                    booleanValue(value.get("allowInsecureHttp"), false)));
        }
        return List.copyOf(providers);
    }

    private static List<Map<String, Object>> encodeProviders(List<ModelProviderConfig> providers) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (ModelProviderConfig provider : providers) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", provider.id());
            item.put("name", provider.name());
            item.put("type", provider.type().name());
            item.put("baseUrl", provider.baseUri().toString());
            item.put("model", provider.model());
            item.put("apiKeySecretId", provider.apiKeySecretId().map(SecretId::toString).orElse(null));
            item.put("allowInsecureHttp", provider.allowInsecureHttp());
            values.add(item);
        }
        return values;
    }

    private static AutomationPermissions decodePermissions(Map<?, ?> values, AutomationPermissions defaults) {
        EnumMap<AutomationCapability, AutomationPermission> decoded = new EnumMap<>(AutomationCapability.class);
        for (AutomationCapability capability : AutomationCapability.values()) {
            Object raw = values.get(capability.name());
            if (!(raw instanceof String)) raw = legacyPermission(values, capability);
            decoded.put(capability, raw instanceof String text
                    ? AutomationPermission.valueOf(text) : defaults.permission(capability));
        }
        return new AutomationPermissions(decoded);
    }

    private static Object legacyPermission(Map<?, ?> values, AutomationCapability capability) {
        return switch (capability) {
            case CREATE_HOSTS, DELETE_HOSTS, EDIT_HOSTS -> values.get("MANAGE_CATALOG");
            case CREATE_LOCAL, EDIT_LOCAL -> values.get("WRITE_LOCAL");
            case DELETE_LOCAL, RENAME_LOCAL -> values.get("MUTATE_LOCAL");
            case READ_REMOTE -> values.get("LIST_REMOTE");
            case CREATE_REMOTE, EDIT_REMOTE, DELETE_REMOTE, RENAME_REMOTE -> values.get("MUTATE_REMOTE");
            default -> null;
        };
    }

    private static Map<String, Object> encodePermissions(AutomationPermissions permissions) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (AutomationCapability capability : AutomationCapability.values()) {
            values.put(capability.name(), permissions.permission(capability).name());
        }
        return values;
    }

    private static List<Path> decodePaths(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        if (list.size() > 32) throw new IllegalArgumentException("Too many allowed local roots");
        try {
            return list.stream().map(YamlAiSettingsRepository::requiredText).map(Path::of).toList();
        } catch (InvalidPathException invalid) {
            throw new IllegalArgumentException("Invalid allowed local path", invalid);
        }
    }

    private static List<String> decodeStrings(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        if (list.size() > 64) throw new IllegalArgumentException("Too many allowed remote roots");
        return list.stream().map(YamlAiSettingsRepository::requiredText).toList();
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> result ? result : Map.of();
    }

    private static Optional<String> optionalText(Object value) {
        return value instanceof String text && !text.isBlank() ? Optional.of(text.strip()) : Optional.empty();
    }

    private static String requiredText(Object value) {
        return optionalText(value).orElseThrow(() -> new IllegalArgumentException("Required AI setting is missing"));
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean result ? result : fallback;
    }

    private static int integerValue(Object value, int fallback) {
        if (!(value instanceof Number number)) return fallback;
        long result = number.longValue();
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("AI numeric setting is out of range");
        }
        return (int) result;
    }

    private static long longValue(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }
}
