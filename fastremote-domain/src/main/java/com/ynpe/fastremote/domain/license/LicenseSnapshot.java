// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.license;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record LicenseSnapshot(
        LicenseStatus status,
        String kind,
        Optional<Instant> expiresAt,
        Optional<Instant> refreshAfter,
        Optional<Instant> offlineUntil,
        Set<LicenseCapability> capabilities,
        String lease,
        String licenseHint,
        String deviceCode,
        boolean activationRequired
) {
    public LicenseSnapshot(LicenseStatus status, String kind, Optional<Instant> expiresAt,
                           Optional<Instant> refreshAfter, Optional<Instant> offlineUntil,
                           Set<LicenseCapability> capabilities, String lease) {
        this(status, kind, expiresAt, refreshAfter, offlineUntil, capabilities, lease, "", "", false);
    }

    public LicenseSnapshot(LicenseStatus status, String kind, Optional<Instant> expiresAt,
                           Optional<Instant> refreshAfter, Optional<Instant> offlineUntil,
                           Set<LicenseCapability> capabilities, String lease, String licenseHint) {
        this(status, kind, expiresAt, refreshAfter, offlineUntil, capabilities, lease, licenseHint, "", false);
    }

    public LicenseSnapshot {
        Objects.requireNonNull(status, "status");
        kind = Objects.requireNonNullElse(kind, "");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        refreshAfter = Objects.requireNonNull(refreshAfter, "refreshAfter");
        offlineUntil = Objects.requireNonNull(offlineUntil, "offlineUntil");
        capabilities = Set.copyOf(capabilities);
        lease = Objects.requireNonNullElse(lease, "");
        licenseHint = Objects.requireNonNullElse(licenseHint, "");
        deviceCode = Objects.requireNonNullElse(deviceCode, "");
        if (status.permitsCommercialUse() && lease.isBlank()) {
            throw new IllegalArgumentException("an authorized snapshot requires a process-bound lease");
        }
    }

    public boolean permits(LicenseCapability capability) {
        return (status == LicenseStatus.COMMUNITY || status.permitsCommercialUse())
                && capabilities.contains(capability);
    }
}
