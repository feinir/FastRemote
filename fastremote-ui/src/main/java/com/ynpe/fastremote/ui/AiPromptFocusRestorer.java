// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

/** Restarts the TextArea caret after the one AI view moves between scenes or windows. */
final class AiPromptFocusRestorer implements AutoCloseable {
    private final TextArea prompt;
    private final Node focusReset;
    private Window pendingWindow;
    private ChangeListener<Boolean> pendingFocus;

    AiPromptFocusRestorer(TextArea prompt, Node focusReset) {
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.focusReset = Objects.requireNonNull(focusReset, "focusReset");
    }

    void request() {
        cancelPending();
        Scene scene = prompt.getScene();
        Window window = scene == null ? null : scene.getWindow();
        if (window == null) return;
        // Choose the editor before native activation can pick the first toolbar button.
        // The activation callback below then restarts its caret once the window is ready.
        prompt.requestFocus();
        if (window.isFocused()) {
            Platform.runLater(() -> restore(scene, window));
            return;
        }
        pendingWindow = window;
        pendingFocus = (ignored, previous, focused) -> {
            if (!focused) return;
            cancelPending();
            Platform.runLater(() -> restore(scene, window));
        };
        window.focusedProperty().addListener(pendingFocus);
    }

    private void restore(Scene scene, Window window) {
        if (prompt.getScene() != scene || scene.getWindow() != window || !window.isFocused()
                || !window.isShowing() || prompt.isDisabled()) return;
        restartCaret(prompt, focusReset, scene);
    }

    static void restartCaret(TextArea prompt, Node focusReset, Scene scene) {
        if (scene.getFocusOwner() == prompt) {
            // requestFocus on an already-focused TextArea is a no-op. A brief focus transition
            // makes its skin restart the native caret blink after reparenting the view.
            boolean traversable = focusReset.isFocusTraversable();
            focusReset.setFocusTraversable(true);
            focusReset.requestFocus();
            prompt.requestFocus();
            focusReset.setFocusTraversable(traversable);
        } else {
            prompt.requestFocus();
        }
    }

    private void cancelPending() {
        if (pendingWindow != null && pendingFocus != null) {
            pendingWindow.focusedProperty().removeListener(pendingFocus);
        }
        pendingWindow = null;
        pendingFocus = null;
    }

    @Override public void close() { cancelPending(); }
}
