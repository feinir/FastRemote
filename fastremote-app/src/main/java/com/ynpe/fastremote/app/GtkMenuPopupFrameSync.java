// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

/** GTK 3/X11 adapter; called only on the FX/GTK thread, before a native menu is mapped. */
final class GtkMenuPopupFrameSync {
    private static final int GTK_WINDOW_POPUP = 1;
    private static final int MAX_TOPLEVELS = 256;
    private static final System.Logger LOG = System.getLogger(GtkMenuPopupFrameSync.class.getName());
    private final Gtk gtk;
    private final Gdk gdk;
    private final Glib glib;
    private final GObject gobject;
    private final Xlib xlib;
    private boolean mismatchReported;

    GtkMenuPopupFrameSync() {
        this(Native.load("gtk-3", Gtk.class), Native.load("gdk-3", Gdk.class),
                Native.load("glib-2.0", Glib.class), Native.load("gobject-2.0", GObject.class),
                Native.load("X11", Xlib.class));
    }

    GtkMenuPopupFrameSync(Gtk gtk, Gdk gdk, Glib glib, GObject gobject, Xlib xlib) {
        this.gtk = gtk;
        this.gdk = gdk;
        this.glib = glib;
        this.gobject = gobject;
        this.xlib = xlib;
    }

    boolean prepareBeforeMap() {
        Pointer head = gtk.gtk_window_list_toplevels();
        try {
            Pointer candidate = null;
            int visited = 0;
            for (Pointer link = head; link != null; link = link.getPointer(Native.POINTER_SIZE)) {
                if (++visited > MAX_TOPLEVELS) {
                    reportMismatch();
                    return false;
                }
                Pointer widget = link.getPointer(0);
                if (gtk.gtk_window_get_window_type(widget) != GTK_WINDOW_POPUP
                        || gtk.gtk_widget_get_visible(widget) != 0) continue;
                Pointer window = gtk.gtk_widget_get_window(widget);
                if (window == null || gdk.gdk_window_is_destroyed(window) != 0
                        || gobject.g_type_check_instance_is_a(window, gdk.gdk_x11_window_get_type()) == 0) continue;
                // Glass serially realizes one new peer during this FX callback. Never guess
                // if that lifecycle changes, and never alter an already mapped GTK window.
                if (candidate != null) {
                    reportMismatch();
                    return false;
                }
                candidate = window;
            }
            if (candidate == null) {
                reportMismatch();
                return false;
            }
            Pointer display = gdk.gdk_window_get_display(candidate);
            if (display == null) {
                reportMismatch();
                return false;
            }
            Pointer xdisplay = gdk.gdk_x11_display_get_xdisplay(display);
            if (xdisplay == null) {
                reportMismatch();
                return false;
            }
            NativeLong xid = gdk.gdk_x11_window_get_xid(candidate);
            NativeLong atom = xlib.XInternAtom(xdisplay, "_NET_WM_SYNC_REQUEST_COUNTER", 0);
            if (xid.longValue() == 0 || atom.longValue() == 0) {
                reportMismatch();
                return false;
            }
            // Both ends must opt out BEFORE MapWindow. Disabling GDK's wait alone still lets
            // Mutter freeze the surface on its extended counter (JDK-8371781). Conversely,
            // removing only the X property leaves GDK waiting for a FRAME_DRAWN that cannot
            // arrive. JavaFX continues to schedule/render the standard Popup normally.
            gdk.gdk_x11_window_set_frame_sync_enabled(candidate, 0);
            xlib.XDeleteProperty(xdisplay, xid, atom);
            // No XSync, sleep, hidden warm-up window, remap, synthetic click or global switch.
            return true;
        } finally {
            glib.g_list_free(head);
        }
    }

    private void reportMismatch() {
        if (mismatchReported) return;
        mismatchReported = true;
        LOG.log(System.Logger.Level.WARNING,
                "GTK menu peer was not uniquely available before mapping; leaving native state unchanged");
    }

    interface Gtk extends Library {
        Pointer gtk_window_list_toplevels();
        int gtk_window_get_window_type(Pointer widget);
        int gtk_widget_get_visible(Pointer widget);
        Pointer gtk_widget_get_window(Pointer widget);
    }

    interface Gdk extends Library {
        NativeLong gdk_x11_window_get_type();
        int gdk_window_is_destroyed(Pointer window);
        void gdk_x11_window_set_frame_sync_enabled(Pointer window, int enabled);
        Pointer gdk_window_get_display(Pointer window);
        Pointer gdk_x11_display_get_xdisplay(Pointer display);
        NativeLong gdk_x11_window_get_xid(Pointer window);
    }

    interface Glib extends Library {
        void g_list_free(Pointer list);
    }

    interface GObject extends Library {
        int g_type_check_instance_is_a(Pointer instance, NativeLong type);
    }

    interface Xlib extends Library {
        NativeLong XInternAtom(Pointer display, String name, int onlyIfExists);
        int XDeleteProperty(Pointer display, NativeLong window, NativeLong atom);
    }
}
