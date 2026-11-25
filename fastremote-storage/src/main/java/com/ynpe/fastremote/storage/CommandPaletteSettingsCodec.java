// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static com.ynpe.fastremote.storage.SettingsYamlValues.*;

import com.ynpe.fastremote.domain.settings.CommandPaletteSettings;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** YAML mapping for CommandPalette settings. */
final class CommandPaletteSettingsCodec {
    private CommandPaletteSettingsCodec() {}

    static CommandPaletteSettings decode(Map<String, Object> workspace)
            throws IOException {
        CommandPaletteSettings defaults = CommandPaletteSettings.defaults();
        Object value = workspace.get("commandPalette");
        if (value == null) return defaults;
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IOException("Global setting 'commandPalette' must be a mapping");
        }
        @SuppressWarnings("unchecked") Map<String, Object> palette = (Map<String, Object>) raw;
        return new CommandPaletteSettings(
                optionalBool(palette, "searchHosts", defaults.searchHosts()),
                optionalBool(palette, "searchCommands", defaults.searchCommands()),
                optionalBool(palette, "searchVariables", defaults.searchVariables()),
                optionalBool(palette, "searchFavoritePaths", defaults.searchFavoritePaths()),
                optionalBool(palette, "pinyinSearch", defaults.pinyinSearch()),
                optionalBool(palette, "showCommandSend", defaults.showCommandSend()),
                optionalBool(palette, "showCommandPaste", defaults.showCommandPaste()),
                optionalBool(palette, "showCommandEdit", defaults.showCommandEdit()),
                optionalBool(palette, "showHostConnect", defaults.showHostConnect()),
                optionalBool(palette, "showHostSftp", defaults.showHostSftp()),
                optionalBool(palette, "showHostEdit", defaults.showHostEdit()),
                optionalBool(palette, "showFavoritePathGoTo", defaults.showFavoritePathGoTo()),
                optionalBool(palette, "showFavoritePathPaste", defaults.showFavoritePathPaste()));
    }

    static Map<String, Object> encode(CommandPaletteSettings commandPalette) {
        Map<String, Object> paletteValues = new LinkedHashMap<>();
        paletteValues.put("searchHosts", commandPalette.searchHosts());
        paletteValues.put("searchCommands", commandPalette.searchCommands());
        paletteValues.put("searchVariables", commandPalette.searchVariables());
        paletteValues.put("searchFavoritePaths", commandPalette.searchFavoritePaths());
        paletteValues.put("pinyinSearch", commandPalette.pinyinSearch());
        paletteValues.put("showCommandSend", commandPalette.showCommandSend());
        paletteValues.put("showCommandPaste", commandPalette.showCommandPaste());
        paletteValues.put("showCommandEdit", commandPalette.showCommandEdit());
        paletteValues.put("showHostConnect", commandPalette.showHostConnect());
        paletteValues.put("showHostSftp", commandPalette.showHostSftp());
        paletteValues.put("showHostEdit", commandPalette.showHostEdit());
        paletteValues.put("showFavoritePathGoTo", commandPalette.showFavoritePathGoTo());
        paletteValues.put("showFavoritePathPaste", commandPalette.showFavoritePathPaste());
        return paletteValues;
    }
}
