// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.connection;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record ConnectionProfile(
        Host host,
        Port port,
        Username username,
        AuthenticationType authenticationType,
        Optional<Path> privateKeyPath,
        boolean legacyAlgorithmsEnabled) {

    public ConnectionProfile(
            Host host,
            Port port,
            Username username,
            AuthenticationType authenticationType,
            Optional<Path> privateKeyPath) {
        this(host, port, username, authenticationType, privateKeyPath, false);
    }

    public ConnectionProfile {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(port, "port");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(authenticationType, "authenticationType");
        privateKeyPath = Objects.requireNonNull(privateKeyPath, "privateKeyPath")
                .map(Path::toAbsolutePath)
                .map(Path::normalize);
        if (authenticationType == AuthenticationType.PASSWORD && privateKeyPath.isPresent()) {
            throw new IllegalArgumentException("Password authentication must not include a key path");
        }
    }
}
