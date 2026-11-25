// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

/** Shared tree-cell rendering and drag target policy for the three catalog libraries. */
final class LibraryTreeSupport {
    private LibraryTreeSupport() { }

    enum DropPosition { BEFORE, AFTER, INTO }

    static ContextMenu withExpansionActions(ContextMenu menu, TreeView<?> tree) {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(tree, "tree");
        ApplicationContextMenus.style(menu);
        MenuItem expandAll = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d60cd4fb38d7d338"));
        expandAll.setOnAction(event -> setAllExpanded(tree, true));
        MenuItem collapseAll = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6268934b65c8014e"));
        collapseAll.setOnAction(event -> setAllExpanded(tree, false));
        if (!menu.getItems().isEmpty()) menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().addAll(expandAll, collapseAll);
        return menu;
    }

    static void setAllExpanded(TreeView<?> tree, boolean expanded) {
        Objects.requireNonNull(tree, "tree");
        setAllExpanded(tree.getRoot(), tree.isShowRoot(), expanded);
    }

    static void setAllExpanded(TreeItem<?> root, boolean showRoot, boolean expanded) {
        if (root == null) return;
        setExpandedRecursively(root, expanded);
        if (!showRoot) root.setExpanded(true);
    }

    private static void setExpandedRecursively(TreeItem<?> item, boolean expanded) {
        item.setExpanded(expanded);
        for (TreeItem<?> child : item.getChildren()) setExpandedRecursively(child, expanded);
    }

    static <T> void hideEmptyGroupsWhileSearching(
            TreeItem<T> root, Map<TreeItem<T>, ?> groups, String query) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(groups, "groups");
        if (query == null || query.isBlank()) return;
        removeEmptyGroupChildren(root, groups);
        setExpandedRecursively(root, true);
    }

    private static <T> void removeEmptyGroupChildren(
            TreeItem<T> parent, Map<TreeItem<T>, ?> groups) {
        for (TreeItem<T> child : List.copyOf(parent.getChildren())) {
            if (groups.containsKey(child)) removeEmptyGroupChildren(child, groups);
        }
        parent.getChildren().removeIf(child ->
                groups.containsKey(child) && child.getChildren().isEmpty());
    }

    static boolean validCatalogDropTarget(
            TreeItem<String> target, DropPosition position, TreeItem<String> dragged,
            Map<TreeItem<String>, SavedHost> hosts,
            Map<TreeItem<String>, HostGroup> groups) {
        if (target == dragged) return false;
        if (target == null) return dragged != null;
        if (hosts.containsKey(dragged)) {
            return hosts.containsKey(target) || (groups.containsKey(target) && position == DropPosition.INTO);
        }
        HostGroup targetGroup = groups.get(target);
        return groups.containsKey(dragged) && targetGroup != null
                && (position != DropPosition.INTO || targetGroup.parentId().isEmpty());
    }

    static <T> List<TreeItem<T>> dragSelection(
            TreeItem<T> clicked, List<TreeItem<T>> selected, Map<TreeItem<T>, ?> items, Map<TreeItem<T>, ?> groups) {
        if (clicked == null || (!items.containsKey(clicked) && !groups.containsKey(clicked))) return List.of();
        if (!selected.contains(clicked)) return List.of(clicked);
        return selected.stream().filter(item -> items.containsKey(item) || groups.containsKey(item)).distinct().toList();
    }

    static boolean validCatalogDropTarget(
            TreeItem<String> target, DropPosition position, List<TreeItem<String>> dragged,
            Map<TreeItem<String>, SavedHost> hosts,
            Map<TreeItem<String>, HostGroup> groups) {
        if (dragged.isEmpty()) return false;
        boolean hostsOnly = dragged.stream().allMatch(hosts::containsKey);
        boolean groupsOnly = dragged.stream().allMatch(groups::containsKey);
        return (hostsOnly || groupsOnly) && dragged.stream().allMatch(item ->
                validCatalogDropTarget(target, position, item, hosts, groups));
    }

    static DropPosition dropPosition(double pointerY, double cellHeight, boolean groupTarget) {
        if (cellHeight <= 0) return DropPosition.AFTER;
        double ratio = pointerY / cellHeight;
        if (groupTarget) {
            if (ratio < 0.28) return DropPosition.BEFORE;
            if (ratio > 0.72) return DropPosition.AFTER;
            return DropPosition.INTO;
        }
        return ratio < 0.5 ? DropPosition.BEFORE : DropPosition.AFTER;
    }

    static boolean validFlatLibraryDropTarget(
            TreeItem<String> target, DropPosition position, TreeItem<String> dragged,
            Map<TreeItem<String>, ?> items, Map<TreeItem<String>, ?> groups) {
        if (target == null || dragged == null || target == dragged) return false;
        if (items.containsKey(dragged)) {
            return items.containsKey(target)
                    || (groups.containsKey(target) && position == DropPosition.INTO);
        }
        return groups.containsKey(dragged) && groups.containsKey(target)
                && position != DropPosition.INTO;
    }

    static boolean validFlatLibraryDropTarget(
            TreeItem<String> target, DropPosition position, List<TreeItem<String>> dragged,
            Map<TreeItem<String>, ?> items, Map<TreeItem<String>, ?> groups) {
        if (dragged.isEmpty()) return false;
        boolean itemsOnly = dragged.stream().allMatch(items::containsKey);
        boolean groupsOnly = dragged.stream().allMatch(groups::containsKey);
        return (itemsOnly || groupsOnly) && dragged.stream().allMatch(item ->
                validFlatLibraryDropTarget(target, position, item, items, groups));
    }

    static DropPosition flatLibraryDropPosition(
            double pointerY, double cellHeight, boolean targetIsGroup, boolean draggedIsGroup) {
        return dropPosition(pointerY, cellHeight, targetIsGroup && !draggedIsGroup);
    }

    static void showDropTarget(TreeCell<String> cell, DropPosition position) {
        clearDropTarget(cell);
        cell.getStyleClass().add(switch (position) {
            case BEFORE -> "catalog-drop-before";
            case AFTER -> "catalog-drop-after";
            case INTO -> "catalog-drop-into";
        });
    }

    static void clearDropTarget(TreeCell<String> cell) {
        cell.getStyleClass().removeAll("catalog-drop-before", "catalog-drop-after", "catalog-drop-into");
    }

    /** Keeps an item's already styled icon node across selection and expansion refresh pulses. */
    abstract static class StableGraphicTreeCell extends TreeCell<String> {
        private final TreeCellGraphicCache<Node> graphicCache = new TreeCellGraphicCache<>();

        @Override
        protected final void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty ? null : item);
            TreeItem<String> treeItem = getTreeItem();
            if (empty || treeItem == null) {
                setGraphic(null);
                return;
            }
            setGraphic(graphicCache.valueFor(treeItem, this::createGraphic));
            setGraphicTextGap(7.0);
        }

        protected abstract Node createGraphic(TreeItem<String> treeItem);
    }

    static final class TreeCellGraphicCache<V> {
        private TreeItem<?> treeItem;
        private V value;

        <T> V valueFor(TreeItem<T> requested, Function<TreeItem<T>, V> factory) {
            Objects.requireNonNull(requested, "requested");
            Objects.requireNonNull(factory, "factory");
            if (requested != treeItem) {
                treeItem = requested;
                value = factory.apply(requested);
            }
            return value;
        }
    }

    static final class KeyedGraphicCache<K, V> {
        private K key;
        private V value;

        V valueFor(K requested, Supplier<V> factory) {
            Objects.requireNonNull(requested, "requested");
            Objects.requireNonNull(factory, "factory");
            if (!Objects.equals(requested, key)) {
                key = requested;
                value = factory.get();
            }
            return value;
        }
    }
}
