// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

public final class SnippetTemplateException extends IllegalArgumentException {
    public SnippetTemplateException(String message) {
        super(message);
    }

    public SnippetTemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
