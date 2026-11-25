// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.vault;

/** Secret vault capabilities needed by global master-password management. */
public interface ManagedSecretVault extends SecretVault {
    /** Verifies the current master password without changing the lock state. */
    void verifyMasterPassword(char[] currentPassword) throws VaultException;

    boolean rememberedUnlockConfigured() throws VaultException;

    /** Validates saved material and returns short-lived export protection; does not unlock the vault. */
    RememberedVaultProtection rememberedExportProtection() throws VaultException;

    void unlockRemembered() throws VaultException;

    void setRememberedUnlock(boolean remember, char[] currentPassword) throws VaultException;

    /** An empty current password requires valid locally remembered unlock material. */
    void changeMasterPassword(char[] currentPassword, char[] newPassword, boolean rememberNewKey)
            throws VaultException;
}
