// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenSshConfigImporterTest {
    @TempDir Path directory;

    @Test
    void importsProxyJumpAliasAsSavedHostRoute() throws Exception {
        Path config = directory.resolve("route-config");
        Files.writeString(config, """
                Host bastion
                  HostName edge.example.com
                  User jump
                Host internal
                  HostName 10.0.0.15
                  User app
                  ProxyJump bastion
                """);

        try (var bundle = new OpenSshConfigImporter().read(config, Map.of())) {
            assertEquals(2, bundle.hosts().size());
            var internal = bundle.hosts().stream()
                    .filter(host -> host.name().equals("internal")).findFirst().orElseThrow();
            assertEquals("bastion", internal.jumpName().orElseThrow());
            assertEquals(1, bundle.inspection().proxyHosts());
        }
    }

    @Test
    void resolvesIncludesDefaultsConcreteAliasesAndReusableKeys() throws Exception {
        Path key = directory.resolve("id test");
        Files.writeString(key, "-----BEGIN PRIVATE KEY-----\ntest\n-----END PRIVATE KEY-----\n");
        Path included = directory.resolve("hosts.conf");
        Files.writeString(included, """
                Host app-one app-two
                  HostName server.example.com
                  IdentityFile missing-key
                  IdentityFile "id test"
                Host proxied
                  HostName hidden.example.com
                  ProxyJump bastion
                Host *.wild
                  User ignored
                """);
        Path config = directory.resolve("config");
        Files.writeString(config, """
                User deploy
                Port 2202
                Include hosts.conf
                """);

        var importer = new OpenSshConfigImporter();
        var info = importer.inspect(config);
        assertEquals(2, info.hosts());
        assertEquals(1, info.readableIdentities());
        assertEquals(1, info.proxyHosts());
        assertEquals(1, info.wildcardPatterns());
        assertEquals(2, info.configFiles());

        try (var bundle = importer.read(config, Map.of())) {
            assertEquals(1, bundle.credentials().size());
            assertEquals(2, bundle.hosts().size());
            assertEquals("server.example.com", bundle.hosts().getFirst().host());
            assertEquals(2202, bundle.hosts().getFirst().port());
            assertEquals("deploy", bundle.hosts().getFirst().username());
            assertEquals(bundle.hosts().getFirst().credentialKey(), bundle.hosts().getLast().credentialKey());
        }
    }

    @Test
    void promptsOnlyForEncryptedKeysAndSkipsThemWhenNoPassphraseWasSupplied() throws Exception {
        Path key = directory.resolve("encrypted.pem");
        Files.writeString(key, "-----BEGIN ENCRYPTED PRIVATE KEY-----\ndata\n-----END ENCRYPTED PRIVATE KEY-----\n");
        Path config = directory.resolve("config");
        Files.writeString(config, "Host encrypted\n  User root\n  IdentityFile encrypted.pem\n");
        var importer = new OpenSshConfigImporter();

        var info = importer.inspect(config);
        assertEquals(1, info.encryptedIdentities());
        try (var skipped = importer.read(config, Map.of())) {
            assertTrue(skipped.credentials().isEmpty());
            assertFalse(skipped.hosts().getFirst().credentialKey().isPresent());
        }
        try (var included = importer.read(config, Map.of(key.toAbsolutePath().normalize(), "secret".toCharArray()))) {
            assertEquals(1, included.credentials().size());
            assertTrue(included.hosts().getFirst().credentialKey().isPresent());
        }
    }

    @Test
    void appliesFirstObtainedValueAndRejectsIncludeCycles() throws Exception {
        Path config = directory.resolve("config");
        Files.writeString(config, "Host target\n  HostName first.example\nHost *\n  HostName second.example\n  User local\n");
        try (var bundle = new OpenSshConfigImporter().read(config, Map.of())) {
            assertEquals("first.example", bundle.hosts().getFirst().host());
            assertEquals("local", bundle.hosts().getFirst().username());
        }

        Path cycle = directory.resolve("cycle");
        Files.writeString(cycle, "Include cycle\n");
        try {
            new OpenSshConfigImporter().inspect(cycle);
            throw new AssertionError("Expected include cycle rejection");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("循环"));
        }
    }
}
