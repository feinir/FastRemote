// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.settings.WindowSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesktopDisplayScaleTest {
    @AfterEach
    void clearScaleProperties() {
        System.clearProperty(DesktopDisplayScale.GTK_SCALE_PROPERTY);
        System.clearProperty(DesktopDisplayScale.WINDOWS_UI_SCALE_PROPERTY);
        System.clearProperty(DesktopDisplayScale.WINDOWS_RENDER_SCALE_PROPERTY);
    }

    @Test
    void recognizesLinuxAndWindowsPlatforms() {
        assertTrue(DesktopDisplayScale.isLinux("GNU/Linux"));
        assertTrue(DesktopDisplayScale.isWindows("Windows 11"));
        assertFalse(DesktopDisplayScale.isLinux("Windows 11"));
        assertFalse(DesktopDisplayScale.isWindows("Mac OS X"));
    }

    @Test
    void parsesGlassScaleFormatsAndRejectsUnsupportedValues() {
        assertEquals(1.25, DesktopDisplayScale.parseScale("125%").orElseThrow());
        assertEquals(1.5, DesktopDisplayScale.parseScale("144dpi").orElseThrow());
        assertEquals(2.0, DesktopDisplayScale.parseScale("2.0").orElseThrow());
        assertTrue(DesktopDisplayScale.parseScale("automatic").isEmpty());
        assertTrue(DesktopDisplayScale.parseScale("0").isEmpty());
        assertTrue(DesktopDisplayScale.parseScale("8").isEmpty());
    }

    @Test
    void parsesOnlyExplicitXftDpiEvidence() {
        assertEquals(1.5, DesktopDisplayScale.xftDpiScale("Xft.dpi:\t144\nXft.antialias: 1").orElseThrow());
        assertEquals(2.0, DesktopDisplayScale.xftDpiScale("192").orElseThrow());
        assertTrue(DesktopDisplayScale.xftDpiScale("Xft.antialias: 1").isEmpty());
    }

    @Test
    void usesPrimaryMonitorScaleAndRejectsAmbiguousUnmarkedMonitors(@TempDir Path directory) throws Exception {
        Path monitors = directory.resolve("monitors.xml");
        Files.writeString(monitors, """
                <monitors><configuration>
                  <logicalmonitor><scale>1</scale></logicalmonitor>
                  <logicalmonitor><scale>1.5</scale><primary>yes</primary></logicalmonitor>
                </configuration></monitors>
                """);
        assertEquals(1.5, DesktopDisplayScale.monitorsXmlScale(monitors).orElseThrow());

        Files.writeString(monitors, """
                <monitors><configuration>
                  <logicalmonitor><scale>1</scale></logicalmonitor>
                  <logicalmonitor><scale>2</scale></logicalmonitor>
                </configuration></monitors>
                """);
        assertTrue(DesktopDisplayScale.monitorsXmlScale(monitors).isEmpty());
    }

    @Test
    void forcedWindowScaleDoesNotReplaceTheDetectedTrayScale() {
        var detectedTrayScale = new DesktopDisplayScale.Resolution(
                1.5, DesktopDisplayScale.Source.JAVA2D, false);
        var settings = WindowSettings.defaults().withHiDpiSettings(true, 300);

        var windowScale = DesktopDisplayScale.configureForOs(
                settings, detectedTrayScale, "Windows 11");

        assertEquals(3.0, windowScale.factor());
        assertTrue(windowScale.forced());
        assertEquals(1.5, detectedTrayScale.factor());
        assertEquals("300%", System.getProperty(DesktopDisplayScale.WINDOWS_UI_SCALE_PROPERTY));
        assertEquals("300%", System.getProperty(DesktopDisplayScale.WINDOWS_RENDER_SCALE_PROPERTY));
    }
}
