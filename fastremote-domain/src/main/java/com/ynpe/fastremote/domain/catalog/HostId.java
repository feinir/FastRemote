// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.catalog;

import java.util.Objects;
import java.util.UUID;

public record HostId(UUID value) {
    public HostId {
        Objects.requireNonNull(value, "value");
    }

    public static HostId create() {
        return new HostId(UUID.randomUUID());
    }

    public static HostId parse(String value) {
        return new HostId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
