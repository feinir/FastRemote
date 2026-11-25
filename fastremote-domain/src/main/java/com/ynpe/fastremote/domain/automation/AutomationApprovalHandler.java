// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface AutomationApprovalHandler {
    CompletionStage<Boolean> approve(AutomationApprovalRequest request);
}
