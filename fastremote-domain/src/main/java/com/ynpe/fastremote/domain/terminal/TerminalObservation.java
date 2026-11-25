// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.terminal;

import com.ynpe.fastremote.domain.automation.WorkspaceOperationSnapshot;
import java.util.concurrent.CompletionStage;

/** Bounded, in-memory observation of one terminal connection; no keyboard recording. */
public interface TerminalObservation {
    String generation();
    default java.util.List<String> operationIds() { return java.util.List.of(); }
    default String origin(String id) { return "AI_OR_MCP"; }
    TerminalOutput read(long after, int maximum, boolean screen);
    CompletionStage<String> execute(String command, boolean allowIntegration, String shellFamily);
    WorkspaceOperationSnapshot operation(String id, long after, int maximum);
}
