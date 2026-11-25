// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.licensing;

import com.ynpe.fastremote.domain.license.LicenseCapability;
import com.ynpe.fastremote.domain.license.LicenseSnapshot;
import com.ynpe.fastremote.domain.license.LicenseStatus;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Local capabilities of the community and store builds, independent of commercial entitlements. */
public final class CommunityLicenseGateway implements LicenseGateway {
    private static final LicenseSnapshot AVAILABLE = new LicenseSnapshot(LicenseStatus.COMMUNITY,
            "community", Optional.empty(), Optional.empty(), Optional.empty(),
            EnumSet.allOf(LicenseCapability.class), "");

    @Override public CompletionStage<LicenseSnapshot> status() {
        return CompletableFuture.completedFuture(AVAILABLE);
    }

    @Override public CompletionStage<Boolean> permits(LicenseCapability capability) {
        return CompletableFuture.completedFuture(AVAILABLE.permits(capability));
    }

    @Override public CompletionStage<LicenseSnapshot> startTrial() { return unsupported(); }
    @Override public CompletionStage<LicenseSnapshot> syncTrial() { return unsupported(); }
    @Override public CompletionStage<LicenseSnapshot> refresh() { return unsupported(); }
    @Override public CompletionStage<LicenseSnapshot> deactivate() { return unsupported(); }

    @Override public CompletionStage<LicenseSnapshot> activate(char[] licenseCode) {
        return rejectLicenseInput(licenseCode);
    }

    @Override public CompletionStage<LicenseSnapshot> activateOffline(char[] licenseContent) {
        return rejectLicenseInput(licenseContent);
    }

    @Override public CompletionStage<String> generateOfflineRequest(char[] licenseCode) {
        return rejectLicenseInput(licenseCode);
    }

    @Override public void close() { }

    private static <T> CompletionStage<T> rejectLicenseInput(char[] input) {
        if (input != null) Arrays.fill(input, '\0');
        return unsupported();
    }

    private static <T> CompletionStage<T> unsupported() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "This edition does not use commercial activation"));
    }
}
