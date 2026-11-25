// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.licensing.CommunityLicenseGateway;
import com.ynpe.fastremote.licensing.GoAgentLicenseGateway;
import com.ynpe.fastremote.licensing.LicenseGateway;
import com.ynpe.fastremote.licensing.ReleaseIntegrityVerifier;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class LicenseRuntime {
    private static final URI RELEASE_SERVICE_URI = URI.create("https://license.ynpe.com");

    private LicenseRuntime() { }

    static LicenseGateway create(Path applicationDataDirectory) {
        if (ApplicationEdition.current() != ApplicationEdition.COMMERCIAL) {
            return new CommunityLicenseGateway();
        }
        Path agent = resolveAgent();
        Path manifest = agent.resolveSibling("fastremote-integrity.json");
        if (Files.isRegularFile(manifest)) {
            try {
                ReleaseIntegrityVerifier.verifyAgent(manifest, agent,
                        System.getProperty("fastremote.license.manifestPublicKeys", ""));
            } catch (IOException error) {
                throw new IllegalStateException("FastRemote license agent failed release integrity verification", error);
            }
        }
        return new GoAgentLicenseGateway(agent, applicationDataDirectory.resolve("license"), developmentServerUri(),
                System.getProperty("fastremote.license.devSigningKeys", ""),
                Files.isRegularFile(manifest) ? manifest : null,
                ApplicationVersion.current());
    }

    static URI serviceUri() {
        URI development = developmentServerUri();
        return development == null ? RELEASE_SERVICE_URI : development;
    }

    private static URI developmentServerUri() {
        String configured = System.getProperty("fastremote.license.serverUrl", "").strip();
        return configured.isEmpty() ? null : URI.create(configured);
    }

    static Path resolveAgent() {
        String configured = System.getProperty("fastremote.license.agent", "").strip();
        if (!configured.isEmpty()) return executable(Path.of(configured));
        String name = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "fastremote-license-agent.exe" : "fastremote-license-agent";
        List<Path> candidates = new ArrayList<>();
        Path current = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 6; depth++, current = current.getParent()) {
            candidates.add(current.resolve("app").resolve(name));
            candidates.add(current.resolve("lib").resolve("app").resolve(name));
            candidates.add(current.resolve("bin").resolve(name));
        }
        candidates.add(Path.of(System.getProperty("user.dir"), "fastremote-license-agent", "build", name));
        return candidates.stream().filter(Files::isRegularFile).filter(Files::isExecutable).findFirst()
                .orElse(candidates.get(candidates.size() - 1).toAbsolutePath().normalize());
    }

    private static Path executable(Path value) {
        Path normalized = value.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || !Files.isExecutable(normalized)) {
            throw new IllegalStateException("FastRemote license agent is unavailable: " + normalized);
        }
        return normalized;
    }
}
