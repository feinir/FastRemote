// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LinuxMenuPopupFrameSyncTest {
    @BeforeAll
    static void toolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void scopesPreparationToMenuBarPopupsBeforeShownAndReleasesListener() throws Exception {
        onFx(() -> {
            Stage stage = new Stage();
            MenuBar bar = new MenuBar(new Menu("File", null, new MenuItem("Open")));
            bar.setUseSystemMenuBar(false);
            TextArea text = new TextArea("Generated log");
            Button nestedOwner = new Button("Nested owner");
            ContextMenu menu = new ContextMenu(new CustomMenuItem(nestedOwner, false));
            ContextMenu nested = new ContextMenu(new MenuItem("Child"));
            ContextMenu ordinary = new ContextMenu(new MenuItem("Copy"));
            Popup plain = new Popup();
            plain.getContent().add(new Label("Not a menu"));
            AtomicInteger prepared = new AtomicInteger();
            AtomicInteger shown = new AtomicInteger();
            try (var support = new LinuxMenuPopupFrameSync(() -> {
                assertEquals(0, shown.get(), "native preparation must precede WINDOW_SHOWN");
                prepared.incrementAndGet();
            })) {
                support.install();
                support.install();
                stage.setScene(new Scene(new VBox(bar, text), 440, 260));
                stage.show();
                menu.setOnShown(event -> shown.incrementAndGet());
                menu.show(bar, javafx.geometry.Side.BOTTOM, 0, 0);
                assertEquals(1, prepared.get());
                assertTrue(LinuxMenuPopupFrameSync.isWindowMenuPopup(menu));
                shown.set(0);
                nested.show(nestedOwner, javafx.geometry.Side.RIGHT, 0, 0);
                assertTrue(LinuxMenuPopupFrameSync.isWindowMenuPopup(nested));
                assertEquals(2, prepared.get());
                nested.hide();
                menu.hide();
                ordinary.show(text, javafx.geometry.Side.BOTTOM, 0, 0);
                assertFalse(LinuxMenuPopupFrameSync.isWindowMenuPopup(ordinary));
                ordinary.hide();
                plain.show(bar, stage.getX(), stage.getY());
                assertFalse(LinuxMenuPopupFrameSync.isWindowMenuPopup(plain));
                plain.hide();
                assertEquals(2, prepared.get(), "unrelated popups must remain untouched");
                support.close();
                support.close();
                menu.show(bar, javafx.geometry.Side.BOTTOM, 0, 0);
                assertEquals(2, prepared.get(), "closing removes the listener");
            } finally {
                nested.hide(); menu.hide(); ordinary.hide(); plain.hide(); stage.hide();
            }
        });
    }

    @Test
    void actualGtkSymbolsAndPeerAreAvailableOnLinux() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").contains("Linux"));
        var nativeSync = new GtkMenuPopupFrameSync();
        AtomicInteger prepared = new AtomicInteger();
        var support = new LinuxMenuPopupFrameSync(() -> {
            assertTrue(nativeSync.prepareBeforeMap(), "exactly one unmapped native peer must be found");
            prepared.incrementAndGet();
        });
        onFx(() -> {
            Stage stage = new Stage();
            Menu file = new Menu("File", null, new MenuItem("Open"));
            MenuBar bar = new MenuBar(file);
            try (support) {
                support.install();
                stage.setScene(new Scene(new VBox(bar, new TextArea()), 400, 240));
                stage.show();
                file.show();
                assertTrue(file.isShowing());
                assertEquals(1, prepared.get(), "real MenuBar must reach the native adapter");
            } finally { file.hide(); stage.hide(); }
        });
        // This verifies native linkage/lifecycle only; visibility requires real desktop recording.
    }

    private static void onFx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
