// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.terminal;

import java.util.Objects;

/** Bounded result of a non-interactive command executed on an authenticated SSH session. */
public record RemoteCommandResult(String standardOutput, String standardError, int exitCode, boolean truncated) {
    public RemoteCommandResult {
        standardOutput = Objects.requireNonNull(standardOutput, "standardOutput");
        standardError = Objects.requireNonNull(standardError, "standardError");
    }
}
