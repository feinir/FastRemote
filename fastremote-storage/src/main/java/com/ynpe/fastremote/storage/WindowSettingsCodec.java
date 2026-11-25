// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static com.ynpe.fastremote.storage.SettingsYamlValues.*;

import com.ynpe.fastremote.domain.settings.ApplicationTheme;
import com.ynpe.fastremote.domain.settings.KeyboardShortcutSettings;
import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.domain.settings.WindowBounds;
import com.ynpe.fastremote.domain.settings.WindowSettings;
import com.ynpe.fastremote.domain.settings.GlobalToolbarButton;
import com.ynpe.fastremote.domain.settings.WorkspaceBlankDoubleClickAction;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** YAML mapping for Window settings. */
final class WindowSettingsCodec {
    private WindowSettingsCodec() {}

    static WindowSettings decode(Map<String, Object> workspace) throws IOException {
        boolean closeTab = optionalBool(workspace, "closeTabOnDoubleClick", false);
        boolean restoreBounds = optionalBool(workspace, "restoreWindowBounds", true);
        boolean confirmExitWithMultipleTabs = optionalBool(workspace, "confirmExitWithMultipleTabs", true);
        boolean exitOnMainWindowClose = optionalBool(workspace, "exitOnMainWindowClose", false);
        boolean macOsMenuBarIconEnabled = optionalBool(workspace, "macOsMenuBarIconEnabled", true);
        boolean showGlobalToolbar = optionalBool(workspace, "showGlobalToolbar", true);
        boolean showGlobalStatusBar = optionalBool(workspace, "showGlobalStatusBar", true);
        boolean openSftpFromTerminalInCurrentWorkspace = optionalBool(
                workspace, "openSftpFromTerminalInCurrentWorkspace", true);
        boolean openTerminalFromSftpInCurrentWorkspace = optionalBool(
                workspace, "openTerminalFromSftpInCurrentWorkspace", true);
        boolean openRemoteEditorInCurrentWorkspace = optionalBool(
                workspace, "openRemoteEditorInCurrentWorkspace", false);
        boolean openLocalEditorInCurrentWorkspace = optionalBool(
                workspace, "openLocalEditorInCurrentWorkspace", true);
        boolean openLocalFilesFromTerminalInCurrentWorkspace = optionalBool(
                workspace, "openLocalFilesFromTerminalInCurrentWorkspace", true);
        boolean openLocalTerminalFromFilesInCurrentWorkspace = optionalBool(
                workspace, "openLocalTerminalFromFilesInCurrentWorkspace", true);
        boolean showLocalFileHiddenFiles = optionalBool(
                workspace, "showLocalFileHiddenFiles", false);
        boolean showLocalFileDirectoryTree = optionalBool(
                workspace, "showLocalFileDirectoryTree", true);
        boolean forceHiDpi = workspace.containsKey("forceHiDpi")
                ? optionalBool(workspace, "forceHiDpi", false)
                : optionalBool(workspace, "linuxForceHiDpi", false);
        int hiDpiScalePercent = optionalInteger(workspace, "hiDpiScalePercent",
                WindowSettings.DEFAULT_HI_DPI_SCALE_PERCENT);
        if (!WindowSettings.isSupportedHiDpiScale(hiDpiScalePercent)) {
            throw new IOException("Global setting 'hiDpiScalePercent' is unsupported");
        }
        ApplicationTheme theme = applicationTheme(workspace);
        InterfaceLanguage language = InterfaceLanguage.fromPersistedTag(
                optionalText(workspace, "interfaceLanguage"));
        KeyboardShortcutSettings shortcuts = keyboardShortcuts(workspace);
        Set<GlobalToolbarButton> toolbarButtons = toolbarButtons(workspace);
        var toolbarDisplayMode = toolbarDisplayMode(workspace);
        WorkspaceBlankDoubleClickAction blankDoubleClickAction = workspace.containsKey(
                "workspaceBlankDoubleClickAction")
                ? workspaceBlankDoubleClickAction(workspace)
                : WorkspaceBlankDoubleClickAction.LOCAL_TERMINAL;
        Object rawBounds = workspace.get("windowBounds");
        if (rawBounds == null) return new WindowSettings(
                closeTab, restoreBounds, java.util.Optional.empty(), theme, shortcuts, language,
                confirmExitWithMultipleTabs, exitOnMainWindowClose, macOsMenuBarIconEnabled,
                forceHiDpi, hiDpiScalePercent, toolbarButtons, blankDoubleClickAction,
                showGlobalToolbar, showGlobalStatusBar,
                openSftpFromTerminalInCurrentWorkspace,
                openTerminalFromSftpInCurrentWorkspace,
                openRemoteEditorInCurrentWorkspace,
                openLocalEditorInCurrentWorkspace,
                openLocalFilesFromTerminalInCurrentWorkspace,
                openLocalTerminalFromFilesInCurrentWorkspace,
                showLocalFileHiddenFiles, showLocalFileDirectoryTree, toolbarDisplayMode);
        if (!(rawBounds instanceof Map<?, ?> raw)) {
            throw new IOException("Global setting 'windowBounds' must be a mapping");
        }
        @SuppressWarnings("unchecked") Map<String, Object> bounds = (Map<String, Object>) raw;
        try {
            return new WindowSettings(closeTab, restoreBounds, java.util.Optional.of(new WindowBounds(
                    finiteNumber(bounds, "x"), finiteNumber(bounds, "y"),
                    number(bounds, "width"), number(bounds, "height"))), theme, shortcuts, language,
                    confirmExitWithMultipleTabs, exitOnMainWindowClose, macOsMenuBarIconEnabled,
                    forceHiDpi, hiDpiScalePercent, toolbarButtons, blankDoubleClickAction,
                    showGlobalToolbar, showGlobalStatusBar,
                    openSftpFromTerminalInCurrentWorkspace,
                    openTerminalFromSftpInCurrentWorkspace,
                    openRemoteEditorInCurrentWorkspace,
                    openLocalEditorInCurrentWorkspace,
                    openLocalFilesFromTerminalInCurrentWorkspace,
                    openLocalTerminalFromFilesInCurrentWorkspace,
                    showLocalFileHiddenFiles, showLocalFileDirectoryTree, toolbarDisplayMode);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Global setting 'windowBounds' is invalid", invalid);
        }
    }

    private static Set<GlobalToolbarButton> toolbarButtons(Map<String, Object> workspace)
            throws IOException {
        Object value = workspace.get("toolbarButtons");
        if (value == null) return WindowSettings.defaultToolbarButtons();
        if (!(value instanceof List<?> list)) {
            throw new IOException("Global setting 'toolbarButtons' must be a list");
        }
        Set<GlobalToolbarButton> result = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String text)) {
                throw new IOException("Global setting 'toolbarButtons' contains an invalid value");
            }
            try {
                result.add(GlobalToolbarButton.valueOf("NOTES".equals(text) ? "EDITOR" : text));
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Global setting 'toolbarButtons' contains an invalid value", invalid);
            }
        }
        return Set.copyOf(result);
    }

    private static com.ynpe.fastremote.domain.settings.ToolbarDisplayMode toolbarDisplayMode(
            Map<String, Object> workspace) throws IOException {
        Object value = workspace.get("toolbarDisplayMode");
        if (value == null) return com.ynpe.fastremote.domain.settings.ToolbarDisplayMode.ICONS_AND_TEXT;
        try {
            if (value instanceof String text) {
                return com.ynpe.fastremote.domain.settings.ToolbarDisplayMode.valueOf(text);
            }
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid toolbarDisplayMode", invalid);
        }
        throw new IOException("toolbarDisplayMode must be a string");
    }

    private static WorkspaceBlankDoubleClickAction workspaceBlankDoubleClickAction(
            Map<String, Object> workspace) throws IOException {
        Object value = workspace.get("workspaceBlankDoubleClickAction");
        if (value instanceof String text) {
            try {
                return WorkspaceBlankDoubleClickAction.valueOf(
                        "TEXT_NOTE".equals(text) ? "EDITOR" : text);
            } catch (IllegalArgumentException invalid) {
                throw new IOException(
                        "Global setting 'workspaceBlankDoubleClickAction' has an invalid value", invalid);
            }
        }
        throw new IOException("Global setting 'workspaceBlankDoubleClickAction' must be a string");
    }

    private static ApplicationTheme applicationTheme(Map<String, Object> workspace) throws IOException {
        Object value = workspace.get("applicationTheme");
        if (value == null) return ApplicationTheme.LIGHT;
        if (value instanceof String text) {
            try {
                return ApplicationTheme.valueOf(text);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Global setting 'applicationTheme' must be LIGHT, DARK or SLATE", invalid);
            }
        }
        throw new IOException("Global setting 'applicationTheme' must be a string");
    }

    private static KeyboardShortcutSettings keyboardShortcuts(Map<String, Object> workspace) throws IOException {
        Object value = workspace.get("keyboardShortcuts");
        if (value == null) return KeyboardShortcutSettings.defaults();
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IOException("Global setting 'keyboardShortcuts' must be a mapping");
        }
        LinkedHashMap<String, String> overrides = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String action) || action.isBlank()
                    || !(entry.getValue() instanceof String shortcut) || shortcut.isBlank()) {
                throw new IOException("Global setting 'keyboardShortcuts' contains an invalid entry");
            }
            overrides.put(action, shortcut);
        }
        try {
            return new KeyboardShortcutSettings(overrides);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Global setting 'keyboardShortcuts' is invalid", invalid);
        }
    }

    static void encodeInto(Map<String, Object> workspace, WindowSettings window) {
        workspace.put("closeTabOnDoubleClick", window.closeTabOnDoubleClick());
        workspace.put("restoreWindowBounds", window.restoreWindowBounds());
        workspace.put("confirmExitWithMultipleTabs", window.confirmExitWithMultipleTabs());
        workspace.put("exitOnMainWindowClose", window.exitOnMainWindowClose());
        workspace.put("macOsMenuBarIconEnabled", window.macOsMenuBarIconEnabled());
        workspace.put("showGlobalToolbar", window.showGlobalToolbar());
        workspace.put("showGlobalStatusBar", window.showGlobalStatusBar());
        workspace.put("openSftpFromTerminalInCurrentWorkspace",
                window.openSftpFromTerminalInCurrentWorkspace());
        workspace.put("openTerminalFromSftpInCurrentWorkspace",
                window.openTerminalFromSftpInCurrentWorkspace());
        workspace.put("openRemoteEditorInCurrentWorkspace",
                window.openRemoteEditorInCurrentWorkspace());
        workspace.put("openLocalEditorInCurrentWorkspace",
                window.openLocalEditorInCurrentWorkspace());
        workspace.put("openLocalFilesFromTerminalInCurrentWorkspace",
                window.openLocalFilesFromTerminalInCurrentWorkspace());
        workspace.put("openLocalTerminalFromFilesInCurrentWorkspace",
                window.openLocalTerminalFromFilesInCurrentWorkspace());
        workspace.put("showLocalFileHiddenFiles", window.showLocalFileHiddenFiles());
        workspace.put("showLocalFileDirectoryTree", window.showLocalFileDirectoryTree());
        workspace.put("toolbarDisplayMode", window.toolbarDisplayMode().name());
        workspace.put("toolbarButtons", window.toolbarButtons().stream()
                .map(Enum::name).sorted().toList());
        workspace.put("workspaceBlankDoubleClickAction",
                window.workspaceBlankDoubleClickAction().name());
        workspace.put("forceHiDpi", window.forceHiDpi());
        workspace.put("hiDpiScalePercent", window.hiDpiScalePercent());
        workspace.put("applicationTheme", window.applicationTheme().name());
        workspace.put("interfaceLanguage", window.interfaceLanguage().persistedTag());
        workspace.put("keyboardShortcuts", new LinkedHashMap<>(
                window.keyboardShortcuts().overrides()));
        window.lastBounds().ifPresent(bounds -> {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("x", bounds.x());
            values.put("y", bounds.y());
            values.put("width", bounds.width());
            values.put("height", bounds.height());
            workspace.put("windowBounds", values);
        });
    }
}
