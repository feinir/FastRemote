// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.catalog.HostId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;

/** Host selector whose menu combines search and a bounded, virtualized result list. */
final class SearchableJumpHostSelector {
    private static final double MINIMUM_POPUP_WIDTH = 390.0;
    private static final PseudoClass POPUP_OPEN = PseudoClass.getPseudoClass("popup-open");

    private final MenuButton node;
    private final Popup popup = new Popup();
    private final TextField search = new TextField();
    private final ListView<Option> results = new ListView<>();
    private final VBox popupContent = new VBox();
    private List<Option> options = List.of(directOption());
    private Optional<HostId> selectedId = Optional.empty();
    private boolean opensUpward;
    private String emptyButtonText;
    private Runnable selectionChanged = () -> {};

    SearchableJumpHostSelector(
            MenuButton node,
            List<SavedHost> hosts,
            List<HostGroup> groups,
            Optional<HostId> selectedId,
            Optional<HostId> excludedId) {
        this.node = Objects.requireNonNull(node, "node");
        node.setMnemonicParsing(false);
        node.setMaxWidth(Double.MAX_VALUE);
        node.getStyleClass().add("jump-host-selector");
        node.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.fc0499b82ca748fc"));

        search.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4e064e9e4f987ec9"));
        search.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.54f76178dcc1c8ce"));
        results.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b7b50e936cd46598"));
        results.setPrefHeight(320.0);
        results.setFixedCellSize(48.0);
        results.setCellFactory(ignored -> optionCell());

        popupContent.setSpacing(3.0);
        popupContent.getChildren().setAll(search, results);
        popupContent.setPadding(new Insets(3.0));
        synchronizePopupWidth();
        node.widthProperty().addListener((ignored, previous, width) -> synchronizePopupWidth());
        popupContent.getStyleClass().add("jump-host-selector-popup");
        VBox.setVgrow(results, Priority.ALWAYS);
        popup.getContent().setAll(popupContent);
        popup.setAnchorLocation(PopupWindow.AnchorLocation.CONTENT_TOP_LEFT);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setAutoFix(false);
        SearchableSelectorPopupInteraction.install(popup, node);
        SearchableSelectorInputMethod.install(popup, node, search);
        popup.setOnShown(event -> {
            node.pseudoClassStateChanged(POPUP_OPEN, true);
            Platform.runLater(search::requestFocus);
        });
        popup.setOnHidden(event -> node.pseudoClassStateChanged(POPUP_OPEN, false));

        search.textProperty().addListener((ignored, previous, query) -> applyFilter(query));
        InputMethodEnterState searchEnterState = InputMethodEnterState.track(search);
        search.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                selectFirstResultIfNecessary();
                results.requestFocus();
                event.consume();
            } else if (event.getCode() == KeyCode.UP) {
                selectPreviousResultOrLast();
                results.requestFocus();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                event.consume();
            }
        });
        search.setOnKeyReleased(event -> {
            if (event.getCode() != KeyCode.ENTER) return;
            if (searchEnterState.inputMethodOwnsKeyRelease(event.getCode())) return;
            selectOption(selectedOrFirst());
            event.consume();
        });
        results.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                selectOption(results.getSelectionModel().getSelectedItem());
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                event.consume();
            }
        });
        SearchableSelectorMouseSupport.install(results, this::selectOption);
        node.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            boolean wasShowing = popup.isShowing();
            node.requestFocus();
            if (wasShowing) popup.hide(); else showPopup();
            event.consume();
        });
        node.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) event.consume();
        });
        node.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE
                    || event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.F4) {
                togglePopup();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && popup.isShowing()) {
                popup.hide();
                event.consume();
            }
        });
        node.setOnAction(event -> togglePopup());
        rebuild(hosts, groups, selectedId, excludedId);
    }

    SearchableJumpHostSelector(MenuButton node, List<Option> choices, Optional<HostId> selected, String emptyText) {
        this(node, List.of(), List.of(), Optional.empty(), Optional.empty());
        emptyButtonText = Objects.requireNonNull(emptyText, "emptyText");
        rebuildOptions(choices, selected);
    }

    void rebuildOptions(List<Option> choices, Optional<HostId> selected) {
        options = List.copyOf(choices);
        selectedId = selected;
        updateButtonText();
        if (!popup.isShowing()) resetPopup();
        else applyFilter(search.getText());
    }

    Optional<HostId> selectedHostId() {
        return selectedId;
    }

    void setOnSelectionChanged(Runnable action) {
        selectionChanged = Objects.requireNonNull(action, "action");
    }

    MenuButton node() {
        return node;
    }

    TextField searchField() {
        return search;
    }

    ListView<Option> results() {
        return results;
    }

    VBox popupContent() {
        return popupContent;
    }

    Popup popup() {
        return popup;
    }

    void rebuild(
            List<SavedHost> hosts,
            List<HostGroup> groups,
            Optional<HostId> preferredId,
            Optional<HostId> excludedId) {
        options = options(hosts, groups, excludedId);
        selectedId = options.stream().filter(option -> option.id().equals(preferredId))
                .map(Option::id).findFirst().orElse(Optional.empty());
        updateButtonText();
        resetPopup();
    }

    private void resetPopup() {
        search.clear();
        results.getItems().setAll(optionsForDirection(options, opensUpward));
        options.stream().filter(option -> option.id().equals(selectedId)).findFirst()
                .ifPresent(results.getSelectionModel()::select);
        Platform.runLater(() -> options.stream().filter(option -> option.id().equals(selectedId)).findFirst()
                .ifPresent(results::scrollTo));
    }

    private void synchronizePopupWidth() {
        double width = Math.max(MINIMUM_POPUP_WIDTH, node.getWidth());
        popupContent.setMinWidth(width);
        popupContent.setPrefWidth(width);
        popupContent.setMaxWidth(width);
        popupContent.requestLayout();
    }

    private void togglePopup() {
        if (popup.isShowing()) {
            popup.hide();
        } else {
            showPopup();
        }
    }

    private void showPopup() {
        Bounds screenBounds = node.localToScreen(node.getBoundsInLocal());
        if (screenBounds == null || node.getScene() == null || node.getScene().getWindow() == null) return;
        synchronizePopupWidth();
        SearchableSelectorPopupStyles.copyFrom(node.getScene(), popupContent);
        opensUpward = SearchableSelectorPopupLayout.opensUpward(
                screenBounds, SearchableSelectorPopupLayout.preferredHeight(popupContent));
        SearchableSelectorPopupLayout.arrange(popupContent, search, results, opensUpward);
        resetPopup();
        double anchorY = opensUpward
                ? screenBounds.getMinY() - SearchableSelectorPopupLayout.preferredHeight(popupContent)
                : screenBounds.getMaxY();
        popup.show(node, screenBounds.getMinX(), anchorY);
    }

    private void applyFilter(String query) {
        List<Option> filtered = filter(options, query);
        results.getItems().setAll(optionsForDirection(filtered, opensUpward));
        filtered.stream().filter(option -> option.id().equals(selectedId)).findFirst()
                .ifPresentOrElse(results.getSelectionModel()::select,
                        () -> results.getSelectionModel().clearSelection());
    }

    private void selectFirstResultIfNecessary() {
        if (results.getSelectionModel().getSelectedItem() == null && !results.getItems().isEmpty()) {
            results.getSelectionModel().selectFirst();
        }
    }

    private void selectPreviousResultOrLast() {
        if (results.getItems().isEmpty()) return;
        if (results.getSelectionModel().getSelectedItem() == null) results.getSelectionModel().selectLast();
        else results.getSelectionModel().selectPrevious();
    }

    private Option selectedOrFirst() {
        Option selected = results.getSelectionModel().getSelectedItem();
        return selected != null ? selected : results.getItems().isEmpty() ? null : results.getItems().getFirst();
    }

    private void selectOption(Option option) {
        if (option == null || !options.contains(option)) return;
        Optional<HostId> previous = selectedId;
        selectedId = option.id();
        updateButtonText();
        popup.hide();
        if (!selectedId.equals(previous)) selectionChanged.run();
    }

    private void updateButtonText() {
        node.setText(options.stream().filter(option -> option.id().equals(selectedId)).findFirst()
                .map(Option::buttonText).orElseGet(() -> emptyButtonText == null ? directOption().buttonText() : emptyButtonText));
    }

    private static ListCell<Option> optionCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Option option, boolean empty) {
                super.updateItem(option, empty);
                if (empty || option == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label primary = new Label(option.primaryText());
                primary.getStyleClass().add("jump-host-option-name");
                if (option.secondaryText().isBlank()) {
                    setGraphic(primary);
                } else {
                    Label secondary = new Label(option.secondaryText());
                    secondary.getStyleClass().add("jump-host-option-detail");
                    setGraphic(new VBox(1.0, primary, secondary));
                }
                setText(null);
            }
        };
    }

    static List<Option> options(
            List<SavedHost> hosts, List<HostGroup> groups, Optional<HostId> excludedId) {
        Objects.requireNonNull(hosts, "hosts");
        Objects.requireNonNull(groups, "groups");
        Objects.requireNonNull(excludedId, "excludedId");
        Map<GroupId, HostGroup> groupsById = new HashMap<>();
        groups.forEach(group -> groupsById.put(group.id(), group));
        List<Option> result = new ArrayList<>();
        result.add(directOption());
        hosts.stream()
                .filter(host -> !excludedId.equals(Optional.of(host.id())))
                .sorted(Comparator.comparing(SavedHost::name, String.CASE_INSENSITIVE_ORDER))
                .map(host -> option(host, groupsById))
                .forEach(result::add);
        return List.copyOf(result);
    }

    private static Option directOption() {
        return new Option(Optional.empty(),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.4e382714f946dc6e"), "", "", "");
    }

    static List<Option> filter(List<Option> options, String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return List.copyOf(options);
        List<Option> filtered = new ArrayList<>();
        options.stream().filter(option -> option.id().isEmpty()).findFirst().ifPresent(filtered::add);
        options.stream().filter(option -> option.id().isPresent() && option.matches(needle))
                .forEach(filtered::add);
        return List.copyOf(filtered);
    }

    static List<Option> optionsForDirection(List<Option> options, boolean opensUpward) {
        if (!opensUpward) return List.copyOf(options);
        List<Option> ordered = new ArrayList<>(options.size());
        options.stream().filter(option -> option.id().isPresent()).forEach(ordered::add);
        options.stream().filter(option -> option.id().isEmpty()).forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private static Option option(SavedHost savedHost, Map<GroupId, HostGroup> groups) {
        String group = savedHost.groupId().map(id -> groupPath(id, groups)).orElse(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e86ea3f280d920a2"));
        String address = savedHost.profile().host().value();
        String endpoint = savedHost.profile().username().value() + "@" + address + ':' + savedHost.profile().port().value();
        return new Option(Optional.of(savedHost.id()), com.ynpe.fastremote.i18n.Texts.text("legacy.java.b4ca6ca05fb3404b") + savedHost.name(), savedHost.name(), address, group,
                group + " · " + endpoint);
    }

    private static String groupPath(GroupId id, Map<GroupId, HostGroup> groups) {
        HostGroup group = groups.get(id);
        if (group == null) return com.ynpe.fastremote.i18n.Texts.text("legacy.java.e86ea3f280d920a2");
        return group.parentId().map(groups::get)
                .map(parent -> parent.name() + " / " + group.name())
                .orElse(group.name());
    }

    record Option(
            Optional<HostId> id,
            String buttonText,
            String hostName,
            String host,
            String group,
            String secondaryText) {
        private Option(Optional<HostId> id, String buttonText, String hostName, String host, String group) {
            this(id, buttonText, hostName, host, group, "");
        }

        Option {
            id = Objects.requireNonNull(id, "id");
            buttonText = Objects.requireNonNull(buttonText, "buttonText");
            hostName = Objects.requireNonNull(hostName, "hostName");
            host = Objects.requireNonNull(host, "host");
            group = Objects.requireNonNull(group, "group");
            secondaryText = Objects.requireNonNull(secondaryText, "secondaryText");
        }

        String primaryText() {
            return buttonText;
        }

        boolean matches(String needle) {
            if (id.isEmpty()) return com.ynpe.fastremote.i18n.Texts.text("legacy.java.4e382714f946dc6e").contains(needle);
            return hostName.toLowerCase(Locale.ROOT).contains(needle)
                    || host.toLowerCase(Locale.ROOT).contains(needle)
                    || group.toLowerCase(Locale.ROOT).contains(needle);
        }
    }
}
