// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.SettingsPageLayout.page;
import static com.ynpe.fastremote.ui.SettingsPageLayout.card;
import static com.ynpe.fastremote.ui.SettingsPageLayout.labeled;
import static com.ynpe.fastremote.ui.SettingsPageLayout.check;
import static com.ynpe.fastremote.ui.SettingsPageLayout.policyInfo;
import static com.ynpe.fastremote.ui.SettingsPageLayout.combo;
import static com.ynpe.fastremote.ui.SettingsPageLayout.enumConverter;
import static com.ynpe.fastremote.ui.SettingsPageLayout.SELECTION_CONTROL_MIN_WIDTH;
import com.ynpe.fastremote.domain.settings.DockSide;
import com.ynpe.fastremote.domain.settings.DockDisplayMode;
import com.ynpe.fastremote.domain.settings.HostTabTitlePart;
import com.ynpe.fastremote.domain.settings.CommandPaletteSettings;
import com.ynpe.fastremote.domain.settings.ApplicationTheme;
import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.i18n.Localizer;
import com.ynpe.fastremote.domain.settings.GlobalToolbarButton;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

final class WorkspaceSettingsPage {
    private WorkspaceSettingsPage() {}

    static Node workspacePage(
            ComboBox<DockSide> hostLibraryDock,
            ComboBox<DockSide> quickConnectDock,
            ComboBox<DockSide> snippetsDock,
            ComboBox<DockSide> variableLibraryDock,
            ComboBox<DockSide> credentialVaultDock,
            ComboBox<DockSide> aiAssistantDock,
            ComboBox<DockSide> diagnosticLogDock,
            ComboBox<DockDisplayMode> leftDockDisplayMode,
            ComboBox<DockDisplayMode> rightDockDisplayMode,
            CheckBox leftDock,
            CheckBox rightDock,
            CheckBox openLocalTerminal,
            CheckBox showGlobalToolbar,
            CheckBox showGlobalStatusBar,
            ToolbarButtonsControls toolbarButtons,
            RadioButton blankDoubleClickLocalTerminal,
            RadioButton blankDoubleClickLocalFiles,
            RadioButton blankDoubleClickEditor,
            RadioButton expandTabsOnDoubleClick,
            RadioButton closeTabOnDoubleClick,
            RadioButton terminalToSftpCurrentWorkspace,
            RadioButton terminalToSftpNewTab,
            RadioButton sftpToTerminalCurrentWorkspace,
            RadioButton sftpToTerminalNewTab,
            RadioButton remoteEditorCurrentWorkspace,
            RadioButton remoteEditorNewTab,
            RadioButton localEditorCurrentWorkspace,
            RadioButton localEditorNewTab,
            RadioButton terminalToLocalFilesCurrentWorkspace,
            RadioButton terminalToLocalFilesNewTab,
            RadioButton localFilesToTerminalCurrentWorkspace,
            RadioButton localFilesToTerminalNewTab,
            CheckBox showLocalFileDirectoryTree,
            CheckBox showLocalFileHiddenFiles,
            Button manageLocalBookmarks) {
        VBox toolbarVisibility = card(
                com.ynpe.fastremote.i18n.Texts.text("workspace.interfaceVisibility"),
                com.ynpe.fastremote.i18n.Texts.text("workspace.interfaceVisibilityDescription"),
                showGlobalToolbar, showGlobalStatusBar);
        VBox toolbar = card(com.ynpe.fastremote.i18n.Texts.text("workspace.toolbarButtons"),
                com.ynpe.fastremote.i18n.Texts.text("workspace.toolbarButtonsDescription"),
                toolbarButtons.displayModeRow(),
                toolbarButtons.commandLibrary(), toolbarButtons.globalFavorites(), toolbarButtons.editor(),
                toolbarButtons.aiAssistant(), toolbarButtons.aiAssistantDialog(), toolbarButtons.diagnosticLog());
        toolbar.setId("toolbar-settings-section");
        VBox docks = card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b1c42d6996b06bbd"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.37d8b871cb57ce49"),
                labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bec3d896410b5294"), quickConnectDock),
                labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.278dda9e9d5dce65"), hostLibraryDock),
                labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bb2b5d546de68220"), credentialVaultDock),
                labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4466bdad43dae482"), snippetsDock),
                labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0aefd44f7a90ca71"), variableLibraryDock),
                labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1b0049fcfd4163fe"), aiAssistantDock),
                labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bb1d19b85634606e"), diagnosticLogDock));
        VBox display = card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ddb2003a4c0277b9"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.18740474f468df7c"),
                labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6a24b14f33c72be4"), leftDockDisplayMode), labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1cf1d4d0b24b0b16"), rightDockDisplayMode));
        VBox startup = card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3fb24a9b17a6fe2d"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.1d53e66b37bf6256"),
                leftDock, rightDock, openLocalTerminal);
        HBox blankDoubleClickOptions = new HBox(18.0,
                blankDoubleClickLocalTerminal, blankDoubleClickLocalFiles,
                blankDoubleClickEditor);
        blankDoubleClickOptions.setAlignment(Pos.CENTER_LEFT);
        Label blankDoubleClickLabel = new Label(
                com.ynpe.fastremote.i18n.Texts.text("workspace.blankDoubleClick"));
        blankDoubleClickLabel.setWrapText(true);
        VBox blankDoubleClickBehavior = new VBox(8.0,
                blankDoubleClickLabel, blankDoubleClickOptions);
        HBox tabDoubleClickOptions = new HBox(18.0,
                expandTabsOnDoubleClick, closeTabOnDoubleClick);
        tabDoubleClickOptions.setAlignment(Pos.CENTER_LEFT);
        Label tabDoubleClickLabel = new Label(
                com.ynpe.fastremote.i18n.Texts.text("workspace.tabDoubleClick"));
        tabDoubleClickLabel.setWrapText(true);
        VBox tabDoubleClickBehavior = new VBox(8.0,
                tabDoubleClickLabel, tabDoubleClickOptions);
        VBox behavior = card(com.ynpe.fastremote.i18n.Texts.text("workspace.programBehavior"),
                com.ynpe.fastremote.i18n.Texts.text("workspace.programBehaviorDescription"),
                blankDoubleClickBehavior, tabDoubleClickBehavior);
        HBox terminalToSftpOptions = relatedPanelOptions(
                terminalToSftpCurrentWorkspace, terminalToSftpNewTab);
        HBox sftpToTerminalOptions = relatedPanelOptions(
                sftpToTerminalCurrentWorkspace, sftpToTerminalNewTab);
        HBox remoteEditorOptions = relatedPanelOptions(
                remoteEditorCurrentWorkspace, remoteEditorNewTab);
        HBox localEditorOptions = relatedPanelOptions(
                localEditorCurrentWorkspace, localEditorNewTab);
        HBox terminalToLocalFilesOptions = relatedPanelOptions(
                terminalToLocalFilesCurrentWorkspace, terminalToLocalFilesNewTab);
        HBox localFilesToTerminalOptions = relatedPanelOptions(
                localFilesToTerminalCurrentWorkspace, localFilesToTerminalNewTab);
        VBox relatedPanels = card(
                com.ynpe.fastremote.i18n.Texts.text("workspace.relatedPanels.title"),
                com.ynpe.fastremote.i18n.Texts.text("workspace.relatedPanels.description"),
                labeled(com.ynpe.fastremote.i18n.Texts.text(
                        "workspace.relatedPanels.terminalToSftp"), terminalToSftpOptions),
                labeled(com.ynpe.fastremote.i18n.Texts.text(
                        "workspace.relatedPanels.sftpToTerminal"), sftpToTerminalOptions),
                labeled(com.ynpe.fastremote.i18n.Texts.text(
                        "workspace.relatedPanels.terminalToLocalFiles"), terminalToLocalFilesOptions),
                labeled(com.ynpe.fastremote.i18n.Texts.text(
                        "workspace.relatedPanels.localFilesToTerminal"), localFilesToTerminalOptions),
                labeled(com.ynpe.fastremote.i18n.Texts.text(
                        "workspace.relatedPanels.remoteEditor"), remoteEditorOptions),
                labeled(com.ynpe.fastremote.i18n.Texts.text(
                        "workspace.relatedPanels.localEditor"), localEditorOptions));
        VBox localFiles = card(
                com.ynpe.fastremote.i18n.Texts.text("workspace.localFiles.title"),
                com.ynpe.fastremote.i18n.Texts.text("workspace.localFiles.description"),
                showLocalFileDirectoryTree, showLocalFileHiddenFiles, manageLocalBookmarks);
        return page(com.ynpe.fastremote.i18n.Texts.text("legacy.java.37979829426b0cdb"),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.a03b63a37cb713e3"),
                toolbarVisibility, toolbar, docks, display, startup, relatedPanels, behavior,
                localFiles);
    }

    static HBox relatedPanelOptions(RadioButton currentWorkspace, RadioButton newTab) {
        HBox options = new HBox(18.0, currentWorkspace, newTab);
        options.setAlignment(Pos.CENTER_LEFT);
        return options;
    }

    static Node appearancePage(ComboBox<ApplicationTheme> applicationTheme,
            ComboBox<InterfaceLanguage> interfaceLanguage,
            CheckBox confirmExitWithMultipleTabs, CheckBox exitOnMainWindowClose,
            CheckBox macOsMenuBarIcon,
            CheckBox restoreWindowBounds, CheckBox linuxForceHiDpi,
            ComboBox<Integer> hiDpiScalePercent,
            Localizer localizer) {
        VBox closeBehavior = card(
                com.ynpe.fastremote.i18n.Texts.text("workspace.closeBehavior"),
                com.ynpe.fastremote.i18n.Texts.text("workspace.closeBehaviorDescription"),
                confirmExitWithMultipleTabs, exitOnMainWindowClose, macOsMenuBarIcon);
        return page(com.ynpe.fastremote.i18n.Texts.text("legacy.java.86a63f23a076b11e"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.c49e66cab50097c3"),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.88906e2e70b66551"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.95a775b8c525c82e"),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.788db1cfec2a3db5"), applicationTheme),
                        policyInfo(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ad61aee7dd87b096"))),
                card(localizer.text("settings.language"), localizer.text("settings.language.restart"),
                        labeled(localizer.text("settings.language"), interfaceLanguage)),
                closeBehavior,
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b93c528c47242ca3"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.dae73c88d445e808"),
                        restoreWindowBounds, linuxForceHiDpi,
                        labeled(com.ynpe.fastremote.i18n.Texts.text("appearance.hiDpiScale"), hiDpiScalePercent),
                        policyInfo(com.ynpe.fastremote.i18n.Texts.text("appearance.linuxForceHiDpi.description"))));
    }

    static ComboBox<InterfaceLanguage> languageChoice(
            Localizer localizer, InterfaceLanguage selected) {
        ComboBox<InterfaceLanguage> values = new ComboBox<>();
        values.getItems().setAll(InterfaceLanguage.publishedPreferences());
        values.setValue(selected);
        values.setMaxWidth(Double.MAX_VALUE);
        values.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(InterfaceLanguage language, boolean empty) {
                super.updateItem(language, empty);
                setText(empty || language == null ? null
                        : localizer.text("language." + language.persistedTag()));
            }
        });
        values.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(InterfaceLanguage language, boolean empty) {
                super.updateItem(language, empty);
                setText(empty || language == null ? null
                        : localizer.text("language." + language.persistedTag()));
            }
        });
        return values;
    }

    static ComboBox<ApplicationTheme> applicationTheme(ApplicationTheme selected) {
        ComboBox<ApplicationTheme> combo = new ComboBox<>();
        combo.getItems().setAll(ApplicationTheme.values());
        combo.setValue(selected == null ? ApplicationTheme.LIGHT : selected);
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ApplicationTheme value) {
                return com.ynpe.fastremote.i18n.Texts.text(
                        value == null ? "theme.light" : "theme." + value.name().toLowerCase(java.util.Locale.ROOT));
            }
            @Override public ApplicationTheme fromString(String value) {
                for (ApplicationTheme theme : ApplicationTheme.values()) {
                    if (theme.name().equalsIgnoreCase(value) || toString(theme).equals(value)) return theme;
                }
                return ApplicationTheme.LIGHT;
            }
        });
        combo.setMinWidth(SELECTION_CONTROL_MIN_WIDTH);
        return combo;
    }

    static ComboBox<DockSide> dockSide(DockSide side) {
        ComboBox<DockSide> combo = new ComboBox<>();
        combo.getItems().setAll(DockSide.LEFT, DockSide.RIGHT);
        combo.setValue(side);
        combo.setConverter(enumConverter(value -> value == DockSide.RIGHT ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.1cf1d4d0b24b0b16") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.6a24b14f33c72be4")));
        combo.setMinWidth(SELECTION_CONTROL_MIN_WIDTH);
        return combo;
    }

    static ComboBox<DockDisplayMode> dockDisplayMode(DockDisplayMode mode) {
        ComboBox<DockDisplayMode> combo = new ComboBox<>();
        combo.getItems().setAll(DockDisplayMode.EMBEDDED, DockDisplayMode.FLOATING);
        combo.setValue(mode);
        combo.setConverter(enumConverter(value -> value == DockDisplayMode.FLOATING ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.31a58efff477b2d5") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.ca899445193f843b")));
        combo.setMinWidth(SELECTION_CONTROL_MIN_WIDTH);
        return combo;
    }

    static CheckBox titlePart(
            String label, HostTabTitlePart part, Set<HostTabTitlePart> selected) {
        CheckBox checkBox = new CheckBox(label);
        checkBox.setUserData(part);
        checkBox.setSelected(selected.contains(part));
        return checkBox;
    }

    static Set<HostTabTitlePart> selectedTitleParts(CheckBox... checkBoxes) {
        java.util.EnumSet<HostTabTitlePart> selected = java.util.EnumSet.noneOf(HostTabTitlePart.class);
        for (CheckBox checkBox : checkBoxes) {
            if (checkBox.isSelected()) selected.add((HostTabTitlePart) checkBox.getUserData());
        }
        return Set.copyOf(selected);
    }

    record PaletteControls(
            CheckBox searchHosts,
            CheckBox searchCommands,
            CheckBox searchVariables,
            CheckBox searchFavoritePaths,
            CheckBox pinyinSearch,
            CheckBox commandSend,
            CheckBox commandPaste,
            CheckBox commandEdit,
            CheckBox hostConnect,
            CheckBox hostSftp,
            CheckBox hostEdit,
            CheckBox favoritePathGoTo,
            CheckBox favoritePathPaste) {

        static PaletteControls from(CommandPaletteSettings settings) {
            PaletteControls controls = new PaletteControls(
                    check(com.ynpe.fastremote.i18n.Texts.text("legacy.java.278dda9e9d5dce65"), settings.searchHosts()),
                    check(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4466bdad43dae482"), settings.searchCommands()),
                    check(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0aefd44f7a90ca71"), settings.searchVariables()),
                    check(com.ynpe.fastremote.i18n.Texts.text("commandPalette.favoritePaths"), settings.searchFavoritePaths()),
                    check(com.ynpe.fastremote.i18n.Texts.text("legacy.java.026857e6bc8a4f49"), settings.pinyinSearch()),
                    check(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2b1820d132b04666"), settings.showCommandSend()),
                    check(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e7027d39deec594b"), settings.showCommandPaste()),
                    check(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9ea7f032d0f39abe"), settings.showCommandEdit()),
                    check(com.ynpe.fastremote.i18n.Texts.text("legacy.java.aac0c509aacbaffa"), settings.showHostConnect()),
                    check(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3b350c8e49f7b7c3"), settings.showHostSftp()),
                    check(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0eae0e9bbc27f3e5"), settings.showHostEdit()),
                    check(com.ynpe.fastremote.i18n.Texts.text("commandPalette.favoritePathGoTo"), settings.showFavoritePathGoTo()),
                    check(com.ynpe.fastremote.i18n.Texts.text("commandPalette.favoritePathPaste"), settings.showFavoritePathPaste()));
            controls.commandSend().disableProperty().bind(controls.searchCommands().selectedProperty().not());
            controls.commandPaste().disableProperty().bind(controls.searchCommands().selectedProperty().not());
            controls.commandEdit().disableProperty().bind(controls.searchCommands().selectedProperty().not());
            controls.hostConnect().disableProperty().bind(controls.searchHosts().selectedProperty().not());
            controls.hostSftp().disableProperty().bind(controls.searchHosts().selectedProperty().not());
            controls.hostEdit().disableProperty().bind(controls.searchHosts().selectedProperty().not());
            controls.favoritePathGoTo().disableProperty().bind(controls.searchFavoritePaths().selectedProperty().not());
            controls.favoritePathPaste().disableProperty().bind(controls.searchFavoritePaths().selectedProperty().not());
            return controls;
        }

        Node pageNode() {
            List.of(searchHosts, searchCommands, searchVariables, searchFavoritePaths,
                    commandSend, commandPaste, commandEdit,
                    hostConnect, hostSftp, hostEdit,
                    favoritePathGoTo, favoritePathPaste)
                    .forEach(control -> control.setMinWidth(Region.USE_PREF_SIZE));
            HBox sources = new HBox(16.0, searchHosts, searchCommands, searchVariables, searchFavoritePaths);
            HBox commandActions = new HBox(16.0, commandSend, commandPaste, commandEdit);
            HBox hostActions = new HBox(16.0, hostConnect, hostSftp, hostEdit);
            HBox favoritePathActions = new HBox(16.0, favoritePathGoTo, favoritePathPaste);
            return page(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4b495d8b87b864fa"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5e43d91a65ff653"),
                    card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bf869b926f598993"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.bcbdfaf4c4c60d5c"),
                            labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4e65d015121fbe4e"), sources),
                            labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0afed1abad818d2e"), commandActions),
                            labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e6f6d875638eca07"), hostActions),
                            labeled(com.ynpe.fastremote.i18n.Texts.text("commandPalette.favoritePaths"), favoritePathActions),
                            pinyinSearch,
                            policyInfo(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1a16b4ef4f816d51"))));
        }

        CommandPaletteSettings settings() {
            return new CommandPaletteSettings(
                    searchHosts.isSelected(), searchCommands.isSelected(), searchVariables.isSelected(),
                    searchFavoritePaths.isSelected(),
                    pinyinSearch.isSelected(), commandSend.isSelected(), commandPaste.isSelected(),
                    commandEdit.isSelected(), hostConnect.isSelected(), hostSftp.isSelected(),
                    hostEdit.isSelected(), favoritePathGoTo.isSelected(), favoritePathPaste.isSelected());
        }

        void addChangeListeners(Runnable listener) {
            for (CheckBox checkBox : List.of(searchHosts, searchCommands, searchVariables,
                    searchFavoritePaths, pinyinSearch, commandSend, commandPaste, commandEdit,
                    hostConnect, hostSftp, hostEdit, favoritePathGoTo, favoritePathPaste)) {
                checkBox.selectedProperty().addListener((ignored, previous, selected) -> listener.run());
            }
        }
    }

    record ToolbarButtonsControls(
            HBox displayModeRow,
            ToggleGroup displayModeGroup,
            CheckBox commandLibrary,
            CheckBox globalFavorites,
            CheckBox editor,
            CheckBox aiAssistant,
            CheckBox aiAssistantDialog,
            CheckBox diagnosticLog) {
        static ToolbarButtonsControls from(Set<GlobalToolbarButton> selected) {
            Objects.requireNonNull(selected, "selected");
            CheckBox commandLibrary = toolbarButton("library.commands",
                    selected.contains(GlobalToolbarButton.COMMAND_LIBRARY));
            CheckBox globalFavorites = toolbarButton("legacy.fxml.5d7d7a4dcc5011fe",
                    selected.contains(GlobalToolbarButton.GLOBAL_FAVORITES));
            CheckBox editor = toolbarButton("editor.title",
                    selected.contains(GlobalToolbarButton.EDITOR));
            CheckBox aiAssistant = toolbarButton("toolbar.aiWorkspace",
                    selected.contains(GlobalToolbarButton.AI_ASSISTANT));
            CheckBox aiAssistantDialog = toolbarButton("toolbar.aiDialog",
                    selected.contains(GlobalToolbarButton.AI_ASSISTANT_DIALOG));
            CheckBox diagnosticLog = toolbarButton("diagnostic.log.toolbar",
                    selected.contains(GlobalToolbarButton.DIAGNOSTIC_LOG));
            ToggleGroup modeGroup = new ToggleGroup();
            HBox modes = new HBox(12, new Label(com.ynpe.fastremote.i18n.Texts.text("toolbar.displayMode")));
            modes.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            for (var mode : com.ynpe.fastremote.domain.settings.ToolbarDisplayMode.values()) {
                RadioButton button = new RadioButton(GlobalToolbarPresentation.label(mode));
                button.setToggleGroup(modeGroup);
                button.setUserData(mode);
                modes.getChildren().add(button);
            }
            modeGroup.selectToggle(modeGroup.getToggles().getFirst());
            return new ToolbarButtonsControls(modes, modeGroup, commandLibrary, globalFavorites, editor, aiAssistant,
                    aiAssistantDialog, diagnosticLog);
        }

        com.ynpe.fastremote.domain.settings.ToolbarDisplayMode displayMode() {
            return (com.ynpe.fastremote.domain.settings.ToolbarDisplayMode)
                    displayModeGroup.getSelectedToggle().getUserData();
        }

        void selectDisplayMode(com.ynpe.fastremote.domain.settings.ToolbarDisplayMode mode) {
            displayModeGroup.getToggles().stream().filter(toggle -> toggle.getUserData() == mode)
                    .findFirst().ifPresent(displayModeGroup::selectToggle);
        }

        private static CheckBox toolbarButton(String textKey, boolean selected) {
            CheckBox checkBox = new CheckBox(com.ynpe.fastremote.i18n.Texts.text(textKey));
            checkBox.setSelected(selected);
            return checkBox;
        }

        Set<GlobalToolbarButton> selectedButtons() {
            java.util.EnumSet<GlobalToolbarButton> selected =
                    java.util.EnumSet.noneOf(GlobalToolbarButton.class);
            if (commandLibrary.isSelected()) selected.add(GlobalToolbarButton.COMMAND_LIBRARY);
            if (globalFavorites.isSelected()) selected.add(GlobalToolbarButton.GLOBAL_FAVORITES);
            if (editor.isSelected()) selected.add(GlobalToolbarButton.EDITOR);
            if (aiAssistant.isSelected()) selected.add(GlobalToolbarButton.AI_ASSISTANT);
            if (aiAssistantDialog.isSelected()) selected.add(GlobalToolbarButton.AI_ASSISTANT_DIALOG);
            if (diagnosticLog.isSelected()) selected.add(GlobalToolbarButton.DIAGNOSTIC_LOG);
            return Set.copyOf(selected);
        }

        void addChangeListeners(Runnable listener) {
            displayModeGroup.selectedToggleProperty().addListener((ignored, previous, selected) -> listener.run());
            List.of(commandLibrary, globalFavorites, editor, aiAssistant, aiAssistantDialog, diagnosticLog).forEach(checkBox ->
                    checkBox.selectedProperty().addListener((ignored, previous, selected) -> listener.run()));
        }
    }
}
