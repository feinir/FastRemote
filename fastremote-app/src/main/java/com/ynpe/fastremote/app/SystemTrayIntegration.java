// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.i18n.Localizer;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.freedesktop.dbus.exceptions.DBusException;

/** Selects the native operating-system tray implementation. */
final class SystemTrayIntegration implements AutoCloseable {
    private static final System.Logger LOGGER =
            System.getLogger(SystemTrayIntegration.class.getName());

    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletableFuture<Boolean> available = new CompletableFuture<>();
    private volatile WindowsNativeTray windowsTray;
    private volatile MacNativeTray macTray;
    private volatile LinuxStatusNotifierTray linuxTray;

    SystemTrayIntegration(
            Localizer localizer,
            Runnable showWindow,
            Runnable showQuickConnect,
            Runnable openLocalTerminal,
            Runnable exitApplication,
            boolean macOsMenuBarIconEnabled) {
        Objects.requireNonNull(localizer, "localizer");
        Objects.requireNonNull(showWindow, "showWindow");
        Objects.requireNonNull(showQuickConnect, "showQuickConnect");
        Objects.requireNonNull(openLocalTerminal, "openLocalTerminal");
        Objects.requireNonNull(exitApplication, "exitApplication");

        if (isMacOs()) {
            try {
                macTray = MacNativeTray.create(
                        localizer.text("action.showMainWindow"),
                        localizer.text("action.quickConnect"),
                        localizer.text("action.localTerminal"),
                        localizer.text("action.exit"),
                        showWindow, showQuickConnect, openLocalTerminal, exitApplication);
                macTray.setEnabled(macOsMenuBarIconEnabled);
                available.complete(macTray.isAvailable());
            } catch (RuntimeException | LinkageError failure) {
                available.complete(false);
                LOGGER.log(System.Logger.Level.WARNING,
                        "Native macOS menu-bar integration is unavailable", failure);
            }
            return;
        }
        if (isWindows()) {
            try {
                windowsTray = WindowsNativeTray.create(
                        localizer.text("action.showMainWindow"),
                        localizer.text("action.quickConnect"),
                        localizer.text("action.localTerminal"),
                        localizer.text("action.exit"),
                        showWindow,
                        showQuickConnect,
                        openLocalTerminal,
                        exitApplication);
                windowsTray.availability().whenComplete((result, failure) -> {
                    if (failure != null) {
                        available.complete(false);
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Native Windows notification-area integration failed", failure);
                    } else {
                        available.complete(Boolean.TRUE.equals(result));
                    }
                });
            } catch (RuntimeException | LinkageError failure) {
                available.complete(false);
                LOGGER.log(System.Logger.Level.WARNING,
                        "Native Windows notification-area integration is unavailable", failure);
            }
            return;
        }
        if (isLinux()) {
            Thread.ofVirtual().name("fastremote-linux-tray-bootstrap").start(() -> {
                try {
                    LinuxStatusNotifierTray candidate = LinuxStatusNotifierTray.create(
                        localizer.text("action.showMainWindow"),
                        localizer.text("action.quickConnect"),
                        localizer.text("action.localTerminal"),
                        localizer.text("action.exit"),
                        showWindow,
                        showQuickConnect,
                        openLocalTerminal,
                        exitApplication);
                    boolean discard;
                    synchronized (this) {
                        discard = closed.get();
                        if (!discard) linuxTray = candidate;
                    }
                    if (discard) {
                        candidate.close();
                        available.complete(false);
                    } else {
                        available.complete(true);
                    }
                } catch (IOException | DBusException | RuntimeException | LinkageError failure) {
                    available.complete(false);
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Linux StatusNotifierItem tray is unavailable", failure);
                }
            });
            return;
        }
        available.complete(false);
    }

    CompletionStage<Boolean> availability() {
        return available;
    }

    boolean isAvailable() {
        MacNativeTray currentMacTray = macTray;
        if (currentMacTray != null) return currentMacTray.isAvailable();
        return Boolean.TRUE.equals(available.getNow(false))
                && (windowsTray != null || linuxTray != null);
    }

    void setMacOsMenuBarIconEnabled(boolean enabled) {
        MacNativeTray currentMacTray = macTray;
        if (currentMacTray == null) return;
        try {
            currentMacTray.setEnabled(enabled);
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot change native macOS menu-bar icon visibility", failure);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        WindowsNativeTray currentWindowsTray = windowsTray;
        windowsTray = null;
        if (currentWindowsTray != null) currentWindowsTray.close();
        MacNativeTray currentMacTray = macTray;
        macTray = null;
        if (currentMacTray != null) currentMacTray.close();
        LinuxStatusNotifierTray currentLinuxTray;
        synchronized (this) {
            currentLinuxTray = linuxTray;
            linuxTray = null;
        }
        if (currentLinuxTray != null) currentLinuxTray.close();
        available.complete(false);
    }

    private static boolean isWindows() {
        return DesktopDisplayScale.isWindows(System.getProperty("os.name", ""));
    }

    private static boolean isLinux() {
        return DesktopDisplayScale.isLinux(System.getProperty("os.name", ""));
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }
}
