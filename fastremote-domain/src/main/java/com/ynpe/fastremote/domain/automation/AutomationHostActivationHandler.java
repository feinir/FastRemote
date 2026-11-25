// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import com.ynpe.fastremote.domain.catalog.HostId;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface AutomationHostActivationHandler {
    CompletionStage<Boolean> activate(HostId hostId);
}
