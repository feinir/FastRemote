// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.security.HostKeyIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileKnownHostRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsAndReplacesAChangedKeyAtomically() throws Exception {
        Path file = directory.resolve("data").resolve("known_hosts");
        FileKnownHostRepository repository = new FileKnownHostRepository(file);
        Host host = new Host("Example.COM");
        Port port = new Port(2222);

        assertTrue(repository.find(host, port).isEmpty());
        repository.save(new HostKeyIdentity(host, port, "ssh-ed25519", "SHA256:first", "ssh-ed25519 AAAA"));
        assertEquals(fingerprint("AAAA"), repository.find(new Host("example.com"), port)
                .orElseThrow().sha256Fingerprint());

        repository.save(new HostKeyIdentity(host, port, "ssh-ed25519", "SHA256:second", "ssh-ed25519 BBBB"));
        HostKeyIdentity loaded = repository.find(host, port).orElseThrow();
        assertEquals(fingerprint("BBBB"), loaded.sha256Fingerprint());
        assertEquals("ssh-ed25519 BBBB", loaded.encodedPublicKey());
        assertFalse(Files.readString(file).contains("AAAA"));
        assertFalse(Files.readString(file).contains("SHA256:second"));
        assertTrue(Files.readString(file).contains("# FastRemote fingerprint " + fingerprint("BBBB")));
        assertTrue(Files.readString(file).contains("[Example.COM]:2222 ssh-ed25519 BBBB"));
    }

    @Test
    void readsStandardKeyLinesWithoutDependingOnFingerprintCommentsAndSkipsMalformedLegacyEntries()
            throws Exception {
        Path file = directory.resolve("known_hosts");
        Files.writeString(file, """
                # FastRemote fingerprint stale-or-invalid
                example.com ssh-ed25519 AAAA
                [bad.example]:99999 ssh-ed25519 BBBB
                malformed legacy line
                """);
        FileKnownHostRepository repository = new FileKnownHostRepository(file);

        HostKeyIdentity loaded = repository.find(new Host("example.com"), new Port(22)).orElseThrow();

        assertEquals("ssh-ed25519 AAAA", loaded.encodedPublicKey());
        assertEquals(fingerprint("AAAA"), loaded.sha256Fingerprint());
        assertTrue(repository.find(new Host("bad.example"), new Port(22)).isEmpty());
    }

    private static String fingerprint(String encodedKey) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(encodedKey));
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
    }
}
