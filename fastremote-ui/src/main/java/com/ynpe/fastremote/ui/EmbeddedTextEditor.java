// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.SftpSettings;
import com.ynpe.fastremote.domain.settings.EditorTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.scene.text.Font;
import javafx.util.Duration;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

/** A bounded-node text editor surface with a floating in-editor find and replace panel. */
final class EmbeddedTextEditor extends BorderPane {
    private static final int MAX_HISTORY = 15;

    private final CodeArea area = new CodeArea();
    private final EditorNewlineBinding newlines = new EditorNewlineBinding(area);
    private final VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(
            area, ScrollPane.ScrollBarPolicy.AS_NEEDED, ScrollPane.ScrollBarPolicy.ALWAYS);
    private final ComboBox<String> find = new ComboBox<>();
    private final ComboBox<String> replace = new ComboBox<>();
    private final CheckBox matchCase = new CheckBox(text("editor.find.matchCase"));
    private final CheckBox regex = new CheckBox(text("editor.find.regex"));
    private final Label result = new Label();
    private final VBox searchPanel = new VBox(10.0);
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(140));
    private final List<Match> matches = new ArrayList<>();
    private final List<ListView<String>> popupLists = new ArrayList<>();
    private final List<ContextMenu> searchInputContextMenus = new ArrayList<>();
    private final SyntaxHighlighting.Binding highlighting;
    private ContextMenu contextMenu;
    private Runnable contextMenuRefresh = () -> { };
    private EditorTheme editorTheme = EditorTheme.DARK;
    private int currentMatch = -1;

    EmbeddedTextEditor(SftpSettings settings, String fileName) {
        getStyleClass().add("embedded-text-editor");
        area.getStyleClass().add("embedded-text-editor-area");
        area.setWrapText(true);
        find.setEditable(true);
        find.setPromptText(text("editor.find.placeholder"));
        find.setItems(FXCollections.observableArrayList());
        replace.setEditable(true);
        replace.setPromptText(text("editor.replace.placeholder"));
        replace.setItems(FXCollections.observableArrayList());
        installPopupTheme(find);
        installPopupTheme(replace);
        installSearchInputContextMenu(find);
        installSearchInputContextMenu(replace);

        buildSearchPanel();
        scrollPane.getStyleClass().add("embedded-editor-scroll");
        StackPane editorLayer = new StackPane(scrollPane, searchPanel);
        editorLayer.getStyleClass().add("embedded-editor-layer");
        StackPane.setAlignment(searchPanel, Pos.TOP_CENTER);
        StackPane.setMargin(searchPanel, new Insets(10.0, 12.0, 0.0, 12.0));
        setCenter(editorLayer);
        applySettings(settings);
        highlighting = SyntaxHighlighting.install(area, fileName);
        installContextMenu();

        searchDebounce.setOnFinished(event -> refreshMatches(true));
        find.getEditor().textProperty().addListener((ignored, before, after) -> scheduleSearch());
        find.valueProperty().addListener((ignored, before, selected) -> {
            if (selected != null && !selected.equals(find.getEditor().getText())) {
                find.getEditor().setText(selected);
            }
        });
        replace.valueProperty().addListener((ignored, before, selected) -> {
            if (selected != null && !selected.equals(replace.getEditor().getText())) {
                replace.getEditor().setText(selected);
            }
        });
        matchCase.selectedProperty().addListener((ignored, before, after) -> scheduleSearch());
        regex.selectedProperty().addListener((ignored, before, after) -> scheduleSearch());
        area.textProperty().addListener((ignored, before, after) -> {
            if (searchPanel.isVisible()) scheduleSearch();
        });
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && searchPanel.isVisible()) {
                hideSearch();
                event.consume();
            }
        });
    }

    private void buildSearchPanel() {
        Button previous = new Button(text("editor.find.previous"));
        Button next = new Button(text("editor.find.next"));
        Button replaceOne = new Button(text("editor.replace.one"));
        Button replaceAll = new Button(text("editor.replace.all"));
        Button close = new Button("×");
        previous.setOnAction(event -> navigate(false));
        next.setOnAction(event -> navigate(true));
        replaceOne.setOnAction(event -> replaceCurrent());
        replaceAll.setOnAction(event -> replaceAll());
        close.setOnAction(event -> hideSearch());
        close.setAccessibleText(text("action.closeFile"));
        close.getStyleClass().add("embedded-editor-search-close");
        previous.getStyleClass().add("embedded-editor-search-action");
        next.getStyleClass().add("embedded-editor-search-action");
        replaceOne.getStyleClass().add("embedded-editor-search-action");
        replaceAll.getStyleClass().add("embedded-editor-search-action");
        previous.setId("embedded-editor-find-previous");
        next.setId("embedded-editor-find-next");
        replaceOne.setId("embedded-editor-replace-one");
        replaceAll.setId("embedded-editor-replace-all");

        Label title = new Label(text("action.find") + " / " + text("action.replace"));
        title.getStyleClass().add("embedded-editor-search-title");
        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox titleRow = new HBox(8.0, title, titleSpacer, close);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        HBox findRow = inputRow(text("action.find"), find);
        HBox replaceRow = inputRow(text("action.replace"), replace);
        HBox buttonRow = new HBox(6.0, previous, next, replaceOne, replaceAll);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        buttonRow.setPadding(new Insets(0.0, 0.0, 0.0, 72.0));
        HBox optionRow = new HBox(16.0, regex, matchCase, result);
        optionRow.setAlignment(Pos.CENTER_LEFT);
        optionRow.setPadding(new Insets(0.0, 0.0, 0.0, 72.0));

        searchPanel.getChildren().setAll(titleRow, findRow, replaceRow, buttonRow, optionRow);
        searchPanel.getStyleClass().add("embedded-editor-search");
        searchPanel.setMinWidth(380.0);
        searchPanel.setPrefWidth(447.0);
        searchPanel.setMaxWidth(513.0);
        searchPanel.setMinHeight(Region.USE_PREF_SIZE);
        searchPanel.setMaxHeight(Region.USE_PREF_SIZE);
        searchPanel.setVisible(false);
        searchPanel.setManaged(false);

        searchPanel.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && find.getEditor().isFocused()) {
                navigate(!event.isShiftDown());
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER && replace.getEditor().isFocused()) {
                replaceCurrent();
                event.consume();
            }
        });
    }

    private static HBox inputRow(String labelText, ComboBox<String> input) {
        Label label = new Label(labelText + ":");
        label.getStyleClass().add("embedded-editor-search-label");
        label.setMinWidth(62.0);
        label.setPrefWidth(62.0);
        input.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(input, Priority.ALWAYS);
        HBox row = new HBox(10.0, label, input);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    void setSearchHistories(ObservableList<String> findHistory, ObservableList<String> replaceHistory) {
        find.setItems(findHistory);
        replace.setItems(replaceHistory);
    }

    private void installContextMenu() {
        MenuItem cut = item("action.cut", area::cut);
        MenuItem copy = item("action.copy", area::copy);
        MenuItem paste = item("action.paste", area::paste);
        MenuItem findItem = item("action.find", this::showFind);
        MenuItem replaceItem = item("action.replace", this::showReplace);
        MenuItem selectAll = item("editor.selectAll", area::selectAll);
        cut.setAccelerator(KeyCombination.valueOf("Shortcut+X"));
        copy.setAccelerator(KeyCombination.valueOf("Shortcut+C"));
        paste.setAccelerator(KeyCombination.valueOf("Shortcut+V"));
        findItem.setAccelerator(KeyCombination.valueOf("Shortcut+F"));
        replaceItem.setAccelerator(KeyCombination.valueOf("Shortcut+R"));
        selectAll.setAccelerator(KeyCombination.valueOf("Shortcut+A"));
        ContextMenu menu = ApplicationContextMenus.create(cut, copy, paste, new SeparatorMenuItem(),
                findItem, replaceItem, new SeparatorMenuItem(), selectAll);
        menu.getStyleClass().add("embedded-editor-context-menu");
        ApplicationContextMenus.prepareBeforeShow(menu, () -> {
            cut.setDisable(area.isDisabled() || area.getSelectedText().isEmpty());
            copy.setDisable(area.getSelectedText().isEmpty());
            paste.setDisable(area.isDisabled());
            contextMenuRefresh.run();
        });
        area.setContextMenu(menu);
        area.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            if (!LocalizedTextInputContextMenus.isLinux()) return;
            if (!ApplicationContextMenus.isShowing(menu)) {
                menu.show(area, event.getScreenX(), event.getScreenY());
            }
            event.consume();
        });
        area.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (!LocalizedTextInputContextMenus.isLinux()
                    || event.getButton() != MouseButton.SECONDARY) return;
            double screenX = event.getScreenX();
            double screenY = event.getScreenY();
            Platform.runLater(() -> {
                if (!ApplicationContextMenus.isShowing(menu) && area.getScene() != null) {
                    menu.show(area, screenX, screenY);
                }
            });
        });
        contextMenu = menu;
        applyPopupThemeClasses();
    }

    void appendContextMenuItems(List<MenuItem> items, Runnable refresh) {
        if (items == null || items.isEmpty()) return;
        contextMenu.getItems().add(new SeparatorMenuItem());
        contextMenu.getItems().addAll(items);
        contextMenuRefresh = refresh == null ? () -> { } : refresh;
    }

    private void installPopupTheme(ComboBox<String> input) {
        input.getStyleClass().add("embedded-editor-search-combo");
        input.setCellFactory(list -> {
            if (!popupLists.contains(list)) popupLists.add(list);
            applyPopupListTheme(list);
            return new ListCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
        });
        input.setOnShowing(event -> Platform.runLater(() -> {
            javafx.scene.Node popupList = input.lookup(".list-view");
            if (popupList instanceof ListView<?> list) applyPopupListTheme(list);
        }));
    }

    private void installSearchInputContextMenu(ComboBox<String> input) {
        ContextMenu menu = LocalizedTextInputContextMenus.createMenu(
                input.getEditor(), com.ynpe.fastremote.i18n.Texts.localizer());
        menu.getStyleClass().add("embedded-editor-context-menu");
        input.getEditor().setContextMenu(menu);
        searchInputContextMenus.add(menu);
    }

    private void applyPopupListTheme(javafx.scene.control.Control popupList) {
        popupList.getStyleClass().removeAll("editor-search-popup-dark", "editor-search-popup-light");
        popupList.getStyleClass().add(editorTheme == EditorTheme.DARK
                ? "editor-search-popup-dark" : "editor-search-popup-light");
    }

    private static MenuItem item(String key, Runnable action) {
        MenuItem item = new MenuItem(text(key));
        item.setOnAction(event -> action.run());
        return item;
    }

    void applySettings(SftpSettings settings) {
        String family = settings.editorFontFamily().replace("'", "\\'");
        area.setStyle("-fx-font-family: '" + family + "'; -fx-font-size: " + settings.editorFontSize() + "px;");
        area.setTextSpacing(settings.editorTextAppearance().lineSpacing(), settings.editorTextAppearance().letterSpacing());
        applyLineNumberFont(settings);
        editorTheme = settings.editorTheme();
        getStyleClass().removeAll("editor-theme-dark", "editor-theme-light");
        getStyleClass().add(editorTheme == EditorTheme.DARK
                ? "editor-theme-dark" : "editor-theme-light");
        applyPopupThemeClasses();
    }

    private void applyLineNumberFont(SftpSettings settings) {
        String configured = settings.editorFontFamily();
        String family = lineNumberFontFamily(configured, Font.getFontNames(configured));
        String escaped = family.replace("'", "\\'");
        var factory = LineNumberFactory.get(area);
        area.setParagraphGraphicFactory(index -> {
            Node graphic = factory.apply(index);
            if (graphic instanceof Label label) {
                label.setStyle("-fx-font-family: '" + escaped + "'; -fx-font-size: "
                        + settings.editorFontSize() + "px; -fx-font-style: italic; -fx-padding: "
                        + Math.max(0, settings.editorTextAppearance().lineSpacing() / 2.0) + "px 8px 0 6px;");
                label.setTranslateY(Math.min(0, settings.editorTextAppearance().lineSpacing() / 2.0));
            }
            return graphic;
        });
    }

    static String lineNumberFontFamily(String configuredFamily, List<String> availableFaces) {
        boolean hasItalicFace = availableFaces.stream()
                .map(face -> face.toLowerCase(Locale.ROOT))
                .anyMatch(face -> face.contains("italic") || face.contains("oblique"));
        return hasItalicFace ? configuredFamily
                : InterfaceFontPolicy.interfaceFontFamily(
                        com.ynpe.fastremote.i18n.Texts.localizer().resolvedLanguage().persistedTag());
    }

    private void applyPopupThemeClasses() {
        String comboTheme = editorTheme == EditorTheme.DARK
                ? "editor-search-combo-dark" : "editor-search-combo-light";
        for (ComboBox<String> input : List.of(find, replace)) {
            input.getStyleClass().removeAll("editor-search-combo-dark", "editor-search-combo-light");
            input.getStyleClass().add(comboTheme);
        }
        popupLists.forEach(this::applyPopupListTheme);
        for (ContextMenu menu : searchInputContextMenus) {
            menu.getStyleClass().removeAll("editor-context-dark", "editor-context-light");
            menu.getStyleClass().add(editorTheme == EditorTheme.DARK
                    ? "editor-context-dark" : "editor-context-light");
        }
        if (contextMenu != null) {
            contextMenu.getStyleClass().removeAll("editor-context-dark", "editor-context-light");
            contextMenu.getStyleClass().add(editorTheme == EditorTheme.DARK
                    ? "editor-context-dark" : "editor-context-light");
        }
    }

    ObservableValue<Number> textProperty() { return newlines.revision.getReadOnlyProperty(); }
    EditorFileContent content() { return newlines.content(); }
    String text() { return area.getText(); }
    void setText(String value) { newlines.load(value == null ? "" : value); }
    void dispose() {
        searchDebounce.stop();
        newlines.close();
        area.dispose();
    }
    void cut() { area.cut(); }
    void copy() { area.copy(); }
    void paste() { area.paste(); }
    void insertText(String value) { area.replaceSelection(value == null ? "" : value); }
    void requestEditorFocus() { area.requestFocus(); }
    void setEditingDisabled(boolean value) { area.setEditable(!value); searchPanel.setDisable(value); }
    void setWrapText(boolean value) {
        area.setWrapText(value);
        area.requestLayout();
        requestLayout();
    }
    boolean isWrapText() { return area.isWrapText(); }
    String syntaxLanguage() { return highlighting.language(); }
    void setSyntaxLanguage(String language) { highlighting.setLanguage(language); }
    void refreshSyntaxHighlighting() { highlighting.refresh(); }

    void showFind() { showSearch(); }
    void showReplace() { showSearch(); }

    private void showSearch() {
        searchPanel.setManaged(true);
        searchPanel.setVisible(true);
        refreshMatches(false);
        find.requestFocus();
        find.getEditor().selectAll();
    }

    private void hideSearch() {
        searchDebounce.stop();
        searchPanel.setVisible(false);
        searchPanel.setManaged(false);
        matches.clear();
        currentMatch = -1;
        result.setText("");
        area.requestFocus();
    }

    private void scheduleSearch() {
        if (searchPanel.isVisible()) searchDebounce.playFromStart();
    }

    private Pattern searchPattern() {
        String query = find.getEditor().getText();
        if (query == null || query.isEmpty()) return null;
        int flags = matchCase.isSelected() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(regex.isSelected() ? query : Pattern.quote(query), flags);
    }

    private void refreshMatches(boolean selectNearest) {
        matches.clear();
        currentMatch = -1;
        try {
            Pattern pattern = searchPattern();
            if (pattern == null) { result.setText(""); return; }
            Matcher matcher = pattern.matcher(area.getText());
            while (matcher.find()) matches.add(new Match(matcher.start(), matcher.end()));
            if (matches.isEmpty()) { result.setText(text("editor.find.noResults")); return; }
            int selectionStart = area.getSelection().getStart();
            int selectionEnd = area.getSelection().getEnd();
            for (int i = 0; i < matches.size(); i++) {
                Match match = matches.get(i);
                if (match.start() == selectionStart && match.end() == selectionEnd) {
                    currentMatch = i;
                    break;
                }
            }
            if (currentMatch < 0) currentMatch = nearestForward(area.getCaretPosition());
            if (selectNearest) selectCurrent();
            updateResult();
        } catch (IllegalArgumentException invalid) {
            result.setText(text("editor.find.invalidRegex"));
        }
    }

    private int nearestForward(int caret) {
        for (int i = 0; i < matches.size(); i++) if (matches.get(i).start() >= caret) return i;
        return 0;
    }

    private void navigate(boolean forward) {
        rememberHistory(find);
        refreshMatches(false);
        if (matches.isEmpty()) return;
        int selected = selectedMatchIndex();
        if (selected >= 0) {
            currentMatch = Math.floorMod(selected + (forward ? 1 : -1), matches.size());
        } else if (forward) {
            currentMatch = nearestForward(area.getCaretPosition());
        } else {
            currentMatch = previousBefore(area.getCaretPosition());
        }
        selectCurrent();
        updateResult();
    }

    private int selectedMatchIndex() {
        int start = area.getSelection().getStart();
        int end = area.getSelection().getEnd();
        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);
            if (match.start() == start && match.end() == end) return i;
        }
        return -1;
    }

    private int previousBefore(int caret) {
        for (int i = matches.size() - 1; i >= 0; i--) if (matches.get(i).end() <= caret) return i;
        return matches.size() - 1;
    }

    private void selectCurrent() {
        if (currentMatch < 0 || currentMatch >= matches.size()) return;
        Match match = matches.get(currentMatch);
        area.selectRange(match.start(), match.end());
        area.requestFollowCaret();
    }

    private void updateResult() {
        if (matches.isEmpty()) result.setText(text("editor.find.noResults"));
        else result.setText(String.format(Locale.ROOT, "%d / %d", currentMatch + 1, matches.size()));
    }

    private void replaceCurrent() {
        rememberHistory(find);
        rememberHistory(replace);
        refreshMatches(false);
        if (matches.isEmpty()) return;
        int index = selectedMatchIndex();
        if (index < 0) index = currentMatch;
        Match match = matches.get(index);
        String replacement = replace.getEditor().getText();
        replacement = replacement == null ? "" : replacement;
        try {
            if (regex.isSelected()) {
                Matcher selected = searchPattern().matcher(area.getText().substring(match.start(), match.end()));
                area.replaceText(match.start(), match.end(), selected.replaceFirst(replacement));
            } else {
                area.replaceText(match.start(), match.end(), replacement);
            }
            area.moveTo(match.start() + replacement.length());
            refreshMatches(true);
        } catch (IllegalArgumentException invalid) {
            result.setText(text("editor.find.invalidRegex"));
        }
    }

    private void replaceAll() {
        rememberHistory(find);
        rememberHistory(replace);
        try {
            Pattern pattern = searchPattern();
            if (pattern == null) return;
            Matcher counter = pattern.matcher(area.getText());
            int count = 0;
            while (counter.find()) count++;
            String replacement = replace.getEditor().getText();
            replacement = replacement == null ? "" : replacement;
            String replacementExpression = regex.isSelected() ? replacement : Matcher.quoteReplacement(replacement);
            area.replaceText(pattern.matcher(area.getText()).replaceAll(replacementExpression));
            matches.clear();
            currentMatch = -1;
            result.setText(String.format(Locale.ROOT, text("editor.replace.count"), count));
        } catch (IllegalArgumentException invalid) {
            result.setText(text("editor.find.invalidRegex"));
        }
    }

    private static void rememberHistory(ComboBox<String> input) {
        String value = input.getEditor().getText();
        ObservableList<String> history = input.getItems();
        if (value == null || value.isBlank()) return;
        history.remove(value);
        history.addFirst(value);
        while (history.size() > MAX_HISTORY) history.removeLast();
        input.getSelectionModel().clearSelection();
        input.setValue(null);
        input.getEditor().setText(value);
    }

    CodeArea editorArea() { return area; }
    VirtualizedScrollPane<CodeArea> editorScrollPane() { return scrollPane; }
    ComboBox<String> findControl() { return find; }
    ComboBox<String> replaceControl() { return replace; }
    ContextMenu contextMenu() { return contextMenu; }
    VBox searchPanel() { return searchPanel; }
    void findNextForTest() { navigate(true); }
    void findPreviousForTest() { navigate(false); }
    void replaceCurrentForTest() { replaceCurrent(); }
    void replaceAllForTest() { replaceAll(); }

    private static String text(String key) { return com.ynpe.fastremote.i18n.Texts.text(key); }
    private record Match(int start, int end) { }
}
