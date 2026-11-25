// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.license.LicenseSnapshot;
import com.ynpe.fastremote.domain.license.LicenseStatus;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LicenseBootstrapControllerTest {
    private static final LicenseSnapshot TRIAL = new LicenseSnapshot(
            LicenseStatus.TRIAL_ACTIVE, "trial", Optional.empty(), Optional.empty(),
            Optional.empty(), Set.of(), "test-lease");
    private static final LicenseSnapshot ONLINE = new LicenseSnapshot(
            LicenseStatus.LICENSED_ONLINE, "online", Optional.empty(), Optional.empty(),
            Optional.empty(), Set.of(), "test-lease");

    @Test
    void statusCheckKeepsAnActiveTrialWindowOpenForFormalActivation() {
        assertFalse(LicenseBootstrapController.shouldCompleteAuthorization(
                LicenseBootstrapController.Operation.STATUS_CHECK, TRIAL));
        assertTrue(LicenseBootstrapController.isActiveTrial(TRIAL));
    }

    @Test
    void explicitTrialStartAndFormalActivationStillCompleteNormally() {
        assertTrue(LicenseBootstrapController.shouldCompleteAuthorization(
                LicenseBootstrapController.Operation.START_TRIAL, TRIAL));
        assertTrue(LicenseBootstrapController.shouldCompleteAuthorization(
                LicenseBootstrapController.Operation.FORMAL_ACTIVATION, ONLINE));
        assertFalse(LicenseBootstrapController.shouldCompleteAuthorization(
                LicenseBootstrapController.Operation.FORMAL_ACTIVATION, TRIAL));
        assertTrue(LicenseBootstrapController.shouldCompleteAuthorization(
                LicenseBootstrapController.Operation.STATUS_CHECK, ONLINE));
    }
}
