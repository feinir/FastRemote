// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.ynpe.fastremote.domain.settings.ToolbarDisplayMode;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GlobalToolbarPresentationTest {
    @BeforeAll static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test void shrinkingKeepsStatusAndFixedActionsWhileTrailingButtonsOverflow() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                var buttons = java.util.stream.IntStream.range(0, 8)
                        .mapToObj(i -> new Button("Action " + i, new Label("Icon"))).toList();
                buttons.forEach(button -> button.getStyleClass().add("toolbar-action-button"));
                HBox group = new HBox();
                group.getStyleClass().add("toolbar-primary-actions");
                group.getChildren().addAll(buttons);
                ToolBar toolbar = new ToolBar(group);
                toolbar.setMinWidth(0);
                toolbar.getStyleClass().addAll("main-toolbar", "main-toolbar-overflow");
                HBox.setHgrow(toolbar, Priority.ALWAYS);
                Label status = new Label("Credential vault · Unlocked");
                Button settings = new Button("Settings", new Label("Icon"));
                settings.getStyleClass().add("toolbar-icon-button");
                settings.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                HBox fixed = new HBox(4, status, settings);
                fixed.setMinWidth(Region.USE_PREF_SIZE);
                HBox row = new HBox(toolbar, fixed);
                row.getStyleClass().addAll("main-toolbar", "main-toolbar-row");
                row.visibleProperty().bind(toolbar.visibleProperty());
                row.managedProperty().bind(toolbar.managedProperty());
                StackPane root = new StackPane(row);
                Scene scene = new Scene(root, 1600, 60);
                scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
                GlobalToolbarPresentation.install(toolbar, () -> ToolbarDisplayMode.ICONS_AND_TEXT,
                        mode -> { }, () -> { });
                assertEquals(buttons, toolbar.getItems());
                for (ToolbarDisplayMode mode : ToolbarDisplayMode.values()) {
                    GlobalToolbarPresentation.apply(toolbar, mode);
                    for (double width : new double[] {1600, 480, 400, 1600}) {
                        root.resize(width, 60);
                        root.applyCss();
                        root.layout();
                        assertEquals(fixed.prefWidth(-1), fixed.getWidth(), 1.0);
                        assertTrue(fixed.getLayoutX() + fixed.getWidth() <= width);
                        assertSame(fixed, status.getParent());
                        assertSame(fixed, settings.getParent());
                        assertTrue(status.isVisible());
                        assertEquals(ContentDisplay.GRAPHIC_ONLY, settings.getContentDisplay());
                        var container = (javafx.scene.Parent) toolbar.lookup(".container");
                        assertTrue(container.getChildrenUnmodifiable().contains(buttons.getFirst()));
                        assertEquals(width == 1600,
                                container.getChildrenUnmodifiable().contains(buttons.getLast()));
                    }
                }
                toolbar.setVisible(false);
                toolbar.setManaged(false);
                assertFalse(row.isVisible());
                assertFalse(row.isManaged());
            } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void fixedActionsRemainIconsWhenMovedIntoTheOverflowPopup() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            javafx.stage.Stage owner = new javafx.stage.Stage();
            ContextMenu overflow = new ContextMenu();
            try {
                for (ToolbarDisplayMode mode : ToolbarDisplayMode.values()) {
                    List<Button> buttons = List.of("Master password", "Settings", "Left panel", "Right panel")
                            .stream().map(text -> {
                                Button button = new Button(text, new Label("Icon"));
                                button.getStyleClass().add("toolbar-icon-button");
                                button.setAccessibleText(text);
                                button.setTooltip(new Tooltip(text));
                                return button;
                            }).toList();
                    ToolBar toolbar = new ToolBar(buttons.toArray(Button[]::new));
                    toolbar.getStyleClass().add("main-toolbar");
                    StackPane root = new StackPane(toolbar);
                    Scene scene = new Scene(root, 400, 100);
                    scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
                    owner.setScene(scene);
                    owner.show();
                    GlobalToolbarPresentation.apply(toolbar, mode);
                    root.applyCss();
                    buttons.forEach(button -> assertEquals(ContentDisplay.GRAPHIC_ONLY, button.getContentDisplay()));

                    // ToolBarSkin reuses overflowed controls inside CustomMenuItems.
                    toolbar.getItems().clear();
                    buttons.forEach(button -> overflow.getItems().add(new CustomMenuItem(button)));
                    overflow.show(owner, owner.getX(), owner.getY());
                    overflow.getScene().getStylesheets().setAll(MainView.stylesheet().toExternalForm());
                    overflow.getScene().getRoot().applyCss();
                    overflow.getScene().getRoot().layout();
                    for (Button button : buttons) {
                        assertEquals(ContentDisplay.GRAPHIC_ONLY, button.getContentDisplay());
                        assertEquals(32.0, button.prefWidth(-1));
                        assertEquals(button.getText(), button.getAccessibleText());
                        assertEquals(button.getText(), button.getTooltip().getText());
                    }
                    overflow.hide();
                    overflow.getItems().clear();
                }
            } catch (Throwable error) { failure.set(error); }
            finally { overflow.hide(); owner.close(); done.countDown(); }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void menuModesSurviveCssAndKeepActionsOrderAndCustomizeTarget() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Button first = new Button("First", new Label("Icon"));
                first.getStyleClass().add("toolbar-action-button");
                Button second = new Button("Settings", new Label("Icon"));
                second.getStyleClass().add("toolbar-icon-button");
                HBox group = new HBox(first, second);
                ToolBar toolbar = new ToolBar(group);
                toolbar.getStyleClass().add("main-toolbar");
                StackPane root = new StackPane(toolbar);
                Scene scene = new Scene(root, 600, 100);
                scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
                AtomicReference<ToolbarDisplayMode> current = new AtomicReference<>(ToolbarDisplayMode.ICONS_AND_TEXT);
                int[] custom = {0};
                ContextMenu menu = GlobalToolbarPresentation.install(toolbar, current::get, mode -> {
                    current.set(mode);
                    GlobalToolbarPresentation.apply(toolbar, mode);
                }, () -> custom[0]++);
                ContentDisplay[] expected = {ContentDisplay.LEFT, ContentDisplay.GRAPHIC_ONLY, ContentDisplay.TEXT_ONLY};
                for (int index = 0; index < 3; index++) {
                    menu.getItems().get(index).fire();
                    root.applyCss();
                    root.layout();
                    assertEquals(expected[index], first.getContentDisplay());
                    assertEquals(ContentDisplay.GRAPHIC_ONLY, second.getContentDisplay());
                    assertEquals(26.0, second.prefWidth(-1));
                    assertEquals(List.of(first, second), group.getChildren());
                    assertEquals("First", first.getText());
                    assertNotNull(first.getGraphic());
                }
                assertInstanceOf(SeparatorMenuItem.class, menu.getItems().get(3));
                menu.getItems().get(4).fire();
                assertEquals(1, custom[0]);

                VBox section = new VBox(new Label("Toolbar"));
                section.setId("toolbar-settings-section");
                section.setPrefHeight(150);
                Region before = new Region(); before.setMinHeight(250);
                Region after = new Region(); after.setMinHeight(900);
                VBox content = new VBox(before, section, after);
                ScrollPane scroll = new ScrollPane(content);
                StackPane host = new StackPane(scroll);
                new Scene(host, 600, 400);
                ConfigurationDialog.revealToolbarSection(host);
                assertTrue(scroll.getVvalue() > 0);
                assertEquals(0, scroll.getContent().sceneToLocal(
                        section.localToScene(section.getBoundsInLocal())).getMinY()
                        - scroll.getVvalue() * (content.getLayoutBounds().getHeight()
                        - scroll.getViewportBounds().getHeight()), 1.0);
            } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
