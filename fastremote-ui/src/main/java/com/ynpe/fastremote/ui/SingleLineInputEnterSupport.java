// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/** Keeps Enter owned by a configuration-center single-line input instead of its default button. */
final class SingleLineInputEnterSupport {
    private SingleLineInputEnterSupport() { }

    static void consume(TextInputControl input) {
        runOnEnter(input, () -> { });
    }

    static void runOnEnter(TextInputControl input, Runnable action) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(action, "action");
        InputMethodEnterState enterState = InputMethodEnterState.track(input);
        input.addEventFilter(KeyEvent.KEY_PRESSED, event -> consumeEnter(event));
        input.addEventFilter(KeyEvent.KEY_RELEASED, event -> handleEnterRelease(
                event, enterState.inputMethodOwnsKeyRelease(event.getCode()), action));
    }

    static <T> void commitSpinnerOnEnter(Spinner<T> spinner) {
        Objects.requireNonNull(spinner, "spinner");
        runOnEnter(spinner.getEditor(), () -> commitSpinnerEditor(spinner));
    }

    static boolean consumeEnter(KeyEvent event) {
        if (event.getCode() != KeyCode.ENTER) return false;
        event.consume();
        return true;
    }

    static boolean handleEnterRelease(
            KeyEvent event, boolean inputMethodOwnsEnter, Runnable action) {
        if (event.getCode() != KeyCode.ENTER) return false;
        event.consume();
        if (!inputMethodOwnsEnter) action.run();
        return !inputMethodOwnsEnter;
    }

    private static <T> void commitSpinnerEditor(Spinner<T> spinner) {
        var factory = spinner.getValueFactory();
        if (factory == null || factory.getConverter() == null) return;
        try {
            factory.setValue(factory.getConverter().fromString(spinner.getEditor().getText()));
        } catch (RuntimeException invalid) {
            // Match a normal Spinner commit: reject invalid editor text and restore its value.
        }
        spinner.getEditor().setText(factory.getConverter().toString(factory.getValue()));
    }
}
