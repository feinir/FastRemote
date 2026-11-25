// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ToolbarButtonCssTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                started.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    @Test
    void genericButtonRuleCannotRestoreVerticalPaddingInTheMainToolbar() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                StackPane iconFrame = new StackPane();
                iconFrame.getStyleClass().add("toolbar-icon-frame");
                Button action = new Button("Quick Connect", iconFrame);
                action.getStyleClass().add("toolbar-action-button");
                ToolBar toolbar = new ToolBar(action);
                toolbar.getStyleClass().add("main-toolbar");
                StackPane root = new StackPane(toolbar);
                Scene scene = new Scene(root, 400.0, 80.0);
                scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
                stage.setScene(scene);
                stage.show();
                root.applyCss();
                root.layout();

                assertEquals(0.0, action.getPadding().getTop());
                assertEquals(0.0, action.getPadding().getBottom());
                assertEquals(24.0, action.getMinHeight());
                assertTrue(action.prefHeight(-1) <= 26.0,
                        "toolbar text button must stay as compact as the icon-only buttons");
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                stage.hide();
                completed.countDown();
            }
        });

        assertTrue(completed.await(10, TimeUnit.SECONDS));
        assertNull(failure.get(), () -> String.valueOf(failure.get()));
    }

    @Test
    void iconOnlyToolbarButtonRemainsCompactAndIconOnlyInsideAnOverflowPopup() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                Button action = new Button("Settings", new StackPane());
                action.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                action.getStyleClass().add("toolbar-icon-button");
                StackPane popup = new StackPane(action);
                popup.getStyleClass().add("context-menu");
                Scene scene = new Scene(popup, 260.0, 80.0);
                scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
                stage.setScene(scene);
                stage.show();
                popup.applyCss();
                popup.layout();

                assertEquals(ContentDisplay.GRAPHIC_ONLY, action.getContentDisplay());
                assertEquals("Settings", action.getText());
                assertEquals(32.0, action.prefWidth(-1));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                stage.hide();
                completed.countDown();
            }
        });

        assertTrue(completed.await(10, TimeUnit.SECONDS));
        assertNull(failure.get(), () -> String.valueOf(failure.get()));
    }
}
