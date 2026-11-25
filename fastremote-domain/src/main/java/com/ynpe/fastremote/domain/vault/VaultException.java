// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.vault;

import java.util.Objects;

public final class VaultException extends Exception {
    private final VaultFailureType type;

    public VaultException(VaultFailureType type, String message) {
        super(message);
        this.type = Objects.requireNonNull(type, "type");
    }

    public VaultException(VaultFailureType type, String message, Throwable cause) {
        super(message, cause);
        this.type = Objects.requireNonNull(type, "type");
    }

    public VaultFailureType type() {
        return type;
    }
}
