// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.security.HostKeyIdentity;
import com.ynpe.fastremote.domain.security.KnownHostRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Stores OpenSSH-compatible key lines. FastRemote's preceding fingerprint comment is informational;
 * trust matching is based only on the key line and the display fingerprint is derived from its key blob.
 */
public final class FileKnownHostRepository implements KnownHostRepository {
    private static final String COMMENT_PREFIX = "# FastRemote fingerprint ";

    private final Path file;

    public FileKnownHostRepository(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public static FileKnownHostRepository inDefaultLocation() {
        return new FileKnownHostRepository(FastRemotePaths.applicationDataDirectory().resolve("known_hosts"));
    }

    @Override
    public synchronized Optional<HostKeyIdentity> find(Host host, Port port) throws IOException {
        return Optional.ofNullable(readEntries().get(key(host, port)));
    }

    @Override
    public synchronized void save(HostKeyIdentity identity) throws IOException {
        Map<String, HostKeyIdentity> entries = readEntries();
        entries.put(key(identity.host(), identity.port()), identity);
        writeAtomically(entries.values());
    }

    private Map<String, HostKeyIdentity> readEntries() throws IOException {
        Map<String, HostKeyIdentity> entries = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return entries;
        }
        // Decode with replacement so unrelated legacy comments or damaged non-key bytes cannot make every
        // host appear unreadable. Individual malformed key lines are ignored and require a fresh user decision.
        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        for (String rawLine : content.lines().toList()) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("@")) continue;
            String[] parts = line.split("\\s+");
            if (parts.length < 3) continue;
            String fingerprint = fingerprint(parts[2]);
            if (fingerprint == null) continue;
            for (String hostToken : parts[0].split(",")) {
                HostAndPort parsed = parseHostToken(hostToken);
                if (parsed == null) continue;
                try {
                    HostKeyIdentity identity = new HostKeyIdentity(
                            new Host(parsed.host()),
                            new Port(parsed.port()),
                            parts[1],
                            fingerprint,
                            parts[1] + " " + parts[2]);
                    entries.put(key(identity.host(), identity.port()), identity);
                } catch (IllegalArgumentException malformedEntry) {
                    // A bad legacy or external entry must not prevent confirmation of an unrelated host.
                }
            }
        }
        return entries;
    }

    private static String fingerprint(String encodedKey) {
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key);
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
        } catch (IllegalArgumentException invalidBase64) {
            return null;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void writeAtomically(Iterable<HostKeyIdentity> entries) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        List<String> lines = new ArrayList<>();
        lines.add("# FastRemote trusted SSH host keys. Do not edit while FastRemote is running.");
        for (HostKeyIdentity identity : entries) {
            String[] keyParts = identity.encodedPublicKey().split("\\s+", 2);
            if (keyParts.length != 2) {
                throw new IOException("Cannot persist malformed SSH public key entry");
            }
            String fingerprint = fingerprint(keyParts[1]);
            if (fingerprint == null) throw new IOException("Cannot persist malformed SSH public key entry");
            lines.add(COMMENT_PREFIX + fingerprint);
            lines.add(formatHostToken(identity.host(), identity.port()) + " " + keyParts[0] + " " + keyParts[1]);
        }

        Path temporary = Files.createTempFile(parent, "known_hosts-", ".tmp");
        boolean moved = false;
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static String formatHostToken(Host host, Port port) {
        return port.value() == 22 ? host.value() : "[" + host.value() + "]:" + port.value();
    }

    private static HostAndPort parseHostToken(String token) {
        if (token.startsWith("[") && token.contains("]:")) {
            int separator = token.lastIndexOf("]:");
            try {
                return new HostAndPort(token.substring(1, separator), Integer.parseInt(token.substring(separator + 2)));
            } catch (NumberFormatException invalidPort) {
                return null;
            }
        }
        return new HostAndPort(token, 22);
    }

    private static String key(Host host, Port port) {
        return host.value().toLowerCase(Locale.ROOT) + ':' + port.value();
    }

    private record HostAndPort(String host, int port) {
    }

}
