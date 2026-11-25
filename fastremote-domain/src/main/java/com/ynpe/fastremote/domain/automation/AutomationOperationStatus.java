// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import java.util.Objects;
import java.util.Optional;

public record AutomationOperationStatus(
        AutomationOperationId id,
        State state,
        long discoveredBytes,
        long transferredBytes,
        Optional<String> error) {
    public enum State { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELED }

    public AutomationOperationStatus {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(state, "state");
        if (discoveredBytes < 0 || transferredBytes < 0) throw new IllegalArgumentException("byte count is negative");
        error = Objects.requireNonNull(error, "error").map(value -> value.length() > 1_000
                ? value.substring(0, 1_000) : value);
    }
}
