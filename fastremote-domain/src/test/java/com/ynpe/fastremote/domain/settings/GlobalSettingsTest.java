// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GlobalSettingsTest {
    @Test
    void defaultsToCollapsingBothFloatingDocksAtStartup() {
        GlobalSettings defaults = GlobalSettings.defaults();

        assertFalse(defaults.leftDockVisible());
        assertFalse(defaults.rightDockVisible());
        assertFalse(defaults.bottomDockVisible());
        assertEquals(245.0, defaults.leftDockWidth());
        assertEquals(245.0, defaults.rightDockWidth());
        assertEquals(DockDisplayMode.FLOATING, defaults.leftDockDisplayMode());
        assertEquals(DockDisplayMode.FLOATING, defaults.rightDockDisplayMode());
        assertEquals(100.0, defaults.terminalActivityHeight());
        assertEquals(180.0, defaults.sftpActivityHeight());
        assertEquals(DockSide.RIGHT, defaults.hostLibraryDock());
        assertEquals(DockSide.RIGHT, defaults.quickConnectDock());
        assertEquals(DockSide.RIGHT, defaults.snippetsDock());
        assertEquals(DockSide.RIGHT, defaults.variableLibraryDock());
        assertEquals(DockSide.RIGHT, defaults.credentialVaultDock());
        assertEquals(DockSide.RIGHT, defaults.aiAssistantDock());
        assertEquals(DockSide.RIGHT, defaults.diagnosticLogDock());
        assertEquals(DiagnosticLogSettings.defaults(), defaults.diagnosticLogSettings());
        assertTrue(defaults.diagnosticLogSettings().enabled());
        assertEquals(java.util.List.of("QUICK_CONNECT", "HOST_LIBRARY", "CREDENTIAL_VAULT",
                        "SNIPPETS", "VARIABLE_LIBRARY", "AI_ASSISTANT", "DIAGNOSTIC_LOG"),
                defaults.rightToolOrder());
        assertTrue(defaults.leftToolOrder().isEmpty());
        assertEquals(java.util.Set.of(HostTabTitlePart.NAME), defaults.hostTabTitleParts());
        assertTrue(defaults.showSftpDirectoryTree());
        assertFalse(defaults.withShowSftpDirectoryTree(false).showSftpDirectoryTree());
        assertTrue(defaults.withShowSftpDirectoryTree(false)
                .withShowSftpDirectoryTree(true).showSftpDirectoryTree());
        assertTrue(defaults.sftpSettings().showHiddenFiles());
        assertEquals(4, defaults.sftpSettings().concurrentTransfers());
        assertEquals(SftpBookmarks.empty(), defaults.sftpSettings().globalBookmarks());
        assertEquals(TerminalPreferences.defaults(), defaults.terminalPreferences());
        assertEquals(TerminalColorScheme.FAST_DARK, defaults.terminalPreferences().colorScheme());
        assertEquals("JetBrains Mono NL", defaults.terminalPreferences().fontFamily());
        assertEquals(TerminalCursorShape.VERTICAL_BAR, defaults.terminalPreferences().cursorShape());
        assertEquals(TerminalType.XTERM_256COLOR, defaults.terminalPreferences().terminalType());
        assertEquals(140, defaults.terminalPreferences().initialColumns());
        assertEquals(32, defaults.terminalPreferences().initialRows());
        assertTrue(defaults.terminalPreferences().openHyperlinksOnClick());
        assertTrue(defaults.terminalPreferences().highlightHyperlinksOnHover());
        assertTrue(defaults.terminalPreferences().remoteProbeSettings().probeServerInformation());
        assertFalse(defaults.windowSettings().closeTabOnDoubleClick());
        assertTrue(defaults.windowSettings().restoreWindowBounds());
        assertTrue(defaults.windowSettings().confirmExitWithMultipleTabs());
        assertFalse(defaults.windowSettings().exitOnMainWindowClose());
        assertTrue(defaults.windowSettings().macOsMenuBarIconEnabled());
        assertTrue(defaults.windowSettings().showGlobalToolbar());
        assertTrue(defaults.windowSettings().showGlobalStatusBar());
        assertEquals(WindowSettings.defaultToolbarButtons(), defaults.windowSettings().toolbarButtons());
        assertEquals(WorkspaceBlankDoubleClickAction.LOCAL_TERMINAL,
                defaults.windowSettings().workspaceBlankDoubleClickAction());
        assertTrue(defaults.windowSettings().openLocalFilesFromTerminalInCurrentWorkspace());
        assertTrue(defaults.windowSettings().openLocalTerminalFromFilesInCurrentWorkspace());
        assertFalse(defaults.windowSettings().openRemoteEditorInCurrentWorkspace());
        assertTrue(defaults.windowSettings().openLocalEditorInCurrentWorkspace());
        assertFalse(defaults.windowSettings().showLocalFileHiddenFiles());
        assertTrue(defaults.windowSettings().showLocalFileDirectoryTree());
        assertTrue(defaults.windowSettings().lastBounds().isEmpty());
        assertEquals(ApplicationTheme.LIGHT, defaults.windowSettings().applicationTheme());
        assertTrue(defaults.windowSettings().keyboardShortcuts().overrides().isEmpty());
        assertTrue(defaults.terminalPreferences().cursorBlink());
        assertEquals(ConnectionSettings.defaults(), defaults.connectionSettings());
        assertFalse(defaults.connectionSettings().disconnectIdleTerminal());
        assertFalse(defaults.openLocalTerminalOnStartup());
        assertTrue(defaults.terminalPreferences().importedColorPalettes().isEmpty());
    }

    @Test
    void toolbarButtonSelectionIsImmutableAndCanBeEmpty() {
        java.util.Set<GlobalToolbarButton> selected = java.util.EnumSet.of(
                GlobalToolbarButton.GLOBAL_FAVORITES, GlobalToolbarButton.DIAGNOSTIC_LOG);
        WindowSettings settings = WindowSettings.defaults().withToolbarButtons(selected);
        selected.clear();

        assertTrue(WindowSettings.defaults().toolbarButtons().contains(GlobalToolbarButton.COMMAND_LIBRARY));
        assertEquals(java.util.Set.of(GlobalToolbarButton.GLOBAL_FAVORITES,
                GlobalToolbarButton.DIAGNOSTIC_LOG), settings.toolbarButtons());
        assertTrue(WindowSettings.defaults().withToolbarButtons(java.util.Set.of())
                .toolbarButtons().isEmpty());
    }

    @Test
    void globalToolbarVisibilityDefaultsOnAndSurvivesOtherWindowSettingChanges() {
        WindowSettings hidden = WindowSettings.defaults().withShowGlobalToolbar(false);

        assertFalse(hidden.showGlobalToolbar());
        assertFalse(hidden.withToolbarButtons(java.util.Set.of()).showGlobalToolbar());
        assertFalse(hidden.withWorkspaceBlankDoubleClickAction(
                WorkspaceBlankDoubleClickAction.EDITOR).showGlobalToolbar());
        assertTrue(hidden.showGlobalStatusBar());
    }

    @Test
    void globalStatusBarVisibilityDefaultsOnAndSurvivesOtherWindowSettingChanges() {
        WindowSettings hidden = WindowSettings.defaults().withShowGlobalStatusBar(false);

        assertFalse(hidden.showGlobalStatusBar());
        assertFalse(hidden.withToolbarButtons(java.util.Set.of()).showGlobalStatusBar());
        assertFalse(hidden.withWorkspaceBlankDoubleClickAction(
                WorkspaceBlankDoubleClickAction.EDITOR).showGlobalStatusBar());
        assertTrue(hidden.showGlobalToolbar());
    }

    @Test
    void keyboardOverridesAreBoundedAndDefensivelyCopied() {
        java.util.Map<String, String> source = new java.util.LinkedHashMap<>();
        source.put("OPEN_SETTINGS", "Shortcut+Comma");
        KeyboardShortcutSettings settings = new KeyboardShortcutSettings(source);
        source.clear();

        assertEquals(java.util.Map.of("OPEN_SETTINGS", "Shortcut+Comma"), settings.overrides());
        assertThrows(IllegalArgumentException.class,
                () -> new KeyboardShortcutSettings(java.util.Map.of("", "Shortcut+T")));
    }

    @Test
    void rejectsInvalidRememberedActivityHeights() {
        assertThrows(IllegalArgumentException.class,
                () -> GlobalSettings.defaults().withActivityHeights(0.0, 180.0));
        assertThrows(IllegalArgumentException.class,
                () -> GlobalSettings.defaults().withActivityHeights(100.0, 4_001.0));
    }

    @Test
    void rejectsConnectionTimingOutsideTheSupportedBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionSettings(2, 20, 30, false, 30));
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionSettings(15, 20, 30, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionSettings(15, 20, 0, false, 30));
    }

    @Test
    void customTerminalSchemeRequiresAnImportedPalette() {
        assertThrows(IllegalArgumentException.class, () -> new TerminalPreferences(
                TerminalColorScheme.CUSTOM, "Monospaced", 14,
                TerminalCursorShape.BLOCK, false));
    }

    @Test
    void rejectsUnsupportedInitialTerminalMatrices() {
        TerminalPreferences defaults = TerminalPreferences.defaults();
        assertThrows(IllegalArgumentException.class, () -> new TerminalPreferences(
                defaults.colorScheme(), defaults.fontFamily(), defaults.fontSize(), defaults.cursorShape(),
                defaults.cursorBlink(), defaults.customColorPalette(), defaults.importedColorPalettes(),
                TerminalType.XTERM, 19, 35));
        assertThrows(IllegalArgumentException.class, () -> new TerminalPreferences(
                defaults.colorScheme(), defaults.fontFamily(), defaults.fontSize(), defaults.cursorShape(),
                defaults.cursorBlink(), defaults.customColorPalette(), defaults.importedColorPalettes(),
                TerminalType.XTERM, 120, 201));
    }

    @Test
    void terminalTypesUseTheirAdvertisedPtyNames() {
        assertEquals(TerminalType.XTERM_256COLOR, TerminalType.fromPtyName("xterm-256color"));
        assertEquals(TerminalType.LINUX, TerminalType.fromPtyName("LINUX"));
        assertThrows(IllegalArgumentException.class, () -> TerminalType.fromPtyName("unknown"));
    }

    @Test
    void remoteProbeSupplementsAreBoundedAndSelectedBySystem() {
        var settings = new RemoteProbeSettings(true, "linux", "mac", "bsd", "windows");
        assertEquals("linux", settings.supplementFor("Linux"));
        assertEquals("mac", settings.supplementFor("Darwin"));
        assertEquals("bsd", settings.supplementFor("FreeBSD"));
        assertEquals("windows", settings.supplementFor("Windows"));
        assertThrows(IllegalArgumentException.class, () -> new RemoteProbeSettings(
                true, "x".repeat(RemoteProbeSettings.MAX_SUPPLEMENT_CHARS + 1), "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> new RemoteProbeSettings(
                true, "echo x\n".repeat(RemoteProbeSettings.MAX_SUPPLEMENT_COMMANDS + 1), "", "", ""));
        for (String system : RemoteProbeCommandCatalog.systems()) {
            assertFalse(RemoteProbeCommandCatalog.commands(system).isEmpty());
            assertEquals(String.join("\n", RemoteProbeCommandCatalog.commands(system)),
                    RemoteProbeCommandCatalog.displayText(system));
        }
    }
}
