// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Secret-safe event emitted for every automation permission decision and side effect. */
public record AutomationAuditEvent(
        Instant timestamp,
        AutomationSource source,
        AutomationAuditPhase phase,
        AutomationCapability capability,
        String action,
        String target,
        String detail,
        Optional<Duration> duration) {
    public AutomationAuditEvent {
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        source = Objects.requireNonNull(source, "source");
        phase = Objects.requireNonNull(phase, "phase");
        capability = Objects.requireNonNull(capability, "capability");
        action = bounded(action, "action", 500);
        target = bounded(target, "target", 1_000);
        detail = bounded(detail, "detail", 4_096);
        duration = Objects.requireNonNull(duration, "duration");
    }

    private static String bounded(String value, String name, int maximum) {
        String safe = Objects.requireNonNullElse(value, "").strip();
        if (safe.length() > maximum) safe = safe.substring(0, maximum) + "…";
        return safe;
    }
}
