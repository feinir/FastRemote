// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.domain.license.LicenseSnapshot;
import com.ynpe.fastremote.domain.license.LicenseStatus;
import java.time.Duration;
import java.time.Instant;

final class LicenseStartupPolicy {
    static final Duration TRIAL_EXPIRY_REMINDER_WINDOW = Duration.ofDays(3);
    static final Duration OFFLINE_EXPIRY_REMINDER_WINDOW = Duration.ofDays(3);

    private LicenseStartupPolicy() { }

    static boolean shouldRefreshOnlineEntitlement(LicenseSnapshot snapshot) {
        return "trial".equals(snapshot.kind()) || "online".equals(snapshot.kind());
    }

    static boolean shouldRemindTrialExpiry(LicenseSnapshot snapshot, Instant now) {
        if (snapshot.status() != LicenseStatus.TRIAL_ACTIVE || !"trial".equals(snapshot.kind())) return false;
        return snapshot.expiresAt()
                .filter(expiry -> !expiry.isBefore(now))
                .filter(expiry -> !expiry.isAfter(now.plus(TRIAL_EXPIRY_REMINDER_WINDOW)))
                .isPresent();
    }

    static boolean shouldRemindOfflineExpiry(LicenseSnapshot snapshot, Instant now) {
        if (snapshot.status() != LicenseStatus.LICENSED_OFFLINE || !"offline".equals(snapshot.kind())) return false;
        return snapshot.expiresAt()
                .filter(expiry -> !expiry.isBefore(now))
                .filter(expiry -> !expiry.isAfter(now.plus(OFFLINE_EXPIRY_REMINDER_WINDOW)))
                .isPresent();
    }
}
