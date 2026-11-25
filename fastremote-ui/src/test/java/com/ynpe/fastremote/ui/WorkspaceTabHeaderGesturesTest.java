// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorkspaceTabHeaderGesturesTest {
    @BeforeAll static void startFx() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        try { Platform.startup(() -> ready.complete(null)); }
        catch (IllegalStateException started) { ready.complete(null); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @Test void closingTabMustNotTurnIntoBlankDoubleClick() throws Exception {
        onFx(() -> {
            Fixture f = new Fixture();
            click(f.close, MouseEvent.MOUSE_PRESSED, 2);
            assertFalse(f.tabs.getTabs().contains(f.first), "real skin closes on mouse press");
            // Scene can retarget the subsequent click after the pressed header disappears.
            click(f.header, MouseEvent.MOUSE_CLICKED, 2);
            assertEquals(0, f.blank.get(), "closing a tab must not open a local terminal");
            assertEquals(0, f.title.get(), "nor close/expand the next selected tab");
        });
    }

    @Test void closeThenBlankIsNotASecondBlankClick() throws Exception {
        onFx(() -> {
            Fixture f = new Fixture();
            click(f.close, MouseEvent.MOUSE_PRESSED, 1);
            click(f.header, MouseEvent.MOUSE_CLICKED, 1);
            gesture(f.header, 2);
            assertEquals(0, f.blank.get());
            gesture(f.header, 1);
            gesture(f.header, 2);
            assertEquals(1, f.blank.get(), "a fresh genuine blank double-click still works");
        });
    }

    @Test void blankAndTabDoubleClicksKeepTheirOwnActions() throws Exception {
        onFx(() -> {
            Fixture f = new Fixture();
            gesture(f.header, 1); gesture(f.header, 2);
            assertEquals(1, f.blank.get()); assertEquals(0, f.title.get());
            Node tab = f.close.getParent();
            while (!tab.getStyleClass().contains("tab")) tab = tab.getParent();
            gesture(tab, 1); gesture(tab, 2);
            assertEquals(1, f.blank.get()); assertEquals(1, f.title.get());
        });
    }

    @Test void crossingFromTabToBlankDoesNotMakeBlankDoubleClick() throws Exception {
        onFx(() -> {
            Fixture f = new Fixture();
            Node tab = f.close.getParent();
            while (!tab.getStyleClass().contains("tab")) tab = tab.getParent();
            gesture(tab, 1); gesture(f.header, 2);
            assertEquals(0, f.blank.get()); assertEquals(0, f.title.get());
        });
    }

    @Test void cancelledCloseNeverInvokesTitleDoubleClick() throws Exception {
        onFx(() -> {
            Fixture f = new Fixture();
            f.first.setOnCloseRequest(Event::consume);
            gesture(f.close, 1); gesture(f.close, 2);
            assertTrue(f.tabs.getTabs().contains(f.first));
            assertEquals(0, f.title.get()); assertEquals(0, f.blank.get());
        });
    }

    @Test void draggingAcrossHeaderAndContentDoubleClickAreNotHeaderGestures() throws Exception {
        onFx(() -> {
            Fixture f = new Fixture();
            gesture(f.header, 1);
            click(f.header, MouseEvent.MOUSE_PRESSED, 2);
            click(f.header, MouseEvent.MOUSE_DRAGGED, 2);
            click(f.header, MouseEvent.MOUSE_CLICKED, 2);
            gesture(f.first.getContent(), 1); gesture(f.first.getContent(), 2);
            assertEquals(0, f.title.get()); assertEquals(0, f.blank.get());
        });
    }

    private static final class Fixture {
        final Tab first = new Tab("first", new StackPane());
        final TabPane tabs = new TabPane(first, new Tab("second", new StackPane()));
        final AtomicInteger blank = new AtomicInteger(), title = new AtomicInteger();
        final Node close, header;
        Fixture() {
            tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
            first.setOnCloseRequest(event -> { event.consume(); tabs.getTabs().remove(first); });
            new Scene(tabs, 640, 320);
            tabs.applyCss(); tabs.resize(640,320); tabs.layout();
            close = tabs.lookup(".tab-close-button");
            header = tabs.lookup(".tab-header-area");
            assertNotNull(close); assertNotNull(header);
            WorkspaceTabHeaderGestures.install(tabs, title::incrementAndGet, blank::incrementAndGet);
        }
    }
    private static void gesture(Node target, int count) {
        click(target, MouseEvent.MOUSE_PRESSED, count);
        click(target, MouseEvent.MOUSE_RELEASED, count);
        click(target, MouseEvent.MOUSE_CLICKED, count);
    }
    private static void click(Node target, EventType<MouseEvent> type, int count) {
        Event.fireEvent(target, new MouseEvent(type, 2,2,2,2, MouseButton.PRIMARY,count,
                false,false,false,false,type == MouseEvent.MOUSE_PRESSED,false,false,
                false,false,true,new PickResult(target,2,2)));
    }
    private static void onFx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Platform.runLater(task); task.get(10,TimeUnit.SECONDS);
    }
}
