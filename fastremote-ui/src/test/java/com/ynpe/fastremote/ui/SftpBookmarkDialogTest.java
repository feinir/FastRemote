// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.SftpBookmarks;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SftpBookmarkDialogTest {
    @Test
    void escapeClosesBookmarksWithLocalPathListFocused() throws Exception { exerciseListEscape(false); }

    @Test
    void escapeClosesBookmarksWithRemotePathListFocused() throws Exception { exerciseListEscape(true); }

    private void exerciseListEscape(boolean remote) throws Exception {
        java.util.concurrent.FutureTask<Void> task = new java.util.concurrent.FutureTask<>(() -> {
            Dialog<Void> dialog = SftpBookmarkDialog.createDialog(null, "Bookmarks",
                    new SftpBookmarks(List.of("/local"), List.of("/remote")),
                    ignored -> CompletableFuture.completedFuture(null));
            try {
                dialog.show();
                var pane = dialog.getDialogPane();
                if (remote) {
                    pane.lookupAll(".dialog-tab-button").stream().map(ToggleButton.class::cast)
                            .filter(button -> button.getText().equals(
                                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.7ba00927dde9122f")))
                            .findFirst().orElseThrow().fire();
                }
                ListView<?> list = pane.lookupAll(".list-view").stream().map(ListView.class::cast)
                        .filter(value -> value.getItems().contains(remote ? "/remote" : "/local"))
                        .findFirst().orElseThrow();
                list.getSelectionModel().select(0);
                list.requestFocus();
                assertEquals(list, pane.getScene().getFocusOwner());
                list.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                        "", "", javafx.scene.input.KeyCode.ESCAPE, false, false, false, false));
                assertFalse(dialog.isShowing(), "Escape from a focused path list must close the dialog");
            } finally { dialog.close(); }
            return null;
        });
        Platform.runLater(task);
        task.get(5, TimeUnit.SECONDS);
    }

    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void bookmarkHeaderDescribesOnlyTheAvailableDragReordering() {
        assertEquals("拖动可调整顺序。",
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.7ea9f30a94d3d78b"));
    }

    @Test
    void bookmarkInputDropsTrailingSeparatorsButPreservesFilesystemRoots() {
        assertEquals("/srv/releases", SftpBookmarkDialog.normalizeBookmarkInput(" /srv/releases///\\ "));
        assertEquals("C:\\work", SftpBookmarkDialog.normalizeBookmarkInput("C:\\work\\\\"));
        assertEquals("/", SftpBookmarkDialog.normalizeBookmarkInput("/"));
        assertEquals("C:\\", SftpBookmarkDialog.normalizeBookmarkInput("C:\\"));
    }

    @Test
    void remoteBookmarkInputAcceptsBothWindowsSeparatorStylesAndUsesOneCanonicalValue() {
        assertEquals("D:/FastRemote",
                SftpBookmarkDialog.normalizeRemoteBookmarkInput(" D:\\FastRemote\\ "));
        assertEquals("D:/FastRemote",
                SftpBookmarkDialog.normalizeRemoteBookmarkInput("D:/FastRemote/"));
        assertEquals("D:/", SftpBookmarkDialog.normalizeRemoteBookmarkInput("D:\\"));
        assertEquals("/srv/releases",
                SftpBookmarkDialog.normalizeRemoteBookmarkInput("/srv/releases///"));
    }

    @Test
    void bookmarkManagerOnlyOffersTheConfirmButton() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Dialog<Void> dialog = new Dialog<>();
                SftpBookmarkDialog.configureDialogButtons(dialog);
                assertEquals(List.of(ButtonType.OK), dialog.getDialogPane().getButtonTypes());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void bookmarkChangesArePersistedInSubmissionOrder() throws Exception {
        CompletableFuture<Void> firstSave = new CompletableFuture<>();
        java.util.concurrent.CopyOnWriteArrayList<SftpBookmarks> saved =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        SftpBookmarkDialog.BookmarkPersistenceQueue queue =
                new SftpBookmarkDialog.BookmarkPersistenceQueue(bookmarks -> {
                    saved.add(bookmarks);
                    return saved.size() == 1 ? firstSave : CompletableFuture.completedFuture(null);
                });
        SftpBookmarks first = new SftpBookmarks(List.of("/one"), List.of());
        SftpBookmarks second = new SftpBookmarks(List.of("/two"), List.of());

        queue.submit(first);
        queue.submit(second);
        assertEquals(List.of(first), saved);
        firstSave.complete(null);
        queue.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(List.of(first, second), saved);
    }

    @Test
    void bookmarkManagerDeclaresStableInitialAndMinimumGeometry() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Dialog<Void> dialog = new Dialog<>();
                SftpBookmarkDialog.configureDialogGeometry(dialog);
                assertEquals(600.0, dialog.getDialogPane().getPrefWidth());
                assertEquals(390.0, dialog.getDialogPane().getPrefHeight());
                assertEquals(600.0, dialog.getDialogPane().getMinWidth());
                assertEquals(390.0, dialog.getDialogPane().getMinHeight());
                assertEquals(600.0,
                        DialogSpacing.configuredResizableMinimumWidth(dialog.getDialogPane()));
                assertEquals(390.0,
                        DialogSpacing.configuredResizableMinimumHeight(dialog.getDialogPane()));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void bookmarkManagerKeepsConfirmButtonInsideTheInitialScene() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Dialog<Void> dialog = SftpBookmarkDialog.createDialog(null, "Bookmarks",
                    new SftpBookmarks(List.of("/one", "/two", "/three", "/four", "/five"),
                            List.of("/remote")),
                    ignored -> CompletableFuture.completedFuture(null));
            try {
                dialog.show();
                PauseTransition afterInitialNativeLayout =
                        new PauseTransition(javafx.util.Duration.millis(1_000));
                afterInitialNativeLayout.setOnFinished(ignored -> {
                    try {
                        var window = dialog.getDialogPane().getScene().getWindow();
                        Node confirm = dialog.getDialogPane().lookupButton(ButtonType.OK);
                        Bounds bounds = confirm.localToScene(confirm.getBoundsInLocal());
                        assertTrue(dialog.getDialogPane().getScene().getHeight() >= 389.5,
                                () -> "configured content height became outer-window height: stage="
                                        + window.getHeight()
                                        + ", scene=" + dialog.getDialogPane().getScene().getHeight()
                                        + ", pane=" + dialog.getDialogPane().getHeight());
                        assertEquals(1.0, window.getOpacity(), 0.001,
                                "the dialog must only be revealed after its initial client height is applied");
                        assertTrue(bounds.getMaxY() <= dialog.getDialogPane().getScene().getHeight() + 0.5,
                                () -> "confirm button extends past scene: " + bounds
                                        + ", scene height="
                                        + dialog.getDialogPane().getScene().getHeight());
                        double revealedWidth = window.getWidth();
                        double revealedHeight = window.getHeight();
                        PauseTransition afterPaneMinimumRelaxation =
                                new PauseTransition(javafx.util.Duration.millis(650));
                        afterPaneMinimumRelaxation.setOnFinished(relaxedIgnored -> {
                            try {
                                assertEquals(revealedWidth, window.getWidth(), 0.5,
                                        "lowering only the pane minimum must not resize the window");
                                assertEquals(revealedHeight, window.getHeight(), 0.5,
                                        "lowering only the pane minimum must not resize the window");
                                Node relaxedConfirm = dialog.getDialogPane().lookupButton(ButtonType.OK);
                                Bounds relaxedBounds = relaxedConfirm.localToScene(
                                        relaxedConfirm.getBoundsInLocal());
                                assertTrue(relaxedBounds.getMaxY()
                                                <= dialog.getDialogPane().getScene().getHeight() + 0.5,
                                        "confirm button must remain visible after the pane minimum relaxes");
                            } catch (Throwable error) {
                                failure.set(error);
                            } finally {
                                dialog.close();
                                completed.countDown();
                            }
                        });
                        afterPaneMinimumRelaxation.play();
                    } catch (Throwable error) {
                        failure.set(error);
                        dialog.close();
                        completed.countDown();
                    }
                });
                afterInitialNativeLayout.play();
            } catch (Throwable error) {
                failure.set(error);
                dialog.close();
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void bookmarkAndAboutDialogsShareTheSegmentedTabSwitcher() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Label first = new Label("first page");
                Label second = new Label("second page");
                var tabs = DialogTabSwitcher.create(List.of(
                        new DialogTabSwitcher.Page("First", first),
                        new DialogTabSwitcher.Page("Second", second)));

                HBox switcher = (HBox) tabs.getChildren().getFirst();
                StackPane content = (StackPane) tabs.getChildren().getLast();
                assertTrue(switcher.getStyleClass().contains("dialog-tab-switcher"));
                assertTrue(((ToggleButton) switcher.getChildren().getFirst()).getStyleClass()
                        .contains("dialog-tab-button"));
                assertTrue(first.isVisible());
                assertTrue(first.isManaged());
                assertTrue(!second.isVisible());
                ((ToggleButton) switcher.getChildren().getLast()).fire();
                assertTrue(!content.getChildren().getFirst().isVisible());
                assertTrue(second.isVisible());
                assertTrue(second.isManaged());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void reorderUsesTheInsertionPositionBeforeOrAfterTheTargetRow() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                ListView<String> values = new ListView<>();
                values.getItems().setAll("one", "two", "three", "four");

                assertEquals(2, ReorderableListView.reorder(values, 0, 3));
                assertEquals(List.of("two", "three", "one", "four"), values.getItems());
                assertEquals(0, ReorderableListView.reorder(values, 3, 0));
                assertEquals(List.of("four", "two", "three", "one"), values.getItems());

                var first = java.nio.file.Path.of("first");
                var second = java.nio.file.Path.of("second");
                var paths = ReorderableListView.create(List.of(first, second));
                var observed = new java.util.ArrayList<List<java.nio.file.Path>>();
                paths.getItems().addListener((javafx.collections.ListChangeListener<java.nio.file.Path>)
                        change -> observed.add(List.copyOf(change.getList())));
                assertEquals(1, ReorderableListView.reorder(paths, 0, 2));
                assertEquals(List.of(List.of(second, first)), observed);
                assertEquals(1, ReorderableListView.reorder(paths, 1, 2));
                assertEquals(1, observed.size());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }
}
