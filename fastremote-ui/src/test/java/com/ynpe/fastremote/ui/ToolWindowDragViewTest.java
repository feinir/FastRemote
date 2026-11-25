// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ToolWindowDragViewTest {
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
    void dragViewUsesTheVisibleToolIconInsteadOfATransparentPixel() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Image> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                Button button = new Button();
                button.setGraphic(new LucideIconView(LucideIconView.Icon.SERVER));
                StackPane root = new StackPane(button);
                stage.setScene(new Scene(root, 80.0, 80.0));
                stage.show();
                root.applyCss();
                root.layout();
                result.set(MainWindowController.toolWindowDragView(button));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                stage.hide();
                completed.countDown();
            }
        });

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
        Image image = result.get();
        assertTrue(image.getWidth() >= 20.0);
        assertTrue(image.getHeight() >= 20.0);
        boolean visiblePixel = false;
        for (int y = 0; y < (int) image.getHeight() && !visiblePixel; y++) {
            for (int x = 0; x < (int) image.getWidth(); x++) {
                if (image.getPixelReader().getColor(x, y).getOpacity() > 0.0) {
                    visiblePixel = true;
                    break;
                }
            }
        }
        assertTrue(visiblePixel, "drag view must contain a visible icon pixel");
    }
}
