// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.skin.VirtualFlow;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CenteredTreeViewTest {
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
    void selectionRepositioningStopsForVisibleRowsAndTreeFocus() {
        assertTrue(CenteredTreeView.shouldRepositionSelection(false, false));
        org.junit.jupiter.api.Assertions.assertFalse(
                CenteredTreeView.shouldRepositionSelection(false, true));
        org.junit.jupiter.api.Assertions.assertFalse(
                CenteredTreeView.shouldRepositionSelection(true, false));
        org.junit.jupiter.api.Assertions.assertFalse(
                CenteredTreeView.shouldRepositionSelection(true, true));
    }

    @Test
    void programmaticSelectionCentersUnlessTheTreeHasReachedAnEdge() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                CenteredTreeView<String> tree = new CenteredTreeView<>();
                // Centering is intentionally suppressed for focused trees. GTK can grant
                // initial focus between show() and the queued centering callback.
                tree.setFocusTraversable(false);
                tree.setFixedCellSize(30.0);
                TreeItem<String> root = new TreeItem<>("root");
                for (int index = 0; index < 30; index++) {
                    root.getChildren().add(new TreeItem<>("directory-" + index));
                }
                root.setExpanded(true);
                tree.setRoot(root);
                tree.setShowRoot(false);
                stage.setScene(new Scene(tree, 320.0, 202.0));
                stage.show();
                tree.applyCss();
                tree.layout();

                TreeItem<String> middle = root.getChildren().get(15);
                tree.getSelectionModel().select(middle);
                tree.centerSelection();
                Platform.runLater(() -> verifyCenterAndEdges(tree, root, stage, completed, failure));
            } catch (Throwable error) {
                failure.set(error);
                stage.hide();
                completed.countDown();
            }
        });
        assertTrue(completed.await(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    private static void verifyCenterAndEdges(
            CenteredTreeView<String> tree,
            TreeItem<String> root,
            Stage stage,
            CountDownLatch completed,
            AtomicReference<Throwable> failure) {
        try {
            tree.layout();
            VirtualFlow<TreeCell<String>> flow = treeFlow(tree);
            TreeCell<String> middleCell = flow.getVisibleCell(tree.getRow(root.getChildren().get(15)));
            assertNotNull(middleCell);
            assertEquals(0.0, CenteredTreeView.centeringDelta(
                    middleCell.getLayoutY(), middleCell.getHeight(), flow.getHeight()), 1.0);

            TreeItem<String> first = root.getChildren().get(0);
            tree.getSelectionModel().select(first);
            tree.centerSelection();
            Platform.runLater(() -> verifyTopEdge(tree, root, stage, completed, failure));
        } catch (Throwable error) {
            failure.set(error);
            stage.hide();
            completed.countDown();
        }
    }

    private static void verifyTopEdge(
            CenteredTreeView<String> tree,
            TreeItem<String> root,
            Stage stage,
            CountDownLatch completed,
            AtomicReference<Throwable> failure) {
        try {
            tree.layout();
            assertEquals(0, treeFlow(tree).getFirstVisibleCell().getIndex());

            TreeItem<String> last = root.getChildren().get(29);
            tree.getSelectionModel().select(last);
            tree.centerSelection();
            Platform.runLater(() -> {
                try {
                    tree.layout();
                    assertEquals(29, treeFlow(tree).getLastVisibleCell().getIndex());
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    stage.hide();
                    completed.countDown();
                }
            });
        } catch (Throwable error) {
            failure.set(error);
            stage.hide();
            completed.countDown();
        }
    }

    @SuppressWarnings("unchecked")
    private static VirtualFlow<TreeCell<String>> treeFlow(CenteredTreeView<String> tree) {
        return (VirtualFlow<TreeCell<String>>) tree.lookup(".virtual-flow");
    }
}
