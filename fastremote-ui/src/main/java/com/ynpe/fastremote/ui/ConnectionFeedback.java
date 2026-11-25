// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import javafx.scene.control.Label;

/** Shared inline feedback presentation for connection forms. */
final class ConnectionFeedback {
    static final double RESERVED_HEIGHT = 34.0;
    private static final Object RESERVED_SPACE_KEY = new Object();
    private static final String BASE_STYLE = "connection-feedback";
    private static final String WORKING_STYLE = "connection-feedback-working";
    private static final String SUCCESS_STYLE = "connection-feedback-success";
    private static final String ERROR_STYLE = "connection-feedback-error";

    private ConnectionFeedback() {}

    static Label label() {
        Label label = new Label();
        configure(label);
        return label;
    }

    static Label reservedLabel() {
        Label label = label();
        label.getProperties().put(RESERVED_SPACE_KEY, Boolean.TRUE);
        label.setMinHeight(RESERVED_HEIGHT);
        clear(label);
        return label;
    }

    static void configure(Label label) {
        Objects.requireNonNull(label, "label");
        if (!label.getStyleClass().contains(BASE_STYLE)) label.getStyleClass().add(BASE_STYLE);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        clear(label);
    }

    static void show(Label label, String message, Tone tone) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(tone, "tone");
        if (message == null || message.isBlank()) {
            clear(label);
            return;
        }
        label.getStyleClass().removeAll(WORKING_STYLE, SUCCESS_STYLE, ERROR_STYLE);
        label.getStyleClass().add(switch (tone) {
            case WORKING -> WORKING_STYLE;
            case SUCCESS -> SUCCESS_STYLE;
            case ERROR -> ERROR_STYLE;
        });
        label.setText(Objects.requireNonNullElse(message, ""));
        label.setVisible(true);
        label.setManaged(true);
    }

    static void clear(Label label) {
        Objects.requireNonNull(label, "label");
        label.getStyleClass().removeAll(WORKING_STYLE, SUCCESS_STYLE, ERROR_STYLE);
        label.setText("");
        boolean reserved = Boolean.TRUE.equals(label.getProperties().get(RESERVED_SPACE_KEY));
        label.setVisible(false);
        label.setManaged(reserved);
    }

    enum Tone { WORKING, SUCCESS, ERROR }
}
