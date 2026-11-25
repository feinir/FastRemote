// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorkspacePaneCoordinatorTest {
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
    void topLevelTabCanContainMoreThanFourLogicalPanesAndTracksTheActivePane() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab terminal = pane("terminal");
            Tab sftp = pane("sftp");
            Tab editor = pane("editor");
            Tab local = pane("local");
            coordinator.addPane(terminal);
            coordinator.addPane(sftp);
            assertTrue(coordinator.mergeWorkspaceInto(coordinator.topLevelFor(sftp),
                    coordinator.topLevelFor(terminal), WorkspaceLayoutModel.DropEdge.RIGHT));
            assertTrue(coordinator.addPaneBeside(editor, terminal,
                    WorkspaceLayoutModel.DropEdge.BOTTOM, true));
            assertTrue(coordinator.addPaneBeside(local, sftp,
                    WorkspaceLayoutModel.DropEdge.BOTTOM, true));

            assertEquals(1, tabs.getTabs().size());
            assertEquals(4, coordinator.paneCount(terminal));
            assertSame(local, coordinator.selectedPane());
            assertTrue(coordinator.addPaneBeside(pane("fifth"), terminal,
                    WorkspaceLayoutModel.DropEdge.RIGHT, true));
            assertEquals(5, coordinator.paneCount(terminal));
        });
    }

    @Test
    void addingABackgroundPaneDoesNotStealTheActivePane() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab foreground = pane("foreground");
            Tab background = pane("background");
            coordinator.addPane(foreground);
            assertTrue(coordinator.addPaneBeside(background, foreground,
                    WorkspaceLayoutModel.DropEdge.RIGHT, false));
            assertSame(foreground, coordinator.selectedPane());
        });
    }

    @Test
    void longWorkspaceTitlesEllipsizeBeforeTheCloseButton() throws Exception {
        runOnFx(() -> {
            for (boolean dark : new boolean[] {false, true}) {
                for (boolean multiple : new boolean[] {false, true}) {
                    TabPane tabs = new TabPane();
                    tabs.getStyleClass().add("workspace-tabs");
                    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
                    List<Tab> closed = new ArrayList<>();
                    WorkspacePaneCoordinator coordinator = coordinator(tabs, closed);
                    String title = "fcinfo-1-Mac-Ubuntu24Arm-Ubuntu-long-file-name.txt";
                    Tab first = pane(title);
                    first.setGraphic(WorkspaceTabGraphics.editor());
                    coordinator.addPane(first);
                    if (multiple) coordinator.addPaneBeside(pane("sftp"), first,
                            WorkspaceLayoutModel.DropEdge.RIGHT, false);
                    var root = new javafx.scene.layout.StackPane(tabs);
                    var scene = new javafx.scene.Scene(root, 800, 300);
                    scene.getStylesheets().add(MainWindowController.class.getResource("main.css").toExternalForm());
                    if (dark) scene.getStylesheets().add(
                            MainWindowController.class.getResource("theme-dark.css").toExternalForm());
                    root.applyCss();
                    root.layout();
                    var outer = coordinator.topLevelFor(first);
                    HBox header = (HBox) outer.getGraphic();
                    Label label = (Label) header.getChildren().getLast();
                    Node close = tabs.lookup(".tab-close-button");
                    assertNotNull(close);
                    assertEquals(WorkspaceTabGraphics.ICON_SIZE, first.getGraphic().getLayoutBounds().getWidth());
                    assertEquals(multiple ? "[2] " + title : title, outer.getText());
                    assertTrue(label.getWidth() < label.prefWidth(-1), "long title must be constrained");
                    var rendered = (javafx.scene.text.Text) label.lookup(".text");
                    assertTrue(rendered.getText().endsWith("..."), rendered.getText());
                    assertTrue(header.localToScene(header.getBoundsInLocal()).getMaxX()
                            <= close.localToScene(close.getBoundsInLocal()).getMinX(),
                            "title and icon must leave the close button unobstructed");
                    assertTrue(label.localToScene(label.getBoundsInLocal()).getMaxX()
                            <= close.localToScene(close.getBoundsInLocal()).getMinX());
                    String snapshots = System.getenv("FASTREMOTE_TAB_SNAPSHOTS");
                    if (snapshots != null) {
                        try {
                            var target = java.nio.file.Path.of(snapshots);
                            java.nio.file.Files.createDirectories(target);
                            javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(
                                    root.snapshot(null, null), null), "png", target.resolve(
                                    "tab-" + (dark ? "dark" : "light") + (multiple ? "-multi" : "") + ".png").toFile());
                        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                    }
                    first.setText("short.txt");
                    coordinator.refreshPaneHeader(first);
                    root.applyCss();
                    root.layout();
                    assertEquals("short.txt", rendered.getText(), "short titles must remain complete");
                    coordinator.requestCloseWorkspace(outer);
                    assertEquals(multiple ? 2 : 1, closed.size());
                }
            }
        });
    }

    @Test
    void multiPaneCountAppearsInBracketsBeforeTheActivePaneIcon() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab first = pane("terminal");
            Label icon = new Label("icon");
            first.setGraphic(icon);
            coordinator.addPane(first);
            coordinator.addPaneBeside(pane("sftp"), first,
                    WorkspaceLayoutModel.DropEdge.RIGHT, false);

            Tab workspace = coordinator.topLevelFor(first);
            HBox header = (HBox) workspace.getGraphic();
            assertEquals("[2] terminal", workspace.getText());
            assertEquals(3, header.getChildren().size());
            assertEquals("[2]", ((Label) header.getChildren().get(0)).getText());
            assertSame(icon, header.getChildren().get(1));
            assertEquals("terminal", ((Label) header.getChildren().get(2)).getText());
        });
    }

    @Test
    void fourPaneWorkspaceUsesThreeResizableSplitControls() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab first = pane("first");
            Tab second = pane("second");
            Tab third = pane("third");
            Tab fourth = pane("fourth");
            coordinator.addPane(first);
            coordinator.addPaneBeside(second, first, WorkspaceLayoutModel.DropEdge.RIGHT, true);
            coordinator.addPaneBeside(third, first, WorkspaceLayoutModel.DropEdge.BOTTOM, true);
            coordinator.addPaneBeside(fourth, second, WorkspaceLayoutModel.DropEdge.BOTTOM, true);

            assertEquals(3, splitPaneCount(coordinator.topLevelFor(first).getContent()));
        });
    }

    @Test
    void paneContextMenuIsOwnedOnlyByItsHeader() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab pane = pane("terminal");
            coordinator.addPane(pane);
            javafx.scene.layout.BorderPane layoutHost = (javafx.scene.layout.BorderPane)
                    coordinator.topLevelFor(pane).getContent();
            javafx.scene.layout.StackPane layoutStack = (javafx.scene.layout.StackPane) layoutHost.getCenter();
            javafx.scene.layout.BorderPane paneRoot = (javafx.scene.layout.BorderPane)
                    layoutStack.getChildren().getFirst();
            assertEquals(null, paneRoot.getOnContextMenuRequested());
            assertTrue(paneRoot.getTop().getOnContextMenuRequested() != null);
        });
    }

    @Test
    void removingTheLastLogicalPaneRemovesItsTopLevelWorkspace() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab terminal = pane("terminal");
            coordinator.addPane(terminal);
            assertEquals(1, tabs.getTabs().size());
            assertTrue(coordinator.removePane(terminal));
            assertTrue(tabs.getTabs().isEmpty());
        });
    }

    @Test
    void closingAWorkspaceRequestsCloseForEveryContainedPane() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            List<Tab> closed = new ArrayList<>();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, closed);
            Tab terminal = pane("terminal");
            Tab sftp = pane("sftp");
            coordinator.addPane(terminal);
            coordinator.addPaneBeside(sftp, terminal, WorkspaceLayoutModel.DropEdge.RIGHT, true);
            coordinator.requestCloseWorkspace(coordinator.topLevelFor(terminal));
            assertEquals(List.of(terminal, sftp), closed);
        });
    }

    @Test
    void rejectedWorkspaceCloseLeavesEveryPaneOpen() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            List<Tab> closed = new ArrayList<>();
            WorkspacePaneCoordinator coordinator = new WorkspacePaneCoordinator(
                    tabs, closed::add, ignored -> false, ignored -> { }, () -> { });
            Tab terminal = pane("terminal");
            Tab sftp = pane("sftp");
            coordinator.addPane(terminal);
            coordinator.addPaneBeside(sftp, terminal, WorkspaceLayoutModel.DropEdge.RIGHT, true);

            coordinator.requestCloseWorkspace(coordinator.topLevelFor(terminal));

            assertTrue(closed.isEmpty());
            assertEquals(2, coordinator.paneCount(terminal));
            assertEquals(1, tabs.getTabs().size());
        });
    }

    @Test
    void mergingTwoPopulatedWorkspacesMovesAllPanesWithoutClosingThem() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            List<Tab> closed = new ArrayList<>();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, closed);
            Tab first = pane("first");
            Tab second = pane("second");
            Tab third = pane("third");
            Tab fourth = pane("fourth");
            coordinator.addPane(first);
            coordinator.addPaneBeside(second, first, WorkspaceLayoutModel.DropEdge.RIGHT, false);
            coordinator.addPane(third);
            coordinator.addPaneBeside(fourth, third, WorkspaceLayoutModel.DropEdge.BOTTOM, false);

            assertTrue(coordinator.mergeWorkspaceInto(coordinator.topLevelFor(third),
                    coordinator.topLevelFor(first), WorkspaceLayoutModel.DropEdge.BOTTOM));
            assertEquals(1, tabs.getTabs().size());
            assertEquals(4, coordinator.panes(first).size());
            assertTrue(coordinator.panes(first).containsAll(List.of(first, second, third, fourth)));
            assertTrue(closed.isEmpty());
        });
    }

    @Test
    void droppingWorkspaceOnTabCenterMergesWhenCombinedWorkspaceFits() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab source = pane("source");
            Tab target = pane("target");
            Tab targetSecond = pane("target-second");
            coordinator.addPane(source);
            coordinator.addPane(target);
            coordinator.addPaneBeside(targetSecond, target,
                    WorkspaceLayoutModel.DropEdge.BOTTOM, false);
            Tab sourceWorkspace = coordinator.topLevelFor(source);
            Tab targetWorkspace = coordinator.topLevelFor(target);

            assertTrue(coordinator.dropWorkspaceOnTab(sourceWorkspace, targetWorkspace,
                    WorkspacePaneCoordinator.TabDropZone.MERGE));
            assertEquals(1, tabs.getTabs().size());
            assertEquals(3, coordinator.paneCount(target));
            assertTrue(coordinator.panes(target).containsAll(List.of(source, target, targetSecond)));
        });
    }

    @Test
    void droppingWorkspaceOnPopulatedTabCenterIsNotLimitedToFourPanes() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab source = pane("source");
            Tab target = pane("target");
            coordinator.addPane(source);
            coordinator.addPane(target);
            coordinator.addPaneBeside(pane("two"), target,
                    WorkspaceLayoutModel.DropEdge.RIGHT, false);
            coordinator.addPaneBeside(pane("three"), target,
                    WorkspaceLayoutModel.DropEdge.BOTTOM, false);
            coordinator.addPaneBeside(pane("four"), target,
                    WorkspaceLayoutModel.DropEdge.LEFT, false);
            assertTrue(coordinator.dropWorkspaceOnTab(coordinator.topLevelFor(source),
                    coordinator.topLevelFor(target), WorkspacePaneCoordinator.TabDropZone.MERGE));
            assertEquals(1, tabs.getTabs().size());
            assertEquals(5, coordinator.paneCount(target));
        });
    }

    @Test
    void tabEdgeDropsReorderInsteadOfMerging() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab first = pane("first");
            Tab second = pane("second");
            Tab third = pane("third");
            coordinator.addPane(first);
            coordinator.addPane(second);
            coordinator.addPane(third);

            assertTrue(coordinator.dropWorkspaceOnTab(coordinator.topLevelFor(third),
                    coordinator.topLevelFor(first), WorkspacePaneCoordinator.TabDropZone.BEFORE));
            assertEquals(List.of(coordinator.topLevelFor(third), coordinator.topLevelFor(first),
                    coordinator.topLevelFor(second)), tabs.getTabs());
            assertEquals(3, tabs.getTabs().size());
        });
    }

    @Test
    void paneEdgeDropExtractsBeforeAnotherWorkspace() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab first = pane("first");
            Tab extracted = pane("extracted");
            Tab target = pane("target");
            coordinator.addPane(first);
            coordinator.addPaneBeside(extracted, first,
                    WorkspaceLayoutModel.DropEdge.RIGHT, false);
            coordinator.addPane(target);
            Tab sourceWorkspace = coordinator.topLevelFor(first);
            Tab targetWorkspace = coordinator.topLevelFor(target);

            assertTrue(coordinator.dropPaneOnTab(extracted, targetWorkspace,
                    WorkspacePaneCoordinator.TabDropZone.BEFORE));
            assertEquals(List.of(sourceWorkspace, coordinator.topLevelFor(extracted),
                    targetWorkspace), tabs.getTabs());
            assertEquals(1, coordinator.paneCount(first));
            assertEquals(1, coordinator.paneCount(extracted));
            assertSame(coordinator.topLevelFor(extracted),
                    tabs.getSelectionModel().getSelectedItem());
        });
    }

    @Test
    void paneEdgeDropExtractsAfterItsOwnWorkspace() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab first = pane("first");
            Tab extracted = pane("extracted");
            Tab last = pane("last");
            coordinator.addPane(first);
            coordinator.addPaneBeside(extracted, first,
                    WorkspaceLayoutModel.DropEdge.RIGHT, false);
            coordinator.addPane(last);
            Tab sourceWorkspace = coordinator.topLevelFor(first);
            Tab lastWorkspace = coordinator.topLevelFor(last);

            assertTrue(coordinator.dropPaneOnTab(extracted, sourceWorkspace,
                    WorkspacePaneCoordinator.TabDropZone.AFTER));
            assertEquals(List.of(sourceWorkspace, coordinator.topLevelFor(extracted),
                    lastWorkspace), tabs.getTabs());
            assertEquals(1, coordinator.paneCount(first));
            assertEquals(1, coordinator.paneCount(extracted));
        });
    }

    @Test
    void paneCenterDropStillMergesIntoAnotherWorkspace() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab source = pane("source");
            Tab sourceSecond = pane("source-second");
            Tab target = pane("target");
            coordinator.addPane(source);
            coordinator.addPaneBeside(sourceSecond, source,
                    WorkspaceLayoutModel.DropEdge.RIGHT, false);
            coordinator.addPane(target);

            assertTrue(coordinator.dropPaneOnTab(sourceSecond, coordinator.topLevelFor(target),
                    WorkspacePaneCoordinator.TabDropZone.MERGE));
            assertEquals(2, tabs.getTabs().size());
            assertEquals(1, coordinator.paneCount(source));
            assertEquals(2, coordinator.paneCount(target));
            assertTrue(coordinator.panes(target).containsAll(List.of(sourceSecond, target)));
        });
    }

    @Test
    void tabHeaderUsesStableBeforeMergeAndAfterDropZones() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab pane = pane("pane");
            coordinator.addPane(pane);

            assertSame(WorkspacePaneCoordinator.TabDropZone.BEFORE,
                    WorkspacePaneCoordinator.tabDropZone(10, 100));
            assertSame(WorkspacePaneCoordinator.TabDropZone.MERGE,
                    WorkspacePaneCoordinator.tabDropZone(50, 100));
            assertSame(WorkspacePaneCoordinator.TabDropZone.AFTER,
                    WorkspacePaneCoordinator.tabDropZone(90, 100));
            Node header = coordinator.topLevelFor(pane).getGraphic();
            assertEquals("pane", coordinator.topLevelFor(pane).getText());
            assertNotNull(header.getOnDragDetected());
        });
    }

    @Test
    void workspaceCanBeInsertedAfterTheLastTab() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab first = pane("first");
            Tab second = pane("second");
            Tab third = pane("third");
            coordinator.addPane(first);
            coordinator.addPane(second);
            coordinator.addPane(third);

            assertTrue(coordinator.moveWorkspaceToEnd(coordinator.topLevelFor(first)));
            assertEquals(List.of(coordinator.topLevelFor(second), coordinator.topLevelFor(third),
                    coordinator.topLevelFor(first)), tabs.getTabs());
            assertFalse(coordinator.moveWorkspaceToEnd(coordinator.topLevelFor(first)));
        });
    }

    @Test
    void directionalDropZonesFollowTheNearestPaneEdge() throws Exception {
        runOnFx(() -> {
            assertSame(WorkspaceLayoutModel.DropEdge.LEFT,
                    WorkspacePaneCoordinator.directionalDropEdge(5, 50, 100, 100));
            assertSame(WorkspaceLayoutModel.DropEdge.RIGHT,
                    WorkspacePaneCoordinator.directionalDropEdge(95, 50, 100, 100));
            assertSame(WorkspaceLayoutModel.DropEdge.TOP,
                    WorkspacePaneCoordinator.directionalDropEdge(50, 5, 100, 100));
            assertSame(WorkspaceLayoutModel.DropEdge.BOTTOM,
                    WorkspacePaneCoordinator.directionalDropEdge(50, 95, 100, 100));
        });
    }

    @Test
    void workspaceDragPayloadsCanBypassNestedContentDropTargets() {
        assertTrue(WorkspacePaneCoordinator.isWorkspaceDragPayload(
                Set.of(WorkspacePaneCoordinator.PANE_FORMAT)));
        assertTrue(WorkspacePaneCoordinator.isWorkspaceDragPayload(
                Set.of(WorkspacePaneCoordinator.WORKSPACE_FORMAT)));
        assertFalse(WorkspacePaneCoordinator.isWorkspaceDragPayload(
                Set.of(javafx.scene.input.DataFormat.FILES)));
        assertFalse(WorkspacePaneCoordinator.isWorkspaceDragPayload(
                Set.of(javafx.scene.input.DataFormat.PLAIN_TEXT)));
    }

    @Test
    void movingPaneToNewWorkspaceDetachesItAndUsesRequestedTabPosition() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab first = pane("first");
            Tab extracted = pane("extracted");
            Tab last = pane("last");
            coordinator.addPane(first);
            coordinator.addPaneBeside(extracted, first,
                    WorkspaceLayoutModel.DropEdge.RIGHT, false);
            coordinator.addPane(last);

            assertTrue(coordinator.movePaneToNewWorkspace(extracted, 0));
            assertEquals(List.of(coordinator.topLevelFor(extracted), coordinator.topLevelFor(first),
                    coordinator.topLevelFor(last)), tabs.getTabs());
            assertEquals(1, coordinator.paneCount(extracted));
            assertEquals(1, coordinator.paneCount(first));
        });
    }

    @Test
    void expandingWorkspaceCreatesOneTabPerPaneInVisualOrderAndKeepsActivePaneSelected()
            throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab before = pane("before");
            Tab first = pane("first");
            Tab second = pane("second");
            Tab third = pane("third");
            Tab after = pane("after");
            coordinator.addPane(before);
            coordinator.addPane(first);
            coordinator.addPaneBeside(second, first,
                    WorkspaceLayoutModel.DropEdge.RIGHT, false);
            coordinator.addPaneBeside(third, second,
                    WorkspaceLayoutModel.DropEdge.BOTTOM, true);
            Tab workspace = coordinator.topLevelFor(first);
            coordinator.addPane(after);

            assertTrue(coordinator.expandWorkspaceToTabs(workspace));

            assertEquals(List.of(coordinator.topLevelFor(before), coordinator.topLevelFor(first),
                    coordinator.topLevelFor(second), coordinator.topLevelFor(third),
                    coordinator.topLevelFor(after)), tabs.getTabs());
            assertSame(third, coordinator.selectedPane());
            assertSame(coordinator.topLevelFor(third),
                    tabs.getSelectionModel().getSelectedItem());
            assertEquals(1, coordinator.paneCount(first));
            assertEquals(1, coordinator.paneCount(second));
            assertEquals(1, coordinator.paneCount(third));
            assertFalse(coordinator.expandWorkspaceToTabs(coordinator.topLevelFor(first)));
        });
    }

    @Test
    void eachPaneContainsOneDirectionalHalfPanePreview() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab pane = pane("pane");
            coordinator.addPane(pane);
            javafx.scene.layout.BorderPane host = (javafx.scene.layout.BorderPane)
                    coordinator.topLevelFor(pane).getContent();
            javafx.scene.layout.StackPane paneContainer = (javafx.scene.layout.StackPane) host.getCenter();
            Node preview = paneContainer.getChildren().stream()
                    .filter(node -> node.getStyleClass().contains("workspace-pane-drop-preview"))
                    .findFirst().orElseThrow();

            assertFalse(preview.isVisible());
        });
    }

    @Test
    void activeOutlineIsOnlyShownWhenAWorkspaceHasMultiplePanes() throws Exception {
        runOnFx(() -> {
            TabPane tabs = new TabPane();
            WorkspacePaneCoordinator coordinator = coordinator(tabs, new ArrayList<>());
            Tab first = pane("first");
            coordinator.addPane(first);
            javafx.scene.layout.BorderPane host = (javafx.scene.layout.BorderPane)
                    coordinator.topLevelFor(first).getContent();
            javafx.scene.layout.StackPane stack = (javafx.scene.layout.StackPane) host.getCenter();
            Node firstRoot = stack.getChildren().getFirst();
            javafx.css.PseudoClass active = javafx.css.PseudoClass.getPseudoClass("active");

            assertFalse(firstRoot.getPseudoClassStates().contains(active));
            assertFalse(((javafx.scene.layout.BorderPane) firstRoot).getTop().isManaged());
            assertTrue(coordinator.addPaneBeside(pane("second"), first,
                    WorkspaceLayoutModel.DropEdge.RIGHT, false));
            assertTrue(firstRoot.getPseudoClassStates().contains(active));
            assertTrue(((javafx.scene.layout.BorderPane) firstRoot).getTop().isManaged());
        });
    }

    private static WorkspacePaneCoordinator coordinator(TabPane tabs, List<Tab> closed) {
        return new WorkspacePaneCoordinator(
                tabs, closed::add, ignored -> true, ignored -> { }, () -> { });
    }

    private static Tab pane(String title) {
        return new Tab(title, new Label(title));
    }

    private static long splitPaneCount(Node node) {
        if (node instanceof javafx.scene.layout.BorderPane border) {
            return splitPaneCount(border.getCenter());
        }
        if (node instanceof javafx.scene.layout.StackPane stack) {
            return stack.getChildren().stream()
                    .mapToLong(WorkspacePaneCoordinatorTest::splitPaneCount).sum();
        }
        if (!(node instanceof SplitPane split)) return 0;
        return 1 + split.getItems().stream().mapToLong(WorkspacePaneCoordinatorTest::splitPaneCount).sum();
    }

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }
}
