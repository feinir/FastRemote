// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.util.List;
import java.util.Objects;

/** One portable 16-color terminal palette imported from a user-owned theme file. */
public record TerminalColorPalette(
        String name,
        int foreground,
        int background,
        int cursor,
        int selectionBackground,
        List<Integer> ansiColors) {
    public static final int ANSI_COLOR_COUNT = 16;

    public TerminalColorPalette {
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty() || name.length() > 128) {
            throw new IllegalArgumentException("palette name must contain 1 to 128 characters");
        }
        requireRgb(foreground, "foreground");
        requireRgb(background, "background");
        requireRgb(cursor, "cursor");
        requireRgb(selectionBackground, "selectionBackground");
        ansiColors = List.copyOf(Objects.requireNonNull(ansiColors, "ansiColors"));
        if (ansiColors.size() != ANSI_COLOR_COUNT) {
            throw new IllegalArgumentException("ansiColors must contain exactly 16 colors");
        }
        ansiColors.forEach(value -> requireRgb(Objects.requireNonNull(value, "ANSI color"), "ANSI color"));
    }

    @Override
    public String toString() {
        return name;
    }

    private static void requireRgb(int value, String name) {
        if (value < 0 || value > 0xFFFFFF) {
            throw new IllegalArgumentException(name + " must be a 24-bit RGB value");
        }
    }
}
