// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.Locale;

/** Global terminal presentation preferences without JavaFX or JediTermFX types. */
public record TerminalPreferences(
        TerminalColorScheme colorScheme,
        String fontFamily,
        int fontSize,
        TerminalCursorShape cursorShape,
        boolean cursorBlink,
        Optional<TerminalColorPalette> customColorPalette,
        List<TerminalColorPalette> importedColorPalettes,
        TerminalType terminalType,
        int initialColumns,
        int initialRows,
        boolean openHyperlinksOnClick,
        RemoteProbeSettings remoteProbeSettings,
        TextAppearance textAppearance,
        boolean highlightHyperlinksOnHover) {
    public static final String DEFAULT_TERMINAL_FONT_FAMILY = "JetBrains Mono NL";
    public static final int MIN_FONT_SIZE = 9;
    public static final int MAX_FONT_SIZE = 32;
    public static final int MIN_INITIAL_COLUMNS = 20;
    public static final int MAX_INITIAL_COLUMNS = 500;
    public static final int MIN_INITIAL_ROWS = 5;
    public static final int MAX_INITIAL_ROWS = 200;
    public static final int DEFAULT_INITIAL_COLUMNS = 140;
    public static final int DEFAULT_INITIAL_ROWS = 32;

    public TerminalPreferences {
        Objects.requireNonNull(textAppearance, "textAppearance");
        Objects.requireNonNull(colorScheme, "colorScheme");
        fontFamily = Objects.requireNonNull(fontFamily, "fontFamily").strip();
        if (fontFamily.isEmpty() || fontFamily.length() > 128) {
            throw new IllegalArgumentException("fontFamily must contain 1 to 128 characters");
        }
        if (fontSize < MIN_FONT_SIZE || fontSize > MAX_FONT_SIZE) {
            throw new IllegalArgumentException("fontSize must be between 9 and 32");
        }
        Objects.requireNonNull(cursorShape, "cursorShape");
        customColorPalette = Objects.requireNonNull(customColorPalette, "customColorPalette");
        importedColorPalettes = List.copyOf(Objects.requireNonNull(importedColorPalettes, "importedColorPalettes"));
        if (importedColorPalettes.size() > 1_000) throw new IllegalArgumentException("At most 1000 imported palettes are supported");
        if (importedColorPalettes.stream().map(TerminalColorPalette::name).distinct().count()
                != importedColorPalettes.size()) throw new IllegalArgumentException("Imported palette names must be unique");
        if (colorScheme == TerminalColorScheme.CUSTOM && customColorPalette.isEmpty()) {
            throw new IllegalArgumentException("CUSTOM color scheme requires a custom palette");
        }
        Objects.requireNonNull(terminalType, "terminalType");
        if (initialColumns < MIN_INITIAL_COLUMNS || initialColumns > MAX_INITIAL_COLUMNS) {
            throw new IllegalArgumentException("initialColumns must be between 20 and 500");
        }
        if (initialRows < MIN_INITIAL_ROWS || initialRows > MAX_INITIAL_ROWS) {
            throw new IllegalArgumentException("initialRows must be between 5 and 200");
        }
        Objects.requireNonNull(remoteProbeSettings, "remoteProbeSettings");
    }

    public TerminalPreferences(
            TerminalColorScheme colorScheme, String fontFamily, int fontSize,
            TerminalCursorShape cursorShape, boolean cursorBlink,
            Optional<TerminalColorPalette> customColorPalette,
            List<TerminalColorPalette> importedColorPalettes,
            TerminalType terminalType, int initialColumns, int initialRows,
            boolean openHyperlinksOnClick, RemoteProbeSettings remoteProbeSettings) {
        this(colorScheme, fontFamily, fontSize, cursorShape, cursorBlink, customColorPalette,
                importedColorPalettes, terminalType, initialColumns, initialRows,
                openHyperlinksOnClick, remoteProbeSettings, TextAppearance.terminalDefaults());
    }

    public TerminalPreferences(
            TerminalColorScheme colorScheme, String fontFamily, int fontSize,
            TerminalCursorShape cursorShape, boolean cursorBlink,
            Optional<TerminalColorPalette> customColorPalette,
            List<TerminalColorPalette> importedColorPalettes,
            TerminalType terminalType, int initialColumns, int initialRows,
            boolean openHyperlinksOnClick, RemoteProbeSettings remoteProbeSettings,
            TextAppearance textAppearance) {
        this(colorScheme, fontFamily, fontSize, cursorShape, cursorBlink, customColorPalette,
                importedColorPalettes, terminalType, initialColumns, initialRows,
                openHyperlinksOnClick, remoteProbeSettings, textAppearance, true);
    }

    public TerminalPreferences withTextAppearance(TextAppearance appearance) {
        return new TerminalPreferences(colorScheme, fontFamily, fontSize, cursorShape, cursorBlink,
                customColorPalette, importedColorPalettes, terminalType, initialColumns, initialRows,
                openHyperlinksOnClick, remoteProbeSettings, appearance, highlightHyperlinksOnHover);
    }

    public TerminalPreferences withHighlightHyperlinksOnHover(boolean enabled) {
        return new TerminalPreferences(colorScheme, fontFamily, fontSize, cursorShape, cursorBlink,
                customColorPalette, importedColorPalettes, terminalType, initialColumns, initialRows,
                openHyperlinksOnClick, remoteProbeSettings, textAppearance, enabled);
    }

    public TerminalPreferences(
            TerminalColorScheme colorScheme, String fontFamily, int fontSize,
            TerminalCursorShape cursorShape, boolean cursorBlink,
            Optional<TerminalColorPalette> customColorPalette,
            List<TerminalColorPalette> importedColorPalettes,
            TerminalType terminalType, int initialColumns, int initialRows,
            boolean openHyperlinksOnClick) {
        this(colorScheme, fontFamily, fontSize, cursorShape, cursorBlink, customColorPalette,
                importedColorPalettes, terminalType, initialColumns, initialRows,
                openHyperlinksOnClick, RemoteProbeSettings.defaults());
    }

    public TerminalPreferences(
            TerminalColorScheme colorScheme, String fontFamily, int fontSize,
            TerminalCursorShape cursorShape, boolean cursorBlink,
            Optional<TerminalColorPalette> customColorPalette,
            List<TerminalColorPalette> importedColorPalettes,
            TerminalType terminalType, int initialColumns, int initialRows) {
        this(colorScheme, fontFamily, fontSize, cursorShape, cursorBlink, customColorPalette,
                importedColorPalettes, terminalType, initialColumns, initialRows, true,
                RemoteProbeSettings.defaults());
    }

    public TerminalPreferences(
            TerminalColorScheme colorScheme, String fontFamily, int fontSize,
            TerminalCursorShape cursorShape, boolean cursorBlink,
            Optional<TerminalColorPalette> customColorPalette,
            List<TerminalColorPalette> importedColorPalettes) {
        this(colorScheme, fontFamily, fontSize, cursorShape, cursorBlink,
                customColorPalette, importedColorPalettes, TerminalType.XTERM_256COLOR,
                DEFAULT_INITIAL_COLUMNS, DEFAULT_INITIAL_ROWS);
    }

    public TerminalPreferences(
            TerminalColorScheme colorScheme, String fontFamily, int fontSize,
            TerminalCursorShape cursorShape, boolean cursorBlink,
            Optional<TerminalColorPalette> customColorPalette) {
        this(colorScheme, fontFamily, fontSize, cursorShape, cursorBlink, customColorPalette, List.of());
    }

    public TerminalPreferences(
            TerminalColorScheme colorScheme,
            String fontFamily,
            int fontSize,
            TerminalCursorShape cursorShape,
            boolean cursorBlink) {
        this(colorScheme, fontFamily, fontSize, cursorShape, cursorBlink, Optional.empty(), List.of());
    }

    public static TerminalPreferences defaults() {
        return new TerminalPreferences(
                TerminalColorScheme.FAST_DARK, DEFAULT_TERMINAL_FONT_FAMILY, 14,
                TerminalCursorShape.VERTICAL_BAR, true, Optional.empty(), List.of(),
                TerminalType.XTERM_256COLOR, DEFAULT_INITIAL_COLUMNS, DEFAULT_INITIAL_ROWS, true,
                RemoteProbeSettings.defaults());
    }

    static String defaultBundledFontFamily(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        return DEFAULT_TERMINAL_FONT_FAMILY;
    }

    public static String defaultInterfaceFontFamily(Locale locale) {
        return switch (InterfaceLanguage.resolveSystem(Objects.requireNonNull(locale, "locale"))) {
            case TRADITIONAL_CHINESE -> "HarmonyOS Sans TC";
            case JAPANESE -> "Noto Sans CJK JP";
            case KOREAN -> "Noto Sans CJK KR";
            case THAI -> "Noto Sans Thai";
            default -> "HarmonyOS Sans SC";
        };
    }
}
