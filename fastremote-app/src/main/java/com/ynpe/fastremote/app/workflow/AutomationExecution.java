// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.automation.AutomationSource;
import com.ynpe.fastremote.domain.automation.AutomationCapability;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import java.util.concurrent.CompletionStage;

/** Shared license, queue, approval and audit boundary for automation operations. */
interface AutomationExecution {
    @FunctionalInterface interface CheckedSupplier<T> { T get() throws Exception; }
    <T> CompletionStage<T> submit(CheckedSupplier<T> action);
    <T> T audited(AutomationSource source, AutomationCapability capability, SavedHost target,
            String summary, String detail, CheckedSupplier<T> action) throws Exception;
    <T> T auditedGlobal(AutomationSource source, AutomationCapability capability, String target,
            String summary, String detail, CheckedSupplier<T> action) throws Exception;
    void requirePermission(AutomationSource source, AutomationCapability capability, SavedHost target,
            String summary, String detail) throws Exception;
}
