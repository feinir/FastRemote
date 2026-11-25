// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import com.ynpe.fastremote.domain.credential.CredentialGroup;
import com.ynpe.fastremote.domain.credential.CredentialGroupId;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.nio.file.Path;

public interface HostCatalogWorkflow extends AutoCloseable {
    CompletionStage<HostCatalogSnapshot> loadCatalog(String query);

    CompletionStage<Void> recordSuccessfulConnection(Optional<HostId> hostId, ConnectionProfile profile);

    CompletionStage<HostGroup> createGroup(String name, Optional<GroupId> parentId, GroupColor color);

    CompletionStage<HostGroup> updateGroup(HostGroup group);

    CompletionStage<Void> moveGroup(GroupId id, Optional<GroupId> parentId, int targetIndex);

    CompletionStage<Void> deleteGroup(GroupId id);

    CompletionStage<SavedHost> saveHost(SavedHost host, Optional<CredentialDraft> credential);

    CompletionStage<SavedHost> copyHost(
            SavedHost source, SavedHost copy, Optional<CredentialDraft> replacementCredential);

    CompletionStage<Void> moveHost(HostId id, Optional<GroupId> groupId, int targetIndex);

    CompletionStage<List<String>> deleteHost(SavedHost host);

    CompletionStage<Void> createVault(char[] masterPassword, boolean rememberUnlock);

    CompletionStage<Void> unlockVault(char[] masterPassword);

    CompletionStage<Boolean> tryUnlockRememberedVault();

    CompletionStage<Boolean> rememberedVaultUnlockConfigured();

    CompletionStage<Void> setRememberedVaultUnlock(boolean remember, char[] currentPassword);

    CompletionStage<Void> changeVaultMasterPassword(
            char[] currentPassword, char[] newPassword, boolean rememberNewKey);

    void lockVault();

    CompletionStage<ResolvedCredential> loadCredential(SavedHost host);

    CompletionStage<ResolvedCredential> loadGlobalCredential(GlobalCredential credential);

    CompletionStage<GlobalCredential> createReusableCredential(
            String label, String username, CredentialDraft credential);

    CompletionStage<GlobalCredential> updateReusableCredential(
            GlobalCredential credential,
            String label,
            String username,
            Optional<CredentialDraft> replacement);

    CompletionStage<Void> deleteReusableCredential(GlobalCredential credential);

    CompletionStage<CredentialGroup> saveCredentialGroup(CredentialGroup group);

    CompletionStage<Void> deleteCredentialGroup(CredentialGroupId id);

    CompletionStage<GlobalCredential> moveReusableCredential(
            GlobalCredential credential, Optional<CredentialGroupId> groupId, int sortOrder);

    CompletionStage<Void> verifyVaultMasterPassword(char[] currentPassword);

    CompletionStage<Void> exportCatalog(
            Path target, boolean includeCredentials, boolean includeHostCredentials, char[] sourceMasterPassword);

    default CompletionStage<Void> exportConfiguration(Path target, ConfigurationSelection selection, char[] password) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Configuration export unavailable"));
    }

    default CompletionStage<Boolean> automaticUpdatePreference() {
        return java.util.concurrent.CompletableFuture.completedFuture(true);
    }

    default CompletionStage<Void> resetProgramConfiguration() {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Configuration reset unavailable"));
    }

    CompletionStage<HostCatalogArchiveInfo> inspectCatalogArchive(Path source);

    CompletionStage<Void> verifyCatalogArchivePassword(Path source, char[] sourceMasterPassword);

    CompletionStage<HostCatalogImportResult> importCatalog(Path source, char[] sourceMasterPassword);

    CompletionStage<SecureCrtImportInfo> inspectSecureCrtArchive(Path source);

    CompletionStage<Void> verifySecureCrtArchivePassword(Path source, char[] sourcePassphrase);

    CompletionStage<HostCatalogImportResult> importSecureCrtArchive(Path source, char[] sourcePassphrase);

    CompletionStage<OpenSshImportInfo> inspectOpenSshConfig(Path source);

    CompletionStage<HostCatalogImportResult> importOpenSshConfig(Path source, Map<Path, char[]> encryptedKeyPassphrases);

    default CompletionStage<com.ynpe.fastremote.domain.host.HostImportPlan> inspectThirdParty(
            com.ynpe.fastremote.domain.host.HostImportSource source, Optional<Path> path) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());
    }

    default CompletionStage<Void> attachImportKey(com.ynpe.fastremote.domain.host.HostImportPlan plan,
            String entryId,Path file,char[] passphrase) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());
    }

    default CompletionStage<ThirdPartyImportResult> importThirdParty(
            com.ynpe.fastremote.domain.host.HostImportPlan plan, java.util.Set<String> selected,
            Map<String,String> usernames, boolean skipDuplicates) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());
    }

    @Override
    void close();
}
