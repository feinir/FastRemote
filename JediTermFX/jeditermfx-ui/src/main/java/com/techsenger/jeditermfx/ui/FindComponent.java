package com.techsenger.jeditermfx.ui;

import com.techsenger.jeditermfx.core.Color;
import com.techsenger.jeditermfx.core.emulator.ColorPalette;
import com.techsenger.jeditermfx.ui.settings.SettingsProvider;
import java.util.Locale;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Read-only terminal search panel shaped like FastRemote's editor search surface. */
final class FindComponent {

    private static final int MAX_HISTORY = 15;
    private static final String FIND_STYLESHEET =
            FindComponent.class.getResource("find-component.css").toExternalForm();
    private final VBox pane = new VBox(10);
    private final HistoryComboBox find = new HistoryComboBox();
    private final Label label = new Label();
    private final CheckBox matchCaseCheckBox = new CheckBox();
    private final CheckBox regexCheckBox = new CheckBox();
    private final JediTermFxWidget jediTermWidget;
    private final SettingsProvider settingsProvider;
    private final ContextMenu inputContextMenu = new ContextMenu();
    private List<String> inputContextStyleClasses = List.of();
    private String paletteStyle = "";
    private String historyStyleClass = "jeditermfx-find-history-dark";

    FindComponent(JediTermFxWidget jediTermWidget, ObservableList<String> history) {
        this.jediTermWidget = jediTermWidget;
        settingsProvider = jediTermWidget.mySettingsProvider;
        pane.setId("jeditermfx-find-component");
        pane.getStyleClass().add("jeditermfx-find-component");
        pane.getStylesheets().add(FIND_STYLESHEET);

        find.setId("jeditermfx-find-field");
        find.getStyleClass().add("jeditermfx-find-field");
        find.setPromptText(settingsProvider.getFindFieldPromptText());
        find.setEditable(true);
        find.setItems(history);
        find.setMaxWidth(Double.MAX_VALUE);
        installInputContextMenu();
        find.setOnShown(event -> {
            applyPopupPalette();
            Platform.runLater(this::applyPopupPalette);
        });

        label.setId("jeditermfx-find-result");
        label.getStyleClass().add("jeditermfx-find-result");
        label.setAlignment(Pos.CENTER_LEFT);

        matchCaseCheckBox.setId("jeditermfx-find-match-case");
        matchCaseCheckBox.getStyleClass().add("jeditermfx-find-option");
        matchCaseCheckBox.setText(settingsProvider.getFindMatchCaseText());
        regexCheckBox.setId("jeditermfx-find-regex");
        regexCheckBox.getStyleClass().add("jeditermfx-find-option");
        regexCheckBox.setText(settingsProvider.getFindRegexText());

        Button previous = actionButton("jeditermfx-find-previous", settingsProvider.getFindPreviousText());
        Button next = actionButton("jeditermfx-find-next", settingsProvider.getFindNextText());
        Button close = new Button("×");
        close.setId("jeditermfx-find-close");
        close.getStyleClass().add("jeditermfx-find-close");
        close.setOnAction(event -> jediTermWidget.hideFindComponent());
        previous.setOnAction(event -> {
            rememberCurrentSearch();
            onResultUpdated(jediTermWidget.getTerminalPanel().selectPrevFindResultItem());
        });
        next.setOnAction(event -> {
            rememberCurrentSearch();
            onResultUpdated(jediTermWidget.getTerminalPanel().selectNextFindResultItem());
        });

        Label title = new Label(settingsProvider.getFindActionPresentation().getName());
        title.getStyleClass().add("jeditermfx-find-title");
        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox titleRow = new HBox(8, title, titleSpacer, close);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label fieldLabel = new Label(settingsProvider.getFindActionPresentation().getName() + ":");
        fieldLabel.getStyleClass().add("jeditermfx-find-label");
        fieldLabel.setMinWidth(62);
        fieldLabel.setPrefWidth(62);
        HBox.setHgrow(find, Priority.ALWAYS);
        HBox findRow = new HBox(10, fieldLabel, find);
        findRow.setAlignment(Pos.CENTER_LEFT);

        HBox buttonRow = new HBox(8, previous, next);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        buttonRow.setPadding(new Insets(0, 0, 0, 72));
        HBox optionRow = new HBox(16, regexCheckBox, matchCaseCheckBox, label);
        optionRow.setAlignment(Pos.CENTER_LEFT);
        optionRow.setPadding(new Insets(0, 0, 0, 72));

        pane.getChildren().setAll(titleRow, findRow, buttonRow, optionRow);
        pane.setPadding(new Insets(12, 14, 12, 14));
        pane.setMinWidth(330);
        pane.setPrefWidth(397);
        pane.setMaxWidth(463);
        pane.setMinHeight(Region.USE_PREF_SIZE);
        pane.setMaxHeight(Region.USE_PREF_SIZE);
        pane.focusedProperty().addListener((ignored, before, focused) -> {
            if (focused) Platform.runLater(this::focusEditor);
        });
        applyTerminalPalette();
        updateLabel(null);
    }

    private void installInputContextMenu() {
        inputContextMenu.setOnShowing(ignored -> rebuildInputContextMenu());
        rebuildInputContextMenu();
        find.getEditor().setContextMenu(inputContextMenu);
    }

    private void rebuildInputContextMenu() {
        TextField input = find.getEditor();
        boolean hasSelection = input.getSelection().getLength() > 0;
        boolean hasText = !input.getText().isEmpty();
        boolean allSelected = hasText && input.getSelection().getLength() == input.getLength();
        MenuItem undo = contextItem(settingsProvider.getTextInputUndoText(), input::undo);
        MenuItem redo = contextItem(settingsProvider.getTextInputRedoText(), input::redo);
        MenuItem cut = contextItem(settingsProvider.getTextInputCutText(), input::cut);
        MenuItem copy = contextItem(settingsProvider.getTextInputCopyText(), input::copy);
        MenuItem paste = contextItem(settingsProvider.getTextInputPasteText(), input::paste);
        MenuItem delete = contextItem(settingsProvider.getTextInputDeleteText(), () -> input.replaceSelection(""));
        MenuItem selectAll = contextItem(settingsProvider.getTextInputSelectAllText(), input::selectAll);
        undo.setDisable(!input.isUndoable());
        redo.setDisable(!input.isRedoable());
        cut.setDisable(!hasSelection);
        copy.setDisable(!hasSelection);
        paste.setDisable(!Clipboard.getSystemClipboard().hasString());
        delete.setDisable(!hasSelection);
        selectAll.setDisable(!hasText || allSelected);
        inputContextMenu.getItems().setAll(undo, redo, cut, copy, paste, delete,
                new SeparatorMenuItem(), selectAll);
    }

    private static MenuItem contextItem(String label, Runnable action) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(ignored -> action.run());
        return item;
    }

    private static Button actionButton(String id, String text) {
        Button button = new Button(text);
        button.setId(id);
        button.getStyleClass().add("jeditermfx-find-action");
        return button;
    }

    void applyTerminalPalette() {
        ColorPalette palette = settingsProvider.getTerminalColorPalette();
        Color background = palette.getBackground(settingsProvider.getDefaultBackground());
        historyStyleClass = historyPopupStyleClass(background);
        FindChrome chrome = historyStyleClass.endsWith("-light")
                ? FindChrome.LIGHT : FindChrome.DARK;
        paletteStyle = String.format(Locale.ROOT,
                "-jeditermfx-find-panel: %s; -jeditermfx-find-control: %s; "
                        + "-jeditermfx-find-text: %s; -jeditermfx-find-muted: %s; "
                        + "-jeditermfx-find-border: %s; -jeditermfx-find-accent: %s; "
                        + "-jeditermfx-find-accent-text: %s; -jeditermfx-find-hover: %s; "
                        + "-jeditermfx-find-pressed: %s;",
                chrome.panel(), chrome.control(), chrome.text(), chrome.muted(), chrome.border(),
                chrome.accent(), chrome.accentText(), chrome.hover(), chrome.pressed());
        pane.setStyle(paletteStyle);
        find.setStyle(paletteStyle);
        inputContextMenu.getStyleClass().removeAll(inputContextStyleClasses);
        inputContextStyleClasses = List.copyOf(settingsProvider.getContextMenuStyleClasses());
        jediTermWidget.getTerminalPanel().configureContextMenu(inputContextMenu);
        applyPopupPalette();
    }

    private void applyPopupPalette() {
        ListView<String> popup = find.popupList();
        if (popup == null) return;
        if (!popup.getStylesheets().contains(FIND_STYLESHEET)) {
            popup.getStylesheets().add(FIND_STYLESHEET);
        }
        if (popup.getScene() != null) {
            popup.getScene().getStylesheets().remove(FIND_STYLESHEET);
            popup.getScene().getStylesheets().add(FIND_STYLESHEET);
        }
        if (!popup.getStyleClass().contains("jeditermfx-find-history")) {
            popup.getStyleClass().add("jeditermfx-find-history");
        }
        popup.getStyleClass().removeAll(
                "jeditermfx-find-history-light", "jeditermfx-find-history-dark");
        popup.getStyleClass().add(historyStyleClass);
        popup.setStyle("");
        popup.applyCss();
    }

    static String historyPopupStyleClass(Color color) {
        double red = linearRgb(color.getRed());
        double green = linearRgb(color.getGreen());
        double blue = linearRgb(color.getBlue());
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue > 0.45
                ? "jeditermfx-find-history-light" : "jeditermfx-find-history-dark";
    }

    private static double linearRgb(int component) {
        double value = component / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    TextField getTextField() {
        return find.getEditor();
    }

    ComboBox<String> getFindControl() {
        return find;
    }

    CheckBox getMatchCaseCheckBox() {
        return matchCaseCheckBox;
    }

    CheckBox getRegexCheckBox() {
        return regexCheckBox;
    }

    private void updateLabel(@Nullable FindResult result) {
        if (result == null) {
            label.setText("");
        } else if (result.getItems().isEmpty()) {
            label.setText(settingsProvider.getFindResultText(0, 0));
        } else {
            FindResult.FindItem selectedItem = result.selectedItem();
            label.setText(settingsProvider.getFindResultText(
                    selectedItem.getIndex(), result.getItems().size()));
        }
    }

    void showInvalidRegex() {
        label.setText(settingsProvider.getFindInvalidRegexText());
    }

    void onResultUpdated(@Nullable FindResult results) {
        updateLabel(results);
    }

    @NotNull Pane getPane() {
        return pane;
    }

    void requestFocus() {
        focusEditor();
        Platform.runLater(() -> {
            focusEditor();
            Platform.runLater(this::focusEditor);
        });
    }

    private void focusEditor() {
        TextField editor = find.getEditor();
        editor.requestFocus();
        editor.positionCaret(editor.getLength());
    }

    void rememberCurrentSearch() {
        String value = find.getEditor().getText();
        if (value == null || value.isBlank()) return;
        ObservableList<String> history = find.getItems();
        history.remove(value);
        history.addFirst(value);
        while (history.size() > MAX_HISTORY) history.removeLast();
        find.getSelectionModel().clearSelection();
        find.setValue(null);
        find.getEditor().setText(value);
    }

    private static final class HistoryComboBox extends ComboBox<String> {
        private ListView<String> popupList;

        @Override
        protected Skin<?> createDefaultSkin() {
            return new ComboBoxListViewSkin<>(this) {
                @Override
                public Node getPopupContent() {
                    Node content = super.getPopupContent();
                    if (content instanceof ListView<?> list) {
                        @SuppressWarnings("unchecked")
                        ListView<String> typed = (ListView<String>) list;
                        popupList = typed;
                    }
                    return content;
                }
            };
        }

        private ListView<String> popupList() {
            return popupList;
        }
    }

    private record FindChrome(
            String panel,
            String control,
            String text,
            String muted,
            String border,
            String accent,
            String accentText,
            String hover,
            String pressed) {
        private static final FindChrome DARK = new FindChrome(
                "#1b2028", "#202630", "#dde3ea", "#98a3b3", "#424e5f",
                "#526da5", "#ffffff", "#293241", "#31558a");
        private static final FindChrome LIGHT = new FindChrome(
                "#eceff1", "#ffffff", "#263238", "#607d8b", "#cfd8dc",
                "#1565c0", "#ffffff", "#dbe8fb", "#c5dbf7");
    }
}
