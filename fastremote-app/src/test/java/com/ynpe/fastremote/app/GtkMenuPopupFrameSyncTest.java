// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GtkMenuPopupFrameSyncTest {
    @Test
    void disablesBothSidesInOrderAndReleasesTheList() {
        try (Fixture f = new Fixture()) {
            f.add(0, false, false, true); // ordinary Stage
            f.add(1, true, false, true);  // already visible menu
            f.add(1, false, false, true);
            f.adapter().prepareBeforeMap();
            assertEquals(List.of("disable", "unadvertise"), f.calls);
            assertEquals(1, f.freed);
        }
    }

    @Test
    void doesNotGuessBetweenTwoUnmappedPopups() {
        try (Fixture f = new Fixture()) {
            f.add(1, false, false, true);
            f.add(1, false, false, true);
            f.adapter().prepareBeforeMap();
            assertTrue(f.calls.isEmpty());
            assertEquals(1, f.freed);
        }
    }

    @Test
    void ignoresDestroyedAndNonX11Peers() {
        try (Fixture f = new Fixture()) {
            f.add(1, false, true, true);
            f.add(1, false, false, false);
            f.adapter().prepareBeforeMap();
            assertTrue(f.calls.isEmpty());
            assertEquals(1, f.freed);
        }
    }

    @Test
    void emptyListAndMissingDisplayDoNotCallXlib() {
        try (Fixture f = new Fixture()) {
            f.adapter().prepareBeforeMap();
            f.add(1, false, false, true);
            f.display = null;
            f.adapter().prepareBeforeMap();
            assertTrue(f.calls.isEmpty());
            assertEquals(0, f.interned);
            assertEquals(2, f.freed);
        }
    }

    @Test
    void boundsNativeListTraversal() {
        try (Fixture f = new Fixture()) {
            f.add(0, false, false, true);
            f.links.getFirst().setPointer(Native.POINTER_SIZE, f.head);
            f.adapter().prepareBeforeMap();
            assertTrue(f.calls.isEmpty());
            assertEquals(1, f.freed);
        }
    }

    @Test
    void alwaysFreesOwnedListIfNativeCallFails() {
        try (Fixture f = new Fixture()) {
            f.add(1, false, false, true);
            f.failDisable = true;
            assertThrows(IllegalStateException.class, () -> f.adapter().prepareBeforeMap());
            assertEquals(1, f.freed);
        }
    }

    private static final class Fixture implements GtkMenuPopupFrameSync.Gtk, GtkMenuPopupFrameSync.Gdk,
            GtkMenuPopupFrameSync.Glib, GtkMenuPopupFrameSync.GObject, GtkMenuPopupFrameSync.Xlib,
            AutoCloseable {
        private record Peer(int type, boolean visible, boolean destroyed, boolean x11) { }
        final List<Memory> links = new ArrayList<>();
        final Map<Pointer, Peer> peers = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        Pointer head;
        Pointer display = new Pointer(0x1000);
        int freed;
        int interned;
        boolean failDisable;

        void add(int type, boolean visible, boolean destroyed, boolean x11) {
            Pointer widget = new Pointer(0x2000 + peers.size());
            peers.put(widget, new Peer(type, visible, destroyed, x11));
            Memory link = new Memory(Native.POINTER_SIZE * 3L);
            link.clear();
            link.setPointer(0, widget);
            if (head == null) head = link;
            else links.getLast().setPointer(Native.POINTER_SIZE, link);
            links.add(link);
        }

        GtkMenuPopupFrameSync adapter() { return new GtkMenuPopupFrameSync(this, this, this, this, this); }
        public Pointer gtk_window_list_toplevels() { return head; }
        public int gtk_window_get_window_type(Pointer widget) { return peers.get(widget).type(); }
        public int gtk_widget_get_visible(Pointer widget) { return peers.get(widget).visible() ? 1 : 0; }
        public Pointer gtk_widget_get_window(Pointer widget) { return widget; }
        public NativeLong gdk_x11_window_get_type() { return new NativeLong(12); }
        public int gdk_window_is_destroyed(Pointer window) { return peers.get(window).destroyed() ? 1 : 0; }
        public void gdk_x11_window_set_frame_sync_enabled(Pointer window, int enabled) {
            assertEquals(0, enabled);
            if (failDisable) throw new IllegalStateException("synthetic native failure");
            calls.add("disable");
        }
        public Pointer gdk_window_get_display(Pointer window) { return display; }
        public Pointer gdk_x11_display_get_xdisplay(Pointer ignored) { return display; }
        public NativeLong gdk_x11_window_get_xid(Pointer window) { return new NativeLong(42); }
        public void g_list_free(Pointer list) { assertEquals(head, list); freed++; }
        public int g_type_check_instance_is_a(Pointer window, NativeLong type) {
            return peers.get(window).x11() ? 1 : 0;
        }
        public NativeLong XInternAtom(Pointer dpy, String name, int onlyIfExists) {
            assertNotNull(dpy);
            assertEquals("_NET_WM_SYNC_REQUEST_COUNTER", name);
            interned++;
            return new NativeLong(43);
        }
        public int XDeleteProperty(Pointer dpy, NativeLong window, NativeLong atom) {
            assertEquals(42, window.longValue());
            assertEquals(43, atom.longValue());
            calls.add("unadvertise");
            return 1;
        }
        public void close() { links.forEach(Memory::close); }
    }
}
