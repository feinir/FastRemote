// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.connection;

import java.util.Objects;

public record Host(String value) {
    public Host {
        value = Objects.requireNonNull(value, "value").strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Host must not be blank");
        }
        if (value.length() > 253) {
            throw new IllegalArgumentException("Host must not exceed 253 characters");
        }
    }
}
