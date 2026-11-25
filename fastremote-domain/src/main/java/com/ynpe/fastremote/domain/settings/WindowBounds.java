// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

/** Last normal main-window rectangle in logical JavaFX screen coordinates. */
public record WindowBounds(double x, double y, double width, double height) {
    public WindowBounds {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("window position must be finite");
        }
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0.0 || height <= 0.0) {
            throw new IllegalArgumentException("window size must be positive and finite");
        }
    }
}
