// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.update;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record SoftwareUpdateSettings(
        boolean automaticChecksEnabled,
        Optional<Instant> lastSuccessfulCheckAt,
        String skippedVersion) {
    public SoftwareUpdateSettings {
        lastSuccessfulCheckAt = Objects.requireNonNull(lastSuccessfulCheckAt, "lastSuccessfulCheckAt");
        skippedVersion = Objects.requireNonNullElse(skippedVersion, "").strip();
        if (!skippedVersion.isEmpty()) SemanticVersion.parse(skippedVersion);
    }

    public static SoftwareUpdateSettings defaults() {
        return new SoftwareUpdateSettings(true, Optional.empty(), "");
    }
}
