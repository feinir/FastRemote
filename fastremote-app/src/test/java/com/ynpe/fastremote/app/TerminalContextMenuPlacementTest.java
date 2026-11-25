// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.*;

import com.ynpe.fastremote.domain.settings.TerminalColorScheme;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.terminal.FastRemoteTerminal;
import com.ynpe.fastremote.ui.MainView;
import com.ynpe.fastremote.ui.TerminalContextMenuPreparation;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TerminalContextMenuPlacementTest {
    @BeforeAll
    static void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @ParameterizedTest
    @EnumSource(value = TerminalColorScheme.class, names = {"FAST_LIGHT", "FAST_DARK"})
    void actualTerminalRootAndSubmenusArePreparedBeforeMapping(TerminalColorScheme palette) throws Exception {
        onFx(() -> {
            for (boolean darkApplication : new boolean[]{false, true}) verify(palette, darkApplication);
        });
    }

    private void verify(TerminalColorScheme palette, boolean darkApplication) {
        var defaults = TerminalPreferences.defaults();
        var preferences = new TerminalPreferences(palette, defaults.fontFamily(), defaults.fontSize(),
                defaults.cursorShape(), defaults.cursorBlink());
        FastRemoteTerminal terminal = new FastRemoteTerminal(preferences, TerminalContextMenuPreparation::prepare);
        Stage stage = new Stage();
        Map<ContextMenu, Point2D> first = new IdentityHashMap<>();
        ListChangeListener<Window> listener = changes -> {
            while (changes.next()) for (Window window : changes.getAddedSubList()) {
                if (window instanceof ContextMenu menu) menu.addEventHandler(WindowEvent.WINDOW_SHOWN,
                        event -> first.put(menu, new Point2D(menu.getAnchorX(), menu.getAnchorY())));
            }
        };
        Window.getWindows().addListener(listener);
        try {
            var children = new ArrayList<FastRemoteTerminal.ContextMenuAction>();
            for (int i = 0; i < 16; i++) children.add(new FastRemoteTerminal.ContextMenuAction.Item(
                    "Generated child " + i, () -> {}, () -> true));
            terminal.setContextMenuItems(() -> List.of(
                    new FastRemoteTerminal.ContextMenuAction.Submenu("Generated submenu", children, () -> true)));
            StackPane owner = new StackPane(terminal.node());
            owner.getStylesheets().add(MainView.stylesheet().toExternalForm());
            if (darkApplication) owner.getStylesheets().add(MainView.class.getResource("theme-dark.css").toExternalForm());
            stage.setScene(new Scene(owner, 550, 380));
            stage.show();
            var area = Screen.getPrimary().getVisualBounds();
            for (boolean edge : new boolean[]{false, true, true}) {
                double x = edge ? area.getMaxX() - 20 : area.getMinX() + 80;
                double y = edge ? area.getMaxY() - 20 : area.getMinY() + 80;
                terminal.focusTarget().fireEvent(mouse(MouseEvent.MOUSE_CLICKED, x, y, MouseButton.SECONDARY));
                ContextMenu root = Window.getWindows().stream().filter(w -> w instanceof ContextMenu
                        && ((ContextMenu) w).getOwnerWindow() == stage).map(w -> (ContextMenu) w).findFirst().orElseThrow();
                assertPrepared(root, first, palette);
                root.getSkin().getNode().lookup(".menu-item")
                        .fireEvent(mouse(MouseEvent.MOUSE_ENTERED, x, y, MouseButton.NONE));
                ContextMenu child = Window.getWindows().stream().filter(w -> w instanceof ContextMenu
                        && ((ContextMenu) w).getOwnerWindow() == root).map(w -> (ContextMenu) w).findFirst().orElseThrow();
                assertPrepared(child, first, palette);
                if (edge && isLinux()) assertTrue(child.getAnchorX() < root.getAnchorX(), "submenu flips left");
                child.hide();
                root.hide();
            }
        } finally {
            Window.getWindows().removeListener(listener);
            first.keySet().forEach(ContextMenu::hide);
            terminal.close(); stage.close();
        }
    }

    private void assertPrepared(ContextMenu menu, Map<ContextMenu, Point2D> first, TerminalColorScheme palette) {
        assertNotNull(first.get(menu));
        assertTrue(menu.getSkin().getNode().getStyleClass().contains(
                palette == TerminalColorScheme.FAST_LIGHT ? "terminal-context-light" : "terminal-context-dark"));
        if (!isLinux()) return;
        assertEquals(first.get(menu).getX(), menu.getAnchorX(), 0.51, "no horizontal post-map correction");
        assertEquals(first.get(menu).getY(), menu.getAnchorY(), 0.51, "no vertical post-map correction");
        var bounds = menu.getScene().getRoot().getLayoutBounds();
        var area = Screen.getPrimary().getVisualBounds();
        assertTrue(menu.getAnchorX() + bounds.getWidth() <= area.getMaxX() + 1);
        assertTrue(menu.getAnchorY() + bounds.getHeight() <= area.getMaxY() + 1);
    }

    private static boolean isLinux() { return System.getProperty("os.name").contains("Linux"); }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double x, double y, MouseButton button) {
        return new MouseEvent(type, 10, 10, x, y, button, 1,
                false, false, false, false, false, false, false, false, button == MouseButton.SECONDARY, true, null);
    }

    private static void onFx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); } });
        assertTrue(done.await(20, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
