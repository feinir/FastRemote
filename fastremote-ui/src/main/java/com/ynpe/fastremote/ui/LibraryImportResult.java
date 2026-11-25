// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

public record LibraryImportResult(int commandCount, int variableCount) {
    public LibraryImportResult {
        if (commandCount < 0 || variableCount < 0) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d3c3cc4a51b56dcd"));
    }
}
