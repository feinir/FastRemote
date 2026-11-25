// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.vault.SecretType;
import com.ynpe.fastremote.domain.vault.VaultException;
import com.ynpe.fastremote.domain.vault.VaultFailureType;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import com.ynpe.fastremote.domain.credential.CredentialGroup;
import com.ynpe.fastremote.domain.credential.CredentialGroupId;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlSecretVaultTest {
    @TempDir Path temporaryDirectory;

    @Test
    void acceptsEightCharacterMasterPasswordsAndRejectsShorterValues() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("minimum-password-global.yaml"));
        try (var vault = new YamlSecretVault(store, new SecureRandom(), 1024, 1, 1)) {
            assertThrows(IllegalArgumentException.class, () -> vault.create("1234567".toCharArray()));

            vault.create("12345678".toCharArray());

            assertEquals(VaultStatus.UNLOCKED, vault.status());
        }
    }

    @Test
    void createsUnlocksRemembersAndNeverWritesPlaintext() throws Exception {
        Path path = temporaryDirectory.resolve("global-config.yaml");
        char[] password = "correct horse battery staple".toCharArray();
        byte[] plaintext = "global-production-password".getBytes(StandardCharsets.UTF_8);
        try {
            var store = new YamlGlobalConfigStore(path);
            try (var vault = new YamlSecretVault(store, new SecureRandom(), 1024, 1, 1)) {
                assertEquals(VaultStatus.NOT_CREATED, vault.status());
                vault.create(password, true);
                var id = vault.put(SecretType.PASSWORD, plaintext);
                vault.lock();

                assertTrue(vault.rememberedUnlockConfigured());
                vault.unlockRemembered();
                assertArrayEquals(plaintext, vault.read(id));

                assertThrows(VaultException.class,
                        () -> vault.setRememberedUnlock(false, "wrong password".toCharArray()));
                assertTrue(vault.rememberedUnlockConfigured());
                vault.setRememberedUnlock(false, password);
                vault.lock();
                assertFalse(vault.rememberedUnlockConfigured());
                assertThrows(VaultException.class, vault::unlockRemembered);
                vault.unlock(password);
                assertArrayEquals(plaintext, vault.read(id));
            }
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(plaintext, (byte) 0);
        }
        String yaml = Files.readString(path, StandardCharsets.UTF_8);
        assertFalse(yaml.contains("global-production-password"));
        assertFalse(yaml.contains("correct horse battery staple"));
    }

    @Test
    void rejectsWrongPasswordAndVerifierTampering() throws Exception {
        Path path = temporaryDirectory.resolve("global-config.yaml");
        char[] password = "another strong master password".toCharArray();
        char[] wrong = "definitely the wrong password".toCharArray();
        try {
            var store = new YamlGlobalConfigStore(path);
            try (var vault = new YamlSecretVault(store, new SecureRandom(), 1024, 1, 1)) {
                vault.create(password);
                vault.lock();
                VaultException invalid = assertThrows(VaultException.class, () -> vault.unlock(wrong));
                assertEquals(VaultFailureType.INVALID_PASSWORD_OR_CORRUPTED, invalid.type());

                store.update(document -> {
                    try {
                        Map<String, Object> metadata = YamlGlobalConfigStore.requireMap(document, "vault");
                        byte[] ciphertext = Base64.getDecoder().decode((String) metadata.get("verifierCiphertext"));
                        ciphertext[0] ^= 1;
                        metadata.put("verifierCiphertext", Base64.getEncoder().encodeToString(ciphertext));
                        Arrays.fill(ciphertext, (byte) 0);
                        return null;
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                });
                VaultException tampered = assertThrows(VaultException.class, () -> vault.unlock(password));
                assertEquals(VaultFailureType.INVALID_PASSWORD_OR_CORRUPTED, tampered.type());
            }
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(wrong, '\0');
        }
    }

    @Test
    void changesMasterPasswordByRewrappingTheDataKey() throws Exception {
        char[] oldPassword = "old master password value".toCharArray();
        char[] newPassword = "new master password value".toCharArray();
        byte[] secret = {7, 6, 5, 4};
        try {
            var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("change.yaml"));
            try (var vault = new YamlSecretVault(store, new SecureRandom(), 1024, 1, 1)) {
                vault.create(oldPassword);
                var id = vault.put(SecretType.TOKEN, secret);
                vault.changeMasterPassword(oldPassword, newPassword, true);
                vault.lock();

                VaultException oldRejected = assertThrows(VaultException.class, () -> vault.unlock(oldPassword));
                assertEquals(VaultFailureType.INVALID_PASSWORD_OR_CORRUPTED, oldRejected.type());
                vault.unlock(newPassword);
                assertArrayEquals(secret, vault.read(id));
                vault.lock();
                vault.unlockRemembered();
                assertArrayEquals(secret, vault.read(id));
            }
        } finally {
            Arrays.fill(oldPassword, '\0');
            Arrays.fill(newPassword, '\0');
            Arrays.fill(secret, (byte) 0);
        }
    }

    @Test
    void savedMaterialAllowsPasswordResetWithoutChangingCredentialCiphertext() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("remembered-reset.yaml"));
        char[] oldPassword = "old-master".toCharArray();
        char[] newPassword = "new-master".toCharArray();
        try (var vault = new YamlSecretVault(store, new SecureRandom(), 1024, 1, 1)) {
            vault.create(oldPassword, true);
            var id = vault.put(SecretType.PASSWORD, new byte[] {1, 2, 3});
            Object before = store.read().get("credentials");
            vault.lock();
            vault.changeMasterPassword(new char[0], newPassword, false);
            assertEquals(before, store.read().get("credentials"));
            assertFalse(vault.rememberedUnlockConfigured());
            vault.lock();
            assertThrows(VaultException.class, () -> vault.unlock(oldPassword));
            vault.unlock(newPassword);
            assertArrayEquals(new byte[] {1, 2, 3}, vault.read(id));
            assertThrows(VaultException.class,
                    () -> vault.changeMasterPassword(new char[0], "other-master".toCharArray(), true));
        }
    }

    @Test
    void invalidSavedMaterialCannotResetPasswordEvenWhenAlreadyUnlocked() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("invalid-remembered.yaml"));
        try (var vault = new YamlSecretVault(store, new SecureRandom(), 1024, 1, 1)) {
            vault.create("original-master".toCharArray(), true);
            store.update(document -> {
                try {
                    YamlGlobalConfigStore.requireMap(document, "vault").put("rememberedKek",
                            Base64.getEncoder().encodeToString(new byte[32]));
                    return null;
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            var before = store.read();
            assertThrows(VaultException.class,
                    () -> vault.changeMasterPassword(new char[0], "replacement-master".toCharArray(), true));
            assertEquals(before, store.read());
            assertThrows(VaultException.class, vault::rememberedExportProtection);
            vault.verifyMasterPassword("original-master".toCharArray());
        }
    }

    @Test
    void attachesReusableNonSecretMetadataToAnEncryptedCredential() throws Exception {
        char[] password = "credential metadata password".toCharArray();
        byte[] secret = "not-in-plaintext".getBytes(StandardCharsets.UTF_8);
        try {
            var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("credential.yaml"));
            try (var vault = new YamlSecretVault(store, new SecureRandom(), 1024, 1, 1)) {
                vault.create(password);
                var id = vault.put(SecretType.PASSWORD, secret);
                var repository = new YamlGlobalCredentialRepository(store);
                GlobalCredential credential = new GlobalCredential(
                        id, "Production deploy", "deploy", AuthenticationType.PASSWORD,
                        com.ynpe.fastremote.domain.credential.CredentialScope.REUSABLE, Optional.empty());
                repository.save(credential);
                assertEquals(java.util.List.of(credential), repository.list());
                assertArrayEquals(secret, vault.read(id));
            }
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(secret, (byte) 0);
        }
        assertFalse(Files.readString(temporaryDirectory.resolve("credential.yaml"))
                .contains("not-in-plaintext"));
    }

    @Test
    void nonSshTokensDoNotInvalidateTheSshCredentialCatalog() throws Exception {
        char[] password = "credential token coexistence".toCharArray();
        byte[] token = "encrypted-api-token".getBytes(StandardCharsets.UTF_8);
        try {
            var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("token-and-credentials.yaml"));
            try (var vault = new YamlSecretVault(store, new SecureRandom(), 1024, 1, 1)) {
                vault.create(password);
                var tokenId = vault.put(SecretType.TOKEN, token);
                var repository = new YamlGlobalCredentialRepository(store);

                assertTrue(repository.list().isEmpty());
                assertTrue(repository.find(tokenId).isEmpty());
                assertArrayEquals(token, vault.read(tokenId));
                assertEquals(VaultStatus.UNLOCKED, vault.status());
                assertTrue(YamlGlobalConfigStore.requireMap(store.read(), "credentials").isEmpty());
                assertTrue(YamlGlobalConfigStore.requireMap(store.read(), "tokens")
                        .containsKey(tokenId.toString()));
            }
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(token, (byte) 0);
        }
        assertFalse(Files.readString(temporaryDirectory.resolve("token-and-credentials.yaml"))
                .contains("encrypted-api-token"));
    }

    @Test
    void legacyTokenMigrationPreservesTheSecretIdAndDecryptability() throws Exception {
        char[] password = "legacy token migration password".toCharArray();
        byte[] token = "token-survives-structural-migration".getBytes(StandardCharsets.UTF_8);
        Path path = temporaryDirectory.resolve("legacy-token-vault.yaml");
        try {
            var store = new YamlGlobalConfigStore(path);
            try (var vault = new YamlSecretVault(store, new SecureRandom(), 1024, 1, 1)) {
                vault.create(password);
                var tokenId = vault.put(SecretType.TOKEN, token);
                String legacyYaml = Files.readString(path)
                        .replace("schemaVersion: 2", "schemaVersion: 1")
                        .replace("credentials: {}\ntokens:", "credentials:");
                assertFalse(legacyYaml.contains("tokens:"));
                Files.writeString(path, legacyYaml);

                assertArrayEquals(token, vault.read(tokenId));
                assertTrue(YamlGlobalConfigStore.requireMap(store.read(), "credentials").isEmpty());
                assertTrue(YamlGlobalConfigStore.requireMap(store.read(), "tokens")
                        .containsKey(tokenId.toString()));
            }
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(token, (byte) 0);
        }
        assertFalse(Files.readString(path).contains("token-survives-structural-migration"));
    }

    @Test
    void storesColoredCredentialGroupsAndRootOrGroupedOrdering() throws Exception {
        char[] password = "credential groups password".toCharArray();
        byte[] secret = "group-secret".getBytes(StandardCharsets.UTF_8);
        try {
            var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("credential-groups.yaml"));
            try (var vault = new YamlSecretVault(store, new SecureRandom(), 1024, 1, 1)) {
                vault.create(password);
                var repository = new YamlGlobalCredentialRepository(store);
                var group = new CredentialGroup(CredentialGroupId.random(), "生产", 10, GroupColor.PURPLE);
                repository.saveGroup(group);
                var id = vault.put(SecretType.PASSWORD, secret);
                var credential = new GlobalCredential(id, "Deploy", "deploy", AuthenticationType.PASSWORD,
                        com.ynpe.fastremote.domain.credential.CredentialScope.REUSABLE, Optional.empty(),
                        Optional.of(group.id()), 20);
                repository.save(credential);
                assertEquals(java.util.List.of(group), repository.listGroups());
                assertEquals(java.util.List.of(credential), repository.list());
                assertThrows(java.io.IOException.class, () -> repository.deleteGroup(group.id()));
            }
        } finally { Arrays.fill(password, '\0'); Arrays.fill(secret, (byte) 0); }
    }
}
