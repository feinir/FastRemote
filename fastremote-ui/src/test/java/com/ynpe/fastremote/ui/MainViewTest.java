// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ResourceBundle;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class MainViewTest {
    @Test
    void commandLibraryToolbarButtonPrecedesFavoritesAndOpensTheDialog() throws IOException {
        try (var input = MainView.fxml().openStream()) {
            String fxml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int command = fxml.indexOf("fx:id=\"toolbarCommandLibraryButton\"");
            int favorites = fxml.indexOf("fx:id=\"toolbarGlobalFavoritesButton\"");
            assertTrue(command >= 0 && command < favorites);
            assertTrue(fxml.substring(command, favorites).contains("onAction=\"#showSnippetLibraryDialog\""));
        }
    }

    @Test
    void toolsMenuHasAnIndependentCommandLibraryGroup() throws IOException {
        try (var input = MainView.fxml().openStream()) {
            String fxml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            String tools = fxml.substring(fxml.indexOf("<Menu text=\"%menu.tools\""),
                    fxml.indexOf("<Menu text=\"%menu.view\""));
            assertTrue(tools.contains("<SeparatorMenuItem/><MenuItem fx:id=\"snippetLibraryMenuItem\" "
                    + "onAction=\"#showSnippetLibraryDialog\" text=\"%library.commands\"/><SeparatorMenuItem/>"));
        }
    }

    @Test
    void everyMainFxmlEventHandlerExistsOnTheController() throws IOException {
        String fxml;
        try (var input = MainView.fxml().openStream()) {
            fxml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var controllerMethods = java.util.Arrays.stream(MainWindowController.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(java.util.stream.Collectors.toSet());
        var handlers = java.util.regex.Pattern.compile("\\son[A-Z][A-Za-z]+=\\\"#([^\\\"]+)\\\"")
                .matcher(fxml);
        int count = 0;
        while (handlers.find()) {
            String handler = handlers.group(1);
            assertTrue(controllerMethods.contains(handler), "Missing FXML handler #" + handler);
            count++;
        }
        assertTrue(count > 0);
    }

    @Test
    void textEditorMenusExposeItemsForShortcutWiring() throws IOException {
        String fxml = localizedFxml();
        for (String id : java.util.List.of(
                "newEditorDocumentMenuItem", "openTextFileMenuItem", "saveTextFileMenuItem",
                "saveTextFileAsMenuItem", "saveAllTextFilesMenuItem", "closeTextFileMenuItem",
                "cutTextMenuItem", "copyTextMenuItem", "pasteTextMenuItem",
                "findTextMenuItem", "replaceTextMenuItem")) {
            assertTrue(fxml.contains("fx:id=\"" + id + "\""), id);
        }
    }

    @Test
    void everyFxmlLucideIconNamesAnImplementedEnumValue() throws IOException {
        var matcher = java.util.regex.Pattern.compile(
                "<LucideIconView[^>]*\\sicon=\\\"([^\\\"]+)\\\"").matcher(localizedFxml());
        int count = 0;
        while (matcher.find()) {
            String icon = matcher.group(1);
            assertDoesNotThrow(() -> LucideIconView.Icon.valueOf(icon), icon);
            count++;
        }
        assertTrue(count > 0);
    }

    @Test
    void workspaceAiComposerUsesTheFullAvailableWidth() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertFalse(stylesheet.contains(".ai-placement-workspace .ai-composer"));
        assertFalse(stylesheet.contains("-fx-max-width: 980px"));
    }

    @Test
    void aiSidebarMenuAndComposerControlsExposeTheRequestedSizingAndActions() throws IOException {
        String fxml = localizedFxml();

        assertTrue(fxml.contains("onAction=\"#openAiSidebar\" text=\"%action.openAiSidebar\""));
        assertTrue(fxml.contains("fx:id=\"aiReasoningField\" accessibleText=\"推理强度\" minWidth=\"78.0\" prefWidth=\"100.0\" maxWidth=\"100.0\""));
        assertTrue(fxml.contains("fx:id=\"chooseKeyButton\" accessibleText=\"选择\" contentDisplay=\"GRAPHIC_ONLY\""));
    }

    @Test
    void viewMenuOffersFloatingAndEmbeddedActionsForBothPanels() throws IOException {
        String fxml = localizedFxml();

        assertTrue(fxml.contains("onAction=\"#toggleFloatingLeftPanel\" text=\"%action.floatingLeftPanel\""));
        assertTrue(fxml.contains("onAction=\"#toggleEmbeddedLeftPanel\" text=\"%action.embeddedLeftPanel\""));
        assertTrue(fxml.contains("onAction=\"#toggleFloatingRightPanel\" text=\"%action.floatingRightPanel\""));
        assertTrue(fxml.contains("onAction=\"#toggleEmbeddedRightPanel\" text=\"%action.embeddedRightPanel\""));
    }

    @Test
    void bookmarkReorderingUsesInsertionLineStyles() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(stylesheet.contains(".list-cell.bookmark-drop-before"));
        assertTrue(stylesheet.contains(".list-cell.bookmark-drop-after"));
        assertFalse(stylesheet.contains(".sftp-bookmark-description"));
    }

    @Test
    void allFourCatalogTreesUseTheSameCompactRowHeight() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String fxml;
        fxml = localizedFxml();

        assertTrue(cssBlock(stylesheet, ".catalog-tree {").contains("-fx-fixed-cell-size: 28px;"));
        assertTrue(cssBlock(stylesheet, ".catalog-tree .tree-cell {").contains("-fx-padding: 4px 8px;"));
        assertTrue(fxml.contains("fx:id=\"hostTree\" accessibleText=\"主机分组列表\" showRoot=\"false\" styleClass=\"catalog-tree\""));
        assertTrue(fxml.contains("fx:id=\"snippetTree\" accessibleText=\"命令库\" showRoot=\"false\" styleClass=\"catalog-tree\""));
        assertTrue(fxml.contains("fx:id=\"credentialTree\" accessibleText=\"凭证库\" managed=\"false\" showRoot=\"false\" styleClass=\"catalog-tree\""));
    }

    @Test
    void topToolbarTextButtonsHaveEnoughHeightAndKeepTextBesideTheIcon() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String darkStylesheet;
        try (var input = MainView.darkStylesheet().openStream()) {
            darkStylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String actionButton = cssBlock(stylesheet, ".main-toolbar .toolbar-action-button {");
        assertTrue(actionButton.contains("-fx-min-height: 24px;"));
        assertFalse(actionButton.contains("-fx-pref-height:"));
        assertFalse(actionButton.contains("-fx-max-height:"));
        assertTrue(actionButton.contains("-fx-content-display: left;"));
        assertTrue(actionButton.contains("-fx-alignment: center-left;"));
        assertTrue(actionButton.contains("-fx-padding: 0 8px 0 5px;"));
        assertTrue(cssBlock(stylesheet, ".main-toolbar .toolbar-icon-button {")
                .contains("-fx-padding: 0;"));
        assertTrue(cssBlock(darkStylesheet, ".main-toolbar .toolbar-action-button {")
                .contains("-fx-text-fill: #b4b6bc;"));
        assertTrue(stylesheet.contains(".root.platform-linux Text, .root.platform-linux .text"));
        assertFalse(stylesheet.contains("-fx-font-smoothing-type: gray;"));
        assertTrue(stylesheet.contains("-fx-font-smoothing-type: lcd;"));
    }

    @Test
    void topAndContextMenusShareRoundedSurfacesAndAlignedRows() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String contextMenu = cssBlock(stylesheet, ".context-menu {");
        assertFalse(contextMenu.contains("-fx-min-width"));
        assertTrue(contextMenu.contains("-fx-background-radius: 10px;"));
        assertTrue(contextMenu.contains("-fx-border-radius: 10px;"));
        assertTrue(cssBlock(stylesheet, ".context-menu .menu-item {")
                .contains("-fx-min-width: 164px;"));
        assertTrue(stylesheet.contains(".context-menu .separator {"));
        assertTrue(stylesheet.contains(".context-menu .separator > .line"));
        assertTrue(stylesheet.contains("-fx-border-width: 1px 0 0 0;"));
        assertTrue(stylesheet.contains(".application-context-menu.terminal-context-dark"));
        assertTrue(stylesheet.contains(".application-context-menu.terminal-context-light"));
    }

    @Test
    void darkInterfaceMenusUseReferencePaletteAndKeepEditorAndTerminalIndependent() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String darkStylesheet;
        try (var input = MainView.darkStylesheet().openStream()) {
            darkStylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(stylesheet.contains("-fr-context-dark-surface: #232427;"));
        assertTrue(stylesheet.contains("-fr-context-dark-selection: #2c3964;"));
        assertTrue(stylesheet.contains("-fr-context-dark-border: #424347;"));
        assertTrue(stylesheet.contains("-fr-context-dark-separator: #393b40;"));
        assertTrue(stylesheet.contains(".embedded-editor-context-menu.editor-context-dark,"));
        assertTrue(stylesheet.contains(".application-context-menu.terminal-context-dark"));

        String applicationMenu = cssBlock(darkStylesheet, ".context-menu {");
        assertTrue(applicationMenu.contains("-fx-background-color: -fr-interface-menu-surface;"));
        assertTrue(applicationMenu.contains("-fx-border-color: -fr-interface-menu-border;"));
        assertTrue(cssBlock(darkStylesheet, ".context-menu .menu-item:focused {")
                .contains("-fx-background-color: -fr-interface-menu-selection;"));
    }

    @Test
    void listsSeparateHoverFromSelectionWithoutChangingTableOrTreeStyles() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String darkStylesheet;
        try (var input = MainView.darkStylesheet().openStream()) {
            darkStylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String selected = cssBlock(stylesheet, ".list-view .list-cell:filled:selected {");
        assertTrue(selected.contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(selected.contains("-fx-text-fill: -fr-popup-selection-text;"));
        assertTrue(cssBlock(stylesheet, ".list-view .list-cell:filled:hover {")
                .contains("-fx-background-color: -fr-popup-hover;"));
        assertTrue(stylesheet.contains(".settings-navigation .list-cell:selected {"));
        assertTrue(stylesheet.contains(".session-editor-navigation .list-cell:selected {"));
        assertTrue(cssBlock(darkStylesheet, ".list-cell:selected {")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(cssBlock(stylesheet, ".table-row-cell:selected {")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(cssBlock(stylesheet, ".table-row-cell:hover,")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(cssBlock(darkStylesheet, ".table-row-cell:selected {")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(cssBlock(darkStylesheet, ".table-row-cell:hover {")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(cssBlock(darkStylesheet, ".list-cell:hover {")
                .contains("-fx-background-color: -fr-popup-hover;"));
        assertTrue(stylesheet.contains("-fr-popup-selection-hover: #dbe8fb;"));
        assertTrue(darkStylesheet.contains("-fr-popup-selection-hover: #2c3964;"));
        assertTrue(cssBlock(stylesheet, ".tree-cell:selected {")
                .contains("-fx-background-color: #dfe9fb;"));
        assertTrue(cssBlock(darkStylesheet, ".tree-cell:selected {")
                .contains("-fx-background-color: #2c3964;"));
    }

    @Test
    void toolbarHoverAndSelectedStatesUseOneSelectionColor() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String darkStylesheet;
        try (var input = MainView.darkStylesheet().openStream()) {
            darkStylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(cssBlock(stylesheet, ".main-toolbar .toolbar-icon-button:hover,")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(cssBlock(darkStylesheet, ".main-toolbar .toolbar-icon-button:hover,")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(cssBlock(stylesheet, ".tool-window-button:hover {")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(cssBlock(stylesheet, ".tool-window-button-selected {")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(cssBlock(stylesheet, ".workspace-rail-action:hover, .workspace-activity-button:hover {")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(cssBlock(stylesheet, ".workspace-activity-button.tool-window-button-selected {")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(cssBlock(darkStylesheet, ".tool-window-button:hover {")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(cssBlock(darkStylesheet, ".tool-window-button-selected {")
                .contains("-fx-background-color: -fr-popup-selection;"));
    }

    @Test
    void comboBoxPopupsShareRoundedSurfacesAndThemeSelectionColors() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String darkStylesheet;
        try (var input = MainView.darkStylesheet().openStream()) {
            darkStylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String popup = cssBlock(stylesheet, ".combo-box-popup > .list-view {");
        assertTrue(popup.contains("-fx-background-radius: 9px;"));
        assertTrue(popup.contains("-fx-border-radius: 9px;"));
        assertTrue(popup.contains("-fx-padding: 4px;"));
        assertTrue(cssBlock(stylesheet,
                ".combo-box-popup > .list-view .list-cell:filled:selected {")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(cssBlock(stylesheet,
                ".ai-model-bar .combo-box-popup .list-view .list-cell:filled:selected,")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(stylesheet.contains("-fr-popup-selection: #dbe8fb;"));
        assertTrue(darkStylesheet.contains("-fr-popup-selection: #2c3964;"));
        assertTrue(cssBlock(stylesheet, ".list-view.editor-search-popup-dark {")
                .contains("-fx-background-radius: 9px;"));
        assertTrue(cssBlock(stylesheet, ".list-view.editor-search-popup-light {")
                .contains("-fx-background-radius: 9px;"));
    }

    @Test
    void terminalScrollBarChromeFollowsTheTerminalPaletteInsteadOfTheInterfaceTheme()
            throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(cssBlock(stylesheet, ".scroll-bar.terminal-scroll-light,")
                .contains("-fx-background-color: #fafafa;"));
        assertTrue(cssBlock(stylesheet, ".scroll-bar.terminal-scroll-light .thumb {")
                .contains("-fx-background-color: #90a4ae;"));
        assertTrue(cssBlock(stylesheet, ".scroll-bar.terminal-scroll-dark,")
                .contains("-fx-background-color: #12161c;"));
        assertTrue(cssBlock(stylesheet, ".scroll-bar.terminal-scroll-dark .thumb {")
                .contains("-fx-background-color: #465263;"));
    }

    @Test
    void editorThemesCopyTerminalPalettesAndKeepCompactFileNameStrip() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(stylesheet.contains("-fx-background-color: #12161c;"));
        assertTrue(stylesheet.contains("-fx-fill: #dde3ea;"));
        assertTrue(stylesheet.contains("-fx-background-color: #fafafa;"));
        assertTrue(stylesheet.contains("-fx-fill: #263238;"));
        assertTrue(stylesheet.contains("-fx-fill: #526da5;"));
        assertTrue(cssBlock(stylesheet, ".embedded-editor-file-strip {")
                .contains("-fx-pref-height: 18px;"));
        assertTrue(cssBlock(stylesheet,
                ".embedded-editor-shell.editor-theme-light .embedded-editor-location-label {")
                .contains("-fx-text-fill: #000000;"));
        assertTrue(cssBlock(stylesheet,
                ".embedded-editor-shell.editor-theme-dark .embedded-editor-location-label {")
                .contains("-fx-text-fill: #ffffff;"));
        assertTrue(cssBlock(stylesheet, ".embedded-editor-status-icon {")
                .contains("-fx-scale-x: .46;"));
        assertTrue(cssBlock(stylesheet, ".embedded-editor-status-save .lucide-icon-stroke {")
                .contains("-fx-stroke: #2f80ed;"));
        assertTrue(cssBlock(stylesheet,
                ".embedded-editor-status-save-local .lucide-icon-stroke {")
                .contains("-fx-stroke: #27a269;"));
        assertTrue(cssBlock(stylesheet,
                ".embedded-editor-shell.editor-theme-light .embedded-editor-status-button,")
                .contains("-fx-background-color: transparent;"));
        assertTrue(cssBlock(stylesheet,
                ".embedded-editor-shell.editor-theme-light .embedded-editor-status-button:hover,")
                .contains("-fx-background-color: rgba(47, 128, 237, 0.13);"));
        assertTrue(cssBlock(stylesheet,
                ".embedded-text-editor.editor-theme-light .scroll-bar,")
                .contains(".scroll-bar .track,"));
        assertTrue(cssBlock(stylesheet,
                ".embedded-text-editor.editor-theme-light .scroll-bar,")
                .contains(".scroll-bar .increment-button,"));
        assertTrue(cssBlock(stylesheet,
                ".embedded-text-editor.editor-theme-light .scroll-bar,")
                .contains("-fx-background-color: #fafafa;"));
        assertTrue(cssBlock(stylesheet,
                ".embedded-text-editor.editor-theme-light .scroll-bar .increment-arrow,")
                .contains("-fx-background-color: #607d8b;"));
        assertTrue(stylesheet.contains(".list-view.editor-search-popup-dark"));
        assertTrue(stylesheet.contains(".embedded-editor-context-menu.editor-context-dark"));
    }

    @Test
    void workspaceIconButtonsShareACompactSizeInsideTheStatusBar() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String tools = cssBlock(stylesheet, ".workspace-status-tools {");
        String statusBar = cssBlock(stylesheet, ".status-bar {");
        String button = cssBlock(stylesheet, ".workspace-icon-button {");
        assertTrue(statusBar.contains("-fx-background-color: -fr-surface;"));
        assertFalse(statusBar.contains("#263140"));
        assertTrue(statusBar.contains("-fx-padding: 3px 10px;"));
        assertTrue(tools.contains("-fx-min-height: 24px;"));
        assertTrue(tools.contains("-fx-max-height: 24px;"));
        assertTrue(button.contains("-fx-min-width: 24px;"));
        assertTrue(button.contains("-fx-max-width: 24px;"));
        assertTrue(button.contains("-fx-min-height: 24px;"));
        assertTrue(button.contains("-fx-max-height: 24px;"));
        assertTrue(button.contains("-fx-padding: 0;"));

        String counterpart = cssBlock(stylesheet, ".workspace-counterpart-button {");
        assertTrue(counterpart.contains("-fx-min-width: 112px;"));
        assertTrue(counterpart.contains("-fx-max-width: 112px;"));
        assertTrue(counterpart.contains("-fx-min-height: 24px;"));
        assertTrue(counterpart.contains("-fx-max-height: 24px;"));
        assertTrue(counterpart.contains("-fx-padding: 0 6px;"));

        String sftpAction = cssBlock(stylesheet, ".sftp-toolbar-icon-button {");
        assertTrue(sftpAction.contains("-fx-min-width: 30px;"));
        assertTrue(sftpAction.contains("-fx-max-width: 30px;"));
        assertTrue(stylesheet.contains(".sftp-toolbar-icon { -fx-scale-x: .75; -fx-scale-y: .75; }"));
    }

    @Test
    void serverOverviewActionsLeaveRoomForLocalizedRefreshLabels() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String actions = cssBlock(stylesheet, ".workspace-server-actions {");
        assertTrue(actions.contains("-fx-min-width: 96px;"));
        assertTrue(actions.contains("-fx-pref-width: 96px;"));
        assertTrue(actions.contains("-fx-max-width: 96px;"));
    }

    @Test
    void mainTabsUseACompactRoundedDocumentStyle() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String darkStylesheet;
        try (var input = MainView.darkStylesheet().openStream()) {
            darkStylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String tabs = cssBlock(stylesheet, ".workspace-tabs {");
        assertTrue(tabs.contains("-fx-tab-max-width: 200px"));
        String tab = cssBlock(stylesheet, ".workspace-tabs > .tab-header-area .tab {");
        assertTrue(tabs.contains("-fx-tab-min-height: 23px;"));
        assertTrue(tabs.contains("-fx-tab-max-height: 23px;"));
        assertTrue(tab.contains("-fx-background-radius: 9px 9px 0 0;"));
        assertTrue(tab.contains("-fx-border-color: transparent #d3dce9 transparent transparent;"));
        assertTrue(stylesheet.contains(".workspace-tabs > .tab-header-area .tab:hover"));
        assertTrue(stylesheet.contains(
                ".workspace-tabs:focused > .tab-header-area > .headers-region > .tab:selected .focus-indicator"));
        assertTrue(stylesheet.contains(
                ".workspace-tab-status-connected { -fx-background-color: #2eae72; }"));
        assertTrue(stylesheet.contains(
                ".workspace-tab-status-connecting { -fx-background-color: #e4a72c; }"));
        assertTrue(stylesheet.contains(
                ".workspace-tab-status-disconnected { -fx-background-color: #df5360; }"));
        assertTrue(cssBlock(stylesheet,
                ".workspace-tabs > .tab-header-area .tab.workspace-tab-cell-drop-before {")
                .contains("-fx-border-radius: 0;"));
        assertTrue(cssBlock(stylesheet,
                ".workspace-tabs > .tab-header-area .tab.workspace-tab-cell-drop-after {")
                .contains("-fx-border-radius: 0;"));
        assertTrue(cssBlock(darkStylesheet,
                ".workspace-tabs > .tab-header-area .tab.workspace-container-tab .workspace-tab-title {")
                .contains("-fx-text-fill: #cbcdd3;"));
        assertTrue(cssBlock(darkStylesheet,
                ".workspace-tabs > .tab-header-area .tab.workspace-container-tab:selected .workspace-tab-title {")
                .contains("-fx-text-fill: #ffffff;"));
    }

    @Test
    void workspacePaneHeadersUseTheExtraCompactMultiPaneStyle() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String header = cssBlock(stylesheet, ".workspace-pane-header {");
        assertTrue(header.contains("-fx-min-height: 22px;"));
        assertTrue(header.contains("-fx-pref-height: 22px;"));
        assertTrue(header.contains("-fx-max-height: 22px;"));
        String close = cssBlock(stylesheet, ".workspace-pane-close {");
        assertTrue(close.contains("-fx-min-width: 17px;"));
        assertTrue(close.contains("-fx-min-height: 17px;"));
    }

    @Test
    void primaryToolbarActionsUseTransparentSeparatedGroups() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String group = cssBlock(stylesheet, ".toolbar-primary-actions {");
        assertTrue(group.contains("-fx-spacing: 6px;"));
        assertTrue(group.contains("-fx-background-color: transparent;"));
        assertFalse(group.contains("-fx-background-color: #f1f6ff;"));
        assertTrue(group.contains("-fx-padding: 0 3px;"));
    }

    @Test
    void connectionFeedbackUsesOneRoundedBannerWithoutTheRemovedSecurityNote() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String fxml;
        fxml = localizedFxml();

        String feedback = cssBlock(stylesheet, ".connection-feedback {");
        assertTrue(feedback.contains("-fx-background-radius: 6px;"));
        assertTrue(feedback.contains("-fx-border-radius: 6px;"));
        assertTrue(stylesheet.contains(".connection-feedback-working {"));
        assertTrue(stylesheet.contains(".connection-feedback-success {"));
        assertTrue(stylesheet.contains(".connection-feedback-error {"));
        assertFalse(stylesheet.contains(".session-editor-test-status"));
        assertTrue(fxml.contains("fx:id=\"quickConnectionTestStatus\" managed=\"false\" maxWidth=\"Infinity\" styleClass=\"connection-feedback\""));
        assertFalse(fxml.contains("styleClass=\"inline-security-note\""));
    }

    @Test
    void searchableSelectorsUseTheSameRootAndArrowInsetsAsComboBoxes() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String darkStylesheet;
        try (var input = MainView.darkStylesheet().openStream()) {
            darkStylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String selector = cssBlock(stylesheet,
                ".font-selector-button, .jump-host-selector, .credential-selector {");
        String arrow = cssBlock(stylesheet,
                ".font-selector-button > .arrow-button, .jump-host-selector > .arrow-button,");
        assertTrue(selector.contains("-fx-padding: 1px 2px;"));
        assertFalse(selector.contains("-fx-padding: 5px 8px;"));
        assertTrue(arrow.contains("-fx-padding: 0.5em 0.667em 0.5em 0.833333em;"));
        assertTrue(stylesheet.contains(".jump-host-selector-popup"));
        assertTrue(stylesheet.contains(".jump-host-option-name"));
        assertTrue(stylesheet.contains(".jump-host-option-detail"));
        assertTrue(cssBlock(stylesheet,
                ".font-selector-popup, .jump-host-selector-popup, .credential-selector-popup {")
                .contains("-fx-background-radius: 9px;"));
        assertTrue(cssBlock(stylesheet,
                ".font-selector-popup, .jump-host-selector-popup, .credential-selector-popup {")
                .contains("-fx-padding: 3px;"));
        assertTrue(cssBlock(stylesheet,
                ".font-selector-popup .list-view, .jump-host-selector-popup .list-view,")
                .contains("-fx-border-color: transparent;"));
        assertFalse(stylesheet.contains("font-selector-menu-item"));
        assertTrue(cssBlock(stylesheet,
                ".jump-host-selector-popup .list-cell:filled:selected,")
                .contains("-fx-background-color: -fr-popup-selection;"));
        assertTrue(stylesheet.contains(
                ".jump-host-selector-popup .list-cell:selected .jump-host-option-detail,"));
        assertTrue(stylesheet.contains("-fx-text-fill: -fr-popup-selection-text;"));
        assertTrue(darkStylesheet.contains(
                ".font-selector-popup, .jump-host-selector-popup, .credential-selector-popup {"));
        assertFalse(darkStylesheet.contains(".session-action-button, .font-selector-button"));
        assertFalse(stylesheet.contains("jump-session-"));
        assertFalse(darkStylesheet.contains("jump-session-"));
    }

    @Test
    void contextMenusUseOnlyAMinimumWidthAndGrowWithTranslatedContent() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String menuItem = cssBlock(stylesheet, ".context-menu .menu-item {");
        assertTrue(menuItem.contains("-fx-min-width: 164px"));
        assertFalse(menuItem.contains("-fx-pref-width"));
        assertFalse(menuItem.contains("-fx-max-width"));
    }

    @Test
    void sftpPaneLabelsUseTheCompactToolbarStyle() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(stylesheet.contains(".sftp-pane-label"));
        assertFalse(stylesheet.contains(".sftp-pane-title"));
        assertTrue(stylesheet.contains(".workspace-transfer-split"));
        assertFalse(stylesheet.contains(".workspace-transfer-tabs"));
        assertTrue(stylesheet.contains(".sftp-directory-statistics"));
        assertTrue(stylesheet.contains(".sftp-folder-icon"));
        assertTrue(stylesheet.contains(".sftp-file-icon"));
        assertTrue(stylesheet.contains(".tree-disclosure-node > .arrow"));
        assertTrue(stylesheet.contains(".sftp-selection-marquee"));
        assertTrue(stylesheet.contains(".dialog-tab-switcher"));
        assertTrue(stylesheet.contains(".dialog-tab-button"));
        String bookmarkSwitcher = cssBlock(stylesheet, ".dialog-tab-switcher {");
        assertFalse(bookmarkSwitcher.contains("-fx-max-width"));
        assertTrue(stylesheet.contains(".workspace-transfer-list .scroll-bar:horizontal"));
        assertTrue(stylesheet.contains("-fx-min-height: 12px"));
        assertTrue(stylesheet.contains(".workspace-operation-log"));
        assertFalse(stylesheet.contains(".root.platform-macos.locale-zh-cn { -fx-font-family:"));
        assertFalse(stylesheet.contains(".root.platform-windows.locale-zh-cn { -fx-font-family:"));
        assertFalse(stylesheet.contains(".root.platform-linux.locale-zh-cn { -fx-font-family:"));
        assertFalse(stylesheet.contains(".root {\n    -fx-font-family: \"HarmonyOS Sans SC\""));
        assertFalse(stylesheet.contains("-fx-font-family: -fr-ui-font-family"));
        assertFalse(stylesheet.contains("Arial Unicode MS"));
        assertFalse(stylesheet.contains(".settings-status-bar"));
        assertTrue(stylesheet.contains(".configuration-dialog > .content { -fx-padding: 0; }"));
        assertTrue(stylesheet.contains(".session-manager-hint"));
        assertTrue(stylesheet.contains(".settings-navigation .scroll-bar:horizontal"));
        assertTrue(stylesheet.contains("-fx-background-color: #eaf1ff"));
        assertTrue(stylesheet.contains(".vault-dialog-heading"));
    }

    @Test
    void terminalHostDoesNotExposeADarkFrameAroundLightSchemes() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(stylesheet.contains(
                ".terminal-host { -fx-background-color: transparent; -fx-padding: 0; }"));
        assertFalse(stylesheet.contains(
                ".terminal-host { -fx-background-color: -fr-terminal; -fx-padding: 5px; }"));
    }

    @Test
    void compactDialogsDelegateTheOnlyBottomGapToTheButtonBar() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String content = cssBlock(stylesheet, ".content-to-button-bar-dialog > .content {");
        String buttons = cssBlock(stylesheet, ".content-to-button-bar-dialog > .button-bar > .container {");
        assertTrue(content.contains("-fx-padding: 0;"));
        assertTrue(buttons.contains("-fx-padding: 10px;"));
        assertTrue(stylesheet.contains(".snippet-template-card"));
        assertTrue(stylesheet.contains("-fx-background-color: #eef6ff"));
        assertTrue(stylesheet.contains(".catalog-host-icon .lucide-icon-stroke"));
        assertTrue(stylesheet.contains("-fx-stroke-width: 2.25px"));
    }

    @Test
    void connectionProgressAndFailurePagesUseANeutralSurface() throws IOException {
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(stylesheet.contains(
                ".workspace-connection-state { -fx-background-color: #e9edf3; -fx-padding: 28px; }"));
        assertTrue(stylesheet.contains(
                ".workspace-connection-state-title { -fx-text-fill: #344054;"));
        assertTrue(stylesheet.contains(
                ".workspace-connection-state-detail { -fx-text-fill: #667085;"));
        assertFalse(stylesheet.contains(".terminal-connection-state"));
    }

    @Test
    void darkThemeOverridesAllExplicitLightWorkspaceSurfaces() throws IOException {
        String stylesheet;
        try (var input = MainView.darkStylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(cssBlock(stylesheet, ".workspace-connection-state {")
                .contains("-fx-background-color: -fr-bg;"));
        assertTrue(cssBlock(stylesheet, ".status-bar, .tool-window-rail, .dock-heading, .component-toolbar,")
                .contains("-fx-background-color: #18191a;"));
        assertTrue(cssBlock(stylesheet, ".workspace-terminal-activity, .workspace-terminal-floating {")
                .contains("-fx-background-color: #18191a;"));
        assertTrue(cssBlock(stylesheet, ".sftp-directory-statistics {")
                .contains("-fx-background-color: #18191a;"));
        assertTrue(cssBlock(stylesheet,
                ".workspace-tabs > .tab-header-area .tab.workspace-container-tab .workspace-tab-title {")
                .contains("-fx-text-fill: #cbcdd3;"));
        assertTrue(cssBlock(stylesheet,
                ".workspace-tabs > .tab-header-area .tab.workspace-container-tab:selected .workspace-tab-title {")
                .contains("-fx-text-fill: #ffffff;"));
    }

    @Test
    void exposesPackagedFxmlAndStylesheet() {
        assertNotNull(MainView.fxml());
        assertNotNull(MainView.stylesheet());
        assertNotNull(MainView.class.getResource("icons/intellij/toolWindowProject.svg"));
        assertNotNull(MainView.class.getResource("icons/intellij/toolWindowOverflow.svg"));
        assertNotNull(MainView.class.getResource("icons/intellij/toolWindowInspection.svg"));
        assertNotNull(MainView.class.getResource("icons/intellij/toolWindowMessages.svg"));
        assertNotNull(MainView.class.getResource("icons/intellij/console.svg"));
        assertNotNull(MainView.class.getResource("icons/intellij/NOTICE.md"));
        assertNotNull(MainView.class.getResource("icons/lucide/NOTICE.md"));
        assertEquals("file", MainView.fxml().getProtocol());
    }

    @Test
    void mainWorkspaceDeclaresProfessionalShellRegions() throws IOException {
        String fxml;
        fxml = localizedFxml();
        String css;
        try (var input = MainView.class.getResourceAsStream("main.css")) {
            css = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(fxml.contains("fx:id=\"hostTree\""));
        assertTrue(fxml.contains("fx:id=\"hostTree\" accessibleText=\"主机分组列表\" showRoot=\"false\""));
        assertTrue(fxml.contains("fx:id=\"workspaceTabs\""));
        assertTrue(fxml.contains("tabDragPolicy=\"REORDER\""));
        assertTrue(fxml.contains("text=\"安全连接，从FastRemote开始！\""));
        assertTrue(fxml.contains("<VBox maxWidth=\"Infinity\" spacing=\"5.0\" styleClass=\"home-hero\">"));
        assertTrue(fxml.contains("styleClass=\"welcome-title\" text=\"安全连接，从FastRemote开始！\" wrapText=\"true\""));
        assertTrue(fxml.contains("fx:id=\"recentHostList\" accessibleText=\"最近成功连接的主机\""));
        assertTrue(fxml.contains("onAction=\"#showQuickConnectDialog\" styleClass=\"home-action-card, home-action-card-primary\""));
        assertTrue(fxml.contains("onAction=\"#openLocalTerminal\" styleClass=\"home-action-card\""));
        assertTrue(fxml.contains("onAction=\"#openLocalFiles\" styleClass=\"home-action-card\" GridPane.columnIndex=\"0\" GridPane.rowIndex=\"1\""));
        assertFalse(fxml.contains("onAction=\"#openSftpFromHome\""));
        assertTrue(fxml.contains("onAction=\"#focusVariableManager\" prefWidth=\"246.0\""));
        assertTrue(fxml.contains("onAction=\"#showSnippetLibraryDialog\" prefWidth=\"246.0\""));
        assertTrue(fxml.contains("onAction=\"#focusQuickConnect\" text=\"%action.quickConnect\""));
        assertFalse(fxml.contains("WORKSPACE"));
        assertFalse(fxml.contains("工作台"));
        assertFalse(fxml.contains("已启用加密保护"));
        assertFalse(fxml.contains("fx:id=\"bottomDock\""));
        assertTrue(fxml.contains("fx:id=\"workbenchBoard\""));
        assertTrue(fxml.contains("fx:id=\"rightDock\""));
        assertTrue(fxml.contains("fx:id=\"horizontalWorkbenchSplit\""));
        assertTrue(fxml.contains("fx:id=\"hostSearchField\" accessibleText=\"搜索主机\" promptText=\"名称、分组名、主机、用户、端口\""));
        assertTrue(fxml.contains("fx:id=\"commandPaletteMenuItem\" onAction=\"#showCommandPalette\" text=\"%action.commandPalette\""));
        assertTrue(fxml.contains("fx:id=\"snippetSearchField\" accessibleText=\"搜索命令\" promptText=\"名称、说明或内容\""));
        assertTrue(fxml.contains("fx:id=\"snippetsRailButton\" accessibleText=\"命令库\""));
        assertTrue(fxml.contains("styleClass=\"home-feature-title\" text=\"命令库\""));
        assertTrue(fxml.contains("fx:id=\"rightDockTitle\" styleClass=\"dock-title\" text=\"命令库\""));
        assertFalse(fxml.contains("代码段"));
        assertTrue(fxml.contains("fx:id=\"credentialSearchField\" accessibleText=\"搜索凭证\" promptText=\"名称、分组名、用户名\""));
        assertTrue(fxml.contains("<fx:include fx:id=\"diagnosticLogPage\" source=\"DiagnosticLogView.fxml\"/>"));
        assertTrue(fxml.contains("fx:id=\"statusBar\" alignment=\"CENTER_LEFT\" minHeight=\"30.0\" prefHeight=\"30.0\" maxHeight=\"30.0\""));
        assertTrue(fxml.contains("fx:id=\"mcpStatusIndicator\" accessibleText=\"%status.mcp.stopped\" alignment=\"CENTER\" minWidth=\"12.0\" prefWidth=\"12.0\" maxWidth=\"12.0\""));
        assertTrue(fxml.contains("fx:id=\"mcpStatusLabel\" styleClass=\"mcp-status-text\" text=\"%status.mcp.stopped\""));
        assertTrue(fxml.contains("fx:id=\"statusIndicator\" alignment=\"CENTER\" minWidth=\"12.0\" prefWidth=\"12.0\" maxWidth=\"12.0\""));
        assertFalse(fxml.contains("fx:id=\"statusDismissButton\""));
        assertFalse(fxml.contains("onAction=\"#dismissStatusMessage\""));
        assertTrue(fxml.contains("fx:controller=\"com.ynpe.fastremote.ui.DiagnosticLogController\" spacing=\"0.0\""));
        assertFalse(fxml.contains("dividerPositions=\"0.21,0.79\""));
        assertTrue(fxml.contains("fx:id=\"hostSidebar\" minWidth=\"245.0\" prefWidth=\"245.0\""));
        assertTrue(fxml.contains("fx:id=\"rightDock\" minWidth=\"245.0\" prefWidth=\"245.0\""));
        assertFalse(fxml.contains("fx:id=\"bottomResizeHandle\""));
        assertFalse(fxml.contains("accessibleText=\"调整底部活动窗口高度\""));
        assertFalse(fxml.contains("fx:id=\"bottomToolRail\""));
        assertFalse(fxml.contains("fx:id=\"verticalWorkbenchSplit\""));
        assertTrue(fxml.contains("fx:id=\"leftToolRail\""));
        assertTrue(fxml.contains("fx:id=\"rightToolRail\""));
        assertFalse(fxml.contains("fx:id=\"bottomDockCollapseButton\""));
        assertFalse(fxml.contains("fx:id=\"bottomDockRestoreButton\""));
        assertTrue(fxml.contains("<left><VBox fx:id=\"leftToolRail\""));
        assertTrue(fxml.contains("<right><VBox fx:id=\"rightToolRail\""));
        assertFalse(fxml.contains("toolbarBottomDockButton"));
        assertFalse(fxml.contains("onAction=\"#toggleBottomDock\""));
        assertFalse(fxml.contains("fx:id=\"leftToolArea\""));
        assertFalse(fxml.contains("fx:id=\"rightToolArea\""));
        assertFalse(fxml.contains("fx:id=\"bottomDockRestoreAction\""));
        assertTrue(fxml.contains("styleClass=\"tool-window-icon\""));
        assertFalse(fxml.contains("fx:id=\"componentTabs\""));
        assertFalse(fxml.contains("styleClass=\"context-tabs\""));
        assertTrue(fxml.contains("fx:id=\"hostLibraryRailButton\""));
        assertTrue(fxml.contains("fx:id=\"quickConnectRailButton\""));
        assertTrue(fxml.contains("fx:id=\"snippetsRailButton\""));
        assertTrue(fxml.contains("fx:id=\"credentialVaultRailButton\""));
        assertTrue(fxml.contains("fx:id=\"hostLibraryPage\""));
        assertTrue(fxml.contains("fx:id=\"quickConnectPage\""));
        assertTrue(fxml.contains("fx:id=\"quickConnectPage\" fitToWidth=\"true\" hbarPolicy=\"NEVER\" minWidth=\"0.0\""));
        assertTrue(fxml.contains("fx:id=\"leftDockSubtitle\" styleClass=\"dock-subtitle\""));
        assertTrue(fxml.contains("fx:id=\"rightDockSubtitle\" styleClass=\"dock-subtitle\""));
        assertTrue(fxml.contains("fx:id=\"leftHostLibraryButton\" accessibleText=\"打开主机库\""));
        assertTrue(fxml.contains("fx:id=\"rightHostLibraryButton\" accessibleText=\"打开主机库\""));
        assertTrue(fxml.contains("styleClass=\"icon-button, dock-heading-button\""));
        assertTrue(fxml.contains("<fx:include fx:id=\"hostLibraryPage\" source=\"HostLibraryView.fxml\"/>"));
        assertTrue(fxml.contains("fx:controller=\"com.ynpe.fastremote.ui.HostLibraryController\" spacing=\"8.0\" styleClass=\"component-page, catalog-controls-shell\""));
        assertFalse(fxml.contains("text=\"双击会话即可连接\""));
        assertFalse(fxml.contains("text=\"完整管理…\""));
        assertTrue(fxml.contains("fx:id=\"snippetsPage\""));
        assertTrue(fxml.contains("fx:id=\"credentialVaultPage\""));
        assertTrue(fxml.contains("fx:id=\"leftDockContentHost\""));
        assertTrue(fxml.contains("fx:id=\"rightDockContentHost\""));
        assertTrue(fxml.contains("fx:id=\"credentialTree\""));
        assertTrue(fxml.contains("fx:id=\"workspaceIntro\""));
        assertFalse(fxml.contains("fx:id=\"welcomeTab\""));
        assertTrue(fxml.contains("fx:id=\"credentialProfileField\""));
        assertTrue(fxml.contains("fx:id=\"credentialSourceField\""));
        assertTrue(fxml.contains("<MenuButton fx:id=\"credentialProfileField\" accessibleText=\"从凭证库选择已有凭证\""));
        assertFalse(fxml.contains("<ComboBox fx:id=\"credentialProfileField\""));
        assertTrue(fxml.contains("<AnchorPane fx:id=\"quickConnectFields\" minHeight=\"484.0\" prefHeight=\"484.0\" maxHeight=\"484.0\" styleClass=\"quick-connect-fields-shell\">"));
        assertTrue(fxml.contains("<VBox spacing=\"10.0\" styleClass=\"quick-connect-form\">"));
        assertTrue(fxml.contains("alignment=\"CENTER_LEFT\" minHeight=\"44.0\" minWidth=\"96.0\""));
        assertFalse(fxml.contains("alignment=\"CENTER_RIGHT\" minHeight=\"44.0\" minWidth=\"96.0\""));
        assertTrue(fxml.contains("wrapText=\"true\" text=\"连接路径\" AnchorPane.leftAnchor=\"0.0\" AnchorPane.topAnchor=\"440.0\""));
        assertTrue(fxml.contains("<MenuButton fx:id=\"jumpHostField\" accessibleText=\"搜索并选择跳板连接路径\""));
        assertTrue(fxml.contains("wrapText=\"true\" text=\"凭证来源\" AnchorPane.leftAnchor=\"0.0\" AnchorPane.topAnchor=\"132.0\""));
        assertTrue(fxml.contains("wrapText=\"true\" text=\"凭证库凭证\" AnchorPane.leftAnchor=\"0.0\" AnchorPane.topAnchor=\"352.0\""));
        assertTrue(fxml.contains("wrapText=\"true\" text=\"证书\" AnchorPane.leftAnchor=\"0.0\" AnchorPane.topAnchor=\"264.0\""));
        assertTrue(fxml.contains("minWidth=\"0.0\" prefHeight=\"34.0\""));
        assertTrue(fxml.contains("AnchorPane.leftAnchor=\"106.0\" AnchorPane.rightAnchor=\"0.0\""));
        assertTrue(fxml.contains("<RevealablePasswordEditor fx:id=\"hostPasswordField\" accessibleText=\"SSH 主机密码\""));
        assertTrue(fxml.contains("<RevealablePasswordEditor fx:id=\"privateKeyPassphraseField\" accessibleText=\"SSH 私钥或证书口令\""));
        assertTrue(fxml.contains("fx:id=\"legacyAlgorithmsField\" accessibleText=\"旧 SSH 算法兼容模式\""));
        assertTrue(fxml.contains("<VBox maxWidth=\"Infinity\" spacing=\"6.0\" HBox.hgrow=\"ALWAYS\">"));
        assertTrue(fxml.contains("text=\"启用旧算法\""));
        assertFalse(fxml.contains("启用旧算法（降低安全性）"));
        assertFalse(fxml.contains("fx:id=\"secretField\""));
        assertFalse(fxml.contains("text=\"临时 SSH 连接\""));
        assertFalse(fxml.contains("旧算法兼容模式会降低连接安全性，仅应对可信旧服务器开启"));
        assertFalse(fxml.contains("text=\"管理凭证库 →\""));
        assertTrue(fxml.contains("#showHostLibraryDialog"));
        assertTrue(fxml.contains("#showQuickConnectDialog"));
        assertTrue(fxml.contains("styleClass=\"toolbar-primary-actions, toolbar-connection-actions\""));
        assertTrue(fxml.contains("styleClass=\"toolbar-primary-actions, toolbar-catalog-actions\""));
        assertTrue(fxml.contains("styleClass=\"toolbar-primary-actions, toolbar-workspace-actions\""));
        assertTrue(fxml.contains("accessibleText=\"打开本地终端\" contentDisplay=\"LEFT\" onAction=\"#openLocalTerminal\" styleClass=\"toolbar-action-button\" text=\"本地终端\""));
        assertTrue(fxml.contains("accessibleText=\"%action.quickConnect\" contentDisplay=\"LEFT\" onAction=\"#showQuickConnectDialog\" styleClass=\"toolbar-action-button\" text=\"快速连接\""));
        assertTrue(fxml.contains("accessibleText=\"%action.newHost\" contentDisplay=\"LEFT\" onAction=\"#createHost\" styleClass=\"toolbar-action-button\" text=\"%action.newHost\""));
        assertTrue(fxml.contains("accessibleText=\"打开主机库\" contentDisplay=\"LEFT\" onAction=\"#showHostLibraryDialog\" styleClass=\"toolbar-action-button\" text=\"主机库\""));
        assertTrue(fxml.contains("fx:id=\"toolbarGlobalFavoritesButton\" accessibleText=\"管理全局收藏夹\""));
        assertTrue(fxml.contains("fx:id=\"toolbarEditorButton\" accessibleText=\"%editor.title\""));
        assertTrue(fxml.contains("fx:id=\"toolbarAiAssistantButton\" accessibleText=\"AI 助手\" contentDisplay=\"LEFT\" onAction=\"#openAiWorkspace\""));
        assertTrue(fxml.contains("fx:id=\"toolbarAiAssistantDialogButton\" accessibleText=\"AI 助手\" contentDisplay=\"LEFT\" onAction=\"#openAiDialog\""));
        assertTrue(fxml.contains("fx:id=\"toolbarDiagnosticLogButton\" accessibleText=\"%diagnostic.log.toolbar\""));
        assertTrue(fxml.contains("onAction=\"#activateDiagnosticLogPlacement\" styleClass=\"toolbar-action-button\" text=\"%diagnostic.log.toolbar\""));
        assertTrue(fxml.indexOf("onAction=\"#manageGlobalBookmarks\"")
                > fxml.indexOf("onAction=\"#showHostLibraryDialog\""));
        assertFalse(fxml.contains("contentDisplay=\"TOP\" onAction=\"#openLocalTerminal\""));
        assertFalse(fxml.contains("contentDisplay=\"TOP\" onAction=\"#showQuickConnectDialog\""));
        assertFalse(fxml.contains("text=\"＋ 新建会话\""));
        assertTrue(fxml.contains("fx:id=\"parseQuickConnectClipboardButton\" maxWidth=\"Infinity\" onAction=\"#parseQuickConnectClipboard\" text=\"解析剪贴板\""));
        assertFalse(fxml.contains("fx:id=\"toolbarConnectButton\""));
        assertFalse(fxml.contains("fx:id=\"toolbarDisconnectButton\""));
        assertTrue(fxml.contains("fx:id=\"testQuickConnectionButton\" maxWidth=\"Infinity\" onAction=\"#testQuickConnection\" text=\"测试连接\""));
        assertTrue(fxml.contains("fx:id=\"connectButton\" defaultButton=\"true\" maxWidth=\"Infinity\""));
        assertTrue(fxml.contains("fx:id=\"disconnectButton\" maxWidth=\"Infinity\""));
        assertTrue(fxml.contains("fx:id=\"saveQuickHostButton\" maxWidth=\"Infinity\" onAction=\"#saveCurrentHost\" text=\"存入主机库\""));
        assertTrue(fxml.indexOf("fx:id=\"testQuickConnectionButton\"")
                < fxml.indexOf("fx:id=\"saveQuickHostButton\""));
        assertTrue(fxml.indexOf("fx:id=\"saveQuickHostButton\"")
                < fxml.indexOf("fx:id=\"disconnectButton\""));
        assertTrue(fxml.indexOf("fx:id=\"disconnectButton\"")
                < fxml.indexOf("fx:id=\"connectButton\""));
        assertTrue(fxml.contains("fx:id=\"quickConnectionTestStatus\" managed=\"false\""));
        assertTrue(fxml.contains("<ColumnConstraints percentWidth=\"28.0\"/><ColumnConstraints percentWidth=\"72.0\"/>"));
        assertTrue(fxml.contains("<ColumnConstraints percentWidth=\"100.0\"/>"));
        assertTrue(fxml.contains("maxWidth=\"Infinity\" spacing=\"5.0\" styleClass=\"home-hero\""));
        assertTrue(fxml.contains("onAction=\"#showHostLibraryDialog\" styleClass=\"home-action-card\" GridPane.columnIndex=\"0\" GridPane.rowIndex=\"3\""));
        assertTrue(fxml.contains("onAction=\"#createHost\" styleClass=\"home-action-card\" GridPane.columnIndex=\"0\" GridPane.rowIndex=\"4\""));
        assertFalse(fxml.contains("text=\"保存为会话…\""));
        assertTrue(fxml.contains("#createHost"));
        assertTrue(fxml.contains("accessibleText=\"新建主机\" contentDisplay=\"GRAPHIC_ONLY\""));
        assertEquals(9, occurrences(fxml, "styleClass=\"icon-button, catalog-create-button\""));
        assertTrue(fxml.contains("<LucideIconView icon=\"PLUS\" styleClass=\"catalog-create-icon\"/>"));
        assertTrue(fxml.contains("accessibleText=\"新建分组\" contentDisplay=\"GRAPHIC_ONLY\""));
        assertTrue(fxml.contains("styleClass=\"catalog-create-icon\""));
        assertFalse(fxml.contains("styleClass=\"catalog-create-plus\""));
        assertFalse(fxml.contains("styleClass=\"toolbar-idea-icon\""));
        assertFalse(fxml.contains("fx:id=\"savePasswordCheckBox\""));
        assertTrue(fxml.contains("#createReusableCredential"));
        assertTrue(fxml.contains("主机专属凭证不会显示在这里"));
        assertTrue(fxml.contains("#showSettings"));
        assertTrue(fxml.contains("#manageVault"));
        assertTrue(fxml.contains("fx:id=\"toolbarActivationStatusLabel\" accessibleRole=\"BUTTON\""));
        assertTrue(fxml.contains("onKeyPressed=\"#activateFromStatusKey\" onMouseClicked=\"#openLicenseActivation\""));
        assertTrue(fxml.contains("fx:id=\"vaultStatusLabel\" styleClass=\"status-pill-muted\" text=\"凭证库 · 未建立\""));
        assertTrue(fxml.contains("fx:id=\"vaultActionButton\" accessibleText=\"%action.credentialStatus\""));
        assertTrue(fxml.contains("fx:id=\"vaultActionIcon\" icon=\"LOCK_KEYHOLE\""));
        assertTrue(fxml.contains("fx:id=\"diagnosticLogMenuItem\" onAction=\"#openDiagnosticLogWorkspace\" text=\"%action.openLogWorkspace\""));
        assertTrue(fxml.contains("onAction=\"#openDiagnosticLogSidebar\" text=\"%action.openLogSidebar\""));
        assertTrue(fxml.contains("onAction=\"#focusCredentialManager\" text=\"%library.credentials\""));
        assertTrue(fxml.contains("onAction=\"#manageGlobalBookmarks\" text=\"路径收藏\"/><MenuItem onAction=\"#manageVault\" text=\"%action.credentialStatus\""));
        assertFalse(fxml.contains("fx:id=\"activityTabs\""));
        assertTrue(fxml.contains("fx:id=\"aiAssistantRailButton\" accessibleText=\"AI 助手\""));
        assertTrue(fxml.contains("fx:id=\"aiAssistantPage\""));
        assertTrue(fxml.contains("fx:id=\"aiPlacementButton\" accessibleText=\"在主工作区打开\""));
        assertTrue(fxml.contains("onAction=\"#togglePlacement\""));
        assertTrue(fxml.contains("fx:id=\"aiPlacementIcon\" icon=\"PANELS_TOP_LEFT\""));
        assertTrue(fxml.contains("fx:id=\"aiPlacementTooltip\" text=\"在主工作区打开\""));
        assertTrue(fxml.contains("fx:id=\"aiSessionList\""));
        assertTrue(fxml.contains("fx:id=\"aiNewChatButton\" accessibleText=\"新建对话\""));
        assertFalse(fxml.contains("onAction=\"#showHistory\""));
        assertTrue(fxml.contains("onAction=\"#createChat\""));
        assertFalse(fxml.contains("bottom-dock-header"));
        assertEquals(1, fxml.split("%import.secureCrt", -1).length - 1,
                "SecureCRT may only appear in the explicit external-import menu label");
        assertFalse(fxml.contains("SecureFX"));
        assertFalse(fxml.contains("保险库"));
        assertFalse(fxml.contains("可复用"));
        assertTrue(fxml.contains("凭证不关联具体主机，可被多个主机引用；主机专属凭证不会显示在这里，只能随对应主机编辑或删除。证书正文会加密保存到凭证库。"));
        assertTrue(fxml.contains("text=\"没有凭证\""));
        assertTrue(fxml.contains("text=\"新建凭证\""));
        assertTrue(fxml.contains("fx:id=\"diagnosticLogRailButton\" accessibleText=\"诊断日志\""));
        assertTrue(fxml.contains("fx:id=\"diagnosticLogPage\""));
        assertTrue(fxml.contains("<AlwaysVisibleVerticalTextArea fx:id=\"diagnosticLogTextArea\" accessibleText=\"诊断日志内容\""));
        assertTrue(fxml.contains("editable=\"false\" focusTraversable=\"true\" styleClass=\"diagnostic-log-text-area\""));
        assertTrue(fxml.contains("fx:id=\"diagnosticLogPlacementButton\""));
        assertTrue(fxml.contains("onAction=\"#clearDiagnosticLog\""));
        assertTrue(fxml.contains("icon=\"BRUSH_CLEANING\" styleClass=\"catalog-create-icon\""));
        assertTrue(fxml.contains("fx:id=\"diagnosticLogPathLabel\""));
        assertTrue(fxml.contains("onAction=\"#revealCurrentLogFile\""));
        assertTrue(fxml.contains("icon=\"LOCATE_FIXED\" styleClass=\"toolbar-action-icon\""));
        assertTrue(fxml.contains("onAction=\"#openLogCleanupSettings\" styleClass=\"compact-button\""));
        assertTrue(fxml.contains("icon=\"TRASH_2\" styleClass=\"toolbar-action-icon\""));
        assertTrue(fxml.contains("fx:id=\"primaryStatusLabel\""));
        assertTrue(fxml.contains("fx:id=\"primaryStatusLabel\" accessibleText=\"%diagnostic.log.toggleWorkspace\""));
        assertTrue(fxml.contains("onAction=\"#toggleDiagnosticLogWorkspace\" styleClass=\"status-log-trigger, status-primary\""));
        assertFalse(fxml.contains("fx:id=\"activeTabStatusLabel\""));
        assertFalse(fxml.contains("fx:id=\"workspaceStatisticsLabel\""));
        assertTrue(fxml.contains("fx:id=\"trafficStatisticsLabel\""));
        assertTrue(fxml.contains("fx:id=\"workspaceQuickTools\" alignment=\"CENTER_LEFT\""));
        assertTrue(fxml.contains("accessibleText=\"%action.about\" minWidth=\"-Infinity\" maxWidth=\"-Infinity\" onAction=\"#showAbout\" styleClass=\"status-about-button\" text=\"FastRemote\""));
        assertTrue(css.contains(".status-about-button"));
        assertTrue(css.contains("-fx-padding: 1px 6.5px;"));
        assertTrue(fxml.indexOf("fx:id=\"mcpStatusIndicator\"")
                < fxml.indexOf("fx:id=\"statusIndicator\""));
        assertTrue(fxml.indexOf("fx:id=\"mcpStatusLabel\"")
                < fxml.indexOf("fx:id=\"statusIndicator\""));
        assertTrue(fxml.indexOf("fx:id=\"statusIndicator\"")
                < fxml.indexOf("fx:id=\"primaryStatusLabel\""));
        assertTrue(fxml.indexOf("fx:id=\"primaryStatusLabel\"")
                < fxml.indexOf("fx:id=\"trafficStatisticsLabel\""));
        assertTrue(fxml.indexOf("fx:id=\"trafficStatisticsLabel\"")
                < fxml.indexOf("fx:id=\"workspaceQuickTools\""));
        assertFalse(fxml.contains("statusLogDrawer"));
        assertTrue(fxml.contains("wrapText=\"true\""));
        assertFalse(fxml.contains("fx:id=\"statusLogList\""));
    }

    @Test
    void statusAiButtonIsASeparateGroupImmediatelyBeforeAbout() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        org.w3c.dom.Document document;
        try (var input = MainView.fxml().openStream()) {
            document = factory.newDocumentBuilder().parse(input);
        }
        Element statusBar = elementWithFxId(document.getDocumentElement(), "statusBar");
        Element ai = elementWithFxId(statusBar, "statusAiButton");
        assertEquals("Button", ai.getTagName());
        assertEquals("", ai.getAttribute("text"));
        assertEquals("GRAPHIC_ONLY", ai.getAttribute("contentDisplay"));
        assertEquals("#toggleAiWorkspace", ai.getAttribute("onAction"));
        assertEquals("%ai.toggleWorkspace", ai.getAttribute("accessibleText"));
        assertEquals("AI\n", iconGeometry(ai));
        assertEquals("%ai.toggleWorkspace",
                ((Element) ai.getElementsByTagName("Tooltip").item(0)).getAttribute("text"));

        var children = new java.util.ArrayList<Element>();
        for (Node child = statusBar.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) children.add(element);
        }
        int index = children.indexOf(ai);
        assertEquals(children.size() - 3, index);
        assertEquals("workspaceQuickTools", children.get(index - 2).getAttribute("fx:id"));
        assertEquals("Separator", children.get(index - 1).getTagName());
        assertEquals("Separator", children.get(index + 1).getTagName());
        assertEquals("#showAbout", children.get(index + 2).getAttribute("onAction"));
    }

    @Test
    void libraryActionButtonsShareIconsAndSnippetImportSharesSecondaryToolbarWithVariables() throws Exception {
        var documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        java.util.List<org.w3c.dom.Document> documents = new java.util.ArrayList<>();
        for (var resource : java.util.stream.Stream.concat(java.util.stream.Stream.of(MainView.fxml()),
                MainView.includedFxml().stream()).toList()) {
            try (var input = resource.openStream()) {
                documents.add(documentBuilderFactory.newDocumentBuilder().parse(input));
            }
        }

        Element sessionCreate = catalogButtonByAction(documents, "#createHost");
        Element sessionGroup = catalogButtonByAction(documents, "#createGroup");
        Element snippetCreate = catalogButtonByAction(documents, "#createSnippet");
        Element snippetGroup = catalogButtonByAction(documents, "#createSnippetGroup");
        Element credentialCreate = catalogButtonByAction(documents, "#createReusableCredential");
        Element credentialGroup = catalogButtonByAction(documents, "#createCredentialGroup");
        Element variableCreate = catalogButtonByAction(documents, "#createVariable");
        Element variableGroup = catalogButtonByAction(documents, "#createVariableGroup");
        Element snippetImport = buttonByAttribute(documents, "fx:id", "leftDockImportButton");
        Element snippetExport = buttonByAttribute(documents, "fx:id", "leftDockExportButton");
        Element builtinCommandImport = buttonByAttribute(documents, "onAction", "#importBuiltinCommands");
        Element hostLibrary = buttonByAttribute(documents, "fx:id", "leftHostLibraryButton");
        Element toolbarSessionManager = buttonByAttribute(documents, "onAction", "#showHostLibraryDialog");
        Element hostLibraryRail = buttonByAttribute(documents, "fx:id", "hostLibraryRailButton");
        Element toolbarQuickConnect = buttonByAttribute(documents, "onAction", "#showQuickConnectDialog");
        Element quickConnectRail = buttonByAttribute(documents, "fx:id", "quickConnectRailButton");

        assertEquals(iconSignature(sessionCreate), iconSignature(snippetCreate));
        assertEquals(iconSignature(sessionCreate), iconSignature(credentialCreate));
        assertEquals(iconSignature(sessionGroup), iconSignature(snippetGroup));
        assertEquals(iconSignature(sessionGroup), iconSignature(credentialGroup));
        assertEquals(iconSignature(sessionCreate), iconSignature(variableCreate));
        assertEquals(iconSignature(sessionGroup), iconSignature(variableGroup));
        assertEquals(iconGeometry(toolbarSessionManager), iconGeometry(hostLibraryRail));
        assertEquals(iconGeometry(toolbarQuickConnect), iconGeometry(quickConnectRail));
        assertSame(snippetCreate.getParentNode(), snippetGroup.getParentNode());
        assertSame(snippetImport.getParentNode(), snippetExport.getParentNode());
        assertEquals("GRAPHIC_ONLY", snippetImport.getAttribute("contentDisplay"));
        assertEquals("GRAPHIC_ONLY", snippetExport.getAttribute("contentDisplay"));
        assertEquals("", snippetImport.getAttribute("text"));
        assertEquals("", snippetExport.getAttribute("text"));
        assertEquals("IMPORT\n", iconGeometry(snippetImport));
        assertEquals("EXPORT\n", iconGeometry(snippetExport));
        assertEquals("PACKAGE\n", iconGeometry(builtinCommandImport));
        assertNotEquals(iconGeometry(snippetImport), iconGeometry(builtinCommandImport));
        assertNotEquals(iconGeometry(snippetImport), iconGeometry(snippetExport));
        assertEquals(1, snippetImport.getElementsByTagName("Tooltip").getLength());
        assertEquals(1, snippetExport.getElementsByTagName("Tooltip").getLength());
    }

    @Test
    void toolbarPrimaryActionsKeepTheRequestedGroupsAndOrder() throws Exception {
        var documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        org.w3c.dom.Document document;
        try (var input = MainView.fxml().openStream()) {
            document = documentBuilderFactory.newDocumentBuilder().parse(input);
        }

        Element localTerminal = buttonByAttribute(document, "onAction", "#openLocalTerminal");
        Element localFiles = buttonByAttribute(document, "onAction", "#openLocalFiles");
        Element quickConnect = buttonByAttribute(document, "onAction", "#showQuickConnectDialog");
        Element createHost = buttonByAttribute(document, "onAction", "#createHost");
        Element hostLibrary = buttonByAttribute(document, "onAction", "#showHostLibraryDialog");
        Element editor = buttonByAttribute(document, "fx:id", "toolbarEditorButton");
        Element aiAssistant = buttonByAttribute(document, "fx:id", "toolbarAiAssistantButton");
        Element aiDialog = buttonByAttribute(document, "fx:id", "toolbarAiAssistantDialogButton");
        assertEquals("#openAiWorkspace", aiAssistant.getAttribute("onAction"));
        assertEquals("#openAiDialog", aiDialog.getAttribute("onAction"));
        assertEquals(aiAssistant.getAttribute("text"), aiDialog.getAttribute("text"));
        Element diagnosticLog = buttonByAttribute(
                document, "onAction", "#activateDiagnosticLogPlacement");
        Element pathFavorites = buttonByAttribute(document, "onAction", "#manageGlobalBookmarks");

        Element connectionGroup = toolbarGroup(localTerminal);
        Element catalogGroup = toolbarGroup(createHost);
        Element workspaceGroup = toolbarGroup(aiAssistant);
        assertSame(connectionGroup, toolbarGroup(localFiles));
        assertSame(catalogGroup, toolbarGroup(quickConnect));
        assertSame(catalogGroup, toolbarGroup(hostLibrary));
        assertSame(workspaceGroup, toolbarGroup(pathFavorites));
        assertSame(workspaceGroup, toolbarGroup(aiDialog));
        assertSame(workspaceGroup, toolbarGroup(diagnosticLog));
        assertNotSame(connectionGroup, catalogGroup);
        assertNotSame(catalogGroup, workspaceGroup);
        assertTrue(connectionGroup.getAttribute("styleClass").contains("toolbar-connection-actions"));
        assertTrue(catalogGroup.getAttribute("styleClass").contains("toolbar-catalog-actions"));
        assertTrue(workspaceGroup.getAttribute("styleClass").contains("toolbar-workspace-actions"));
        assertTrue((localTerminal.compareDocumentPosition(localFiles) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
        assertTrue((localFiles.compareDocumentPosition(quickConnect) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
        assertTrue((quickConnect.compareDocumentPosition(hostLibrary) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
        assertTrue((hostLibrary.compareDocumentPosition(createHost) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
        assertTrue((createHost.compareDocumentPosition(pathFavorites) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
        assertTrue((pathFavorites.compareDocumentPosition(editor) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
        assertTrue((editor.compareDocumentPosition(aiAssistant) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
        assertTrue((aiAssistant.compareDocumentPosition(diagnosticLog) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
        assertTrue((aiAssistant.compareDocumentPosition(aiDialog) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
        assertTrue((aiDialog.compareDocumentPosition(diagnosticLog) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
    }

    private static Element toolbarGroup(Element button) {
        return (Element) button.getParentNode().getParentNode();
    }

    private static Element buttonByAttribute(org.w3c.dom.Document document, String attribute, String value) {
        var buttons = document.getElementsByTagName("Button");
        for (int index = 0; index < buttons.getLength(); index++) {
            Element button = (Element) buttons.item(index);
            if (value.equals(button.getAttribute(attribute))
                    && (button.getElementsByTagName("SVGPath").getLength() > 0
                            || button.getElementsByTagName("LucideIconView").getLength() > 0)) {
                return button;
            }
        }
        throw new AssertionError("missing button with " + attribute + "=" + value);
    }

    private static Element catalogButtonByAction(org.w3c.dom.Document document, String action) {
        var buttons = document.getElementsByTagName("Button");
        for (int index = 0; index < buttons.getLength(); index++) {
            Element button = (Element) buttons.item(index);
            if (action.equals(button.getAttribute("onAction"))
                    && button.getAttribute("styleClass").contains("catalog-create-button")) {
                return button;
            }
        }
        throw new AssertionError("missing catalog button with onAction=" + action);
    }

    private static Element catalogButtonByAction(
            java.util.List<org.w3c.dom.Document> documents, String action) {
        for (var document : documents) {
            try { return catalogButtonByAction(document, action); }
            catch (AssertionError ignored) { }
        }
        throw new AssertionError("missing catalog button with onAction=" + action);
    }

    private static Element buttonByAttribute(
            java.util.List<org.w3c.dom.Document> documents, String attribute, String value) {
        for (var document : documents) {
            try { return buttonByAttribute(document, attribute, value); }
            catch (AssertionError ignored) { }
        }
        throw new AssertionError("missing button with " + attribute + "=" + value);
    }

    private static String iconSignature(Element button) {
        StringBuilder signature = new StringBuilder();
        var icons = button.getElementsByTagName("LucideIconView");
        for (int index = 0; index < icons.getLength(); index++) {
            Element icon = (Element) icons.item(index);
            signature.append(icon.getAttribute("icon"))
                    .append('|')
                    .append(icon.getAttribute("styleClass"))
                    .append('\n');
        }
        var paths = button.getElementsByTagName("SVGPath");
        for (int index = 0; index < paths.getLength(); index++) {
            Element path = (Element) paths.item(index);
            signature.append(path.getAttribute("content"))
                    .append('|')
                    .append(path.getAttribute("styleClass"))
                    .append('\n');
        }
        return signature.toString();
    }

    private static String iconGeometry(Element button) {
        StringBuilder geometry = new StringBuilder();
        var icons = button.getElementsByTagName("LucideIconView");
        for (int index = 0; index < icons.getLength(); index++) {
            geometry.append(((Element) icons.item(index)).getAttribute("icon")).append('\n');
        }
        var paths = button.getElementsByTagName("SVGPath");
        for (int index = 0; index < paths.getLength(); index++) {
            geometry.append(((Element) paths.item(index)).getAttribute("content")).append('\n');
        }
        return geometry.toString();
    }

    private static String cssBlock(String stylesheet, String selector) {
        int start = stylesheet.indexOf(selector);
        assertTrue(start >= 0, "missing CSS selector: " + selector);
        int end = stylesheet.indexOf('}', start);
        assertTrue(end > start, "unterminated CSS selector: " + selector);
        return stylesheet.substring(start, end + 1);
    }

    @Test
    void menuItemsUseShortLabelsWithoutEllipses() throws Exception {
        var documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var input = MainView.fxml().openStream()) {
            var document = documentBuilderFactory.newDocumentBuilder().parse(input);
            var items = document.getElementsByTagName("MenuItem");
            for (int index = 0; index < items.getLength(); index++) {
                Element item = (Element) items.item(index);
                String text = item.getAttribute("text");
                assertFalse(text.contains("…") || text.contains("..."), text);
                assertFalse("true".equals(item.getAttribute("disable")),
                        "global menus must not advertise placeholders: " + text);
            }
        }
        String fxml;
        fxml = localizedFxml();
        assertFalse(fxml.contains("将当前连接存入主机库"));
        assertTrue(fxml.contains("onAction=\"#reconnectSelectedHost\" text=\"%action.reconnect\""));
        assertFalse(fxml.contains("显示/隐藏状态日志"));
        assertTrue(fxml.contains("text=\"%action.hostLibrary\""));
        assertTrue(fxml.contains("onAction=\"#exitApplication\" text=\"%action.exit\""));
        int fileMenu = fxml.indexOf("<Menu text=\"%menu.file\"");
        int editMenu = fxml.indexOf("<Menu text=\"%menu.edit\"");
        int configurationMenu = fxml.indexOf("<Menu text=\"%menu.configuration\"");
        int sessionMenu = fxml.indexOf("<Menu text=\"%menu.hosts\"");
        assertTrue(fileMenu >= 0 && fileMenu < editMenu && editMenu < configurationMenu
                && configurationMenu < sessionMenu);
        int toolsMenu = fxml.indexOf("<Menu text=\"%menu.tools\"");
        String configurationItems = fxml.substring(configurationMenu, sessionMenu);
        String toolsItems = fxml.substring(toolsMenu, fxml.indexOf("<Menu text=\"%menu.view\""));
        int viewMenu = fxml.indexOf("<Menu text=\"%menu.view\"");
        int helpMenu = fxml.indexOf("<Menu text=\"%menu.help\"");
        String viewItems = fxml.substring(viewMenu, helpMenu);
        assertTrue(viewItems.indexOf("fx:id=\"globalToolbarMenuItem\"")
                < viewItems.indexOf("#toggleFloatingLeftPanel"));
        assertTrue(viewItems.contains(
                "onAction=\"#toggleGlobalToolbar\" selected=\"true\" text=\"%action.toolbar\""));
        assertTrue(viewItems.indexOf("fx:id=\"globalToolbarMenuItem\"")
                < viewItems.indexOf("fx:id=\"globalStatusBarMenuItem\""));
        assertTrue(viewItems.contains(
                "onAction=\"#toggleGlobalStatusBar\" selected=\"true\" text=\"%action.statusBar\""));
        assertTrue(configurationItems.contains(
                "fx:id=\"settingsMenuItem\" onAction=\"#showSettings\" text=\"%action.settings\""));
        assertTrue(configurationItems.indexOf("fx:id=\"settingsMenuItem\"")
                > configurationItems.indexOf("onAction=\"#exportCatalog\""));
        assertFalse(toolsItems.contains("fx:id=\"settingsMenuItem\""));
        assertTrue(toolsItems.contains(
                "fx:id=\"aiAssistantMenuItem\" onAction=\"#openAiDialog\""));
        assertTrue(toolsItems.contains(
                "fx:id=\"diagnosticLogMenuItem\" onAction=\"#openDiagnosticLogWorkspace\""));
        assertFalse(toolsItems.contains("onAction=\"#focusCredentialManager\""));
        assertFalse(toolsItems.contains("onAction=\"#focusSnippetManager\""));
        assertFalse(toolsItems.contains("onAction=\"#focusVariableManager\""));
        assertFalse(toolsItems.contains("onAction=\"#openAiSidebar\""));
        assertFalse(toolsItems.contains("onAction=\"#openDiagnosticLogSidebar\""));
        assertFalse(viewItems.contains("fx:id=\"aiAssistantMenuItem\""));
        assertFalse(viewItems.contains("fx:id=\"diagnosticLogMenuItem\""));
        List<String> viewPanelActions = List.of(
                "#focusQuickConnect", "#focusHostLibrary", "#focusCredentialManager",
                "#focusSnippetManager", "#focusVariableManager", "#openAiSidebar",
                "#openDiagnosticLogSidebar");
        int previousViewAction = -1;
        for (String action : viewPanelActions) {
            int actionIndex = viewItems.indexOf(action);
            assertTrue(actionIndex > previousViewAction, "view menu action order: " + action);
            previousViewAction = actionIndex;
        }
        assertTrue(fxml.contains("onAction=\"#newEditorDocument\" text=\"%action.newFile\""));
        assertTrue(fxml.contains("onAction=\"#openTextFile\" text=\"%action.openFile\""));
        assertTrue(fxml.contains("fx:id=\"openRecentMenu\" onShowing=\"#rebuildRecentFiles\""));
        assertTrue(fxml.contains("onAction=\"#saveTextFile\" text=\"%action.save\""));
        assertTrue(fxml.contains("onAction=\"#saveTextFileAs\" text=\"%action.saveAs\""));
        assertTrue(fxml.contains("onAction=\"#saveAllTextFiles\" text=\"%action.saveAll\""));
        assertTrue(fxml.contains("onAction=\"#closeTextFile\" text=\"%action.closeFile\""));
        assertTrue(fxml.indexOf("onAction=\"#exitApplication\"") < editMenu);
        assertTrue(fxml.contains("onAction=\"#cutText\" text=\"%action.cut\""));
        assertTrue(fxml.contains("onAction=\"#copyText\" text=\"%action.copy\""));
        assertTrue(fxml.contains("onAction=\"#pasteText\" text=\"%action.paste\""));
        assertTrue(fxml.contains("onAction=\"#findText\" text=\"%action.find\""));
        assertTrue(fxml.contains("onAction=\"#replaceText\" text=\"%action.replace\""));
        assertTrue(fxml.contains("onAction=\"#importSecureCrtCatalog\" text=\"%import.secureCrt\""));
        assertTrue(fxml.contains("onAction=\"#importOpenSshCatalog\" text=\"%import.openSshConfig\""));
        assertTrue(fxml.contains("<Menu text=\"%config.import.menu\">"));
        assertFalse(fxml.contains("<Menu text=\"导入\">"));
        int exportConfiguration = fxml.indexOf("onAction=\"#exportCatalog\" text=\"%config.export.title\"");
        int importCommonCommands = fxml.indexOf("onAction=\"#importBuiltinCommands\"");
        assertTrue(exportConfiguration >= 0 && importCommonCommands > exportConfiguration);
        assertTrue(fxml.contains("onAction=\"#importCatalog\" text=\"%config.import.title\""));
        assertFalse(fxml.contains("onAction=\"#importSnippets\""));
        assertFalse(fxml.contains("onAction=\"#exportVariables\""));
        assertFalse(fxml.contains("text=\"广播命令\""));
        assertFalse(fxml.contains("text=\"克隆会话标签\""));
    }

    @Test
    void fxmlDoesNotHardcodeEllipsesIntoUserFacingAttributes() throws IOException {
        String fxml = localizedFxml();
        var matcher = java.util.regex.Pattern.compile(
                "(?:text|promptText|accessibleText)=\\\"[^\\\"]*(?:…|\\.\\.\\.)[^\\\"]*\\\"")
                .matcher(fxml);
        boolean found = matcher.find();
        assertFalse(found, found ? matcher.group() : "hardcoded ellipsis");
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int offset = 0; (offset = value.indexOf(needle, offset)) >= 0; offset += needle.length()) {
            count++;
        }
        return count;
    }

    @Test
    void globalActivityRailIsRemovedFromTheMainView() throws Exception {
        var documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var input = MainView.fxml().openStream()) {
            var document = documentBuilderFactory.newDocumentBuilder().parse(input);
            assertEquals(null, elementWithFxId(document.getDocumentElement(), "bottomDockRestoreButton"));
            assertEquals(null, elementWithFxId(document.getDocumentElement(), "bottomToolRail"));
        }
    }

    @Test
    void usesTheNativeSystemMenuBarWhenThePlatformSupportsIt() throws Exception {
        var documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var input = MainView.fxml().openStream()) {
            var document = documentBuilderFactory.newDocumentBuilder().parse(input);
            var menuBars = document.getElementsByTagName("MenuBar");

            assertEquals(1, menuBars.getLength());
            assertEquals("true", ((Element) menuBars.item(0)).getAttribute("useSystemMenuBar"));
        }
    }

    @Test
    void fixedToolbarIconActionsRetainAccessibleNamesAndTooltips() throws Exception {
        var documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var input = MainView.fxml().openStream()) {
            var document = documentBuilderFactory.newDocumentBuilder().parse(input);
            for (String fxId : new String[] {
                    "vaultActionButton",
                    "toolbarLeftDockButton",
                    "toolbarRightDockButton",
                    "toolbarSettingsButton"
            }) {
                Element button = elementWithFxId(document.getDocumentElement(), fxId);

                assertNotNull(button);
                assertFalse(button.getAttribute("text").isBlank());
                assertEquals("GRAPHIC_ONLY", button.getAttribute("contentDisplay"));
                assertEquals("toolbar-icon-button", button.getAttribute("styleClass"));
                assertFalse(button.getAttribute("accessibleText").isBlank());
                assertTrue(hasDescendantNamed(button, "StackPane"));
                assertTrue(hasDescendantNamed(button, "LucideIconView"));
                assertTrue(hasDescendantNamed(button, "Tooltip"));
            }

            Element vaultButton = elementWithFxId(document.getDocumentElement(), "vaultActionButton");
            assertTrue(hasDescendantNamed(vaultButton, "LucideIconView"));
            assertFalse(hasDescendantNamed(vaultButton, "Arc"));

            Element activationStatus = elementWithFxId(
                    document.getDocumentElement(), "toolbarActivationStatusLabel");
            assertNotNull(activationStatus);
            assertEquals("BUTTON", activationStatus.getAttribute("accessibleRole"));
            assertEquals("true", activationStatus.getAttribute("focusTraversable"));
            assertTrue(hasDescendantNamed(activationStatus, "Tooltip"));

            assertPrecedes(document.getDocumentElement(), "toolbarActivationStatusLabel", "vaultStatusLabel");
            assertPrecedes(document.getDocumentElement(), "vaultStatusLabel", "vaultActionButton");
            assertPrecedes(document.getDocumentElement(), "vaultActionButton", "toolbarSettingsButton");
            assertPrecedes(document.getDocumentElement(), "toolbarSettingsButton", "toolbarLeftDockButton");
            assertPrecedes(document.getDocumentElement(), "toolbarLeftDockButton", "toolbarRightDockButton");
        }
    }

    @Test
    void aiChatUsesSidebarSessionsAndASeparateInferenceRow() throws IOException {
        String fxml;
        fxml = localizedFxml();
        String stylesheet;
        try (var input = MainView.stylesheet().openStream()) {
            stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String darkStylesheet;
        try (var input = MainView.darkStylesheet().openStream()) {
            darkStylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertFalse(fxml.contains("fx:id=\"aiHistoryButton\""));
        assertTrue(fxml.contains("fx:id=\"aiSessionList\""));
        assertFalse(fxml.contains("icon=\"MESSAGE_SQUARE_MORE\""));
        assertTrue(fxml.contains("fx:id=\"aiNewChatButton\" accessibleText=\"新建对话\" text=\"新建对话\""));
        assertFalse(fxml.contains("aiNewChatButton\" accessibleText=\"新建对话\" contentDisplay=\"LEFT\""));
        assertTrue(fxml.contains("styleClass=\"ai-inference-row\" GridPane.columnIndex=\"0\" GridPane.columnSpan=\"3\" GridPane.rowIndex=\"1\""));
        assertTrue(fxml.contains("text=\"显示推理\""));
        assertTrue(fxml.contains("styleClass=\"ai-composer-button, ai-settings-button\" GridPane.columnIndex=\"4\" GridPane.rowIndex=\"0\" GridPane.rowSpan=\"2\" GridPane.valignment=\"CENTER\""));
        assertTrue(fxml.contains("GridPane.columnIndex=\"5\" GridPane.rowIndex=\"0\" GridPane.rowSpan=\"2\""));
        assertTrue(fxml.contains("fx:id=\"aiStopButton\""));
        assertTrue(fxml.contains("fx:id=\"aiSendOptions\""));
        assertTrue(fxml.contains("onAction=\"#stop\""));
        assertTrue(fxml.contains("onAction=\"#steer\""));
        assertFalse(fxml.contains("ai-composer-tall-button"));
        assertTrue(cssBlock(stylesheet, ".ai-model-bar {").contains("-fx-pref-height: 48px;"));
        assertTrue(cssBlock(stylesheet, ".ai-composer-button {").contains("-fx-pref-height: 28px;"));
        assertTrue(cssBlock(stylesheet, ".ai-chat-toolbar-button {").contains("-fx-border-color: -fr-border;"));
        assertTrue(cssBlock(stylesheet, ".ai-chat-toolbar {")
                .contains("-fx-border-color: transparent transparent -fr-border transparent;"));
        assertTrue(cssBlock(stylesheet, ".ai-chat-toolbar {").contains("-fx-padding: 1px 1px 1px 3px;"));
        assertTrue(fxml.contains("<fx:include fx:id=\"aiAssistantPage\" source=\"AiAssistantView.fxml\"/>"));
        assertTrue(fxml.contains("fx:controller=\"com.ynpe.fastremote.ui.AiAssistantController\" styleClass=\"component-page, global-ai-page\""));
        assertTrue(fxml.contains("<VBox.margin><Insets top=\"8.0\"/></VBox.margin>"));
        assertTrue(cssBlock(stylesheet, ".ai-history-action-button {").contains("-fx-pref-width: 24px;"));
        String historyRow = cssBlock(stylesheet, ".ai-history-row {");
        assertTrue(historyRow.contains("-fx-min-width: 0;"));
        assertTrue(historyRow.contains("-fx-max-width: Infinity;"));
        assertFalse(historyRow.contains("-fx-pref-width: 344px;"));
        assertTrue(stylesheet.contains(".ai-history-rename-button:hover .lucide-icon-stroke"));
        assertTrue(cssBlock(darkStylesheet, ".ai-chat-toolbar-button {")
                .contains("-fx-background-color: #232427;"));
        assertTrue(fxml.contains("fx:id=\"aiConversationHost\" accessibleText=\"AI 聊天内容\""));
        assertFalse(fxml.contains("fx:id=\"aiConversationScroll\""));
        assertTrue(cssBlock(stylesheet, ".ai-chat-host, .ai-chat-view {")
                .contains("-fx-background-color: -fr-surface;"));
        assertFalse(stylesheet.contains(".ai-markdown-area"));
        assertFalse(darkStylesheet.contains(".ai-markdown-area"));
    }

    private static Element elementWithFxId(Element root, String fxId) {
        if (fxId.equals(root.getAttribute("fx:id"))) {
            return root;
        }
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                Element match = elementWithFxId(element, fxId);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static String localizedFxml() throws IOException {
        String source;
        try (var input = MainView.fxml().openStream()) {
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (var child : MainView.includedFxml()) {
            try (var input = child.openStream()) {
                source += '\n' + new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        ResourceBundle bundle = ResourceBundle.getBundle(
                com.ynpe.fastremote.i18n.Localizer.BUNDLE_NAME, java.util.Locale.SIMPLIFIED_CHINESE);
        var reference = java.util.regex.Pattern.compile(
                        "%((?:(?:legacy\\.(?:java|fxml)|form)\\.[A-Za-z0-9_.-]+|ai\\.chat\\.(?:newSession|create)))")
                .matcher(source);
        StringBuilder result = new StringBuilder();
        while (reference.find()) {
            String value = bundle.getString(reference.group(1))
                    .replace("&", "&amp;").replace("\"", "&quot;")
                    .replace("<", "&lt;").replace(">", "&gt;");
            reference.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(value));
        }
        reference.appendTail(result);
        return result.toString();
    }

    private static boolean hasAncestorWithFxId(Node node, String fxId) {
        for (Node ancestor = node.getParentNode(); ancestor != null; ancestor = ancestor.getParentNode()) {
            if (ancestor instanceof Element element && fxId.equals(element.getAttribute("fx:id"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDescendantNamed(Element root, String tagName) {
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && (tagName.equals(element.getTagName()) || hasDescendantNamed(element, tagName))) {
                return true;
            }
        }
        return false;
    }

    private static void assertPrecedes(Element root, String earlierFxId, String laterFxId) {
        Element earlier = elementWithFxId(root, earlierFxId);
        Element later = elementWithFxId(root, laterFxId);
        assertNotNull(earlier);
        assertNotNull(later);
        assertTrue((earlier.compareDocumentPosition(later) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
    }
}
