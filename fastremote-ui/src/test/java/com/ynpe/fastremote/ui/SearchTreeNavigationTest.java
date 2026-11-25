// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SearchTreeNavigationTest {
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test void loopsAcrossFilteredExpandedRowsAndReturnsCaretToEndWithoutSelectingText() throws Exception {
        var stage = new Stage[1];
        var search = new TextField[1];
        try {
            fx(() -> {
                TextField field = new TextField("needle");
                search[0] = field;
                var root = new TreeItem<>("root");
                var empty = new TreeItem<>("hidden group");
                var group = new TreeItem<>("group");
                var child = new TreeItem<>("needle");
                group.getChildren().add(child);
                root.getChildren().addAll(java.util.List.of(group, empty));
                LibraryTreeSupport.hideEmptyGroupsWhileSearching(root, java.util.Map.of(group, true, empty, true), "needle");
                TreeView<String> tree = new TreeView<>(root);
                tree.setShowRoot(false);
                SearchTreeNavigation.install(field, tree, () -> {});
                var scene = new Scene(new VBox(field, tree), 400, 300);
                stage[0] = new Stage(); stage[0].setScene(scene); stage[0].show();
                field.requestFocus();
                field.fireEvent(key(KeyCode.DOWN, false));
                assertSame(tree, scene.getFocusOwner());
                assertSame(group, tree.getSelectionModel().getSelectedItem());
                tree.fireEvent(key(KeyCode.UP, false));
                assertSame(field, scene.getFocusOwner());
                assertEquals(field.getLength(), field.getCaretPosition());
                assertEquals(0, field.getSelection().getLength());
                field.fireEvent(key(KeyCode.UP, false));
                assertSame(tree, scene.getFocusOwner());
                assertSame(child, tree.getSelectionModel().getSelectedItem());
                assertEquals(1, tree.getFocusModel().getFocusedIndex());
                tree.fireEvent(key(KeyCode.DOWN, false));
                assertSame(field, scene.getFocusOwner());
                // Collapsing makes the group itself the final visible row, not its hidden child.
                group.setExpanded(false);
                field.fireEvent(key(KeyCode.UP, false));
                assertSame(group, tree.getSelectionModel().getSelectedItem());
                tree.fireEvent(key(KeyCode.DOWN, false));
                assertSame(field, scene.getFocusOwner());
                assertEquals(0, field.getSelection().getLength());
            });
            fx(() -> {
                assertEquals(search[0].getLength(), search[0].getCaretPosition());
                assertEquals(0, search[0].getSelection().getLength());
            });
        } finally { fx(() -> { if (stage[0] != null) stage[0].hide(); }); }
    }

    @Test void flushesPendingSearchBeforeSelectingFirstResultAndLeavesEmptySearchFocused() throws Exception {
        fx(() -> {
            var field = new TextField("new query");
            var oldRoot = new TreeItem<>("old"); oldRoot.setExpanded(true);
            oldRoot.getChildren().add(new TreeItem<>("stale result"));
            var tree = new TreeView<>(oldRoot); tree.setShowRoot(false);
            var flushes = new java.util.concurrent.atomic.AtomicInteger();
            SearchTreeNavigation.install(field, tree, () -> {
                flushes.incrementAndGet();
                var root = new TreeItem<>("new"); root.setExpanded(true);
                tree.setRoot(root);
            });
            var scene = new Scene(new VBox(field, tree));
            field.requestFocus();
            field.fireEvent(key(KeyCode.DOWN, true));
            field.fireEvent(key(KeyCode.UP, true));
            assertEquals(0, flushes.get());
            field.fireEvent(key(KeyCode.DOWN, false));
            assertEquals(1, flushes.get());
            assertNull(tree.getSelectionModel().getSelectedItem());
            assertSame(field, scene.getFocusOwner());
            field.positionCaret(3);
            field.fireEvent(key(KeyCode.UP, false));
            assertEquals(2, flushes.get());
            assertSame(field, scene.getFocusOwner());
            assertEquals(3, field.getCaretPosition());
        });
    }

    private static KeyEvent key(KeyCode key, boolean shift) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, shift, false, false, false);
    }
    private static void fx(Runnable action) throws Exception {
        var task = new FutureTask<Void>(() -> { action.run(); return null; });
        Platform.runLater(task); task.get(5, TimeUnit.SECONDS);
    }
}
