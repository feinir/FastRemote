// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.ynpe.fastremote.domain.settings.GlobalSettings;
import com.ynpe.fastremote.domain.settings.WindowBounds;
import com.ynpe.fastremote.domain.settings.WindowSettings;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs({OS.MAC, OS.WINDOWS})
class MainWindowBoundsStartupTest {
    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test void savedBoundsAreAppliedBeforeFirstShowAndAreNotReappliedAfterwards() throws Exception {
        fx(() -> {
            Stage stage = window();
            try {
                var screen = Screen.getPrimary().getVisualBounds();
                WindowBounds saved = new WindowBounds(screen.getMinX() + 30, screen.getMinY() + 30, 800, 500);
                MainWindowController controller = controller(stage, WindowSettings.defaults().withLastBounds(saved));
                restore(controller);
                assertBounds(saved, stage);
                assertFalse(stage.isShowing());
                stage.addEventHandler(WindowEvent.WINDOW_SHOWING, event -> assertBounds(saved, stage));
                stage.show();
                assertBounds(saved, stage);
                // A later shown callback must not overwrite a user's subsequent resize.
                stage.setWidth(850);
                restore(controller);
                assertEquals(850, stage.getWidth(), 0.01);
            } finally { stage.close(); }
        });
    }

    @Test void settingsMustBeLoadedBeforeRestorationIsConsumed() throws Exception {
        fx(() -> {
            Stage stage = window();
            try {
                var screen = Screen.getPrimary().getVisualBounds();
                WindowBounds saved = new WindowBounds(screen.getMinX() + 30, screen.getMinY() + 30, 800, 500);
                MainWindowController controller = controller(stage, WindowSettings.defaults().withLastBounds(saved));
                set(controller, "workspaceSettingsLoaded", false);
                restore(controller);
                assertEquals(1420, stage.getWidth());
                set(controller, "workspaceSettingsLoaded", true);
                restore(controller);
                assertBounds(saved, stage);
            } finally { stage.close(); }
        });
    }

    @Test void missingOrDisabledSavedBoundsKeepDefaultSize() throws Exception {
        fx(() -> {
            for (WindowSettings settings : new WindowSettings[] {WindowSettings.defaults(),
                    new WindowSettings(false, false, Optional.of(new WindowBounds(30, 30, 800, 500)))}) {
                Stage stage = window();
                try {
                    restore(controller(stage, settings));
                    assertEquals(1420, stage.getWidth());
                    assertEquals(860, stage.getHeight());
                } finally { stage.close(); }
            }
        });
    }

    private static Stage window() {
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane()));
        stage.setMinWidth(540); stage.setMinHeight(340);
        stage.setWidth(1420); stage.setHeight(860);
        return stage;
    }

    private static MainWindowController controller(Stage stage, WindowSettings settings) throws Exception {
        var controller = new MainWindowController(unused(ConnectionWorkflow.class), unused(HostCatalogWorkflow.class),
                unused(SettingsWorkflow.class), unused(SnippetWorkflow.class));
        set(controller, "mainWindow", stage);
        set(controller, "persistedWorkspaceSettings", GlobalSettings.defaults().withWindowSettings(settings));
        set(controller, "workspaceSettingsLoaded", true);
        return controller;
    }

    private static void assertBounds(WindowBounds expected, Stage stage) {
        // Native windows round to physical pixels after showing, notably at fractional Windows DPI.
        // Keep the pre-show assertion exact enough to catch the original deferred-restoration bug.
        double toleranceX = stage.isShowing() ? 1.0 / stage.getOutputScaleX() + 0.001 : 0.01;
        double toleranceY = stage.isShowing() ? 1.0 / stage.getOutputScaleY() + 0.001 : 0.01;
        assertEquals(expected.x(), stage.getX(), toleranceX);
        assertEquals(expected.y(), stage.getY(), toleranceY);
        assertEquals(expected.width(), stage.getWidth(), toleranceX);
        assertEquals(expected.height(), stage.getHeight(), toleranceY);
    }

    private static void restore(MainWindowController controller) throws Exception {
        var method = MainWindowController.class.getDeclaredMethod("restoreMainWindowBounds");
        method.setAccessible(true); method.invoke(controller);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }

    private static <T> T unused(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> { throw new AssertionError(method.getName()); }));
    }

    private interface FxAction { void run() throws Exception; }
    private static void fx(FxAction action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task); task.get(10, TimeUnit.SECONDS);
    }
}
