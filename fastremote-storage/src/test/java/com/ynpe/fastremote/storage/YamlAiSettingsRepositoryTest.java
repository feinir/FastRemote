// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.ai.AiSettings;
import com.ynpe.fastremote.domain.ai.McpServerSettings;
import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.ModelProviderType;
import com.ynpe.fastremote.domain.automation.AutomationCapability;
import com.ynpe.fastremote.domain.automation.AutomationPermission;
import com.ynpe.fastremote.domain.automation.AutomationPermissions;
import com.ynpe.fastremote.domain.vault.SecretId;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlAiSettingsRepositoryTest {
    @TempDir Path temporary;

    @Test
    void persistsGeminiApiUsingOnlySecretReferences() throws Exception {
        var repository = new YamlAiSettingsRepository(new YamlGlobalConfigStore(temporary.resolve("global-config.yaml")));
        var providers = List.of(ModelProviderType.GEMINI_API).stream()
                .map(type -> new ModelProviderConfig(type.name(), "Gemini", type,
                        URI.create("https://generativelanguage.googleapis.com/v1beta"), "", Optional.of(SecretId.create()), false)).toList();
        var defaults = AiSettings.defaults();
        var settings = new AiSettings(providers, Optional.of(providers.getFirst().id()), defaults.builtInPermissions(),
                defaults.mcpPermissions(), List.of(), List.of(), defaults.mcpServer());
        repository.save(settings);
        assertEquals(settings, repository.load());
        String yaml = java.nio.file.Files.readString(temporary.resolve("global-config.yaml"));
        assertFalse(yaml.contains("refresh_token") || yaml.contains("client_secret") || yaml.contains("access_token"));
    }

    @Test
    void persistsKimiProviderAndSecretReference() throws Exception {
        var repository = new YamlAiSettingsRepository(
                new YamlGlobalConfigStore(temporary.resolve("global-config.yaml")));
        var kimi = new ModelProviderConfig("kimi", "月之暗面", ModelProviderType.KIMI_MOONSHOT,
                URI.create("https://api.moonshot.cn/v1"), "kimi-k2.6", Optional.of(SecretId.create()), false);
        var defaults = AiSettings.defaults();
        var expected = new AiSettings(List.of(kimi), Optional.of(kimi.id()), defaults.builtInPermissions(),
                defaults.mcpPermissions(), List.of(), List.of(), defaults.mcpServer());
        repository.save(expected);
        assertEquals(expected, repository.load());
        assertTrue(java.nio.file.Files.readString(temporary.resolve("global-config.yaml")).contains("KIMI_MOONSHOT"));
    }

    @Test
    void defaultsAreSafeWhenAiSectionIsMissing() throws Exception {
        var repository = new YamlAiSettingsRepository(
                new YamlGlobalConfigStore(temporary.resolve("global-config.yaml")));
        AiSettings settings = repository.load();
        assertTrue(settings.providers().isEmpty());
        assertEquals(1024L * 1024L * 1024L, settings.maxTransferBytes());
        assertEquals(20, settings.maxToolRounds());
        assertFalse(settings.mcpServer().enabled());
        assertFalse(settings.mcpServer().allowRemoteAccess());
        assertEquals(AutomationPermission.ASK,
                settings.builtInPermissions().permission(AutomationCapability.RUN_COMMAND));
        assertEquals(AutomationPermission.ASK,
                settings.builtInPermissions().permission(AutomationCapability.OPEN_HOST));
        assertEquals(AutomationPermission.ASK,
                settings.mcpPermissions().permission(AutomationCapability.EDIT_REMOTE));
        assertEquals(AutomationPermission.ASK,
                settings.builtInPermissions().permission(AutomationCapability.READ_WORKSPACE));
        assertEquals(AutomationPermission.ASK,
                settings.mcpPermissions().permission(AutomationCapability.EDIT_WORKSPACE));
        assertEquals(AutomationPermission.ASK,
                settings.mcpPermissions().permission(AutomationCapability.READ_WORKSPACE_LOG));
    }

    @Test
    void splitsLegacyHostPermissionsAndHonorsExplicitOverrides() throws Exception {
        var settings = YamlAiSettingsRepository.decodeSettings(java.util.Map.of(
                "builtInPermissions", java.util.Map.of("MANAGE_CATALOG", "ASK", "DELETE_HOSTS", "DENY"),
                "mcpPermissions", java.util.Map.of("MANAGE_CATALOG", "ALLOW", "EDIT_HOSTS", "ASK")));
        assertEquals(AutomationPermission.ASK, settings.builtInPermissions().permission(AutomationCapability.CREATE_HOSTS));
        assertEquals(AutomationPermission.DENY, settings.builtInPermissions().permission(AutomationCapability.DELETE_HOSTS));
        assertEquals(AutomationPermission.ASK, settings.builtInPermissions().permission(AutomationCapability.EDIT_HOSTS));
        assertEquals(AutomationPermission.ALLOW, settings.mcpPermissions().permission(AutomationCapability.CREATE_HOSTS));
        assertEquals(AutomationPermission.ALLOW, settings.mcpPermissions().permission(AutomationCapability.DELETE_HOSTS));
        assertEquals(AutomationPermission.ASK, settings.mcpPermissions().permission(AutomationCapability.EDIT_HOSTS));
    }

    @Test
    void roundTripsProviderPermissionsRootsAndMcpSettings() throws Exception {
        var repository = new YamlAiSettingsRepository(
                new YamlGlobalConfigStore(temporary.resolve("global-config.yaml")));
        var provider = new ModelProviderConfig("local", "本地模型", ModelProviderType.OLLAMA,
                URI.create("http://127.0.0.1:11434/v1"), "", Optional.empty(), false);
        AiSettings expected = new AiSettings(List.of(provider), Optional.of("local"),
                AiSettings.defaults().builtInPermissions().with(
                        AutomationCapability.RUN_COMMAND, AutomationPermission.ALLOW)
                        .with(AutomationCapability.READ_WORKSPACE, AutomationPermission.DENY),
                AiSettings.defaults().mcpPermissions()
                        .with(AutomationCapability.EDIT_WORKSPACE, AutomationPermission.ALLOW)
                        .with(AutomationCapability.READ_WORKSPACE_LOG, AutomationPermission.DENY),
                List.of(temporary), List.of("/data"),
                2048L * 1024L * 1024L, 37,
                new McpServerSettings(true, true, 38080, Optional.of("sha256:test"),
                        Optional.of(SecretId.create())));
        repository.save(expected);
        assertEquals(expected, repository.load());
        String yaml = java.nio.file.Files.readString(temporary.resolve("global-config.yaml"));
        assertTrue(yaml.contains("READ_WORKSPACE: DENY"));
        assertTrue(yaml.contains("EDIT_WORKSPACE: ALLOW"));
        assertTrue(yaml.contains("READ_WORKSPACE_LOG: DENY"));
    }

    @Test
    void persistsQwenWorkspaceAndSecretReferenceAlongsideExistingProvider() throws Exception {
        var repository = new YamlAiSettingsRepository(
                new YamlGlobalConfigStore(temporary.resolve("global-config.yaml")));
        var legacy = new ModelProviderConfig("local", "Ollama", ModelProviderType.OLLAMA,
                URI.create("http://127.0.0.1:11434/v1"), "", Optional.empty(), false);
        var qwen = new ModelProviderConfig("qwen", "Qwen", ModelProviderType.QWEN_DASHSCOPE,
                URI.create("https://ws-test.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"),
                "qwen-plus", Optional.of(SecretId.create()), false);
        var defaults = AiSettings.defaults();
        var expected = new AiSettings(List.of(legacy, qwen), Optional.of(qwen.id()), defaults.builtInPermissions(),
                defaults.mcpPermissions(), List.of(), List.of(), defaults.mcpServer());
        repository.save(expected);
        assertEquals(expected, repository.load());
        String yaml = java.nio.file.Files.readString(temporary.resolve("global-config.yaml"));
        assertTrue(yaml.contains("QWEN_DASHSCOPE"));
        assertFalse(yaml.contains("Authorization"));
    }

    @Test
    void migratesLegacyGroupedFilePermissionsIntoIndependentCapabilities() throws Exception {
        var store = new YamlGlobalConfigStore(temporary.resolve("global-config.yaml"));
        store.update(document -> {
            Map<String, Object> builtIn = new LinkedHashMap<>();
            builtIn.put("READ_LOCAL", "ALLOW");
            builtIn.put("WRITE_LOCAL", "DENY");
            builtIn.put("MUTATE_LOCAL", "ALLOW");
            builtIn.put("LIST_REMOTE", "ALLOW");
            builtIn.put("MUTATE_REMOTE", "DENY");
            Map<String, Object> ai = new LinkedHashMap<>();
            ai.put("builtInPermissions", builtIn);
            document.put("ai", ai);
            return null;
        });

        var repository = new YamlAiSettingsRepository(store);
        AutomationPermissions permissions = repository.load().builtInPermissions();
        assertEquals(AutomationPermission.ALLOW, permissions.permission(AutomationCapability.READ_LOCAL));
        assertEquals(AutomationPermission.DENY, permissions.permission(AutomationCapability.CREATE_LOCAL));
        assertEquals(AutomationPermission.DENY, permissions.permission(AutomationCapability.EDIT_LOCAL));
        assertEquals(AutomationPermission.ALLOW, permissions.permission(AutomationCapability.DELETE_LOCAL));
        assertEquals(AutomationPermission.ALLOW, permissions.permission(AutomationCapability.RENAME_LOCAL));
        assertEquals(AutomationPermission.ALLOW, permissions.permission(AutomationCapability.READ_REMOTE));
        assertEquals(AutomationPermission.DENY, permissions.permission(AutomationCapability.CREATE_REMOTE));
        assertEquals(AutomationPermission.DENY, permissions.permission(AutomationCapability.EDIT_REMOTE));
        assertEquals(AutomationPermission.DENY, permissions.permission(AutomationCapability.DELETE_REMOTE));
        assertEquals(AutomationPermission.DENY, permissions.permission(AutomationCapability.RENAME_REMOTE));

        repository.save(repository.load());
        Map<?, ?> ai = (Map<?, ?>) store.read().get("ai");
        Map<?, ?> encoded = (Map<?, ?>) ai.get("builtInPermissions");
        assertTrue(encoded.containsKey("CREATE_LOCAL"));
        assertTrue(encoded.containsKey("EDIT_REMOTE"));
        assertFalse(encoded.containsKey("WRITE_LOCAL"));
        assertFalse(encoded.containsKey("MUTATE_REMOTE"));
    }
}
