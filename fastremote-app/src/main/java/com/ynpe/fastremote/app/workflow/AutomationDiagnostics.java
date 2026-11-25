// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import java.util.concurrent.CompletionException;
final class AutomationDiagnostics {
    static String auditTarget(SavedHost host) {
        return host == null
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.63d6b47116de363c")
                : host.name();
    }
    static String sanitizedMessage(Throwable failure) {
        Throwable value = failure;
        while ((value instanceof CompletionException || value instanceof java.util.concurrent.ExecutionException)
                && value.getCause() != null) value = value.getCause();
        String message = value.getMessage();
        return message == null || message.isBlank() ? value.getClass().getSimpleName() : message;
    }
}
