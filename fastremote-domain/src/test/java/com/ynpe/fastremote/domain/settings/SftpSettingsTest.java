// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;

class SftpSettingsTest {
    @Test
    void defaultsOpenFilesAndAllowEightMiB() {
        SftpSettings defaults = SftpSettings.defaults();
        assertEquals(8L * 1024L * 1024L, defaults.maxEditableFileBytes());
        assertEquals(SftpDoubleClickAction.OPEN, defaults.doubleClickFileAction());
    }

    @Test
    void acceptsSixtyFourMiBAndRejectsLargerEditorLimits() {
        SftpSettings maximum = new SftpSettings(
                true, 4, SftpBookmarks.empty(), Map.of(), 64L * 1024L * 1024L,
                TerminalPreferences.defaultInterfaceFontFamily(java.util.Locale.getDefault()),
                TerminalPreferences.defaults().fontSize(),
                EditorTheme.DARK, SftpDoubleClickAction.OPEN);
        assertEquals(64L * 1024L * 1024L, maximum.maxEditableFileBytes());

        assertThrows(IllegalArgumentException.class, () -> new SftpSettings(
                true, 4, SftpBookmarks.empty(), Map.of(), 65L * 1024L * 1024L,
                TerminalPreferences.defaultInterfaceFontFamily(java.util.Locale.getDefault()),
                TerminalPreferences.defaults().fontSize(),
                EditorTheme.DARK, SftpDoubleClickAction.OPEN));
    }

    @Test
    void remoteBookmarksNormalizeWindowsSeparatorsAndTrailingSeparatorsBeforeDeduplication() {
        SftpBookmarks bookmarks = new SftpBookmarks(List.of("/local/"), List.of(
                "D:\\FastRemote\\", "D:/FastRemote/", "/srv/releases///"));

        assertEquals(List.of("/local/"), bookmarks.localPaths());
        assertEquals(List.of("D:/FastRemote", "/srv/releases"), bookmarks.remotePaths());
    }
}
