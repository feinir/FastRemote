// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommandPaletteSettingsTest {
    @Test
    void defaultsMatchTheFocusedInitialCommandPaletteConfiguration() {
        CommandPaletteSettings settings = CommandPaletteSettings.defaults();

        assertTrue(settings.searchHosts());
        assertTrue(settings.searchCommands());
        assertFalse(settings.searchVariables());
        assertTrue(settings.searchFavoritePaths());
        assertFalse(settings.pinyinSearch());
        assertTrue(settings.showCommandSend());
        assertFalse(settings.showCommandPaste());
        assertFalse(settings.showCommandEdit());
        assertTrue(settings.showHostConnect());
        assertFalse(settings.showHostSftp());
        assertFalse(settings.showHostEdit());
        assertTrue(settings.showFavoritePathGoTo());
        assertTrue(settings.showFavoritePathPaste());
    }
}
