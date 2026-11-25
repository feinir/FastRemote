// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.SettingsPageLayout.page;
import static com.ynpe.fastremote.ui.SettingsPageLayout.fixedPage;
import static com.ynpe.fastremote.ui.SettingsPageLayout.card;
import static com.ynpe.fastremote.ui.SettingsPageLayout.policyInfo;
import static com.ynpe.fastremote.ui.SettingsPageLayout.spinner;
import static com.ynpe.fastremote.ui.TerminalSettingsPage.terminalPage;
import static com.ynpe.fastremote.ui.TerminalSettingsPage.TerminalControls;
import static com.ynpe.fastremote.ui.TerminalSettingsPage.importColorScheme;
import static com.ynpe.fastremote.ui.ConnectionSettingsPage.connectionPage;
import static com.ynpe.fastremote.ui.ConnectionSettingsPage.ConnectionControls;
import static com.ynpe.fastremote.ui.TransferSettingsPage.transferPage;
import static com.ynpe.fastremote.ui.TransferSettingsPage.editorPage;
import static com.ynpe.fastremote.ui.TransferSettingsPage.EditorControls;
import static com.ynpe.fastremote.ui.TransferSettingsPage.sftpDoubleClickActionChoice;
import static com.ynpe.fastremote.ui.WorkspaceSettingsPage.workspacePage;
import static com.ynpe.fastremote.ui.WorkspaceSettingsPage.appearancePage;
import static com.ynpe.fastremote.ui.WorkspaceSettingsPage.languageChoice;
import static com.ynpe.fastremote.ui.WorkspaceSettingsPage.applicationTheme;
import static com.ynpe.fastremote.ui.WorkspaceSettingsPage.dockSide;
import static com.ynpe.fastremote.ui.WorkspaceSettingsPage.dockDisplayMode;
import static com.ynpe.fastremote.ui.WorkspaceSettingsPage.titlePart;
import static com.ynpe.fastremote.ui.WorkspaceSettingsPage.selectedTitleParts;
import static com.ynpe.fastremote.ui.WorkspaceSettingsPage.PaletteControls;
import static com.ynpe.fastremote.ui.WorkspaceSettingsPage.ToolbarButtonsControls;
import static com.ynpe.fastremote.ui.LicenseSettingsPage.licensePage;
import static com.ynpe.fastremote.ui.DiagnosticSettingsPage.diagnosticLogPage;
import static com.ynpe.fastremote.ui.DiagnosticSettingsPage.diagnosticLogDirectoryRow;
import static com.ynpe.fastremote.ui.DiagnosticSettingsPage.DiagnosticLogFilesPane;
import static com.ynpe.fastremote.ui.ConfigurationSaveController.saveChanges;
import static com.ynpe.fastremote.ui.ConfigurationSaveController.confirmDiscardChanges;
import static com.ynpe.fastremote.ui.ConfigurationSaveController.EditTracker;

import com.ynpe.fastremote.domain.settings.DockSide;
import com.ynpe.fastremote.domain.settings.DockDisplayMode;
import com.ynpe.fastremote.domain.settings.HostTabTitlePart;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.settings.WindowSettings;
import com.ynpe.fastremote.domain.settings.SftpBookmarks;
import com.ynpe.fastremote.domain.settings.SftpSettings;
import com.ynpe.fastremote.domain.settings.SftpDoubleClickAction;
import com.ynpe.fastremote.domain.settings.ConnectionSettings;
import com.ynpe.fastremote.domain.settings.CommandPaletteSettings;
import com.ynpe.fastremote.domain.settings.ApplicationTheme;
import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.i18n.Localizer;
import com.ynpe.fastremote.domain.settings.DiagnosticLogSettings;
import com.ynpe.fastremote.domain.settings.DiagnosticLogOutput;
import com.ynpe.fastremote.domain.settings.WorkspaceBlankDoubleClickAction;
import com.ynpe.fastremote.domain.ai.AiSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;

/** Builds the preferences surface without coupling UI state to a concrete settings store. */
final class ConfigurationDialog {
    static final double NAVIGATION_WIDTH = 180.0;
    static final double DIALOG_MIN_WIDTH = 860.0;
    static final double DIALOG_MIN_HEIGHT = 620.0;
    private static final List<String> PAGE_TITLES =
            List.of(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f0018b2e95040e8a"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.86a63f23a076b11e"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.37979829426b0cdb"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.4b495d8b87b864fa"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.e2a76ef1f12e147f"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5574109f0208e89"), "SFTP", com.ynpe.fastremote.i18n.Texts.text("editor.settings.title"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.ee2638183d3ea860"), com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.settingsTitle"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.e2eb47024664bec8"), com.ynpe.fastremote.i18n.Texts.text("config.management.title"), com.ynpe.fastremote.i18n.Texts.text("update.settings.title"), com.ynpe.fastremote.i18n.Texts.text("license.settings.title"));

    private ConfigurationDialog() {
    }

    static final String TOOLBAR_SECTION = "workspace.toolbar";

    record ImportAction(String label, Runnable handler) { }

    record EditActions(
            SettingsWorkflow settingsWorkflow,
            Function<WorkspaceLayout, CompletionStage<Void>> save,
            Function<SftpBookmarks, CompletionStage<Void>> saveGlobalBookmarks,
            Function<List<Path>, CompletionStage<TerminalPaletteImportBatch>> colorSchemeImporter,
            Consumer<Window> vaultManager,
            DiagnosticLogOutput diagnosticLogOutput,
            java.util.function.BiFunction<Path, Boolean, CompletionStage<Void>> revealLog) {}

    record AiConfiguration(
            AiAssistantWorkflow aiWorkflow,
            AiSettings aiSettings,
            Consumer<AiSettings> aiSaved,
            Consumer<String> aiFailure) {}

    record LicenseActions(
            java.util.function.Supplier<CompletionStage<com.ynpe.fastremote.domain.license.LicenseSnapshot>> licenseStatus,
            java.util.function.Supplier<CompletionStage<com.ynpe.fastremote.domain.license.LicenseSnapshot>> deactivateLicense,
            Consumer<com.ynpe.fastremote.domain.license.LicenseSnapshot> licenseChanged,
            Runnable openLicenseActivation) {}

    record UpdateActions(
            boolean automaticUpdateChecksEnabled,
            Function<Boolean, CompletionStage<Void>> saveAutomaticUpdateChecks,
            boolean available) {
        UpdateActions(boolean automaticUpdateChecksEnabled,
                Function<Boolean, CompletionStage<Void>> saveAutomaticUpdateChecks) {
            this(automaticUpdateChecksEnabled, saveAutomaticUpdateChecks, true);
        }
    }

    record ArchiveActions(
            Runnable resetConfiguration,
            Runnable importConfiguration,
            Runnable exportConfiguration,
            List<ImportAction> additionalImports) {}

    static void show(Window owner, WorkspaceLayout current, Localizer localizer,
            EditActions editActions, AiConfiguration aiConfiguration, LicenseActions licenseActions,
            UpdateActions updateActions, String initialPage, ArchiveActions archiveActions) {
        var settingsWorkflow = editActions.settingsWorkflow();
        var save = editActions.save();
        var saveGlobalBookmarks = editActions.saveGlobalBookmarks();
        var colorSchemeImporter = editActions.colorSchemeImporter();
        var vaultManager = editActions.vaultManager();
        var diagnosticLogOutput = editActions.diagnosticLogOutput();
        var revealLog = editActions.revealLog();
        var aiWorkflow = aiConfiguration.aiWorkflow();
        var aiSettings = aiConfiguration.aiSettings();
        var aiSaved = aiConfiguration.aiSaved();
        var aiFailure = aiConfiguration.aiFailure();
        var automaticUpdateChecksEnabled = updateActions.automaticUpdateChecksEnabled();
        var saveAutomaticUpdateChecks = updateActions.saveAutomaticUpdateChecks();
        var resetConfiguration = archiveActions.resetConfiguration();
        var importConfiguration = archiveActions.importConfiguration();
        var exportConfiguration = archiveActions.exportConfiguration();
        var additionalImports = archiveActions.additionalImports();

        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(localizer, "localizer");
        Objects.requireNonNull(save, "save");
        Objects.requireNonNull(saveGlobalBookmarks, "saveGlobalBookmarks");
        Objects.requireNonNull(colorSchemeImporter, "colorSchemeImporter");
        Objects.requireNonNull(vaultManager, "vaultManager");
        Objects.requireNonNull(aiWorkflow, "aiWorkflow");
        Objects.requireNonNull(aiSettings, "aiSettings");
        Objects.requireNonNull(aiSaved, "aiSaved");
        Objects.requireNonNull(aiFailure, "aiFailure");
        Objects.requireNonNull(saveAutomaticUpdateChecks, "saveAutomaticUpdateChecks");
        Objects.requireNonNull(diagnosticLogOutput, "diagnosticLogOutput");
        Dialog<Void> dialog = createDialog();
        dialog.initOwner(owner);
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0705906453d8bc59"));
        dialog.setHeaderText(null);
        DialogSpacing.prepare(dialog);
        dialog.getDialogPane().getStyleClass().add("configuration-dialog");
        ButtonType apply = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.63c73c4730f4473e"), ButtonBar.ButtonData.APPLY);
        ButtonType confirm = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.fac2a67ad87807c4"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, apply, confirm);

        ListView<String> navigation = new ListView<>();
        navigation.getItems().setAll(PAGE_TITLES);
        navigation.setPrefWidth(NAVIGATION_WIDTH);
        navigation.setMinWidth(NAVIGATION_WIDTH);
        navigation.setMaxWidth(NAVIGATION_WIDTH);
        navigation.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setWrapText(true);
                setMinWidth(0.0);
                setPrefWidth(0.0);
                setMaxWidth(Double.MAX_VALUE);
            }
        });
        navigation.getStyleClass().add("settings-navigation");

        List<String> dockExpansionOptions = startupDockExpansionOptions();
        CheckBox leftDock = new CheckBox(dockExpansionOptions.get(0));
        CheckBox rightDock = new CheckBox(dockExpansionOptions.get(1));
        CheckBox openLocalTerminal = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5dfd593c8a3bbd3e"));
        ToolbarButtonsControls toolbarButtons = ToolbarButtonsControls.from(
                current.windowSettings().toolbarButtons());
        toolbarButtons.selectDisplayMode(current.windowSettings().toolbarDisplayMode());
        CheckBox showGlobalToolbar = new CheckBox(
                com.ynpe.fastremote.i18n.Texts.text("workspace.showGlobalToolbar"));
        showGlobalToolbar.setSelected(current.windowSettings().showGlobalToolbar());
        CheckBox showGlobalStatusBar = new CheckBox(
                com.ynpe.fastremote.i18n.Texts.text("workspace.showGlobalStatusBar"));
        showGlobalStatusBar.setSelected(current.windowSettings().showGlobalStatusBar());
        RadioButton blankDoubleClickLocalTerminal = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.blankDoubleClick.localTerminal"));
        RadioButton blankDoubleClickLocalFiles = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.blankDoubleClick.localFiles"));
        RadioButton blankDoubleClickEditor = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.blankDoubleClick.editor"));
        ToggleGroup blankDoubleClickGroup = new ToggleGroup();
        blankDoubleClickLocalTerminal.setToggleGroup(blankDoubleClickGroup);
        blankDoubleClickLocalFiles.setToggleGroup(blankDoubleClickGroup);
        blankDoubleClickEditor.setToggleGroup(blankDoubleClickGroup);
        if (current.windowSettings().workspaceBlankDoubleClickAction()
                == WorkspaceBlankDoubleClickAction.EDITOR) {
            blankDoubleClickEditor.setSelected(true);
        } else if (current.windowSettings().workspaceBlankDoubleClickAction()
                == WorkspaceBlankDoubleClickAction.LOCAL_FILES) {
            blankDoubleClickLocalFiles.setSelected(true);
        } else {
            blankDoubleClickLocalTerminal.setSelected(true);
        }
        RadioButton terminalToSftpCurrentWorkspace = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.relatedPanels.currentWorkspace"));
        RadioButton terminalToSftpNewTab = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.relatedPanels.newTab"));
        ToggleGroup terminalToSftpGroup = new ToggleGroup();
        terminalToSftpCurrentWorkspace.setToggleGroup(terminalToSftpGroup);
        terminalToSftpNewTab.setToggleGroup(terminalToSftpGroup);
        if (current.windowSettings().openSftpFromTerminalInCurrentWorkspace()) {
            terminalToSftpCurrentWorkspace.setSelected(true);
        } else {
            terminalToSftpNewTab.setSelected(true);
        }
        RadioButton sftpToTerminalCurrentWorkspace = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.relatedPanels.currentWorkspace"));
        RadioButton sftpToTerminalNewTab = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.relatedPanels.newTab"));
        ToggleGroup sftpToTerminalGroup = new ToggleGroup();
        sftpToTerminalCurrentWorkspace.setToggleGroup(sftpToTerminalGroup);
        sftpToTerminalNewTab.setToggleGroup(sftpToTerminalGroup);
        if (current.windowSettings().openTerminalFromSftpInCurrentWorkspace()) {
            sftpToTerminalCurrentWorkspace.setSelected(true);
        } else {
            sftpToTerminalNewTab.setSelected(true);
        }
        RadioButton remoteEditorCurrentWorkspace = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.relatedPanels.currentWorkspace"));
        RadioButton remoteEditorNewTab = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.relatedPanels.newTab"));
        ToggleGroup remoteEditorGroup = new ToggleGroup();
        remoteEditorCurrentWorkspace.setToggleGroup(remoteEditorGroup);
        remoteEditorNewTab.setToggleGroup(remoteEditorGroup);
        if (current.windowSettings().openRemoteEditorInCurrentWorkspace()) {
            remoteEditorCurrentWorkspace.setSelected(true);
        } else {
            remoteEditorNewTab.setSelected(true);
        }
        RadioButton localEditorCurrentWorkspace = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.relatedPanels.currentWorkspace"));
        RadioButton localEditorNewTab = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.relatedPanels.newTab"));
        ToggleGroup localEditorGroup = new ToggleGroup();
        localEditorCurrentWorkspace.setToggleGroup(localEditorGroup);
        localEditorNewTab.setToggleGroup(localEditorGroup);
        if (current.windowSettings().openLocalEditorInCurrentWorkspace()) {
            localEditorCurrentWorkspace.setSelected(true);
        } else {
            localEditorNewTab.setSelected(true);
        }
        RadioButton terminalToLocalFilesCurrentWorkspace = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.relatedPanels.currentWorkspace"));
        RadioButton terminalToLocalFilesNewTab = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.relatedPanels.newTab"));
        ToggleGroup terminalToLocalFilesGroup = new ToggleGroup();
        terminalToLocalFilesCurrentWorkspace.setToggleGroup(terminalToLocalFilesGroup);
        terminalToLocalFilesNewTab.setToggleGroup(terminalToLocalFilesGroup);
        (current.windowSettings().openLocalFilesFromTerminalInCurrentWorkspace()
                ? terminalToLocalFilesCurrentWorkspace : terminalToLocalFilesNewTab).setSelected(true);
        RadioButton localFilesToTerminalCurrentWorkspace = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.relatedPanels.currentWorkspace"));
        RadioButton localFilesToTerminalNewTab = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.relatedPanels.newTab"));
        ToggleGroup localFilesToTerminalGroup = new ToggleGroup();
        localFilesToTerminalCurrentWorkspace.setToggleGroup(localFilesToTerminalGroup);
        localFilesToTerminalNewTab.setToggleGroup(localFilesToTerminalGroup);
        (current.windowSettings().openLocalTerminalFromFilesInCurrentWorkspace()
                ? localFilesToTerminalCurrentWorkspace : localFilesToTerminalNewTab).setSelected(true);
        CheckBox confirmExitWithMultipleTabs = new CheckBox(
                com.ynpe.fastremote.i18n.Texts.text("workspace.confirmExitWithMultipleTabs"));
        CheckBox exitOnMainWindowClose = new CheckBox(
                com.ynpe.fastremote.i18n.Texts.text("workspace.exitOnMainWindowClose"));
        CheckBox macOsMenuBarIcon = new CheckBox(
                com.ynpe.fastremote.i18n.Texts.text("workspace.macOsMenuBarIcon"));
        RadioButton expandTabsOnDoubleClick = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.tabDoubleClick.expand"));
        RadioButton closeTabOnDoubleClick = new RadioButton(
                com.ynpe.fastremote.i18n.Texts.text("workspace.tabDoubleClick.close"));
        ToggleGroup tabDoubleClickGroup = new ToggleGroup();
        expandTabsOnDoubleClick.setToggleGroup(tabDoubleClickGroup);
        closeTabOnDoubleClick.setToggleGroup(tabDoubleClickGroup);
        CheckBox restoreWindowBounds = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c2dd48119a2f5f26"));
        CheckBox linuxForceHiDpi = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("appearance.linuxForceHiDpi"));
        ComboBox<Integer> hiDpiScalePercent = new ComboBox<>();
        hiDpiScalePercent.getItems().setAll(100, 125, 150, 175, 200, 300, 400);
        hiDpiScalePercent.setConverter(new StringConverter<>() {
            @Override public String toString(Integer value) { return value == null ? "" : value + "%"; }
            @Override public Integer fromString(String value) { throw new UnsupportedOperationException(); }
        });
        ComboBox<ApplicationTheme> applicationTheme = applicationTheme(current.windowSettings().applicationTheme());
        ComboBox<InterfaceLanguage> interfaceLanguage = languageChoice(
                localizer, current.windowSettings().interfaceLanguage());
        ShortcutPreferencesEditor shortcutEditor = new ShortcutPreferencesEditor(
                current.windowSettings().keyboardShortcuts());
        ComboBox<DockDisplayMode> leftDockDisplayMode = dockDisplayMode(current.leftDockDisplayMode());
        ComboBox<DockDisplayMode> rightDockDisplayMode = dockDisplayMode(current.rightDockDisplayMode());
        leftDock.setSelected(current.leftDock());
        rightDock.setSelected(current.rightDock());
        openLocalTerminal.setSelected(current.openLocalTerminalOnStartup());
        confirmExitWithMultipleTabs.setSelected(current.windowSettings().confirmExitWithMultipleTabs());
        exitOnMainWindowClose.setSelected(current.windowSettings().exitOnMainWindowClose());
        macOsMenuBarIcon.setSelected(current.windowSettings().macOsMenuBarIconEnabled());
        boolean macOs = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
        macOsMenuBarIcon.setManaged(macOs);
        macOsMenuBarIcon.setVisible(macOs);
        (current.windowSettings().closeTabOnDoubleClick()
                ? closeTabOnDoubleClick : expandTabsOnDoubleClick).setSelected(true);
        restoreWindowBounds.setSelected(current.windowSettings().restoreWindowBounds());
        linuxForceHiDpi.setSelected(current.windowSettings().linuxForceHiDpi());
        hiDpiScalePercent.setValue(current.windowSettings().hiDpiScalePercent());
        hiDpiScalePercent.disableProperty().bind(linuxForceHiDpi.selectedProperty().not());
        ComboBox<DockSide> hostLibraryDock = dockSide(current.hostLibraryDock());
        ComboBox<DockSide> quickConnectDock = dockSide(current.quickConnectDock());
        ComboBox<DockSide> snippetsDock = dockSide(current.snippetsDock());
        ComboBox<DockSide> variableLibraryDock = dockSide(current.variableLibraryDock());
        ComboBox<DockSide> credentialVaultDock = dockSide(current.credentialVaultDock());
        ComboBox<DockSide> aiAssistantDock = dockSide(current.aiAssistantDock());
        ComboBox<DockSide> diagnosticLogDock = dockSide(current.diagnosticLogDock());
        CheckBox persistDiagnosticLog = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.enabled"));
        persistDiagnosticLog.setSelected(current.diagnosticLogSettings().enabled());
        CheckBox automaticUpdateChecks = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("update.settings.automatic"));
        automaticUpdateChecks.setSelected(automaticUpdateChecksEnabled);
        TextField diagnosticLogDirectory = new TextField(current.diagnosticLogSettings().directory());
        SingleLineInputEnterSupport.consume(diagnosticLogDirectory);
        diagnosticLogDirectory.setPromptText(diagnosticLogOutput.defaultDirectory().toString());
        Button chooseDiagnosticLogDirectory = new Button();
        chooseDiagnosticLogDirectory.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.chooseDirectory"));
        chooseDiagnosticLogDirectory.setGraphic(new LucideIconView(LucideIconView.Icon.FOLDER));
        chooseDiagnosticLogDirectory.getStyleClass().add("compact-button");
        chooseDiagnosticLogDirectory.setMinWidth(40.0);
        chooseDiagnosticLogDirectory.setPrefWidth(40.0);
        chooseDiagnosticLogDirectory.setMaxWidth(40.0);
        HBox diagnosticLogDirectoryRow = diagnosticLogDirectoryRow(
                diagnosticLogDirectory, chooseDiagnosticLogDirectory);
        DiagnosticLogFilesPane diagnosticLogFiles =
                new DiagnosticLogFilesPane(owner, diagnosticLogOutput, localizer, revealLog);
        CheckBox titleName = titlePart(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d44e9b3d3b31d37b"), HostTabTitlePart.NAME, current.hostTabTitleParts());
        CheckBox titleUser = titlePart(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0d0e1a86b3aa7877"), HostTabTitlePart.USER, current.hostTabTitleParts());
        CheckBox titleHost = titlePart(com.ynpe.fastremote.i18n.Texts.text("form.targetHost"), HostTabTitlePart.HOST, current.hostTabTitleParts());
        CheckBox titlePort = titlePart(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e71ac32b544b0ebf"), HostTabTitlePart.PORT, current.hostTabTitleParts());
        CheckBox showDirectoryTree = new CheckBox(
                com.ynpe.fastremote.i18n.Texts.text("sftp.settings.showRemoteTree"));
        showDirectoryTree.setSelected(current.showSftpDirectoryTree());
        CheckBox showHiddenFiles = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ddc6575a723d4762"));
        showHiddenFiles.setSelected(current.sftpSettings().showHiddenFiles());
        CheckBox showLocalFileDirectoryTree = new CheckBox(
                com.ynpe.fastremote.i18n.Texts.text("workspace.localFiles.showTree"));
        showLocalFileDirectoryTree.setSelected(current.windowSettings().showLocalFileDirectoryTree());
        CheckBox showLocalFileHiddenFiles = new CheckBox(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.ddc6575a723d4762"));
        showLocalFileHiddenFiles.setSelected(current.windowSettings().showLocalFileHiddenFiles());
        Spinner<Integer> transferConcurrency = spinner(
                1, SftpSettings.MAX_CONCURRENT_TRANSFERS, current.sftpSettings().concurrentTransfers());
        Spinner<Integer> maxEditableFileMiB = spinner(
                Math.toIntExact(SftpSettings.MIN_MAX_EDITABLE_FILE_BYTES / 1024L / 1024L),
                Math.toIntExact(SftpSettings.MAX_MAX_EDITABLE_FILE_BYTES / 1024L / 1024L),
                Math.toIntExact(current.sftpSettings().maxEditableFileBytes() / 1024 / 1024));
        ComboBox<SftpDoubleClickAction> doubleClickFileAction =
                sftpDoubleClickActionChoice(current.sftpSettings().doubleClickFileAction());
        AtomicReference<SftpBookmarks> globalBookmarks =
                new AtomicReference<>(current.sftpSettings().globalBookmarks());
        Map<String, SftpBookmarks> hostBookmarks = current.sftpSettings().hostBookmarks();
        Button manageGlobalBookmarks = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.902f61a727acc766"));
        Button manageLocalBookmarks = new Button(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.902f61a727acc766"));
        TerminalControls terminalControls = TerminalControls.from(current.terminalPreferences());
        EditorControls editorControls = EditorControls.from(current.sftpSettings());
        ConnectionControls connectionControls = ConnectionControls.from(current.connectionSettings());
        PaletteControls paletteControls = PaletteControls.from(current.commandPaletteSettings());

        AiSettingsPane.Editor aiEditor =
                AiSettingsPane.create(owner, aiWorkflow, aiSettings, aiSaved, aiFailure);
        Map<String, Node> pages = new LinkedHashMap<>();
        pages.put(com.ynpe.fastremote.i18n.Texts.text("legacy.java.86a63f23a076b11e"), appearancePage(applicationTheme, interfaceLanguage,
                confirmExitWithMultipleTabs, exitOnMainWindowClose, macOsMenuBarIcon,
                restoreWindowBounds, linuxForceHiDpi, hiDpiScalePercent, localizer));
        pages.put(com.ynpe.fastremote.i18n.Texts.text("legacy.java.37979829426b0cdb"), workspacePage(
                hostLibraryDock, quickConnectDock, snippetsDock, variableLibraryDock,
                credentialVaultDock, aiAssistantDock, diagnosticLogDock,
                leftDockDisplayMode, rightDockDisplayMode, leftDock, rightDock, openLocalTerminal,
                showGlobalToolbar, showGlobalStatusBar, toolbarButtons,
                blankDoubleClickLocalTerminal, blankDoubleClickLocalFiles, blankDoubleClickEditor,
                expandTabsOnDoubleClick, closeTabOnDoubleClick,
                terminalToSftpCurrentWorkspace, terminalToSftpNewTab,
                sftpToTerminalCurrentWorkspace, sftpToTerminalNewTab,
                remoteEditorCurrentWorkspace, remoteEditorNewTab,
                localEditorCurrentWorkspace, localEditorNewTab,
                terminalToLocalFilesCurrentWorkspace, terminalToLocalFilesNewTab,
                localFilesToTerminalCurrentWorkspace, localFilesToTerminalNewTab,
                showLocalFileDirectoryTree, showLocalFileHiddenFiles, manageLocalBookmarks));
        pages.put(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4b495d8b87b864fa"), paletteControls.pageNode());
        pages.put(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e2a76ef1f12e147f"), terminalPage(titleName, titleUser, titleHost, titlePort, terminalControls));
        pages.put(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5574109f0208e89"), connectionPage(connectionControls, terminalControls));
        pages.put("SFTP", transferPage(showDirectoryTree, showHiddenFiles,
                transferConcurrency, doubleClickFileAction, manageGlobalBookmarks));
        pages.put(com.ynpe.fastremote.i18n.Texts.text("editor.settings.title"),
                editorPage(editorControls, maxEditableFileMiB));
        pages.put(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ee2638183d3ea860"), fixedPage(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ee2638183d3ea860"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.24b048115f225487"),
                shortcutEditor.node()));
        pages.put(com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.settingsTitle"), diagnosticLogPage(
                persistDiagnosticLog, diagnosticLogDirectoryRow, diagnosticLogFiles.node()));
        pages.put(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e2eb47024664bec8"), aiEditor.node());
        if (licenseActions != null) {
            pages.put(localizer.text("license.settings.title"), licensePage(owner, localizer,
                    licenseActions.licenseStatus(), licenseActions.deactivateLicense(),
                    licenseActions.licenseChanged(), licenseActions.openLicenseActivation()));
        }
        VBox configurationActions = new VBox(12.0);
        String[] configurationLabels = {"config.reset.title", "config.import.title", "config.export.title"};
        Runnable[] configurationHandlers = {resetConfiguration, importConfiguration, exportConfiguration};
        for (int index = 0; index < configurationLabels.length; index++) {
            Button action = new Button(localizer.text(configurationLabels[index]));
            Runnable handler = configurationHandlers[index];
            action.setOnAction(event -> {
                dialog.close();
                if (!dialog.isShowing()) javafx.application.Platform.runLater(handler);
            });
            configurationActions.getChildren().add(action);
        }
        int importIndex = 2;
        for (ImportAction entry : additionalImports) {
            Button action = new Button(entry.label());
            action.setOnAction(event -> {
                dialog.close();
                if (!dialog.isShowing()) javafx.application.Platform.runLater(entry.handler());
            });
            configurationActions.getChildren().add(importIndex++, action);
        }
        configurationActions.getChildren().add(new DataLocationSettingsPane(localizer, settingsWorkflow));
        pages.put(localizer.text("config.management.title"), configurationManagementPage(localizer, configurationActions));
        if (updateActions.available()) {
            pages.put(localizer.text("update.settings.title"), softwareUpdatePage(automaticUpdateChecks));
        }
        pages.put(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f0018b2e95040e8a"), securityPage(vaultManager));

        StackPane pageHost = new StackPane();
        pageHost.setMinSize(0.0, 0.0);
        pageHost.getStyleClass().add("settings-page-host");
        navigation.getSelectionModel().selectedItemProperty().addListener((ignored, previous, selected) -> {
            Node page = pages.get(selected);
            if (page != null) {
                pageHost.getChildren().setAll(page);
            }
        });
        if (TOOLBAR_SECTION.equals(initialPage)) navigation.getSelectionModel().select(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.37979829426b0cdb"));
        else if (initialPage != null && pages.containsKey(initialPage)) navigation.getSelectionModel().select(initialPage);
        else navigation.getSelectionModel().selectFirst();

        BorderPane shell = new BorderPane(pageHost);
        shell.setMinSize(0.0, 0.0);
        shell.setLeft(navigation);
        shell.getStyleClass().add("settings-shell");
        dialog.getDialogPane().setContent(shell);
        configureDialogSizing(dialog);

        EditTracker edits = new EditTracker(current);
        AtomicBoolean savedAutomaticUpdateChecks = new AtomicBoolean(automaticUpdateChecksEnabled);
        AtomicBoolean saving = new AtomicBoolean();
        AtomicBoolean approvedClose = new AtomicBoolean();
        Button applyButton = (Button) dialog.getDialogPane().lookupButton(apply);
        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(confirm);
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        java.util.function.Supplier<WorkspaceLayout> draft = () -> selectedLayout(applicationTheme, interfaceLanguage, shortcutEditor, leftDock, rightDock, openLocalTerminal, showGlobalToolbar, showGlobalStatusBar, toolbarButtons,
                        blankDoubleClickLocalFiles, blankDoubleClickEditor,
                        terminalToSftpCurrentWorkspace,
                        sftpToTerminalCurrentWorkspace, remoteEditorCurrentWorkspace,
                        localEditorCurrentWorkspace,
                        terminalToLocalFilesCurrentWorkspace,
                        localFilesToTerminalCurrentWorkspace,
                        confirmExitWithMultipleTabs,
                        exitOnMainWindowClose, macOsMenuBarIcon,
                        closeTabOnDoubleClick, restoreWindowBounds, linuxForceHiDpi, hiDpiScalePercent, current.windowSettings(),
                        leftDockDisplayMode, rightDockDisplayMode,
                        hostLibraryDock, quickConnectDock, snippetsDock, variableLibraryDock,
                        credentialVaultDock, aiAssistantDock,
                        diagnosticLogDock, persistDiagnosticLog, diagnosticLogDirectory, paletteControls,
                        titleName, titleUser, titleHost, titlePort, showDirectoryTree,
                        showLocalFileDirectoryTree, showLocalFileHiddenFiles,
                        terminalControls, connectionControls, showHiddenFiles, transferConcurrency,
                        maxEditableFileMiB, doubleClickFileAction, editorControls,
                        globalBookmarks.get(), hostBookmarks);
        Runnable updateDirtyState = () -> applyButton.setDisable((!edits.isDirty(
                draft.get()) && !aiEditor.isDirty()
                        && automaticUpdateChecks.isSelected() == savedAutomaticUpdateChecks.get())
                || saving.get() || !shortcutEditor.valid());
        aiEditor.onChanged(updateDirtyState);
        Function<WorkspaceLayout, CompletionStage<Void>> saveAll = selected ->
                save.apply(selected).thenCompose(ignored -> aiEditor.save()).thenCompose(ignored -> {
                    boolean enabled = automaticUpdateChecks.isSelected();
                    if (enabled == savedAutomaticUpdateChecks.get()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                    return saveAutomaticUpdateChecks.apply(enabled).thenRun(() -> savedAutomaticUpdateChecks.set(enabled));
                }).thenRun(() -> Platform.runLater(diagnosticLogFiles::refresh));
        manageGlobalBookmarks.setOnAction(event -> SftpBookmarkDialog.show(owner,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.0750edf9cda6401f"),
                globalBookmarks.get(), bookmarks -> {
                    globalBookmarks.set(bookmarks);
                    updateDirtyState.run();
                    CompletionStage<Void> savingBookmarks = saveGlobalBookmarks.apply(bookmarks);
                    savingBookmarks.whenComplete((ignored, error) -> Platform.runLater(() -> {
                        if (error == null) {
                            edits.markGlobalBookmarksSaved(bookmarks);
                            updateDirtyState.run();
                        }
                    }));
                    return savingBookmarks;
                }));
        manageLocalBookmarks.setOnAction(manageGlobalBookmarks.getOnAction());
        leftDock.selectedProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        rightDock.selectedProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        openLocalTerminal.selectedProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        showGlobalToolbar.selectedProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        showGlobalStatusBar.selectedProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        toolbarButtons.addChangeListeners(updateDirtyState);
        blankDoubleClickGroup.selectedToggleProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        terminalToSftpGroup.selectedToggleProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        sftpToTerminalGroup.selectedToggleProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        remoteEditorGroup.selectedToggleProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        localEditorGroup.selectedToggleProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        terminalToLocalFilesGroup.selectedToggleProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        localFilesToTerminalGroup.selectedToggleProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        showLocalFileDirectoryTree.selectedProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        showLocalFileHiddenFiles.selectedProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        confirmExitWithMultipleTabs.selectedProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        exitOnMainWindowClose.selectedProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        macOsMenuBarIcon.selectedProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        tabDoubleClickGroup.selectedToggleProperty().addListener(
                (ignored, previous, selected) -> updateDirtyState.run());
        restoreWindowBounds.selectedProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        linuxForceHiDpi.selectedProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        hiDpiScalePercent.valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        applicationTheme.valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        interfaceLanguage.valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        shortcutEditor.settingsProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        leftDockDisplayMode.valueProperty().addListener((ignored, previous, selected) -> {
            leftDock.setDisable(selected == DockDisplayMode.FLOATING);
            updateDirtyState.run();
        });
        rightDockDisplayMode.valueProperty().addListener((ignored, previous, selected) -> {
            rightDock.setDisable(selected == DockDisplayMode.FLOATING);
            updateDirtyState.run();
        });
        leftDock.setDisable(current.leftDockDisplayMode() == DockDisplayMode.FLOATING);
        rightDock.setDisable(current.rightDockDisplayMode() == DockDisplayMode.FLOATING);
        hostLibraryDock.valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        quickConnectDock.valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        snippetsDock.valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        variableLibraryDock.valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        credentialVaultDock.valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        aiAssistantDock.valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        diagnosticLogDock.valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        persistDiagnosticLog.selectedProperty().addListener((ignored, previous, selected) -> {
            updateDirtyState.run();
        });
        diagnosticLogDirectory.textProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        automaticUpdateChecks.selectedProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        chooseDiagnosticLogDirectory.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.chooseDirectory"));
            String currentDirectory = diagnosticLogDirectory.getText().strip();
            if (!currentDirectory.isEmpty()) {
                try {
                    java.io.File candidate = Path.of(currentDirectory).toFile();
                    if (candidate.isDirectory()) chooser.setInitialDirectory(candidate);
                } catch (RuntimeException ignored) { }
            }
            java.io.File selected = PlatformFileChooser.showDialog(chooser, owner);
            if (selected != null) diagnosticLogDirectory.setText(selected.toPath().toAbsolutePath().normalize().toString());
        });
        paletteControls.addChangeListeners(updateDirtyState);
        titleName.selectedProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        titleUser.selectedProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        titleHost.selectedProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        titlePort.selectedProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        showDirectoryTree.selectedProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        showHiddenFiles.selectedProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        transferConcurrency.valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        transferConcurrency.getEditor().textProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        doubleClickFileAction.valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        maxEditableFileMiB.valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        maxEditableFileMiB.getEditor().textProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        editorControls.addChangeListeners(updateDirtyState);
        terminalControls.appearance().onChange(updateDirtyState);
        terminalControls.colorScheme().valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        terminalControls.fontFamily().selectedFontProperty()
                .addListener((ignored, previous, selected) -> updateDirtyState.run());
        terminalControls.fontSize().valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        terminalControls.fontSize().getEditor().textProperty()
                .addListener((ignored, previous, selected) -> updateDirtyState.run());
        terminalControls.cursorShape().valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        terminalControls.cursorBlink().selectedProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        terminalControls.openHyperlinksOnClick().selectedProperty()
                .addListener((ignored, previous, selected) -> updateDirtyState.run());
        terminalControls.highlightHyperlinksOnHover().selectedProperty()
                .addListener((ignored, previous, selected) -> updateDirtyState.run());
        terminalControls.probeServerInformation().selectedProperty()
                .addListener((ignored, previous, selected) -> {
                    terminalControls.updateProbeControlState();
                    updateDirtyState.run();
                });
        for (TextArea command : terminalControls.supplementCommands()) {
            command.textProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        }
        terminalControls.terminalType().valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        terminalControls.initialColumns().valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        terminalControls.initialColumns().getEditor().textProperty()
                .addListener((ignored, previous, selected) -> updateDirtyState.run());
        terminalControls.initialRows().valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        terminalControls.initialRows().getEditor().textProperty()
                .addListener((ignored, previous, selected) -> updateDirtyState.run());
        connectionControls.connectTimeout().valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        connectionControls.authenticationTimeout().valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        connectionControls.keepAliveInterval().valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        connectionControls.disconnectIdleTerminal().selectedProperty().addListener((ignored, previous, selected) -> {
            connectionControls.updateIdleControlState();
            updateDirtyState.run();
        });
        connectionControls.idleTimeout().valueProperty().addListener((ignored, previous, selected) -> updateDirtyState.run());
        terminalControls.importColorScheme().setOnAction(event -> importColorScheme(
                owner, terminalControls, colorSchemeImporter, updateDirtyState));
        updateDirtyState.run();

        applyButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (!shortcutEditor.valid()) return;
            saveChanges(dialog, draft.get(), false,
                    saveAll, edits, saving, approvedClose, applyButton, confirmButton, cancelButton,
                    updateDirtyState);
        });
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (!shortcutEditor.valid()) return;
            WorkspaceLayout selected = draft.get();
            if (!edits.isDirty(selected) && !aiEditor.isDirty()
                    && automaticUpdateChecks.isSelected() == savedAutomaticUpdateChecks.get()) {
                approvedClose.set(true);
                dialog.hide();
                return;
            }
            saveChanges(dialog, selected, true,
                    saveAll, edits, saving, approvedClose, applyButton, confirmButton, cancelButton,
                    updateDirtyState);
        });
        cancelButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (saving.get()) {
                event.consume();
            } else if ((edits.isDirty(draft.get()) || aiEditor.isDirty()
                            || automaticUpdateChecks.isSelected() != savedAutomaticUpdateChecks.get())
                    && !confirmDiscardChanges(dialog.getDialogPane().getScene().getWindow())) {
                event.consume();
            } else {
                approvedClose.set(true);
            }
        });
        dialog.setOnCloseRequest(event -> {
            if (approvedClose.getAndSet(false)) {
                return;
            }
            if (saving.get()
                    || ((edits.isDirty(draft.get()) || aiEditor.isDirty()
                            || automaticUpdateChecks.isSelected() != savedAutomaticUpdateChecks.get())
                    && !confirmDiscardChanges(dialog.getDialogPane().getScene().getWindow()))) {
                event.consume();
            }
        });
        if (TOOLBAR_SECTION.equals(initialPage)) dialog.setOnShown(event ->
                Platform.runLater(() -> revealToolbarSection(pageHost)));
        dialog.showAndWait();
    }

    static void revealToolbarSection(Node pageHost) {
        pageHost.applyCss();
        if (pageHost instanceof javafx.scene.Parent parent) parent.layout();
        Node section = pageHost.lookup("#toolbar-settings-section");
        if (section == null) return;
        for (Node node = section.getParent(); node != null; node = node.getParent()) {
            if (node instanceof ScrollPane scroll) {
                Node content = scroll.getContent();
                double y = content.sceneToLocal(section.localToScene(section.getBoundsInLocal())).getMinY();
                double range = content.getLayoutBounds().getHeight() - scroll.getViewportBounds().getHeight();
                scroll.setVvalue(range <= 0 ? 0 : Math.max(0, Math.min(1, y / range)));
                return;
            }
        }
    }

    static void configureDialogSizing(javafx.scene.control.Dialog<?> dialog) {
        Objects.requireNonNull(dialog, "dialog");
        dialog.setResizable(true);
        dialog.getDialogPane().setMinSize(DIALOG_MIN_WIDTH, DIALOG_MIN_HEIGHT);
        dialog.getDialogPane().setPrefSize(DIALOG_MIN_WIDTH, DIALOG_MIN_HEIGHT);
        DialogSpacing.configureResizableWindowSize(dialog, DIALOG_MIN_WIDTH, DIALOG_MIN_HEIGHT);
    }

    static Dialog<Void> createDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setDialogPane(new InsertionOrderedDialogPane());
        return dialog;
    }

    static List<String> pageTitles() {
        return PAGE_TITLES;
    }

    static List<String> startupDockExpansionOptions() {
        return List.of(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8f3021d5e8467d43"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.49fda3ab26042e88"));
    }

    private static WorkspaceLayout selectedLayout(
            ComboBox<ApplicationTheme> applicationTheme,
            ComboBox<InterfaceLanguage> interfaceLanguage,
            ShortcutPreferencesEditor shortcutEditor,
            CheckBox leftDock, CheckBox rightDock, CheckBox openLocalTerminal,
            CheckBox showGlobalToolbar,
            CheckBox showGlobalStatusBar,
            ToolbarButtonsControls toolbarButtons,
            RadioButton blankDoubleClickLocalFiles,
            RadioButton blankDoubleClickEditor,
            RadioButton terminalToSftpCurrentWorkspace,
            RadioButton sftpToTerminalCurrentWorkspace,
            RadioButton remoteEditorCurrentWorkspace,
            RadioButton localEditorCurrentWorkspace,
            RadioButton terminalToLocalFilesCurrentWorkspace,
            RadioButton localFilesToTerminalCurrentWorkspace,
            CheckBox confirmExitWithMultipleTabs,
            CheckBox exitOnMainWindowClose,
            CheckBox macOsMenuBarIcon,
            RadioButton closeTabOnDoubleClick, CheckBox restoreWindowBounds, CheckBox linuxForceHiDpi,
            ComboBox<Integer> hiDpiScalePercent,
            WindowSettings savedWindowSettings,
            ComboBox<DockDisplayMode> leftDockDisplayMode, ComboBox<DockDisplayMode> rightDockDisplayMode,
            ComboBox<DockSide> hostLibraryDock, ComboBox<DockSide> quickConnectDock,
            ComboBox<DockSide> snippetsDock, ComboBox<DockSide> variableLibraryDock,
            ComboBox<DockSide> credentialVaultDock,
            ComboBox<DockSide> aiAssistantDock,
            ComboBox<DockSide> diagnosticLogDock,
            CheckBox persistDiagnosticLog,
            TextField diagnosticLogDirectory,
            PaletteControls paletteControls,
            CheckBox titleName, CheckBox titleUser, CheckBox titleHost, CheckBox titlePort,
            CheckBox showDirectoryTree,
            CheckBox showLocalFileDirectoryTree,
            CheckBox showLocalFileHiddenFiles,
            TerminalControls terminalControls,
            ConnectionControls connectionControls,
            CheckBox showHiddenFiles,
            Spinner<Integer> transferConcurrency,
            Spinner<Integer> maxEditableFileMiB,
            ComboBox<SftpDoubleClickAction> doubleClickFileAction,
            EditorControls editorControls,
            SftpBookmarks globalBookmarks,
            Map<String, SftpBookmarks> hostBookmarks) {
        SftpSettings currentSftp = new SftpSettings(showHiddenFiles.isSelected(),
                transferConcurrency.getValue(), globalBookmarks, hostBookmarks,
                maxEditableFileMiB.getValue().longValue() * 1024L * 1024L,
                editorControls.fontFamily().selectedFont(), editorControls.fontSize().getValue(),
                editorControls.theme().getValue(), doubleClickFileAction.getValue(), editorControls.appearance().value());
        return new WorkspaceLayout(
                leftDock.isSelected() && leftDockDisplayMode.getValue() == DockDisplayMode.EMBEDDED,
                rightDock.isSelected() && rightDockDisplayMode.getValue() == DockDisplayMode.EMBEDDED,
                false,
                hostLibraryDock.getValue(), quickConnectDock.getValue(),
                snippetsDock.getValue(), variableLibraryDock.getValue(),
                credentialVaultDock.getValue(),
                aiAssistantDock.getValue(),
                diagnosticLogDock.getValue(),
                selectedTitleParts(titleName, titleUser, titleHost, titlePort),
                showDirectoryTree.isSelected(), terminalControls.preferences(), currentSftp,
                connectionControls.settings(), openLocalTerminal.isSelected(),
                new WindowSettings(closeTabOnDoubleClick.isSelected(), restoreWindowBounds.isSelected(),
                        savedWindowSettings.lastBounds(), applicationTheme.getValue(), shortcutEditor.settings(),
                        interfaceLanguage.getValue(), confirmExitWithMultipleTabs.isSelected(),
                        exitOnMainWindowClose.isSelected(), macOsMenuBarIcon.isSelected(),
                        linuxForceHiDpi.isSelected(),
                        hiDpiScalePercent.getValue(), toolbarButtons.selectedButtons(),
                        blankDoubleClickEditor.isSelected()
                                ? WorkspaceBlankDoubleClickAction.EDITOR
                                : blankDoubleClickLocalFiles.isSelected()
                                        ? WorkspaceBlankDoubleClickAction.LOCAL_FILES
                                        : WorkspaceBlankDoubleClickAction.LOCAL_TERMINAL,
                        showGlobalToolbar.isSelected(), showGlobalStatusBar.isSelected(),
                        terminalToSftpCurrentWorkspace.isSelected(),
                        sftpToTerminalCurrentWorkspace.isSelected(),
                        remoteEditorCurrentWorkspace.isSelected(),
                        localEditorCurrentWorkspace.isSelected(),
                        terminalToLocalFilesCurrentWorkspace.isSelected(),
                        localFilesToTerminalCurrentWorkspace.isSelected(),
                        showLocalFileHiddenFiles.isSelected(),
                        showLocalFileDirectoryTree.isSelected(), toolbarButtons.displayMode()),
                leftDockDisplayMode.getValue(), rightDockDisplayMode.getValue(),
                paletteControls.settings(), new DiagnosticLogSettings(
                        persistDiagnosticLog.isSelected(), diagnosticLogDirectory.getText()));
    }







    private static Node softwareUpdatePage(CheckBox automaticChecks) {
        return page(com.ynpe.fastremote.i18n.Texts.text("update.settings.title"),
                com.ynpe.fastremote.i18n.Texts.text("update.settings.description"),
                card(com.ynpe.fastremote.i18n.Texts.text("update.settings.stableCard"),
                        com.ynpe.fastremote.i18n.Texts.text("update.settings.stableDescription"),
                        automaticChecks, policyInfo(com.ynpe.fastremote.i18n.Texts.text("update.settings.offlinePolicy"))));
    }







    private static Node securityPage(Consumer<Window> vaultManager) {
        Label warning = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3dd76fcf0b1a1bc1"));
        warning.setWrapText(true);
        warning.getStyleClass().add("settings-security-note");
        javafx.scene.control.Button manageVault = new javafx.scene.control.Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d48a11edd1ee2cc8"));
        manageVault.setOnAction(event -> vaultManager.accept(manageVault.getScene().getWindow()));
        return page(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f0018b2e95040e8a"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.8a51277bd8d43ec6"),
                warning,
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bb2b5d546de68220"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.7b9aaf582384a2d7"),
                        manageVault,
                        policyInfo(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a9ae032b39b92625"))),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.157d2452ccbec19c"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.3f37c2f2e88e0a1d"),
                        policyInfo(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1e4ff9790d04c2bf")),
                        policyInfo(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c4958098d50fcaad"))));
    }

    static VBox configurationManagementPage(Localizer localizer, Node actions) {
        return page(localizer.text("config.management.title"), localizer.text("config.reset.note"), actions);
    }













    record WorkspaceLayout(
            boolean leftDock,
            boolean rightDock,
            boolean bottomDock,
            DockSide hostLibraryDock,
            DockSide quickConnectDock,
            DockSide snippetsDock,
            DockSide variableLibraryDock,
            DockSide credentialVaultDock,
            DockSide aiAssistantDock,
            DockSide diagnosticLogDock,
            Set<HostTabTitlePart> hostTabTitleParts,
            boolean showSftpDirectoryTree,
            TerminalPreferences terminalPreferences,
            SftpSettings sftpSettings,
            ConnectionSettings connectionSettings,
            boolean openLocalTerminalOnStartup,
            WindowSettings windowSettings,
            DockDisplayMode leftDockDisplayMode,
            DockDisplayMode rightDockDisplayMode,
            CommandPaletteSettings commandPaletteSettings,
            DiagnosticLogSettings diagnosticLogSettings) {
        WorkspaceLayout(
                boolean leftDock, boolean rightDock, boolean bottomDock,
                DockSide hostLibraryDock, DockSide quickConnectDock,
                DockSide snippetsDock, DockSide variableLibraryDock, DockSide credentialVaultDock,
                DockSide aiAssistantDock, DockSide diagnosticLogDock,
                Set<HostTabTitlePart> hostTabTitleParts,
                boolean showSftpDirectoryTree, TerminalPreferences terminalPreferences,
                SftpSettings sftpSettings, ConnectionSettings connectionSettings,
                boolean openLocalTerminalOnStartup, WindowSettings windowSettings) {
            this(leftDock, rightDock, bottomDock, hostLibraryDock, quickConnectDock,
                    snippetsDock, variableLibraryDock, credentialVaultDock, aiAssistantDock, diagnosticLogDock,
                    hostTabTitleParts, showSftpDirectoryTree, terminalPreferences, sftpSettings,
                    connectionSettings, openLocalTerminalOnStartup, windowSettings,
                    DockDisplayMode.FLOATING, DockDisplayMode.FLOATING, CommandPaletteSettings.defaults(),
                    DiagnosticLogSettings.defaults());
        }

        WorkspaceLayout(
                boolean leftDock, boolean rightDock, boolean bottomDock,
                DockSide hostLibraryDock, DockSide quickConnectDock,
                DockSide snippetsDock, DockSide credentialVaultDock,
                Set<HostTabTitlePart> hostTabTitleParts,
                boolean showSftpDirectoryTree, TerminalPreferences terminalPreferences) {
            this(leftDock, rightDock, bottomDock, hostLibraryDock, quickConnectDock,
                    snippetsDock, DockSide.RIGHT, credentialVaultDock, DockSide.RIGHT, DockSide.RIGHT,
                    hostTabTitleParts, showSftpDirectoryTree, terminalPreferences, SftpSettings.defaults(),
                    ConnectionSettings.defaults(), false, WindowSettings.defaults(),
                    DockDisplayMode.FLOATING, DockDisplayMode.FLOATING, CommandPaletteSettings.defaults(),
                    DiagnosticLogSettings.defaults());
        }

        WorkspaceLayout {
            Objects.requireNonNull(hostLibraryDock, "hostLibraryDock");
            Objects.requireNonNull(quickConnectDock, "quickConnectDock");
            Objects.requireNonNull(snippetsDock, "snippetsDock");
            Objects.requireNonNull(variableLibraryDock, "variableLibraryDock");
            Objects.requireNonNull(credentialVaultDock, "credentialVaultDock");
            Objects.requireNonNull(aiAssistantDock, "aiAssistantDock");
            Objects.requireNonNull(diagnosticLogDock, "diagnosticLogDock");
            hostTabTitleParts = Set.copyOf(
                    Objects.requireNonNull(hostTabTitleParts, "hostTabTitleParts"));
            Objects.requireNonNull(terminalPreferences, "terminalPreferences");
            Objects.requireNonNull(sftpSettings, "sftpSettings");
            Objects.requireNonNull(connectionSettings, "connectionSettings");
            Objects.requireNonNull(windowSettings, "windowSettings");
            Objects.requireNonNull(leftDockDisplayMode, "leftDockDisplayMode");
            Objects.requireNonNull(rightDockDisplayMode, "rightDockDisplayMode");
            Objects.requireNonNull(commandPaletteSettings, "commandPaletteSettings");
            Objects.requireNonNull(diagnosticLogSettings, "diagnosticLogSettings");
        }

        WorkspaceLayout withSftpSettings(SftpSettings updated) {
            return new WorkspaceLayout(leftDock, rightDock, bottomDock, hostLibraryDock,
                    quickConnectDock, snippetsDock, variableLibraryDock, credentialVaultDock,
                    aiAssistantDock, diagnosticLogDock, hostTabTitleParts, showSftpDirectoryTree,
                    terminalPreferences, updated, connectionSettings, openLocalTerminalOnStartup,
                    windowSettings, leftDockDisplayMode, rightDockDisplayMode,
                    commandPaletteSettings, diagnosticLogSettings);
        }
    }







}
