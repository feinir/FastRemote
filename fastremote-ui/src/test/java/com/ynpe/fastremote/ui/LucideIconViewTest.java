// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.Test;

class LucideIconViewTest {
    @Test
    void vaultUnlockImmediatelyKeepsKeyInteriorTransparentWithoutAnotherCssPass() {
        var icon = new LucideIconView(LucideIconView.Icon.LOCK_KEYHOLE);
        var path = (SVGPath) icon.getChildren().getFirst();
        assertEquals(Color.TRANSPARENT, path.getFill());
        // Automatic unlock changes the icon after the initial CSS pass; no hover/click follows.
        icon.setIcon(LucideIconView.Icon.KEY_ROUND);
        assertEquals(Color.TRANSPARENT, path.getFill());
        assertFalse(path.fillProperty().isBound());
    }

    @Test
    void switchingFilledAndStrokeIconsResetsFillWithoutLosingFilledIconColor() {
        var icon = new LucideIconView();
        var path = (SVGPath) icon.getChildren().getFirst();
        assertEquals(Color.TRANSPARENT, path.getFill());
        for (var filled : new LucideIconView.Icon[] {LucideIconView.Icon.AI,
                LucideIconView.Icon.VARIABLE, LucideIconView.Icon.CHAT_NEW}) {
            icon.setIcon(filled);
            path.setStroke(Color.BLUE);
            assertTrue(path.fillProperty().isBound());
            assertEquals(Color.BLUE, path.getFill());
            icon.setIcon(LucideIconView.Icon.KEY_ROUND);
            assertFalse(path.fillProperty().isBound());
            assertEquals(Color.TRANSPARENT, path.getFill());
        }
    }
}
