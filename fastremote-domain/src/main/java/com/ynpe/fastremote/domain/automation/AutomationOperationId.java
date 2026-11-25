// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import java.util.Objects;
import java.util.UUID;

public record AutomationOperationId(UUID value) {
    public AutomationOperationId { Objects.requireNonNull(value, "value"); }
    public static AutomationOperationId create() { return new AutomationOperationId(UUID.randomUUID()); }
    public static AutomationOperationId parse(String value) { return new AutomationOperationId(UUID.fromString(value)); }
    @Override public String toString() { return value.toString(); }
}
