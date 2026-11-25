// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostCatalogRepository;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import com.ynpe.fastremote.domain.credential.GlobalCredentialRepository;
import com.ynpe.fastremote.domain.credential.CredentialScope;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.vault.SecretType;
import com.ynpe.fastremote.domain.vault.ManagedSecretVault;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import com.ynpe.fastremote.domain.vault.VaultException;
import com.ynpe.fastremote.storage.FastRemoteDatabase;
import com.ynpe.fastremote.storage.HostCatalogArchive;
import com.ynpe.fastremote.storage.SecureCrtXmlImporter;
import com.ynpe.fastremote.storage.OpenSshConfigImporter;
import com.ynpe.fastremote.ui.HostCatalogSnapshot;
import com.ynpe.fastremote.ui.HostCatalogArchiveInfo;
import com.ynpe.fastremote.ui.HostCatalogImportResult;
import com.ynpe.fastremote.ui.HostCatalogWorkflow;
import com.ynpe.fastremote.ui.CredentialDraft;
import com.ynpe.fastremote.ui.ResolvedCredential;
import com.ynpe.fastremote.ui.SecureCrtImportInfo;
import com.ynpe.fastremote.ui.OpenSshImportInfo;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Instant;

public final class SqliteHostCatalogWorkflow implements HostCatalogWorkflow {
    private static final int PRIVATE_KEY_MAGIC = 0x46524331;
    private static final int MAX_PRIVATE_KEY_BYTES = 1024 * 1024;
    private static final int MAX_SECRET_TEXT_BYTES = 64 * 1024;
    private final FastRemoteDatabase database;
    private final boolean ownsStorage;
    private final HostCatalogRepository repository;
    private final ManagedSecretVault vault;
    private final GlobalCredentialRepository credentials;
    private final HostCatalogArchive archive;
    private ConfigurationArchive configurationArchive;
    private com.ynpe.fastremote.storage.YamlGlobalConfigStore configurationStore;
    private final SecureCrtXmlImporter secureCrtImporter;
    private final OpenSshConfigImporter openSshImporter;
    private final BoundedWorkflowExecutor executor;
    private final AtomicBoolean closed = new AtomicBoolean();

    public SqliteHostCatalogWorkflow(
            FastRemoteDatabase database,
            HostCatalogRepository repository,
            ManagedSecretVault vault,
            GlobalCredentialRepository credentials) {
        this(database, repository, vault, credentials, true);
    }

    public SqliteHostCatalogWorkflow(FastRemoteDatabase database, HostCatalogRepository repository,
            ManagedSecretVault vault, GlobalCredentialRepository credentials, boolean ownsStorage) {
        this.ownsStorage = ownsStorage;
        this.database = Objects.requireNonNull(database, "database");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.vault = Objects.requireNonNull(vault, "vault");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        archive = new HostCatalogArchive(repository, vault, credentials);
        secureCrtImporter = new SecureCrtXmlImporter();
        openSshImporter = new OpenSshConfigImporter();
        executor = new BoundedWorkflowExecutor("fastremote-catalog-", 32,
                BoundedWorkflowExecutor.ClosePolicy.CANCEL_PENDING,
                () -> "Host catalog is closed", () -> "Host catalog queue is full");
    }

    public void configureArchive(com.ynpe.fastremote.storage.YamlGlobalConfigStore store,
            com.ynpe.fastremote.domain.snippet.SnippetRepository snippets, SqliteSnippetWorkflow library) {
        configurationStore = store;
        configurationArchive = new ConfigurationArchive(database, store, archive, snippets, library, vault);
    }

    @Override
    public CompletionStage<Void> exportConfiguration(Path target,
            com.ynpe.fastremote.ui.ConfigurationSelection selection, char[] sourcePassword) {
        char[] password = sourcePassword.clone();
        return supply(() -> {
            try { configurationArchive.exportTo(target, selection, password); return null; }
            finally { Arrays.fill(password, '\0'); }
        }, () -> Arrays.fill(password, '\0'));
    }

    @Override
    public CompletionStage<Boolean> automaticUpdatePreference() {
        return supply(() -> new com.ynpe.fastremote.storage.YamlSoftwareUpdateRepository(configurationStore).load().automaticChecksEnabled());
    }

    @Override
    public CompletionStage<Void> resetProgramConfiguration() {
        return supply(() -> { configurationArchive.resetProgram(); return null; });
    }

    @Override
    public CompletionStage<HostCatalogSnapshot> loadCatalog(String query) {
        String safeQuery = Objects.requireNonNull(query, "query");
        return supply(() -> new HostCatalogSnapshot(
                repository.listGroups(),
                repository.searchHosts(safeQuery, 1_000),
                repository.listRecentConnections(100),
                credentials.list().stream()
                        .filter(credential -> credential.scope() == CredentialScope.REUSABLE).toList(),
                credentials.listGroups(),
                vault.status()));
    }

    @Override
    public CompletionStage<Void> recordSuccessfulConnection(
            Optional<HostId> hostId, com.ynpe.fastremote.domain.connection.ConnectionProfile profile) {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(profile, "profile");
        return supply(() -> {
            repository.recordSuccessfulConnection(hostId, profile, Instant.now());
            return null;
        });
    }

    @Override
    public CompletionStage<HostGroup> createGroup(String name, Optional<GroupId> parentId, GroupColor color) {
        String safeName = Objects.requireNonNull(name, "name");
        Optional<GroupId> safeParent = Objects.requireNonNull(parentId, "parentId");
        GroupColor safeColor = Objects.requireNonNull(color, "color");
        return supply(() -> {
            int nextSortOrder = repository.listGroups().stream()
                    .filter(existing -> existing.parentId().equals(safeParent))
                    .mapToInt(HostGroup::sortOrder)
                    .max().orElse(-10) + 10;
            HostGroup group = new HostGroup(GroupId.create(), safeParent, safeName, nextSortOrder, safeColor);
            repository.saveGroup(group);
            return group;
        });
    }

    @Override
    public CompletionStage<HostGroup> updateGroup(HostGroup group) {
        HostGroup safeGroup = Objects.requireNonNull(group, "group");
        return supply(() -> {
            repository.saveGroup(safeGroup);
            return safeGroup;
        });
    }

    @Override
    public CompletionStage<Void> moveGroup(GroupId id, Optional<GroupId> parentId, int targetIndex) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parentId, "parentId");
        return supply(() -> {
            repository.moveGroup(id, parentId, targetIndex);
            return null;
        });
    }

    @Override
    public CompletionStage<Void> deleteGroup(GroupId id) {
        Objects.requireNonNull(id, "id");
        return supply(() -> {
            repository.deleteGroup(id);
            return null;
        });
    }

    @Override
    public CompletionStage<Void> moveHost(HostId id, Optional<GroupId> groupId, int targetIndex) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(groupId, "groupId");
        return supply(() -> {
            repository.moveHost(id, groupId, targetIndex);
            return null;
        });
    }

    @Override
    public CompletionStage<SavedHost> saveHost(SavedHost host, Optional<CredentialDraft> credential) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(credential, "credential");
        CredentialInput credentialCopy = credential.map(CredentialInput::take).orElse(null);
        credential.ifPresent(CredentialDraft::close);
        return supply(() -> {
            try {
                return persistHost(host, credentialCopy);
            } finally {
                if (credentialCopy != null) credentialCopy.close();
            }
        }, () -> { if (credentialCopy != null) credentialCopy.close(); });
    }

    @Override
    public CompletionStage<SavedHost> copyHost(
            SavedHost source, SavedHost copy, Optional<CredentialDraft> replacementCredential) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(copy, "copy");
        Objects.requireNonNull(replacementCredential, "replacementCredential");
        if (source.id().equals(copy.id())) {
            throw new IllegalArgumentException("A copied host must have a new host ID");
        }
        CredentialInput replacement = replacementCredential.map(CredentialInput::take).orElse(null);
        replacementCredential.ifPresent(CredentialDraft::close);
        return supply(() -> {
            try {
                if (replacement != null) return persistHost(copy, replacement);
                Optional<GlobalCredential> sourceCredential = source.credentialSecretId().isEmpty()
                        ? Optional.empty() : credentials.find(source.credentialSecretId().orElseThrow());
                boolean preservesSourceCredential = source.credentialSecretId().isPresent()
                        && copy.credentialSecretId().equals(source.credentialSecretId());
                if (sourceCredential.isPresent()
                        && sourceCredential.get().scope() == CredentialScope.HOST_BOUND
                        && preservesSourceCredential) {
                    return persistHostWithCopiedBoundCredential(copy, sourceCredential.get());
                }
                return persistHost(copy, null);
            } finally {
                if (replacement != null) replacement.close();
            }
        }, () -> { if (replacement != null) replacement.close(); });
    }

    <T> CompletionStage<T> catalogTask(BoundedWorkflowExecutor.CheckedSupplier<T> action) {
        return supply(action);
    }

    SavedHost saveAutomationHost(SavedHost host, CredentialInput inline) throws Exception {
        var previous = repository.findHost(host.id()).flatMap(value -> value.credentialSecretId());
        var oldBound = previous.isPresent() && !previous.equals(host.credentialSecretId())
                ? credentials.find(previous.orElseThrow()).filter(value -> value.scope() == CredentialScope.HOST_BOUND
                        && value.ownerHostId().filter(host.id()::equals).isPresent())
                : Optional.<GlobalCredential>empty();
        if (oldBound.isPresent() && vault.status() != VaultStatus.UNLOCKED) {
            throw new IllegalStateException("Unlock the vault before replacing a host-bound credential");
        }
        SavedHost saved = persistHost(host, inline);
        if (oldBound.isPresent()) vault.delete(oldBound.orElseThrow().id());
        return saved;
    }

    private SavedHost persistHost(SavedHost host, CredentialInput credential) throws Exception {
        GlobalCredential created = null;
        boolean persisted = false;
        try {
            Optional<GlobalCredential> previous = host.credentialSecretId().flatMap(id -> {
                try {
                    return credentials.find(id);
                } catch (IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                }
            });
            SavedHost toSave = host;
            if (credential != null) {
                var replacementTarget = previous
                        .filter(value -> value.scope() == CredentialScope.HOST_BOUND)
                        .filter(value -> value.ownerHostId().filter(host.id()::equals).isPresent());
                String label = host.name() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.43d5b7a4846ee2e0");
                if (replacementTarget.isPresent()) {
                    return replaceHostCredential(host, credential, replacementTarget.orElseThrow(), label);
                }
                created = storeCredential(label, host.profile().username().value(), CredentialScope.HOST_BOUND,
                        Optional.of(host.id()), credential);
                toSave = withBoundCredential(host, created);
            }
            boolean removesPreviousBound = previous.isPresent()
                    && previous.get().scope() == CredentialScope.HOST_BOUND
                    && !toSave.credentialSecretId().equals(previous.map(GlobalCredential::id));
            if (removesPreviousBound && vault.status() != VaultStatus.UNLOCKED) {
                throw new IllegalStateException("Unlock the vault before replacing a host-bound credential");
            }
            repository.saveHost(toSave);
            persisted = true;
            if (removesPreviousBound) vault.delete(previous.get().id());
            return toSave;
        } finally {
            if (created != null && !persisted) removeCredentialAfterFailedSave(created.id());
        }
    }

    private SavedHost replaceHostCredential(SavedHost host, CredentialInput input, GlobalCredential previous,
            String label) throws Exception {
        var updated = new GlobalCredential(previous.id(), label, host.profile().username().value(),
                input.authenticationType(), CredentialScope.HOST_BOUND, Optional.of(host.id()), Optional.empty(), 0);
        SavedHost toSave = withBoundCredential(host, updated);
        try (var encoded = encodeCredential(input)) {
            byte[] oldPayload = vault.read(previous.id());
            try {
                vault.replace(updated.id(), encoded.type(), encoded.payload());
                try {
                    credentials.save(updated);
                    repository.saveHost(toSave);
                } catch (Exception failure) {
                    try {
                        var previousType = previous.embeddedPrivateKey() ? SecretType.PRIVATE_KEY : SecretType.PASSWORD;
                        vault.replace(previous.id(), previousType, oldPayload);
                        credentials.save(previous);
                    } catch (Exception rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                    throw failure;
                }
                return toSave;
            } finally {
                Arrays.fill(oldPayload, (byte) 0);
            }
        }
    }

    private static SavedHost withBoundCredential(SavedHost host, GlobalCredential credential) {
        var profile = host.profile();
        if (credential.embeddedPrivateKey()) {
            profile = new ConnectionProfile(profile.host(), profile.port(), profile.username(),
                    profile.authenticationType(), Optional.empty(), profile.legacyAlgorithmsEnabled());
        }
        return new SavedHost(host.id(), host.groupId(), host.name(), profile, host.jumpHostId(),
                Optional.of(credential.id()), host.favorite(), host.sortOrder(), host.createdAt(),
                host.updatedAt(), host.icon());
    }

    private SavedHost persistHostWithCopiedBoundCredential(
            SavedHost copy, GlobalCredential sourceCredential) throws Exception {
        byte[] plaintext = vault.read(sourceCredential.id());
        com.ynpe.fastremote.domain.vault.SecretId copiedId = null;
        boolean persisted = false;
        try {
            SecretType type = sourceCredential.authenticationType() == AuthenticationType.PASSWORD
                    ? SecretType.PASSWORD : SecretType.PRIVATE_KEY;
            copiedId = vault.put(type, plaintext);
            credentials.save(new GlobalCredential(
                    copiedId,
                    copy.name() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.43d5b7a4846ee2e0"),
                    copy.profile().username().value(), sourceCredential.authenticationType(),
                    CredentialScope.HOST_BOUND, Optional.of(copy.id()), Optional.empty(), 0));
            SavedHost saved = copy.withCredential(Optional.of(copiedId));
            repository.saveHost(saved);
            persisted = true;
            return saved;
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            if (copiedId != null && !persisted) removeCredentialAfterFailedSave(copiedId);
        }
    }

    private void removeCredentialAfterFailedSave(com.ynpe.fastremote.domain.vault.SecretId id) {
        try {
            vault.delete(id);
        } catch (Exception cleanupFailure) {
            System.getLogger(SqliteHostCatalogWorkflow.class.getName()).log(
                    System.Logger.Level.WARNING,
                    "Unable to remove a credential after the host save failed", cleanupFailure);
        }
    }

    @Override
    public CompletionStage<List<String>> deleteHost(SavedHost host) {
        Objects.requireNonNull(host, "host");
        return supply(() -> deleteStoredHost(host));
    }

    List<String> deleteStoredHost(SavedHost host) throws IOException, VaultException {
        Optional<GlobalCredential> metadata = host.credentialSecretId().isEmpty()
                ? Optional.empty() : credentials.find(host.credentialSecretId().orElseThrow());
        if (metadata.isPresent() && metadata.get().scope() == CredentialScope.HOST_BOUND
                && vault.status() != com.ynpe.fastremote.domain.vault.VaultStatus.UNLOCKED) {
            throw new IllegalStateException("Unlock the vault before deleting a host with a saved credential");
        }
        List<String> directHosts = repository.deleteHost(host.id());
        if (metadata.isPresent() && metadata.get().scope() == CredentialScope.HOST_BOUND) {
            vault.delete(metadata.get().id());
        }
        return directHosts;
    }

    @Override
    public CompletionStage<Void> createVault(char[] masterPassword, boolean rememberUnlock) {
        char[] copy = takeCopy(masterPassword);
        return supply(() -> {
            try {
                vault.create(copy);
                if (rememberUnlock) vault.setRememberedUnlock(true, copy);
                return null;
            } finally {
                Arrays.fill(copy, '\0');
            }
        }, () -> Arrays.fill(copy, '\0'));
    }

    @Override
    public CompletionStage<Void> unlockVault(char[] masterPassword) {
        char[] copy = takeCopy(masterPassword);
        return supply(() -> {
            try {
                vault.unlock(copy);
                return null;
            } finally {
                Arrays.fill(copy, '\0');
            }
        }, () -> Arrays.fill(copy, '\0'));
    }

    @Override
    public CompletionStage<Boolean> tryUnlockRememberedVault() {
        return supply(() -> {
            if (vault.status() != VaultStatus.LOCKED) return false;
            if (!vault.rememberedUnlockConfigured()) return false;
            vault.unlockRemembered();
            return true;
        });
    }

    @Override
    public CompletionStage<Boolean> rememberedVaultUnlockConfigured() {
        return supply(() -> vault.status() != VaultStatus.NOT_CREATED && vault.rememberedUnlockConfigured());
    }

    @Override
    public CompletionStage<Void> setRememberedVaultUnlock(boolean remember, char[] currentPassword) {
        char[] copy = takeCopy(currentPassword);
        return supply(() -> {
            try {
                vault.setRememberedUnlock(remember, copy);
                return null;
            } finally {
                Arrays.fill(copy, '\0');
            }
        }, () -> Arrays.fill(copy, '\0'));
    }

    @Override
    public CompletionStage<Void> changeVaultMasterPassword(
            char[] currentPassword,
            char[] newPassword,
            boolean rememberNewKey) {
        char[] currentCopy = takeCopy(currentPassword);
        char[] newCopy = takeCopy(newPassword);
        return supply(() -> {
            try {
                vault.changeMasterPassword(currentCopy, newCopy, rememberNewKey);
                return null;
            } finally {
                Arrays.fill(currentCopy, '\0');
                Arrays.fill(newCopy, '\0');
            }
        }, () -> {
            Arrays.fill(currentCopy, '\0');
            Arrays.fill(newCopy, '\0');
        });
    }

    @Override
    public void lockVault() {
        vault.lock();
    }

    @Override
    public CompletionStage<ResolvedCredential> loadCredential(SavedHost host) {
        Objects.requireNonNull(host, "host");
        return supply(() -> {
            var id = host.credentialSecretId().orElseThrow(
                    () -> new IllegalStateException("The saved host has no stored credential"));
            byte[] encoded = vault.read(id);
            try {
                return resolve(host.profile().authenticationType(), encoded);
            } finally {
                Arrays.fill(encoded, (byte) 0);
            }
        });
    }

    @Override
    public CompletionStage<ResolvedCredential> loadGlobalCredential(GlobalCredential credential) {
        Objects.requireNonNull(credential, "credential");
        return supply(() -> {
            byte[] encoded = vault.read(credential.id());
            try {
                return resolve(credential.authenticationType(), encoded);
            } finally {
                Arrays.fill(encoded, (byte) 0);
            }
        });
    }

    @Override
    public CompletionStage<GlobalCredential> createReusableCredential(
            String label, String username, CredentialDraft credential) {
        String safeLabel = Objects.requireNonNull(label, "label");
        String safeUsername = Objects.requireNonNull(username, "username");
        CredentialInput input = CredentialInput.take(Objects.requireNonNull(credential, "credential"));
        credential.close();
        return supply(() -> {
            try {
                return storeCredential(safeLabel, safeUsername, CredentialScope.REUSABLE, Optional.empty(), input);
            } finally {
                input.close();
            }
        }, input::close);
    }

    @Override
    public CompletionStage<GlobalCredential> updateReusableCredential(
            GlobalCredential credential,
            String label,
            String username,
            Optional<CredentialDraft> replacement) {
        GlobalCredential current = Objects.requireNonNull(credential, "credential");
        if (current.scope() != CredentialScope.REUSABLE) {
            throw new IllegalArgumentException("Host-bound credentials are managed with their host");
        }
        String safeLabel = Objects.requireNonNull(label, "label");
        String safeUsername = Objects.requireNonNull(username, "username");
        Objects.requireNonNull(replacement, "replacement");
        CredentialInput input = replacement.map(CredentialInput::take).orElse(null);
        replacement.ifPresent(CredentialDraft::close);
        return supply(() -> {
            try {
                return updateStoredCredential(current, safeLabel, safeUsername, current.groupId(), input);
            } finally {
                if (input != null) input.close();
            }
        }, () -> { if (input != null) input.close(); });
    }

    GlobalCredential saveAutomationCredential(Optional<GlobalCredential> current, String label, String username,
            Optional<com.ynpe.fastremote.domain.credential.CredentialGroupId> groupId,
            CredentialInput input) throws Exception {
        if (current.isPresent()) return updateStoredCredential(current.orElseThrow(), label, username, groupId, input);
        return storeCredential(label, username, CredentialScope.REUSABLE, Optional.empty(),
                Objects.requireNonNull(input), groupId);
    }

    private GlobalCredential updateStoredCredential(GlobalCredential current, String label, String username,
            Optional<com.ynpe.fastremote.domain.credential.CredentialGroupId> groupId, CredentialInput input)
            throws Exception {
        AuthenticationType authentication = input == null ? current.authenticationType() : input.authenticationType();
        var updated = new GlobalCredential(current.id(), label, username, authentication,
                current.scope(), current.ownerHostId(), groupId, current.sortOrder());
        if (credentials.list().stream().anyMatch(entry -> !entry.id().equals(updated.id())
                && entry.label().equalsIgnoreCase(updated.label()))) {
            throw new IOException("Credential name already exists");
        }
        var attachedHosts = repository.listHosts().stream()
                .filter(host -> host.credentialSecretId().equals(Optional.of(updated.id()))).toList();
        if (!attachedHosts.isEmpty()) new com.ynpe.fastremote.domain.connection.Username(username);
        if (input != null) {
            EncodedCredential encoded = encodeCredential(input);
            try {
                vault.replace(current.id(), encoded.type(), encoded.payload());
            } finally {
                encoded.close();
            }
        }
        credentials.save(updated);
        for (SavedHost host : attachedHosts) {
            var profile = host.profile();
            var updatedProfile = new com.ynpe.fastremote.domain.connection.ConnectionProfile(
                    profile.host(), profile.port(), new com.ynpe.fastremote.domain.connection.Username(username),
                    authentication, Optional.empty(), profile.legacyAlgorithmsEnabled());
            if (updatedProfile.equals(profile)) continue;
            repository.saveHost(new SavedHost(host.id(), host.groupId(), host.name(), updatedProfile,
                    host.jumpHostId(), host.credentialSecretId(), host.favorite(), host.sortOrder(),
                    host.createdAt(), Instant.now(), host.icon()));
        }
        return updated;
    }

    @Override
    public CompletionStage<com.ynpe.fastremote.domain.credential.CredentialGroup> saveCredentialGroup(
            com.ynpe.fastremote.domain.credential.CredentialGroup group) {
        return supply(() -> { credentials.saveGroup(Objects.requireNonNull(group)); return group; });
    }

    @Override
    public CompletionStage<Void> deleteCredentialGroup(
            com.ynpe.fastremote.domain.credential.CredentialGroupId id) {
        return supply(() -> { credentials.deleteGroup(Objects.requireNonNull(id)); return null; });
    }

    @Override
    public CompletionStage<GlobalCredential> moveReusableCredential(GlobalCredential credential,
            Optional<com.ynpe.fastremote.domain.credential.CredentialGroupId> groupId, int sortOrder) {
        return supply(() -> {
            GlobalCredential moved = new GlobalCredential(credential.id(), credential.label(), credential.username(),
                    credential.authenticationType(), credential.scope(), credential.ownerHostId(), groupId, sortOrder);
            credentials.save(moved);
            return moved;
        });
    }

    @Override
    public CompletionStage<Void> deleteReusableCredential(GlobalCredential credential) {
        GlobalCredential safe = Objects.requireNonNull(credential, "credential");
        return supply(() -> { deleteStoredCredential(safe); return null; });
    }

    void deleteStoredCredential(GlobalCredential safe) throws IOException, VaultException {
        if (safe.scope() != CredentialScope.REUSABLE) {
            throw new IllegalArgumentException("Host-bound credentials are managed with their host");
        }
        boolean referenced = repository.listHosts().stream()
                .anyMatch(host -> host.credentialSecretId().equals(Optional.of(safe.id())));
        if (referenced) throw new IllegalStateException("Credential is still referenced by a saved host");
        vault.delete(safe.id());
    }

    @Override
    public CompletionStage<Void> verifyVaultMasterPassword(char[] currentPassword) {
        char[] password = Objects.requireNonNull(currentPassword, "currentPassword").clone();
        return supply(() -> {
            try {
                if (password.length == 0) {
                    try (var protection = vault.rememberedExportProtection()) {
                        // The storage layer validates the saved key against the vault verifier.
                    }
                } else {
                    vault.verifyMasterPassword(password);
                }
                return null;
            } finally {
                Arrays.fill(password, '\0');
            }
        }, () -> Arrays.fill(password, '\0'));
    }

    @Override
    public CompletionStage<Void> exportCatalog(
            Path target, boolean includeCredentials, boolean includeHostCredentials, char[] sourceMasterPassword) {
        Objects.requireNonNull(target, "target");
        char[] password = Objects.requireNonNull(sourceMasterPassword, "sourceMasterPassword").clone();
        return supply(() -> {
            try {
                archive.exportTo(target,
                        new HostCatalogArchive.ExportSelection(includeCredentials, includeHostCredentials), password);
                return null;
            } finally {
                Arrays.fill(password, '\0');
            }
        }, () -> Arrays.fill(password, '\0'));
    }

    @Override
    public CompletionStage<HostCatalogArchiveInfo> inspectCatalogArchive(Path source) {
        Objects.requireNonNull(source, "source");
        return supply(() -> {
            var info = configurationArchive == null ? archive.inspect(source) : configurationArchive.inspect(source);
            return new HostCatalogArchiveInfo(info.credentials(), info.hostCredentials(),
                    info.groupIdConflicts(), info.hostIdConflicts(), info.credentialIdConflicts());
        });
    }

    @Override
    public CompletionStage<Void> verifyCatalogArchivePassword(Path source, char[] sourceMasterPassword) {
        Objects.requireNonNull(source, "source");
        char[] password = Objects.requireNonNull(sourceMasterPassword, "sourceMasterPassword").clone();
        return supply(() -> {
            try {
                if (configurationArchive == null) archive.verifyPassword(source, password);
                else configurationArchive.verifyPassword(source, password);
                return null;
            } finally {
                Arrays.fill(password, '\0');
            }
        }, () -> Arrays.fill(password, '\0'));
    }

    @Override
    public CompletionStage<HostCatalogImportResult> importCatalog(Path source, char[] sourceMasterPassword) {
        Objects.requireNonNull(source, "source");
        char[] password = Objects.requireNonNull(sourceMasterPassword, "sourceMasterPassword").clone();
        return supply(() -> {
            try {
                var result = configurationArchive == null ? archive.importFrom(source, password) : configurationArchive.importFrom(source, password);
                return new HostCatalogImportResult(result.hosts(), result.groups(), result.credentials());
            } finally {
                Arrays.fill(password, '\0');
            }
        }, () -> Arrays.fill(password, '\0'));
    }

    @Override
    public CompletionStage<SecureCrtImportInfo> inspectSecureCrtArchive(Path source) {
        Objects.requireNonNull(source, "source");
        return supply(() -> {
            var info = secureCrtImporter.inspect(source);
            return new SecureCrtImportInfo(info.hosts(), info.groups(), info.sharedCredentials(),
                    info.encryptedPasswords(), info.certificateReferences(), info.readableCertificates(),
                    info.missingCertificates(), info.skippedHosts());
        });
    }

    @Override
    public CompletionStage<Void> verifySecureCrtArchivePassword(Path source, char[] sourcePassphrase) {
        Objects.requireNonNull(source, "source");
        char[] password = Objects.requireNonNull(sourcePassphrase, "sourcePassphrase").clone();
        return supply(() -> {
            try {
                secureCrtImporter.verifyPassphrase(source, password);
                return null;
            } finally {
                Arrays.fill(password, '\0');
            }
        }, () -> Arrays.fill(password, '\0'));
    }

    @Override
    public CompletionStage<HostCatalogImportResult> importSecureCrtArchive(Path source, char[] sourcePassphrase) {
        Objects.requireNonNull(source, "source");
        char[] password = Objects.requireNonNull(sourcePassphrase, "sourcePassphrase").clone();
        return supply(() -> {
            try (var bundle = secureCrtImporter.read(source, password,
                    com.ynpe.fastremote.i18n.Texts.text("credential.hostSpecificSuffix"))) {
                return persistSecureCrt(bundle);
            } finally {
                Arrays.fill(password, '\0');
            }
        }, () -> Arrays.fill(password, '\0'));
    }

    @Override
    public CompletionStage<OpenSshImportInfo> inspectOpenSshConfig(Path source) {
        Objects.requireNonNull(source, "source");
        return supply(() -> {
            var info = openSshImporter.inspect(source);
            return new OpenSshImportInfo(info.hosts(), info.readableIdentities(), info.encryptedIdentities(),
                    info.missingIdentities(), info.proxyHosts(), info.invalidHosts(), info.wildcardPatterns(),
                    info.ignoredOptions(), info.configFiles(), info.identities().stream()
                            .map(value -> new OpenSshImportInfo.Identity(value.path(), value.encrypted())).toList());
        });
    }

    @Override
    public CompletionStage<HostCatalogImportResult> importOpenSshConfig(
            Path source, Map<Path, char[]> encryptedKeyPassphrases) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(encryptedKeyPassphrases, "encryptedKeyPassphrases");
        Map<Path, char[]> passphrases = new HashMap<>();
        encryptedKeyPassphrases.forEach((path, value) -> passphrases.put(path, value.clone()));
        return supply(() -> {
            try (var bundle = openSshImporter.read(source, passphrases)) {
                return persistOpenSsh(bundle);
            } finally {
                passphrases.values().forEach(value -> Arrays.fill(value, '\0'));
            }
        }, () -> passphrases.values().forEach(value -> Arrays.fill(value, '\0')));
    }

    @Override
    public CompletionStage<com.ynpe.fastremote.domain.host.HostImportPlan> inspectThirdParty(
            com.ynpe.fastremote.domain.host.HostImportSource source, Optional<Path> path) {
        return supply(() -> {
            Path selected = path.orElseGet(() -> ThirdPartyLocalSources.defaultPath(source));
            if (source == com.ynpe.fastremote.domain.host.HostImportSource.TERMIUS) {
                byte[] key = TermiusLocalKey.read();
                try { return new com.ynpe.fastremote.storage.TermiusHostImporter().read(selected, key); }
                finally { Arrays.fill(key, (byte) 0); }
            }
            if (source == com.ynpe.fastremote.domain.host.HostImportSource.PUTTY && path.isEmpty()
                    && System.getProperty("os.name", "").startsWith("Windows")) {
                return ThirdPartyLocalSources.readPutty();
            }
            return new com.ynpe.fastremote.storage.ThirdPartyHostImporter().read(source, selected);
        });
    }

    @Override
    public CompletionStage<Void> attachImportKey(com.ynpe.fastremote.domain.host.HostImportPlan plan,
            String entryId,Path file,char[] passphrase) {
        char[] password=passphrase.clone();
        return supply(() -> {
            char[] key=new char[0];
            try {
                key=com.ynpe.fastremote.storage.ImportedPrivateKey.read(file);
                try(var material=new com.ynpe.fastremote.domain.host.HostImportPlan.Secret(password,key)) {
                    // Validate key and passphrase with the same parser used for an SSH connection.
                    byte[] encoded=encode(key);
                    try {
                        com.ynpe.fastremote.transport.ssh.PrivateKeyImportValidator.validate(encoded,password);
                    } finally {Arrays.fill(encoded,(byte)0);}
                    plan.attachSecret(entryId,material);
                    return null;
                }
            } finally {Arrays.fill(key,'\0');Arrays.fill(password,'\0');}
        }, () -> Arrays.fill(password,'\0'));
    }

    @Override
    public CompletionStage<com.ynpe.fastremote.ui.ThirdPartyImportResult> importThirdParty(
            com.ynpe.fastremote.domain.host.HostImportPlan plan, Set<String> selected,
            Map<String,String> usernames, boolean skipDuplicates) {
        Set<String> selection = Set.copyOf(selected);
        Map<String,String> users = Map.copyOf(usernames);
        return supply(() -> new ThirdPartyCatalogImport(repository, vault, credentials)
                .apply(plan, selection, users, skipDuplicates));
    }

    private HostCatalogImportResult persistOpenSsh(OpenSshConfigImporter.ImportBundle bundle) throws Exception {
        List<HostId> insertedHosts = new ArrayList<>();
        List<com.ynpe.fastremote.domain.vault.SecretId> insertedSecrets = new ArrayList<>();
        List<GroupId> insertedGroups = new ArrayList<>();
        List<HostGroup> existingGroups = new ArrayList<>(repository.listGroups());
        List<SavedHost> existingHosts = new ArrayList<>(repository.listHosts());
        Set<String> credentialLabels = new HashSet<>();
        credentials.list().stream().filter(value -> value.scope() == CredentialScope.REUSABLE)
                .map(GlobalCredential::label).map(SqliteHostCatalogWorkflow::normalized).forEach(credentialLabels::add);
        Map<String, GlobalCredential> importedCredentials = new HashMap<>();
        try {
            Optional<GroupId> root = Optional.empty();
            String groupName = uniqueGroupName("OpenSSH", root, existingGroups);
            HostGroup group = new HostGroup(GroupId.create(), root, groupName,
                    existingGroups.stream().filter(value -> value.parentId().isEmpty())
                            .mapToInt(HostGroup::sortOrder).max().orElse(-10) + 10,
                    GroupColor.BLUE);
            repository.saveGroup(group);
            insertedGroups.add(group.id());
            existingGroups.add(group);

            for (var sourceCredential : bundle.credentials()) {
                String label = uniqueName(sourceCredential.label(), credentialLabels);
                credentialLabels.add(normalized(label));
                char[] passphrase = sourceCredential.copyPassphrase();
                try (var input = new CredentialInput(AuthenticationType.PRIVATE_KEY, passphrase,
                        Optional.of(sourceCredential.privateKeySource()))) {
                    GlobalCredential stored = storeCredential(label, sourceCredential.username(),
                            CredentialScope.REUSABLE, Optional.empty(), input);
                    importedCredentials.put(sourceCredential.sourceKey(), stored);
                    insertedSecrets.add(stored.id());
                } finally {
                    Arrays.fill(passphrase, '\0');
                }
            }

            Map<String, SavedHost> importedHosts = new HashMap<>();
            for (var sourceHost : bundle.hosts()) {
                String name = uniqueHostName(sourceHost.name(), Optional.of(group.id()), existingHosts);
                var profile = new com.ynpe.fastremote.domain.connection.ConnectionProfile(
                        new com.ynpe.fastremote.domain.connection.Host(sourceHost.host()),
                        new com.ynpe.fastremote.domain.connection.Port(sourceHost.port()),
                        new com.ynpe.fastremote.domain.connection.Username(sourceHost.username()),
                        sourceHost.authentication(), Optional.empty());
                SavedHost host = SavedHost.create(name, Optional.of(group.id()), profile,
                        sourceHost.sortOrder());
                GlobalCredential credential = sourceHost.credentialKey().map(importedCredentials::get).orElse(null);
                if (credential != null) host = host.withCredential(Optional.of(credential.id()));
                repository.saveHost(host);
                insertedHosts.add(host.id());
                existingHosts.add(host);
                importedHosts.put(sourceHost.name(), host);
            }
            for (var sourceHost : bundle.hosts()) {
                if (sourceHost.jumpName().isEmpty()) continue;
                SavedHost host = importedHosts.get(sourceHost.name());
                SavedHost jump = importedHosts.get(sourceHost.jumpName().orElseThrow());
                if (host == null || jump == null) continue;
                SavedHost routed = host.withJumpHost(Optional.of(jump.id()));
                repository.saveHost(routed);
                importedHosts.put(sourceHost.name(), routed);
            }
            return new HostCatalogImportResult(bundle.hosts().size(), 1, importedCredentials.size());
        } catch (Exception failure) {
            for (int i = insertedHosts.size() - 1; i >= 0; i--) {
                try { repository.deleteHost(insertedHosts.get(i)); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            }
            for (int i = insertedSecrets.size() - 1; i >= 0; i--) {
                try { vault.delete(insertedSecrets.get(i)); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            }
            for (int i = insertedGroups.size() - 1; i >= 0; i--) {
                try { repository.deleteGroup(insertedGroups.get(i)); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    private HostCatalogImportResult persistSecureCrt(SecureCrtXmlImporter.ImportBundle bundle) throws Exception {
        Map<String, GroupId> groupIds = new HashMap<>();
        Map<String, GlobalCredential> reusableCredentials = new HashMap<>();
        Map<String, SecureCrtXmlImporter.CredentialEntry> boundCredentials = new HashMap<>();
        List<HostId> insertedHosts = new ArrayList<>();
        List<com.ynpe.fastremote.domain.vault.SecretId> insertedSecrets = new ArrayList<>();
        List<GroupId> insertedGroups = new ArrayList<>();
        List<HostGroup> existingGroups = new ArrayList<>(repository.listGroups());
        List<SavedHost> existingHosts = new ArrayList<>(repository.listHosts());
        Set<String> credentialLabels = new HashSet<>();
        credentials.list().stream().filter(value -> value.scope() == CredentialScope.REUSABLE)
                .map(GlobalCredential::label).map(SqliteHostCatalogWorkflow::normalized).forEach(credentialLabels::add);
        try {
            for (var sourceGroup : bundle.groups()) {
                Optional<GroupId> parent = sourceGroup.parentKey().map(groupIds::get);
                String name = uniqueGroupName(sourceGroup.name(), parent, existingGroups);
                HostGroup group = new HostGroup(GroupId.create(), parent, name, sourceGroup.sortOrder(), GroupColor.BLUE);
                repository.saveGroup(group);
                existingGroups.add(group);
                insertedGroups.add(group.id());
                groupIds.put(sourceGroup.sourceKey(), group.id());
            }

            for (var sourceCredential : bundle.credentials()) {
                if (!sourceCredential.reusable()) {
                    boundCredentials.put(sourceCredential.sourceKey(), sourceCredential);
                    continue;
                }
                String label = uniqueName(sourceCredential.label(), credentialLabels);
                credentialLabels.add(normalized(label));
                GlobalCredential stored = storeImportedCredential(
                        sourceCredential, label, CredentialScope.REUSABLE, Optional.empty());
                reusableCredentials.put(sourceCredential.sourceKey(), stored);
                insertedSecrets.add(stored.id());
            }

            int importedCredentialCount = reusableCredentials.size();
            Map<String, SavedHost> importedHosts = new HashMap<>();
            for (var sourceHost : bundle.hosts()) {
                Optional<GroupId> groupId = sourceHost.groupKey().map(groupIds::get);
                String name = uniqueHostName(sourceHost.name(), groupId, existingHosts);
                var profile = new com.ynpe.fastremote.domain.connection.ConnectionProfile(
                        new com.ynpe.fastremote.domain.connection.Host(sourceHost.host()),
                        new com.ynpe.fastremote.domain.connection.Port(sourceHost.port()),
                        new com.ynpe.fastremote.domain.connection.Username(sourceHost.username()),
                        sourceHost.authentication(), Optional.empty());
                SavedHost host = SavedHost.create(name, groupId, profile, sourceHost.sortOrder());
                repository.saveHost(host);
                insertedHosts.add(host.id());

                if (sourceHost.credentialKey().isPresent()) {
                    String key = sourceHost.credentialKey().orElseThrow();
                    GlobalCredential reusable = reusableCredentials.get(key);
                    if (reusable != null) {
                        host = host.withCredential(Optional.of(reusable.id()));
                        repository.saveHost(host);
                    } else {
                        var sourceCredential = boundCredentials.get(key);
                        if (sourceCredential != null) {
                            GlobalCredential stored = storeImportedCredential(
                                    sourceCredential, name + com.ynpe.fastremote.i18n.Texts.text("legacy.java.43d5b7a4846ee2e0"), CredentialScope.HOST_BOUND,
                                    Optional.of(host.id()));
                            insertedSecrets.add(stored.id());
                            importedCredentialCount++;
                            host = host.withCredential(Optional.of(stored.id()));
                            repository.saveHost(host);
                        }
                    }
                }
                existingHosts.add(host);
                importedHosts.put(sourceHost.sourceKey(), host);
            }
            for (var sourceHost : bundle.hosts()) {
                if (sourceHost.jumpSourceKey().isEmpty()) continue;
                SavedHost host = importedHosts.get(sourceHost.sourceKey());
                SavedHost jump = importedHosts.get(sourceHost.jumpSourceKey().orElseThrow());
                if (host == null || jump == null) continue;
                SavedHost routed = host.withJumpHost(Optional.of(jump.id()));
                repository.saveHost(routed);
                importedHosts.put(sourceHost.sourceKey(), routed);
            }
            return new HostCatalogImportResult(bundle.hosts().size(), bundle.groups().size(), importedCredentialCount);
        } catch (Exception failure) {
            for (int i = insertedHosts.size() - 1; i >= 0; i--) {
                try { repository.deleteHost(insertedHosts.get(i)); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            }
            for (int i = insertedSecrets.size() - 1; i >= 0; i--) {
                try { vault.delete(insertedSecrets.get(i)); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            }
            for (int i = insertedGroups.size() - 1; i >= 0; i--) {
                try { repository.deleteGroup(insertedGroups.get(i)); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    private GlobalCredential storeImportedCredential(
            SecureCrtXmlImporter.CredentialEntry source, String label, CredentialScope scope,
            Optional<HostId> owner) throws Exception {
        char[] secret = source.copySecret();
        try (var input = new CredentialInput(source.authentication(), secret, source.privateKeySource())) {
            return storeCredential(label, source.username(), scope, owner, input);
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    private static String uniqueGroupName(String requested, Optional<GroupId> parent, List<HostGroup> existing) {
        Set<String> names = new HashSet<>();
        existing.stream().filter(group -> group.parentId().equals(parent)).map(HostGroup::name)
                .map(SqliteHostCatalogWorkflow::normalized).forEach(names::add);
        return uniqueName(requested, names);
    }

    private static String uniqueHostName(String requested, Optional<GroupId> group, List<SavedHost> existing) {
        Set<String> names = new HashSet<>();
        existing.stream().filter(host -> host.groupId().equals(group)).map(SavedHost::name)
                .map(SqliteHostCatalogWorkflow::normalized).forEach(names::add);
        return uniqueName(requested, names);
    }

    private static String uniqueName(String requested, Set<String> existing) {
        String base = requested.strip();
        if (!existing.contains(normalized(base))) return base;
        for (int suffix = 1; suffix < 100_000; suffix++) {
            String candidate = base + "-" + suffix;
            if (!existing.contains(normalized(candidate))) return candidate;
        }
        throw new IllegalStateException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.225af8eedf8c14f8"));
    }

    private static String normalized(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private GlobalCredential storeCredential(
            String label,
            String username,
            CredentialScope scope,
            Optional<HostId> owner,
            CredentialInput input) throws Exception {
        return storeCredential(label, username, scope, owner, input, Optional.empty());
    }

    private GlobalCredential storeCredential(String label, String username, CredentialScope scope,
            Optional<HostId> owner, CredentialInput input,
            Optional<com.ynpe.fastremote.domain.credential.CredentialGroupId> groupId) throws Exception {
        int nextSortOrder = scope == CredentialScope.REUSABLE ? credentials.list().stream()
                .filter(value -> value.scope() == CredentialScope.REUSABLE && value.groupId().equals(groupId))
                .mapToInt(GlobalCredential::sortOrder).max().orElse(-10) + 10 : 0;
        EncodedCredential encoded = encodeCredential(input);
        byte[] payload = encoded.payload();
        SecretType type = encoded.type();
        try {
            var secretId = vault.put(type, payload);
            try {
                GlobalCredential metadata = new GlobalCredential(
                        secretId, label, username, input.authenticationType(), scope, owner, groupId,
                        nextSortOrder);
                credentials.save(metadata);
                return metadata;
            } catch (Exception metadataFailure) {
                vault.delete(secretId);
                throw metadataFailure;
            }
        } finally {
            encoded.close();
        }
    }

    private static EncodedCredential encodeCredential(CredentialInput input) throws Exception {
        byte[] payload;
        SecretType type;
        if (input.authenticationType() == AuthenticationType.PASSWORD) {
            payload = encode(input.secret());
            type = SecretType.PASSWORD;
        } else if (input.inlinePrivateKey() != null) {
            payload = encodePrivateKey(input.inlinePrivateKey(), input.secret());
            type = SecretType.PRIVATE_KEY;
        } else {
            Path source = input.privateKeySource().orElseThrow();
            long size = Files.size(source);
            if (!Files.isRegularFile(source) || size < 1 || size > MAX_PRIVATE_KEY_BYTES) {
                throw new IOException("Private key must be a readable file no larger than 1 MiB");
            }
            byte[] key;
            try (var stream = Files.newInputStream(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                key = stream.readNBytes(MAX_PRIVATE_KEY_BYTES + 1);
            }
            try {
                if (key.length == 0 || key.length > MAX_PRIVATE_KEY_BYTES) {
                    throw new IOException("Private key must be a readable file no larger than 1 MiB");
                }
                payload = encodePrivateKey(key, input.secret());
            } finally {
                Arrays.fill(key, (byte) 0);
            }
            type = SecretType.PRIVATE_KEY;
        }
        return new EncodedCredential(type, payload);
    }

    private static ResolvedCredential resolve(AuthenticationType authentication, byte[] payload) throws IOException {
        if (authentication == AuthenticationType.PASSWORD) {
            try {
                char[] secret = decode(payload);
                try {
                    return new ResolvedCredential(authentication, secret, null);
                } finally {
                    Arrays.fill(secret, '\0');
                }
            } catch (CharacterCodingException failure) {
                throw new IOException("Stored credential text is invalid", failure);
            }
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readInt() != PRIVATE_KEY_MAGIC) throw new IOException("Stored private key has an unknown format");
            int keyLength = input.readInt();
            if (keyLength < 1 || keyLength > MAX_PRIVATE_KEY_BYTES) throw new IOException("Stored private key is too large");
            byte[] key = input.readNBytes(keyLength);
            if (key.length != keyLength) throw new IOException("Stored private key is truncated");
            int secretLength = input.readInt();
            if (secretLength < 0 || secretLength > MAX_SECRET_TEXT_BYTES) throw new IOException("Stored passphrase is too large");
            byte[] encodedSecret = input.readNBytes(secretLength);
            if (encodedSecret.length != secretLength) {
                Arrays.fill(key, (byte) 0);
                throw new IOException("Stored private-key passphrase is truncated");
            }
            try {
                char[] secret = decode(encodedSecret);
                try {
                    return new ResolvedCredential(authentication, secret, key);
                } finally {
                    Arrays.fill(secret, '\0');
                }
            } finally {
                Arrays.fill(key, (byte) 0);
                Arrays.fill(encodedSecret, (byte) 0);
            }
        }
    }

    private static byte[] encodePrivateKey(byte[] key, char[] passphrase) throws IOException {
        byte[] encodedSecret = encode(passphrase);
        try {
            return ByteBuffer.allocate(Integer.BYTES * 3 + key.length + encodedSecret.length)
                    .putInt(PRIVATE_KEY_MAGIC)
                    .putInt(key.length)
                    .put(key)
                    .putInt(encodedSecret.length)
                    .put(encodedSecret)
                    .array();
        } finally {
            Arrays.fill(encodedSecret, (byte) 0);
        }
    }

    public CompletionStage<Void> stopped() { return executor.stopped(); }

    private <T> CompletionStage<T> supply(BoundedWorkflowExecutor.CheckedSupplier<T> operation) {
        return executor.supply(operation);
    }

    private <T> CompletionStage<T> supply(BoundedWorkflowExecutor.CheckedSupplier<T> operation,
            Runnable rejectionCleanup) {
        return executor.supply(operation, rejectionCleanup);
    }

    private static char[] takeCopy(char[] value) {
        Objects.requireNonNull(value, "value");
        char[] copy = value.clone();
        Arrays.fill(value, '\0');
        return copy;
    }

    private static byte[] encode(char[] value) throws CharacterCodingException {
        var encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer encoded = encoder.encode(CharBuffer.wrap(value));
        byte[] result = new byte[encoded.remaining()];
        encoded.get(result);
        if (encoded.hasArray()) {
            Arrays.fill(encoded.array(), (byte) 0);
        }
        return result;
    }

    private static char[] decode(byte[] value) throws CharacterCodingException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer decoded = decoder.decode(ByteBuffer.wrap(value));
        char[] result = new char[decoded.remaining()];
        decoded.get(result);
        if (decoded.hasArray()) {
            Arrays.fill(decoded.array(), '\0');
        }
        return result;
    }

    record CredentialInput(AuthenticationType authenticationType, char[] secret, Optional<Path> privateKeySource,
            byte[] inlinePrivateKey)
            implements AutoCloseable {
        CredentialInput {
            secret = secret.clone();
            privateKeySource = Objects.requireNonNull(privateKeySource, "privateKeySource");
        }

        private CredentialInput(AuthenticationType authenticationType, char[] secret, Optional<Path> privateKeySource) {
            this(authenticationType, secret, privateKeySource, null);
        }

        static CredentialInput inline(AuthenticationType type, String secret, Optional<String> privateKey) throws IOException {
            if (type == AuthenticationType.PASSWORD && secret.isEmpty()) {
                throw new IllegalArgumentException("password must not be empty");
            }
            if (privateKey.filter(String::isBlank).isPresent()) {
                throw new IllegalArgumentException("private_key must not be blank");
            }
            String secretField = privateKey.isPresent() ? "passphrase" : "password";
            if (secret.length() > MAX_SECRET_TEXT_BYTES) throw oversized(secretField, MAX_SECRET_TEXT_BYTES);
            char[] secretChars = secret.toCharArray();
            try {
                byte[] check = boundedUtf8(secretChars, secretField, MAX_SECRET_TEXT_BYTES);
                Arrays.fill(check, (byte) 0);
                byte[] key = null;
                if (privateKey.isPresent()) {
                    String text = privateKey.orElseThrow();
                    if (text.length() > MAX_PRIVATE_KEY_BYTES) throw oversized("private_key", MAX_PRIVATE_KEY_BYTES);
                    char[] keyChars = text.toCharArray();
                    try {
                        key = boundedUtf8(keyChars, "private_key", MAX_PRIVATE_KEY_BYTES);
                    } finally {
                        Arrays.fill(keyChars, '\0');
                    }
                }
                return new CredentialInput(type, secretChars, Optional.empty(), key);
            } finally {
                Arrays.fill(secretChars, '\0');
            }
        }

        private static byte[] boundedUtf8(char[] value, String field, int limit) throws IOException {
            byte[] encoded;
            try {
                encoded = encode(value);
            } catch (CharacterCodingException invalid) {
                throw new IOException(field + " must contain valid Unicode", invalid);
            }
            if (encoded.length <= limit) return encoded;
            Arrays.fill(encoded, (byte) 0);
            throw oversized(field, limit);
        }

        private static IllegalArgumentException oversized(String field, int limit) {
            return new IllegalArgumentException(field + " exceeds " + limit + " UTF-8 bytes");
        }

        private static CredentialInput take(CredentialDraft draft) {
            char[] secret = draft.copySecret();
            try {
                return new CredentialInput(draft.authenticationType(), secret, draft.privateKeySource());
            } finally {
                Arrays.fill(secret, '\0');
            }
        }

        @Override
        public void close() {
            Arrays.fill(secret, '\0');
            if (inlinePrivateKey != null) Arrays.fill(inlinePrivateKey, (byte) 0);
        }

        @Override public String toString() { return "CredentialInput[" + authenticationType + "]"; }
    }

    private record EncodedCredential(SecretType type, byte[] payload) implements AutoCloseable {
        private EncodedCredential {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(payload, "payload");
        }

        @Override
        public void close() {
            Arrays.fill(payload, (byte) 0);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        executor.close();
        if (ownsStorage) executor.stopped().thenRun(() -> {
            try { vault.close(); }
            finally { database.close(); }
        });
    }

}
