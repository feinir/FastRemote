// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Short-lived command object; secret buffers are cloned and explicitly cleared. */
public final class CredentialDraft implements AutoCloseable {
    private final AuthenticationType authenticationType;
    private final Optional<Path> privateKeySource;
    private char[] secret;

    public CredentialDraft(
            AuthenticationType authenticationType, char[] secret, Optional<Path> privateKeySource) {
        this.authenticationType = Objects.requireNonNull(authenticationType, "authenticationType");
        Objects.requireNonNull(secret, "secret");
        this.privateKeySource = Objects.requireNonNull(privateKeySource, "privateKeySource")
                .map(Path::toAbsolutePath).map(Path::normalize);
        if (authenticationType == AuthenticationType.PASSWORD && secret.length == 0) {
            throw new IllegalArgumentException("Password credentials require a password");
        }
        if ((authenticationType == AuthenticationType.PRIVATE_KEY) != this.privateKeySource.isPresent()) {
            throw new IllegalArgumentException("Private-key credentials require a source file");
        }
        this.secret = secret.clone();
    }

    public AuthenticationType authenticationType() {
        return authenticationType;
    }

    public Optional<Path> privateKeySource() {
        return privateKeySource;
    }

    public synchronized char[] copySecret() {
        ensureOpen();
        return secret.clone();
    }

    @Override
    public synchronized void close() {
        if (secret != null) {
            Arrays.fill(secret, '\0');
            secret = null;
        }
    }

    private void ensureOpen() {
        if (secret == null) throw new IllegalStateException("Credential draft has already been cleared");
    }
}
