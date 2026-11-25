// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import java.util.Arrays;
import java.util.Objects;

/** Decrypted credential material with an explicit clearing lifecycle. */
public final class ResolvedCredential implements AutoCloseable {
    private final AuthenticationType authenticationType;
    private char[] secret;
    private byte[] privateKey;

    public ResolvedCredential(AuthenticationType authenticationType, char[] secret, byte[] privateKey) {
        this.authenticationType = Objects.requireNonNull(authenticationType, "authenticationType");
        this.secret = Objects.requireNonNull(secret, "secret").clone();
        this.privateKey = privateKey == null ? null : privateKey.clone();
    }

    public AuthenticationType authenticationType() { return authenticationType; }

    public synchronized char[] copySecret() {
        ensureOpen();
        return secret.clone();
    }

    public synchronized byte[] copyPrivateKey() {
        ensureOpen();
        return privateKey == null ? null : privateKey.clone();
    }

    @Override
    public synchronized void close() {
        if (secret != null) Arrays.fill(secret, '\0');
        if (privateKey != null) Arrays.fill(privateKey, (byte) 0);
        secret = null;
        privateKey = null;
    }

    private void ensureOpen() {
        if (secret == null) throw new IllegalStateException("Resolved credential has already been cleared");
    }
}
