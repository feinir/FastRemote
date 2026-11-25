// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

/** Result of an atomic common-command import. */
public record BuiltinSnippetImportResult(
        int importedCommands,
        int overwrittenCommands,
        int skippedCommands) {
    public BuiltinSnippetImportResult {
        if (importedCommands < 0 || overwrittenCommands < 0 || skippedCommands < 0) {
            throw new IllegalArgumentException("Import counts must not be negative");
        }
    }

    public BuiltinSnippetImportResult(int importedCommands, int skippedCommands) {
        this(importedCommands, 0, skippedCommands);
    }
}
