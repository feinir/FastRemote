// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Unbounded recursive split tree for panes inside one top-level workspace tab. */
final class WorkspaceLayoutModel<T> {
    enum DropEdge { TOP, RIGHT, BOTTOM, LEFT, CENTER }

    enum Axis { HORIZONTAL, VERTICAL }

    sealed interface LayoutNode<T> permits PaneNode, SplitNode { }

    record PaneNode<T>(T item) implements LayoutNode<T> {
        PaneNode {
            Objects.requireNonNull(item, "item");
        }
    }

    record SplitNode<T>(Axis axis, List<LayoutNode<T>> children) implements LayoutNode<T> {
        SplitNode {
            Objects.requireNonNull(axis, "axis");
            children = List.copyOf(children);
            if (children.size() < 2) throw new IllegalArgumentException("A split needs at least two children");
        }
    }

    private LayoutNode<T> root;

    int size() {
        return itemsInVisualOrder().size();
    }

    boolean isEmpty() {
        return root == null;
    }

    boolean contains(T item) {
        return root != null && contains(root, item);
    }

    LayoutNode<T> root() {
        if (root == null) throw new IllegalStateException("Workspace is empty");
        return root;
    }

    List<T> itemsInVisualOrder() {
        if (root == null) return List.of();
        List<T> result = new ArrayList<>();
        collect(root, result);
        return List.copyOf(result);
    }

    void addInitial(T item) {
        Objects.requireNonNull(item, "item");
        if (root != null) throw new IllegalStateException("Workspace is not empty");
        root = new PaneNode<>(item);
    }

    boolean add(T item, T target, DropEdge edge) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(edge, "edge");
        if (contains(item)) return false;
        if (root == null) {
            addInitial(item);
            return true;
        }
        T resolvedTarget = target != null && contains(target)
                ? target : itemsInVisualOrder().getFirst();
        DropEdge resolvedEdge = edge == DropEdge.CENTER ? DropEdge.RIGHT : edge;
        root = insert(root, item, resolvedTarget, resolvedEdge);
        return true;
    }

    boolean move(T item, T target, DropEdge edge) {
        Objects.requireNonNull(edge, "edge");
        if (Objects.equals(item, target) || !contains(item) || !contains(target)) return false;
        if (edge == DropEdge.CENTER) {
            root = swap(root, item, target);
            return true;
        }
        root = removeNode(root, item);
        root = insert(root, item, target, edge);
        return true;
    }

    boolean remove(T item) {
        if (!contains(item)) return false;
        root = removeNode(root, item);
        return true;
    }

    private LayoutNode<T> insert(LayoutNode<T> node, T item, T target, DropEdge edge) {
        if (node instanceof PaneNode<T> pane) {
            if (!pane.item().equals(target)) return pane;
            Axis axis = axis(edge);
            PaneNode<T> added = new PaneNode<>(item);
            return before(edge)
                    ? new SplitNode<>(axis, List.of(added, pane))
                    : new SplitNode<>(axis, List.of(pane, added));
        }
        SplitNode<T> split = (SplitNode<T>) node;
        Axis requestedAxis = axis(edge);
        int targetIndex = directChildContaining(split, target);
        if (split.axis() == requestedAxis && targetIndex >= 0
                && split.children().get(targetIndex) instanceof PaneNode<?>) {
            List<LayoutNode<T>> children = new ArrayList<>(split.children());
            children.add(targetIndex + (before(edge) ? 0 : 1), new PaneNode<>(item));
            return new SplitNode<>(split.axis(), children);
        }
        List<LayoutNode<T>> children = new ArrayList<>(split.children().size());
        for (LayoutNode<T> child : split.children()) children.add(insert(child, item, target, edge));
        return new SplitNode<>(split.axis(), children);
    }

    private int directChildContaining(SplitNode<T> split, T item) {
        for (int index = 0; index < split.children().size(); index++) {
            if (contains(split.children().get(index), item)) return index;
        }
        return -1;
    }

    private LayoutNode<T> removeNode(LayoutNode<T> node, T item) {
        if (node instanceof PaneNode<T> pane) return pane.item().equals(item) ? null : pane;
        SplitNode<T> split = (SplitNode<T>) node;
        List<LayoutNode<T>> children = new ArrayList<>();
        for (LayoutNode<T> child : split.children()) {
            LayoutNode<T> remaining = removeNode(child, item);
            if (remaining != null) children.add(remaining);
        }
        if (children.isEmpty()) return null;
        if (children.size() == 1) return children.getFirst();
        return new SplitNode<>(split.axis(), flatten(split.axis(), children));
    }

    private List<LayoutNode<T>> flatten(Axis axis, List<LayoutNode<T>> children) {
        List<LayoutNode<T>> result = new ArrayList<>();
        for (LayoutNode<T> child : children) {
            if (child instanceof SplitNode<T> nested && nested.axis() == axis) {
                result.addAll(nested.children());
            } else {
                result.add(child);
            }
        }
        return result;
    }

    private LayoutNode<T> swap(LayoutNode<T> node, T first, T second) {
        if (node instanceof PaneNode<T> pane) {
            if (pane.item().equals(first)) return new PaneNode<>(second);
            if (pane.item().equals(second)) return new PaneNode<>(first);
            return pane;
        }
        SplitNode<T> split = (SplitNode<T>) node;
        return new SplitNode<>(split.axis(), split.children().stream()
                .map(child -> swap(child, first, second)).toList());
    }

    private boolean contains(LayoutNode<T> node, T item) {
        if (node instanceof PaneNode<T> pane) return pane.item().equals(item);
        return ((SplitNode<T>) node).children().stream().anyMatch(child -> contains(child, item));
    }

    private void collect(LayoutNode<T> node, List<T> result) {
        if (node instanceof PaneNode<T> pane) {
            result.add(pane.item());
            return;
        }
        for (LayoutNode<T> child : ((SplitNode<T>) node).children()) collect(child, result);
    }

    private Axis axis(DropEdge edge) {
        return edge == DropEdge.LEFT || edge == DropEdge.RIGHT ? Axis.HORIZONTAL : Axis.VERTICAL;
    }

    private boolean before(DropEdge edge) {
        return edge == DropEdge.LEFT || edge == DropEdge.TOP;
    }
}
