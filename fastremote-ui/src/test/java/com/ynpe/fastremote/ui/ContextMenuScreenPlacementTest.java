// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ContextMenuScreenPlacementTest {
    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void fitsEdgesIncludingNegativeMonitorCoordinatesAndOversizedMenus() {
        assertEquals(100, ContextMenuScreenPlacement.fit(100, 200, 0, 1000, false));
        assertEquals(800, ContextMenuScreenPlacement.fit(950, 200, 0, 1000, false));
        assertEquals(-200, ContextMenuScreenPlacement.fit(-20, 200, -1920, 0, false));
        assertEquals(0, ContextMenuScreenPlacement.fit(950, 1200, 0, 1000, false));
        assertEquals(200, ContextMenuScreenPlacement.fit(50, 200, 0, 1000, true));
        assertEquals(1000, ContextMenuScreenPlacement.fit(50, 1200, 0, 1000, true));
    }

    @Test
    void leftOpeningSubmenuUsesOwnerScaleBeforeTheSkinSnapshotsItsSize() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(LocalizedTextInputContextMenus.isLinux());
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            ContextMenu menu = ApplicationContextMenus.create();
            ContextMenu childPopup = new ContextMenu();
            try {
                StackPane owner = new StackPane();
                owner.getStylesheets().add(MainView.stylesheet().toExternalForm());
                stage.setScene(new Scene(owner, 300, 200)); stage.show();
                menu.getItems().add(new MenuItem("Parent"));
                var area = Screen.getPrimary().getVisualBounds();
                menu.show(owner, area.getMaxX() - 80, area.getMinY() + 40);
                for (int i = 0; i < 18; i++) childPopup.getItems().add(new MenuItem("测试菜单 / Menu item " + i));
                childPopup.getStyleClass().add(ApplicationContextMenus.STYLE_CLASS);
                childPopup.setStyle(menu.getStyle());
                ContextMenuScreenPlacement.install(childPopup);
                javafx.scene.Node anchor = menu.getSkin().getNode().lookup(".menu-item");
                ThemeManager.prepareContextMenuBeforeShow(childPopup, anchor);
                AtomicReference<Point2D> first = new AtomicReference<>();
                childPopup.addEventHandler(WindowEvent.WINDOW_SHOWN, ignored ->
                        first.set(new Point2D(childPopup.getAnchorX(), childPopup.getAnchorY())));
                childPopup.show(anchor, javafx.geometry.Side.RIGHT, 0, 0);
                assertTrue(childPopup.getAnchorX() < menu.getAnchorX(), "submenu must flip to the left");
                assertEquals(first.get().getX(), childPopup.getAnchorX(), 0.01,
                        "ContextMenuSkin must not shift the visible popup after HiDPI sizing");
                assertEquals(first.get().getY(), childPopup.getAnchorY(), 0.01);
            } catch (Throwable thrown) { failure.set(thrown); }
            finally { childPopup.hide(); menu.hide(); stage.close(); done.countDown(); }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test
    void nativeFirstVisiblePositionAlreadyFitsAndDoesNotMoveDuringAutoFix() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            ContextMenu menu = ApplicationContextMenus.create();
            try {
                StackPane owner = new StackPane();
                owner.getStylesheets().add(MainView.stylesheet().toExternalForm());
                stage.setScene(new Scene(owner, 300, 200));
                stage.show();
                // Exercise the Linux policy on every test platform without changing os.name.
                ContextMenuScreenPlacement.install(menu);
                var area = Screen.getPrimary().getVisualBounds();
                AtomicReference<Point2D> firstVisible = new AtomicReference<>();
                menu.addEventHandler(WindowEvent.WINDOW_SHOWN, ignored ->
                        firstVisible.set(new Point2D(menu.getAnchorX(), menu.getAnchorY())));
                for (int count : new int[]{3, 18, 60, 3}) {
                    menu.getItems().clear();
                    for (int i = 0; i < count; i++) menu.getItems().add(new MenuItem("Test menu item " + i));
                    for (boolean nearEdge : new boolean[]{false, true}) {
                        double x = nearEdge ? area.getMaxX() - 12 : area.getMinX() + 30;
                        double y = nearEdge ? area.getMaxY() - 12 : area.getMinY() + 30;
                        menu.show(owner, x, y);
                        assertNotNull(firstVisible.get());
                        // Native HiDPI peers may round the location to a physical pixel.
                        assertEquals(firstVisible.get().getX(), menu.getAnchorX(), 1.0);
                        assertEquals(firstVisible.get().getY(), menu.getAnchorY(), 1.0);
                        var content = menu.getScene().getRoot().getLayoutBounds();
                        assertTrue(menu.getAnchorX() >= area.getMinX() - 0.01);
                        assertTrue(menu.getAnchorY() >= area.getMinY() - 0.01);
                        assertTrue(menu.getAnchorY() + content.getHeight() <= area.getMaxY() + 1.0);
                        if (count == 3 && !nearEdge) {
                            assertEquals(x, menu.getAnchorX(), 1.0);
                            assertEquals(y, menu.getAnchorY(), 1.0);
                        }
                        assertTrue(menu.isAutoFix(), "Keep native autoFix for later screen changes");
                        menu.hide();
                    }
                }
            } catch (Throwable thrown) { failure.set(thrown); }
            finally { menu.hide(); stage.close(); done.countDown(); }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
