package com.techsenger.jeditermfx.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.techsenger.jeditermfx.ui.settings.DefaultSettingsProvider;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TerminalPanelContextMenuStyleTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void terminalPopupReceivesHostContextMenuStyleClasses() throws Exception {
        runOnJavaFxThread(() -> {
            DefaultSettingsProvider settings = new DefaultSettingsProvider() {
                @Override
                public @NotNull List<String> getContextMenuStyleClasses() {
                    return List.of("host-context-menu");
                }

                @Override
                public @NotNull String getContextMenuStyle() {
                    return "-fx-font-family: 'Test Font';";
                }
            };
            JediTermFxWidget widget = new JediTermFxWidget(settings);
            try {
                ContextMenu menu = widget.getTerminalPanel().createPopupMenu(widget.getTerminalPanel());
                assertTrue(menu.getStyleClass().contains("host-context-menu"));
                assertEquals("-fx-font-family: 'Test Font';", menu.getStyle());
                MenuItem copy = menu.getItems().stream()
                        .filter(item -> item.getText() != null
                                && "Copy".equals(item.getText().replace("_", "")))
                        .findFirst().orElseThrow();
                assertEquals(settings.getCopyActionPresentation().getKeyCombinations().getFirst(),
                        copy.getAccelerator());
                assertTrue(menu.getItems().stream().anyMatch(item -> item.getText() != null
                        && "Find".equals(item.getText().replace("_", ""))));
                MenuItem paste = menu.getItems().stream()
                        .filter(item -> item.getText() != null
                                && "Paste".equals(item.getText().replace("_", "")))
                        .findFirst().orElseThrow();
                assertFalse(paste.isDisable(),
                        "opening the popup must not synchronously probe the system clipboard");
            } finally {
                widget.close();
            }
        });
    }

    @Test
    void hostContextActionsPrecedeBuiltInActionsAndSupportSubmenus() throws Exception {
        runOnJavaFxThread(() -> {
            AtomicInteger invocations = new AtomicInteger();
            DefaultSettingsProvider settings = new DefaultSettingsProvider() {
                @Override
                public @NotNull List<TerminalContextMenuEntry> getContextMenuEntries() {
                    return List.of(
                            TerminalContextMenuEntry.submenu("Go to", List.of(
                                    TerminalContextMenuEntry.action("/srv", invocations::incrementAndGet))),
                            new TerminalContextMenuEntry.Action(
                                    "New Terminal", () -> {}, () -> false));
                }
            };
            JediTermFxWidget widget = new JediTermFxWidget(settings);
            try {
                ContextMenu menu = widget.getTerminalPanel().createPopupMenu(widget.getTerminalPanel());
                assertEquals("Go to", menu.getItems().get(0).getText());
                MenuItem favorite = ((Menu) menu.getItems().get(0)).getItems().getFirst();
                assertEquals("/srv", favorite.getText());
                favorite.fire();
                assertEquals(1, invocations.get(),
                        "JediTermFX must bind the supplied callback to its own MenuItem");
                assertEquals("New Terminal", menu.getItems().get(1).getText());
                assertTrue(menu.getItems().get(1).isDisable());
                assertTrue(menu.getItems().get(2) instanceof SeparatorMenuItem);
                assertTrue(menu.getItems().stream().skip(3).anyMatch(item -> item.getText() != null
                        && "Copy".equals(item.getText().replace("_", ""))));
            } finally {
                widget.close();
            }
        });
    }

    @Test
    void hostSubmenusReceiveTheTerminalPaletteInsteadOfTheApplicationPalette() throws Exception {
        assertSubmenuPalette("terminal-context-dark", "#171c23");
        assertSubmenuPalette("terminal-context-light", "#ffffff");
    }

    private static void assertSubmenuPalette(String paletteClass, String expectedBackground)
            throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            DefaultSettingsProvider settings = new DefaultSettingsProvider() {
                @Override
                public @NotNull List<String> getContextMenuStyleClasses() {
                    return List.of("application-context-menu", paletteClass);
                }

                @Override
                public @NotNull String getContextMenuStyle() {
                    return "-fx-font-family: 'Terminal Font';";
                }

                @Override
                public @NotNull List<TerminalContextMenuEntry> getContextMenuEntries() {
                    return List.of(TerminalContextMenuEntry.submenu("Go to", List.of(
                            TerminalContextMenuEntry.action("/srv", () -> {}))));
                }
            };
            JediTermFxWidget widget = new JediTermFxWidget(settings);
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(widget.getPane()), 640, 420));
            stage.getScene().getStylesheets().add(Objects.requireNonNull(
                    TerminalPanelContextMenuStyleTest.class.getResource(
                            "terminal-context-menu-test.css")).toExternalForm());
            stage.show();
            ContextMenu root = widget.getTerminalPanel().createPopupMenu(widget.getTerminalPanel());
            ContextMenu submenu = new ContextMenu();
            try {
                root.show(widget.getPane(), 40, 40);
                Menu hostMenu = (Menu) root.getItems().getFirst();
                submenu.getItems().setAll(hostMenu.getItems());
                assertTrue(submenu.getStyleClass().contains(paletteClass),
                        "submenu palette must be installed before its native window is shown");
                assertNull(hostMenu.getOnShown(), "no post-map styling or delayed retry");
                submenu.show(root.getSkin().getNode(), javafx.geometry.Side.RIGHT, 0, 0);
                Platform.runLater(() -> {
                    try {
                        assertTrue(submenu.getStyleClass().contains(paletteClass));
                        assertEquals("-fx-font-family: 'Terminal Font';", submenu.getStyle());
                        Region popup = (Region) submenu.getSkin().getNode();
                        popup.applyCss();
                        assertTrue(popup.getStyleClass().contains(paletteClass));
                        assertEquals("-fx-font-family: 'Terminal Font';", popup.getStyle());
                        assertEquals(Color.web(expectedBackground),
                                popup.getBackground().getFills().getFirst().getFill(),
                                "the submenu popup must resolve the terminal palette CSS");
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        submenu.hide();
                        root.hide();
                        widget.close();
                        stage.close();
                        completed.countDown();
                    }
                });
            } catch (Throwable error) {
                failure.set(error);
                submenu.hide();
                root.hide();
                widget.close();
                stage.close();
                completed.countDown();
            }
        });
        assertTrue(completed.await(15, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void hostPreparationSeesFreshActionsBeforeEachNativeShow() throws Exception {
        runOnJavaFxThread(() -> {
            AtomicInteger revision = new AtomicInteger();
            AtomicInteger prepared = new AtomicInteger();
            DefaultSettingsProvider settings = new DefaultSettingsProvider() {
                @Override public List<TerminalContextMenuEntry> getContextMenuEntries() {
                    return List.of(TerminalContextMenuEntry.action("Revision " + revision.get(), () -> {}));
                }
                @Override public void prepareContextMenu(ContextMenu menu, javafx.scene.Node owner) {
                    assertFalse(menu.isShowing());
                    assertEquals("Revision " + revision.get(), menu.getItems().getFirst().getText());
                    prepared.incrementAndGet();
                }
            };
            JediTermFxWidget widget = new JediTermFxWidget(settings);
            Stage stage = new Stage();
            ContextMenu menu = widget.getTerminalPanel().createPopupMenu(widget.getTerminalPanel());
            try {
                stage.setScene(new Scene(new StackPane(widget.getPane()), 640, 420));
                stage.show();
                for (int i = 1; i <= 3; i++) {
                    revision.set(i);
                    assertSame(menu, widget.getTerminalPanel().createPopupMenu(widget.getTerminalPanel()));
                    menu.show(widget.getPane(), 40, 40);
                    assertEquals(i, prepared.get());
                    menu.hide();
                }
            } finally { menu.hide(); widget.close(); stage.close(); }
        });
    }

    @Test
    void linkHoverPaintingAndNavigationAreIndependentAndUpdateLive() throws Exception {
        runOnJavaFxThread(() -> {
            var mode = new AtomicReference<>(com.techsenger.jeditermfx.core.HyperlinkStyle.HighlightMode.NEVER_WITH_ORIGINAL_COLOR);
            var direct = new java.util.concurrent.atomic.AtomicBoolean(true);
            var navigations = new AtomicInteger();
            DefaultSettingsProvider settings = new DefaultSettingsProvider() {
                @Override public com.techsenger.jeditermfx.core.HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode() {
                    return mode.get();
                }
                @Override public boolean openHyperlinksOnClick() { return direct.get(); }
                @Override public com.techsenger.jeditermfx.core.TextStyle getHyperlinkColor() {
                    return new com.techsenger.jeditermfx.core.TextStyle(
                            com.techsenger.jeditermfx.core.TerminalColor.BLACK,
                            com.techsenger.jeditermfx.core.TerminalColor.rgb(255, 255, 0));
                }
            };
            JediTermFxWidget widget = new JediTermFxWidget(settings);
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(widget.getPane()), 640, 420));
            stage.show();
            try {
                widget.getPane().applyCss();
                widget.getPane().layout();
                String url = "https://example.invalid";
                widget.addHyperlinkFilter(line -> line.startsWith(url)
                        ? new com.techsenger.jeditermfx.core.model.hyperlinks.LinkResult(
                                new com.techsenger.jeditermfx.core.model.hyperlinks.LinkResultItem(0, url.length() + 1,
                                        new com.techsenger.jeditermfx.core.model.hyperlinks.LinkInfo(navigations::incrementAndGet)))
                        : null);
                // Include a blank cell in this synthetic link so sampling cannot hit a platform-specific glyph.
                widget.getTerminal().writeCharacters(url + " ");
                var panel = widget.getTerminalPanel();
                var canvas = panel.getCanvas();
                canvas.fireEvent(new MouseEvent(MouseEvent.MOUSE_MOVED, 10, 5, 10, 5, MouseButton.NONE, 0,
                        false, false, false, false, false, false, false, false, false, false, null));
                assertEquals(javafx.scene.Cursor.HAND, canvas.getCursor());
                var renderer = new TerminalPanel.WeakRedrawTimer(panel);
                for (boolean highlight : new boolean[] {false, true, false}) {
                    mode.set(highlight
                            ? com.techsenger.jeditermfx.core.HyperlinkStyle.HighlightMode.HOVER_WITH_BOTH_COLORS
                            : com.techsenger.jeditermfx.core.HyperlinkStyle.HighlightMode.NEVER_WITH_ORIGINAL_COLOR);
                    panel.repaint();
                    renderer.handle(null);
                    int sampleX = (int) (panel.getInsetX() + (url.length() + 0.5) * panel.myCharSize.getWidth());
                    int sampleY = (int) (panel.myCharSize.getHeight() / 2);
                    assertEquals(highlight ? Color.YELLOW : panel.getBackground(),
                            canvas.snapshot(null, null).getPixelReader().getColor(sampleX, sampleY));
                    direct.set(true);
                    int before = navigations.get();
                    fireMouseClick(widget, MouseButton.PRIMARY, 10, 5);
                    assertEquals(before + 1, navigations.get());
                    direct.set(false);
                    fireMouseClick(widget, MouseButton.PRIMARY, 10, 5);
                    assertEquals(before + 1, navigations.get());
                    canvas.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 10, 5, 10, 5, MouseButton.PRIMARY, 1,
                            false, true, false, true, false, false, false, false, false, true, null));
                    assertEquals(before + 2, navigations.get());
                }
            } finally {
                widget.close();
                stage.close();
            }
        });
    }

    @Test
    void repeatedSecondaryClickReusesPopupAndPrimaryClickClosesIt() throws Exception {
        runOnJavaFxThread(() -> {
            JediTermFxWidget widget = new JediTermFxWidget(new DefaultSettingsProvider());
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(widget.getPane()), 640, 420));
            stage.show();
            try {
                fireMouseClick(widget, MouseButton.SECONDARY, 20, 20);
                ContextMenu first = widget.getTerminalPanel().getActivePopupMenu();
                assertTrue(first.isShowing());

                for (int index = 0; index < 20; index++) {
                    fireMouseClick(widget, MouseButton.SECONDARY, 80 + index, 40 + index);
                }
                ContextMenu second = widget.getTerminalPanel().getActivePopupMenu();
                assertSame(first, second);
                assertTrue(second.isShowing());

                fireMouseClick(widget, MouseButton.PRIMARY, 100, 60);
                assertFalse(second.isShowing());
                assertNull(widget.getTerminalPanel().getActivePopupMenu());
            } finally {
                widget.close();
                stage.close();
            }
        });
    }

    @Test
    void leavingAlternateScreenRestoresThePrimaryViewportToThePrompt() throws Exception {
        runOnJavaFxThread(() -> {
            JediTermFxWidget widget = new JediTermFxWidget(new DefaultSettingsProvider());
            try {
                widget.getTerminalPanel().swingClientScrollOrigin = -12;
                widget.getTerminalPanel().restorePrimaryBufferViewport();
                assertEquals(0, widget.getTerminalPanel().swingClientScrollOrigin);
            } finally {
                widget.close();
            }
        });
    }

    private static void fireMouseClick(JediTermFxWidget widget, MouseButton button, double x, double y) {
        var canvas = widget.getTerminalPanel().getCanvas();
        var screenPoint = canvas.localToScreen(x, y);
        canvas.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED,
                x, y, screenPoint.getX(), screenPoint.getY(), button, 1,
                false, false, false, false,
                false, false, false, false, false, true, null));
    }

    private static void runOnJavaFxThread(Runnable assertion) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                assertion.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }
}
