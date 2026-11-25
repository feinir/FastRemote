// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.security;

import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import java.util.Objects;

public record HostKeyIdentity(
        Host host,
        Port port,
        String algorithm,
        String sha256Fingerprint,
        String encodedPublicKey) {

    public HostKeyIdentity {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(port, "port");
        algorithm = requireText(algorithm, "algorithm");
        sha256Fingerprint = requireText(sha256Fingerprint, "sha256Fingerprint");
        encodedPublicKey = requireText(encodedPublicKey, "encodedPublicKey");
    }

    public boolean hasSameKey(HostKeyIdentity other) {
        return other != null
                && algorithm.equals(other.algorithm)
                && encodedPublicKey.equals(other.encodedPublicKey);
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
