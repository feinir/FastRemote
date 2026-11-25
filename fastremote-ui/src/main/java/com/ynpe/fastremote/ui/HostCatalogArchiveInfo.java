// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

public record HostCatalogArchiveInfo(boolean credentials, boolean hostCredentials,
        int groupIdConflicts, int hostIdConflicts, int credentialIdConflicts) {
    public boolean hasSecrets() {
        return credentials || hostCredentials;
    }

    public boolean hasIdConflicts() {
        return groupIdConflicts > 0 || hostIdConflicts > 0 || credentialIdConflicts > 0;
    }
}
