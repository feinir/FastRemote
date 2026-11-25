// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

final class UiFailureMessages {
    private UiFailureMessages() {}

    static String messageFor(Throwable error, String fallback) {
        Throwable cause = unwrap(error);
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? fallback : com.ynpe.fastremote.i18n.Texts.translateExternalText(message);
    }

    static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
