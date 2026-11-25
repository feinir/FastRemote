// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextAppearanceTest {
    @Test void validatesRangesAndKeepsTypographyDuringBookmarkAndVisibilityChanges() {
        assertThrows(IllegalArgumentException.class, () -> new TextAppearance(-15.1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TextAppearance(15.1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TextAppearance(1, 5.1));
        assertThrows(IllegalArgumentException.class, () -> new TextAppearance(0, -5.1));
        assertEquals(-15, new TextAppearance(-15, -5).lineSpacing());
        assertEquals(5, new TextAppearance(15, 5).letterSpacing());
        var appearance = new TextAppearance(-1.2, -0.3);
        assertEquals(new TextAppearance(0.3, -0.3), new TextAppearance(0.1 + 0.2, -0.1 - 0.2));
        assertThrows(IllegalArgumentException.class, () -> new TextAppearance(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new TextAppearance(0, Double.POSITIVE_INFINITY));
        var settings = SftpSettings.defaults().withEditorTextAppearance(appearance);
        var bookmarks = new SftpBookmarks(List.of("/tmp"), List.of("/srv"));
        assertEquals(appearance, settings.withHostBookmarks("host", bookmarks).editorTextAppearance());
        assertEquals(appearance, settings.withShowHiddenFiles(false).editorTextAppearance());
        assertEquals(appearance, TerminalPreferences.defaults().withTextAppearance(appearance).textAppearance());
    }
}
