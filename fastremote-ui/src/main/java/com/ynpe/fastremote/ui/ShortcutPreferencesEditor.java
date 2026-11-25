// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.KeyboardShortcutSettings;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Editor for one shortcut scheme; it deliberately has no profile or import/export surface. */
final class ShortcutPreferencesEditor {
    private final VBox node = new VBox(10.0);
    private final TextField search = new TextField();
    private final ListView<ShortcutAction> actions = new ListView<>();
    private final Label validation = new Label();
    private final EnumMap<ShortcutAction, String> values = new EnumMap<>(ShortcutAction.class);
    private final ObjectProperty<KeyboardShortcutSettings> settings =
            new SimpleObjectProperty<>(KeyboardShortcutSettings.defaults());

    ShortcutPreferencesEditor(KeyboardShortcutSettings initial) {
        SingleLineInputEnterSupport.consume(search);
        for (ShortcutAction action : ShortcutAction.values()) {
            values.put(action, initial.overrides().getOrDefault(action.id(), action.defaultShortcut()));
        }
        search.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.21f743960a6b78a3"));
        search.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.49504fdfc1b5a604"));
        actions.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3622113ed17030ab"));
        actions.setPrefHeight(350.0);
        actions.setMinHeight(120.0);
        actions.setMaxHeight(Double.MAX_VALUE);
        actions.setCellFactory(ignored -> new ShortcutCell());
        search.textProperty().addListener((ignored, previous, current) -> refresh(current));

        Button resetSelected = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a540351ed04b81fb"));
        resetSelected.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        resetSelected.setDisable(true);
        actions.getSelectionModel().selectedItemProperty().addListener(
                (ignored, previous, selected) -> resetSelected.setDisable(selected == null));
        resetSelected.setOnAction(event -> {
            ShortcutAction selected = actions.getSelectionModel().getSelectedItem();
            if (selected != null) {
                values.put(selected, selected.defaultShortcut());
                publish();
                actions.refresh();
            }
        });
        Button resetAll = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.80fba41dc57ad59e"));
        resetAll.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        resetAll.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.94f3ea26abd36f32"));
        resetAll.setOnAction(event -> resetAllToDefaults());
        HBox searchBar = new HBox(8.0, search, resetSelected, resetAll);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        search.setMinWidth(220.0);
        HBox.setHgrow(search, Priority.ALWAYS);

        validation.getStyleClass().add("shortcut-validation");
        validation.setWrapText(true);
        node.setMinHeight(0.0);
        node.getChildren().addAll(searchBar,
                SettingsPageLayout.policyInfo(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9091e0ce82f2e3db")),
                validation, actions);
        VBox.setVgrow(actions, Priority.ALWAYS);
        refresh("");
        publish();
    }

    Node node() { return node; }
    TextField searchField() { return search; }
    KeyboardShortcutSettings settings() { return settings.get(); }
    ObjectProperty<KeyboardShortcutSettings> settingsProperty() { return settings; }
    boolean valid() { return validation.getText().isEmpty(); }

    void resetAllToDefaults() {
        for (ShortcutAction action : ShortcutAction.values()) values.put(action, action.defaultShortcut());
        publish();
        refresh(search.getText());
        actions.refresh();
    }

    private void refresh(String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        actions.getItems().setAll(java.util.Arrays.stream(ShortcutAction.values())
                .filter(action -> needle.isEmpty()
                        || action.label().toLowerCase(Locale.ROOT).contains(needle)
                        || action.category().toLowerCase(Locale.ROOT).contains(needle)
                        || values.get(action).toLowerCase(Locale.ROOT).contains(needle))
                .toList());
    }

    private void publish() {
        LinkedHashMap<String, String> overrides = new LinkedHashMap<>();
        for (ShortcutAction action : ShortcutAction.values()) {
            String value = values.get(action);
            if (!action.defaultShortcut().equals(value)) overrides.put(action.id(), value);
        }
        KeyboardShortcutSettings current = new KeyboardShortcutSettings(overrides);
        settings.set(current);
        String conflict = KeyboardShortcutManager.conflict(current);
        validation.setText(conflict);
        validation.setManaged(!conflict.isEmpty());
        validation.setVisible(!conflict.isEmpty());
    }

    private final class ShortcutCell extends ListCell<ShortcutAction> {
        @Override protected void updateItem(ShortcutAction action, boolean empty) {
            super.updateItem(action, empty);
            if (empty || action == null) { setGraphic(null); setText(null); return; }
            Label category = new Label(action.category());
            category.getStyleClass().add("shortcut-category");
            Label label = new Label(action.label());
            VBox description = new VBox(2.0, label, category);
            Button shortcut = new Button(display(values.get(action)));
            shortcut.getStyleClass().add("shortcut-capture-button");
            shortcut.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.37090f45651676fc") + action.label() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.ee2638183d3ea860"));
            shortcut.setOnAction(event -> {
                shortcut.getProperties().put("fastremote.shortcut.capture", Boolean.TRUE);
                shortcut.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.faeda3a59eef925c"));
                shortcut.requestFocus();
            });
            shortcut.addEventFilter(KeyEvent.KEY_PRESSED, event -> capture(action, shortcut, event));
            HBox row = new HBox(12.0, description, shortcut);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(description, Priority.ALWAYS);
            setGraphic(row);
            setText(null);
        }
    }

    private void capture(ShortcutAction action, Button button, KeyEvent event) {
        if (!Boolean.TRUE.equals(button.getProperties().get("fastremote.shortcut.capture"))) return;
        event.consume();
        if (event.getCode() == KeyCode.ESCAPE) {
            button.getProperties().remove("fastremote.shortcut.capture");
            button.setText(display(values.get(action)));
            return;
        }
        if (event.getCode().isModifierKey()) return;
        if (!event.isShortcutDown() && !event.isControlDown() && !event.isAltDown()
                && !event.isMetaDown() && !event.isShiftDown()
                && event.getCode() != KeyCode.ENTER && event.getCode() != KeyCode.F2 && event.getCode() != KeyCode.F5
                && event.getCode() != KeyCode.DELETE) {
            return;
        }
        KeyCodeCombination combination = new KeyCodeCombination(event.getCode(),
                event.isShiftDown() ? KeyCombination.ModifierValue.DOWN : KeyCombination.ModifierValue.UP,
                event.isControlDown() ? KeyCombination.ModifierValue.DOWN : KeyCombination.ModifierValue.UP,
                event.isAltDown() ? KeyCombination.ModifierValue.DOWN : KeyCombination.ModifierValue.UP,
                event.isMetaDown() ? KeyCombination.ModifierValue.DOWN : KeyCombination.ModifierValue.UP,
                KeyCombination.ModifierValue.ANY);
        values.put(action, combination.getName());
        button.getProperties().remove("fastremote.shortcut.capture");
        button.setText(display(combination.getName()));
        publish();
    }

    private static String display(String value) {
        try {
            String display = KeyCombination.valueOf(value).getDisplayText();
            return display.isBlank() ? value : display;
        } catch (IllegalArgumentException invalid) {
            return value;
        }
    }
}
