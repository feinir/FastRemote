// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.connection;

import java.util.Arrays;
import java.util.Objects;

public final class PasswordCredentials implements Credentials {
    private char[] password;

    public PasswordCredentials(char[] password) {
        Objects.requireNonNull(password, "password");
        if (password.length == 0) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        this.password = password.clone();
    }

    @Override
    public synchronized char[] copySecret() {
        ensureOpen();
        return password.clone();
    }

    @Override
    public synchronized void close() {
        if (password != null) {
            Arrays.fill(password, '\0');
            password = null;
        }
    }

    private void ensureOpen() {
        if (password == null) {
            throw new IllegalStateException("Credentials have already been cleared");
        }
    }

    @Override
    public String toString() {
        return "PasswordCredentials[redacted]";
    }
}
