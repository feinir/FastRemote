// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.license;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface LicenseAccess {
    CompletionStage<Boolean> permits(LicenseCapability capability);
}
