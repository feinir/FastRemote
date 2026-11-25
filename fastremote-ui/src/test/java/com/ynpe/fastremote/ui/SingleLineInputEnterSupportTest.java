// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SingleLineInputEnterSupportTest {
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
    void enterPressAndReleaseStayInsideSingleLineInputAndRunActionOnce() throws Exception {
        runOnFxThread(() -> {
            TextField input = new TextField();
            VBox dialogContent = new VBox(input);
            AtomicInteger actions = new AtomicInteger();
            AtomicInteger leakedEvents = new AtomicInteger();
            SingleLineInputEnterSupport.runOnEnter(input, actions::incrementAndGet);
            dialogContent.addEventHandler(KeyEvent.ANY, event -> {
                if (event.getCode() == KeyCode.ENTER) leakedEvents.incrementAndGet();
            });

            Event.fireEvent(input, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.ENTER));
            Event.fireEvent(input, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.ENTER));

            assertEquals(1, actions.get());
            assertEquals(0, leakedEvents.get());
        });
    }

    @Test
    void inputMethodEnterIsConsumedWithoutRunningTheFieldAction() {
        AtomicInteger actions = new AtomicInteger();
        KeyEvent enter = keyEvent(KeyEvent.KEY_RELEASED, KeyCode.ENTER);

        assertFalse(SingleLineInputEnterSupport.handleEnterRelease(
                enter, true, actions::incrementAndGet));
        assertTrue(enter.isConsumed());
        assertEquals(0, actions.get());
    }

    @Test
    void editableSpinnerCommitsItsTextWithoutClosingTheDialog() throws Exception {
        runOnFxThread(() -> {
            Spinner<Integer> spinner = new Spinner<>(1, 100, 5);
            spinner.setEditable(true);
            VBox dialogContent = new VBox(spinner);
            AtomicInteger leakedEvents = new AtomicInteger();
            SingleLineInputEnterSupport.commitSpinnerOnEnter(spinner);
            dialogContent.addEventHandler(KeyEvent.ANY, event -> {
                if (event.getCode() == KeyCode.ENTER) leakedEvents.incrementAndGet();
            });
            spinner.getEditor().setText("42");

            Event.fireEvent(spinner.getEditor(), keyEvent(KeyEvent.KEY_PRESSED, KeyCode.ENTER));
            Event.fireEvent(spinner.getEditor(), keyEvent(KeyEvent.KEY_RELEASED, KeyCode.ENTER));

            assertEquals(42, spinner.getValue());
            assertEquals("42", spinner.getEditor().getText());
            assertEquals(0, leakedEvents.get());
        });
    }

    private static void runOnFxThread(Runnable assertion) throws Exception {
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
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    private static KeyEvent keyEvent(javafx.event.EventType<KeyEvent> type, KeyCode code) {
        return new KeyEvent(type, "", "", code, false, false, false, false);
    }
}
