// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.IdentityHashMap;
import java.util.Map;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;

class LibraryTreeSupportTest {
    @Test
    void blankSearchKeepsEmptyGroups() {
        TreeItem<String> root = new TreeItem<>("root");
        TreeItem<String> empty = new TreeItem<>("empty");
        root.getChildren().add(empty);
        Map<TreeItem<String>, Object> groups = new IdentityHashMap<>();
        groups.put(empty, new Object());

        LibraryTreeSupport.hideEmptyGroupsWhileSearching(root, groups, "   ");

        assertEquals(java.util.List.of(empty), root.getChildren());
        assertFalse(empty.isExpanded());
    }

    @Test
    void activeSearchRemovesEmptyGroupsRecursivelyButKeepsMatchingBranchesAndUngroupedItems() {
        TreeItem<String> root = new TreeItem<>("root");
        TreeItem<String> emptyParent = new TreeItem<>("empty parent");
        TreeItem<String> emptyChild = new TreeItem<>("empty child");
        emptyParent.getChildren().add(emptyChild);
        TreeItem<String> populatedParent = new TreeItem<>("populated parent");
        TreeItem<String> populatedChild = new TreeItem<>("populated child");
        TreeItem<String> groupedResult = new TreeItem<>("grouped result");
        TreeItem<String> ungroupedResult = new TreeItem<>("ungrouped result");
        populatedChild.getChildren().add(groupedResult);
        populatedParent.getChildren().add(populatedChild);
        root.getChildren().setAll(java.util.List.of(emptyParent, populatedParent, ungroupedResult));
        Map<TreeItem<String>, Object> groups = new IdentityHashMap<>();
        groups.put(emptyParent, new Object());
        groups.put(emptyChild, new Object());
        groups.put(populatedParent, new Object());
        groups.put(populatedChild, new Object());

        LibraryTreeSupport.hideEmptyGroupsWhileSearching(root, groups, "result");

        assertEquals(java.util.List.of(populatedParent, ungroupedResult), root.getChildren());
        assertEquals(java.util.List.of(populatedChild), populatedParent.getChildren());
        assertEquals(java.util.List.of(groupedResult), populatedChild.getChildren());
        assertTrue(root.isExpanded());
        assertTrue(populatedParent.isExpanded());
        assertTrue(populatedChild.isExpanded());
    }
}
