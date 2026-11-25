// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.RecentConnection;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import com.ynpe.fastremote.domain.credential.CredentialGroup;
import java.util.List;
import java.util.Objects;

public record HostCatalogSnapshot(
        List<HostGroup> groups,
        List<SavedHost> hosts,
        List<RecentConnection> recentConnections,
        List<GlobalCredential> credentials,
        List<CredentialGroup> credentialGroups,
        VaultStatus vaultStatus) {
    public HostCatalogSnapshot {
        groups = List.copyOf(Objects.requireNonNull(groups, "groups"));
        hosts = List.copyOf(Objects.requireNonNull(hosts, "hosts"));
        recentConnections = List.copyOf(Objects.requireNonNull(recentConnections, "recentConnections"));
        credentials = List.copyOf(Objects.requireNonNull(credentials, "credentials"));
        credentialGroups = List.copyOf(Objects.requireNonNull(credentialGroups, "credentialGroups"));
        Objects.requireNonNull(vaultStatus, "vaultStatus");
    }

    public HostCatalogSnapshot(List<HostGroup> groups, List<SavedHost> hosts,
            List<GlobalCredential> credentials, VaultStatus vaultStatus) {
        this(groups, hosts, List.of(), credentials, List.of(), vaultStatus);
    }

    public HostCatalogSnapshot(List<HostGroup> groups, List<SavedHost> hosts,
            List<RecentConnection> recentConnections, List<GlobalCredential> credentials,
            VaultStatus vaultStatus) {
        this(groups, hosts, recentConnections, credentials, List.of(), vaultStatus);
    }
}
