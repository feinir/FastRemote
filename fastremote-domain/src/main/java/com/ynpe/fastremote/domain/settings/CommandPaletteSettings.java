// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

/** Controls which catalog entities and entity actions are exposed by the global command palette. */
public record CommandPaletteSettings(
        boolean searchHosts,
        boolean searchCommands,
        boolean searchVariables,
        boolean searchFavoritePaths,
        boolean pinyinSearch,
        boolean showCommandSend,
        boolean showCommandPaste,
        boolean showCommandEdit,
        boolean showHostConnect,
        boolean showHostSftp,
        boolean showHostEdit,
        boolean showFavoritePathGoTo,
        boolean showFavoritePathPaste) {

    public static CommandPaletteSettings defaults() {
        return new CommandPaletteSettings(
                true, true, false, true, false,
                true, false, false,
                true, false, false,
                true, true);
    }
}
