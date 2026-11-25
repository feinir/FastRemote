// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Main-window interaction and restoration preferences. */
public record WindowSettings(
        boolean closeTabOnDoubleClick,
        boolean restoreWindowBounds,
        Optional<WindowBounds> lastBounds,
        ApplicationTheme applicationTheme,
        KeyboardShortcutSettings keyboardShortcuts,
        InterfaceLanguage interfaceLanguage,
        boolean confirmExitWithMultipleTabs,
        boolean exitOnMainWindowClose,
        boolean macOsMenuBarIconEnabled,
        boolean forceHiDpi,
        int hiDpiScalePercent,
        Set<GlobalToolbarButton> toolbarButtons,
        WorkspaceBlankDoubleClickAction workspaceBlankDoubleClickAction,
        boolean showGlobalToolbar,
        boolean showGlobalStatusBar,
        boolean openSftpFromTerminalInCurrentWorkspace,
        boolean openTerminalFromSftpInCurrentWorkspace,
        boolean openRemoteEditorInCurrentWorkspace,
        boolean openLocalEditorInCurrentWorkspace,
        boolean openLocalFilesFromTerminalInCurrentWorkspace,
        boolean openLocalTerminalFromFilesInCurrentWorkspace,
        boolean showLocalFileHiddenFiles,
        boolean showLocalFileDirectoryTree,
        ToolbarDisplayMode toolbarDisplayMode) {
    public static final int DEFAULT_HI_DPI_SCALE_PERCENT = 200;

    public WindowSettings {
        toolbarDisplayMode = Objects.requireNonNull(toolbarDisplayMode, "toolbarDisplayMode");
        lastBounds = Objects.requireNonNull(lastBounds, "lastBounds");
        applicationTheme = Objects.requireNonNull(applicationTheme, "applicationTheme");
        keyboardShortcuts = Objects.requireNonNull(keyboardShortcuts, "keyboardShortcuts");
        interfaceLanguage = Objects.requireNonNull(interfaceLanguage, "interfaceLanguage");
        toolbarButtons = Set.copyOf(Objects.requireNonNull(toolbarButtons, "toolbarButtons"));
        workspaceBlankDoubleClickAction = Objects.requireNonNull(
                workspaceBlankDoubleClickAction, "workspaceBlankDoubleClickAction");
        if (!isSupportedHiDpiScale(hiDpiScalePercent)) {
            throw new IllegalArgumentException("Unsupported HiDPI scale: " + hiDpiScalePercent);
        }
    }

    /** Compatibility constructor for configurations without a toolbar display mode. */
    public WindowSettings(
            boolean closeTabOnDoubleClick,
            boolean restoreWindowBounds,
            Optional<WindowBounds> lastBounds,
            ApplicationTheme applicationTheme,
            KeyboardShortcutSettings keyboardShortcuts,
            InterfaceLanguage interfaceLanguage,
            boolean confirmExitWithMultipleTabs,
            boolean exitOnMainWindowClose,
            boolean macOsMenuBarIconEnabled,
            boolean forceHiDpi,
            int hiDpiScalePercent,
            Set<GlobalToolbarButton> toolbarButtons,
            WorkspaceBlankDoubleClickAction workspaceBlankDoubleClickAction,
            boolean showGlobalToolbar,
            boolean showGlobalStatusBar,
            boolean openSftpFromTerminalInCurrentWorkspace,
            boolean openTerminalFromSftpInCurrentWorkspace,
            boolean openRemoteEditorInCurrentWorkspace,
            boolean openLocalEditorInCurrentWorkspace,
            boolean openLocalFilesFromTerminalInCurrentWorkspace,
            boolean openLocalTerminalFromFilesInCurrentWorkspace,
            boolean showLocalFileHiddenFiles,
            boolean showLocalFileDirectoryTree) {
        this(closeTabOnDoubleClick, restoreWindowBounds, lastBounds, applicationTheme, keyboardShortcuts,
                interfaceLanguage, confirmExitWithMultipleTabs, exitOnMainWindowClose, macOsMenuBarIconEnabled,
                forceHiDpi, hiDpiScalePercent, toolbarButtons, workspaceBlankDoubleClickAction,
                showGlobalToolbar, showGlobalStatusBar, openSftpFromTerminalInCurrentWorkspace,
                openTerminalFromSftpInCurrentWorkspace, openRemoteEditorInCurrentWorkspace,
                openLocalEditorInCurrentWorkspace, openLocalFilesFromTerminalInCurrentWorkspace,
                openLocalTerminalFromFilesInCurrentWorkspace, showLocalFileHiddenFiles,
                showLocalFileDirectoryTree, ToolbarDisplayMode.ICONS_AND_TEXT);
    }

    /** Compatibility constructor for settings created before local-file workspaces existed. */
    public WindowSettings(
            boolean closeTabOnDoubleClick, boolean restoreWindowBounds,
            Optional<WindowBounds> lastBounds, ApplicationTheme applicationTheme,
            KeyboardShortcutSettings keyboardShortcuts, InterfaceLanguage interfaceLanguage,
            boolean confirmExitWithMultipleTabs, boolean exitOnMainWindowClose,
            boolean macOsMenuBarIconEnabled, boolean forceHiDpi, int hiDpiScalePercent,
            Set<GlobalToolbarButton> toolbarButtons,
            WorkspaceBlankDoubleClickAction workspaceBlankDoubleClickAction,
            boolean showGlobalToolbar, boolean showGlobalStatusBar,
            boolean openSftpFromTerminalInCurrentWorkspace,
            boolean openTerminalFromSftpInCurrentWorkspace,
            boolean openRemoteEditorInCurrentWorkspace) {
        this(closeTabOnDoubleClick, restoreWindowBounds, lastBounds, applicationTheme,
                keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent,
                toolbarButtons, workspaceBlankDoubleClickAction, showGlobalToolbar,
                showGlobalStatusBar, openSftpFromTerminalInCurrentWorkspace,
                openTerminalFromSftpInCurrentWorkspace, openRemoteEditorInCurrentWorkspace,
                true, true, true, false, true);
    }

    /** Compatibility constructor for settings created before related-panel placement was configurable. */
    public WindowSettings(
            boolean closeTabOnDoubleClick,
            boolean restoreWindowBounds,
            Optional<WindowBounds> lastBounds,
            ApplicationTheme applicationTheme,
            KeyboardShortcutSettings keyboardShortcuts,
            InterfaceLanguage interfaceLanguage,
            boolean confirmExitWithMultipleTabs,
            boolean exitOnMainWindowClose,
            boolean macOsMenuBarIconEnabled,
            boolean forceHiDpi,
            int hiDpiScalePercent,
            Set<GlobalToolbarButton> toolbarButtons,
            WorkspaceBlankDoubleClickAction workspaceBlankDoubleClickAction,
            boolean showGlobalToolbar,
            boolean showGlobalStatusBar) {
        this(closeTabOnDoubleClick, restoreWindowBounds, lastBounds, applicationTheme,
                keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent,
                toolbarButtons, workspaceBlankDoubleClickAction, showGlobalToolbar,
                showGlobalStatusBar, true, true, false);
    }

    /** Compatibility constructor for settings created before global-status-bar visibility was configurable. */
    public WindowSettings(
            boolean closeTabOnDoubleClick,
            boolean restoreWindowBounds,
            Optional<WindowBounds> lastBounds,
            ApplicationTheme applicationTheme,
            KeyboardShortcutSettings keyboardShortcuts,
            InterfaceLanguage interfaceLanguage,
            boolean confirmExitWithMultipleTabs,
            boolean exitOnMainWindowClose,
            boolean macOsMenuBarIconEnabled,
            boolean forceHiDpi,
            int hiDpiScalePercent,
            Set<GlobalToolbarButton> toolbarButtons,
            WorkspaceBlankDoubleClickAction workspaceBlankDoubleClickAction,
            boolean showGlobalToolbar) {
        this(closeTabOnDoubleClick, restoreWindowBounds, lastBounds, applicationTheme,
                keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent,
                toolbarButtons, workspaceBlankDoubleClickAction, showGlobalToolbar, true);
    }

    /** Compatibility constructor for settings created before global-toolbar visibility was configurable. */
    public WindowSettings(
            boolean closeTabOnDoubleClick,
            boolean restoreWindowBounds,
            Optional<WindowBounds> lastBounds,
            ApplicationTheme applicationTheme,
            KeyboardShortcutSettings keyboardShortcuts,
            InterfaceLanguage interfaceLanguage,
            boolean confirmExitWithMultipleTabs,
            boolean exitOnMainWindowClose,
            boolean macOsMenuBarIconEnabled,
            boolean forceHiDpi,
            int hiDpiScalePercent,
            Set<GlobalToolbarButton> toolbarButtons,
            WorkspaceBlankDoubleClickAction workspaceBlankDoubleClickAction) {
        this(closeTabOnDoubleClick, restoreWindowBounds, lastBounds, applicationTheme,
                keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent,
                toolbarButtons, workspaceBlankDoubleClickAction, true, true);
    }

    /** Compatibility constructor for settings created before workspace-header behavior was configurable. */
    public WindowSettings(
            boolean closeTabOnDoubleClick,
            boolean restoreWindowBounds,
            Optional<WindowBounds> lastBounds,
            ApplicationTheme applicationTheme,
            KeyboardShortcutSettings keyboardShortcuts,
            InterfaceLanguage interfaceLanguage,
            boolean confirmExitWithMultipleTabs,
            boolean exitOnMainWindowClose,
            boolean macOsMenuBarIconEnabled,
            boolean forceHiDpi,
            int hiDpiScalePercent,
            Set<GlobalToolbarButton> toolbarButtons) {
        this(closeTabOnDoubleClick, restoreWindowBounds, lastBounds, applicationTheme,
                keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent,
                toolbarButtons, WorkspaceBlankDoubleClickAction.LOCAL_TERMINAL, true, true);
    }

    /** Compatibility constructor for settings created before optional toolbar buttons existed. */
    public WindowSettings(
            boolean closeTabOnDoubleClick,
            boolean restoreWindowBounds,
            Optional<WindowBounds> lastBounds,
            ApplicationTheme applicationTheme,
            KeyboardShortcutSettings keyboardShortcuts,
            InterfaceLanguage interfaceLanguage,
            boolean confirmExitWithMultipleTabs,
            boolean exitOnMainWindowClose,
            boolean macOsMenuBarIconEnabled,
            boolean forceHiDpi,
            int hiDpiScalePercent) {
        this(closeTabOnDoubleClick, restoreWindowBounds, lastBounds, applicationTheme,
                keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent,
                defaultToolbarButtons());
    }

    /** Compatibility constructor for settings created before a selectable HiDPI scale existed. */
    public WindowSettings(
            boolean closeTabOnDoubleClick,
            boolean restoreWindowBounds,
            Optional<WindowBounds> lastBounds,
            ApplicationTheme applicationTheme,
            KeyboardShortcutSettings keyboardShortcuts,
            InterfaceLanguage interfaceLanguage,
            boolean confirmExitWithMultipleTabs,
            boolean exitOnMainWindowClose,
            boolean forceHiDpi,
            int hiDpiScalePercent) {
        this(closeTabOnDoubleClick, restoreWindowBounds, lastBounds, applicationTheme,
                keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, true, forceHiDpi, hiDpiScalePercent);
    }

    /** Compatibility constructor for settings created before a selectable HiDPI scale existed. */
    public WindowSettings(
            boolean closeTabOnDoubleClick,
            boolean restoreWindowBounds,
            Optional<WindowBounds> lastBounds,
            ApplicationTheme applicationTheme,
            KeyboardShortcutSettings keyboardShortcuts,
            InterfaceLanguage interfaceLanguage,
            boolean confirmExitWithMultipleTabs,
            boolean exitOnMainWindowClose,
            boolean linuxForceHiDpi) {
        this(closeTabOnDoubleClick, restoreWindowBounds, lastBounds, applicationTheme,
                keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, true, linuxForceHiDpi, DEFAULT_HI_DPI_SCALE_PERCENT);
    }

    public WindowSettings(
            boolean closeTabOnDoubleClick,
            boolean restoreWindowBounds,
            Optional<WindowBounds> lastBounds,
            ApplicationTheme applicationTheme,
            KeyboardShortcutSettings keyboardShortcuts,
            InterfaceLanguage interfaceLanguage,
            boolean confirmExitWithMultipleTabs,
            boolean exitOnMainWindowClose) {
        this(closeTabOnDoubleClick, restoreWindowBounds, lastBounds, applicationTheme,
                keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, true, false, DEFAULT_HI_DPI_SCALE_PERCENT);
    }

    public WindowSettings(
            boolean closeTabOnDoubleClick,
            boolean restoreWindowBounds,
            Optional<WindowBounds> lastBounds,
            ApplicationTheme applicationTheme,
            KeyboardShortcutSettings keyboardShortcuts,
            InterfaceLanguage interfaceLanguage,
            boolean confirmExitWithMultipleTabs) {
        this(closeTabOnDoubleClick, restoreWindowBounds, lastBounds, applicationTheme,
                keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs, false);
    }

    public WindowSettings(
            boolean closeTabOnDoubleClick,
            boolean restoreWindowBounds,
            Optional<WindowBounds> lastBounds,
            ApplicationTheme applicationTheme,
            KeyboardShortcutSettings keyboardShortcuts,
            InterfaceLanguage interfaceLanguage) {
        this(closeTabOnDoubleClick, restoreWindowBounds, lastBounds, applicationTheme,
                keyboardShortcuts, interfaceLanguage, true, false);
    }

    public WindowSettings(
            boolean closeTabOnDoubleClick,
            boolean restoreWindowBounds,
            Optional<WindowBounds> lastBounds,
            ApplicationTheme applicationTheme,
            KeyboardShortcutSettings keyboardShortcuts) {
        this(closeTabOnDoubleClick, restoreWindowBounds, lastBounds, applicationTheme,
                keyboardShortcuts, InterfaceLanguage.SYSTEM);
    }

    public WindowSettings(
            boolean closeTabOnDoubleClick,
            boolean restoreWindowBounds,
            Optional<WindowBounds> lastBounds) {
        this(closeTabOnDoubleClick, restoreWindowBounds, lastBounds,
                ApplicationTheme.LIGHT, KeyboardShortcutSettings.defaults());
    }

    public static WindowSettings defaults() {
        return new WindowSettings(false, true, Optional.empty(),
                ApplicationTheme.LIGHT, KeyboardShortcutSettings.defaults(), InterfaceLanguage.SYSTEM,
                true, false, true, false, DEFAULT_HI_DPI_SCALE_PERCENT);
    }

    public WindowSettings withLastBounds(WindowBounds bounds) {
        return new WindowSettings(closeTabOnDoubleClick, restoreWindowBounds, Optional.of(bounds),
                applicationTheme, keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent,
                toolbarButtons, workspaceBlankDoubleClickAction, showGlobalToolbar,
                showGlobalStatusBar, openSftpFromTerminalInCurrentWorkspace,
                openTerminalFromSftpInCurrentWorkspace, openRemoteEditorInCurrentWorkspace,
                openLocalEditorInCurrentWorkspace,
                openLocalFilesFromTerminalInCurrentWorkspace,
                openLocalTerminalFromFilesInCurrentWorkspace,
                showLocalFileHiddenFiles, showLocalFileDirectoryTree, toolbarDisplayMode);
    }

    public WindowSettings withAppearanceAndShortcuts(
            ApplicationTheme theme, KeyboardShortcutSettings shortcuts) {
        return new WindowSettings(closeTabOnDoubleClick, restoreWindowBounds, lastBounds,
                theme, shortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent,
                toolbarButtons, workspaceBlankDoubleClickAction, showGlobalToolbar,
                showGlobalStatusBar, openSftpFromTerminalInCurrentWorkspace,
                openTerminalFromSftpInCurrentWorkspace, openRemoteEditorInCurrentWorkspace,
                openLocalEditorInCurrentWorkspace,
                openLocalFilesFromTerminalInCurrentWorkspace,
                openLocalTerminalFromFilesInCurrentWorkspace,
                showLocalFileHiddenFiles, showLocalFileDirectoryTree, toolbarDisplayMode);
    }

    public WindowSettings withInterfaceLanguage(InterfaceLanguage language) {
        return new WindowSettings(closeTabOnDoubleClick, restoreWindowBounds, lastBounds,
                applicationTheme, keyboardShortcuts, language, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent,
                toolbarButtons, workspaceBlankDoubleClickAction, showGlobalToolbar,
                showGlobalStatusBar, openSftpFromTerminalInCurrentWorkspace,
                openTerminalFromSftpInCurrentWorkspace, openRemoteEditorInCurrentWorkspace,
                openLocalEditorInCurrentWorkspace,
                openLocalFilesFromTerminalInCurrentWorkspace,
                openLocalTerminalFromFilesInCurrentWorkspace,
                showLocalFileHiddenFiles, showLocalFileDirectoryTree, toolbarDisplayMode);
    }

    public WindowSettings withLinuxForceHiDpi(boolean enabled) {
        return withHiDpiSettings(enabled, hiDpiScalePercent);
    }

    public WindowSettings withHiDpiSettings(boolean enabled, int scalePercent) {
        return new WindowSettings(closeTabOnDoubleClick, restoreWindowBounds, lastBounds,
                applicationTheme, keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, enabled, scalePercent, toolbarButtons,
                workspaceBlankDoubleClickAction, showGlobalToolbar, showGlobalStatusBar,
                openSftpFromTerminalInCurrentWorkspace, openTerminalFromSftpInCurrentWorkspace,
                openRemoteEditorInCurrentWorkspace, openLocalEditorInCurrentWorkspace,
                openLocalFilesFromTerminalInCurrentWorkspace,
                openLocalTerminalFromFilesInCurrentWorkspace,
                showLocalFileHiddenFiles, showLocalFileDirectoryTree, toolbarDisplayMode);
    }

    public WindowSettings withMacOsMenuBarIconEnabled(boolean enabled) {
        return new WindowSettings(closeTabOnDoubleClick, restoreWindowBounds, lastBounds,
                applicationTheme, keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, enabled, forceHiDpi, hiDpiScalePercent, toolbarButtons,
                workspaceBlankDoubleClickAction, showGlobalToolbar, showGlobalStatusBar,
                openSftpFromTerminalInCurrentWorkspace, openTerminalFromSftpInCurrentWorkspace,
                openRemoteEditorInCurrentWorkspace, openLocalEditorInCurrentWorkspace,
                openLocalFilesFromTerminalInCurrentWorkspace,
                openLocalTerminalFromFilesInCurrentWorkspace,
                showLocalFileHiddenFiles, showLocalFileDirectoryTree, toolbarDisplayMode);
    }

    public WindowSettings withToolbarButtons(Set<GlobalToolbarButton> buttons) {
        return new WindowSettings(closeTabOnDoubleClick, restoreWindowBounds, lastBounds,
                applicationTheme, keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent, buttons,
                workspaceBlankDoubleClickAction, showGlobalToolbar, showGlobalStatusBar,
                openSftpFromTerminalInCurrentWorkspace, openTerminalFromSftpInCurrentWorkspace,
                openRemoteEditorInCurrentWorkspace, openLocalEditorInCurrentWorkspace,
                openLocalFilesFromTerminalInCurrentWorkspace,
                openLocalTerminalFromFilesInCurrentWorkspace,
                showLocalFileHiddenFiles, showLocalFileDirectoryTree, toolbarDisplayMode);
    }

    public WindowSettings withWorkspaceBlankDoubleClickAction(
            WorkspaceBlankDoubleClickAction action) {
        return new WindowSettings(closeTabOnDoubleClick, restoreWindowBounds, lastBounds,
                applicationTheme, keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent,
                toolbarButtons, action, showGlobalToolbar, showGlobalStatusBar,
                openSftpFromTerminalInCurrentWorkspace, openTerminalFromSftpInCurrentWorkspace,
                openRemoteEditorInCurrentWorkspace, openLocalEditorInCurrentWorkspace,
                openLocalFilesFromTerminalInCurrentWorkspace,
                openLocalTerminalFromFilesInCurrentWorkspace,
                showLocalFileHiddenFiles, showLocalFileDirectoryTree, toolbarDisplayMode);
    }

    public WindowSettings withShowGlobalToolbar(boolean visible) {
        return new WindowSettings(closeTabOnDoubleClick, restoreWindowBounds, lastBounds,
                applicationTheme, keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent,
                toolbarButtons, workspaceBlankDoubleClickAction, visible, showGlobalStatusBar,
                openSftpFromTerminalInCurrentWorkspace, openTerminalFromSftpInCurrentWorkspace,
                openRemoteEditorInCurrentWorkspace, openLocalEditorInCurrentWorkspace,
                openLocalFilesFromTerminalInCurrentWorkspace,
                openLocalTerminalFromFilesInCurrentWorkspace,
                showLocalFileHiddenFiles, showLocalFileDirectoryTree, toolbarDisplayMode);
    }

    public WindowSettings withShowGlobalStatusBar(boolean visible) {
        return new WindowSettings(closeTabOnDoubleClick, restoreWindowBounds, lastBounds,
                applicationTheme, keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent,
                toolbarButtons, workspaceBlankDoubleClickAction, showGlobalToolbar, visible,
                openSftpFromTerminalInCurrentWorkspace, openTerminalFromSftpInCurrentWorkspace,
                openRemoteEditorInCurrentWorkspace, openLocalEditorInCurrentWorkspace,
                openLocalFilesFromTerminalInCurrentWorkspace,
                openLocalTerminalFromFilesInCurrentWorkspace,
                showLocalFileHiddenFiles, showLocalFileDirectoryTree, toolbarDisplayMode);
    }

    public WindowSettings withRelatedPanelPlacement(
            boolean sftpFromTerminalInCurrentWorkspace,
            boolean terminalFromSftpInCurrentWorkspace,
            boolean remoteEditorInCurrentWorkspace,
            boolean localEditorInCurrentWorkspace,
            boolean localFilesFromTerminalInCurrentWorkspace,
            boolean localTerminalFromFilesInCurrentWorkspace) {
        return new WindowSettings(closeTabOnDoubleClick, restoreWindowBounds, lastBounds,
                applicationTheme, keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent,
                toolbarButtons, workspaceBlankDoubleClickAction, showGlobalToolbar,
                showGlobalStatusBar, sftpFromTerminalInCurrentWorkspace,
                terminalFromSftpInCurrentWorkspace, remoteEditorInCurrentWorkspace,
                localEditorInCurrentWorkspace,
                localFilesFromTerminalInCurrentWorkspace, localTerminalFromFilesInCurrentWorkspace,
                showLocalFileHiddenFiles, showLocalFileDirectoryTree, toolbarDisplayMode);
    }

    /** Compatibility overload for callers created before local-editor placement was configurable. */
    public WindowSettings withRelatedPanelPlacement(
            boolean sftpFromTerminalInCurrentWorkspace,
            boolean terminalFromSftpInCurrentWorkspace,
            boolean remoteEditorInCurrentWorkspace,
            boolean localFilesFromTerminalInCurrentWorkspace,
            boolean localTerminalFromFilesInCurrentWorkspace) {
        return withRelatedPanelPlacement(sftpFromTerminalInCurrentWorkspace,
                terminalFromSftpInCurrentWorkspace, remoteEditorInCurrentWorkspace,
                openLocalEditorInCurrentWorkspace, localFilesFromTerminalInCurrentWorkspace,
                localTerminalFromFilesInCurrentWorkspace);
    }

    /** Compatibility overload for callers that only configure remote related panels. */
    public WindowSettings withRelatedPanelPlacement(
            boolean sftpFromTerminalInCurrentWorkspace,
            boolean terminalFromSftpInCurrentWorkspace,
            boolean remoteEditorInCurrentWorkspace) {
        return withRelatedPanelPlacement(sftpFromTerminalInCurrentWorkspace,
                terminalFromSftpInCurrentWorkspace, remoteEditorInCurrentWorkspace,
                openLocalEditorInCurrentWorkspace,
                openLocalFilesFromTerminalInCurrentWorkspace,
                openLocalTerminalFromFilesInCurrentWorkspace);
    }

    public WindowSettings withLocalFileDisplay(boolean showHiddenFiles, boolean showDirectoryTree) {
        return new WindowSettings(closeTabOnDoubleClick, restoreWindowBounds, lastBounds,
                applicationTheme, keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs,
                exitOnMainWindowClose, macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent,
                toolbarButtons, workspaceBlankDoubleClickAction, showGlobalToolbar,
                showGlobalStatusBar, openSftpFromTerminalInCurrentWorkspace,
                openTerminalFromSftpInCurrentWorkspace, openRemoteEditorInCurrentWorkspace,
                openLocalEditorInCurrentWorkspace,
                openLocalFilesFromTerminalInCurrentWorkspace,
                openLocalTerminalFromFilesInCurrentWorkspace, showHiddenFiles, showDirectoryTree, toolbarDisplayMode);
    }

    public WindowSettings withToolbarDisplayMode(ToolbarDisplayMode mode) {
        return new WindowSettings(closeTabOnDoubleClick, restoreWindowBounds, lastBounds, applicationTheme,
                keyboardShortcuts, interfaceLanguage, confirmExitWithMultipleTabs, exitOnMainWindowClose,
                macOsMenuBarIconEnabled, forceHiDpi, hiDpiScalePercent, toolbarButtons,
                workspaceBlankDoubleClickAction, showGlobalToolbar, showGlobalStatusBar,
                openSftpFromTerminalInCurrentWorkspace, openTerminalFromSftpInCurrentWorkspace,
                openRemoteEditorInCurrentWorkspace, openLocalEditorInCurrentWorkspace,
                openLocalFilesFromTerminalInCurrentWorkspace, openLocalTerminalFromFilesInCurrentWorkspace,
                showLocalFileHiddenFiles, showLocalFileDirectoryTree, mode);
    }

    public static Set<GlobalToolbarButton> defaultToolbarButtons() {
        return Set.of(GlobalToolbarButton.COMMAND_LIBRARY, GlobalToolbarButton.GLOBAL_FAVORITES,
                GlobalToolbarButton.EDITOR);
    }

    /** Compatibility accessor for the former Linux-only setting name. */
    public boolean linuxForceHiDpi() {
        return forceHiDpi;
    }

    public static boolean isSupportedHiDpiScale(int scalePercent) {
        return scalePercent == 100 || scalePercent == 125 || scalePercent == 150
                || scalePercent == 175 || scalePercent == 200
                || scalePercent == 300 || scalePercent == 400;
    }
}
