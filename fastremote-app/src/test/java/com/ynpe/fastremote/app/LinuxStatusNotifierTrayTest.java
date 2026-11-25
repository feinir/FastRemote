// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.Properties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class LinuxStatusNotifierTrayTest {
    @Test
    void registersWithTheRealLinuxDesktopWatcherWhenExplicitlyEnabled() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("FASTREMOTE_TEST_LINUX_TRAY")));
        try (LinuxStatusNotifierTray tray = LinuxStatusNotifierTray.create(
                "Show", "Quick connect", "Local terminal", "Exit",
                () -> { }, () -> { }, () -> { }, () -> { });
                DBusConnection inspection = DBusConnectionBuilder.forSessionBus().build()) {
            Properties watcher = inspection.getRemoteObject(
                    "org.kde.StatusNotifierWatcher", "/StatusNotifierWatcher", Properties.class);
            Object registered = watcher.Get(
                    "org.kde.StatusNotifierWatcher", "RegisteredStatusNotifierItems");

            assertTrue(stringValues(registered).stream().anyMatch(value ->
                    value.equals(tray.busName()) || value.startsWith(tray.busName() + "@")));

            Properties item = inspection.getRemoteObject(
                    tray.busName(), LinuxStatusNotifierTray.ITEM_PATH, Properties.class);
            assertEquals("Active", item.Get(
                    LinuxStatusNotifierTray.ITEM_INTERFACE, "Status"));
            LinuxStatusNotifierTray.DBusMenu menu = inspection.getRemoteObject(
                    tray.busName(), LinuxStatusNotifierTray.MENU_PATH,
                    LinuxStatusNotifierTray.DBusMenu.class);
            assertEquals(5, menu.GetLayout(0, -1, List.of()).layout.children.size());
        }
    }

    @Test
    void statusItemPublishesActiveIconAndMenuProperties() {
        LinuxStatusNotifierTray.StatusNotifierItemObject item =
                new LinuxStatusNotifierTray.StatusNotifierItemObject(
                        Path.of("/tmp/fastremote-icon-theme"), () -> { });

        assertEquals("Active", item.GetAll(LinuxStatusNotifierTray.ITEM_INTERFACE)
                .get("Status").getValue());
        assertEquals("fastremote-tray", item.GetAll(LinuxStatusNotifierTray.ITEM_INTERFACE)
                .get("IconName").getValue());
        assertEquals(new org.freedesktop.dbus.DBusPath(LinuxStatusNotifierTray.MENU_PATH),
                item.GetAll(LinuxStatusNotifierTray.ITEM_INTERFACE).get("Menu").getValue());
        assertFalse((Boolean) item.GetAll(LinuxStatusNotifierTray.ITEM_INTERFACE)
                .get("ItemIsMenu").getValue());
    }

    @Test
    void dbusMenuPublishesStableOrderAndDispatchesClickedAction() {
        AtomicInteger quickConnects = new AtomicInteger();
        LinuxStatusNotifierTray.DBusMenuObject menu =
                new LinuxStatusNotifierTray.DBusMenuObject(List.of(
                        LinuxStatusNotifierTray.MenuItem.action(1, "Show_Main", () -> { }),
                        LinuxStatusNotifierTray.MenuItem.action(2, "Quick", quickConnects::incrementAndGet),
                        LinuxStatusNotifierTray.MenuItem.separator(3)));

        LinuxStatusNotifierTray.MenuLayoutResult<UInt32, LinuxStatusNotifierTray.MenuLayout> result =
                menu.GetLayout(0, -1, List.of());

        assertEquals(3, result.layout.children.size());
        LinuxStatusNotifierTray.MenuLayout first = (LinuxStatusNotifierTray.MenuLayout)
                result.layout.children.getFirst().getValue();
        assertEquals(1, first.id);
        assertEquals("Show__Main", first.properties.get("label").getValue());

        menu.Event(2, "clicked", new Variant<>(0), new UInt32(0));
        menu.Event(3, "clicked", new Variant<>(0), new UInt32(0));
        assertEquals(1, quickConnects.get());
    }

    private static List<String> stringValues(Object value) {
        if (value instanceof String[] array) return List.of(array);
        if (value instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        return List.of(String.valueOf(value));
    }
}
