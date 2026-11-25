// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import javafx.stage.FileChooser;
import org.junit.jupiter.api.Test;

class ConfigurationFileChooserTest {
    @Test
    void macSavePanelReceivesBaseNameAndExplicitExtension() {
        FileChooser chooser = ConfigurationFileChooser.forExport("Export", "Configuration", true);
        assertEquals("fastremote-config", chooser.getInitialFileName());
        assertEquals(List.of("*.frc"), chooser.getSelectedExtensionFilter().getExtensions());
        assertSame(chooser.getExtensionFilters().getFirst(), chooser.getSelectedExtensionFilter());
    }

    @Test
    void otherPlatformsRetainExtensionInSuggestedNameAndPortalRequest() {
        FileChooser chooser = ConfigurationFileChooser.forExport("Export", "Configuration", false);
        assertEquals("fastremote-config.frc", chooser.getInitialFileName());
        DesktopPortalFileChooser.Request request = PlatformFileChooser.request(chooser, false, true);
        assertEquals("fastremote-config.frc", request.initialFileName());
        assertEquals(List.of("*.frc"), chooser.getSelectedExtensionFilter().getExtensions());
    }
}
