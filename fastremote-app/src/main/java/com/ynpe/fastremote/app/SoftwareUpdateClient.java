// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ynpe.fastremote.domain.update.SoftwareRelease;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

final class SoftwareUpdateClient {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private final URI serverBaseUri;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final Target target;
    private final boolean community;

    SoftwareUpdateClient(URI serverBaseUri) {
        this(serverBaseUri, false);
    }

    SoftwareUpdateClient(URI serverBaseUri, boolean community) {
        this(serverBaseUri, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL).build(), Target.current(), community);
    }

    SoftwareUpdateClient(URI serverBaseUri, HttpClient http, Target target) {
        this(serverBaseUri, http, target, false);
    }

    SoftwareUpdateClient(URI serverBaseUri, HttpClient http, Target target, boolean community) {
        this.serverBaseUri = Objects.requireNonNull(serverBaseUri, "serverBaseUri");
        this.http = Objects.requireNonNull(http, "http");
        this.target = Objects.requireNonNull(target, "target");
        this.community = community;
    }

    SoftwareRelease latestStable() throws IOException, InterruptedException {
        String query = "platform=" + encoded(target.platform()) + "&architecture="
                + encoded(target.architecture()) + "&channel=stable";
        URI endpoint = community
                ? serverBaseUri.resolve("/community/latest/" + target.platform() + "-" + target.architecture() + ".json")
                : serverBaseUri.resolve("/api/client/v1/versions/latest?" + query);
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json").GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        byte[] body;
        try (InputStream input = response.body()) {
            body = input.readNBytes(MAX_RESPONSE_BYTES + 1);
        }
        if (body.length > MAX_RESPONSE_BYTES) throw new IOException("Version response is too large");
        if (community && response.statusCode() == 404) return null;
        JsonNode root = json.readTree(body);
        if (root == null || !root.isObject()) throw new IOException("Version service returned invalid JSON");
        if (response.statusCode() == 404
                && "VERSION_NOT_FOUND".equals(root.path("error").path("code").asText())) return null;
        if (response.statusCode() != 200) throw new IOException("Version service returned HTTP " + response.statusCode());
        if (!root.path("ok").asBoolean(false) || !root.path("data").isObject()) {
            throw new IOException("Version service returned an invalid response");
        }
        JsonNode data = root.path("data");
        String channel = requiredText(data, "channel");
        if (!"stable".equals(channel)) throw new IOException("Version service returned a non-stable release");
        String platform = requiredText(data, "platform");
        String architecture = requiredText(data, "architecture");
        if (!target.platform().equals(platform) || !target.architecture().equals(architecture)) {
            throw new IOException("Version service returned a release for another target");
        }
        try {
            String version = requiredText(data, "version");
            if (!com.ynpe.fastremote.domain.update.SemanticVersion.parse(version).prerelease().isEmpty()) {
                throw new IllegalArgumentException("stable release uses a prerelease version");
            }
            URI download = URI.create(requiredText(data, "download_url"));
            if (community && (!serverBaseUri.getAuthority().equals(download.getAuthority())
                    || !download.getPath().startsWith("/community/"))) {
                throw new IllegalArgumentException("Community release uses a different download origin or edition");
            }
            return new SoftwareRelease(version, platform, architecture,
                    download, requiredText(data, "sha256"),
                    requiredLong(data, "file_size"), data.path("release_notes").asText(""),
                    Instant.parse(requiredText(data, "published_at")));
        } catch (RuntimeException invalid) {
            throw new IOException("Version service returned invalid release metadata", invalid);
        }
    }

    URI downloadPage() {
        return community ? serverBaseUri.resolve("/community/") : SoftwareUpdateCoordinator.downloadPage();
    }

    private static String requiredText(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) throw new IOException("Missing " + field);
        return value.asText().strip();
    }

    private static long requiredLong(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.canConvertToLong() || value.asLong() <= 0) throw new IOException("Invalid " + field);
        return value.asLong();
    }

    private static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    record Target(String platform, String architecture, String extension) {
        Target {
            Objects.requireNonNull(platform); Objects.requireNonNull(architecture); Objects.requireNonNull(extension);
        }

        static Target current() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            String architecture = switch (arch) {
                case "x86_64", "amd64" -> "amd64";
                case "aarch64", "arm64" -> "arm64";
                default -> throw new IllegalStateException("Unsupported update architecture: " + arch);
            };
            if (os.contains("mac")) return new Target("macos", architecture, ".dmg");
            if (os.contains("win")) return new Target("windows", architecture, ".msi");
            if (os.contains("linux")) return new Target("linux", architecture, ".tar.gz");
            throw new IllegalStateException("Unsupported update platform: " + os);
        }
    }
}
