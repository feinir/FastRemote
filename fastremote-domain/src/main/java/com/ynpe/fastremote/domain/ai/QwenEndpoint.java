// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import java.net.URI;
import java.util.Objects;

/** Converts Model Studio host and endpoint forms to the OpenAI-compatible base address. */
public final class QwenEndpoint {
    private QwenEndpoint() { }

    public static URI parse(String input) {
        String value = Objects.requireNonNull(input, "input").strip();
        if (value.isEmpty()) throw new IllegalArgumentException("Qwen API Host is required");
        if (!value.contains("://")) value = "https://" + value;
        URI uri = URI.create(value).normalize();
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Qwen API Host must be an HTTP(S) address without credentials or query parameters");
        }
        value = uri.toString();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        String path = uri.getRawPath().replaceAll("/+$", "");
        if (path.isEmpty()) value += "/compatible-mode/v1";
        else if (path.equals("/api/v1")) value = value.substring(0, value.length() - path.length()) + "/compatible-mode/v1";
        else if (path.endsWith("/chat/completions")) value = value.substring(0, value.length() - "/chat/completions".length());
        return URI.create(value);
    }
}
