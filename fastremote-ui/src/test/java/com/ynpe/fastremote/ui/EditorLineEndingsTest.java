// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EditorLineEndingsTest {
    @Test
    void largeUniformFilesDoNotAllocatePerLineMetadata() {
        var content = EditorFileContent.read("x\r\n".repeat(1_000_000));
        assertEquals(1_000_000, content.endings().size());
        assertEquals(0, content.endings().storageBytes());
        assertEquals("x\r\n".repeat(1_000_000), content.fileText());
    }

    @Test
    void mixedFilesUseOneBytePerDelimiterAndPreserveTrailingNewline() {
        String raw = "x\r\ny\rz\n".repeat(100_000);
        var content = EditorFileContent.read(raw);
        assertEquals(300_000, content.endings().storageBytes());
        assertEquals(raw, content.fileText());
        assertEquals("", EditorFileContent.read("").fileText());
        assertEquals("single", EditorFileContent.read("single").fileText());
        assertEquals("\r\n\n\r", EditorFileContent.read("\r\n\n\r").fileText());
    }
}
