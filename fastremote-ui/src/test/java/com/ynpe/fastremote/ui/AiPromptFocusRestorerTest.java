// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AiPromptFocusRestorerTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void firstOpenAndReopenSelectPromptEvenWhenToolbarHadFocus() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Stage window = new Stage();
            try {
                Button history = new Button("History");
                TextArea prompt = new TextArea("draft");
                VBox view = new VBox(history, prompt);
                Scene scene = new Scene(view, 400, 260);
                window.setScene(scene);
                try (AiPromptFocusRestorer restorer = new AiPromptFocusRestorer(prompt, view)) {
                    for (int opening = 0; opening < 2; opening++) {
                        history.requestFocus();
                        assertSame(history, scene.getFocusOwner());
                        restorer.request();
                        assertSame(prompt, scene.getFocusOwner());
                    }
                }
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                window.close();
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void alreadySelectedPromptGetsARealFocusOwnerTransition() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                TextArea prompt = new TextArea("draft");
                VBox view = new VBox(prompt);
                Scene scene = new Scene(view, 400, 260);
                prompt.requestFocus();
                assertSame(prompt, scene.getFocusOwner());
                AtomicInteger resetFocus = new AtomicInteger();
                scene.focusOwnerProperty().addListener((ignored, previous, focused) -> {
                    if (focused == view) resetFocus.incrementAndGet();
                });
                AiPromptFocusRestorer.restartCaret(prompt, view, scene);
                assertTrue(resetFocus.get() >= 1);
                assertSame(prompt, scene.getFocusOwner());
                assertFalse(view.isFocusTraversable());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }
}
