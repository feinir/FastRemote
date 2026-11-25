// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.util.Objects;

/** Portable textual key combinations consumed only by the terminal adapter. */
public record TerminalShortcutSettings(String copy, String paste, String find) {
    public TerminalShortcutSettings {
        copy = require(copy, "copy");
        paste = require(paste, "paste");
        find = require(find, "find");
    }

    private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > KeyboardShortcutSettings.MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Invalid terminal shortcut: " + name);
        }
        return value;
    }
}
