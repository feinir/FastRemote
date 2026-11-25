// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorkspaceLayoutModelTest {
    @Test
    void recursiveSplitTreeHasNoFixedPaneLimit() {
        WorkspaceLayoutModel<String> layout = new WorkspaceLayoutModel<>();
        layout.addInitial("pane-0");
        for (int index = 1; index < 12; index++) {
            WorkspaceLayoutModel.DropEdge edge = index % 2 == 0
                    ? WorkspaceLayoutModel.DropEdge.BOTTOM : WorkspaceLayoutModel.DropEdge.RIGHT;
            assertTrue(layout.add("pane-" + index, "pane-" + (index - 1), edge));
        }

        assertEquals(12, layout.size());
        assertEquals(12, layout.itemsInVisualOrder().stream().distinct().count());
        assertFalse(layout.add("pane-0", "pane-11", WorkspaceLayoutModel.DropEdge.RIGHT));
    }

    @Test
    void sameDirectionSplitsShareOneSplitter() {
        WorkspaceLayoutModel<String> layout = new WorkspaceLayoutModel<>();
        layout.addInitial("a");
        layout.add("b", "a", WorkspaceLayoutModel.DropEdge.RIGHT);
        layout.add("c", "b", WorkspaceLayoutModel.DropEdge.RIGHT);

        WorkspaceLayoutModel.SplitNode<?> root = assertInstanceOf(
                WorkspaceLayoutModel.SplitNode.class, layout.root());
        assertEquals(WorkspaceLayoutModel.Axis.HORIZONTAL, root.axis());
        assertEquals(3, root.children().size());
        assertEquals(List.of("a", "b", "c"), layout.itemsInVisualOrder());
    }

    @Test
    void perpendicularDropCreatesNestedSplitAtTheTargetPane() {
        WorkspaceLayoutModel<String> layout = new WorkspaceLayoutModel<>();
        layout.addInitial("left");
        layout.add("right", "left", WorkspaceLayoutModel.DropEdge.RIGHT);
        layout.add("bottom-left", "left", WorkspaceLayoutModel.DropEdge.BOTTOM);

        WorkspaceLayoutModel.SplitNode<?> root = assertInstanceOf(
                WorkspaceLayoutModel.SplitNode.class, layout.root());
        assertEquals(WorkspaceLayoutModel.Axis.HORIZONTAL, root.axis());
        WorkspaceLayoutModel.SplitNode<?> left = assertInstanceOf(
                WorkspaceLayoutModel.SplitNode.class, root.children().getFirst());
        assertEquals(WorkspaceLayoutModel.Axis.VERTICAL, left.axis());
        assertEquals(List.of("left", "bottom-left", "right"), layout.itemsInVisualOrder());
    }

    @Test
    void removingPaneCollapsesRedundantSplitters() {
        WorkspaceLayoutModel<String> layout = new WorkspaceLayoutModel<>();
        layout.addInitial("left");
        layout.add("right", "left", WorkspaceLayoutModel.DropEdge.RIGHT);
        layout.add("bottom-left", "left", WorkspaceLayoutModel.DropEdge.BOTTOM);

        assertTrue(layout.remove("bottom-left"));
        WorkspaceLayoutModel.SplitNode<?> root = assertInstanceOf(
                WorkspaceLayoutModel.SplitNode.class, layout.root());
        assertEquals(WorkspaceLayoutModel.Axis.HORIZONTAL, root.axis());
        assertEquals(2, root.children().size());
        assertEquals(List.of("left", "right"), layout.itemsInVisualOrder());
    }

    @Test
    void directionalMoveRelocatesPaneBesideTargetAndCenterMoveSwaps() {
        WorkspaceLayoutModel<String> layout = new WorkspaceLayoutModel<>();
        layout.addInitial("a");
        layout.add("b", "a", WorkspaceLayoutModel.DropEdge.RIGHT);
        layout.add("c", "b", WorkspaceLayoutModel.DropEdge.RIGHT);

        assertTrue(layout.move("a", "c", WorkspaceLayoutModel.DropEdge.RIGHT));
        assertEquals(List.of("b", "c", "a"), layout.itemsInVisualOrder());
        assertTrue(layout.move("a", "b", WorkspaceLayoutModel.DropEdge.CENTER));
        assertEquals(List.of("a", "c", "b"), layout.itemsInVisualOrder());
    }
}
