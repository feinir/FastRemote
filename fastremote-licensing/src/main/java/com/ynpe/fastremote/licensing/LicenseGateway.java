// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.licensing;

import com.ynpe.fastremote.domain.license.LicenseCapability;
import com.ynpe.fastremote.domain.license.LicenseSnapshot;
import java.util.concurrent.CompletionStage;

public interface LicenseGateway extends AutoCloseable {
    CompletionStage<LicenseSnapshot> status();
    CompletionStage<LicenseSnapshot> startTrial();
    CompletionStage<LicenseSnapshot> syncTrial();
    CompletionStage<LicenseSnapshot> activate(char[] licenseCode);
    CompletionStage<LicenseSnapshot> refresh();
    CompletionStage<LicenseSnapshot> deactivate();
    CompletionStage<LicenseSnapshot> activateOffline(char[] licenseContent);
    CompletionStage<String> generateOfflineRequest(char[] licenseCode);
    CompletionStage<Boolean> permits(LicenseCapability capability);
    @Override void close();
}
