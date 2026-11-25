// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.credential.CredentialGroup;
import com.ynpe.fastremote.domain.credential.CredentialGroupId;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import com.ynpe.fastremote.domain.vault.SecretId;
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

/** Credential selector with inline search and a virtualized result list. */
final class SearchableCredentialSelector {
    private static final double MINIMUM_POPUP_WIDTH = 390.0;
    private static final PseudoClass POPUP_OPEN = PseudoClass.getPseudoClass("popup-open");

    private final MenuButton node;
    private final Popup popup = new Popup();
    private final TextField search = new TextField();
    private final ListView<Option> results = new ListView<>();
    private final VBox popupContent = new VBox();
    private List<Option> options = List.of();
    private Optional<SecretId> selectedId = Optional.empty();
    private boolean opensUpward;
    private Runnable selectionChanged = () -> {};

    SearchableCredentialSelector(
            MenuButton node,
            List<GlobalCredential> credentials,
            List<CredentialGroup> groups,
            Optional<SecretId> selectedId) {
        this.node = Objects.requireNonNull(node, "node");
        node.setMnemonicParsing(false);
        node.setMaxWidth(Double.MAX_VALUE);
        node.getStyleClass().add("credential-selector");
        node.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.fxml.cf1065647e949843"));

        search.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.fxml.e8cac64d872cf6fe"));
        search.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.fxml.e8cac64d872cf6fe"));
        results.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.fxml.cf1065647e949843"));
        results.setPrefHeight(320.0);
        results.setFixedCellSize(48.0);
        results.setCellFactory(ignored -> optionCell());

        popupContent.setSpacing(3.0);
        popupContent.getChildren().setAll(search, results);
        popupContent.setPadding(new Insets(3.0));
        popupContent.getStyleClass().add("credential-selector-popup");
        VBox.setVgrow(results, Priority.ALWAYS);
        popup.getContent().setAll(popupContent);
        popup.setAnchorLocation(PopupWindow.AnchorLocation.CONTENT_TOP_LEFT);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setAutoFix(false);
        SearchableSelectorPopupInteraction.install(popup, node);
        SearchableSelectorInputMethod.install(popup, node, search);
        synchronizePopupWidth();
        node.widthProperty().addListener((ignored, previous, width) -> synchronizePopupWidth());
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
            if (event.getButton() != MouseButton.PRIMARY || node.isDisabled()) return;
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
        rebuild(credentials, groups, selectedId);
    }

    MenuButton node() { return node; }
    TextField searchField() { return search; }
    ListView<Option> results() { return results; }
    VBox popupContent() { return popupContent; }
    Popup popup() { return popup; }
    boolean isEmpty() { return options.isEmpty(); }

    Optional<GlobalCredential> selectedCredential() {
        return options.stream().filter(option -> selectedId.equals(Optional.of(option.credential().id())))
                .map(Option::credential).findFirst();
    }

    void setOnSelectionChanged(Runnable action) {
        selectionChanged = Objects.requireNonNull(action, "action");
    }

    void clearSelection() {
        updateSelection(Optional.empty());
    }

    void selectFirst() {
        if (!options.isEmpty()) updateSelection(Optional.of(options.getFirst().credential().id()));
    }

    void selectCredential(SecretId id) {
        Optional<SecretId> selection = options.stream()
                .map(option -> option.credential().id()).filter(id::equals).findFirst();
        updateSelection(selection);
    }

    void rebuild(
            List<GlobalCredential> credentials,
            List<CredentialGroup> groups,
            Optional<SecretId> preferredId) {
        options = options(credentials, groups);
        selectedId = options.stream().map(option -> option.credential().id())
                .filter(id -> preferredId.equals(Optional.of(id))).findFirst();
        updateButtonText();
        resetPopup();
    }

    private void updateSelection(Optional<SecretId> next) {
        Optional<SecretId> previous = selectedId;
        selectedId = next;
        updateButtonText();
        resetPopup();
        if (!selectedId.equals(previous)) selectionChanged.run();
    }

    private void resetPopup() {
        search.clear();
        results.getItems().setAll(options);
        options.stream().filter(option -> selectedId.equals(Optional.of(option.credential().id())))
                .findFirst().ifPresent(results.getSelectionModel()::select);
    }

    private void synchronizePopupWidth() {
        double width = Math.max(MINIMUM_POPUP_WIDTH, node.getWidth());
        popupContent.setMinWidth(width);
        popupContent.setPrefWidth(width);
        popupContent.setMaxWidth(width);
        popupContent.requestLayout();
    }

    private void togglePopup() {
        if (popup.isShowing()) popup.hide();
        else showPopup();
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
        results.getItems().setAll(filtered);
        filtered.stream().filter(option -> selectedId.equals(Optional.of(option.credential().id())))
                .findFirst().ifPresentOrElse(results.getSelectionModel()::select,
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
        updateSelection(Optional.of(option.credential().id()));
        popup.hide();
    }

    private void updateButtonText() {
        node.setText(selectedCredential().map(credential -> options.stream()
                        .filter(option -> option.credential().id().equals(credential.id()))
                        .findFirst().orElseThrow().primaryText())
                .orElseGet(() -> options.isEmpty()
                        ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.7b9903e626187f28")
                        : com.ynpe.fastremote.i18n.Texts.text("legacy.java.6dbefe50bb8e578d")));
    }

    private static ListCell<Option> optionCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Option option, boolean empty) {
                super.updateItem(option, empty);
                if (empty || option == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label primary = new Label(option.primaryText());
                primary.getStyleClass().add("credential-selector-option-name");
                Label secondary = new Label(option.secondaryText());
                secondary.getStyleClass().add("credential-selector-option-detail");
                setGraphic(new VBox(1.0, primary, secondary));
                setText(null);
            }
        };
    }

    static List<Option> options(List<GlobalCredential> credentials, List<CredentialGroup> groups) {
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(groups, "groups");
        Map<CredentialGroupId, String> groupNames = new HashMap<>();
        groups.forEach(group -> groupNames.put(group.id(), group.name()));
        return credentials.stream().map(credential -> {
                    String group = credential.groupId().map(groupNames::get).orElse("");
                    String primary = group.isBlank() ? credential.label() : group + "/" + credential.label();
                    return new Option(credential, primary, credential.username(), group);
                })
                .sorted(Comparator.comparing(Option::primaryText, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static List<Option> filter(List<Option> options, String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return List.copyOf(options);
        return options.stream().filter(option -> option.matches(needle)).toList();
    }

    record Option(GlobalCredential credential, String primaryText, String username, String group) {
        Option {
            Objects.requireNonNull(credential, "credential");
            primaryText = Objects.requireNonNull(primaryText, "primaryText");
            username = Objects.requireNonNull(username, "username");
            group = Objects.requireNonNull(group, "group");
        }

        String secondaryText() {
            return group.isBlank() ? username : group + " · " + username;
        }

        private boolean matches(String needle) {
            return credential.label().toLowerCase(Locale.ROOT).contains(needle)
                    || username.toLowerCase(Locale.ROOT).contains(needle)
                    || group.toLowerCase(Locale.ROOT).contains(needle);
        }
    }
}
