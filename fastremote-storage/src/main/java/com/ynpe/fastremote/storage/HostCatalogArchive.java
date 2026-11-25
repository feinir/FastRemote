// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.catalog.*;
import com.ynpe.fastremote.domain.connection.*;
import com.ynpe.fastremote.domain.credential.*;
import com.ynpe.fastremote.domain.vault.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.snakeyaml.engine.v2.api.*;
import org.snakeyaml.engine.v2.common.FlowStyle;

/** Single-file versioned YAML interchange for hosts and optionally encrypted credentials. */
public final class HostCatalogArchive {
    private static final String FORMAT = "FastRemote";
    private static final int VERSION = 2;
    private static final int MAX_RECORDS = 100_000;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MEMORY_KIB = 65_536;
    private static final int ITERATIONS = 3;
    private static final int PARALLELISM = 4;
    private static final byte[] VERIFIER = "FastRemote portable YAML export verifier v1"
            .getBytes(StandardCharsets.UTF_8);

    private final HostCatalogRepository repository;
    private final ManagedSecretVault vault;
    private final GlobalCredentialRepository credentials;
    private final SecureRandom random = new SecureRandom();
    private final Load loader = new Load(LoadSettings.builder()
            .setLabel("FastRemote portable export").setAllowDuplicateKeys(false)
            .setMaxAliasesForCollections(8).setCodePointLimit(MAX_BYTES).build());
    private final Dump dumper = new Dump(DumpSettings.builder()
            .setDefaultFlowStyle(FlowStyle.BLOCK).setIndent(2).setIndicatorIndent(0)
            .setDumpComments(false).build());

    public HostCatalogArchive(HostCatalogRepository repository, ManagedSecretVault vault,
            GlobalCredentialRepository credentials) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.vault = Objects.requireNonNull(vault, "vault");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
    }

    public ArchiveInfo inspect(Path source) throws IOException {
        return inspect(read(source));
    }

    public ArchiveInfo inspect(Map<String, Object> document) throws IOException {
        validateHeader(document);
        ArchiveInfo content = inspectDocument(document);
        Set<String> targetGroups = new HashSet<>();
        for (HostGroup item : repository.listGroups()) targetGroups.add(item.id().value().toString());
        Set<String> targetHosts = new HashSet<>();
        for (SavedHost item : repository.listHosts()) targetHosts.add(item.id().toString());
        Set<String> targetCredentials = new HashSet<>();
        for (GlobalCredential item : credentials.list()) targetCredentials.add(item.id().toString());
        int groupConflicts = idConflicts(list(document, "groups"), targetGroups, "group");
        int hostConflicts = idConflicts(list(document, "hosts"), targetHosts, "host");
        int credentialConflicts = idConflicts(list(document, "credentials"), targetCredentials, "credential");
        return new ArchiveInfo(content.credentials(), content.hostCredentials(),
                groupConflicts, hostConflicts, credentialConflicts);
    }

    public void exportTo(Path target, ExportSelection selection, char[] sourcePassword)
            throws IOException, VaultException {
        write(target, dumper.dumpToString(exportDocument(selection, sourcePassword)));
    }

    public Map<String, Object> exportDocument(ExportSelection selection, char[] sourcePassword)
            throws IOException, VaultException {
        Objects.requireNonNull(selection, "selection");
        char[] password = Objects.requireNonNull(sourcePassword, "sourcePassword").clone();
        boolean includeSecrets = selection.credentials() || selection.hostCredentials();
        VaultStatus original = vault.status();
        byte[] key = null;
        byte[] salt = null;
        RememberedVaultProtection remembered = null;
        try {
            if (includeSecrets) {
                if (original == VaultStatus.NOT_CREATED) {
                    throw new IOException("导出凭证前必须创建凭证库");
                }
                if (password.length == 0) {
                    remembered = vault.rememberedExportProtection();
                    key = remembered.copyKey();
                    salt = remembered.copySalt();
                    if (original == VaultStatus.LOCKED) vault.unlockRemembered();
                } else {
                    vault.verifyMasterPassword(password);
                    if (original == VaultStatus.LOCKED) vault.unlock(password);
                    salt = randomBytes(SALT_BYTES);
                    key = deriveKey(password, salt, MEMORY_KIB, ITERATIONS, PARALLELISM);
                }
            }
            return encode(selection, key, salt, remembered);
        } finally {
            if (original == VaultStatus.LOCKED && vault.status() == VaultStatus.UNLOCKED) vault.lock();
            Arrays.fill(password, '\0');
            clear(key);
            clear(salt);
            if (remembered != null) remembered.close();
        }
    }

    public ImportResult importFrom(Path source, char[] sourcePassword) throws IOException, VaultException {
        return importDocument(read(source), sourcePassword);
    }

    public ImportResult importDocument(Map<String, Object> document, char[] sourcePassword) throws IOException, VaultException {
        validateHeader(document);
        ArchiveInfo info = inspectDocument(document);
        char[] password = Objects.requireNonNull(sourcePassword, "sourcePassword").clone();
        byte[] key = null;
        List<ImportedSecret> imported = new ArrayList<>();
        try {
            if (info.hasSecrets()) {
                if (password.length == 0) throw new IOException("请输入导出文件的原主密码");
                if (vault.status() != VaultStatus.UNLOCKED) {
                    throw new IOException("导入凭证前必须先创建并解锁目标凭证库");
                }
                key = exportKey(password, map(document, "crypto"));
                imported = decodeSecrets(list(document, "credentials"), key);
            }
            return apply(document, imported);
        } finally {
            Arrays.fill(password, '\0');
            clear(key);
            imported.forEach(ImportedSecret::close);
        }
    }

    public void verifyPassword(Path source, char[] sourcePassword) throws IOException {
        verifyPassword(read(source), sourcePassword);
    }

    public void verifyPassword(Map<String, Object> document, char[] sourcePassword) throws IOException {
        validateHeader(document);
        if (!inspectDocument(document).hasSecrets()) return;
        char[] password = Objects.requireNonNull(sourcePassword, "sourcePassword").clone();
        byte[] key = null;
        try {
            if (password.length == 0) throw new IOException("请输入导出文件的原主密码");
            key = exportKey(password, map(document, "crypto"));
        } finally {
            Arrays.fill(password, '\0');
            clear(key);
        }
    }

    private Map<String, Object> encode(ExportSelection selection, byte[] key, byte[] salt, RememberedVaultProtection remembered)
            throws IOException, VaultException {
        List<HostGroup> groups = repository.listGroups();
        List<SavedHost> hosts = repository.listHosts();
        List<GlobalCredential> all = credentials.list();
        if (groups.size() > MAX_RECORDS || hosts.size() > MAX_RECORDS || all.size() > MAX_RECORDS) {
            throw new IOException("主机库内容过多，无法导出");
        }
        List<GlobalCredential> selected = all.stream().filter(item ->
                item.scope() == CredentialScope.REUSABLE ? selection.credentials() : selection.hostCredentials()).toList();
        Set<SecretId> selectedIds = new HashSet<>();
        selected.forEach(item -> selectedIds.add(item.id()));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("exportedAt", Instant.now().toEpochMilli());
        root.put("included", values("credentials", selection.credentials(),
                "hostCredentials", selection.hostCredentials()));
        root.put("crypto", key == null ? null : cryptoHeader(key, salt, remembered));
        root.put("groups", groups.stream().map(this::encodeGroup).toList());
        root.put("credentialGroups", credentials.listGroups().stream().map(group -> values(
                "id", group.id().toString(), "name", group.name(), "sort", group.sortOrder(),
                "color", group.color().name())).toList());
        root.put("hosts", hosts.stream().map(item -> encodeHost(item, selectedIds)).toList());
        List<Object> credentialEntries = new ArrayList<>();
        for (GlobalCredential credential : selected) credentialEntries.add(encodeCredential(credential, key));
        root.put("credentials", credentialEntries);
        return root;
    }

    private Map<String, Object> encodeCredential(GlobalCredential credential, byte[] key)
            throws VaultException, IOException {
        byte[] plaintext = vault.read(credential.id());
        byte[] nonce = randomBytes(NONCE_BYTES);
        byte[] ciphertext = null;
        SecretType type = credential.authenticationType() == AuthenticationType.PASSWORD
                ? SecretType.PASSWORD : SecretType.PRIVATE_KEY;
        try {
            ciphertext = encrypt(key, nonce, aad(credential.id(), type), plaintext);
            return values("id", credential.id().toString(), "name", credential.label(),
                    "username", credential.username(), "authentication", credential.authenticationType().name(),
                    "scope", credential.scope().name(), "ownerHost",
                    credential.ownerHostId().map(HostId::toString).orElse(null),
                    "group", credential.groupId().map(CredentialGroupId::toString).orElse(null),
                    "sort", credential.sortOrder(),
                    "secretType", type.name(), "nonce", b64(nonce), "ciphertext", b64(ciphertext));
        } catch (GeneralSecurityException failure) {
            throw new IOException("无法加密导出凭证", failure);
        } finally {
            clear(plaintext);
            clear(nonce);
            clear(ciphertext);
        }
    }

    private Map<String, Object> cryptoHeader(byte[] key, byte[] salt, RememberedVaultProtection remembered) throws IOException {
        byte[] nonce = randomBytes(NONCE_BYTES);
        byte[] ciphertext = null;
        try {
            ciphertext = encrypt(key, nonce, "FastRemote-export-v1|verifier".getBytes(StandardCharsets.UTF_8), VERIFIER);
            return values("algorithm", "ARGON2ID_AES_256_GCM", "salt", b64(salt),
                    "memoryKiB", remembered == null ? MEMORY_KIB : remembered.memoryKiB(),
                    "iterations", remembered == null ? ITERATIONS : remembered.iterations(),
                    "parallelism", remembered == null ? PARALLELISM : remembered.parallelism(),
                    "verifierNonce", b64(nonce), "verifierCiphertext", b64(ciphertext));
        } catch (GeneralSecurityException failure) {
            throw new IOException("无法建立导出密码验证信息", failure);
        } finally {
            clear(nonce);
            clear(ciphertext);
        }
    }

    private Map<String, Object> encodeGroup(HostGroup group) {
        return values("id", group.id().value().toString(), "parent",
                group.parentId().map(id -> id.value().toString()).orElse(null), "name", group.name(),
                "sort", group.sortOrder(), "color", group.color().name());
    }

    private Map<String, Object> encodeHost(SavedHost host, Set<SecretId> selectedIds) {
        return values("id", host.id().toString(), "group",
                host.groupId().map(id -> id.value().toString()).orElse(null), "name", host.name(),
                "host", host.profile().host().value(), "port", host.profile().port().value(),
                "username", host.profile().username().value(), "authentication",
                host.profile().authenticationType().name(), "keyPath",
                host.profile().privateKeyPath().map(Path::toString).orElse(null), "legacyAlgorithms",
                host.profile().legacyAlgorithmsEnabled(), "credential",
                host.credentialSecretId().filter(selectedIds::contains).map(SecretId::toString).orElse(null),
                "jumpHost", host.jumpHostId().map(HostId::toString).orElse(null),
                "favorite", host.favorite(), "sort", host.sortOrder(), "icon", host.icon().name(),
                "createdAt", host.createdAt().toEpochMilli(), "updatedAt", host.updatedAt().toEpochMilli());
    }

    private byte[] exportKey(char[] password, Map<String, Object> crypto) throws IOException {
        if (!"ARGON2ID_AES_256_GCM".equals(text(crypto, "algorithm"))) {
            throw new IOException("导出文件使用了不支持的加密算法");
        }
        byte[] salt = binary(crypto, "salt", SALT_BYTES, SALT_BYTES);
        byte[] nonce = binary(crypto, "verifierNonce", NONCE_BYTES, NONCE_BYTES);
        byte[] ciphertext = binary(crypto, "verifierCiphertext", 1, 4096);
        byte[] key = deriveKey(password, salt, bounded(crypto, "memoryKiB", 8, 1_048_576),
                bounded(crypto, "iterations", 1, 20), bounded(crypto, "parallelism", 1, 64));
        byte[] plaintext = null;
        try {
            plaintext = decrypt(key, nonce, "FastRemote-export-v1|verifier".getBytes(StandardCharsets.UTF_8), ciphertext);
            if (!MessageDigest.isEqual(VERIFIER, plaintext)) throw new IOException("导出文件主密码错误或文件已损坏");
            return key;
        } catch (AEADBadTagException invalid) {
            clear(key);
            throw new IOException("导出文件主密码错误或文件已损坏", invalid);
        } catch (GeneralSecurityException failure) {
            clear(key);
            throw new IOException("无法解密导出文件", failure);
        } finally {
            clear(salt); clear(nonce); clear(ciphertext); clear(plaintext);
        }
    }

    private List<ImportedSecret> decodeSecrets(List<Object> entries, byte[] key) throws IOException {
        List<ImportedSecret> result = new ArrayList<>();
        try {
            for (Object raw : entries) {
                Map<String, Object> item = objectMap(raw, "credential");
                SecretId oldId = SecretId.parse(text(item, "id"));
                SecretType type = enumeration(item, "secretType", SecretType.class);
                byte[] nonce = binary(item, "nonce", NONCE_BYTES, NONCE_BYTES);
                byte[] ciphertext = binary(item, "ciphertext", 1, MAX_BYTES);
                byte[] plaintext;
                try {
                    plaintext = decrypt(key, nonce, aad(oldId, type), ciphertext);
                } catch (AEADBadTagException invalid) {
                    throw new IOException("导出文件主密码错误或凭证数据已损坏", invalid);
                } catch (GeneralSecurityException failure) {
                    throw new IOException("无法解密导出凭证", failure);
                } finally { clear(nonce); clear(ciphertext); }
                result.add(new ImportedSecret(oldId, text(item, "name"), text(item, "username"),
                        enumeration(item, "authentication", AuthenticationType.class),
                        enumeration(item, "scope", CredentialScope.class), optionalText(item, "ownerHost"),
                        optionalText(item, "group"), integer(item, "sort"), type, plaintext));
                clear(plaintext);
            }
            return result;
        } catch (RuntimeException | IOException failure) {
            result.forEach(ImportedSecret::close);
            if (failure instanceof IOException io) throw io;
            throw new IOException("导出文件中的凭证记录无效", failure);
        }
    }

    private ImportResult apply(Map<String, Object> document, List<ImportedSecret> secrets)
            throws IOException, VaultException {
        List<Object> groupEntries = list(document, "groups");
        List<Object> credentialGroupEntries = list(document, "credentialGroups");
        List<Object> hostEntries = list(document, "hosts");
        if (groupEntries.size() > MAX_RECORDS || hostEntries.size() > MAX_RECORDS || secrets.size() > MAX_RECORDS) {
            throw new IOException("导出文件记录数量超过限制");
        }
        Map<String, GroupId> groupIds = new HashMap<>();
        Map<String, HostId> hostIds = new HashMap<>();
        Map<String, SecretId> credentialIds = new HashMap<>();
        Map<String, CredentialGroupId> credentialGroupIds = new HashMap<>();
        List<CredentialGroupId> insertedCredentialGroups = new ArrayList<>();
        List<GroupId> insertedGroups = new ArrayList<>();
        List<HostId> insertedHosts = new ArrayList<>();
        List<SecretId> insertedCredentials = new ArrayList<>();
        Set<GroupId> existingGroups = new HashSet<>();
        Map<GroupId, HostGroup> originalGroups = new HashMap<>();
        for (HostGroup item : repository.listGroups()) {
            existingGroups.add(item.id());
            originalGroups.put(item.id(), item);
        }
        Set<HostId> existingHosts = new HashSet<>();
        Map<HostId, SavedHost> originalHosts = new HashMap<>();
        for (SavedHost item : repository.listHosts()) {
            existingHosts.add(item.id());
            originalHosts.put(item.id(), item);
        }
        Map<SecretId, CredentialBackup> originalCredentials = new HashMap<>();
        for (ImportedSecret secret : secrets) {
            Optional<GlobalCredential> current = credentials.find(secret.oldId());
            if (current.isPresent()) {
                byte[] plaintext = vault.read(secret.oldId());
                originalCredentials.put(secret.oldId(), new CredentialBackup(current.get(),
                        current.get().authenticationType() == AuthenticationType.PASSWORD
                                ? SecretType.PASSWORD : SecretType.PRIVATE_KEY,
                        plaintext));
                clear(plaintext);
            }
        }
        try {
            for (Object raw : credentialGroupEntries) {
                Map<String, Object> item = objectMap(raw, "credentialGroup");
                CredentialGroupId id = CredentialGroupId.parse(text(item, "id"));
                if (credentials.listGroups().stream().noneMatch(value -> value.id().equals(id))) insertedCredentialGroups.add(id);
                credentials.saveGroup(new CredentialGroup(id, text(item, "name"), integer(item, "sort"),
                        enumeration(item, "color", GroupColor.class)));
                credentialGroupIds.put(text(item, "id"), id);
            }
            importGroups(groupEntries, groupIds, insertedGroups, existingGroups);
            importHosts(hostEntries, groupIds, hostIds, insertedHosts, existingHosts);
            updateHostRoutes(hostEntries, hostIds);
            for (ImportedSecret secret : secrets) {
                Optional<HostId> owner = secret.scope() == CredentialScope.HOST_BOUND
                        ? Optional.ofNullable(hostIds.get(secret.ownerHost().orElseThrow())) : Optional.empty();
                if (secret.scope() == CredentialScope.HOST_BOUND && owner.isEmpty()) {
                    throw new IOException("主机专属凭证引用了不存在的主机");
                }
                SecretId stableId = secret.oldId();
                if (credentials.find(stableId).isPresent()) {
                    vault.replace(stableId, secret.type(), secret.plaintext());
                } else {
                    vault.put(stableId, secret.type(), secret.plaintext());
                    insertedCredentials.add(stableId);
                }
                credentials.save(new GlobalCredential(stableId, secret.name(), secret.username(),
                        secret.authentication(), secret.scope(), owner,
                        secret.group().map(credentialGroupIds::get), secret.sortOrder()));
                credentialIds.put(secret.oldId().toString(), stableId);
            }
            updateHostCredentials(hostEntries, hostIds, credentialIds);
            return new ImportResult(hostEntries.size(), groupEntries.size(), secrets.size());
        } catch (Exception failure) {
            for (CredentialGroupId id : insertedCredentialGroups.reversed()) {
                try { credentials.deleteGroup(id); } catch (IOException ignored) { }
            }
            rollback(insertedHosts, insertedCredentials, insertedGroups,
                    originalHosts, originalCredentials, originalGroups);
            if (failure instanceof VaultException vaultFailure) throw vaultFailure;
            if (failure instanceof IOException io) throw io;
            throw new IOException("导入主机库失败", failure);
        } finally {
            originalCredentials.values().forEach(CredentialBackup::close);
        }
    }

    private void importGroups(List<Object> entries, Map<String, GroupId> ids, List<GroupId> inserted,
            Set<GroupId> existing) throws IOException {
        for (Object raw : entries) {
            Map<String, Object> item = objectMap(raw, "group");
            String oldId = text(item, "id");
            if (ids.put(oldId, new GroupId(java.util.UUID.fromString(oldId))) != null) {
                throw new IOException("导出文件包含重复的分组 ID");
            }
        }
        List<Map<String, Object>> remaining = new ArrayList<>();
        for (Object raw : entries) remaining.add(objectMap(raw, "group"));
        Set<String> completed = new HashSet<>();
        while (!remaining.isEmpty()) {
            boolean progressed = false;
            for (var iterator = remaining.iterator(); iterator.hasNext();) {
                Map<String, Object> item = iterator.next();
                String oldId = text(item, "id");
                Optional<String> parent = optionalText(item, "parent");
                if (parent.isPresent() && !ids.containsKey(parent.get())) throw new IOException("分组引用不存在的父分组");
                if (parent.isPresent() && !completed.contains(parent.get())) continue;
                GroupId stableId = ids.get(oldId);
                HostGroup group = new HostGroup(stableId, parent.map(ids::get),
                        uniqueGroupName(text(item, "name"), parent.map(ids::get), Optional.of(stableId)), integer(item, "sort"),
                        enumeration(item, "color", GroupColor.class));
                repository.saveGroup(group);
                if (!existing.contains(group.id())) inserted.add(group.id());
                completed.add(oldId);
                iterator.remove();
                progressed = true;
            }
            if (!progressed) throw new IOException("分组层级存在循环");
        }
    }

    private void importHosts(List<Object> entries, Map<String, GroupId> groupIds,
            Map<String, HostId> hostIds, List<HostId> inserted, Set<HostId> existing) throws IOException {
        for (Object raw : entries) {
            Map<String, Object> item = objectMap(raw, "host");
            String oldId = text(item, "id");
            HostId id = HostId.parse(oldId);
            if (hostIds.put(oldId, id) != null) throw new IOException("导出文件包含重复的主机 ID");
            Optional<String> oldGroup = optionalText(item, "group");
            Optional<GroupId> group = oldGroup.map(groupIds::get);
            if (oldGroup.isPresent() && group.isEmpty()) throw new IOException("主机引用不存在的分组");
            AuthenticationType authentication = enumeration(item, "authentication", AuthenticationType.class);
            ConnectionProfile profile = new ConnectionProfile(new Host(text(item, "host")),
                    new Port(integer(item, "port")), new Username(text(item, "username")), authentication,
                    optionalText(item, "keyPath").map(Path::of), optionalBool(item, "legacyAlgorithms", false));
            Instant createdAt = Instant.ofEpochMilli(longInteger(item, "createdAt"));
            Instant updatedAt = Instant.ofEpochMilli(longInteger(item, "updatedAt"));
            Optional<SavedHost> current = repository.findHost(id);
            if (current.isPresent()) {
                createdAt = current.get().createdAt();
                if (updatedAt.isBefore(createdAt)) updatedAt = createdAt;
            }
            SavedHost host = new SavedHost(id, group, uniqueHostName(text(item, "name"), group, Optional.of(id)),
                    profile, Optional.empty(), bool(item, "favorite"), integer(item, "sort"), createdAt, updatedAt,
                    enumeration(item, "icon", HostIcon.class));
            repository.saveHost(host);
            if (!existing.contains(id)) inserted.add(id);
        }
    }

    private void updateHostCredentials(List<Object> entries, Map<String, HostId> hosts,
            Map<String, SecretId> credentialIds) throws IOException {
        for (Object raw : entries) {
            Map<String, Object> item = objectMap(raw, "host");
            Optional<String> oldCredential = optionalText(item, "credential");
            if (oldCredential.isEmpty()) continue;
            SecretId credential = credentialIds.get(oldCredential.get());
            if (credential == null) throw new IOException("主机引用了未包含的凭证");
            SavedHost current = repository.findHost(hosts.get(text(item, "id"))).orElseThrow();
            repository.saveHost(current.withCredential(Optional.of(credential)));
        }
    }

    private void updateHostRoutes(List<Object> entries, Map<String, HostId> hosts) throws IOException {
        for (Object raw : entries) {
            Map<String, Object> item = objectMap(raw, "host");
            Optional<String> oldJump = optionalText(item, "jumpHost");
            if (oldJump.isEmpty()) continue;
            HostId jump = hosts.get(oldJump.orElseThrow());
            if (jump == null) throw new IOException("主机引用了未包含的跳板主机");
            SavedHost current = repository.findHost(hosts.get(text(item, "id"))).orElseThrow();
            repository.saveHost(current.withJumpHost(Optional.of(jump)));
        }
    }

    private String uniqueGroupName(
            String requested, Optional<GroupId> parent, Optional<GroupId> replacing) throws IOException {
        Set<String> names = new HashSet<>();
        for (HostGroup item : repository.listGroups()) {
            if (item.parentId().equals(parent) && !replacing.equals(Optional.of(item.id()))) names.add(lower(item.name()));
        }
        return uniqueName(requested, names);
    }

    private String uniqueHostName(
            String requested, Optional<GroupId> group, Optional<HostId> replacing) throws IOException {
        Set<String> names = new HashSet<>();
        for (SavedHost item : repository.listHosts()) {
            if (item.groupId().equals(group) && !replacing.equals(Optional.of(item.id()))) names.add(lower(item.name()));
        }
        return uniqueName(requested, names);
    }

    private static String uniqueName(String requested, Set<String> existing) {
        if (!existing.contains(lower(requested))) return requested;
        for (int index = 1; index < 100_000; index++) {
            String candidate = requested + "-" + index;
            if (!existing.contains(lower(candidate))) return candidate;
        }
        throw new IllegalStateException("无法为导入项目生成唯一名称");
    }

    private void rollback(List<HostId> hosts, List<SecretId> secretIds, List<GroupId> groups,
            Map<HostId, SavedHost> originalHosts,
            Map<SecretId, CredentialBackup> originalCredentials,
            Map<GroupId, HostGroup> originalGroups) {
        for (int i = hosts.size() - 1; i >= 0; i--) try { repository.deleteHost(hosts.get(i)); } catch (Exception ignored) { }
        for (int i = secretIds.size() - 1; i >= 0; i--) try { vault.delete(secretIds.get(i)); } catch (Exception ignored) { }
        for (CredentialBackup backup : originalCredentials.values()) {
            try {
                vault.replace(backup.metadata().id(), backup.type(), backup.plaintext());
                credentials.save(backup.metadata());
            } catch (Exception ignored) { }
        }
        for (SavedHost original : originalHosts.values()) {
            try { repository.saveHost(original); } catch (Exception ignored) { }
        }
        for (int i = groups.size() - 1; i >= 0; i--) try { repository.deleteGroup(groups.get(i)); } catch (Exception ignored) { }
        for (HostGroup original : originalGroups.values()) {
            try { repository.saveGroup(original); } catch (Exception ignored) { }
        }
    }

    private Map<String, Object> read(Path source) throws IOException {
        Path path = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) throw new IOException("所选导出文件不存在或超过 64 MiB 限制");
        try { return objectMap(loader.loadFromString(Files.readString(path, StandardCharsets.UTF_8)), "document"); }
        catch (RuntimeException malformed) { throw new IOException("所选文件不是有效的 FastRemote YAML 导出文件", malformed); }
    }

    private static void validateHeader(Map<String, Object> document) throws IOException {
        if (!FORMAT.equals(text(document, "format")) || integer(document, "version") != VERSION) {
            throw new IOException("不支持的 FastRemote 导出文件版本");
        }
        map(document, "included"); list(document, "groups"); list(document, "hosts");
        list(document, "credentialGroups"); list(document, "credentials");
    }

    private static ArchiveInfo inspectDocument(Map<String, Object> document) throws IOException {
        Map<String, Object> included = map(document, "included");
        return new ArchiveInfo(bool(included, "credentials"), bool(included, "hostCredentials"), 0, 0, 0);
    }

    private static int idConflicts(List<Object> entries, Set<String> targetIds, String recordName)
            throws IOException {
        Set<String> archiveIds = new HashSet<>();
        int conflicts = 0;
        for (Object raw : entries) {
            String id = text(objectMap(raw, recordName), "id");
            try { java.util.UUID.fromString(id); }
            catch (IllegalArgumentException invalid) { throw new IOException(recordName + " ID 不是有效 UUID", invalid); }
            if (!archiveIds.add(id)) throw new IOException("导出文件包含重复的 " + recordName + " ID");
            if (targetIds.contains(id)) conflicts++;
        }
        return conflicts;
    }

    private static void write(Path target, String yaml) throws IOException {
        Path absolute = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new IOException("导出目录不可用");
        byte[] encoded = yaml.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_BYTES) throw new IOException("导出文件超过 64 MiB 限制");
        Path temporary = Files.createTempFile(parent, ".fastremote-export-", ".tmp");
        try {
            Files.write(temporary, encoded, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); clear(encoded); }
    }

    private static byte[] deriveKey(char[] password, byte[] salt, int memory, int iterations, int parallelism) throws IOException {
        byte[] encoded = encodePassword(password);
        try {
            Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13).withSalt(salt).withMemoryAsKB(memory)
                    .withIterations(iterations).withParallelism(parallelism).build();
            Argon2BytesGenerator generator = new Argon2BytesGenerator(); generator.init(parameters);
            byte[] result = new byte[KEY_BYTES]; generator.generateBytes(encoded, result); return result;
        } catch (RuntimeException failure) { throw new IOException("Argon2id 导出密钥派生失败", failure); }
        finally { clear(encoded); }
    }

    private static byte[] encodePassword(char[] password) throws IOException {
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password));
            byte[] result = new byte[buffer.remaining()];
            buffer.get(result);
            if (buffer.hasArray()) clear(buffer.array());
            return result;
        } catch (CharacterCodingException invalid) {
            throw new IOException("主密码包含无法编码的字符", invalid);
        }
    }

    private static byte[] encrypt(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad); return cipher.doFinal(plaintext);
    }

    private static byte[] decrypt(byte[] key, byte[] nonce, byte[] aad, byte[] ciphertext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad); return cipher.doFinal(ciphertext);
    }

    private static byte[] aad(SecretId id, SecretType type) {
        return ("FastRemote-export-v1|credential|" + id + "|" + type).getBytes(StandardCharsets.UTF_8);
    }
    private byte[] randomBytes(int size) { byte[] result = new byte[size]; random.nextBytes(result); return result; }
    private static String b64(byte[] value) { return Base64.getEncoder().encodeToString(value); }
    private static String lower(String value) { return value.toLowerCase(Locale.ROOT); }
    private static void clear(byte[] value) { if (value != null) Arrays.fill(value, (byte) 0); }

    private static byte[] binary(Map<String, Object> values, String field, int min, int max) throws IOException {
        try { byte[] result = Base64.getDecoder().decode(text(values, field));
            if (result.length < min || result.length > max) throw new IOException("字段长度无效：" + field); return result;
        } catch (IllegalArgumentException invalid) { throw new IOException("字段不是有效的 Base64：" + field, invalid); }
    }
    private static Map<String, Object> objectMap(Object raw, String name) throws IOException {
        if (!(raw instanceof Map<?, ?> source)) throw new IOException(name + " 必须是 YAML 映射");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new IOException(name + " 包含非文本字段名"); result.put(key, entry.getValue());
        } return result;
    }
    private static Map<String, Object> map(Map<String, Object> values, String field) throws IOException { return objectMap(values.get(field), field); }
    private static List<Object> list(Map<String, Object> values, String field) throws IOException {
        if (!(values.get(field) instanceof List<?> items)) throw new IOException(field + " 必须是 YAML 列表"); return new ArrayList<>(items);
    }
    private static String text(Map<String, Object> values, String field) throws IOException {
        if (values.get(field) instanceof String text) return text; throw new IOException("缺少文本字段：" + field);
    }
    private static Optional<String> optionalText(Map<String, Object> values, String field) throws IOException {
        Object value = values.get(field); if (value == null) return Optional.empty();
        if (value instanceof String text && !text.isBlank()) return Optional.of(text); throw new IOException("字段必须是文本或 null：" + field);
    }
    private static int integer(Map<String, Object> values, String field) throws IOException {
        if (values.get(field) instanceof Number number) return number.intValue(); throw new IOException("缺少数字字段：" + field);
    }
    private static long longInteger(Map<String, Object> values, String field) throws IOException {
        if (values.get(field) instanceof Number number) return number.longValue();
        throw new IOException("缺少数字字段：" + field);
    }
    private static int bounded(Map<String, Object> values, String field, int min, int max) throws IOException {
        int value = integer(values, field); if (value < min || value > max) throw new IOException("字段超出支持范围：" + field); return value;
    }
    private static boolean bool(Map<String, Object> values, String field) throws IOException {
        if (values.get(field) instanceof Boolean result) return result; throw new IOException("缺少布尔字段：" + field);
    }
    private static boolean optionalBool(Map<String, Object> values, String field, boolean defaultValue) throws IOException {
        Object value = values.get(field);
        if (value == null) return defaultValue;
        if (value instanceof Boolean result) return result;
        throw new IOException("字段必须是布尔值：" + field);
    }
    private static <T extends Enum<T>> T enumeration(Map<String, Object> values, String field, Class<T> type) throws IOException {
        try { return Enum.valueOf(type, text(values, field)); }
        catch (IllegalArgumentException invalid) { throw new IOException("字段值不受支持：" + field, invalid); }
    }
    private static Map<String, Object> values(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]); return result;
    }

    public record ExportSelection(boolean credentials, boolean hostCredentials) { }
    public record ArchiveInfo(boolean credentials, boolean hostCredentials,
            int groupIdConflicts, int hostIdConflicts, int credentialIdConflicts) {
        public boolean hasSecrets() { return credentials || hostCredentials; }
        public boolean hasIdConflicts() {
            return groupIdConflicts > 0 || hostIdConflicts > 0 || credentialIdConflicts > 0;
        }
    }
    public record ImportResult(int hosts, int groups, int credentials) { }
    private record ImportedSecret(SecretId oldId, String name, String username, AuthenticationType authentication,
            CredentialScope scope, Optional<String> ownerHost, Optional<String> group, int sortOrder,
            SecretType type, byte[] plaintext) implements AutoCloseable {
        private ImportedSecret { plaintext = plaintext.clone(); }
        @Override public void close() { clear(plaintext); }
    }
    private record CredentialBackup(GlobalCredential metadata, SecretType type, byte[] plaintext)
            implements AutoCloseable {
        private CredentialBackup { plaintext = plaintext.clone(); }
        @Override public void close() { clear(plaintext); }
    }
}
