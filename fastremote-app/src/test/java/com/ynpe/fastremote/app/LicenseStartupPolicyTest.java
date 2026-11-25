// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.license.LicenseSnapshot;
import com.ynpe.fastremote.domain.license.LicenseStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LicenseStartupPolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    @Test
    void installedTrialAndOnlineEntitlementsAreRefreshed() {
        assertTrue(LicenseStartupPolicy.shouldRefreshOnlineEntitlement(snapshot(
                LicenseStatus.TRIAL_ACTIVE, "trial", NOW.plusSeconds(10 * 86_400))));
        assertTrue(LicenseStartupPolicy.shouldRefreshOnlineEntitlement(snapshot(
                LicenseStatus.EXPIRED, "trial", NOW.minusSeconds(1))));
        assertTrue(LicenseStartupPolicy.shouldRefreshOnlineEntitlement(snapshot(
                LicenseStatus.LICENSED_ONLINE, "online", NOW.plusSeconds(10 * 86_400))));
        assertTrue(LicenseStartupPolicy.shouldRefreshOnlineEntitlement(snapshot(
                LicenseStatus.REVOKED, "online", NOW.plusSeconds(10 * 86_400))));
        assertFalse(LicenseStartupPolicy.shouldRefreshOnlineEntitlement(snapshot(
                LicenseStatus.NEEDS_ACTIVATION, "", null)));
        assertFalse(LicenseStartupPolicy.shouldRefreshOnlineEntitlement(snapshot(
                LicenseStatus.LICENSED_OFFLINE, "offline", NOW.plusSeconds(10 * 86_400))));
    }

    @Test
    void activeTrialIsRemindedOnlyDuringItsFinalThreeDays() {
        assertTrue(LicenseStartupPolicy.shouldRemindTrialExpiry(snapshot(
                LicenseStatus.TRIAL_ACTIVE, "trial", NOW.plusSeconds(3 * 86_400)), NOW));
        assertTrue(LicenseStartupPolicy.shouldRemindTrialExpiry(snapshot(
                LicenseStatus.TRIAL_ACTIVE, "trial", NOW.plusSeconds(60)), NOW));
        assertFalse(LicenseStartupPolicy.shouldRemindTrialExpiry(snapshot(
                LicenseStatus.TRIAL_ACTIVE, "trial", NOW.plusSeconds(3 * 86_400 + 1)), NOW));
        assertFalse(LicenseStartupPolicy.shouldRemindTrialExpiry(snapshot(
                LicenseStatus.EXPIRED, "trial", NOW.minusSeconds(1)), NOW));
        assertFalse(LicenseStartupPolicy.shouldRemindTrialExpiry(snapshot(
                LicenseStatus.LICENSED_ONLINE, "online", NOW.plusSeconds(60)), NOW));
    }

    @Test
    void offlineLicenseIsRemindedOnlyDuringItsFinalThreeDays() {
        assertTrue(LicenseStartupPolicy.shouldRemindOfflineExpiry(snapshot(
                LicenseStatus.LICENSED_OFFLINE, "offline", NOW.plusSeconds(3 * 86_400)), NOW));
        assertFalse(LicenseStartupPolicy.shouldRemindOfflineExpiry(snapshot(
                LicenseStatus.LICENSED_OFFLINE, "offline", NOW.plusSeconds(3 * 86_400 + 1)), NOW));
        assertFalse(LicenseStartupPolicy.shouldRemindOfflineExpiry(snapshot(
                LicenseStatus.EXPIRED, "offline", NOW.minusSeconds(1)), NOW));
        assertFalse(LicenseStartupPolicy.shouldRemindOfflineExpiry(snapshot(
                LicenseStatus.LICENSED_ONLINE, "online", NOW.plusSeconds(60)), NOW));
    }

    private static LicenseSnapshot snapshot(LicenseStatus status, String kind, Instant expiry) {
        return new LicenseSnapshot(status, kind, Optional.ofNullable(expiry), Optional.empty(), Optional.empty(),
                Set.of(), status.permitsCommercialUse() ? "test-lease" : "");
    }
}
