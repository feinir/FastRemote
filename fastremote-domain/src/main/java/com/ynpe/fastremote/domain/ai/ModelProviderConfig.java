// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import com.ynpe.fastremote.domain.vault.SecretId;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public record ModelProviderConfig(
        String id,
        String name,
        ModelProviderType type,
        URI baseUri,
        String model,
        Optional<SecretId> apiKeySecretId,
        boolean allowInsecureHttp) {
    public ModelProviderConfig {
        id = bounded(id, "id", 64);
        name = bounded(name, "name", 100);
        type = Objects.requireNonNull(type, "type");
        baseUri = Objects.requireNonNull(baseUri, "baseUri").normalize();
        model = optionalBounded(model, "model", 200);
        apiKeySecretId = Objects.requireNonNull(apiKeySecretId, "apiKeySecretId");
        String scheme = Objects.requireNonNullElse(baseUri.getScheme(), "").toLowerCase(java.util.Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Model service URL must use HTTP or HTTPS");
        }
        if (baseUri.getHost() == null || baseUri.getUserInfo() != null || baseUri.getFragment() != null) {
            throw new IllegalArgumentException("Model service URL is not valid");
        }
        if (type == ModelProviderType.GEMINI_API
                && baseUri.getRawQuery() != null) {
            throw new IllegalArgumentException("Gemini Base URL must not contain query parameters");
        }
        if (scheme.equals("http") && !allowInsecureHttp && !isLoopback(baseUri.getHost())) {
            throw new IllegalArgumentException("Non-local model services must use HTTPS");
        }
    }

    private static String bounded(String value, String name, int maximum) {
        String safe = Objects.requireNonNull(value, name).strip();
        if (safe.isEmpty() || safe.length() > maximum) {
            throw new IllegalArgumentException(name + " must contain 1 to " + maximum + " characters");
        }
        return safe;
    }

    private static String optionalBounded(String value, String name, int maximum) {
        String safe = Objects.requireNonNull(value, name).strip();
        if (safe.length() > maximum) {
            throw new IllegalArgumentException(name + " must contain at most " + maximum + " characters");
        }
        return safe;
    }

    private static boolean isLoopback(String host) {
        return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1")
                || host.equals("[::1]");
    }
}
