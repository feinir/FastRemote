// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.css.PseudoClass;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SearchableSelectorPopupStylesTest {
    @Test
    void historyDeleteHoverResolvesDangerColorInBothThemes() throws Exception {
        SearchableSelectorInputMethodTest.onFx(() -> {
            var icon = new LucideIconView(LucideIconView.Icon.TRASH_2);
            var button = new javafx.scene.control.Button();
            button.setGraphic(icon);
            button.getStyleClass().add("ai-history-delete-button");
            var root = new VBox(button);
            var scene = new Scene(root);
            scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
            button.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true);
            root.applyCss();
            var path = (javafx.scene.shape.SVGPath) icon.getChildren().getFirst();
            assertEquals(javafx.scene.paint.Color.web("#be2d2d"), path.getStroke());
            scene.getStylesheets().add(MainView.darkStylesheet().toExternalForm());
            root.applyCss();
            assertEquals(javafx.scene.paint.Color.web("#ff6975"), path.getStroke());
        });
    }

    @Test
    void onePressOnRowTextAcceptsItWhileSearchOwnsFocus() throws Exception {
        var done = new java.util.concurrent.CompletableFuture<Void>();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                TextField search = new TextField();
                ListView<String> results = new ListView<>();
                results.getItems().setAll("first", "second");
                results.setCellFactory(ignored -> new ListCell<>() {
                    @Override protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(null);
                        setGraphic(empty ? null : new Label(item));
                    }
                });
                var accepted = new AtomicReference<String>();
                SearchableSelectorMouseSupport.install(results, accepted::set);
                stage.setScene(new Scene(new VBox(search, results), 400, 400));
                stage.show(); search.requestFocus();
                results.getSelectionModel().selectFirst();
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                ListCell<?> second = results.lookupAll(".list-cell").stream()
                        .filter(n -> n instanceof ListCell<?> c && c.getIndex() == 1)
                        .map(n -> (ListCell<?>) n).findFirst().orElseThrow();
                var text = second.getGraphic();
                text.fireEvent(new javafx.scene.input.MouseEvent(
                        javafx.scene.input.MouseEvent.MOUSE_PRESSED, 5, 5, 5, 5,
                        javafx.scene.input.MouseButton.SECONDARY, 1,
                        false, false, false, false, false, false, false, false, false, true,
                        new javafx.scene.input.PickResult(text, 5, 5)));
                assertNull(accepted.get());
                text.fireEvent(new javafx.scene.input.MouseEvent(
                        javafx.scene.input.MouseEvent.MOUSE_PRESSED, 5, 5, 5, 5,
                        javafx.scene.input.MouseButton.PRIMARY, 1,
                        false, false, false, false, true, false, false, false, false, true,
                        new javafx.scene.input.PickResult(text, 5, 5)));
                assertEquals("second", accepted.get());
                assertEquals("second", results.getSelectionModel().getSelectedItem());
                done.complete(null);
            } catch (Throwable error) { done.completeExceptionally(error); }
            finally { stage.hide(); }
        });
        done.get(10, TimeUnit.SECONDS);
    }

    @Test
    void popupHoverIsSubtleAndDoesNotReplaceSelectionInEitherTheme() throws Exception {
        var completed = new java.util.concurrent.CompletableFuture<Void>();
        Platform.runLater(() -> {
            try {
                for (boolean dark : new boolean[]{false, true}) {
                    for (String kind : java.util.List.of("combo-box-popup", "font-selector-popup",
                            "jump-host-selector-popup", "credential-selector-popup", "command-palette-results",
                            "editor-search-popup-light", "editor-search-popup-dark")) {
                        ListView<String> results = new ListView<>();
                        results.getItems().setAll("Selected", "Hovered");
                        VBox root = new VBox(results);
                        if (kind.equals("command-palette-results") || kind.startsWith("editor-search")) {
                            results.getStyleClass().add(kind);
                        } else root.getStyleClass().add(kind);
                        Scene scene = new Scene(root, 400, 180);
                        scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
                        if (dark) scene.getStylesheets().add(MainView.darkStylesheet().toExternalForm());
                        results.getSelectionModel().select(0);
                        root.applyCss(); root.resize(400, 180); root.layout();
                        var cells = results.lookupAll(".list-cell").stream().map(ListCell.class::cast)
                                .filter(cell -> !cell.isEmpty()).sorted(java.util.Comparator.comparingInt(ListCell::getIndex))
                                .toList();
                        ListCell<?> selected = cells.get(0);
                        ListCell<?> hovered = cells.get(1);
                        hovered.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true);
                        root.applyCss();
                        boolean rowDark = kind.equals("editor-search-popup-dark")
                                || (dark && !kind.equals("editor-search-popup-light"));
                        var selection = javafx.scene.paint.Color.web(kind.equals("editor-search-popup-dark") ? "#31558a" : rowDark ? "#2c3964" : "#dbe8fb");
                        var hover = javafx.scene.paint.Color.web(kind.equals("editor-search-popup-dark") ? "#252e3b" : rowDark ? "#2b2d30" : "#f1f4f8");
                        assertEquals(selection, selected.getBackground().getFills().getFirst().getFill(), kind);
                        assertEquals(hover, hovered.getBackground().getFills().getFirst().getFill(), kind);
                        selected.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true);
                        root.applyCss();
                        assertEquals(selection, selected.getBackground().getFills().getFirst().getFill(),
                                kind + " selected+hover");
                    }
                }
                completed.complete(null);
            } catch (Throwable failure) { completed.completeExceptionally(failure); }
        });
        completed.get(15, TimeUnit.SECONDS);
    }

    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        org.junit.jupiter.api.Assertions.assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void copiesStylesheetsMovedFromTheSceneToItsRoot() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                VBox root = new VBox();
                Scene scene = new Scene(root);
                String main = MainView.stylesheet().toExternalForm();
                String dark = MainView.darkStylesheet().toExternalForm();
                root.getStylesheets().setAll(main, dark);
                VBox popupContent = new VBox();

                SearchableSelectorPopupStyles.copyFrom(scene, popupContent);

                assertEquals(java.util.List.of(main, dark), popupContent.getStylesheets());
                assertTrue(popupContent.getStyleClass().contains(
                        InterfaceFontPolicy.platformStyleClass(System.getProperty("os.name", ""))));
                java.util.concurrent.atomic.AtomicInteger invalidations = new java.util.concurrent.atomic.AtomicInteger();
                popupContent.getStylesheets().addListener((javafx.beans.InvalidationListener)
                        ignored -> invalidations.incrementAndGet());
                SearchableSelectorPopupStyles.copyFrom(scene, popupContent);
                assertEquals(0, invalidations.get(), "Reopening must not reparse unchanged stylesheets");
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                completed.countDown();
            }
        });
        org.junit.jupiter.api.Assertions.assertTrue(completed.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test
    void detachedPopupSearchAndResultsUseTheInterfaceFont() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Stage owner = new Stage();
            Popup popup = new Popup();
            try {
                Label reference = new Label("界面字体 Interface");
                VBox root = new VBox(reference);
                Scene scene = new Scene(root, 400, 240);
                root.getStylesheets().add(MainView.stylesheet().toExternalForm());
                InterfaceFontPolicy.apply(root);
                owner.setScene(scene);
                owner.show();
                root.applyCss();
                String expectedFamily = reference.getFont().getFamily();
                for (String kind : java.util.List.of("font-selector", "jump-host-selector", "credential-selector")) {
                    TextField search = new TextField("搜索 Search");
                    ListView<String> results = new ListView<>();
                    results.getItems().add("Monaco / 测试条目");
                    results.setPrefSize(300, 100);
                    Label detail = new Label("详情 Detail");
                    VBox content = new VBox(search, results, detail);
                    content.getStyleClass().add(kind + "-popup");
                    content.setStyle("-fx-opacity: 0.98;");
                    popup.getContent().setAll(content);
                    for (int opening = 0; opening < 2; opening++) {
                        SearchableSelectorPopupStyles.copyFrom(scene, content);
                        var warnings = new java.util.ArrayList<String>();
                        var logger = java.util.logging.Logger.getLogger("javafx.scene.CssStyleHelper");
                        var handler = new java.util.logging.Handler() {
                            @Override public void publish(java.util.logging.LogRecord record) { warnings.add(record.getMessage()); }
                            @Override public void flush() { }
                            @Override public void close() { }
                        };
                        logger.addHandler(handler);
                        try { SearchableSelectorPopupLayout.preferredHeight(content); }
                        finally { logger.removeHandler(handler); }
                        assertTrue(warnings.isEmpty(), kind + " pre-show CSS: " + warnings);
                        assertTrue(content.getStyleClass().contains("root"));
                        popup.show(owner);
                        popup.getScene().getRoot().applyCss();
                        popup.getScene().getRoot().layout();
                        assertEquals(expectedFamily, search.getFont().getFamily(), kind + " search");
                        assertEquals(expectedFamily, detail.getFont().getFamily(), kind + " detail");
                        var cells = results.lookupAll(".list-cell").stream()
                                .map(ListCell.class::cast).filter(cell -> !cell.isEmpty()).toList();
                        assertTrue(!cells.isEmpty(), kind + " must render a result cell");
                        for (ListCell<?> cell : cells) {
                            assertEquals(expectedFamily, cell.getFont().getFamily(), kind + " result");
                        }
                        assertTrue(content.getStyle().contains("-fx-opacity: 0.98;"));
                        popup.hide();
                    }
                }
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                popup.hide();
                owner.close();
                completed.countDown();
            }
        });
        assertTrue(completed.await(15, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test
    void linuxSearchableSelectorsKeepBordersWithoutStackedShadowEffects() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                for (boolean dark : new boolean[]{false, true}) {
                    for (String platform : java.util.List.of("platform-linux", "platform-macos", "platform-windows")) {
                        for (String kind : java.util.List.of("font-selector", "jump-host-selector", "credential-selector")) {
                            MenuButton owner = new MenuButton("Choice");
                            owner.getStyleClass().add(kind.equals("font-selector") ? kind + "-button" : kind);
                            owner.pseudoClassStateChanged(PseudoClass.getPseudoClass("popup-open"), true);
                            TextField search = new TextField();
                            search.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
                            VBox popup = new VBox(search);
                            popup.getStyleClass().addAll(kind + "-popup", platform);
                            VBox root = new VBox(owner, popup);
                            root.getStyleClass().add(platform);
                            Scene scene = new Scene(root);
                            scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
                            if (dark) scene.getStylesheets().add(MainView.darkStylesheet().toExternalForm());
                            root.applyCss();
                            root.autosize();
                            root.layout();
                            if (platform.equals("platform-linux")) {
                                assertNull(owner.getEffect(), kind);
                                assertNull(popup.getEffect(), kind);
                                assertNull(search.getEffect(), kind);
                            } else {
                                assertNotNull(owner.getEffect(), kind);
                                assertNotNull(popup.getEffect(), kind);
                                assertNotNull(search.getEffect(), kind);
                            }
                            assertEquals(1.0, popup.getBorder().getStrokes().getFirst().getWidths().getTop());
                            assertEquals(3.0, popup.getPadding().getTop());
                            assertEquals(owner.getBorder().getStrokes().getFirst().getTopStroke(),
                                    search.getBorder().getStrokes().getFirst().getTopStroke());
                        }
                    }
                }
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
