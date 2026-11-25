// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Arrays;
import java.util.Objects;

/** In-memory-only connection material shared by independently opened workspace tabs. */
final class TemporaryConnectionCredential implements AutoCloseable {
    private char[] secret;
    private byte[] privateKey;

    TemporaryConnectionCredential(char[] secret, byte[] privateKey) {
        this.secret = Objects.requireNonNull(secret, "secret").clone();
        this.privateKey = privateKey == null ? null : privateKey.clone();
    }

    static TemporaryConnectionCredential from(ResolvedCredential resolved) {
        Objects.requireNonNull(resolved, "resolved");
        char[] secret = resolved.copySecret();
        byte[] privateKey = resolved.copyPrivateKey();
        try {
            return new TemporaryConnectionCredential(secret, privateKey);
        } finally {
            Arrays.fill(secret, '\0');
            if (privateKey != null) Arrays.fill(privateKey, (byte) 0);
        }
    }

    synchronized char[] copySecret() {
        ensureOpen();
        return secret.clone();
    }

    synchronized byte[] copyPrivateKey() {
        ensureOpen();
        return privateKey == null ? null : privateKey.clone();
    }

    synchronized TemporaryConnectionCredential copy() {
        ensureOpen();
        return new TemporaryConnectionCredential(secret, privateKey);
    }

    @Override
    public synchronized void close() {
        if (secret != null) Arrays.fill(secret, '\0');
        if (privateKey != null) Arrays.fill(privateKey, (byte) 0);
        secret = null;
        privateKey = null;
    }

    private void ensureOpen() {
        if (secret == null) throw new IllegalStateException("Temporary credential has already been cleared");
    }
}
