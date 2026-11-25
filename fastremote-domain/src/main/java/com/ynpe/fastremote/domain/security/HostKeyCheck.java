// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.security;

import java.util.Objects;
import java.util.Optional;

public record HostKeyCheck(
        HostKeyStatus status,
        HostKeyIdentity presented,
        Optional<HostKeyIdentity> previouslyTrusted) {

    public HostKeyCheck {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(presented, "presented");
        previouslyTrusted = Objects.requireNonNull(previouslyTrusted, "previouslyTrusted");
        if (status == HostKeyStatus.UNKNOWN && previouslyTrusted.isPresent()) {
            throw new IllegalArgumentException("Unknown host must not have a trusted key");
        }
        if (status != HostKeyStatus.UNKNOWN && previouslyTrusted.isEmpty()) {
            throw new IllegalArgumentException("Known host status requires a trusted key");
        }
    }
}
