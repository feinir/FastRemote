// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import com.ynpe.fastremote.domain.security.HostKeyPrompt;
import java.util.function.Consumer;

/** Composition-time hooks supplied by the desktop UI; protocol adapters only receive AutomationGateway. */
public interface InteractiveAutomationGateway extends AutomationGateway {
    void setApprovalHandler(AutomationApprovalHandler handler);
    void setHostKeyPrompt(HostKeyPrompt prompt);
    void setHostActivationHandler(AutomationHostActivationHandler handler);
    void setUiOperationHandler(AutomationUiOperationHandler handler);
    void setAuditHandler(Consumer<AutomationAuditEvent> handler);
    void setLibraryChangedHandler(Runnable handler);

    /** Removes callbacks owned by a disposed main window without stopping background automation. */
    default void clearWindowHandlers() { }
}
