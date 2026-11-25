// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LicenseReminderPolicyTest {
    @Test
    void modalStartupDialogDefersWithoutConsumingTheUnauthorizedReminder() {
        LicenseReminderPolicy policy = new LicenseReminderPolicy();

        assertFalse(policy.claim(true));
        assertFalse(policy.wasShown());
        assertTrue(policy.claim(false));
        assertTrue(policy.wasShown());
        assertFalse(policy.claim(false));
    }

    @Test
    void aLaterAuthorizedStateAllowsAFutureUnauthorizedEpisodeToNotifyAgain() {
        LicenseReminderPolicy policy = new LicenseReminderPolicy();
        assertTrue(policy.claim(false));

        policy.markAuthorized();

        assertFalse(policy.wasShown());
        assertTrue(policy.claim(false));
    }
}
