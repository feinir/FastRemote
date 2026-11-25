// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

/** Accepted work, never evidence of remote success. IDs are scoped to this running application. */
public record WorkspaceOperationReceipt(String operationId, String workspaceId, String generation,
        boolean submitted, String kind, String origin) {
    public String getDetail() {
        if (!submitted) return "Operation was not submitted.";
        return "TERMINAL".equals(kind)
                ? "Command input submitted to the terminal. Execution and exit status are unconfirmed. "
                    + "Use read_workspace_operation with operationId to check the result."
                : "Operation submitted. Use read_workspace_operation with operationId to check the result.";
    }

    public WorkspaceOperationReceipt(String operationId, String workspaceId, String generation, boolean submitted, String kind) {
        this(operationId, workspaceId, generation, submitted, kind, "AI_OR_MCP");
    }
}
