// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import org.junit.jupiter.api.Test;

class MacNativeTrayTest {
    @Test
    void nsSizeMatchesTheSixtyFourBitAppKitAbi() {
        assertEquals(16, new MacNativeTray.NSSize().size());
    }

    @Test
    void mapsEveryNativeMenuTagToTheRequestedAction() {
        assertEquals(MacNativeTray.MenuCommand.SHOW_WINDOW, MacNativeTray.menuCommand(1));
        assertEquals(MacNativeTray.MenuCommand.QUICK_CONNECT, MacNativeTray.menuCommand(2));
        assertEquals(MacNativeTray.MenuCommand.LOCAL_TERMINAL, MacNativeTray.menuCommand(3));
        assertEquals(MacNativeTray.MenuCommand.EXIT, MacNativeTray.menuCommand(4));
        assertEquals(MacNativeTray.MenuCommand.NONE, MacNativeTray.menuCommand(0));
    }

    @Test
    void shipsAStandalonePngTemplateForTheMenuBar() throws IOException {
        try (InputStream input = MacNativeTray.class.getResourceAsStream("icons/logo-menubar-template.png")) {
            assertNotNull(input);
            Image image = new Image(input);
            assertEquals(36, Math.round(image.getWidth()));
            assertEquals(36, Math.round(image.getHeight()));
            PixelReader pixels = image.getPixelReader();
            assertNotNull(pixels);
            assertEquals(0.0, pixels.getColor(0, 0).getOpacity());
            assertTrue(pixels.getColor(2, 18).getOpacity() > 0.0);
            assertTrue(pixels.getColor(33, 18).getOpacity() > 0.0);
        }
    }
}
