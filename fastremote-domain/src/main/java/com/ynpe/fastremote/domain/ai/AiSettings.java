// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import com.ynpe.fastremote.domain.automation.AutomationPermissions;
import com.ynpe.fastremote.domain.transfer.RemotePathSyntax;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record AiSettings(
        List<ModelProviderConfig> providers,
        Optional<String> selectedProviderId,
        AutomationPermissions builtInPermissions,
        AutomationPermissions mcpPermissions,
        List<Path> allowedLocalRoots,
        List<String> allowedRemoteRoots,
        long maxTransferBytes,
        int maxToolRounds,
        McpServerSettings mcpServer) {
    public static final long MEBIBYTE = 1024L * 1024L;
    public static final long DEFAULT_MAX_TRANSFER_BYTES = 1024L * MEBIBYTE;
    public static final long MIN_MAX_TRANSFER_BYTES = 1024L * MEBIBYTE;
    public static final long MAX_MAX_TRANSFER_BYTES = 4096L * MEBIBYTE;
    public static final int DEFAULT_MAX_TOOL_ROUNDS = 20;
    public static final int MIN_MAX_TOOL_ROUNDS = 1;
    public static final int MAX_MAX_TOOL_ROUNDS = 100;

    public AiSettings(List<ModelProviderConfig> providers, Optional<String> selectedProviderId,
            AutomationPermissions builtInPermissions, AutomationPermissions mcpPermissions,
            List<Path> allowedLocalRoots, List<String> allowedRemoteRoots, int maxToolRounds,
            McpServerSettings mcpServer) {
        this(providers, selectedProviderId, builtInPermissions, mcpPermissions, allowedLocalRoots,
                allowedRemoteRoots, DEFAULT_MAX_TRANSFER_BYTES, maxToolRounds, mcpServer);
    }

    public AiSettings(List<ModelProviderConfig> providers, Optional<String> selectedProviderId,
            AutomationPermissions builtInPermissions, AutomationPermissions mcpPermissions,
            List<Path> allowedLocalRoots, List<String> allowedRemoteRoots, McpServerSettings mcpServer) {
        this(providers, selectedProviderId, builtInPermissions, mcpPermissions, allowedLocalRoots,
                allowedRemoteRoots, DEFAULT_MAX_TRANSFER_BYTES, DEFAULT_MAX_TOOL_ROUNDS, mcpServer);
    }

    public AiSettings(List<ModelProviderConfig> providers, Optional<String> selectedProviderId,
            AutomationPermissions builtInPermissions, AutomationPermissions mcpPermissions,
            List<Path> allowedLocalRoots, McpServerSettings mcpServer) {
        this(providers, selectedProviderId, builtInPermissions, mcpPermissions,
                allowedLocalRoots, List.of(), DEFAULT_MAX_TRANSFER_BYTES, DEFAULT_MAX_TOOL_ROUNDS, mcpServer);
    }

    public AiSettings {
        providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
        // Up to 32 user-defined providers plus the non-persisted built-in ChatGPT provider.
        if (providers.size() > 33) throw new IllegalArgumentException("At most 33 model providers are supported");
        Set<String> ids = providers.stream().map(ModelProviderConfig::id).collect(Collectors.toSet());
        if (ids.size() != providers.size()) throw new IllegalArgumentException("Model provider IDs must be unique");
        selectedProviderId = Objects.requireNonNull(selectedProviderId, "selectedProviderId")
                .map(String::strip).filter(ids::contains);
        builtInPermissions = Objects.requireNonNull(builtInPermissions, "builtInPermissions");
        mcpPermissions = Objects.requireNonNull(mcpPermissions, "mcpPermissions");
        allowedLocalRoots = Objects.requireNonNull(allowedLocalRoots, "allowedLocalRoots").stream()
                .map(Path::toAbsolutePath).map(Path::normalize).distinct().limit(32).toList();
        allowedRemoteRoots = Objects.requireNonNull(allowedRemoteRoots, "allowedRemoteRoots").stream()
                .map(AiSettings::normalizeRemoteRoot).distinct().limit(64).toList();
        if (maxTransferBytes < MIN_MAX_TRANSFER_BYTES || maxTransferBytes > MAX_MAX_TRANSFER_BYTES) {
            throw new IllegalArgumentException("Automation transfer limit must be between 1 GiB and 4 GiB");
        }
        if (maxToolRounds < MIN_MAX_TOOL_ROUNDS || maxToolRounds > MAX_MAX_TOOL_ROUNDS) {
            throw new IllegalArgumentException("AI tool rounds must be between "
                    + MIN_MAX_TOOL_ROUNDS + " and " + MAX_MAX_TOOL_ROUNDS);
        }
        mcpServer = Objects.requireNonNull(mcpServer, "mcpServer");
    }

    public static AiSettings defaults() {
        return new AiSettings(List.of(), Optional.empty(), AutomationPermissions.builtInDefaults(),
                AutomationPermissions.mcpDefaults(), List.of(), List.of(), DEFAULT_MAX_TRANSFER_BYTES,
                DEFAULT_MAX_TOOL_ROUNDS,
                McpServerSettings.defaults());
    }

    public static String normalizeRemoteRoot(String value) {
        return RemotePathSyntax.normalizeAbsolute(value);
    }
}
