// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

/** Keeps an IME-owned Enter from becoming a control action after composition commits. */
final class InputMethodEnterState {
    private boolean inputMethodOwnsEnter;
    private boolean committedTextAwaitingPhysicalKey;
    private boolean enterDown;

    static InputMethodEnterState track(TextInputControl input) {
        Objects.requireNonNull(input, "input");
        InputMethodEnterState state = new InputMethodEnterState();
        // TextInputControl's skin can consume an input-method event while applying committed
        // text. Observe it in the capturing phase so the action guard always sees it first.
        input.addEventFilter(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, event ->
                state.inputMethodTextChanged(event.getCommitted()));
        // Once JavaFX has delivered committed text, the following physical Enter belongs to the
        // input method. Consume its press before TextField's built-in behavior can turn it into an
        // ActionEvent (for example, selecting a search result or firing a dialog default button).
        input.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (state.keyPressed(event.getCode())) event.consume();
        });
        input.addEventFilter(KeyEvent.KEY_RELEASED, event -> state.keyReleased(event.getCode()));
        input.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> state.pointerPressed());
        input.focusedProperty().addListener((ignored, previous, focused) -> {
            if (!focused) state.reset();
        });
        return state;
    }

    void inputMethodTextChanged(String committedText) {
        String committed = Objects.requireNonNullElse(committedText, "");
        // Some native input methods commit raw Latin text before JavaFX reports the physical
        // Enter that confirmed it. Only that observed ordering is associated with the next key.
        if (!enterDown && !committed.isEmpty()) committedTextAwaitingPhysicalKey = true;
    }

    boolean keyPressed(KeyCode code) {
        if (code != KeyCode.ENTER) {
            reset();
            return false;
        }
        enterDown = true;
        inputMethodOwnsEnter = committedTextAwaitingPhysicalKey;
        committedTextAwaitingPhysicalKey = false;
        return inputMethodOwnsEnter;
    }

    void keyReleased(KeyCode code) {
        if (code != KeyCode.ENTER) committedTextAwaitingPhysicalKey = false;
    }

    void pointerPressed() {
        committedTextAwaitingPhysicalKey = false;
    }

    boolean inputMethodOwnsKeyRelease(KeyCode code) {
        if (code != KeyCode.ENTER) return false;
        boolean owned = inputMethodOwnsEnter || committedTextAwaitingPhysicalKey;
        enterDown = false;
        inputMethodOwnsEnter = false;
        committedTextAwaitingPhysicalKey = false;
        return owned;
    }

    void reset() {
        inputMethodOwnsEnter = false;
        committedTextAwaitingPhysicalKey = false;
        enterDown = false;
    }
}
