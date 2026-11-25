// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import com.ynpe.fastremote.domain.settings.GlobalSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlGlobalConfigStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void allThreeInterfaceThemesRoundTripWithoutChangingEditorOrTerminalSettings() throws Exception {
        var repository = new YamlGlobalSettingsRepository(
                new YamlGlobalConfigStore(temporaryDirectory.resolve("interface-themes.yaml")));
        var initial = repository.load();
        for (var theme : com.ynpe.fastremote.domain.settings.ApplicationTheme.values()) {
            repository.save(initial.withWindowSettings(initial.windowSettings().withAppearanceAndShortcuts(theme, initial.windowSettings().keyboardShortcuts())));
            var restored = repository.load();
            assertEquals(theme, restored.windowSettings().applicationTheme());
            assertEquals(initial.sftpSettings(), restored.sftpSettings());
            assertEquals(initial.terminalPreferences(), restored.terminalPreferences());
        }
    }

    @Test
    void aiSessionSidebarPreferenceRoundTripsAndDefaultsForOlderConfig() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("ai-sidebar.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        for (boolean collapsed : new boolean[] {true, false}) {
            var settings = GlobalSettings.defaults().withAiSessionsCollapsed(collapsed);
            repository.save(settings.withWindowSettings(settings.windowSettings())
                    .withDiagnosticLogSettings(settings.diagnosticLogSettings()));
            assertEquals(collapsed, new YamlGlobalSettingsRepository(store).load().aiSessionsCollapsed());
        }
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings = (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var workspace = (java.util.Map<String, Object>) settings.get("workspace");
            workspace.remove("aiSessionsCollapsed");
            return null;
        });
        assertFalse(repository.load().aiSessionsCollapsed());
    }

    @Test
    void toolbarDisplayModesRoundTripAndLegacyMissingValueDefaultsToIconsAndText() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("toolbar-mode.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        for (var mode : com.ynpe.fastremote.domain.settings.ToolbarDisplayMode.values()) {
            var window = GlobalSettings.defaults().windowSettings().withToolbarDisplayMode(mode)
                    .withShowGlobalToolbar(true).withToolbarButtons(Set.of())
                    .withLocalFileDisplay(true, false)
                    .withLastBounds(new com.ynpe.fastremote.domain.settings.WindowBounds(10, 10, 900, 700));
            repository.save(GlobalSettings.defaults().withWindowSettings(window));
            assertEquals(window, repository.load().windowSettings());
            assertEquals(mode, window.toolbarDisplayMode());
        }
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings = (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var workspace = (java.util.Map<String, Object>) settings.get("workspace");
            workspace.remove("toolbarDisplayMode");
            return null;
        });
        assertEquals(com.ynpe.fastremote.domain.settings.ToolbarDisplayMode.ICONS_AND_TEXT,
                repository.load().windowSettings().toolbarDisplayMode());
    }

    @Test
    void legacyIntegerSpacingLoadsAndBoldIsDiscardedOnSave() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("legacy-spacing.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults());
        store.update(document -> {
            try {
                var settings = YamlGlobalConfigStore.requireMap(document, "settings");
                YamlGlobalConfigStore.requireMap(settings, "terminal").put("textAppearance",
                        java.util.Map.of("lineSpacing", 3, "letterSpacing", 2, "bold", true));
                YamlGlobalConfigStore.requireMap(settings, "sftp").put("editorTextAppearance",
                        java.util.Map.of("lineSpacing", 2, "letterSpacing", 1, "bold", true));
                return null;
            } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
        });
        var loaded = repository.load();
        assertEquals(new com.ynpe.fastremote.domain.settings.TextAppearance(3, 2), loaded.terminalPreferences().textAppearance());
        assertEquals(new com.ynpe.fastremote.domain.settings.TextAppearance(2, 1), loaded.sftpSettings().editorTextAppearance());
        repository.save(loaded);
        assertFalse(Files.readString(temporaryDirectory.resolve("legacy-spacing.yaml")).contains("bold:"));
    }

    @Test
    void oldTypographyDefaultsAndInvalidSpacingAreHandledAtStorageBoundary() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("typography.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults());
        store.update(document -> {
            try {
                var settings = YamlGlobalConfigStore.requireMap(document, "settings");
                YamlGlobalConfigStore.requireMap(settings, "terminal").remove("textAppearance");
                YamlGlobalConfigStore.requireMap(settings, "sftp").remove("editorTextAppearance");
                return null;
            } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
        });
        var loaded = repository.load();
        assertEquals(com.ynpe.fastremote.domain.settings.TextAppearance.terminalDefaults(), loaded.terminalPreferences().textAppearance());
        assertEquals(com.ynpe.fastremote.domain.settings.TextAppearance.editorDefaults(), loaded.sftpSettings().editorTextAppearance());
        store.update(document -> {
            try {
                var settings = YamlGlobalConfigStore.requireMap(document, "settings");
                YamlGlobalConfigStore.requireMap(settings, "sftp").put("editorTextAppearance", java.util.Map.of("letterSpacing", 5.1));
                return null;
            } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
        });
        assertThrows(IOException.class, repository::load);
    }

    @Test
    void writesVersionedYamlAtomicallyAndPreservesSections() throws Exception {
        Path path = temporaryDirectory.resolve("global-config.yaml");
        var store = new YamlGlobalConfigStore(path);
        store.update(document -> {
            try {
                YamlGlobalConfigStore.requireMap(document, "settings").put("theme", "dark");
                return null;
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        });

        assertEquals("dark", YamlGlobalConfigStore.requireMap(store.read(), "settings").get("theme"));
        String yaml = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(yaml.contains("schemaVersion: 2"));
        assertTrue(yaml.contains("credentials:"));
        assertTrue(yaml.contains("tokens:"));
        assertTrue(yaml.contains("credentialGroups:"));
        assertFalse(Files.list(temporaryDirectory)
                .anyMatch(file -> file.getFileName().toString().startsWith(".global-config-")));
    }

    @Test
    void rejectsDuplicateYamlKeys() throws Exception {
        Path path = temporaryDirectory.resolve("invalid.yaml");
        Files.writeString(path, """
                schemaVersion: 1
                settings: {}
                settings: {}
                vault: {}
                credentials: {}
                """);
        var store = new YamlGlobalConfigStore(path);
        assertThrows(IOException.class, store::read);
    }

    @Test
    void ignoresStoredSchemaVersionAndUsesSchemaTwo() throws Exception {
        Path path = temporaryDirectory.resolve("schema-version.yaml");
        var store = new YamlGlobalConfigStore(path);
        store.update(document -> null);
        Files.writeString(path, Files.readString(path).replace("schemaVersion: 2", "schemaVersion: 99"));

        assertEquals(2, store.read().get("schemaVersion"));
    }

    @Test
    void migratesLegacyTokenRecordsOutOfTheSshCredentialSectionWithoutChangingThem() throws Exception {
        Path path = temporaryDirectory.resolve("legacy-token.yaml");
        var store = new YamlGlobalConfigStore(path);
        String tokenId = "ad1f330c-6589-4b99-94b1-0675bc93ae3d";
        java.util.Map<String, Object> encryptedRecord = new java.util.LinkedHashMap<>();
        encryptedRecord.put("cryptoVersion", 1);
        encryptedRecord.put("type", "TOKEN");
        encryptedRecord.put("nonce", "unchanged-nonce");
        encryptedRecord.put("ciphertext", "unchanged-ciphertext");
        encryptedRecord.put("createdAt", 1787112000000L);
        encryptedRecord.put("updatedAt", 1787112000000L);
        store.update(document -> {
            try {
                YamlGlobalConfigStore.requireMap(document, "tokens").put(tokenId, encryptedRecord);
                return null;
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        });
        String legacyYaml = Files.readString(path)
                .replace("schemaVersion: 2", "schemaVersion: 1")
                .replace("credentials: {}\ntokens:", "credentials:");
        assertFalse(legacyYaml.contains("tokens:"));
        Files.writeString(path, legacyYaml);

        var migratedStore = new YamlGlobalConfigStore(path);
        java.util.Map<String, Object> migrated = migratedStore.read();

        assertTrue(YamlGlobalConfigStore.requireMap(migrated, "credentials").isEmpty());
        assertEquals(encryptedRecord,
                YamlGlobalConfigStore.requireMap(migrated, "tokens").get(tokenId));
        assertEquals(2, migrated.get("schemaVersion"));
        String yaml = Files.readString(path);
        assertTrue(yaml.contains("unchanged-ciphertext"));
        assertFalse(yaml.contains("schemaVersion: 1"));
    }

    @Test
    void storesWorkspacePreferencesSeparatelyFromCredentials() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("settings.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        assertEquals(GlobalSettings.defaults(), repository.load());
        assertEquals(com.ynpe.fastremote.domain.settings.EditorTheme.DARK,
                repository.load().sftpSettings().editorTheme());

        var importedPalette = new com.ynpe.fastremote.domain.settings.TerminalColorPalette(
                "Imported Ocean", 0xeeeeee, 0x101820, 0xffffff, 0x234567,
                java.util.stream.IntStream.range(0, 16).boxed().toList());
        GlobalSettings changed = new GlobalSettings(false, true, false, 312.5, 271.0,
                com.ynpe.fastremote.domain.settings.DockSide.RIGHT,
                com.ynpe.fastremote.domain.settings.DockSide.LEFT,
                com.ynpe.fastremote.domain.settings.DockSide.RIGHT,
                com.ynpe.fastremote.domain.settings.DockSide.LEFT,
                com.ynpe.fastremote.domain.settings.DockSide.LEFT,
                com.ynpe.fastremote.domain.settings.DockSide.RIGHT,
                Set.of("c4ea6365-4026-4bf7-8de0-005032de281f",
                        "credential:446c7ebc-488f-4a4d-9897-a090e13da701",
                        "snippet:de9eb81d-0e4e-493b-a371-3bb588bde7ae"),
                Set.of(com.ynpe.fastremote.domain.settings.HostTabTitlePart.USER,
                        com.ynpe.fastremote.domain.settings.HostTabTitlePart.HOST), false,
                new com.ynpe.fastremote.domain.settings.TerminalPreferences(
                        com.ynpe.fastremote.domain.settings.TerminalColorScheme.CUSTOM,
                        "Menlo", 17,
                        com.ynpe.fastremote.domain.settings.TerminalCursorShape.UNDERLINE, false,
                        java.util.Optional.of(importedPalette), java.util.List.of(importedPalette),
                        com.ynpe.fastremote.domain.settings.TerminalType.LINUX, 132, 41, false,
                        new com.ynpe.fastremote.domain.settings.RemoteProbeSettings(
                                true, "uname -a", "sw_vers", "freebsd-version", "Get-ComputerInfo"),
                        new com.ynpe.fastremote.domain.settings.TextAppearance(-14.9, 4.9)),
                new com.ynpe.fastremote.domain.settings.SftpSettings(false, 7,
                        new com.ynpe.fastremote.domain.settings.SftpBookmarks(
                                java.util.List.of("/Users/example/work"), java.util.List.of("/srv/www")),
                        java.util.Map.of("8f638b6d-9278-48fa-8c6f-ecc8a6be092e",
                                new com.ynpe.fastremote.domain.settings.SftpBookmarks(
                                        java.util.List.of("/tmp"), java.util.List.of("/var/log"))),
                        3L * 1024L * 1024L, "Noto Sans Mono", 16,
                        com.ynpe.fastremote.domain.settings.EditorTheme.LIGHT,
                        com.ynpe.fastremote.domain.settings.SftpDoubleClickAction.TRANSFER,
                        new com.ynpe.fastremote.domain.settings.TextAppearance(14.9, -4.9)),
                new com.ynpe.fastremote.domain.settings.ConnectionSettings(27, 41, 12, true, 45), true)
                .withWindowSettings(new com.ynpe.fastremote.domain.settings.WindowSettings(
                        false, true, java.util.Optional.of(
                                new com.ynpe.fastremote.domain.settings.WindowBounds(-1220.0, 64.0, 1360.0, 820.0)),
                        com.ynpe.fastremote.domain.settings.ApplicationTheme.DARK,
                        new com.ynpe.fastremote.domain.settings.KeyboardShortcutSettings(
                                java.util.Map.of("OPEN_SETTINGS", "Shortcut+Shift+Comma")),
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.SYSTEM,
                        false, true, true, 175).withToolbarButtons(java.util.Set.of(
                                com.ynpe.fastremote.domain.settings.GlobalToolbarButton.GLOBAL_FAVORITES,
                                com.ynpe.fastremote.domain.settings.GlobalToolbarButton.AI_ASSISTANT))
                        .withWorkspaceBlankDoubleClickAction(
                                com.ynpe.fastremote.domain.settings.WorkspaceBlankDoubleClickAction.EDITOR)
                        .withShowGlobalToolbar(false)
                        .withShowGlobalStatusBar(false)
                        .withRelatedPanelPlacement(false, true, false, false, false, true)
                        .withLocalFileDisplay(true, false))
                .withDockDisplayModes(
                        com.ynpe.fastremote.domain.settings.DockDisplayMode.FLOATING,
                        com.ynpe.fastremote.domain.settings.DockDisplayMode.EMBEDDED)
                .withActivityHeights(164.0, 238.0);
        changed = changed.withToolWindowLayout(
                com.ynpe.fastremote.domain.settings.DockSide.LEFT,
                java.util.List.of("CREDENTIAL_VAULT", "VARIABLE_LIBRARY", "QUICK_CONNECT", "HOST_LIBRARY"),
                java.util.List.of("DIAGNOSTIC_LOG", "AI_ASSISTANT", "SNIPPETS"));
        changed = changed.withCommandPaletteSettings(new com.ynpe.fastremote.domain.settings.CommandPaletteSettings(
                false, true, true, true, true, false, true, false, true, false, true, false, true));
        changed = changed.withDiagnosticLogSettings(new com.ynpe.fastremote.domain.settings.DiagnosticLogSettings(
                false, temporaryDirectory.resolve("durable-logs").toString()));
        repository.save(changed);
        assertEquals(changed, repository.load());
        @SuppressWarnings("unchecked") var savedWorkspace = (java.util.Map<String, Object>)
                YamlGlobalConfigStore.requireMap(store.read(), "settings").get("workspace");
        @SuppressWarnings("unchecked") var commandPalette =
                (java.util.Map<String, Object>) savedWorkspace.get("commandPalette");
        assertEquals(false, commandPalette.get("searchHosts"));
        assertEquals(true, commandPalette.get("searchVariables"));
        assertEquals(true, commandPalette.get("searchFavoritePaths"));
        assertEquals(true, commandPalette.get("pinyinSearch"));
        assertEquals(false, commandPalette.get("showHostSftp"));
        assertEquals(true, savedWorkspace.get("forceHiDpi"));
        assertEquals(175, savedWorkspace.get("hiDpiScalePercent"));
        assertEquals(java.util.List.of("AI_ASSISTANT", "GLOBAL_FAVORITES"),
                savedWorkspace.get("toolbarButtons"));
        assertEquals(false, savedWorkspace.get("showGlobalToolbar"));
        assertEquals(false, savedWorkspace.get("showGlobalStatusBar"));
        assertEquals(false, savedWorkspace.get("openSftpFromTerminalInCurrentWorkspace"));
        assertEquals(true, savedWorkspace.get("openTerminalFromSftpInCurrentWorkspace"));
        assertEquals(false, savedWorkspace.get("openRemoteEditorInCurrentWorkspace"));
        assertEquals(false, savedWorkspace.get("openLocalEditorInCurrentWorkspace"));
        assertEquals("EDITOR", savedWorkspace.get("workspaceBlankDoubleClickAction"));
        assertFalse(savedWorkspace.containsKey("linuxForceHiDpi"));
        assertEquals(false, commandPalette.get("showFavoritePathGoTo"));
        assertEquals(true, commandPalette.get("showFavoritePathPaste"));
        @SuppressWarnings("unchecked") var connection = (java.util.Map<String, Object>)
                YamlGlobalConfigStore.requireMap(store.read(), "settings").get("connection");
        assertEquals(27, connection.get("connectTimeoutSeconds"));
        assertEquals(true, connection.get("disconnectIdleTerminal"));
        @SuppressWarnings("unchecked") var sftp = (java.util.Map<String, Object>)
                YamlGlobalConfigStore.requireMap(store.read(), "settings").get("sftp");
        assertEquals(3L * 1024L * 1024L, ((Number) sftp.get("maxEditableFileBytes")).longValue());
        assertEquals("Noto Sans Mono", sftp.get("editorFontFamily"));
        assertEquals(16, sftp.get("editorFontSize"));
        assertEquals("LIGHT", sftp.get("editorTheme"));
        assertEquals("TRANSFER", sftp.get("doubleClickFileAction"));
        @SuppressWarnings("unchecked") var terminal = (java.util.Map<String, Object>)
                YamlGlobalConfigStore.requireMap(store.read(), "settings").get("terminal");
        assertEquals("linux", terminal.get("terminalType"));
        assertEquals(132, terminal.get("initialColumns"));
        assertEquals(41, terminal.get("initialRows"));
        assertEquals(false, terminal.get("openHyperlinksOnClick"));
        @SuppressWarnings("unchecked") var remoteProbe =
                (java.util.Map<String, Object>) terminal.get("remoteProbe");
        assertFalse(remoteProbe.containsKey("injectOsc7"));
        assertEquals("uname -a", remoteProbe.get("linuxSupplement"));
        @SuppressWarnings("unchecked") var workspace = (java.util.Map<String, Object>)
                YamlGlobalConfigStore.requireMap(store.read(), "settings").get("workspace");
        assertEquals(false, workspace.get("closeTabOnDoubleClick"));
        assertEquals(true, workspace.get("restoreWindowBounds"));
        assertEquals(false, workspace.get("confirmExitWithMultipleTabs"));
        assertEquals(true, workspace.get("exitOnMainWindowClose"));
        assertEquals("DARK", workspace.get("applicationTheme"));
        @SuppressWarnings("unchecked") var shortcuts =
                (java.util.Map<String, Object>) workspace.get("keyboardShortcuts");
        assertEquals("Shortcut+Shift+Comma", shortcuts.get("OPEN_SETTINGS"));
        assertEquals("FLOATING", workspace.get("leftDockDisplayMode"));
        assertEquals("EMBEDDED", workspace.get("rightDockDisplayMode"));
        assertEquals(164.0, workspace.get("terminalActivityHeight"));
        assertEquals(238.0, workspace.get("sftpActivityHeight"));
        assertEquals("RIGHT", workspace.get("diagnosticLogDock"));
        @SuppressWarnings("unchecked") var diagnosticLog = (java.util.Map<String, Object>)
                YamlGlobalConfigStore.requireMap(store.read(), "settings").get("diagnosticLog");
        assertEquals(false, diagnosticLog.get("enabled"));
        assertEquals(temporaryDirectory.resolve("durable-logs").toString(), diagnosticLog.get("directory"));
        @SuppressWarnings("unchecked") var bounds = (java.util.Map<String, Object>) workspace.get("windowBounds");
        assertEquals(-1220.0, bounds.get("x"));
        assertEquals(1360.0, bounds.get("width"));
        assertTrue(YamlGlobalConfigStore.requireMap(store.read(), "credentials").isEmpty());
    }

    @Test
    void optionalToolbarButtonsUseProductDefaultsWhenAbsent() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("toolbar-buttons.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults().withWindowSettings(
                com.ynpe.fastremote.domain.settings.WindowSettings.defaults()
                        .withToolbarButtons(java.util.Set.of())));
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings =
                    (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var workspace =
                    (java.util.Map<String, Object>) settings.get("workspace");
            workspace.remove("toolbarButtons");
            return null;
        });

        assertEquals(com.ynpe.fastremote.domain.settings.WindowSettings.defaultToolbarButtons(),
                repository.load().windowSettings().toolbarButtons());
    }

    @Test
    void commandLibraryToolbarChoiceSurvivesSaveAndReload() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("command-toolbar.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        var button = com.ynpe.fastremote.domain.settings.GlobalToolbarButton.COMMAND_LIBRARY;
        for (var buttons : java.util.List.of(java.util.Set.of(button),
                java.util.Set.of(com.ynpe.fastremote.domain.settings.GlobalToolbarButton.AI_ASSISTANT),
                java.util.Set.of(com.ynpe.fastremote.domain.settings.GlobalToolbarButton.AI_ASSISTANT_DIALOG),
                java.util.Set.of(com.ynpe.fastremote.domain.settings.GlobalToolbarButton.AI_ASSISTANT,
                        com.ynpe.fastremote.domain.settings.GlobalToolbarButton.AI_ASSISTANT_DIALOG),
                java.util.Set.<com.ynpe.fastremote.domain.settings.GlobalToolbarButton>of())) {
            repository.save(GlobalSettings.defaults().withWindowSettings(
                    com.ynpe.fastremote.domain.settings.WindowSettings.defaults().withToolbarButtons(buttons)));
            assertEquals(buttons, repository.load().windowSettings().toolbarButtons());
        }
    }

    @Test
    void globalToolbarDefaultsVisibleWhenSettingIsAbsent() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("global-toolbar.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults().withWindowSettings(
                com.ynpe.fastremote.domain.settings.WindowSettings.defaults()
                        .withShowGlobalToolbar(false)));
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings =
                    (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var workspace =
                    (java.util.Map<String, Object>) settings.get("workspace");
            workspace.remove("showGlobalToolbar");
            return null;
        });

        assertTrue(repository.load().windowSettings().showGlobalToolbar());
    }

    @Test
    void globalStatusBarDefaultsVisibleWhenSettingIsAbsent() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("global-status-bar.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults().withWindowSettings(
                com.ynpe.fastremote.domain.settings.WindowSettings.defaults()
                        .withShowGlobalStatusBar(false)));
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings =
                    (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var workspace =
                    (java.util.Map<String, Object>) settings.get("workspace");
            workspace.remove("showGlobalStatusBar");
            return null;
        });

        assertTrue(repository.load().windowSettings().showGlobalStatusBar());
    }

    @Test
    void relatedPanelPlacementUsesProductDefaultsWhenSettingIsAbsent() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("related-panels.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults().withWindowSettings(
                com.ynpe.fastremote.domain.settings.WindowSettings.defaults()
                        .withRelatedPanelPlacement(false, false, false, false, false)));
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings =
                    (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var workspace =
                    (java.util.Map<String, Object>) settings.get("workspace");
            workspace.remove("openSftpFromTerminalInCurrentWorkspace");
            workspace.remove("openTerminalFromSftpInCurrentWorkspace");
            workspace.remove("openRemoteEditorInCurrentWorkspace");
            workspace.remove("openLocalEditorInCurrentWorkspace");
            workspace.remove("openLocalFilesFromTerminalInCurrentWorkspace");
            workspace.remove("openLocalTerminalFromFilesInCurrentWorkspace");
            return null;
        });

        var loaded = repository.load().windowSettings();
        assertTrue(loaded.openSftpFromTerminalInCurrentWorkspace());
        assertTrue(loaded.openTerminalFromSftpInCurrentWorkspace());
        assertFalse(loaded.openRemoteEditorInCurrentWorkspace());
        assertTrue(loaded.openLocalEditorInCurrentWorkspace());
        assertTrue(loaded.openLocalFilesFromTerminalInCurrentWorkspace());
        assertTrue(loaded.openLocalTerminalFromFilesInCurrentWorkspace());
    }

    @Test
    void localFileDisplayDefaultsToHiddenOffAndTreeOnWhenSettingsAreAbsent() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("local-files.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults().withWindowSettings(
                com.ynpe.fastremote.domain.settings.WindowSettings.defaults()
                        .withLocalFileDisplay(true, false)));
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings =
                    (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var workspace =
                    (java.util.Map<String, Object>) settings.get("workspace");
            workspace.remove("showLocalFileHiddenFiles");
            workspace.remove("showLocalFileDirectoryTree");
            return null;
        });

        var loaded = repository.load().windowSettings();
        assertFalse(loaded.showLocalFileHiddenFiles());
        assertTrue(loaded.showLocalFileDirectoryTree());
    }

    @Test
    void optionalWorkspaceBlankDoubleClickActionDefaultsToLocalTerminalWhenAbsent() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("blank-double-click.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults().withWindowSettings(
                com.ynpe.fastremote.domain.settings.WindowSettings.defaults()
                        .withWorkspaceBlankDoubleClickAction(
                                com.ynpe.fastremote.domain.settings.WorkspaceBlankDoubleClickAction.EDITOR)));
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings =
                    (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var workspace =
                    (java.util.Map<String, Object>) settings.get("workspace");
            workspace.remove("workspaceBlankDoubleClickAction");
            return null;
        });

        assertEquals(com.ynpe.fastremote.domain.settings.WorkspaceBlankDoubleClickAction.LOCAL_TERMINAL,
                repository.load().windowSettings().workspaceBlankDoubleClickAction());
    }

    @Test
    void workspaceTabDoubleClickDefaultsToExpandButPreservesExplicitLegacyCloseChoice()
            throws Exception {
        var store = new YamlGlobalConfigStore(
                temporaryDirectory.resolve("workspace-tab-double-click.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults());
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings =
                    (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var workspace =
                    (java.util.Map<String, Object>) settings.get("workspace");
            workspace.remove("closeTabOnDoubleClick");
            return null;
        });

        assertFalse(repository.load().windowSettings().closeTabOnDoubleClick());

        store.update(document -> {
            @SuppressWarnings("unchecked") var settings =
                    (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var workspace =
                    (java.util.Map<String, Object>) settings.get("workspace");
            workspace.put("closeTabOnDoubleClick", true);
            return null;
        });
        assertTrue(repository.load().windowSettings().closeTabOnDoubleClick());
    }

    @Test
    void legacyNoteSettingsMigrateToEditorNamesOnReadAndSave() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("legacy-editor-names.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults());
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings =
                    (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var workspace =
                    (java.util.Map<String, Object>) settings.get("workspace");
            workspace.put("toolbarButtons", java.util.List.of("GLOBAL_FAVORITES", "NOTES"));
            workspace.put("workspaceBlankDoubleClickAction", "TEXT_NOTE");
            return null;
        });

        var loaded = repository.load();
        assertTrue(loaded.windowSettings().toolbarButtons().contains(
                com.ynpe.fastremote.domain.settings.GlobalToolbarButton.EDITOR));
        assertEquals(com.ynpe.fastremote.domain.settings.WorkspaceBlankDoubleClickAction.EDITOR,
                loaded.windowSettings().workspaceBlankDoubleClickAction());

        repository.save(loaded);
        @SuppressWarnings("unchecked") var savedWorkspace = (java.util.Map<String, Object>)
                YamlGlobalConfigStore.requireMap(store.read(), "settings").get("workspace");
        assertEquals(java.util.List.of("EDITOR", "GLOBAL_FAVORITES"),
                savedWorkspace.get("toolbarButtons"));
        assertEquals("EDITOR", savedWorkspace.get("workspaceBlankDoubleClickAction"));
    }

    @Test
    void readsLegacyLinuxHiDpiFlagAsTwoHundredPercentAndSavesNewKeys() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("legacy-hidpi.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults());
        store.update(document -> {
            try {
                @SuppressWarnings("unchecked") var workspace = (java.util.Map<String, Object>)
                        YamlGlobalConfigStore.requireMap(document, "settings").get("workspace");
                workspace.remove("forceHiDpi");
                workspace.remove("hiDpiScalePercent");
                workspace.put("linuxForceHiDpi", true);
                return null;
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        });

        GlobalSettings migrated = repository.load();
        assertTrue(migrated.windowSettings().forceHiDpi());
        assertEquals(200, migrated.windowSettings().hiDpiScalePercent());
        repository.save(migrated);

        @SuppressWarnings("unchecked") var savedWorkspace = (java.util.Map<String, Object>)
                YamlGlobalConfigStore.requireMap(store.read(), "settings").get("workspace");
        assertEquals(true, savedWorkspace.get("forceHiDpi"));
        assertEquals(200, savedWorkspace.get("hiDpiScalePercent"));
        assertFalse(savedWorkspace.containsKey("linuxForceHiDpi"));
    }

    @Test
    void terminalLinkHighlightAndOpeningRoundTripIndependently() throws Exception {
        var path = temporaryDirectory.resolve("terminal-links.yaml");
        var store = new YamlGlobalConfigStore(path);
        var repository = new YamlGlobalSettingsRepository(store);
        for (boolean highlight : new boolean[] {false, true}) {
            for (boolean directClick : new boolean[] {false, true}) {
                repository.save(GlobalSettings.defaults());
                store.update(document -> {
                    @SuppressWarnings("unchecked") var settings = (java.util.Map<String, Object>) document.get("settings");
                    @SuppressWarnings("unchecked") var terminal = (java.util.Map<String, Object>) settings.get("terminal");
                    terminal.put("highlightHyperlinksOnHover", highlight);
                    terminal.put("openHyperlinksOnClick", directClick);
                    return null;
                });
                var loaded = repository.load();
                repository.save(loaded);
                var reopened = new YamlGlobalSettingsRepository(new YamlGlobalConfigStore(path)).load();
                assertEquals(highlight, reopened.terminalPreferences().highlightHyperlinksOnHover());
                assertEquals(directClick, reopened.terminalPreferences().openHyperlinksOnClick());
                assertEquals(highlight, reopened.terminalPreferences()
                        .withTextAppearance(new com.ynpe.fastremote.domain.settings.TextAppearance(1, 1))
                        .highlightHyperlinksOnHover());
            }
        }
    }

    @Test
    void terminalProtocolFieldsUseNewDefaultsWhenAbsentFromAnExistingSchemaTwoFile() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("terminal-defaults.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults());
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings =
                    (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var terminal =
                    (java.util.Map<String, Object>) settings.get("terminal");
            terminal.remove("terminalType");
            terminal.remove("initialColumns");
            terminal.remove("initialRows");
            terminal.remove("openHyperlinksOnClick");
            terminal.remove("highlightHyperlinksOnHover");
            terminal.remove("remoteProbe");
            return null;
        });

        assertEquals(com.ynpe.fastremote.domain.settings.TerminalType.XTERM_256COLOR,
                repository.load().terminalPreferences().terminalType());
        assertEquals(140, repository.load().terminalPreferences().initialColumns());
        assertEquals(32, repository.load().terminalPreferences().initialRows());
        assertTrue(repository.load().terminalPreferences().openHyperlinksOnClick());
        assertTrue(repository.load().terminalPreferences().highlightHyperlinksOnHover());
        assertEquals(com.ynpe.fastremote.domain.settings.RemoteProbeSettings.defaults(),
                repository.load().terminalPreferences().remoteProbeSettings());
    }

    @Test
    void mainWindowCloseKeepsTheProcessRunningWhenTheNewSettingIsAbsent() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("window-close-default.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults().withWindowSettings(
                new com.ynpe.fastremote.domain.settings.WindowSettings(
                        true, true, java.util.Optional.empty(),
                        com.ynpe.fastremote.domain.settings.ApplicationTheme.LIGHT,
                        com.ynpe.fastremote.domain.settings.KeyboardShortcutSettings.defaults(),
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.SYSTEM, true, true)));
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings =
                    (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var workspace =
                    (java.util.Map<String, Object>) settings.get("workspace");
            workspace.remove("exitOnMainWindowClose");
            return null;
        });

        assertFalse(repository.load().windowSettings().exitOnMainWindowClose());
    }

    @Test
    void macOsMenuBarIconDefaultsToEnabledAndPersistsAnExplicitChoice() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("macos-menu-bar.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults());
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings =
                    (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var workspace =
                    (java.util.Map<String, Object>) settings.get("workspace");
            workspace.remove("macOsMenuBarIconEnabled");
            return null;
        });
        assertTrue(repository.load().windowSettings().macOsMenuBarIconEnabled());

        repository.save(GlobalSettings.defaults().withWindowSettings(
                GlobalSettings.defaults().windowSettings().withMacOsMenuBarIconEnabled(false)));
        assertFalse(repository.load().windowSettings().macOsMenuBarIconEnabled());
    }

    @Test
    void repairsTheUnmarkedThirtyTwoByThirtyTwoMatrixProducedByTheOldEditor() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("legacy-matrix.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults());
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings = (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var terminal = (java.util.Map<String, Object>) settings.get("terminal");
            terminal.put("initialColumns", 32);
            terminal.put("initialRows", 32);
            terminal.remove("matrixEditorVersion");
            return null;
        });

        assertEquals(140, repository.load().terminalPreferences().initialColumns());
        assertEquals(32, repository.load().terminalPreferences().initialRows());
    }

    @Test
    void migratesTheUnmarkedInterfaceFontTerminalDefaultToBundledMonospace() throws Exception {
        var store = new YamlGlobalConfigStore(temporaryDirectory.resolve("legacy-terminal-font.yaml"));
        var repository = new YamlGlobalSettingsRepository(store);
        repository.save(GlobalSettings.defaults());
        store.update(document -> {
            @SuppressWarnings("unchecked") var settings = (java.util.Map<String, Object>) document.get("settings");
            @SuppressWarnings("unchecked") var terminal = (java.util.Map<String, Object>) settings.get("terminal");
            terminal.put("fontFamily", "HarmonyOS Sans SC");
            terminal.remove("fontDefaultVersion");
            return null;
        });

        assertEquals("JetBrains Mono NL", repository.load().terminalPreferences().fontFamily());
    }

}
