// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.DockSide;
import com.ynpe.fastremote.domain.settings.HostTabTitlePart;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.settings.SftpBookmarks;
import com.ynpe.fastremote.domain.settings.SftpSettings;
import com.ynpe.fastremote.domain.settings.GlobalToolbarButton;
import com.ynpe.fastremote.domain.license.LicenseSnapshot;
import com.ynpe.fastremote.domain.license.LicenseStatus;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConfigurationDialogTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void activeTrialCanBeDeactivated() {
        var trial = new LicenseSnapshot(LicenseStatus.TRIAL_ACTIVE, "trial", Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), "test-lease");
        var invalid = new LicenseSnapshot(LicenseStatus.INTEGRITY_ERROR, "", Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), "");
        var inactive = new LicenseSnapshot(LicenseStatus.NEEDS_ACTIVATION, "", Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), "");

        assertTrue(LicenseSettingsPage.canDeactivateLicense(trial));
        assertTrue(LicenseSettingsPage.canDeactivateLicense(invalid));
        assertFalse(LicenseSettingsPage.canDeactivateLicense(inactive));
    }

    @Test
    void onlyAnActiveTrialOffersFullVersionActivation() {
        var trial = new LicenseSnapshot(LicenseStatus.TRIAL_ACTIVE, "trial", Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), "test-lease");
        var online = new LicenseSnapshot(LicenseStatus.LICENSED_ONLINE, "online", Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), "test-lease");
        var inactive = new LicenseSnapshot(LicenseStatus.NEEDS_ACTIVATION, "", Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), "");

        assertTrue(LicenseSettingsPage.canActivateFullVersion(trial));
        assertFalse(LicenseSettingsPage.canActivateFullVersion(online));
        assertFalse(LicenseSettingsPage.canActivateFullVersion(inactive));
    }

    @Test
    void deactivationSnapshotImmediatelyUpdatesSettingsAndGlobalLicenseControls() {
        var inactive = new LicenseSnapshot(LicenseStatus.NEEDS_ACTIVATION, "", Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), "");
        AtomicReference<LicenseSnapshot> rendered = new AtomicReference<>();
        AtomicReference<LicenseSnapshot> global = new AtomicReference<>();
        AtomicInteger failures = new AtomicInteger();

        LicenseSettingsPage.applyDeactivationResult(inactive, null, rendered::set, global::set,
                ignored -> failures.incrementAndGet());

        assertEquals(inactive, rendered.get());
        assertEquals(inactive, global.get());
        assertEquals(0, failures.get());
    }

    @Test
    void inactiveAndTrialSnapshotsExposeTheirToolbarStatusAndMenuState() {
        var inactive = new LicenseSnapshot(LicenseStatus.NEEDS_ACTIVATION, "", Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), "");
        var active = new LicenseSnapshot(LicenseStatus.TRIAL_ACTIVE, "trial", Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), "test-lease");

        var inactiveControls = MainWindowController.licenseControlState(inactive, null);
        assertEquals(MainWindowController.LicenseActivationStatus.INACTIVE,
                inactiveControls.activationStatus());
        assertFalse(inactiveControls.activateDisabled());
        assertTrue(inactiveControls.deactivateDisabled());

        var activeControls = MainWindowController.licenseControlState(active, null);
        assertEquals(MainWindowController.LicenseActivationStatus.TRIAL,
                activeControls.activationStatus());
        assertTrue(activeControls.activateDisabled());
        assertFalse(activeControls.deactivateDisabled());
    }

    @Test
    void filtersSystemFontsByCaseInsensitiveSubstring() {
        assertEquals(java.util.List.of("JetBrains Mono", "Noto Sans Mono"),
                SettingsFontSelector.filterFonts(
                        java.util.List.of("JetBrains Mono", "Menlo", "Noto Sans Mono"), "MONO"));
        assertEquals(java.util.List.of("Menlo"), SettingsFontSelector.filterFonts(
                java.util.List.of("JetBrains Mono", "Menlo"), "men"));
    }

    @Test
    void settingsNavigationContainsOnlyImplementedConfigurationAreas() {
        assertEquals(java.util.List.of("安全与凭证库", "外观", "工作区", "命令面板", "终端", "连接", "SFTP", "编辑器", "快捷键", "日志", "AI 与 MCP", "配置管理", "软件更新", "许可证"),
                ConfigurationDialog.pageTitles());
        assertEquals(180.0, ConfigurationDialog.NAVIGATION_WIDTH);
        assertEquals(860.0, ConfigurationDialog.DIALOG_MIN_WIDTH);
        assertEquals(620.0, ConfigurationDialog.DIALOG_MIN_HEIGHT);
        assertEquals(15.0, SettingsPageLayout.COMMAND_AREA_WIDTH_REDUCTION);
    }

    @Test
    void terminalLinkControlsKeepHoverAndClickIndependent() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                var preferences = com.ynpe.fastremote.domain.settings.TerminalPreferences.defaults()
                        .withHighlightHyperlinksOnHover(false);
                var controls = TerminalSettingsPage.TerminalControls.from(preferences);
                assertTrue(controls.openHyperlinksOnClick().isSelected());
                assertFalse(controls.highlightHyperlinksOnHover().isSelected());
                for (boolean highlight : new boolean[] {false, true}) {
                    for (boolean direct : new boolean[] {false, true}) {
                        controls.highlightHyperlinksOnHover().setSelected(highlight);
                        controls.openHyperlinksOnClick().setSelected(direct);
                        assertEquals(highlight, controls.preferences().highlightHyperlinksOnHover());
                        assertEquals(direct, controls.preferences().openHyperlinksOnClick());
                    }
                }
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, java.util.concurrent.TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void toolbarButtonControlsDefaultSelectedAndReturnExactSelection() {
        var defaults = WorkspaceSettingsPage.ToolbarButtonsControls.from(
                com.ynpe.fastremote.domain.settings.WindowSettings.defaultToolbarButtons());
        assertEquals(com.ynpe.fastremote.domain.settings.WindowSettings.defaultToolbarButtons(),
                defaults.selectedButtons());

        assertTrue(defaults.commandLibrary().isSelected());
        assertFalse(defaults.aiAssistant().isSelected());
        assertFalse(defaults.aiAssistantDialog().isSelected());
        assertFalse(defaults.diagnosticLog().isSelected());
        defaults.editor().setSelected(false);
        defaults.aiAssistant().setSelected(true);
        defaults.aiAssistantDialog().setSelected(true);
        assertEquals(Set.of(GlobalToolbarButton.COMMAND_LIBRARY, GlobalToolbarButton.GLOBAL_FAVORITES,
                GlobalToolbarButton.AI_ASSISTANT, GlobalToolbarButton.AI_ASSISTANT_DIALOG), defaults.selectedButtons());
        defaults.commandLibrary().setSelected(false);
        assertFalse(defaults.selectedButtons().contains(GlobalToolbarButton.COMMAND_LIBRARY));
    }

    @Test
    void globalToolbarSettingDefaultsToVisible() {
        assertTrue(com.ynpe.fastremote.domain.settings.WindowSettings.defaults()
                .showGlobalToolbar());
        assertTrue(com.ynpe.fastremote.domain.settings.WindowSettings.defaults()
                .showGlobalStatusBar());
        var relatedDefaults = com.ynpe.fastremote.domain.settings.WindowSettings.defaults();
        assertTrue(relatedDefaults.openSftpFromTerminalInCurrentWorkspace());
        assertTrue(relatedDefaults.openTerminalFromSftpInCurrentWorkspace());
        assertFalse(relatedDefaults.openRemoteEditorInCurrentWorkspace());
        assertTrue(relatedDefaults.openLocalEditorInCurrentWorkspace());
        assertEquals("显示全局工具栏",
                com.ynpe.fastremote.i18n.Localizer.forLanguage(
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.SIMPLIFIED_CHINESE)
                        .text("workspace.showGlobalToolbar"));
        assertEquals("界面显示",
                com.ynpe.fastremote.i18n.Localizer.forLanguage(
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.SIMPLIFIED_CHINESE)
                        .text("workspace.interfaceVisibility"));
        assertEquals("显示全局状态栏",
                com.ynpe.fastremote.i18n.Localizer.forLanguage(
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.SIMPLIFIED_CHINESE)
                        .text("workspace.showGlobalStatusBar"));
        assertEquals("关联内容打开位置",
                com.ynpe.fastremote.i18n.Localizer.forLanguage(
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.SIMPLIFIED_CHINESE)
                        .text("workspace.relatedPanels.title"));
        assertEquals("当前工作区",
                com.ynpe.fastremote.i18n.Localizer.forLanguage(
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.SIMPLIFIED_CHINESE)
                        .text("workspace.relatedPanels.currentWorkspace"));
        assertEquals("终端打开 SFTP",
                com.ynpe.fastremote.i18n.Localizer.forLanguage(
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.SIMPLIFIED_CHINESE)
                        .text("workspace.relatedPanels.terminalToSftp"));
        assertEquals("本地文件编辑器",
                com.ynpe.fastremote.i18n.Localizer.forLanguage(
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.SIMPLIFIED_CHINESE)
                        .text("workspace.relatedPanels.localEditor"));
    }

    @Test
    void hiddenToolbarButtonDoesNotReserveLayoutSpace() {
        javafx.scene.control.Button button = new javafx.scene.control.Button();
        MainWindowController.setToolbarButtonVisible(button, false);
        assertFalse(button.isVisible());
        assertFalse(button.isManaged());

        MainWindowController.setToolbarButtonVisible(button, true);
        assertTrue(button.isVisible());
        assertTrue(button.isManaged());
    }

    @Test
    void configurationCenterStartsAtItsMinimumSizeAndAllowsResizing() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.control.Dialog<Void> dialog = ConfigurationDialog.createDialog();
                ConfigurationDialog.configureDialogSizing(dialog);

                assertTrue(dialog.getDialogPane() instanceof InsertionOrderedDialogPane);
                assertTrue(dialog.isResizable());
                assertEquals(ConfigurationDialog.DIALOG_MIN_WIDTH,
                        dialog.getDialogPane().getMinWidth());
                assertEquals(ConfigurationDialog.DIALOG_MIN_WIDTH,
                        dialog.getDialogPane().getPrefWidth());
                assertEquals(ConfigurationDialog.DIALOG_MIN_HEIGHT,
                        dialog.getDialogPane().getMinHeight());
                assertEquals(ConfigurationDialog.DIALOG_MIN_HEIGHT,
                        dialog.getDialogPane().getPrefHeight());
                assertEquals(ConfigurationDialog.DIALOG_MIN_WIDTH,
                        DialogSpacing.configuredResizableWindowWidth(dialog.getDialogPane()));
                assertEquals(ConfigurationDialog.DIALOG_MIN_HEIGHT,
                        DialogSpacing.configuredResizableWindowHeight(dialog.getDialogPane()));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void diagnosticDirectoryChooserMatchesTextFieldHeight() {
        var directory = new javafx.scene.control.TextField();
        var chooser = new javafx.scene.control.Button();

        var row = DiagnosticSettingsPage.diagnosticLogDirectoryRow(directory, chooser);

        assertEquals(DiagnosticSettingsPage.DIAGNOSTIC_DIRECTORY_CONTROL_HEIGHT, directory.getPrefHeight());
        assertEquals(DiagnosticSettingsPage.DIAGNOSTIC_DIRECTORY_CONTROL_HEIGHT, directory.getMaxHeight());
        assertEquals(directory.getPrefHeight(), chooser.getPrefHeight());
        assertEquals(directory.getMaxHeight(), chooser.getMaxHeight());
        assertEquals(directory.getPrefHeight(), row.getPrefHeight());
        assertEquals(directory.getMaxHeight(), row.getMaxHeight());
        assertEquals(390.0, row.getMinWidth());
        assertEquals(470.0, row.getPrefWidth());
        assertEquals(Double.MAX_VALUE, row.getMaxWidth());
        assertEquals(javafx.geometry.Pos.CENTER_LEFT, row.getAlignment());
        assertFalse(row.isFillHeight());
    }

    @Test
    void editorThemeChoiceMatchesTerminalSelectionWidth() {
        var theme = TransferSettingsPage.editorThemeChoice(
                com.ynpe.fastremote.domain.settings.EditorTheme.DARK);

        assertEquals(SettingsPageLayout.SELECTION_CONTROL_MIN_WIDTH, theme.getMinWidth());
        assertEquals(210.0, theme.getMinWidth());
    }

    @Test
    void sftpDoubleClickDefaultsToOpeningFiles() {
        var choice = TransferSettingsPage.sftpDoubleClickActionChoice(
                com.ynpe.fastremote.domain.settings.SftpDoubleClickAction.OPEN);

        assertEquals(com.ynpe.fastremote.domain.settings.SftpDoubleClickAction.OPEN, choice.getValue());
        assertEquals(java.util.List.of(
                com.ynpe.fastremote.domain.settings.SftpDoubleClickAction.OPEN,
                com.ynpe.fastremote.domain.settings.SftpDoubleClickAction.TRANSFER), choice.getItems());
    }

    @Test
    void expandedLanguagesUseCompactShortcutResetLabels() {
        var german = com.ynpe.fastremote.i18n.Localizer.forLanguage(
                com.ynpe.fastremote.domain.settings.InterfaceLanguage.GERMAN);
        assertEquals("Auswahl zurücksetzen", german.text("legacy.java.a540351ed04b81fb"));
        assertEquals("Alles zurücksetzen", german.text("legacy.java.80fba41dc57ad59e"));

        var spanish = com.ynpe.fastremote.i18n.Localizer.forLanguage(
                com.ynpe.fastremote.domain.settings.InterfaceLanguage.SPANISH);
        assertEquals("Restablecer selección", spanish.text("legacy.java.a540351ed04b81fb"));
        assertEquals("Restablecer todo", spanish.text("legacy.java.80fba41dc57ad59e"));

        var portuguese = com.ynpe.fastremote.i18n.Localizer.forLanguage(
                com.ynpe.fastremote.domain.settings.InterfaceLanguage.BRAZILIAN_PORTUGUESE);
        assertEquals("Redefinir seleção", portuguese.text("legacy.java.a540351ed04b81fb"));
        assertEquals("Redefinir tudo", portuguese.text("legacy.java.80fba41dc57ad59e"));
    }

    @Test
    void startupExpansionKeepsOnlyTheTwoSideDockOptions() {
        assertEquals(java.util.List.of(
                        "程序启动时自动展开左侧功能区",
                        "程序启动时自动展开右侧功能区"),
                ConfigurationDialog.startupDockExpansionOptions());
        assertTrue(ConfigurationDialog.startupDockExpansionOptions().stream()
                .noneMatch(value -> value.contains("服务器信息")));
    }

    @Test
    void transientlyClearedColorSelectionFallsBackWithoutThrowing() {
        assertEquals(com.ynpe.fastremote.domain.settings.TerminalColorScheme.FAST_DARK,
                TerminalSettingsPage.selectedColorScheme(null));
        assertEquals(com.ynpe.fastremote.domain.settings.TerminalColorScheme.CUSTOM,
                TerminalSettingsPage.selectedColorScheme("Imported"));
    }

    @Test
    void editableSpinnerUsesItsOwnRangeInsteadOfTheFontSizeRange() {
        assertEquals(140, SettingsPageLayout.clampedSpinnerValue("140", 32, 20, 500));
        assertEquals(32, SettingsPageLayout.clampedSpinnerValue("32", 35, 5, 200));
        assertEquals(500, SettingsPageLayout.clampedSpinnerValue("999", 140, 20, 500));
        assertEquals(140, SettingsPageLayout.clampedSpinnerValue("invalid", 140, 20, 500));
    }

    @Test
    void supplementalProbeEditorAcceptsAtMostThirtyTwoNonBlankCommands() {
        assertTrue(SettingsPageLayout.validSupplementText("ifconfig -a\n\ndf -h"));
        assertTrue(SettingsPageLayout.validSupplementText("echo x\n".repeat(32)));
        assertFalse(SettingsPageLayout.validSupplementText("echo x\n".repeat(33)));
    }

    @Test
    void editTrackerClearsDirtyStateOnlyAfterSavedValuesBecomeTheBaseline() {
        var original = new ConfigurationDialog.WorkspaceLayout(
                true, false, false, DockSide.LEFT, DockSide.LEFT, DockSide.LEFT, DockSide.LEFT,
                Set.of(HostTabTitlePart.NAME), true, TerminalPreferences.defaults());
        var changed = new ConfigurationDialog.WorkspaceLayout(
                true, true, false, DockSide.RIGHT, DockSide.LEFT, DockSide.LEFT, DockSide.RIGHT,
                Set.of(HostTabTitlePart.NAME, HostTabTitlePart.HOST), false,
                new TerminalPreferences(
                        com.ynpe.fastremote.domain.settings.TerminalColorScheme.FAST_LIGHT,
                        "Menlo", 16,
                        com.ynpe.fastremote.domain.settings.TerminalCursorShape.VERTICAL_BAR, false));
        var edits = new ConfigurationSaveController.EditTracker(original);

        assertFalse(edits.isDirty(original));
        assertTrue(edits.isDirty(changed));

        edits.markSaved(changed);

        assertFalse(edits.isDirty(changed));
        assertTrue(edits.isDirty(original));
    }

    @Test
    void immediatelySavedGlobalBookmarksBecomeTheNewBookmarkBaseline() {
        var original = new ConfigurationDialog.WorkspaceLayout(
                true, false, false, DockSide.LEFT, DockSide.LEFT, DockSide.LEFT, DockSide.LEFT,
                Set.of(HostTabTitlePart.NAME), true, TerminalPreferences.defaults());
        SftpBookmarks bookmarks = new SftpBookmarks(List.of("/local"), List.of("/remote"));
        SftpSettings defaults = original.sftpSettings();
        SftpSettings updatedSettings = new SftpSettings(
                defaults.showHiddenFiles(), defaults.concurrentTransfers(), bookmarks,
                defaults.hostBookmarks(), defaults.maxEditableFileBytes(),
                defaults.editorFontFamily(), defaults.editorFontSize(), defaults.editorTheme(),
                defaults.doubleClickFileAction());
        var updated = original.withSftpSettings(updatedSettings);
        var edits = new ConfigurationSaveController.EditTracker(original);

        assertTrue(edits.isDirty(updated));
        edits.markGlobalBookmarksSaved(bookmarks);
        assertFalse(edits.isDirty(updated));
    }

    @Test
    void searchableFontPopupMatchesControlWidthWhenReopenedAfterResizing() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            var stage = new javafx.stage.Stage();
            try {
                var selector = new SettingsFontSelector.SearchableFontSelector(
                        List.of("Menlo", "A font family with a deliberately very long display name"), "Menlo");
                var root = new javafx.scene.layout.Pane(selector.node());
                var scene = new javafx.scene.Scene(root, 600, 150);
                scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
                stage.setScene(scene);
                stage.show();
                var content = (javafx.scene.layout.VBox) selector.searchField().getParent();
                for (double width : new double[] {280, 440, 300}) {
                    selector.node().setPrefWidth(width);
                    root.applyCss();
                    root.layout();
                    selector.node().fire();
                    content.applyCss();
                    content.layout();
                    assertTrue(content.getScene().getWindow().isShowing());
                    assertEquals(width, selector.node().getWidth(), 0.01);
                    assertEquals(width, content.getWidth(), 0.01);
                    assertEquals(width, content.getMinWidth(), 0.01);
                    assertEquals(width, content.getPrefWidth(), 0.01);
                    assertEquals(width, content.getMaxWidth(), 0.01);
                    assertEquals(width - content.getInsets().getLeft() - content.getInsets().getRight(),
                            selector.results().getWidth(), 0.01);
                    assertEquals(selector.results().getWidth(), selector.searchField().getWidth(), 0.01);
                    selector.node().fire();
                }
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                stage.close();
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, java.util.concurrent.TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void searchableFontSelectorKeepsTheSelectedValueSeparateFromPopupSearch()
            throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                var selector = new SettingsFontSelector.SearchableFontSelector(
                        java.util.List.of("JetBrains Mono", "Custom_Font", "Noto Sans Mono"), "Custom_Font");
                assertFalse(selector.node().isMnemonicParsing());
                assertEquals("Custom_Font", selector.node().getText());
                assertEquals(3, selector.results().getItems().size());
                assertEquals("", selector.searchField().getText());
                selector.searchField().setText("mono");
                assertEquals(java.util.List.of("JetBrains Mono", "Noto Sans Mono"),
                        java.util.List.copyOf(selector.results().getItems()));
                selector.searchField().positionCaret(4);
                selector.results().getSelectionModel().select("Noto Sans Mono");
                selector.searchField().fireEvent(new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", KeyCode.UP,
                        false, false, false, false));
                assertEquals("JetBrains Mono", selector.results().getSelectionModel().getSelectedItem());
                assertEquals(4, selector.searchField().getCaretPosition());
                AtomicInteger leakedSearchEnterEvents = new AtomicInteger();
                selector.node().addEventHandler(javafx.scene.input.KeyEvent.ANY, event -> {
                    if (event.getCode() == KeyCode.ENTER) leakedSearchEnterEvents.incrementAndGet();
                });
                selector.results().getSelectionModel().select("JetBrains Mono");
                selector.searchField().fireEvent(new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                selector.searchField().fireEvent(new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                assertEquals("JetBrains Mono", selector.selectedFont());
                assertEquals(0, leakedSearchEnterEvents.get());

                selector.results().getSelectionModel().select("Noto Sans Mono");
                selector.results().fireEvent(new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                assertEquals("Noto Sans Mono", selector.selectedFont());
                assertEquals("Noto Sans Mono", selector.node().getText());
                completed.countDown();
            } catch (Throwable error) {
                failure.set(error);
                completed.countDown();
            }
        });
        assertTrue(completed.await(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void darkAndLightThemesSwapOnlyTheBuiltInThemeStylesheet() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.Scene scene = new javafx.scene.Scene(new javafx.scene.layout.StackPane());
                scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
                ThemeManager.install(scene);
                ThemeManager.setTheme(com.ynpe.fastremote.domain.settings.ApplicationTheme.DARK);
                assertTrue(scene.getRoot().getStylesheets()
                        .contains(MainView.darkStylesheet().toExternalForm()));
                javafx.scene.control.DialogPane pane = new javafx.scene.control.DialogPane();
                javafx.scene.Scene dialogScene = new javafx.scene.Scene(pane);
                javafx.beans.binding.Bindings.bindContent(dialogScene.getStylesheets(), pane.getStylesheets());
                try {
                    DialogSpacing.style(pane);
                    assertEquals(java.util.List.of(
                                    MainView.stylesheet().toExternalForm(),
                                    MainView.darkStylesheet().toExternalForm()),
                            java.util.List.copyOf(pane.getStylesheets()));
                    assertTrue(dialogScene.getStylesheets().contains(MainView.darkStylesheet().toExternalForm()));
                    pane.applyCss();
                    assertEquals(javafx.scene.paint.Color.web("#18191a"),
                            pane.getBackground().getFills().getFirst().getFill());
                    ThemeManager.setTheme(com.ynpe.fastremote.domain.settings.ApplicationTheme.DARK);
                    assertEquals(1, pane.getStylesheets().stream()
                            .filter(MainView.darkStylesheet().toExternalForm()::equals).count());
                    assertEquals(1, dialogScene.getStylesheets().stream()
                            .filter(MainView.darkStylesheet().toExternalForm()::equals).count());
                    ThemeManager.setTheme(com.ynpe.fastremote.domain.settings.ApplicationTheme.LIGHT);
                    assertFalse(scene.getRoot().getStylesheets()
                            .contains(MainView.darkStylesheet().toExternalForm()));
                    assertFalse(pane.getStylesheets().contains(MainView.darkStylesheet().toExternalForm()));
                    assertFalse(dialogScene.getStylesheets().contains(MainView.darkStylesheet().toExternalForm()));
                } finally {
                    javafx.beans.binding.Bindings.unbindContent(
                            dialogScene.getStylesheets(), pane.getStylesheets());
                }
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void parsedPasswordIsRevealedInTheMatchingQuickConnectField() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                RevealablePasswordEditor password = new RevealablePasswordEditor("SSH 主机密码");
                RevealablePasswordEditor passphrase = new RevealablePasswordEditor("私钥口令");
                var inferred = ConnectionClipboardImporter.parse("192.168.11.40 root secret");
                ConnectionClipboardImporter.revealParsedSecret(inferred, password, passphrase);
                assertTrue(password.isRevealed());
                assertFalse(passphrase.isRevealed());

                var explicitKey = ConnectionClipboardImporter.parse(
                        "sshpass -p secret ssh -i /tmp/id_ed25519 root@example.com");
                ConnectionClipboardImporter.revealParsedSecret(explicitKey, password, passphrase);
                assertFalse(password.isRevealed());
                assertTrue(passphrase.isRevealed());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void shortcutEditorCanRestoreEveryBuiltInDefault() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                var editor = new ShortcutPreferencesEditor(
                        new com.ynpe.fastremote.domain.settings.KeyboardShortcutSettings(
                                Map.of(ShortcutAction.OPEN_SETTINGS.id(), "Shortcut+Alt+Z")));
                assertFalse(editor.settings().overrides().isEmpty());

                editor.resetAllToDefaults();

                assertEquals(com.ynpe.fastremote.domain.settings.KeyboardShortcutSettings.defaults(), editor.settings());
                assertTrue(editor.valid());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void shortcutSearchKeepsEnterAwayFromTheConfigurationDialog() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                var editor = new ShortcutPreferencesEditor(
                        com.ynpe.fastremote.domain.settings.KeyboardShortcutSettings.defaults());
                javafx.scene.layout.VBox dialogContent = new javafx.scene.layout.VBox(editor.node());
                AtomicInteger leakedEvents = new AtomicInteger();
                dialogContent.addEventHandler(javafx.scene.input.KeyEvent.ANY, event -> {
                    if (event.getCode() == KeyCode.ENTER) leakedEvents.incrementAndGet();
                });

                editor.searchField().setText("terminal");
                editor.searchField().fireEvent(new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                editor.searchField().fireEvent(new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
                        false, false, false, false));

                assertEquals("terminal", editor.searchField().getText());
                assertEquals(0, leakedEvents.get());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void shortcutPageKeepsActionsAsItsOnlyScrollingRegion() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                var editor = new ShortcutPreferencesEditor(
                        com.ynpe.fastremote.domain.settings.KeyboardShortcutSettings.defaults());
                var page = SettingsPageLayout.fixedPage("快捷键", "说明", editor.node());
                var editorNode = (javafx.scene.layout.VBox) editor.node();
                var searchBar = (javafx.scene.layout.HBox) editorNode.getChildren().getFirst();

                assertEquals(3, searchBar.getChildren().size());
                assertTrue(searchBar.getChildren().get(1) instanceof javafx.scene.control.Button);
                assertTrue(searchBar.getChildren().get(2) instanceof javafx.scene.control.Button);
                assertEquals(220.0, ((javafx.scene.control.TextField) searchBar.getChildren().getFirst()).getMinWidth());
                assertEquals(javafx.scene.layout.Region.USE_PREF_SIZE,
                        ((javafx.scene.control.Button) searchBar.getChildren().get(1)).getMinWidth());
                assertEquals(javafx.scene.layout.Region.USE_PREF_SIZE,
                        ((javafx.scene.control.Button) searchBar.getChildren().get(2)).getMinWidth());
                assertTrue(editorNode.getChildren().get(1) instanceof javafx.scene.control.Label);
                javafx.scene.control.Label instructions =
                        (javafx.scene.control.Label) editorNode.getChildren().get(1);
                assertTrue(instructions.isWrapText());
                assertEquals(0.0, instructions.getMinWidth());
                assertEquals(Double.MAX_VALUE, instructions.getMaxWidth());
                assertEquals(javafx.scene.layout.Region.USE_PREF_SIZE, instructions.getMinHeight());
                assertTrue(editorNode.getChildren().getLast() instanceof javafx.scene.control.ListView<?>);
                assertTrue(page.getChildren().stream()
                        .noneMatch(javafx.scene.control.ScrollPane.class::isInstance));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }
}
