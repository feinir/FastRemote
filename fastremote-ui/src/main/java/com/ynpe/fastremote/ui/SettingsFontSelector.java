// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;

final class SettingsFontSelector {
    private SettingsFontSelector() {}

    static final class SearchableFontSelector {
        private static final PseudoClass POPUP_OPEN = PseudoClass.getPseudoClass("popup-open");
        private final MenuButton node = new MenuButton();
        private final TextField search = new TextField();
        private final ListView<String> results = new ListView<>();
        private final Popup popup = new Popup();
        private final VBox popupContent = new VBox(3.0);
        private final List<String> fonts;
        private final javafx.beans.property.StringProperty selectedFont =
                new javafx.beans.property.SimpleStringProperty();

        SearchableFontSelector(List<String> fonts, String initial) {
            java.util.TreeSet<String> available = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            available.addAll(fonts);
            available.add(initial);
            this.fonts = List.copyOf(available);
            String selected = this.fonts.stream().filter(initial::equals).findFirst().orElse(initial);
            node.setMinWidth(280.0);
            node.setMaxWidth(Double.MAX_VALUE);
            node.setMnemonicParsing(false);
            node.setText(selected);
            node.getStyleClass().add("font-selector-button");
            node.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.91411c96eed3bf21"));
            selectedFont.set(selected);

            search.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b4d4aad5f00db271"));
            search.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b4d4aad5f00db271"));
            results.getItems().setAll(this.fonts);
            results.setPrefSize(320.0, 340.0);
            results.setFixedCellSize(30.0);
            results.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.74b418c2bda34fc7"));
            popupContent.getChildren().setAll(search, results);
            popupContent.setPadding(new Insets(3.0));
            popupContent.getStyleClass().add("font-selector-popup");
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
                Platform.runLater(() -> DialogSpacing.focusAtEnd(search));
            });
            popup.setOnHidden(event -> node.pseudoClassStateChanged(POPUP_OPEN, false));

            search.textProperty().addListener((ignored, previous, query) -> applyFilter(query));
            SingleLineInputEnterSupport.runOnEnter(search, () -> {
                String choice = results.getSelectionModel().getSelectedItem();
                if (choice == null && !results.getItems().isEmpty()) choice = results.getItems().getFirst();
                selectFont(choice);
            });
            search.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.DOWN) {
                    if (results.getSelectionModel().getSelectedItem() == null && !results.getItems().isEmpty()) {
                        results.getSelectionModel().selectFirst();
                    }
                    results.requestFocus();
                    event.consume();
                } else if (event.getCode() == KeyCode.UP) {
                    if (!results.getItems().isEmpty()) {
                        if (results.getSelectionModel().getSelectedItem() == null) {
                            results.getSelectionModel().selectLast();
                        } else {
                            results.getSelectionModel().selectPrevious();
                        }
                    }
                    results.requestFocus();
                    event.consume();
                } else if (event.getCode() == KeyCode.ESCAPE) {
                    popup.hide();
                    event.consume();
                }
            });
            results.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    selectFont(results.getSelectionModel().getSelectedItem());
                    event.consume();
                } else if (event.getCode() == KeyCode.ESCAPE) {
                    popup.hide();
                    event.consume();
                }
            });
            SearchableSelectorMouseSupport.install(results, this::selectFont);
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
        }

        MenuButton node() { return node; }
        String selectedFont() { return selectedFont.get(); }
        javafx.beans.property.ReadOnlyStringProperty selectedFontProperty() { return selectedFont; }
        TextField searchField() { return search; }
        ListView<String> results() { return results; }

        private void resetPopup() {
            search.clear();
            results.getItems().setAll(fonts);
            results.getSelectionModel().select(selectedFont.get());
            Platform.runLater(() -> results.scrollTo(selectedFont.get()));
        }

        private void synchronizePopupWidth() {
            double width = node.getWidth();
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
            boolean opensUpward = SearchableSelectorPopupLayout.opensUpward(
                    screenBounds, SearchableSelectorPopupLayout.preferredHeight(popupContent));
            SearchableSelectorPopupLayout.arrange(popupContent, search, results, opensUpward);
            resetPopup();
            double anchorY = opensUpward
                    ? screenBounds.getMinY() - SearchableSelectorPopupLayout.preferredHeight(popupContent)
                    : screenBounds.getMaxY();
            popup.show(node, screenBounds.getMinX(), anchorY);
        }

        private void applyFilter(String query) {
            results.getItems().setAll(filterFonts(fonts, query));
            if (results.getItems().contains(selectedFont.get())) {
                results.getSelectionModel().select(selectedFont.get());
            } else {
                results.getSelectionModel().clearSelection();
            }
        }

        private void selectFont(String font) {
            if (font == null || !fonts.contains(font)) return;
            selectedFont.set(font);
            node.setText(font);
            popup.hide();
        }
    }

    static List<String> filterFonts(List<String> fonts, String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
        return needle.isEmpty() ? List.copyOf(fonts) : fonts.stream()
                .filter(font -> font.toLowerCase(java.util.Locale.ROOT).contains(needle)).toList();
    }
}
