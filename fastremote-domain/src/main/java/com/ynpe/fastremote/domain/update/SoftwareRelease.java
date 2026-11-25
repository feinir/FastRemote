// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.update;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public record SoftwareRelease(
        String version,
        String platform,
        String architecture,
        URI downloadUri,
        String sha256,
        long fileSize,
        String releaseNotes,
        Instant publishedAt) {
    public SoftwareRelease {
        version = required(version, "version");
        SemanticVersion.parse(version);
        platform = required(platform, "platform");
        architecture = required(architecture, "architecture");
        downloadUri = Objects.requireNonNull(downloadUri, "downloadUri");
        if (!"https".equalsIgnoreCase(downloadUri.getScheme())
                && !isLoopbackDevelopmentUri(downloadUri)) {
            throw new IllegalArgumentException("downloadUri must use HTTPS");
        }
        sha256 = required(sha256, "sha256");
        if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid SHA-256");
        if (fileSize <= 0) throw new IllegalArgumentException("fileSize must be positive");
        releaseNotes = Objects.requireNonNullElse(releaseNotes, "").strip();
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
    }

    private static String required(String value, String name) {
        String text = Objects.requireNonNull(value, name).strip();
        if (text.isEmpty()) throw new IllegalArgumentException(name + " is blank");
        return text;
    }

    private static boolean isLoopbackDevelopmentUri(URI uri) {
        if (!"http".equalsIgnoreCase(uri.getScheme())) return false;
        String host = Objects.requireNonNullElse(uri.getHost(), "");
        return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1");
    }
}
