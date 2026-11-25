// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.Username;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileConnectionProfileRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsOnlyNonSecretConnectionFields() throws Exception {
        Path file = directory.resolve("profile.properties");
        FileConnectionProfileRepository repository = new FileConnectionProfileRepository(file);
        ConnectionProfile profile = new ConnectionProfile(
                new Host("server.example"),
                new Port(2222),
                new Username("alice"),
                AuthenticationType.PRIVATE_KEY,
                Optional.of(directory.resolve("id_ed25519")),
                true);

        repository.saveLastProfile(profile);
        assertEquals(profile, repository.loadLastProfile().orElseThrow());
        String raw = Files.readString(file);
        assertFalse(raw.toLowerCase().contains("password"));
        assertFalse(raw.toLowerCase().contains("passphrase"));
        assertFalse(raw.toLowerCase().contains("secret"));
    }

    @Test
    void ignoresStoredSchemaVersion() throws Exception {
        Path file = directory.resolve("profile.properties");
        Files.writeString(file, """
                schemaVersion=99
                host=example.com
                port=22
                username=alice
                authentication=PASSWORD
                """);
        FileConnectionProfileRepository repository = new FileConnectionProfileRepository(file);
        assertEquals("example.com", repository.loadLastProfile().orElseThrow().host().value());
    }
}
