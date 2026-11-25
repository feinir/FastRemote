// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import java.util.Locale;

final class MacDockIcon {
    private static final System.Logger LOGGER = System.getLogger(MacDockIcon.class.getName());

    private MacDockIcon() {
    }

    static void restoreBundleIcon() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            return;
        }

        try {
            NativeLibrary objectiveC = NativeLibrary.getInstance("objc");
            Function getClass = objectiveC.getFunction("objc_getClass");
            Function registerSelector = objectiveC.getFunction("sel_registerName");
            Function sendMessage = objectiveC.getFunction("objc_msgSend");

            Pointer applicationClass = getClass.invokePointer(new Object[]{"NSApplication"});
            Pointer sharedApplicationSelector = registerSelector.invokePointer(
                    new Object[]{"sharedApplication"});
            Pointer application = sendMessage.invokePointer(
                    new Object[]{applicationClass, sharedApplicationSelector});
            Pointer setIconSelector = registerSelector.invokePointer(
                    new Object[]{"setApplicationIconImage:"});

            // JavaFX has already assigned the bundle image through this legacy AppKit setter.
            // nil removes that runtime override so the Dock renders CFBundleIconFile normally.
            sendMessage.invokeVoid(new Object[]{application, setIconSelector, Pointer.NULL});
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Unable to restore the macOS Bundle icon after JavaFX startup", failure);
        }
    }

    /**
     * Hiding the application after its last window closes makes the very next Dock click produce
     * an AppKit unhide event. JavaFX forwards that event to the main-window reopen callback.
     */
    static void hideApplication() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) return;
        try {
            NativeLibrary objectiveC = NativeLibrary.getInstance("objc");
            Function getClass = objectiveC.getFunction("objc_getClass");
            Function registerSelector = objectiveC.getFunction("sel_registerName");
            Function sendMessage = objectiveC.getFunction("objc_msgSend");
            Pointer runningApplicationClass = getClass.invokePointer(new Object[]{"NSRunningApplication"});
            Pointer currentApplicationSelector = registerSelector.invokePointer(
                    new Object[]{"currentApplication"});
            Pointer runningApplication = sendMessage.invokePointer(
                    new Object[]{runningApplicationClass, currentApplicationSelector});
            Pointer hideSelector = registerSelector.invokePointer(new Object[]{"hide"});
            int hidden = sendMessage.invokeInt(new Object[]{runningApplication, hideSelector});
            if (hidden == 0) {
                // NSRunningApplication may refuse to hide after the last native window has
                // already gone. Ask NSApplication directly and always deactivate it so the next
                // Dock click produces a did-become-active callback even on that macOS path.
                Pointer applicationClass = getClass.invokePointer(new Object[]{"NSApplication"});
                Pointer sharedApplicationSelector = registerSelector.invokePointer(
                        new Object[]{"sharedApplication"});
                Pointer application = sendMessage.invokePointer(
                        new Object[]{applicationClass, sharedApplicationSelector});
                Pointer applicationHideSelector = registerSelector.invokePointer(new Object[]{"hide:"});
                sendMessage.invokeVoid(new Object[]{application, applicationHideSelector, Pointer.NULL});
                Pointer deactivateSelector = registerSelector.invokePointer(new Object[]{"deactivate"});
                sendMessage.invokeVoid(new Object[]{application, deactivateSelector});
            }
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Unable to hide the macOS application after closing its main window", failure);
        }
    }

    /** Makes a newly rebuilt JavaFX Stage visible even when the process was previously hidden. */
    static void showApplication() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) return;
        try {
            NativeLibrary objectiveC = NativeLibrary.getInstance("objc");
            Function getClass = objectiveC.getFunction("objc_getClass");
            Function registerSelector = objectiveC.getFunction("sel_registerName");
            Function sendMessage = objectiveC.getFunction("objc_msgSend");
            Pointer runningApplicationClass = getClass.invokePointer(new Object[]{"NSRunningApplication"});
            Pointer currentApplicationSelector = registerSelector.invokePointer(
                    new Object[]{"currentApplication"});
            Pointer runningApplication = sendMessage.invokePointer(
                    new Object[]{runningApplicationClass, currentApplicationSelector});
            Pointer unhideSelector = registerSelector.invokePointer(new Object[]{"unhide"});
            int unhidden = sendMessage.invokeInt(new Object[]{runningApplication, unhideSelector});
            Pointer activateSelector = registerSelector.invokePointer(new Object[]{"activateWithOptions:"});
            // NSApplicationActivateAllWindows | NSApplicationActivateIgnoringOtherApps
            int activated = sendMessage.invokeInt(new Object[]{runningApplication, activateSelector, 3L});
            if (unhidden == 0 || activated == 0) {
                Pointer applicationClass = getClass.invokePointer(new Object[]{"NSApplication"});
                Pointer sharedApplicationSelector = registerSelector.invokePointer(
                        new Object[]{"sharedApplication"});
                Pointer application = sendMessage.invokePointer(
                        new Object[]{applicationClass, sharedApplicationSelector});
                Pointer unhideSelectorFallback = registerSelector.invokePointer(new Object[]{"unhideWithoutActivation"});
                sendMessage.invokeVoid(new Object[]{application, unhideSelectorFallback});
                Pointer activateSelectorFallback = registerSelector.invokePointer(
                        new Object[]{"activateIgnoringOtherApps:"});
                sendMessage.invokeVoid(new Object[]{application, activateSelectorFallback, true});
            }
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Unable to show the macOS application while rebuilding its main window", failure);
        }
    }
}
