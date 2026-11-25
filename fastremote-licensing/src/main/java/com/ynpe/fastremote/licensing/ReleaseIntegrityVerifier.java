// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.licensing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;

public final class ReleaseIntegrityVerifier {
    private static final byte[] ED25519_X509_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");
    private static final byte[] DOMAIN = "FastRemote/release-manifest/v1\0".getBytes(StandardCharsets.UTF_8);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReleaseIntegrityVerifier() { }

    public static void verifyAgent(Path manifest, Path agent, String encodedKeys) throws IOException {
        try {
            if (Files.size(manifest) < 1 || Files.size(manifest) > 1024 * 1024) {
                throw new IOException("integrity manifest has invalid size");
            }
            JsonNode envelope = MAPPER.readTree(Files.readAllBytes(manifest));
            byte[] payload = Base64.getUrlDecoder().decode(envelope.path("payload").asText());
            byte[] signature = Base64.getUrlDecoder().decode(envelope.path("signature").asText());
            JsonNode claims = MAPPER.readTree(payload);
            if (claims.path("version").asInt() != 1) throw new IOException("unsupported integrity manifest");
            PublicKey key = keys(encodedKeys).get(claims.path("key_id").asText());
            if (key == null) throw new IOException("unknown integrity manifest key");
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(DOMAIN);
            verifier.update(payload);
            if (!verifier.verify(signature)) throw new IOException("invalid integrity manifest signature");
            Path root = manifest.toAbsolutePath().normalize().getParent();
            int protectedFiles = 0;
            boolean agentProtected = false;
            var paths = new HashSet<String>();
            for (JsonNode file : claims.path("files")) {
                String relative = file.path("path").asText();
                boolean agentEntry = relative.equals(agent.getFileName().toString());
                if ((!relative.endsWith(".jar") && !agentEntry) || relative.contains("/")
                        || relative.contains("\\") || !paths.add(relative)) {
                    throw new IOException("invalid protected release path");
                }
                verifyDigest(agentEntry ? agent : root.resolve(relative),
                        file.path("sha256").asText(), 1024L * 1024 * 1024);
                agentProtected |= agentEntry;
                protectedFiles++;
            }
            if (protectedFiles < 2 || !agentProtected) {
                throw new IOException("release manifest must protect application jars and the license agent");
            }
            if (!Files.isRegularFile(agent) || !Files.isExecutable(agent)) throw new IOException("license agent is unavailable");
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("release integrity verification failed", error);
        }
    }

    private static void verifyDigest(Path file, String expectedHex, long maximumSize) throws Exception {
        long size = Files.size(file);
        if (size < 1 || size > maximumSize) throw new IOException("protected release file has invalid size");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int count; (count = input.read(buffer)) >= 0;) if (count > 0) digest.update(buffer, 0, count);
        }
        if (!MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(expectedHex))) {
            throw new IOException("protected release file integrity mismatch");
        }
    }

    private static Map<String, PublicKey> keys(String value) throws Exception {
        Map<String, PublicKey> result = new HashMap<>();
        for (String entry : value.split(",")) {
            if (entry.isBlank()) continue;
            String[] parts = entry.strip().split("=", 2);
            if (parts.length != 2) throw new IOException("invalid integrity public key entry");
            byte[] raw = Base64.getDecoder().decode(parts[1]);
            if (raw.length != 32) throw new IOException("invalid Ed25519 integrity public key");
            byte[] encoded = Arrays.copyOf(ED25519_X509_PREFIX, ED25519_X509_PREFIX.length + raw.length);
            System.arraycopy(raw, 0, encoded, ED25519_X509_PREFIX.length, raw.length);
            result.put(parts[0], KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded)));
        }
        if (result.isEmpty()) throw new IOException("no integrity public keys configured");
        return result;
    }
}
