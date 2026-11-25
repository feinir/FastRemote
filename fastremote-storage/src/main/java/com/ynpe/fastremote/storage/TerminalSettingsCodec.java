// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static com.ynpe.fastremote.storage.SettingsYamlValues.*;

import com.ynpe.fastremote.domain.settings.TerminalColorScheme;
import com.ynpe.fastremote.domain.settings.TerminalCursorShape;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.settings.TextAppearance;
import com.ynpe.fastremote.domain.settings.RemoteProbeSettings;
import com.ynpe.fastremote.domain.settings.TerminalType;
import com.ynpe.fastremote.domain.settings.TerminalColorPalette;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** YAML mapping for Terminal settings. */
final class TerminalSettingsCodec {
    private TerminalSettingsCodec() {}

    static TerminalPreferences decode(Map<String, Object> settings) throws IOException {
        Object value = settings.get("terminal");
        if (value == null) return TerminalPreferences.defaults();
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IOException("Global setting 'terminal' must be a mapping");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> terminal = (Map<String, Object>) raw;
        try {
            int initialColumns = optionalInteger(
                    terminal, "initialColumns", TerminalPreferences.DEFAULT_INITIAL_COLUMNS);
            int initialRows = optionalInteger(
                    terminal, "initialRows", TerminalPreferences.DEFAULT_INITIAL_ROWS);
            // The first editable matrix UI accidentally clamped both fields to the font-size maximum (32).
            // Repair only unmarked files; current versions write the marker and preserve an intentional 32x32.
            if (terminal.get("matrixEditorVersion") == null && initialColumns == 32 && initialRows == 32) {
                initialColumns = TerminalPreferences.DEFAULT_INITIAL_COLUMNS;
                initialRows = TerminalPreferences.DEFAULT_INITIAL_ROWS;
            }
            String fontFamily = string(terminal, "fontFamily");
            if (terminal.get("fontDefaultVersion") == null && isLegacyInterfaceDefaultFont(fontFamily)) {
                fontFamily = TerminalPreferences.DEFAULT_TERMINAL_FONT_FAMILY;
            }
            return new TerminalPreferences(
                    enumValue(terminal, "colorScheme", TerminalColorScheme.class),
                    fontFamily,
                    integer(terminal, "fontSize"),
                    enumValue(terminal, "cursorShape", TerminalCursorShape.class),
                    bool(terminal, "cursorBlink"),
                    optionalPalette(terminal.get("customColorPalette")),
                    palettes(terminal.get("importedColorPalettes")),
                    terminal.get("terminalType") == null ? TerminalPreferences.defaults().terminalType()
                            : TerminalType.fromPtyName(string(terminal, "terminalType")),
                    initialColumns, initialRows,
                    optionalBool(terminal, "openHyperlinksOnClick", true),
                    remoteProbeSettings(terminal.get("remoteProbe")),
                    textAppearance(terminal.get("textAppearance"), TextAppearance.terminalDefaults()),
                    optionalBool(terminal, "highlightHyperlinksOnHover", true));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Global terminal settings are outside the supported range", invalid);
        }
    }

    private static boolean isLegacyInterfaceDefaultFont(String family) {
        return Set.of("HarmonyOS Sans SC", "HarmonyOS Sans TC", "Noto Sans CJK JP",
                "Noto Sans CJK KR", "Noto Sans Thai").contains(family);
    }

    private static RemoteProbeSettings remoteProbeSettings(Object value) throws IOException {
        if (value == null) return RemoteProbeSettings.defaults();
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IOException("Global terminal remoteProbe setting must be a mapping");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> probe = (Map<String, Object>) raw;
        return new RemoteProbeSettings(
                optionalBool(probe, "probeServerInformation", true),
                optionalText(probe, "linuxSupplement"),
                optionalText(probe, "macosSupplement"),
                optionalText(probe, "freebsdSupplement"),
                optionalText(probe, "windowsSupplement"));
    }

    private static List<TerminalColorPalette> palettes(Object value) throws IOException {
        if (value == null) return List.of();
        if (!(value instanceof Map<?, ?> map) || map.size() > 1_000) {
            throw new IOException("Global imported terminal palettes must be a mapping of at most 1000 entries");
        }
        List<TerminalColorPalette> result = new java.util.ArrayList<>(map.size());
        for (Object item : map.values()) result.add(optionalPalette(item).orElseThrow());
        return List.copyOf(result);
    }

    private static java.util.Optional<TerminalColorPalette> optionalPalette(Object value) throws IOException {
        if (value == null) return java.util.Optional.empty();
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IOException("Global setting 'customColorPalette' must be a mapping");
        }
        @SuppressWarnings("unchecked") Map<String, Object> palette = (Map<String, Object>) raw;
        Object ansiValue = palette.get("ansiColors");
        if (!(ansiValue instanceof List<?> ansi) || ansi.size() != TerminalColorPalette.ANSI_COLOR_COUNT) {
            throw new IOException("Global custom terminal palette must contain exactly 16 ANSI colors");
        }
        List<Integer> colors = new java.util.ArrayList<>(ansi.size());
        for (Object color : ansi) colors.add(rgb(color, "ansiColors"));
        try {
            return java.util.Optional.of(new TerminalColorPalette(
                    string(palette, "name"),
                    rgb(palette.get("foreground"), "foreground"),
                    rgb(palette.get("background"), "background"),
                    rgb(palette.get("cursor"), "cursor"),
                    rgb(palette.get("selectionBackground"), "selectionBackground"),
                    colors));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Global custom terminal palette is invalid", invalid);
        }
    }

    private static Map<String, Object> paletteValues(TerminalColorPalette palette) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", palette.name());
        values.put("foreground", rgbHex(palette.foreground()));
        values.put("background", rgbHex(palette.background()));
        values.put("cursor", rgbHex(palette.cursor()));
        values.put("selectionBackground", rgbHex(palette.selectionBackground()));
        values.put("ansiColors", palette.ansiColors().stream()
                .map(TerminalSettingsCodec::rgbHex).toList());
        return values;
    }

    private static int rgb(Object value, String field) throws IOException {
        if (value instanceof String text && text.matches("#[0-9A-Fa-f]{6}")) {
            return Integer.parseInt(text.substring(1), 16);
        }
        if (value instanceof Number number && number.longValue() >= 0 && number.longValue() <= 0xFFFFFFL) {
            return number.intValue();
        }
        throw new IOException("Global terminal color '" + field + "' must be #RRGGBB");
    }

    private static String rgbHex(int value) {
        return String.format("#%06X", value);
    }

    static Map<String, Object> encode(TerminalPreferences terminal) {
        Map<String, Object> terminalValues = new LinkedHashMap<>();
        terminalValues.put("colorScheme", terminal.colorScheme().name());
        terminalValues.put("fontFamily", terminal.fontFamily());
        terminalValues.put("fontSize", terminal.fontSize());
        terminalValues.put("textAppearance", appearanceValues(terminal.textAppearance()));
        terminalValues.put("cursorShape", terminal.cursorShape().name());
        terminalValues.put("cursorBlink", terminal.cursorBlink());
        terminalValues.put("terminalType", terminal.terminalType().ptyName());
        terminalValues.put("initialColumns", terminal.initialColumns());
        terminalValues.put("initialRows", terminal.initialRows());
        terminalValues.put("openHyperlinksOnClick", terminal.openHyperlinksOnClick());
        terminalValues.put("highlightHyperlinksOnHover", terminal.highlightHyperlinksOnHover());
        RemoteProbeSettings probe = terminal.remoteProbeSettings();
        Map<String, Object> probeValues = new LinkedHashMap<>();
        probeValues.put("probeServerInformation", probe.probeServerInformation());
        probeValues.put("linuxSupplement", probe.linuxSupplement());
        probeValues.put("macosSupplement", probe.macosSupplement());
        probeValues.put("freebsdSupplement", probe.freebsdSupplement());
        probeValues.put("windowsSupplement", probe.windowsSupplement());
        terminalValues.put("remoteProbe", probeValues);
        terminalValues.put("matrixEditorVersion", 2);
        terminalValues.put("fontDefaultVersion", 2);
        terminal.customColorPalette().ifPresent(palette ->
                terminalValues.put("customColorPalette", paletteValues(palette)));
        Map<String, Object> importedPalettes = new LinkedHashMap<>();
        terminal.importedColorPalettes().forEach(palette ->
                importedPalettes.put(palette.name(), paletteValues(palette)));
        terminalValues.put("importedColorPalettes", importedPalettes);
        return terminalValues;
    }
}
