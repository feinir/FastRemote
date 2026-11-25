// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

public record SecureCrtImportInfo(
        int hosts,
        int groups,
        int sharedCredentials,
        int encryptedPasswords,
        int certificateReferences,
        int readableCertificates,
        int missingCertificates,
        int skippedHosts) {
    public boolean hasImportableSecrets() {
        return encryptedPasswords > 0 || readableCertificates > 0;
    }
}
