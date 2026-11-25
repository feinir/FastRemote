// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class InterfaceFontPolicyTest {
    @Test
    void mapsSupportedOperatingSystemsToExplicitFontSelectors() {
        assertEquals("platform-macos", InterfaceFontPolicy.platformStyleClass("Mac OS X"));
        assertEquals("platform-windows", InterfaceFontPolicy.platformStyleClass("Windows 11"));
        assertEquals("platform-linux", InterfaceFontPolicy.platformStyleClass("Linux"));
    }

    @Test
    void mapsEveryPublishedScriptToItsBundledInterfaceFamily() {
        assertEquals("HarmonyOS Sans SC", InterfaceFontPolicy.interfaceFontFamily("en"));
        assertEquals("HarmonyOS Sans SC", InterfaceFontPolicy.interfaceFontFamily("zh-cn"));
        assertEquals("HarmonyOS Sans TC", InterfaceFontPolicy.interfaceFontFamily("zh-tw"));
        assertEquals("Noto Sans CJK JP", InterfaceFontPolicy.interfaceFontFamily("ja"));
        assertEquals("Noto Sans CJK KR", InterfaceFontPolicy.interfaceFontFamily("ko"));
        assertEquals("Noto Sans Thai", InterfaceFontPolicy.interfaceFontFamily("th"));
    }

    @Test
    void mapsEachInterfaceFamilyToOneBundledFontResource() {
        assertEquals("HarmonyOS_Sans_SC.ttf",
                InterfaceFontPolicy.fontFileName(InterfaceFontPolicy.HARMONY_SC_FAMILY));
        assertEquals("HarmonyOS_Sans_TC.ttf",
                InterfaceFontPolicy.fontFileName(InterfaceFontPolicy.HARMONY_TC_FAMILY));
        assertEquals("NotoSansCJKjp-Regular.otf",
                InterfaceFontPolicy.fontFileName(InterfaceFontPolicy.NOTO_JP_FAMILY));
        assertEquals("NotoSansCJKkr-Regular.otf",
                InterfaceFontPolicy.fontFileName(InterfaceFontPolicy.NOTO_KR_FAMILY));
        assertEquals("NotoSansThai-Regular.ttf",
                InterfaceFontPolicy.fontFileName(InterfaceFontPolicy.NOTO_THAI_FAMILY));
    }
}
