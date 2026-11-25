// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** User overrides for the single built-in keyboard shortcut scheme. */
public record KeyboardShortcutSettings(Map<String, String> overrides) {
    public static final int MAX_OVERRIDES = 256;
    public static final int MAX_VALUE_LENGTH = 96;

    public KeyboardShortcutSettings {
        Objects.requireNonNull(overrides, "overrides");
        if (overrides.size() > MAX_OVERRIDES) {
            throw new IllegalArgumentException("Too many keyboard shortcut overrides");
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        overrides.forEach((action, shortcut) -> {
            if (action == null || action.isBlank() || action.length() > 80
                    || shortcut == null || shortcut.isBlank() || shortcut.length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("Invalid keyboard shortcut override");
            }
            copy.put(action, shortcut);
        });
        overrides = Map.copyOf(copy);
    }

    public static KeyboardShortcutSettings defaults() {
        return new KeyboardShortcutSettings(Map.of());
    }
}
