// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.LibraryTreeSupport.*;
import static com.ynpe.fastremote.ui.HostCatalogModel.*;
import static com.ynpe.fastremote.ui.UiFailureMessages.messageFor;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.snippet.SnippetVariable;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroup;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroupId;
import com.ynpe.fastremote.ui.LucideIconView.Icon;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.input.Dragboard;

/** Library view and drag state; edits are routed through narrow actions. */
final class VariableLibraryTree {
    interface Actions {
        void prepareLibrarySearch(TreeItem<String> root, Map<TreeItem<String>, ?> groups, String query);
        void registerGroupExpansion(TreeItem<String> item, SnippetVariableGroupId id);
        ContextMenu variableContextMenu(TreeItem<String> item);
        void editVariable(SnippetVariable variable);
        void refreshSnippets();
        void status(String message, boolean error);
    }
    private final TreeView<String> variableTree;
    private final TextField variableSearchField;
    private final Map<TreeItem<String>,SnippetVariable> variableItems;
    private final Map<TreeItem<String>,SnippetVariableGroup> variableGroupItems;
    private final java.util.function.Supplier<SnippetSnapshot> snippetSnapshot;
    private final SnippetWorkflow snippetWorkflow;
    private final Actions actions;
    private List<TreeItem<String>> draggedVariableItems = List.of();
    private List<TreeItem<String>> pressedVariableSelection = List.of();
    VariableLibraryTree(TreeView<String> tree, TextField search, Map<TreeItem<String>,SnippetVariable> items,
            Map<TreeItem<String>,SnippetVariableGroup> groups, java.util.function.Supplier<SnippetSnapshot> snapshot,
            SnippetWorkflow workflow, Actions actions) {
        this.variableTree=tree; this.variableSearchField=search; this.variableItems=items;
        this.variableGroupItems=groups; this.snippetSnapshot=snapshot; this.snippetWorkflow=workflow; this.actions=actions;
        
    }
    void initialize() {
        SearchTreeNavigation.install(variableSearchField, variableTree, () -> {});
        variableTree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        variableTree.setCellFactory(ignored -> createVariableTreeCell());
        variableTree.setContextMenu(withExpansionActions(actions.variableContextMenu(null), variableTree));
        variableTree.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                SnippetVariable variable = variableItems.get(variableTree.getSelectionModel().getSelectedItem());
                if (variable != null) actions.editVariable(variable);
                event.consume();
            }
        });
        variableSearchField.textProperty().addListener((ignored, previous, value) -> rebuild());
    }

    TreeCell<String> createVariableTreeCell() {
        TreeCell<String> cell = new StableGraphicTreeCell() {
            @Override protected Node createGraphic(TreeItem<String> treeItem) {
                SnippetVariableGroup group = variableGroupItems.get(treeItem);
                LucideIconView icon = new LucideIconView(group == null ? Icon.VARIABLE : Icon.FOLDER);
                if (group == null) icon.getStyleClass().setAll("catalog-item-icon", "snippet-code-icon");
                else icon.getStyleClass().setAll(
                        "catalog-item-icon", "catalog-group-icon", groupColorClass(group.color()));
                return icon;
            }
        };
        cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            TreeItem<String> item = cell.getTreeItem();
            pressedVariableSelection = event.getButton() == MouseButton.PRIMARY && item != null
                    && variableTree.getSelectionModel().getSelectedItems().contains(item)
                    ? List.copyOf(variableTree.getSelectionModel().getSelectedItems()) : List.of();
        });
        cell.setOnContextMenuRequested(event -> {
            TreeItem<String> item = cell.getTreeItem();
            if (item == null) return;
            if (!variableTree.getSelectionModel().getSelectedItems().contains(item)) {
                variableTree.getSelectionModel().clearAndSelect(cell.getIndex());
            }
            ContextMenu menu = withExpansionActions(actions.variableContextMenu(item), variableTree);
            menu.show(cell, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        cell.setOnDragDetected(event -> {
            if (cell.getTreeItem() == null || event.getButton() != MouseButton.PRIMARY) return;
            if (!variableSearchField.getText().isBlank()) {
                actions.status(com.ynpe.fastremote.i18n.Texts.text("legacy.java.95c671b69d6a2c08"), false);
                return;
            }
            draggedVariableItems = dragSelection(cell.getTreeItem(),
                    pressedVariableSelection.isEmpty()
                            ? List.copyOf(variableTree.getSelectionModel().getSelectedItems())
                            : pressedVariableSelection,
                    variableItems, variableGroupItems);
            if (draggedVariableItems.isEmpty()) return;
            Dragboard board = cell.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("fastremote/variable");
            board.setContent(content);
            event.consume();
        });
        cell.setOnDragOver(event -> {
            DropPosition position = flatLibraryDropPosition(event.getY(), cell.getHeight(),
                    variableGroupItems.containsKey(cell.getTreeItem()),
                    draggedVariableItems.stream().allMatch(variableGroupItems::containsKey));
            if (validFlatLibraryDropTarget(cell.getTreeItem(), position, draggedVariableItems,
                    variableItems, variableGroupItems)) {
                event.acceptTransferModes(TransferMode.MOVE);
                showDropTarget(cell, position);
                event.consume();
            } else clearDropTarget(cell);
        });
        cell.setOnDragExited(event -> clearDropTarget(cell));
        cell.setOnDragDropped(event -> {
            TreeItem<String> target = cell.getTreeItem();
            DropPosition position = flatLibraryDropPosition(event.getY(), cell.getHeight(),
                    variableGroupItems.containsKey(target),
                    draggedVariableItems.stream().allMatch(variableGroupItems::containsKey));
            clearDropTarget(cell);
            if (!validFlatLibraryDropTarget(target, position, draggedVariableItems,
                    variableItems, variableGroupItems)) return;
            List<SnippetVariable> moved = draggedVariableItems.stream()
                    .map(variableItems::get).filter(Objects::nonNull).toList();
            SnippetVariableGroup destination = variableGroupItems.get(target);
            CompletionStage<?> chain = CompletableFuture.completedFuture(null);
            if (!moved.isEmpty()) {
                SnippetVariable targetVariable = variableItems.get(target);
                Optional<SnippetVariableGroupId> groupId = destination != null ? Optional.of(destination.id())
                        : targetVariable == null ? Optional.empty() : targetVariable.groupId();
                Set<com.ynpe.fastremote.domain.snippet.SnippetVariableId> movedIds = moved.stream()
                        .map(SnippetVariable::id).collect(java.util.stream.Collectors.toSet());
                List<SnippetVariable> ordered = snippetSnapshot.get().variables().stream()
                        .filter(value -> value.groupId().equals(groupId) && !movedIds.contains(value.id()))
                        .sorted(Comparator.comparingInt(SnippetVariable::sortOrder))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                int index = targetVariable == null ? ordered.size() : Math.max(0, ordered.indexOf(targetVariable));
                if (targetVariable != null && position == DropPosition.AFTER) index++;
                ordered.addAll(index, moved);
                for (int i = 0; i < ordered.size(); i++) {
                    SnippetVariable value = ordered.get(i);
                    SnippetVariable changed = new SnippetVariable(value.id(), groupId, value.name(),
                            value.variableName(), value.type(), value.delimiter(), value.value(), value.description(),
                            i * 10, value.createdAt(), movedIds.contains(value.id()) ? Instant.now() : value.updatedAt());
                    chain = chain.thenCompose(ignored -> snippetWorkflow.saveVariable(changed));
                }
            } else if (destination != null) {
                List<SnippetVariableGroup> movedGroups = draggedVariableItems.stream()
                        .map(variableGroupItems::get).filter(Objects::nonNull).toList();
                Set<SnippetVariableGroupId> movedIds = movedGroups.stream().map(SnippetVariableGroup::id)
                        .collect(java.util.stream.Collectors.toSet());
                List<SnippetVariableGroup> ordered = new ArrayList<>(snippetSnapshot.get().variableGroups());
                ordered.removeIf(value -> movedIds.contains(value.id()));
                int index = Math.max(0, ordered.indexOf(destination));
                if (position == DropPosition.AFTER) index++;
                ordered.addAll(index, movedGroups);
                for (int i = 0; i < ordered.size(); i++) {
                    SnippetVariableGroup value = ordered.get(i);
                    int order = i * 10;
                    chain = chain.thenCompose(ignored -> snippetWorkflow.saveVariableGroup(
                            new SnippetVariableGroup(value.id(), value.name(), order, value.color())));
                }
            }
            chain.whenComplete((ignored, error) -> Platform.runLater(() -> {
                if (error != null) actions.status(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.4739891fa0f9187a")), true);
                actions.refreshSnippets();
            }));
            event.setDropCompleted(true);
            event.consume();
        });
        cell.setOnDragDone(event -> {
            draggedVariableItems = List.of();
            pressedVariableSelection = List.of();
            clearDropTarget(cell);
        });
        return cell;
    }

    void rebuild() {
        if (variableTree == null) return;
        String query = variableSearchField.getText() == null
                ? "" : variableSearchField.getText().strip().toLowerCase(Locale.ROOT);
        variableItems.clear();
        variableGroupItems.clear();
        TreeItem<String> root = new TreeItem<>(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a772fa4ebe36b63d"));
        Map<SnippetVariableGroupId, TreeItem<String>> groups = new LinkedHashMap<>();
        snippetSnapshot.get().variableGroups().stream()
                .sorted(Comparator.comparingInt(SnippetVariableGroup::sortOrder)
                        .thenComparing(SnippetVariableGroup::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(group -> {
                    TreeItem<String> node = new TreeItem<>(group.name());
                    actions.registerGroupExpansion(node, group.id());
                    groups.put(group.id(), node);
                    variableGroupItems.put(node, group);
                });
        snippetSnapshot.get().variables().stream().filter(variable -> query.isEmpty()
                        || variable.name().toLowerCase(Locale.ROOT).contains(query)
                        || variable.variableName().toLowerCase(Locale.ROOT).contains(query)
                        || variable.value().toLowerCase(Locale.ROOT).contains(query)
                        || variable.description().toLowerCase(Locale.ROOT).contains(query)
                        || variable.groupId().flatMap(id -> snippetSnapshot.get().variableGroups().stream()
                                .filter(group -> group.id().equals(id)).findFirst())
                                .map(SnippetVariableGroup::name).orElse("").toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparingInt(SnippetVariable::sortOrder)
                        .thenComparing(SnippetVariable::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(variable -> {
                    TreeItem<String> node = new TreeItem<>(variable.name() + "  ·  " + variable.variableName());
                    variableItems.put(node, variable);
                    variable.groupId().map(groups::get).ifPresentOrElse(
                            parent -> parent.getChildren().add(node), () -> root.getChildren().add(node));
                });
        groups.values().forEach(root.getChildren()::add);
        actions.prepareLibrarySearch(root, variableGroupItems, query);
        variableTree.setRoot(root);
    }
    private static String groupColorClass(GroupColor color) { return "catalog-group-"+color.name().toLowerCase(Locale.ROOT); }

}
