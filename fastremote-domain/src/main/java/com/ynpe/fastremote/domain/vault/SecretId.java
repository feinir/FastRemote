// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.vault;

import java.util.Objects;
import java.util.UUID;

public record SecretId(UUID value) {
    public SecretId {
        Objects.requireNonNull(value, "value");
    }

    public static SecretId create() {
        return new SecretId(UUID.randomUUID());
    }

    public static SecretId parse(String value) {
        return new SecretId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
