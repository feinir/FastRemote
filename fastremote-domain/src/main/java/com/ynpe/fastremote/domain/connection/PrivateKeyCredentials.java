// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.connection;

import java.util.Arrays;
import java.util.Objects;

public final class PrivateKeyCredentials implements Credentials {
    private char[] passphrase;
    private byte[] privateKey;

    public PrivateKeyCredentials(char[] passphrase) {
        this(passphrase, null);
    }

    public PrivateKeyCredentials(char[] passphrase, byte[] privateKey) {
        this.passphrase = Objects.requireNonNull(passphrase, "passphrase").clone();
        this.privateKey = privateKey == null ? null : privateKey.clone();
    }

    public synchronized byte[] copyPrivateKey() {
        ensureOpen();
        return privateKey == null ? null : privateKey.clone();
    }

    @Override
    public synchronized char[] copySecret() {
        ensureOpen();
        return passphrase.clone();
    }

    @Override
    public synchronized void close() {
        if (passphrase != null) {
            Arrays.fill(passphrase, '\0');
            passphrase = null;
        }
        if (privateKey != null) {
            Arrays.fill(privateKey, (byte) 0);
            privateKey = null;
        }
    }

    private void ensureOpen() {
        if (passphrase == null) {
            throw new IllegalStateException("Credentials have already been cleared");
        }
    }

    @Override
    public String toString() {
        return "PrivateKeyCredentials[redacted]";
    }
}
