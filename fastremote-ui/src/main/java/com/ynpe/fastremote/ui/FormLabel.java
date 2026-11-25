// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.text.BreakIterator;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.text.TextAlignment;

/** English-baseline form label that wraps longer localized text within the shared column. */
final class FormLabel extends Label {
    private static final char SOFT_WRAP = '\u200B';
    static final double FORM_FONT_SIZE = 13.0;
    static final double TWO_LINE_HEIGHT = 48.0;
    static final double COMPACT_TWO_LINE_HEIGHT = 48.0;
    /** Compact English baseline; longer translations wrap within the shared two-line column. */
    static final double ENGLISH_BASELINE_WIDTH = 85.0;
    private final String rawText;

    FormLabel(String text, double width, double height) {
        super(withSoftWrapOpportunities(Objects.requireNonNull(text, "text")));
        rawText = text;
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Form label size must be positive");
        getStyleClass().add("form-label");
        setAlignment(rowAlignment());
        setTextAlignment(TextAlignment.LEFT);
        setWrapText(true);
        setAccessibleText(text);
        setFocusTraversable(false);
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
    }

    String text() {
        return rawText;
    }

    static String withSoftWrapOpportunities(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) return text;
        BreakIterator lines = BreakIterator.getLineInstance(com.ynpe.fastremote.i18n.Texts.localizer().locale());
        lines.setText(text);
        StringBuilder result = new StringBuilder(text.length() + 8);
        int start = lines.first();
        for (int end = lines.next(); end != BreakIterator.DONE; start = end, end = lines.next()) {
            result.append(text, start, end);
            if (end < text.length()
                    && !Character.isWhitespace(text.charAt(end - 1))
                    && !Character.isWhitespace(text.charAt(end))) {
                result.append(SOFT_WRAP);
            }
        }
        return result.toString();
    }

    static Pos rowAlignment() {
        return Pos.CENTER_LEFT;
    }

    static double naturalWidthForCharacters(int characterCount) {
        if (characterCount < 1) throw new IllegalArgumentException("Character count must be positive");
        return Math.max(ENGLISH_BASELINE_WIDTH, FORM_FONT_SIZE * characterCount);
    }
}
