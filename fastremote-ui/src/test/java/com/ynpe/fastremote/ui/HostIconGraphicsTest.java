// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.catalog.HostIcon;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.Test;

class HostIconGraphicsTest {
    @Test
    void providesDistinctVectorGeometryForEveryBuiltInHostType() {
        assertEquals(15, HostIcon.values().length);
        for (HostIcon icon : HostIcon.values()) {
            assertFalse(HostIconGraphics.path(icon).isBlank(), icon.name());
        }
        assertNotEquals(HostIconGraphics.path(HostIcon.LINUX), HostIconGraphics.path(HostIcon.SERVER));
        assertNotEquals(HostIconGraphics.path(HostIcon.WINDOWS), HostIconGraphics.path(HostIcon.FREEBSD));

        for (LucideIconView.Icon icon : LucideIconView.Icon.values()) {
            String geometry = LucideIconPaths.path(icon);
            SVGPath path = new SVGPath();
            path.setContent(geometry);
            assertTrue(path.getBoundsInLocal().getMinX() >= -0.01, icon.name());
            assertTrue(path.getBoundsInLocal().getMinY() >= -0.01, icon.name());
            assertTrue(path.getBoundsInLocal().getMaxX() <= 24.01, icon.name());
            assertTrue(path.getBoundsInLocal().getMaxY() <= 24.01, icon.name());
        }
    }
}
