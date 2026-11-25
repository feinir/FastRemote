// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import com.ynpe.fastremote.domain.vault.SecretId;
import java.util.Objects;
import java.util.Optional;

public record McpServerSettings(
        boolean enabled,
        boolean allowRemoteAccess,
        int port,
        Optional<String> tokenHash,
        Optional<SecretId> tokenSecretId) {
    public static final int DEFAULT_PORT = 37654;

    public McpServerSettings(
            boolean enabled, int port, Optional<String> tokenHash, Optional<SecretId> tokenSecretId) {
        this(enabled, false, port, tokenHash, tokenSecretId);
    }

    public McpServerSettings {
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("MCP port must be between 1024 and 65535");
        tokenHash = Objects.requireNonNull(tokenHash, "tokenHash")
                .map(String::strip).filter(value -> !value.isEmpty());
        tokenSecretId = Objects.requireNonNull(tokenSecretId, "tokenSecretId");
        if (enabled && tokenHash.isEmpty()) throw new IllegalArgumentException("Enabled MCP server requires a token hash");
    }

    public static McpServerSettings defaults() {
        return new McpServerSettings(false, false, DEFAULT_PORT, Optional.empty(), Optional.empty());
    }
}
