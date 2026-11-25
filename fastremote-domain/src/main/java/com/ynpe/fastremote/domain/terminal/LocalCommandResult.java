// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.terminal;

import java.util.List;
import java.util.Optional;

/** A non-interactive local process result, distinct from a visible terminal submission receipt. */
public record LocalCommandResult(String stdout, String stderr, Optional<Integer> exitCode,
        String status, boolean truncated, boolean outputComplete, String workingDirectory,
        List<String> shell, String notice) {
    public LocalCommandResult { shell = List.copyOf(shell); }
}
