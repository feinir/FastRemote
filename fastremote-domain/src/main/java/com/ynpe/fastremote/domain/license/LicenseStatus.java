// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.license;

public enum LicenseStatus {
    COMMUNITY,
    TRIAL_AVAILABLE,
    TRIAL_ACTIVE,
    LICENSED_ONLINE,
    LICENSED_OFFLINE,
    OFFLINE_GRACE,
    NEEDS_ACTIVATION,
    EXPIRED,
    REVOKED,
    DEVICE_LIMIT_REACHED,
    DEVICE_REBIND_REQUIRED,
    CLOCK_REVALIDATION_REQUIRED,
    ONLINE_REVALIDATION_REQUIRED,
    CLIENT_VERSION_BLOCKED,
    INTEGRITY_ERROR,
    AGENT_UNAVAILABLE;

    public boolean permitsCommercialUse() {
        return this == TRIAL_ACTIVE || this == LICENSED_ONLINE || this == LICENSED_OFFLINE
                || this == OFFLINE_GRACE;
    }
}
