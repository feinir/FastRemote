// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import com.ynpe.fastremote.domain.terminal.TerminalOutput;
import java.util.List;
import java.util.Optional;

public record WorkspaceOperationSnapshot(String operationId, String workspaceId, String generation,
        String kind, String state, Optional<Integer> exitCode, Optional<TerminalOutput> output,
        long totalBytes, long transferredBytes, List<WorkspaceFileResult> files,
        boolean detailsTruncated, String detail, long succeededEntries, long skippedEntries, long failedEntries,
        long nextFileOffset, long retainedFileResults) {
    public WorkspaceOperationSnapshot(String operationId, String workspaceId, String generation,
            String kind, String state, Optional<Integer> exitCode, Optional<TerminalOutput> output,
            long totalBytes, long transferredBytes, List<WorkspaceFileResult> files, boolean detailsTruncated, String detail) {
        this(operationId, workspaceId, generation, kind, state, exitCode, output, totalBytes, transferredBytes,
                files, detailsTruncated, detail, 0, 0, 0, 0, files.size());
    }
    public WorkspaceOperationSnapshot { files = List.copyOf(files); }
    public boolean terminalState() {
        return switch (state) {
            case "SUCCEEDED", "FAILED", "CANCELED", "UNKNOWN", "PARTIAL" -> true;
            default -> false;
        };
    }
}
