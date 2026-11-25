// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.SettingsPageLayout.page;
import static com.ynpe.fastremote.ui.SettingsPageLayout.card;
import static com.ynpe.fastremote.ui.SettingsPageLayout.labeled;
import static com.ynpe.fastremote.ui.SettingsPageLayout.enumConverter;
import static com.ynpe.fastremote.ui.SettingsPageLayout.spinner;
import static com.ynpe.fastremote.ui.TerminalSettingsPage.TerminalControls;
import static com.ynpe.fastremote.ui.SettingsFontSelector.SearchableFontSelector;
import static com.ynpe.fastremote.ui.SettingsPageLayout.SELECTION_CONTROL_MIN_WIDTH;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.settings.SftpSettings;
import com.ynpe.fastremote.domain.settings.SftpDoubleClickAction;
import com.ynpe.fastremote.domain.settings.EditorTheme;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;

final class TransferSettingsPage {
    private TransferSettingsPage() {}

    static Node transferPage(CheckBox showDirectoryTree, CheckBox showHiddenFiles,
            Spinner<Integer> concurrency, ComboBox<SftpDoubleClickAction> doubleClickFileAction,
            Button manageGlobalBookmarks) {
        return page("SFTP", com.ynpe.fastremote.i18n.Texts.text("legacy.java.98ffbf215ebb038d"),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e842a0ad3266aa2e"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.ddf740ba2f2dfcb8"),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.33963885165d5d43"), concurrency),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("sftp.settings.doubleClickAction"), doubleClickFileAction)),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8b97daa7380a7904"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.6aadfd25aed21594"),
                        showDirectoryTree, showHiddenFiles, manageGlobalBookmarks));
    }

    static Node editorPage(EditorControls controls, Spinner<Integer> maxEditableFileMiB) {
        return page(com.ynpe.fastremote.i18n.Texts.text("editor.settings.title"),
                com.ynpe.fastremote.i18n.Texts.text("editor.settings.description"),
                card(com.ynpe.fastremote.i18n.Texts.text("editor.settings.themeCard"),
                        com.ynpe.fastremote.i18n.Texts.text("editor.settings.themeDescription"),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("editor.settings.theme"), controls.theme())),
                card(com.ynpe.fastremote.i18n.Texts.text("editor.settings.fontCard"),
                        com.ynpe.fastremote.i18n.Texts.text("editor.settings.fontDescription"),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.078838da4218490b"), controls.fontFamily().node()),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f71561b5af0abced"), controls.fontSize()), controls.appearance()),
                card(com.ynpe.fastremote.i18n.Texts.text("editor.settings.fileCard"),
                        com.ynpe.fastremote.i18n.Texts.text("editor.settings.fileDescription"),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("editor.settings.maxSize"), maxEditableFileMiB)));
    }

    record EditorControls(ComboBox<EditorTheme> theme,
            SearchableFontSelector fontFamily, Spinner<Integer> fontSize, TextAppearanceControls appearance) {
        static EditorControls from(SftpSettings settings) {
            ComboBox<EditorTheme> theme = editorThemeChoice(settings.editorTheme());
            SearchableFontSelector family = new SearchableFontSelector(TerminalControls.systemFontFamilies(), settings.editorFontFamily());
            Spinner<Integer> size = spinner(TerminalPreferences.MIN_FONT_SIZE, TerminalPreferences.MAX_FONT_SIZE, settings.editorFontSize());
            TextAppearanceControls appearance = new TextAppearanceControls(settings.editorTextAppearance(), false,
                    family::selectedFont, () -> TerminalControls.spinnerValue(size));
            family.selectedFontProperty().addListener(ignored -> appearance.refreshPreview());
            size.valueProperty().addListener(ignored -> appearance.refreshPreview());
            return new EditorControls(theme, family, size, appearance);
        }

        void addChangeListeners(Runnable listener) {
            appearance.onChange(listener);
            theme.valueProperty().addListener((ignored, previous, selected) -> listener.run());
            fontFamily.selectedFontProperty().addListener((ignored, previous, selected) -> listener.run());
            fontSize.valueProperty().addListener((ignored, previous, selected) -> listener.run());
            fontSize.getEditor().textProperty().addListener((ignored, previous, selected) -> listener.run());
        }
    }

    static ComboBox<EditorTheme> editorThemeChoice(EditorTheme selected) {
        ComboBox<EditorTheme> theme = new ComboBox<>();
        theme.getItems().setAll(EditorTheme.values());
        theme.setValue(selected);
        theme.setConverter(enumConverter(value -> switch (value) {
            case DARK -> com.ynpe.fastremote.i18n.Texts.text("editor.settings.themeDark");
            case LIGHT -> com.ynpe.fastremote.i18n.Texts.text("editor.settings.themeLight");
        }));
        theme.setMinWidth(SELECTION_CONTROL_MIN_WIDTH);
        return theme;
    }

    static ComboBox<SftpDoubleClickAction> sftpDoubleClickActionChoice(SftpDoubleClickAction selected) {
        ComboBox<SftpDoubleClickAction> choice = new ComboBox<>();
        choice.getItems().setAll(SftpDoubleClickAction.values());
        choice.setValue(selected);
        choice.setConverter(enumConverter(value -> switch (value) {
            case OPEN -> com.ynpe.fastremote.i18n.Texts.text("sftp.settings.doubleClickOpen");
            case TRANSFER -> com.ynpe.fastremote.i18n.Texts.text("sftp.settings.doubleClickTransfer");
        }));
        choice.setMinWidth(SELECTION_CONTROL_MIN_WIDTH);
        return choice;
    }
}
