// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static com.ynpe.fastremote.storage.SettingsYamlValues.*;

import com.ynpe.fastremote.domain.settings.GlobalSettings;
import com.ynpe.fastremote.domain.settings.DockSide;
import com.ynpe.fastremote.domain.settings.DockDisplayMode;
import com.ynpe.fastremote.domain.settings.HostTabTitlePart;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** YAML mapping for Global settings. */
final class GlobalSettingsCodec {
    private GlobalSettingsCodec() {}

    static GlobalSettings decodeSettings(Map<String, Object> settings) throws IOException {
        Object value = settings.get("workspace");
        if (!(value instanceof Map<?, ?> raw)) return GlobalSettings.defaults();
        @SuppressWarnings("unchecked")
        Map<String, Object> workspace = (Map<String, Object>) raw;
        TerminalPreferences terminalPreferences = TerminalSettingsCodec.decode(settings);
        return new GlobalSettings(
                bool(workspace, "leftDockVisible"),
                bool(workspace, "rightDockVisible"),
                bool(workspace, "bottomDockVisible"),
                number(workspace, "leftDockWidth"),
                number(workspace, "rightDockWidth"),
                dockSide(workspace, "hostLibraryDock"),
                dockSide(workspace, "quickConnectDock"),
                dockSide(workspace, "snippetsDock"),
                optionalDockSide(workspace, "variableLibraryDock", DockSide.RIGHT),
                dockSide(workspace, "credentialVaultDock"),
                optionalDockSide(workspace, "aiAssistantDock", DockSide.RIGHT),
                optionalDockSide(workspace, "diagnosticLogDock", DockSide.RIGHT),
                stringSet(workspace, "collapsedHostGroups"),
                titleParts(workspace),
                optionalBool(workspace, "showSftpDirectoryTree", true),
                terminalPreferences,
                SftpSettingsCodec.decode(settings),
                ConnectionSettingsCodec.decode(settings),
                optionalBool(workspace, "openLocalTerminalOnStartup", false),
                WindowSettingsCodec.decode(workspace),
                optionalDockDisplayMode(workspace, "leftDockDisplayMode", DockDisplayMode.FLOATING),
                optionalDockDisplayMode(workspace, "rightDockDisplayMode", DockDisplayMode.FLOATING),
                optionalActivityHeight(workspace, "terminalActivityHeight",
                        GlobalSettings.DEFAULT_TERMINAL_ACTIVITY_HEIGHT),
                optionalActivityHeight(workspace, "sftpActivityHeight",
                        GlobalSettings.DEFAULT_SFTP_ACTIVITY_HEIGHT),
                toolOrder(workspace, "leftToolOrder", GlobalSettings.defaultLeftToolOrder()),
                toolOrder(workspace, "rightToolOrder", GlobalSettings.defaultRightToolOrder()),
                CommandPaletteSettingsCodec.decode(workspace), DiagnosticLogSettingsCodec.decode(settings),
                optionalBool(workspace, "aiSessionsCollapsed", false));
    }

    static Map<String, Object> encodeSettings(GlobalSettings settings) {
        Map<String, Object> workspace = new LinkedHashMap<>();
        workspace.put("leftDockVisible", settings.leftDockVisible());
        workspace.put("rightDockVisible", settings.rightDockVisible());
        workspace.put("bottomDockVisible", settings.bottomDockVisible());
        workspace.put("leftDockWidth", settings.leftDockWidth());
        workspace.put("rightDockWidth", settings.rightDockWidth());
        workspace.put("leftDockDisplayMode", settings.leftDockDisplayMode().name());
        workspace.put("rightDockDisplayMode", settings.rightDockDisplayMode().name());
        workspace.put("terminalActivityHeight", settings.terminalActivityHeight());
        workspace.put("sftpActivityHeight", settings.sftpActivityHeight());
        workspace.put("hostLibraryDock", settings.hostLibraryDock().name());
        workspace.put("quickConnectDock", settings.quickConnectDock().name());
        workspace.put("snippetsDock", settings.snippetsDock().name());
        workspace.put("variableLibraryDock", settings.variableLibraryDock().name());
        workspace.put("credentialVaultDock", settings.credentialVaultDock().name());
        workspace.put("aiAssistantDock", settings.aiAssistantDock().name());
        workspace.put("diagnosticLogDock", settings.diagnosticLogDock().name());
        workspace.put("leftToolOrder", settings.leftToolOrder());
        workspace.put("rightToolOrder", settings.rightToolOrder());
        workspace.put("aiSessionsCollapsed", settings.aiSessionsCollapsed());
        workspace.put("collapsedHostGroups", List.copyOf(settings.collapsedHostGroupIds()));
        workspace.put("hostTabTitle", settings.hostTabTitleParts().stream()
                .map(Enum::name).sorted().toList());
        workspace.put("showSftpDirectoryTree", settings.showSftpDirectoryTree());
        workspace.put("openLocalTerminalOnStartup", settings.openLocalTerminalOnStartup());
        workspace.put("commandPalette", CommandPaletteSettingsCodec.encode(settings.commandPaletteSettings()));
        WindowSettingsCodec.encodeInto(workspace, settings.windowSettings());
        Map<String, Object> rootSettings = new LinkedHashMap<>();
        rootSettings.put("workspace", workspace);
        rootSettings.put("diagnosticLog", DiagnosticLogSettingsCodec.encode(settings.diagnosticLogSettings()));
        rootSettings.put("terminal", TerminalSettingsCodec.encode(settings.terminalPreferences()));
        rootSettings.put("connection", ConnectionSettingsCodec.encode(settings.connectionSettings()));
        rootSettings.put("sftp", SftpSettingsCodec.encode(settings.sftpSettings()));
        return rootSettings;
    }

    private static List<String> toolOrder(
            Map<String, Object> workspace, String key, List<String> fallback) throws IOException {
        if (!workspace.containsKey(key)) return fallback;
        return stringList(workspace.get(key), key);
    }

    private static DockSide dockSide(Map<String, Object> values, String key) throws IOException {
        Object value = values.get(key);
        if (value instanceof String text) {
            try {
                return DockSide.valueOf(text);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Global setting '" + key + "' must be LEFT or RIGHT", invalid);
            }
        }
        throw new IOException("Global setting '" + key + "' must be LEFT or RIGHT");
    }

    private static DockSide optionalDockSide(
            Map<String, Object> values, String key, DockSide defaultValue) throws IOException {
        if (!values.containsKey(key)) return defaultValue;
        return dockSide(values, key);
    }

    private static DockDisplayMode optionalDockDisplayMode(
            Map<String, Object> values, String key, DockDisplayMode defaultValue) throws IOException {
        Object value = values.get(key);
        if (value == null) return defaultValue;
        if (value instanceof String text) {
            try {
                return DockDisplayMode.valueOf(text);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Global setting '" + key + "' is invalid", invalid);
            }
        }
        throw new IOException("Global setting '" + key + "' must be a string");
    }

    private static double optionalActivityHeight(
            Map<String, Object> values, String key, double defaultValue) throws IOException {
        Object value = values.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number number) {
            double result = number.doubleValue();
            if (Double.isFinite(result) && result > 0.0 && result <= GlobalSettings.MAX_ACTIVITY_HEIGHT) {
                return result;
            }
        }
        throw new IOException("Global setting '" + key + "' must be a positive number no greater than "
                + GlobalSettings.MAX_ACTIVITY_HEIGHT);
    }

    private static Set<String> stringSet(Map<String, Object> values, String key) throws IOException {
        Object value = values.get(key);
        if (value == null) return Set.of();
        if (!(value instanceof List<?> list)) {
            throw new IOException("Global setting '" + key + "' must be a list of strings");
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new IOException("Global setting '" + key + "' must contain only non-empty strings");
            }
            result.add(text);
        }
        return Set.copyOf(result);
    }

    private static Set<HostTabTitlePart> titleParts(Map<String, Object> values) throws IOException {
        Object value = values.get("hostTabTitle");
        if (value == null) return Set.of(HostTabTitlePart.NAME);
        if (!(value instanceof List<?> list)) {
            throw new IOException("Global setting 'hostTabTitle' must be a list");
        }
        Set<HostTabTitlePart> result = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String text)) {
                throw new IOException("Global setting 'hostTabTitle' contains an invalid value");
            }
            try {
                result.add(HostTabTitlePart.valueOf(text));
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Global setting 'hostTabTitle' contains an invalid value", invalid);
            }
        }
        return Set.copyOf(result);
    }
}
