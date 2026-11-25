// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.vault.ManagedSecretVault;
import com.ynpe.fastremote.domain.vault.RememberedVaultProtection;
import com.ynpe.fastremote.domain.vault.SecretId;
import com.ynpe.fastremote.domain.vault.SecretType;
import com.ynpe.fastremote.domain.vault.VaultException;
import com.ynpe.fastremote.domain.vault.VaultFailureType;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/** Global YAML credential vault using Argon2id and per-record AES-256-GCM nonces. */
public final class YamlSecretVault implements ManagedSecretVault {
    private static final int MIN_MASTER_PASSWORD_LENGTH = 8;
    private static final int CRYPTO_VERSION = 1;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int DEFAULT_MEMORY_KIB = 65_536;
    private static final int DEFAULT_ITERATIONS = 3;
    private static final int DEFAULT_PARALLELISM = 4;
    private static final byte[] VERIFIER = ("FastRemote global vault verifier v1|"
            + "This fixed context deliberately spans enough bytes to detect the wrong master password, "
            + "a mismatched configuration, record substitution, or ciphertext corruption before any "
            + "credential is released to the connection workflow.").getBytes(StandardCharsets.UTF_8);

    private final YamlGlobalConfigStore store;
    private final SecureRandom random;
    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;
    private UnlockedKey unlocked;

    public YamlSecretVault(YamlGlobalConfigStore store) {
        this(store, new SecureRandom(), DEFAULT_MEMORY_KIB, DEFAULT_ITERATIONS, DEFAULT_PARALLELISM);
    }

    YamlSecretVault(
            YamlGlobalConfigStore store,
            SecureRandom random,
            int memoryKiB,
            int iterations,
            int parallelism) {
        this.store = Objects.requireNonNull(store, "store");
        this.random = Objects.requireNonNull(random, "random");
        if (memoryKiB < 8 || iterations < 1 || parallelism < 1) {
            throw new IllegalArgumentException("Invalid Argon2 parameters");
        }
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
    }

    @Override
    public synchronized VaultStatus status() throws VaultException {
        if (unlocked != null) {
            return VaultStatus.UNLOCKED;
        }
        try {
            Map<String, Object> document = store.read();
            Map<String, Object> settings = YamlGlobalConfigStore.requireMap(document, "settings");
            Map<String, Object> vault = YamlGlobalConfigStore.requireMap(document, "vault");
            boolean configured = Boolean.TRUE.equals(settings.get("masterPasswordConfigured"));
            if (configured != !vault.isEmpty()) {
                throw new IOException("Master-password state does not match global vault metadata");
            }
            boolean rememberConfigured = Boolean.TRUE.equals(settings.get("rememberUnlock"));
            boolean rememberedKeyPresent = vault.get("rememberedKek") instanceof String saved && !saved.isBlank();
            if (configured && rememberConfigured != rememberedKeyPresent) {
                throw new IOException("Remembered-unlock state does not match global vault metadata");
            }
            return configured ? VaultStatus.LOCKED : VaultStatus.NOT_CREATED;
        } catch (IOException failure) {
            throw storageFailure("Unable to read global vault status", failure);
        }
    }

    @Override
    public synchronized void create(char[] masterPassword) throws VaultException {
        create(masterPassword, false);
    }

    public synchronized void create(char[] masterPassword, boolean rememberUnlock) throws VaultException {
        createWithSecrets(masterPassword, rememberUnlock, Map.of());
    }

    synchronized void createWithSecrets(
            char[] masterPassword,
            boolean rememberUnlock,
            Map<SecretId, SecretMaterial> importedSecrets) throws VaultException {
        requireMasterPassword(masterPassword);
        Objects.requireNonNull(importedSecrets, "importedSecrets");
        if (status() != VaultStatus.NOT_CREATED) {
            throw new VaultException(VaultFailureType.ALREADY_CREATED, "The global vault already exists");
        }
        String vaultId = UUID.randomUUID().toString();
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] kek = deriveKey(masterPassword, salt, memoryKiB, iterations, parallelism);
        byte[] dek = randomBytes(KEY_BYTES);
        try {
            Map<String, Object> vault = buildVaultRecord(vaultId, salt, kek, dek, rememberUnlock);
            Map<String, Object> credentials = new LinkedHashMap<>();
            Map<String, Object> tokens = new LinkedHashMap<>();
            for (Map.Entry<SecretId, SecretMaterial> imported : importedSecrets.entrySet()) {
                Map<String, Object> destination = imported.getValue().type() == SecretType.TOKEN
                        ? tokens : credentials;
                destination.put(imported.getKey().toString(), encryptEntry(vaultId, dek, imported.getKey(),
                        imported.getValue().type(), imported.getValue().plaintext()));
            }
            store.update(document -> {
                try {
                    Map<String, Object> current = YamlGlobalConfigStore.requireMap(document, "vault");
                    if (!current.isEmpty()) {
                        throw new IllegalStateException("The global vault was created concurrently");
                    }
                    document.put("vault", vault);
                    document.put("credentials", credentials);
                    document.put("tokens", tokens);
                    settings(document).put("masterPasswordConfigured", true);
                    settings(document).put("rememberUnlock", rememberUnlock);
                    return null;
                } catch (IOException failure) {
                    throw new ConfigMutationException(failure);
                }
            });
            lock();
            unlocked = new UnlockedKey(vaultId, dek.clone());
        } catch (IOException failure) {
            throw storageFailure("Unable to create the global vault", unwrap(failure));
        } catch (GeneralSecurityException failure) {
            throw cryptoFailure("Unable to create the global vault", failure);
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(kek, (byte) 0);
            Arrays.fill(dek, (byte) 0);
        }
    }

    @Override
    public synchronized void unlock(char[] masterPassword) throws VaultException {
        Objects.requireNonNull(masterPassword, "masterPassword");
        VaultRecord record = loadVaultRecord();
        byte[] kek = deriveKey(masterPassword, record.salt(), record.memoryKiB(),
                record.iterations(), record.parallelism());
        try {
            unlockWithKek(record, kek);
        } finally {
            Arrays.fill(kek, (byte) 0);
            record.clear();
        }
    }

    @Override
    public synchronized void verifyMasterPassword(char[] currentPassword) throws VaultException {
        Objects.requireNonNull(currentPassword, "currentPassword");
        VaultRecord record = loadVaultRecord();
        byte[] kek = deriveKey(currentPassword, record.salt(), record.memoryKiB(),
                record.iterations(), record.parallelism());
        byte[] dek = null;
        try {
            dek = unwrapAndVerify(record, kek);
        } finally {
            Arrays.fill(kek, (byte) 0);
            if (dek != null) Arrays.fill(dek, (byte) 0);
            record.clear();
        }
    }

    @Override
    public synchronized boolean rememberedUnlockConfigured() throws VaultException {
        VaultRecord record = loadVaultRecord();
        try {
            return record.rememberedKek() != null;
        } finally {
            record.clear();
        }
    }

    @Override
    public synchronized RememberedVaultProtection rememberedExportProtection() throws VaultException {
        VaultRecord record = loadVaultRecord();
        byte[] dek = null;
        try {
            if (record.rememberedKek() == null) {
                throw new VaultException(VaultFailureType.LOCKED, "No remembered global vault key is available");
            }
            dek = unwrapAndVerify(record, record.rememberedKek());
            return new RememberedVaultProtection(record.rememberedKek(), record.salt(),
                    record.memoryKiB(), record.iterations(), record.parallelism());
        } finally {
            if (dek != null) Arrays.fill(dek, (byte) 0);
            record.clear();
        }
    }

    @Override
    public synchronized void unlockRemembered() throws VaultException {
        VaultRecord record = loadVaultRecord();
        byte[] remembered = record.rememberedKek();
        if (remembered == null) {
            record.clear();
            throw new VaultException(VaultFailureType.LOCKED, "No remembered global vault key is configured");
        }
        try {
            unlockWithKek(record, remembered);
        } catch (VaultException invalidSavedKey) {
            clearRememberedKey();
            throw invalidSavedKey;
        } finally {
            record.clear();
        }
    }

    @Override
    public synchronized void setRememberedUnlock(boolean remember, char[] currentPassword) throws VaultException {
        Objects.requireNonNull(currentPassword, "currentPassword");
        VaultRecord record = loadVaultRecord();
        byte[] kek = deriveKey(currentPassword, record.salt(), record.memoryKiB(),
                record.iterations(), record.parallelism());
        try {
            byte[] dek = unwrapAndVerify(record, kek);
            Arrays.fill(dek, (byte) 0);
            updateRememberedKey(remember ? kek : null);
        } finally {
            Arrays.fill(kek, (byte) 0);
            record.clear();
        }
    }

    @Override
    public synchronized void changeMasterPassword(
            char[] currentPassword,
            char[] newPassword,
            boolean rememberNewKey) throws VaultException {
        Objects.requireNonNull(currentPassword, "currentPassword");
        requireMasterPassword(newPassword);
        VaultRecord current = loadVaultRecord();
        byte[] oldKek = null;
        byte[] dek = null;
        byte[] newSalt = randomBytes(SALT_BYTES);
        byte[] newKek = null;
        try {
            if (currentPassword.length == 0) {
                if (current.rememberedKek() == null) {
                    throw new VaultException(VaultFailureType.LOCKED, "No remembered global vault key is available");
                }
                oldKek = current.rememberedKek().clone();
            } else {
                oldKek = deriveKey(currentPassword, current.salt(), current.memoryKiB(),
                        current.iterations(), current.parallelism());
            }
            dek = unwrapAndVerify(current, oldKek);
            newKek = deriveKey(newPassword, newSalt, memoryKiB, iterations, parallelism);
            Map<String, Object> replacement = buildVaultRecord(
                    current.vaultId(), newSalt, newKek, dek, rememberNewKey);
            store.update(document -> {
                document.put("vault", replacement);
                try {
                    settings(document).put("rememberUnlock", rememberNewKey);
                } catch (IOException failure) {
                    throw new ConfigMutationException(failure);
                }
                return null;
            });
            lock();
            unlocked = new UnlockedKey(current.vaultId(), dek.clone());
        } catch (IOException failure) {
            throw storageFailure("Unable to change the global vault master password", unwrap(failure));
        } catch (GeneralSecurityException failure) {
            throw cryptoFailure("Unable to change the global vault master password", failure);
        } finally {
            if (oldKek != null) Arrays.fill(oldKek, (byte) 0);
            if (newKek != null) Arrays.fill(newKek, (byte) 0);
            Arrays.fill(newSalt, (byte) 0);
            if (dek != null) Arrays.fill(dek, (byte) 0);
            current.clear();
        }
    }

    @Override
    public synchronized void lock() {
        if (unlocked != null) {
            unlocked.clear();
            unlocked = null;
        }
    }

    @Override
    public synchronized SecretId put(SecretType type, byte[] plaintext) throws VaultException {
        SecretId id = SecretId.create();
        put(id, type, plaintext);
        return id;
    }

    @Override
    public synchronized void put(SecretId id, SecretType type, byte[] plaintext) throws VaultException {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(plaintext, "plaintext");
        UnlockedKey key = requireUnlocked();
        byte[] nonce = randomBytes(NONCE_BYTES);
        try {
            byte[] ciphertext = encrypt(key.dek(), nonce, secretAad(key.vaultId(), id, type), plaintext);
            Map<String, Object> entry = encryptedEntry(type, nonce, ciphertext);
            store.update(document -> {
                try {
                    Map<String, Object> credentials = YamlGlobalConfigStore.requireMap(document, "credentials");
                    Map<String, Object> tokens = YamlGlobalConfigStore.requireMap(document, "tokens");
                    if (credentials.containsKey(id.toString()) || tokens.containsKey(id.toString())) {
                        throw new ConfigMutationException(new IOException("Credential already exists"));
                    }
                    secretSection(document, type).put(id.toString(), entry);
                    return null;
                } catch (IOException failure) {
                    throw new ConfigMutationException(failure);
                }
            });
            Arrays.fill(ciphertext, (byte) 0);
        } catch (IOException failure) {
            throw storageFailure("Unable to store the encrypted global credential", unwrap(failure));
        } catch (GeneralSecurityException failure) {
            throw cryptoFailure("Unable to encrypt the global credential", failure);
        } finally {
            Arrays.fill(nonce, (byte) 0);
        }
    }

    @Override
    public synchronized void replace(SecretId id, SecretType type, byte[] plaintext) throws VaultException {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(plaintext, "plaintext");
        UnlockedKey key = requireUnlocked();
        byte[] nonce = randomBytes(NONCE_BYTES);
        byte[] ciphertext = null;
        try {
            ciphertext = encrypt(key.dek(), nonce, secretAad(key.vaultId(), id, type), plaintext);
            Map<String, Object> entry = encryptedEntry(type, nonce, ciphertext);
            store.update(document -> {
                try {
                    Map<String, Object> currentSection = sectionContaining(document, id);
                    Object current = currentSection == null ? null : currentSection.get(id.toString());
                    if (!(current instanceof Map<?, ?> currentEntry)) {
                        throw new ConfigMutationException(new IOException("Credential to replace does not exist"));
                    }
                    Map<String, Object> destination = secretSection(document, type);
                    if (currentSection != destination) {
                        throw new ConfigMutationException(new IOException(
                                "Credential type cannot change between SSH credentials and API tokens"));
                    }
                    Map<String, Object> updated = new LinkedHashMap<>();
                    currentEntry.forEach((name, value) -> updated.put(String.valueOf(name), value));
                    updated.putAll(entry);
                    destination.put(id.toString(), updated);
                    return null;
                } catch (IOException failure) {
                    throw new ConfigMutationException(failure);
                }
            });
        } catch (ConfigMutationException failure) {
            throw storageFailure("Unable to replace the encrypted global credential", failure.getCause());
        } catch (IOException failure) {
            throw storageFailure("Unable to replace the encrypted global credential", unwrap(failure));
        } catch (GeneralSecurityException failure) {
            throw cryptoFailure("Unable to encrypt the global credential", failure);
        } finally {
            Arrays.fill(nonce, (byte) 0);
            if (ciphertext != null) Arrays.fill(ciphertext, (byte) 0);
        }
    }

    @Override
    public synchronized byte[] read(SecretId id) throws VaultException {
        Objects.requireNonNull(id, "id");
        UnlockedKey key = requireUnlocked();
        SecretRecord record = loadSecret(id);
        try {
            return decrypt(key.dek(), record.nonce(), secretAad(key.vaultId(), id, record.type()),
                    record.ciphertext());
        } catch (AEADBadTagException invalid) {
            throw invalidPasswordOrData(invalid);
        } catch (GeneralSecurityException failure) {
            throw cryptoFailure("Unable to decrypt the global credential", failure);
        } finally {
            record.clear();
        }
    }

    @Override
    public synchronized void delete(SecretId id) throws VaultException {
        Objects.requireNonNull(id, "id");
        requireUnlocked();
        try {
            store.update(document -> {
                try {
                    YamlGlobalConfigStore.requireMap(document, "credentials").remove(id.toString());
                    YamlGlobalConfigStore.requireMap(document, "tokens").remove(id.toString());
                    return null;
                } catch (IOException failure) {
                    throw new ConfigMutationException(failure);
                }
            });
        } catch (IOException failure) {
            throw storageFailure("Unable to delete the global credential", unwrap(failure));
        }
    }

    private void unlockWithKek(VaultRecord record, byte[] kek) throws VaultException {
        byte[] dek = unwrapAndVerify(record, kek);
        lock();
        unlocked = new UnlockedKey(record.vaultId(), dek);
    }

    private static byte[] unwrapAndVerify(VaultRecord record, byte[] kek) throws VaultException {
        try {
            byte[] dek = decrypt(kek, record.wrapNonce(), keyAad(record.vaultId()), record.wrappedDek());
            byte[] plaintext = null;
            try {
                plaintext = decrypt(dek, record.verifierNonce(), verifierAad(record.vaultId()),
                        record.verifierCiphertext());
                if (!MessageDigest.isEqual(VERIFIER, plaintext)) {
                    throw invalidPasswordOrData(null);
                }
                return dek;
            } catch (GeneralSecurityException | VaultException failure) {
                Arrays.fill(dek, (byte) 0);
                if (failure instanceof VaultException vaultFailure) throw vaultFailure;
                throw invalidPasswordOrData(failure);
            } finally {
                if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
            }
        } catch (AEADBadTagException invalid) {
            throw invalidPasswordOrData(invalid);
        } catch (GeneralSecurityException failure) {
            throw cryptoFailure("Unable to unlock the global vault", failure);
        }
    }

    private Map<String, Object> buildVaultRecord(
            String vaultId,
            byte[] salt,
            byte[] kek,
            byte[] dek,
            boolean remember) throws GeneralSecurityException {
        byte[] wrapNonce = randomBytes(NONCE_BYTES);
        byte[] verifierNonce = randomBytes(NONCE_BYTES);
        byte[] wrappedDek = null;
        byte[] encryptedVerifier = null;
        try {
            wrappedDek = encrypt(kek, wrapNonce, keyAad(vaultId), dek);
            encryptedVerifier = encrypt(dek, verifierNonce, verifierAad(vaultId), VERIFIER);
            Map<String, Object> vault = new LinkedHashMap<>();
            vault.put("cryptoVersion", CRYPTO_VERSION);
            vault.put("vaultId", vaultId);
            vault.put("kdfAlgorithm", "ARGON2ID");
            vault.put("kdfSalt", encode(salt));
            vault.put("kdfMemoryKiB", memoryKiB);
            vault.put("kdfIterations", iterations);
            vault.put("kdfParallelism", parallelism);
            vault.put("wrapNonce", encode(wrapNonce));
            vault.put("wrappedDek", encode(wrappedDek));
            vault.put("verifierNonce", encode(verifierNonce));
            vault.put("verifierCiphertext", encode(encryptedVerifier));
            vault.put("rememberedKek", remember ? encode(kek) : null);
            return vault;
        } finally {
            Arrays.fill(wrapNonce, (byte) 0);
            Arrays.fill(verifierNonce, (byte) 0);
            if (wrappedDek != null) Arrays.fill(wrappedDek, (byte) 0);
            if (encryptedVerifier != null) Arrays.fill(encryptedVerifier, (byte) 0);
        }
    }

    private Map<String, Object> encryptEntry(
            String vaultId,
            byte[] dek,
            SecretId id,
            SecretType type,
            byte[] plaintext) throws GeneralSecurityException {
        byte[] nonce = randomBytes(NONCE_BYTES);
        byte[] ciphertext = null;
        try {
            ciphertext = encrypt(dek, nonce, secretAad(vaultId, id, type), plaintext);
            return encryptedEntry(type, nonce, ciphertext);
        } finally {
            Arrays.fill(nonce, (byte) 0);
            if (ciphertext != null) Arrays.fill(ciphertext, (byte) 0);
        }
    }

    private static Map<String, Object> encryptedEntry(SecretType type, byte[] nonce, byte[] ciphertext) {
        long now = System.currentTimeMillis();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("cryptoVersion", CRYPTO_VERSION);
        entry.put("type", type.name());
        entry.put("nonce", encode(nonce));
        entry.put("ciphertext", encode(ciphertext));
        entry.put("createdAt", now);
        entry.put("updatedAt", now);
        return entry;
    }

    private VaultRecord loadVaultRecord() throws VaultException {
        try {
            Map<String, Object> vault = YamlGlobalConfigStore.requireMap(store.read(), "vault");
            if (vault.isEmpty()) {
                throw new VaultException(VaultFailureType.NOT_CREATED, "The global vault has not been created");
            }
            if (number(vault, "cryptoVersion") != CRYPTO_VERSION
                    || !"ARGON2ID".equals(text(vault, "kdfAlgorithm"))) {
                throw new VaultException(VaultFailureType.CRYPTOGRAPHY_FAILED,
                        "The global vault uses an unsupported cryptographic format");
            }
            Object remembered = vault.get("rememberedKek");
            return new VaultRecord(
                    text(vault, "vaultId"),
                    decode(text(vault, "kdfSalt")),
                    number(vault, "kdfMemoryKiB"),
                    number(vault, "kdfIterations"),
                    number(vault, "kdfParallelism"),
                    decode(text(vault, "wrapNonce")),
                    decode(text(vault, "wrappedDek")),
                    decode(text(vault, "verifierNonce")),
                    decode(text(vault, "verifierCiphertext")),
                    remembered instanceof String encoded ? decode(encoded) : null);
        } catch (IOException | IllegalArgumentException failure) {
            throw storageFailure("Unable to load the global vault", failure);
        }
    }

    private SecretRecord loadSecret(SecretId id) throws VaultException {
        try {
            Map<String, Object> document = store.read();
            Map<String, Object> section = sectionContaining(document, id);
            Object value = section == null ? null : section.get(id.toString());
            if (!(value instanceof Map<?, ?> raw)) {
                throw new VaultException(VaultFailureType.SECRET_NOT_FOUND,
                        "The requested global credential no longer exists");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) raw;
            if (number(entry, "cryptoVersion") != CRYPTO_VERSION) {
                throw new VaultException(VaultFailureType.CRYPTOGRAPHY_FAILED,
                        "The global credential uses an unsupported cryptographic format");
            }
            return new SecretRecord(
                    SecretType.valueOf(text(entry, "type")),
                    decode(text(entry, "nonce")),
                    decode(text(entry, "ciphertext")));
        } catch (IOException | IllegalArgumentException failure) {
            throw storageFailure("Unable to load the global credential", failure);
        }
    }

    private static Map<String, Object> secretSection(Map<String, Object> document, SecretType type)
            throws IOException {
        return YamlGlobalConfigStore.requireMap(document, type == SecretType.TOKEN ? "tokens" : "credentials");
    }

    private static Map<String, Object> sectionContaining(Map<String, Object> document, SecretId id)
            throws IOException {
        Map<String, Object> credentials = YamlGlobalConfigStore.requireMap(document, "credentials");
        Map<String, Object> tokens = YamlGlobalConfigStore.requireMap(document, "tokens");
        boolean credentialPresent = credentials.containsKey(id.toString());
        boolean tokenPresent = tokens.containsKey(id.toString());
        if (credentialPresent && tokenPresent) {
            throw new IOException("SecretId exists in both credentials and tokens");
        }
        return credentialPresent ? credentials : tokenPresent ? tokens : null;
    }

    private void updateRememberedKey(byte[] key) throws VaultException {
        try {
            store.update(document -> {
                try {
                    Map<String, Object> vault = YamlGlobalConfigStore.requireMap(document, "vault");
                    vault.put("rememberedKek", key == null ? null : encode(key));
                    settings(document).put("rememberUnlock", key != null);
                    return null;
                } catch (IOException failure) {
                    throw new ConfigMutationException(failure);
                }
            });
        } catch (IOException failure) {
            throw storageFailure("Unable to update remembered global vault state", unwrap(failure));
        }
    }

    private void clearRememberedKey() throws VaultException {
        updateRememberedKey(null);
    }

    private static Map<String, Object> settings(Map<String, Object> document) throws IOException {
        return YamlGlobalConfigStore.requireMap(document, "settings");
    }

    private UnlockedKey requireUnlocked() throws VaultException {
        if (unlocked == null) {
            throw new VaultException(VaultFailureType.LOCKED,
                    "Unlock the global vault before accessing credentials");
        }
        return unlocked;
    }

    private static void requireMasterPassword(char[] password) {
        Objects.requireNonNull(password, "masterPassword");
        if (password.length < MIN_MASTER_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("The master password must contain at least 8 characters");
        }
    }

    private static byte[] deriveKey(char[] password, byte[] salt, int memory, int iterations, int parallelism)
            throws VaultException {
        byte[] output = new byte[KEY_BYTES];
        Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(memory)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build();
        try {
            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(parameters);
            generator.generateBytes(password, output);
            return output;
        } catch (RuntimeException failure) {
            Arrays.fill(output, (byte) 0);
            throw new VaultException(VaultFailureType.CRYPTOGRAPHY_FAILED,
                    "Argon2id key derivation failed", failure);
        } finally {
            parameters.clear();
        }
    }

    private static byte[] encrypt(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(plaintext);
    }

    private static byte[] decrypt(byte[] key, byte[] nonce, byte[] aad, byte[] ciphertext)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(ciphertext);
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    private static byte[] keyAad(String vaultId) {
        return ("FastRemote|global-vault-key|" + vaultId + '|' + CRYPTO_VERSION)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] verifierAad(String vaultId) {
        return ("FastRemote|global-vault-verifier|" + vaultId + '|' + CRYPTO_VERSION)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] secretAad(String vaultId, SecretId id, SecretType type) {
        return ("FastRemote|global-credential|" + vaultId + '|' + id + '|' + type + '|' + CRYPTO_VERSION)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }

    private static String text(Map<String, Object> map, String key) throws IOException {
        Object value = map.get(key);
        if (value instanceof String text && !text.isBlank()) return text;
        throw new IOException("Global vault field '" + key + "' must be a non-empty string");
    }

    private static int number(Map<String, Object> map, String key) throws IOException {
        Object value = map.get(key);
        if (value instanceof Number number) return number.intValue();
        throw new IOException("Global vault field '" + key + "' must be an integer");
    }

    private static VaultException invalidPasswordOrData(Throwable cause) {
        return new VaultException(VaultFailureType.INVALID_PASSWORD_OR_CORRUPTED,
                "The master password is incorrect or the global configuration is damaged", cause);
    }

    private static VaultException storageFailure(String message, Throwable cause) {
        return new VaultException(VaultFailureType.STORAGE_FAILED, message, cause);
    }

    private static VaultException cryptoFailure(String message, GeneralSecurityException cause) {
        return new VaultException(VaultFailureType.CRYPTOGRAPHY_FAILED, message, cause);
    }

    private static Throwable unwrap(IOException failure) {
        return failure.getCause() instanceof ConfigMutationException mutation
                && mutation.getCause() != null ? mutation.getCause() : failure;
    }

    @Override
    public synchronized void close() {
        lock();
    }

    private record UnlockedKey(String vaultId, byte[] dek) {
        private void clear() {
            Arrays.fill(dek, (byte) 0);
        }
    }

    private record VaultRecord(
            String vaultId,
            byte[] salt,
            int memoryKiB,
            int iterations,
            int parallelism,
            byte[] wrapNonce,
            byte[] wrappedDek,
            byte[] verifierNonce,
            byte[] verifierCiphertext,
            byte[] rememberedKek) {
        private void clear() {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(wrapNonce, (byte) 0);
            Arrays.fill(wrappedDek, (byte) 0);
            Arrays.fill(verifierNonce, (byte) 0);
            Arrays.fill(verifierCiphertext, (byte) 0);
            if (rememberedKek != null) Arrays.fill(rememberedKek, (byte) 0);
        }
    }

    private record SecretRecord(SecretType type, byte[] nonce, byte[] ciphertext) {
        private void clear() {
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(ciphertext, (byte) 0);
        }
    }

    private static final class ConfigMutationException extends RuntimeException {
        private ConfigMutationException(IOException cause) {
            super(cause);
        }
    }

    record SecretMaterial(SecretType type, byte[] plaintext) {
        SecretMaterial {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(plaintext, "plaintext");
        }
    }
}
