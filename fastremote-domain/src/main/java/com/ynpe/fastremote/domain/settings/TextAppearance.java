// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

/** Extra spacing in logical pixels, rounded to tenths at the settings boundary. */
public record TextAppearance(double lineSpacing, double letterSpacing) {
    public static final double MIN_LINE_SPACING = -15.0;
    public static final double MAX_LINE_SPACING = 15.0;
    public static final double MIN_LETTER_SPACING = -5.0;
    public static final double MAX_LETTER_SPACING = 5.0;

    public TextAppearance {
        lineSpacing = checkedTenth(lineSpacing, MIN_LINE_SPACING, MAX_LINE_SPACING);
        letterSpacing = checkedTenth(letterSpacing, MIN_LETTER_SPACING, MAX_LETTER_SPACING);
    }

    private static double checkedTenth(double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Spacing outside supported range");
        }
        return Math.round(value * 10) / 10.0;
    }

    public static TextAppearance terminalDefaults() { return new TextAppearance(0, 0); }
    public static TextAppearance editorDefaults() { return new TextAppearance(1, 0); }
}
