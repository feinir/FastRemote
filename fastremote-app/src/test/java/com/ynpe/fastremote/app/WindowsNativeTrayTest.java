// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.jna.Native;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class WindowsNativeTrayTest {
    @Test
    void mapsVersionFourNotificationEventsToNativeActions() {
        assertEquals(WindowsNativeTray.NativeAction.SHOW_WINDOW,
                WindowsNativeTray.nativeAction(WindowsNativeTray.NIN_SELECT));
        assertEquals(WindowsNativeTray.NativeAction.SHOW_WINDOW,
                WindowsNativeTray.nativeAction(WindowsNativeTray.NIN_KEYSELECT));
        assertEquals(WindowsNativeTray.NativeAction.SHOW_MENU,
                WindowsNativeTray.nativeAction(WindowsNativeTray.WM_CONTEXTMENU));
        assertEquals(WindowsNativeTray.NativeAction.SHOW_MENU,
                WindowsNativeTray.nativeAction(WindowsNativeTray.WM_RBUTTONUP));
        assertEquals(WindowsNativeTray.NativeAction.NONE,
                WindowsNativeTray.nativeAction(0x0200));
    }

    @Test
    void mapsEveryNativeMenuIdentifierToTheRequestedAction() {
        assertEquals(WindowsNativeTray.MenuCommand.SHOW_WINDOW,
                WindowsNativeTray.menuCommand(1001));
        assertEquals(WindowsNativeTray.MenuCommand.QUICK_CONNECT,
                WindowsNativeTray.menuCommand(1002));
        assertEquals(WindowsNativeTray.MenuCommand.LOCAL_TERMINAL,
                WindowsNativeTray.menuCommand(1003));
        assertEquals(WindowsNativeTray.MenuCommand.EXIT,
                WindowsNativeTray.menuCommand(1004));
        assertEquals(WindowsNativeTray.MenuCommand.NONE,
                WindowsNativeTray.menuCommand(0));
    }

    @Test
    void notifyIconDataMatchesTheWindowsTenAndElevenAbi() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));

        int expectedSize = Native.POINTER_SIZE == 8 ? 976 : 956;
        assertEquals(expectedSize, new WindowsNativeTray.NotifyIconData().size());
    }
}
