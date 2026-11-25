// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

/** Normalized editor text and its lossless on-disk newline representation. */
record EditorFileContent(String text, EditorLineEndings endings) {
    static EditorFileContent read(String raw) {
        return new EditorFileContent(raw.replace("\r\n", "\n").replace('\r', '\n'), EditorLineEndings.from(raw));
    }

    String fileText() { return endings.restore(text); }
}
