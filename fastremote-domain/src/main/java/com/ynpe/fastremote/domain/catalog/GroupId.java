// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.catalog;

import java.util.Objects;
import java.util.UUID;

public record GroupId(UUID value) {
    public GroupId {
        Objects.requireNonNull(value, "value");
    }

    public static GroupId create() {
        return new GroupId(UUID.randomUUID());
    }

    public static GroupId parse(String value) {
        return new GroupId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
