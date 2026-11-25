// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.vault;

public interface SecretVault extends AutoCloseable {
    VaultStatus status() throws VaultException;

    void create(char[] masterPassword) throws VaultException;

    void unlock(char[] masterPassword) throws VaultException;

    void lock();

    SecretId put(SecretType type, byte[] plaintext) throws VaultException;

    /** Stores a portable secret under its stable interchange identifier. */
    void put(SecretId id, SecretType type, byte[] plaintext) throws VaultException;

    void replace(SecretId id, SecretType type, byte[] plaintext) throws VaultException;

    byte[] read(SecretId id) throws VaultException;

    void delete(SecretId id) throws VaultException;

    @Override
    void close();
}
