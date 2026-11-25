// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

public record LibraryImportPreview(
        int commandCount,
        int variableCount,
        int commandConflictCount,
        int variableConflictCount) {
    public LibraryImportPreview {
        if (commandCount < 0 || variableCount < 0 || commandConflictCount < 0 || variableConflictCount < 0) {
            throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d3c3cc4a51b56dcd"));
        }
    }

    public boolean hasConflicts() {
        return commandConflictCount > 0 || variableConflictCount > 0;
    }
}
