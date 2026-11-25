// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

class InputMethodEnterStateTest {
    @Test
    void commitAfterEnterPressDoesNotRetroactivelyClaimThatEnter() {
        InputMethodEnterState state = new InputMethodEnterState();

        state.inputMethodTextChanged("");
        assertFalse(state.keyPressed(KeyCode.ENTER));
        state.inputMethodTextChanged("raw-English");

        assertFalse(state.inputMethodOwnsKeyRelease(KeyCode.ENTER));
    }

    @Test
    void commitDeliveredBeforeEnterPressOwnsThatEnterOnEveryPlatform() {
        InputMethodEnterState state = new InputMethodEnterState();

        state.inputMethodTextChanged("");
        state.inputMethodTextChanged("raw-English");
        assertTrue(state.keyPressed(KeyCode.ENTER));

        assertTrue(state.inputMethodOwnsKeyRelease(KeyCode.ENTER));
    }

    @Test
    void nonEnterKeyAfterImeCommitLeavesFollowingEnterAvailableToTheControl() {
        InputMethodEnterState state = new InputMethodEnterState();

        state.inputMethodTextChanged("中文");
        state.keyPressed(KeyCode.SPACE);
        state.keyReleased(KeyCode.SPACE);
        state.keyPressed(KeyCode.ENTER);

        assertFalse(state.inputMethodOwnsKeyRelease(KeyCode.ENTER));
    }

    @Test
    void pointerInteractionAfterImeCommitLeavesFollowingEnterAvailableToTheControl() {
        InputMethodEnterState state = new InputMethodEnterState();

        state.inputMethodTextChanged("中文");
        state.pointerPressed();
        state.keyPressed(KeyCode.ENTER);

        assertFalse(state.inputMethodOwnsKeyRelease(KeyCode.ENTER));
    }

    @Test
    void ordinaryEnterWithoutImeActivityRunsTheControlAction() {
        InputMethodEnterState state = new InputMethodEnterState();

        state.keyPressed(KeyCode.ENTER);

        assertFalse(state.inputMethodOwnsKeyRelease(KeyCode.ENTER));
    }

    @Test
    void activeCompositionWithoutCommittedTextDoesNotClaimEnter() {
        InputMethodEnterState state = new InputMethodEnterState();

        state.inputMethodTextChanged("");
        state.keyPressed(KeyCode.ENTER);

        assertFalse(state.inputMethodOwnsKeyRelease(KeyCode.ENTER));
    }

    @Test
    void focusResetDropsEveryPendingImeFlag() {
        InputMethodEnterState state = new InputMethodEnterState();

        state.inputMethodTextChanged("");
        state.keyPressed(KeyCode.ENTER);
        state.reset();

        assertFalse(state.inputMethodOwnsKeyRelease(KeyCode.ENTER));
    }
}
