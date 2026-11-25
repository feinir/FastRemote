// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlatformFileManagerLabelTest {
    @Test
    void selectsFinderExplorerOrGenericFileBrowserByOperatingSystem() {
        assertEquals("legacy.java.1280364b7f8430c6", PlatformFileManagerLabel.revealKey("Mac OS X"));
        assertEquals("legacy.java.9e3fc11213b90ed0", PlatformFileManagerLabel.revealKey("Windows 11"));
        assertEquals("legacy.java.7959616dd346227d", PlatformFileManagerLabel.revealKey("Linux"));
        assertEquals("legacy.java.7959616dd346227d", PlatformFileManagerLabel.revealKey(null));
    }
}
