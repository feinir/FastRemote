// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.connection;

import java.util.Objects;

public final class ConnectionException extends Exception {
    private final ConnectionFailureType failureType;

    public ConnectionException(ConnectionFailureType failureType, String message) {
        super(message);
        this.failureType = Objects.requireNonNull(failureType, "failureType");
    }

    public ConnectionException(ConnectionFailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = Objects.requireNonNull(failureType, "failureType");
    }

    public ConnectionFailureType failureType() {
        return failureType;
    }
}
