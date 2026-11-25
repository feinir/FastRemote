// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.connection;

import java.util.Objects;

public record Username(String value) {
    public Username {
        value = Objects.requireNonNull(value, "value").strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Username contains an invalid character");
        }
    }
}
