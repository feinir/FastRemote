// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConnectionValuesTest {
    @Test
    void normalizesRequiredTextValues() {
        assertEquals("example.com", new Host("  example.com ").value());
        assertEquals("alice", new Username(" alice ").value());
    }

    @Test
    void rejectsInvalidPorts() {
        assertThrows(IllegalArgumentException.class, () -> new Port(0));
        assertThrows(IllegalArgumentException.class, () -> new Port(65_536));
    }

    @Test
    void supportsExternalOrVaultBackedPrivateKeys() {
        ConnectionProfile embedded = new ConnectionProfile(
                new Host("localhost"),
                new Port(22),
                new Username("alice"),
                AuthenticationType.PRIVATE_KEY,
                Optional.empty());
        assertEquals(Optional.empty(), embedded.privateKeyPath());

        ConnectionProfile profile = new ConnectionProfile(
                new Host("localhost"),
                new Port(22),
                new Username("alice"),
                AuthenticationType.PRIVATE_KEY,
                Optional.of(Path.of("id_ed25519")));
        assertEquals(Path.of("id_ed25519").toAbsolutePath(), profile.privateKeyPath().orElseThrow());
    }
}
