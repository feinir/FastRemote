// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuBar;
import javafx.stage.PopupWindow;
import javafx.stage.Window;

/** Keeps GTK/Mutter's optional frame handshake out of window-menu popup mapping. */
final class LinuxMenuPopupFrameSync implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(LinuxMenuPopupFrameSync.class.getName());
    private final Runnable prepareNativePopup;
    private final ListChangeListener<Window> windowsChanged = this::windowsChanged;
    private boolean installed;

    private void windowsChanged(ListChangeListener.Change<? extends Window> change) {
        while (change.next()) {
            if (!change.wasAdded()) continue;
            for (Window window : change.getAddedSubList()) {
                if (isWindowMenuPopup(window)) prepareNativePopup.run();
            }
        }
    }

    LinuxMenuPopupFrameSync(Runnable prepareNativePopup) {
        this.prepareNativePopup = Objects.requireNonNull(prepareNativePopup, "prepareNativePopup");
    }

    /** Load native symbols during Application.init(), not during the first menu click. */
    static Optional<LinuxMenuPopupFrameSync> loadIfSupported() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            return Optional.empty();
        }
        try {
            GtkMenuPopupFrameSync nativeSync = new GtkMenuPopupFrameSync();
            return Optional.of(new LinuxMenuPopupFrameSync(nativeSync::prepareBeforeMap));
        } catch (UnsatisfiedLinkError | SecurityException unavailable) {
            LOG.log(System.Logger.Level.WARNING, "GTK menu frame synchronization support unavailable", unavailable);
            return Optional.empty();
        }
    }

    void install() {
        requireFxThread();
        if (installed) return;
        installed = true;
        // JavaFX 21 creates the native peer before adding the Window to this list, but maps
        // it afterwards. WINDOW_SHOWING is too early; WINDOW_SHOWN is already too late.
        Window.getWindows().addListener(windowsChanged);
    }

    static boolean isWindowMenuPopup(Window window) {
        if (!(window instanceof ContextMenu)) return false;
        for (Window current = window; current instanceof PopupWindow popup; current = popup.getOwnerWindow()) {
            for (Node node = popup.getOwnerNode(); node != null; node = node.getParent()) {
                if (node instanceof MenuBar) return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        requireFxThread();
        if (!installed) return;
        Window.getWindows().removeListener(windowsChanged);
        installed = false;
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("JavaFX thread required");
    }
}
