// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.TextInputControl;

/** Associates local validation failures with the control that can correct them. */
final class FormValidation {
    private FormValidation() { }

    static void focus(Node control) {
        if (control instanceof TextInputControl input) DialogSpacing.focusAtEnd(input);
        else if (control instanceof RevealablePasswordEditor password) password.focusInput();
        else control.requestFocus();
    }

    static void require(Node control, boolean valid, String message) {
        if (!valid) {
            focus(control);
            throw new IllegalArgumentException(message);
        }
    }

    static <T> T value(Node control, Supplier<T> validation) {
        try { return validation.get(); }
        catch (IllegalArgumentException failure) {
            focus(control);
            throw failure;
        }
    }
}
