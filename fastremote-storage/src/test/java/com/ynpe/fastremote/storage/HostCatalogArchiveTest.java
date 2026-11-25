// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.ynpe.fastremote.domain.catalog.*;
import com.ynpe.fastremote.domain.connection.*;
import com.ynpe.fastremote.domain.credential.*;
import com.ynpe.fastremote.domain.vault.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostCatalogArchiveTest {
    @TempDir Path directory;

    @Test
    void exportsAndImportsOneYamlFileWithoutCredentials() throws Exception {
        Path export = directory.resolve("hosts.yaml");
        try (Fixture source = fixture("plain-source")) {
            HostGroup group = new HostGroup(GroupId.create(), Optional.empty(),
                    "生产环境", 0, GroupColor.PURPLE);
            source.hosts.saveGroup(group);
            SavedHost host = SavedHost.create("Web 主机", Optional.of(group.id()),
                    new ConnectionProfile(new Host("web.example.com"), new Port(22), new Username("root"),
                            AuthenticationType.PRIVATE_KEY, Optional.empty(), true), 30, HostIcon.CLOUD)
                    .withFavorite(true);
            source.hosts.saveHost(host);
            source.archive.exportTo(export, new HostCatalogArchive.ExportSelection(false, false), new char[0]);
        }

        String yaml = java.nio.file.Files.readString(export);
        assertTrue(yaml.contains("format: FastRemote"));
        assertTrue(yaml.contains("hosts:"));
        assertFalse(yaml.startsWith("PK"), "the export must be YAML, not a ZIP container");

        try (Fixture target = fixture("plain-target")) {
            HostCatalogArchive.ImportResult result = target.archive.importFrom(export, new char[0]);
            assertEquals(1, result.hosts());
            assertEquals(1, result.groups());
            assertEquals(0, result.credentials());
            SavedHost imported = target.hosts.listHosts().getFirst();
            assertEquals("Web 主机", imported.name());
            assertEquals(HostIcon.CLOUD, imported.icon());
            assertTrue(imported.favorite());
            assertTrue(imported.profile().legacyAlgorithmsEnabled());
            assertTrue(imported.credentialSecretId().isEmpty());
        }
    }

    @Test
    void roundTripsJumpHostReferences() throws Exception {
        Path export = directory.resolve("jump-route.yaml");
        HostId jumpId;
        HostId targetId;
        try (Fixture source = fixture("jump-source")) {
            SavedHost jump = SavedHost.create("Bastion", Optional.empty(),
                    profile("edge.example.com", AuthenticationType.PASSWORD), 0);
            SavedHost target = SavedHost.create("Internal", Optional.empty(),
                    profile("10.0.0.15", AuthenticationType.PASSWORD), 10)
                    .withJumpHost(Optional.of(jump.id()));
            source.hosts.saveHost(jump);
            source.hosts.saveHost(target);
            jumpId = jump.id();
            targetId = target.id();
            source.archive.exportTo(export,
                    new HostCatalogArchive.ExportSelection(false, false), new char[0]);
        }

        try (Fixture target = fixture("jump-target")) {
            target.archive.importFrom(export, new char[0]);
            assertEquals(Optional.of(jumpId),
                    target.hosts.findHost(targetId).orElseThrow().jumpHostId());
        }
    }

    @Test
    void importsReusableAndHostCredentialsWithReferencesAndReencryptsForTargetVault() throws Exception {
        Path export = directory.resolve("complete.yaml");
        char[] sourcePassword = "source-master".toCharArray();
        char[] targetPassword = "target-master".toCharArray();
        byte[] sharedPlaintext = "shared-password-value".getBytes(StandardCharsets.UTF_8);
        byte[] boundPlaintext = "host-password-value".getBytes(StandardCharsets.UTF_8);
        try {
            try (Fixture source = fixture("secret-source")) {
                source.vault.create(sourcePassword);
                SecretId sharedId = source.vault.put(SecretType.PASSWORD, sharedPlaintext);
                source.credentials.save(new GlobalCredential(sharedId, "运维账号", "ops",
                        AuthenticationType.PASSWORD, CredentialScope.REUSABLE, Optional.empty()));

                SavedHost first = SavedHost.create("共享主机", Optional.empty(),
                        profile("shared.example.com", AuthenticationType.PASSWORD));
                source.hosts.saveHost(first.withCredential(Optional.of(sharedId)));

                SavedHost second = SavedHost.create("专属主机", Optional.empty(),
                        profile("bound.example.com", AuthenticationType.PASSWORD));
                source.hosts.saveHost(second);
                SecretId boundId = source.vault.put(SecretType.PASSWORD, boundPlaintext);
                source.credentials.save(new GlobalCredential(boundId, "专属主机（主机专属）", "root",
                        AuthenticationType.PASSWORD, CredentialScope.HOST_BOUND, Optional.of(second.id())));
                source.hosts.saveHost(second.withCredential(Optional.of(boundId)));

                source.archive.exportTo(export,
                        new HostCatalogArchive.ExportSelection(true, true), sourcePassword);
            }

            String yaml = java.nio.file.Files.readString(export);
            assertFalse(yaml.contains(new String(sourcePassword)));
            assertFalse(yaml.contains(new String(sharedPlaintext, StandardCharsets.UTF_8)));
            assertFalse(yaml.contains(new String(boundPlaintext, StandardCharsets.UTF_8)));

            try (Fixture target = fixture("secret-target")) {
                target.vault.create(targetPassword);
                HostCatalogArchive.ImportResult result = target.archive.importFrom(export, sourcePassword);
                assertEquals(2, result.hosts());
                assertEquals(2, result.credentials());
                assertEquals(2, target.credentials.list().size());

                SavedHost shared = target.hosts.listHosts().stream()
                        .filter(item -> item.name().equals("共享主机")).findFirst().orElseThrow();
                SavedHost bound = target.hosts.listHosts().stream()
                        .filter(item -> item.name().equals("专属主机")).findFirst().orElseThrow();
                GlobalCredential sharedCredential = target.credentials
                        .find(shared.credentialSecretId().orElseThrow()).orElseThrow();
                GlobalCredential boundCredential = target.credentials
                        .find(bound.credentialSecretId().orElseThrow()).orElseThrow();
                assertEquals(CredentialScope.REUSABLE, sharedCredential.scope());
                assertEquals(CredentialScope.HOST_BOUND, boundCredential.scope());
                assertEquals(Optional.of(bound.id()), boundCredential.ownerHostId());
                assertArrayEquals(sharedPlaintext, target.vault.read(sharedCredential.id()));
                assertArrayEquals(boundPlaintext, target.vault.read(boundCredential.id()));
            }
        } finally {
            Arrays.fill(sourcePassword, '\0');
            Arrays.fill(targetPassword, '\0');
            Arrays.fill(sharedPlaintext, (byte) 0);
            Arrays.fill(boundPlaintext, (byte) 0);
        }
    }

    @Test
    void independentCredentialFlagsAndWrongPasswordAreEnforced() throws Exception {
        Path export = directory.resolve("reusable-only.yaml");
        char[] password = "source-master".toCharArray();
        try (Fixture source = fixture("flags-source")) {
            source.vault.create(password);
            SavedHost host = SavedHost.create("主机", Optional.empty(),
                    profile("host.example.com", AuthenticationType.PASSWORD));
            source.hosts.saveHost(host);
            SecretId reusable = source.vault.put(SecretType.PASSWORD, "reusable".getBytes(StandardCharsets.UTF_8));
            source.credentials.save(new GlobalCredential(reusable, "共享", "ops", AuthenticationType.PASSWORD,
                    CredentialScope.REUSABLE, Optional.empty()));
            SecretId bound = source.vault.put(SecretType.PASSWORD, "bound".getBytes(StandardCharsets.UTF_8));
            source.credentials.save(new GlobalCredential(bound, "专属", "root", AuthenticationType.PASSWORD,
                    CredentialScope.HOST_BOUND, Optional.of(host.id())));
            source.hosts.saveHost(host.withCredential(Optional.of(bound)));
            source.archive.exportTo(export, new HostCatalogArchive.ExportSelection(true, false), password);
        }

        try (Fixture target = fixture("flags-target")) {
            target.vault.create("target-master".toCharArray());
            assertThrows(java.io.IOException.class,
                    () -> target.archive.importFrom(export, "wrong-password".toCharArray()));
            assertTrue(target.hosts.listHosts().isEmpty());
            HostCatalogArchive.ImportResult result = target.archive.importFrom(export, password);
            assertEquals(1, result.credentials());
            assertEquals(CredentialScope.REUSABLE, target.credentials.list().getFirst().scope());
            assertTrue(target.hosts.listHosts().getFirst().credentialSecretId().isEmpty());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Test
    void preservesStableIdsReportsOverwritesAndSuffixesOnlyDifferentIds() throws Exception {
        Path export = directory.resolve("identity.yaml");
        HostId stableId = HostId.create();
        try (Fixture source = fixture("identity-source")) {
            InstantPair time = new InstantPair();
            source.hosts.saveHost(new SavedHost(stableId, Optional.empty(), "同名主机",
                    profile("new.example.com", AuthenticationType.PASSWORD), Optional.empty(), false, 0,
                    time.value, time.value, HostIcon.SERVER));
            source.archive.exportTo(export, new HostCatalogArchive.ExportSelection(false, false), new char[0]);
        }

        try (Fixture target = fixture("identity-target")) {
            InstantPair time = new InstantPair();
            target.hosts.saveHost(new SavedHost(stableId, Optional.empty(), "旧名称",
                    profile("old.example.com", AuthenticationType.PASSWORD), Optional.empty(), false, 0,
                    time.value, time.value, HostIcon.SERVER));
            target.hosts.saveHost(SavedHost.create("同名主机", Optional.empty(),
                    profile("other.example.com", AuthenticationType.PASSWORD)));

            HostCatalogArchive.ArchiveInfo info = target.archive.inspect(export);
            assertEquals(1, info.hostIdConflicts());
            assertTrue(info.hasIdConflicts());
            target.archive.importFrom(export, new char[0]);

            SavedHost overwritten = target.hosts.findHost(stableId).orElseThrow();
            assertEquals("同名主机-1", overwritten.name());
            assertEquals("new.example.com", overwritten.profile().host().value());
            assertEquals(2, target.hosts.listHosts().size());
        }
    }

    @Test
    void rememberedExportNeedsNoPasswordAndStillRequiresOriginalPasswordForImport() throws Exception {
        char[] original = "export-master".toCharArray();
        Map<String, Object> document;
        SecretId id;
        try (Fixture source = fixture("remembered-export")) {
            source.vault.create(original, true);
            id = source.vault.put(SecretType.PASSWORD, "private-value".getBytes(StandardCharsets.UTF_8));
            source.credentials.save(new GlobalCredential(id, "test", "user",
                    AuthenticationType.PASSWORD, CredentialScope.REUSABLE, Optional.empty()));
            source.vault.lock();
            document = source.archive.exportDocument(new HostCatalogArchive.ExportSelection(true, false), new char[0]);
            assertEquals(VaultStatus.LOCKED, source.vault.status());
            source.archive.verifyPassword(document, original);
            assertThrows(java.io.IOException.class,
                    () -> source.archive.verifyPassword(document, "wrong-master".toCharArray()));
            String encoded = document.toString();
            assertFalse(encoded.contains("private-value"));
            assertFalse(encoded.contains("rememberedKek"));
            assertFalse(encoded.contains("wrappedDek"));
            source.vault.unlock(original);
            source.vault.setRememberedUnlock(false, original);
            assertThrows(VaultException.class,
                    () -> source.archive.exportDocument(new HostCatalogArchive.ExportSelection(true, false), new char[0]));
            // Already unlocked is not sufficient to bypass original-password verification.
            assertThrows(VaultException.class,
                    () -> source.archive.exportDocument(new HostCatalogArchive.ExportSelection(true, false), "wrong-master".toCharArray()));
        }
        try (Fixture target = fixture("remembered-export-import")) {
            target.vault.create("target-master".toCharArray());
            target.archive.importDocument(document, original);
            assertArrayEquals("private-value".getBytes(StandardCharsets.UTF_8), target.vault.read(id));
        }
    }

    private Fixture fixture(String name) throws Exception {
        var store = new YamlGlobalConfigStore(directory.resolve(name + ".yaml"));
        var vault = new YamlSecretVault(store, new java.security.SecureRandom(), 8, 1, 1);
        var credentials = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve(name + ".db"));
        var hosts = new SqliteHostCatalogRepository(database);
        return new Fixture(database, hosts, vault, credentials,
                new HostCatalogArchive(hosts, vault, credentials));
    }

    private static ConnectionProfile profile(String host, AuthenticationType type) {
        return new ConnectionProfile(new Host(host), new Port(22), new Username("root"), type, Optional.empty());
    }

    private static final class InstantPair {
        private final java.time.Instant value = java.time.Instant.now();
    }

    private record Fixture(FastRemoteDatabase database, SqliteHostCatalogRepository hosts,
            YamlSecretVault vault, YamlGlobalCredentialRepository credentials,
            HostCatalogArchive archive) implements AutoCloseable {
        @Override public void close() {
            vault.close();
            database.close();
        }
    }
}
