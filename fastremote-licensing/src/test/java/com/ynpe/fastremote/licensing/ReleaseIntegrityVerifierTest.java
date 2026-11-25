// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.licensing;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReleaseIntegrityVerifierTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void verifiesManifestAndRejectsPatchedApplicationJarOrAgent(@TempDir Path directory) throws Exception {
        Path agent = directory.resolve("fastremote-license-agent");
        Files.writeString(agent, "agent");
        agent.toFile().setExecutable(true);
        Path applicationJar = directory.resolve("fastremote-app.jar");
        Files.writeString(applicationJar, "original");
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(applicationJar));
        byte[] agentDigest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(agent));
        byte[] payload = MAPPER.writeValueAsBytes(Map.of("version", 1, "key_id", "release", "files",
                List.of(Map.of("path", applicationJar.getFileName().toString(), "sha256", HexFormat.of().formatHex(digest)),
                        Map.of("path", agent.getFileName().toString(), "sha256", HexFormat.of().formatHex(agentDigest)))));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update("FastRemote/release-manifest/v1\0".getBytes(StandardCharsets.UTF_8));
        signer.update(payload);
        Path manifest = directory.resolve("fastremote-integrity.json");
        Files.write(manifest, MAPPER.writeValueAsBytes(Map.of("payload", Base64.getUrlEncoder().withoutPadding().encodeToString(payload),
                "signature", Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()))));
        byte[] x509 = keys.getPublic().getEncoded();
        String publicValue = Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(x509, x509.length - 32, x509.length));
        ReleaseIntegrityVerifier.verifyAgent(manifest, agent, "release=" + publicValue);
        Files.writeString(applicationJar, "patched");
        assertThrows(java.io.IOException.class, () -> ReleaseIntegrityVerifier.verifyAgent(manifest, agent, "release=" + publicValue));
        Files.writeString(applicationJar, "original");
        Files.writeString(agent, "patched-agent");
        assertThrows(java.io.IOException.class, () -> ReleaseIntegrityVerifier.verifyAgent(manifest, agent, "release=" + publicValue));
    }
}
