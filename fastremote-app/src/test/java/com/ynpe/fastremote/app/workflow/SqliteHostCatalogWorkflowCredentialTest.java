// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.Username;
import com.ynpe.fastremote.domain.credential.CredentialScope;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import com.ynpe.fastremote.storage.FastRemoteDatabase;
import com.ynpe.fastremote.storage.SqliteHostCatalogRepository;
import com.ynpe.fastremote.storage.YamlGlobalConfigStore;
import com.ynpe.fastremote.storage.YamlGlobalCredentialRepository;
import com.ynpe.fastremote.storage.YamlSecretVault;
import com.ynpe.fastremote.ui.CredentialDraft;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteHostCatalogWorkflowCredentialTest {
    @TempDir Path directory;

    @Test
    void importsOpenSshHostsAndDeduplicatesEncryptedPrivateKeyCredentials() throws Exception {
        byte[] keyMaterial = "-----BEGIN ENCRYPTED PRIVATE KEY-----\ntest-key\n-----END ENCRYPTED PRIVATE KEY-----\n"
                .getBytes(StandardCharsets.US_ASCII);
        Path key = directory.resolve("id_shared");
        Files.write(key, keyMaterial);
        Path config = directory.resolve("ssh-config");
        Files.writeString(config, """
                Host first second
                  HostName ssh.example.com
                  User deploy
                  Port 2222
                  IdentityFile id_shared
                """);
        Path global = directory.resolve("openssh-global.yaml");
        var store = new YamlGlobalConfigStore(global);
        var vault = new YamlSecretVault(store);
        char[] master = "strong-master".toCharArray();
        vault.create(master);
        var metadata = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve("openssh-catalog.db"));
        var repository = new SqliteHostCatalogRepository(database);
        try (var workflow = new SqliteHostCatalogWorkflow(database, repository, vault, metadata)) {
            var info = workflow.inspectOpenSshConfig(config).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(2, info.hosts());
            assertEquals(1, info.encryptedIdentities());

            char[] passphrase = "key-secret".toCharArray();
            var result = workflow.importOpenSshConfig(config, Map.of(key, passphrase))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            java.util.Arrays.fill(passphrase, '\0');
            assertEquals(2, result.hosts());
            assertEquals(1, result.groups());
            assertEquals(1, result.credentials());
            var hosts = repository.listHosts();
            assertEquals(hosts.getFirst().credentialSecretId(), hosts.getLast().credentialSecretId());
            var credential = metadata.find(hosts.getFirst().credentialSecretId().orElseThrow()).orElseThrow();
            assertEquals(CredentialScope.REUSABLE, credential.scope());
            try (var resolved = workflow.loadGlobalCredential(credential)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS)) {
                assertArrayEquals(keyMaterial, resolved.copyPrivateKey());
                assertArrayEquals("key-secret".toCharArray(), resolved.copySecret());
            }
            String persisted = Files.readString(global);
            assertFalse(persisted.contains("key-secret"));
            assertFalse(persisted.contains(key.toString()));
            assertFalse(persisted.contains(java.util.Base64.getEncoder().encodeToString(keyMaterial)));
        } finally {
            java.util.Arrays.fill(master, '\0');
            java.util.Arrays.fill(keyMaterial, (byte) 0);
        }
    }

    @Test
    void freshVaultSkipsRememberedUnlockAndCreationLeavesItUnlocked() throws Exception {
        var store = new YamlGlobalConfigStore(directory.resolve("fresh-global.yaml"));
        var vault = new YamlSecretVault(store);
        var metadata = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve("fresh-catalog.db"));
        var repository = new SqliteHostCatalogRepository(database);
        try (var workflow = new SqliteHostCatalogWorkflow(database, repository, vault, metadata)) {
            assertFalse(workflow.tryUnlockRememberedVault()
                    .toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertEquals(VaultStatus.NOT_CREATED, vault.status());

            workflow.createVault("strong-master-password".toCharArray(), false)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertEquals(VaultStatus.UNLOCKED, vault.status());
        }
    }

    @Test
    void rememberedUnlockSurvivesACompleteWorkflowRestart() throws Exception {
        Path config = directory.resolve("remembered-restart-global.yaml");
        Path catalog = directory.resolve("remembered-restart-catalog.db");
        char[] password = "12345678".toCharArray();
        try {
            var firstStore = new YamlGlobalConfigStore(config);
            var firstVault = new YamlSecretVault(firstStore);
            var firstDatabase = new FastRemoteDatabase(catalog);
            try (var first = new SqliteHostCatalogWorkflow(
                    firstDatabase,
                    new SqliteHostCatalogRepository(firstDatabase),
                    firstVault,
                    new YamlGlobalCredentialRepository(firstStore))) {
                first.createVault(password, true).toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertEquals(VaultStatus.UNLOCKED, firstVault.status());
            }

            var reopenedStore = new YamlGlobalConfigStore(config);
            var reopenedVault = new YamlSecretVault(reopenedStore);
            var reopenedDatabase = new FastRemoteDatabase(catalog);
            try (var reopened = new SqliteHostCatalogWorkflow(
                    reopenedDatabase,
                    new SqliteHostCatalogRepository(reopenedDatabase),
                    reopenedVault,
                    new YamlGlobalCredentialRepository(reopenedStore))) {
                assertEquals(VaultStatus.LOCKED, reopenedVault.status());
                assertTrue(reopened.tryUnlockRememberedVault()
                        .toCompletableFuture().get(5, TimeUnit.SECONDS));
                assertEquals(VaultStatus.UNLOCKED, reopenedVault.status());
            }
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    @Test
    void hostBoundPasswordFollowsItsOwnerHost() throws Exception {
        var store = new YamlGlobalConfigStore(directory.resolve("global.yaml"));
        var vault = new YamlSecretVault(store);
        vault.create("strong-master-password".toCharArray());
        var metadata = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve("catalog.db"));
        var repository = new SqliteHostCatalogRepository(database);
        try (var workflow = new SqliteHostCatalogWorkflow(database, repository, vault, metadata)) {
            ConnectionProfile profile = new ConnectionProfile(
                    new Host("host.example.com"), new Port(22), new Username("ops"),
                    AuthenticationType.PASSWORD, Optional.empty());
            SavedHost host = SavedHost.create("主机", Optional.empty(), profile);
            char[] password = "host-secret".toCharArray();
            SavedHost saved = workflow.saveHost(
                            host, Optional.of(new CredentialDraft(
                                    AuthenticationType.PASSWORD, password, Optional.empty())))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);

            var credential = metadata.find(saved.credentialSecretId().orElseThrow()).orElseThrow();
            assertEquals(CredentialScope.HOST_BOUND, credential.scope());
            assertEquals(Optional.of(saved.id()), credential.ownerHostId());
            try (var resolved = workflow.loadCredential(saved).toCompletableFuture().get(5, TimeUnit.SECONDS)) {
                assertEquals("host-secret", new String(resolved.copySecret()));
            }

            workflow.deleteHost(saved).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(metadata.find(credential.id()).isEmpty());
        }
    }

    @Test
    void editingAHostReplacesItsBoundPasswordUnderTheStableCredentialId() throws Exception {
        var store = new YamlGlobalConfigStore(directory.resolve("replace-host-global.yaml"));
        var vault = new YamlSecretVault(store);
        vault.create("strong-master-password".toCharArray());
        var metadata = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve("replace-host-catalog.db"));
        var repository = new SqliteHostCatalogRepository(database);
        try (var workflow = new SqliteHostCatalogWorkflow(database, repository, vault, metadata)) {
            ConnectionProfile profile = new ConnectionProfile(new Host("host.example.com"), new Port(22),
                    new Username("ops"), AuthenticationType.PASSWORD, Optional.empty());
            SavedHost original = workflow.saveHost(SavedHost.create("主机", Optional.empty(), profile),
                            Optional.of(new CredentialDraft(AuthenticationType.PASSWORD,
                                    "old-secret".toCharArray(), Optional.empty())))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);

            SavedHost updated = workflow.saveHost(original,
                            Optional.of(new CredentialDraft(AuthenticationType.PASSWORD,
                                    "new-secret".toCharArray(), Optional.empty())))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertEquals(original.credentialSecretId(), updated.credentialSecretId());
            try (var resolved = workflow.loadCredential(updated).toCompletableFuture().get(5, TimeUnit.SECONDS)) {
                assertEquals("new-secret", new String(resolved.copySecret()));
            }
        }
    }

    @Test
    void copyingHostBoundPasswordCreatesANewOwnedCredentialWithTheSameSecret() throws Exception {
        var store = new YamlGlobalConfigStore(directory.resolve("copy-password-global.yaml"));
        var vault = new YamlSecretVault(store);
        vault.create("strong-master-password".toCharArray());
        var metadata = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve("copy-password-catalog.db"));
        var repository = new SqliteHostCatalogRepository(database);
        try (var workflow = new SqliteHostCatalogWorkflow(database, repository, vault, metadata)) {
            ConnectionProfile profile = new ConnectionProfile(
                    new Host("host.example.com"), new Port(22), new Username("ops"),
                    AuthenticationType.PASSWORD, Optional.empty());
            SavedHost source = workflow.saveHost(
                            SavedHost.create("主机", Optional.empty(), profile),
                            Optional.of(new CredentialDraft(
                                    AuthenticationType.PASSWORD, "host-secret".toCharArray(), Optional.empty())))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            SavedHost requestedCopy = SavedHost.create("主机 副本", Optional.empty(), profile)
                    .withCredential(source.credentialSecretId());

            SavedHost copied = workflow.copyHost(source, requestedCopy, Optional.empty())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertFalse(source.id().equals(copied.id()));
            assertFalse(source.credentialSecretId().equals(copied.credentialSecretId()));
            var copiedMetadata = metadata.find(copied.credentialSecretId().orElseThrow()).orElseThrow();
            assertEquals(CredentialScope.HOST_BOUND, copiedMetadata.scope());
            assertEquals(Optional.of(copied.id()), copiedMetadata.ownerHostId());
            try (var resolved = workflow.loadCredential(copied).toCompletableFuture().get(5, TimeUnit.SECONDS)) {
                assertArrayEquals("host-secret".toCharArray(), resolved.copySecret());
            }
        }
    }

    @Test
    void copyingHostBoundPrivateKeyDoesNotNeedTheOriginalFile() throws Exception {
        Path key = directory.resolve("copy-source-key");
        byte[] keyMaterial = "-----BEGIN PRIVATE KEY-----\ncopy-test\n-----END PRIVATE KEY-----\n"
                .getBytes(StandardCharsets.UTF_8);
        Files.write(key, keyMaterial);
        var store = new YamlGlobalConfigStore(directory.resolve("copy-key-global.yaml"));
        var vault = new YamlSecretVault(store);
        vault.create("strong-master-password".toCharArray());
        var metadata = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve("copy-key-catalog.db"));
        var repository = new SqliteHostCatalogRepository(database);
        try (var workflow = new SqliteHostCatalogWorkflow(database, repository, vault, metadata)) {
            ConnectionProfile profile = new ConnectionProfile(
                    new Host("key.example.com"), new Port(22), new Username("deploy"),
                    AuthenticationType.PRIVATE_KEY, Optional.of(key));
            SavedHost source = workflow.saveHost(
                            SavedHost.create("证书主机", Optional.empty(), profile),
                            Optional.of(new CredentialDraft(
                                    AuthenticationType.PRIVATE_KEY, "key-passphrase".toCharArray(), Optional.of(key))))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            Files.delete(key);
            SavedHost requestedCopy = SavedHost.create(
                            "证书主机 副本", Optional.empty(), source.profile())
                    .withCredential(source.credentialSecretId());

            SavedHost copied = workflow.copyHost(source, requestedCopy, Optional.empty())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertFalse(source.credentialSecretId().equals(copied.credentialSecretId()));
            assertTrue(copied.profile().privateKeyPath().isEmpty());
            try (var resolved = workflow.loadCredential(copied).toCompletableFuture().get(5, TimeUnit.SECONDS)) {
                assertArrayEquals(keyMaterial, resolved.copyPrivateKey());
                assertArrayEquals("key-passphrase".toCharArray(), resolved.copySecret());
            }
        } finally {
            java.util.Arrays.fill(keyMaterial, (byte) 0);
        }
    }

    @Test
    void copyingAReusableCredentialKeepsTheSelectedCredentialId() throws Exception {
        var store = new YamlGlobalConfigStore(directory.resolve("copy-library-global.yaml"));
        var vault = new YamlSecretVault(store);
        vault.create("strong-master-password".toCharArray());
        var metadata = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve("copy-library-catalog.db"));
        var repository = new SqliteHostCatalogRepository(database);
        try (var workflow = new SqliteHostCatalogWorkflow(database, repository, vault, metadata)) {
            var credential = workflow.createReusableCredential(
                            "共享运维密码", "ops",
                            new CredentialDraft(AuthenticationType.PASSWORD,
                                    "library-secret".toCharArray(), Optional.empty()))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            ConnectionProfile profile = new ConnectionProfile(
                    new Host("library.example.com"), new Port(22), new Username("ops"),
                    AuthenticationType.PASSWORD, Optional.empty());
            SavedHost source = SavedHost.create("凭证库主机", Optional.empty(), profile)
                    .withCredential(Optional.of(credential.id()));
            workflow.saveHost(source, Optional.empty()).toCompletableFuture().get(5, TimeUnit.SECONDS);
            SavedHost requestedCopy = SavedHost.create("凭证库主机 副本", Optional.empty(), profile)
                    .withCredential(Optional.of(credential.id()));

            SavedHost copied = workflow.copyHost(source, requestedCopy, Optional.empty())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertEquals(Optional.of(credential.id()), copied.credentialSecretId());
            assertEquals(CredentialScope.REUSABLE,
                    metadata.find(copied.credentialSecretId().orElseThrow()).orElseThrow().scope());
        }
    }

    @Test
    void reusablePrivateKeyCopiesMaterialIntoTheVault() throws Exception {
        Path key = directory.resolve("id_test");
        byte[] keyMaterial = "-----BEGIN PRIVATE KEY-----\ntest\n-----END PRIVATE KEY-----\n"
                .getBytes(StandardCharsets.UTF_8);
        Files.write(key, keyMaterial);
        var store = new YamlGlobalConfigStore(directory.resolve("private-key-global.yaml"));
        var vault = new YamlSecretVault(store);
        vault.create("strong-master-password".toCharArray());
        var metadata = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve("private-key-catalog.db"));
        var repository = new SqliteHostCatalogRepository(database);
        try (var workflow = new SqliteHostCatalogWorkflow(database, repository, vault, metadata)) {
            var saved = workflow.createReusableCredential(
                            "部署私钥", "deploy",
                            new CredentialDraft(AuthenticationType.PRIVATE_KEY, "phrase".toCharArray(), Optional.of(key)))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertEquals(CredentialScope.REUSABLE, saved.scope());
            assertTrue(saved.embeddedPrivateKey());
            try (var resolved = workflow.loadGlobalCredential(saved).toCompletableFuture().get(5, TimeUnit.SECONDS)) {
                assertArrayEquals(keyMaterial, resolved.copyPrivateKey());
                assertEquals("phrase", new String(resolved.copySecret()));
            }

            String persisted = Files.readString(directory.resolve("private-key-global.yaml"));
            assertFalse(persisted.contains("-----BEGIN PRIVATE KEY-----"));
            assertFalse(persisted.contains("phrase"));
        }
    }

    @Test
    void editingReusableCredentialKeepsItsStableReferenceAndReplacesEncryptedPassword() throws Exception {
        var store = new YamlGlobalConfigStore(directory.resolve("edited-global.yaml"));
        var vault = new YamlSecretVault(store);
        vault.create("strong-master-password".toCharArray());
        var metadata = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve("edited-catalog.db"));
        var repository = new SqliteHostCatalogRepository(database);
        try (var workflow = new SqliteHostCatalogWorkflow(database, repository, vault, metadata)) {
            var original = workflow.createReusableCredential(
                            "旧名称", "root",
                            new CredentialDraft(AuthenticationType.PASSWORD, "old-secret".toCharArray(), Optional.empty()))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);

            var updated = workflow.updateReusableCredential(
                            original, "新名称", "deploy",
                            Optional.of(new CredentialDraft(
                                    AuthenticationType.PASSWORD, "new-secret".toCharArray(), Optional.empty())))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertEquals(original.id(), updated.id());
            assertEquals("新名称", updated.label());
            assertEquals("deploy", updated.username());
            try (var resolved = workflow.loadGlobalCredential(updated)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS)) {
                assertEquals("new-secret", new String(resolved.copySecret()));
            }
            String persisted = Files.readString(directory.resolve("edited-global.yaml"));
            assertFalse(persisted.contains("old-secret"));
            assertFalse(persisted.contains("new-secret"));
        }
    }

    @Test
    void changingReusableCredentialTypeUpdatesReferencedHostsInBothDirections() throws Exception {
        var store = new YamlGlobalConfigStore(directory.resolve("type-global.yaml"));
        var vault = new YamlSecretVault(store);
        vault.create("strong-master-password".toCharArray());
        var metadata = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve("type-catalog.db"));
        var repository = new SqliteHostCatalogRepository(database);
        Path key = directory.resolve("fixture-key");
        byte[] material = "-----BEGIN PRIVATE KEY-----\nfixture\n-----END PRIVATE KEY-----\n".getBytes(StandardCharsets.UTF_8);
        Files.write(key, material);
        try (var workflow = new SqliteHostCatalogWorkflow(database, repository, vault, metadata)) {
            var original = workflow.createReusableCredential("fixture", "root", new CredentialDraft(
                    AuthenticationType.PASSWORD, "fixture-old".toCharArray(), Optional.empty()))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            var host = SavedHost.create("fixture", Optional.empty(), new ConnectionProfile(
                    new Host("example.test"), new Port(22), new Username("root"),
                    AuthenticationType.PASSWORD, Optional.empty(), true)).withCredential(Optional.of(original.id()));
            workflow.saveHost(host, Optional.empty()).toCompletableFuture().get(5, TimeUnit.SECONDS);
            var current = original;
            for (AuthenticationType type : new AuthenticationType[]{AuthenticationType.PRIVATE_KEY, AuthenticationType.PASSWORD}) {
                current = workflow.updateReusableCredential(current, "updated", "deploy", Optional.of(new CredentialDraft(
                        type, "fixture-new".toCharArray(), type == AuthenticationType.PRIVATE_KEY ? Optional.of(key) : Optional.empty())))
                        .toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertEquals(original.id(), current.id());
                assertEquals(type, metadata.find(current.id()).orElseThrow().authenticationType());
                var savedHost = repository.listHosts().getFirst();
                assertEquals(host.id(), savedHost.id());
                assertEquals(Optional.of(original.id()), savedHost.credentialSecretId());
                assertEquals(type, savedHost.profile().authenticationType());
                assertEquals("deploy", savedHost.profile().username().value());
                assertTrue(savedHost.profile().legacyAlgorithmsEnabled());
                try (var resolved = workflow.loadCredential(savedHost).toCompletableFuture().get(5, TimeUnit.SECONDS)) {
                    assertEquals(type, resolved.authenticationType());
                    assertEquals("fixture-new", new String(resolved.copySecret()));
                    if (type == AuthenticationType.PRIVATE_KEY) assertArrayEquals(material, resolved.copyPrivateKey());
                }
            }
            String persisted = Files.readString(directory.resolve("type-global.yaml"));
            assertFalse(persisted.contains("fixture-new"));
            assertFalse(persisted.contains("BEGIN PRIVATE KEY"));
        }
    }

    @Test
    void verifiesCurrentMasterPasswordBeforePortableExportSelection() throws Exception {
        var store = new YamlGlobalConfigStore(directory.resolve("export-verification-global.yaml"));
        var vault = new YamlSecretVault(store);
        char[] password = "correct-master".toCharArray();
        vault.create(password);
        var metadata = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve("export-verification-catalog.db"));
        var repository = new SqliteHostCatalogRepository(database);
        try (var workflow = new SqliteHostCatalogWorkflow(database, repository, vault, metadata)) {
            workflow.verifyVaultMasterPassword(password).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> workflow
                    .verifyVaultMasterPassword("wrong-master".toCharArray())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS));
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    @Test
    void exportVerificationUsesOnlyValidSavedMaterialWhenPasswordIsEmpty() throws Exception {
        var store = new YamlGlobalConfigStore(directory.resolve("remembered-export-global.yaml"));
        var vault = new YamlSecretVault(store);
        var metadata = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve("remembered-export-catalog.db"));
        var repository = new SqliteHostCatalogRepository(database);
        try (var workflow = new SqliteHostCatalogWorkflow(database, repository, vault, metadata)) {
            assertFalse(workflow.rememberedVaultUnlockConfigured().toCompletableFuture().get(5, TimeUnit.SECONDS));
            vault.create("correct-master".toCharArray(), true);
            workflow.verifyVaultMasterPassword(new char[0]).toCompletableFuture().get(5, TimeUnit.SECONDS);
            workflow.changeVaultMasterPassword(new char[0], "replacement-master".toCharArray(), false)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> workflow.verifyVaultMasterPassword(new char[0])
                    .toCompletableFuture().get(5, TimeUnit.SECONDS));
            vault.verifyMasterPassword("replacement-master".toCharArray());
        }
    }

    @Test
    void importsSecureCrtXmlAndCopiesReferencedPrivateKeyIntoTargetVault() throws Exception {
        Path key = directory.resolve("securecrt-id");
        byte[] keyMaterial = new byte[] {9, 18, 27, 36, 45, 54};
        Files.write(key, keyMaterial);
        Path xml = directory.resolve("securecrt.xml");
        Files.writeString(xml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <VanDyke version="3.0">
                  <key name="Credentials">
                    <key name="Deploy">
                      <string name="Username">deploy</string>
                      <string name="Identity Filename V2">%s</string>
                      <array name="SSH2 Authentications V2"><string>publickey</string></array>
                    </key>
                  </key>
                  <key name="Sessions">
                    <key name="Production">
                      <key name="Server">
                        <dword name="Is Session">00000001</dword>
                        <string name="Protocol Name">SSH2</string>
                        <string name="Hostname">server.example</string>
                        <string name="Username">deploy</string>
                        <dword name="[SSH2] Port">00000016</dword>
                        <string name="Credential Title">Deploy</string>
                      </key>
                    </key>
                  </key>
                </VanDyke>
                """.formatted(key.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")));

        var store = new YamlGlobalConfigStore(directory.resolve("securecrt-global.yaml"));
        var vault = new YamlSecretVault(store);
        char[] targetMaster = new char[12];
        java.util.Arrays.fill(targetMaster, 'x');
        vault.create(targetMaster);
        var metadata = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve("securecrt-catalog.db"));
        var repository = new SqliteHostCatalogRepository(database);
        try (var workflow = new SqliteHostCatalogWorkflow(database, repository, vault, metadata)) {
            var info = workflow.inspectSecureCrtArchive(xml).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, info.hosts());
            assertEquals(1, info.readableCertificates());

            var result = workflow.importSecureCrtArchive(xml, new char[0])
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, result.hosts());
            assertEquals(1, result.groups());
            assertEquals(1, result.credentials());
            SavedHost imported = repository.listHosts().getFirst();
            assertTrue(imported.credentialSecretId().isPresent());
            var importedCredential = metadata.find(imported.credentialSecretId().orElseThrow()).orElseThrow();
            try (var resolved = workflow.loadGlobalCredential(importedCredential)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS)) {
                assertArrayEquals(keyMaterial, resolved.copyPrivateKey());
                assertEquals(0, resolved.copySecret().length);
            }
            String persisted = Files.readString(directory.resolve("securecrt-global.yaml"));
            assertFalse(persisted.contains(java.util.Base64.getEncoder().encodeToString(keyMaterial)));
            assertFalse(persisted.contains(key.toString()));
        } finally {
            java.util.Arrays.fill(targetMaster, '\0');
        }
    }
}
