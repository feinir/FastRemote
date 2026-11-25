// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.geometry.BoundingBox;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

class SearchableSelectorPopupLayoutTest {
    private static final Rectangle2D SCREEN = new Rectangle2D(0, 0, 1200, 900);

    @Test
    void opensUpwardWhenThePopupDoesNotFitBelowAndAboveHasMoreSpace() {
        assertTrue(SearchableSelectorPopupLayout.opensUpward(
                new BoundingBox(100, 760, 300, 34), SCREEN, 320));
    }

    @Test
    void remainsBelowWhenBelowHasEnoughSpaceOrMoreSpaceThanAbove() {
        assertFalse(SearchableSelectorPopupLayout.opensUpward(
                new BoundingBox(100, 400, 300, 34), SCREEN, 320));
        assertFalse(SearchableSelectorPopupLayout.opensUpward(
                new BoundingBox(100, 100, 300, 34), SCREEN, 800));
    }
}
