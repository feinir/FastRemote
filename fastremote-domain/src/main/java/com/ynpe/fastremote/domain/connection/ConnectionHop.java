// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.connection;

import java.util.Objects;

/** One authenticated SSH leg in root-jump-to-target order. */
public record ConnectionHop(String name, ConnectionProfile profile, Credentials credentials) {
    public ConnectionHop {
        name = Objects.requireNonNull(name, "name").strip();
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(credentials, "credentials");
        if (name.isEmpty()) throw new IllegalArgumentException("Connection hop name must not be blank");
    }
}
