// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DialogPlacementTest {
    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test void centersAndClampsOnSecondaryScreensIncludingNegativeCoordinates() {
        var screen = new Rectangle2D(-1920, 25, 1920, 1055);
        var owner = new Rectangle2D(-1600, 100, 1000, 800);
        assertEquals(new javafx.geometry.Point2D(-1300, 350),
                DialogSpacing.centeredPosition(owner, screen, 400, 300));
        var edge = new Rectangle2D(-2000, -100, 300, 200);
        assertEquals(new javafx.geometry.Point2D(-1920, 25),
                DialogSpacing.centeredPosition(edge, screen, 400, 300));
        assertEquals(new javafx.geometry.Point2D(-1920, 25),
                DialogSpacing.centeredPosition(owner, screen, 2100, 1200));
    }

    @Test void fixedAndResizableDialogsCenterAfterNativeGeometrySettles() throws Exception {
        for (boolean resizable : new boolean[]{false, true}) {
            Stage owner = owner();
            Dialog<Void> dialog = fx(() -> {
                Dialog<Void> d = new Dialog<>();
                d.initOwner(owner);
                d.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
                d.getDialogPane().setContent(new TextField("credential"));
                DialogSpacing.prepare(d);
                if (resizable) DialogSpacing.configureResizableWindowGeometry(d, 400, 250, 300, 180);
                d.show();
                return d;
            });
            try {
                settle();
                fx(() -> {
                    var window = dialog.getDialogPane().getScene().getWindow();
                    assertEquals(owner.getX() + owner.getWidth() / 2, window.getX() + window.getWidth() / 2, 2);
                    assertEquals(owner.getY() + owner.getHeight() / 2, window.getY() + window.getHeight() / 2, 2);
                    assertEquals(1, window.getOpacity());
                    return null;
                });
            } finally { fx(() -> { dialog.close(); owner.close(); return null; }); }
        }
    }

    @Test void standaloneDialogStagesUseTheSamePlacement() throws Exception {
        Stage owner = owner();
        Stage dialog = fx(() -> {
            Stage d = new Stage();
            d.initOwner(owner);
            d.setScene(new Scene(new StackPane(new Label("progress")), 300, 160));
            DialogSpacing.centerOnOwnerWhenShown(d);
            d.show();
            return d;
        });
        try {
            settle();
            fx(() -> {
                assertEquals(owner.getX() + owner.getWidth() / 2, dialog.getX() + dialog.getWidth() / 2, 2);
                assertEquals(owner.getY() + owner.getHeight() / 2, dialog.getY() + dialog.getHeight() / 2, 2);
                return null;
            });
        } finally { fx(() -> { dialog.close(); owner.close(); return null; }); }
    }

    private static Stage owner() throws Exception {
        return fx(() -> {
            var bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 750, 550));
            stage.setX(bounds.getMinX() + 80);
            stage.setY(bounds.getMinY() + 60);
            stage.show();
            return stage;
        });
    }
    private static void settle() throws Exception {
        CountDownLatch settled = new CountDownLatch(1);
        fx(() -> {
            PauseTransition pause = new PauseTransition(Duration.millis(180));
            pause.setOnFinished(event -> settled.countDown());
            pause.play();
            return null;
        });
        assertTrue(settled.await(5, TimeUnit.SECONDS));
    }
    private static <T> T fx(Callable<T> task) throws Exception {
        FutureTask<T> future = new FutureTask<>(task);
        Platform.runLater(future);
        return future.get(5, TimeUnit.SECONDS);
    }
}
