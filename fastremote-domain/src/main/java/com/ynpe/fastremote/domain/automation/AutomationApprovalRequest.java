// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import com.ynpe.fastremote.domain.catalog.HostId;
import java.util.Objects;
import java.util.Optional;

public record AutomationApprovalRequest(
        AutomationSource source,
        AutomationCapability capability,
        Optional<HostId> hostId,
        String hostName,
        String summary,
        String detail) {
    public AutomationApprovalRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(capability, "capability");
        hostId = Objects.requireNonNull(hostId, "hostId");
        hostName = bounded(hostName, "hostName", 255);
        summary = bounded(summary, "summary", 500);
        detail = Objects.requireNonNull(detail, "detail");
        if (detail.length() > 64 * 1024) throw new IllegalArgumentException("Approval detail is too large");
    }

    private static String bounded(String value, String name, int maximum) {
        String safe = Objects.requireNonNull(value, name).strip();
        if (safe.isEmpty() || safe.length() > maximum) throw new IllegalArgumentException(name + " is invalid");
        return safe;
    }
}
