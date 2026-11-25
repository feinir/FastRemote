// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.nio.file.Path;
import java.util.List;

public record OpenSshImportInfo(
        int hosts,
        int readableIdentities,
        int encryptedIdentities,
        int missingIdentities,
        int proxyHosts,
        int invalidHosts,
        int wildcardPatterns,
        int ignoredOptions,
        int configFiles,
        List<Identity> identities) {
    public OpenSshImportInfo {
        identities = List.copyOf(identities);
    }

    public boolean hasImportableSecrets() {
        return readableIdentities > 0;
    }

    public record Identity(Path path, boolean encrypted) {
        public Identity {
            path = path.toAbsolutePath().normalize();
        }
    }
}
