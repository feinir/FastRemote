// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

public enum AutomationAuditPhase {
    REQUESTED,
    APPROVAL_REQUIRED,
    APPROVED,
    DENIED,
    STARTED,
    SUCCEEDED,
    FAILED,
    CANCELED
}
