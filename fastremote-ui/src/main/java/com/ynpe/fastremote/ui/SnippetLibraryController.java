// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.LibraryTreeSupport.*;
import com.ynpe.fastremote.domain.snippet.*;
import com.ynpe.fastremote.ui.LucideIconView.Icon;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;

/** Shared command library view for the dock and the independent manager. */
public final class SnippetLibraryController {
    interface Actions {
        void importBuiltins();
        void createSnippet(Optional<SnippetGroupId> group);
        void createGroup();
        void importText();
        void send(Snippet snippet, boolean execute);
        void edit(Snippet snippet);
        void copy(Snippet snippet);
        void editGroup(SnippetGroup group);
        void deleteSnippets(List<Snippet> snippets);
        void deleteSelection(List<Snippet> snippets, List<SnippetGroup> groups);
        void refresh();
        CompletionStage<Snippet> saveSnippet(Snippet snippet);
        CompletionStage<SnippetGroup> saveGroup(SnippetGroup group);
        void registerExpansion(TreeItem<String> item, SnippetGroupId group);
        void prepareSearch(TreeItem<String> root, Map<TreeItem<String>, SnippetGroup> groups, String query);
        void showShortcut(MenuItem item, ShortcutAction action);
    }
    @FXML private TextField snippetSearchField;
    @FXML private TreeView<String> snippetTree;
    @FXML private Label snippetHelpLabel;
    private final Map<TreeItem<String>, Snippet> snippetItems = new IdentityHashMap<>();
    private final Map<TreeItem<String>, SnippetGroup> snippetGroupItems = new IdentityHashMap<>();
    private List<TreeItem<String>> draggedSnippetItems = List.of();
    private List<TreeItem<String>> pressedSnippetSelection = List.of();
    private SnippetSnapshot snippetSnapshot = new SnippetSnapshot(List.of(), List.of(), List.of());
    private Actions actions;
    private Runnable onDefaultCommand = () -> {};

    void onDefaultCommand(Runnable action) {
        onDefaultCommand = Objects.requireNonNull(action, "action");
    }

    private void openCommand(Snippet snippet) {
        actions.send(snippet, true);
        onDefaultCommand.run();
    }

    void bind(Actions value) {
        actions = Objects.requireNonNull(value, "actions");
        initializeTree();
    }
    void update(SnippetSnapshot snapshot) {
        snippetSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        rebuildSnippetTree();
    }
    @FXML private void importBuiltinCommands() { actions.importBuiltins(); }
    @FXML void createSnippet() {
        actions.createSnippet(Optional.ofNullable(snippetGroupItems.get(
                snippetTree.getSelectionModel().getSelectedItem())).map(SnippetGroup::id));
    }
    @FXML private void createSnippetGroup() { actions.createGroup(); }
    TextField searchField() { return snippetSearchField; }
    TreeView<String> tree() { return snippetTree; }
    Label helpLabel() { return snippetHelpLabel; }

    void shortcut(ShortcutAction action) {
        if (action == ShortcutAction.FOCUS_CONTEXT_SEARCH || action == ShortcutAction.OPEN_SNIPPET_LIBRARY) {
            snippetSearchField.requestFocus();
            return;
        }
        TreeItem<String> item = snippetTree.getSelectionModel().getSelectedItem();
        if (item == null) return;
        Snippet snippet = snippetItems.get(item);
        SnippetGroup group = snippetGroupItems.get(item);
        switch (action) {
            case OPEN_SELECTED_LIBRARY_ITEM -> {
                if (snippet != null) openCommand(snippet);
                else if (group != null) item.setExpanded(!item.isExpanded());
            }
            case EDIT_SELECTED_LIBRARY_ITEM -> {
                if (snippet != null) actions.edit(snippet);
                else if (group != null) actions.editGroup(group);
            }
            case COPY_SELECTED_LIBRARY_ITEM -> { if (snippet != null) actions.copy(snippet); }
            case DELETE_SELECTED_LIBRARY_ITEM -> deleteSnippetSelection();
            default -> { }
        }
    }

    private void deleteSnippetSelection() {
        List<TreeItem<String>> selected = List.copyOf(snippetTree.getSelectionModel().getSelectedItems());
        actions.deleteSelection(
                selected.stream().map(snippetItems::get).filter(Objects::nonNull).toList(),
                selected.stream().map(snippetGroupItems::get).filter(Objects::nonNull).toList());
    }

    private void initializeTree() {
        SearchTreeNavigation.install(snippetSearchField, snippetTree, () -> {});
        snippetTree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        snippetTree.setCellFactory(ignored -> createSnippetTreeCell());
        snippetTree.setContextMenu(withExpansionActions(snippetContextMenu(null), snippetTree));
        snippetTree.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                Snippet snippet = snippetItems.get(snippetTree.getSelectionModel().getSelectedItem());
                if (snippet != null) openCommand(snippet);
                event.consume();
            }
        });
        snippetSearchField.textProperty().addListener((ignored, previous, value) -> rebuildSnippetTree());

    }

    private TreeCell<String> createSnippetTreeCell() {
        TreeCell<String> cell = new StableGraphicTreeCell() {
            @Override protected Node createGraphic(TreeItem<String> treeItem) {
                LucideIconView icon;
                SnippetGroup group = snippetGroupItems.get(treeItem);
                if (group != null) {
                    icon = new LucideIconView(Icon.FOLDER);
                    icon.getStyleClass().setAll("catalog-item-icon", "catalog-group-icon", "catalog-group-" + group.color().name().toLowerCase(Locale.ROOT));
                } else {
                    icon = new LucideIconView(Icon.BRACES);
                    icon.getStyleClass().setAll("catalog-item-icon", "snippet-code-icon");
                }
                return icon;
            }
        };
        cell.setOnMouseEntered(event -> {
            Snippet snippet = snippetItems.get(cell.getTreeItem());
            cell.setTooltip(snippet == null || snippet.description().isBlank()
                    ? null : new Tooltip(snippet.description()));
        });
        cell.setOnMouseExited(event -> cell.setTooltip(null));
        cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            TreeItem<String> item = cell.getTreeItem();
            pressedSnippetSelection = event.getButton() == MouseButton.PRIMARY && item != null
                    && snippetTree.getSelectionModel().getSelectedItems().contains(item)
                    ? List.copyOf(snippetTree.getSelectionModel().getSelectedItems()) : List.of();
        });
        cell.setOnContextMenuRequested(event -> {
            TreeItem<String> item = cell.getTreeItem();
            if (item == null) return;
            if (!snippetTree.getSelectionModel().getSelectedItems().contains(item))
                snippetTree.getSelectionModel().clearAndSelect(cell.getIndex());
            ContextMenu menu = withExpansionActions(snippetContextMenu(item), snippetTree);
            menu.show(cell, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        cell.setOnDragDetected(event -> {
            if (cell.getTreeItem() == null || event.getButton() != MouseButton.PRIMARY) return;
            draggedSnippetItems = dragSelection(cell.getTreeItem(),
                    pressedSnippetSelection.isEmpty()
                            ? List.copyOf(snippetTree.getSelectionModel().getSelectedItems())
                            : pressedSnippetSelection,
                    snippetItems, snippetGroupItems);
            if (draggedSnippetItems.size() == 1
                    && !snippetTree.getSelectionModel().getSelectedItems().contains(cell.getTreeItem()))
                snippetTree.getSelectionModel().clearAndSelect(cell.getIndex());
            Dragboard board = cell.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent(); content.putString("fastremote/snippet"); board.setContent(content);
            event.consume();
        });
        cell.setOnDragOver(event -> {
            DropPosition position = flatLibraryDropPosition(event.getY(), cell.getHeight(),
                    snippetGroupItems.containsKey(cell.getTreeItem()),
                    draggedSnippetItems.stream().allMatch(snippetGroupItems::containsKey));
            if (validFlatLibraryDropTarget(cell.getTreeItem(), position, draggedSnippetItems,
                    snippetItems, snippetGroupItems)) {
                event.acceptTransferModes(TransferMode.MOVE);
                showDropTarget(cell, position);
                event.consume();
            } else clearDropTarget(cell);
        });
        cell.setOnDragExited(event -> clearDropTarget(cell));
        cell.setOnDragDropped(event -> {
            TreeItem<String> target = cell.getTreeItem();
            DropPosition position = flatLibraryDropPosition(event.getY(), cell.getHeight(),
                    snippetGroupItems.containsKey(target),
                    draggedSnippetItems.stream().allMatch(snippetGroupItems::containsKey));
            clearDropTarget(cell);
            if (!validFlatLibraryDropTarget(target, position, draggedSnippetItems,
                    snippetItems, snippetGroupItems)) return;
            List<Snippet> movedSnippets = draggedSnippetItems.stream()
                    .map(snippetItems::get).filter(Objects::nonNull).toList();
            SnippetGroup destination = snippetGroupItems.get(target);
            if (!movedSnippets.isEmpty()) {
                Snippet targetSnippet = snippetItems.get(target);
                Optional<SnippetGroupId> groupId = destination != null ? Optional.of(destination.id())
                        : targetSnippet == null ? Optional.empty() : targetSnippet.groupId();
                Set<SnippetId> movedIds = movedSnippets.stream().map(Snippet::id)
                        .collect(java.util.stream.Collectors.toSet());
                List<Snippet> ordered = snippetSnapshot.snippets().stream()
                        .filter(value -> value.groupId().equals(groupId) && !movedIds.contains(value.id()))
                        .sorted(Comparator.comparingInt(Snippet::sortOrder)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                int index = targetSnippet == null ? ordered.size() : Math.max(0, ordered.indexOf(targetSnippet));
                if (targetSnippet != null && position == DropPosition.AFTER) index++;
                ordered.addAll(index, movedSnippets);
                CompletionStage<?> chain = CompletableFuture.completedFuture(null);
                for (int i = 0; i < ordered.size(); i++) {
                    Snippet value = ordered.get(i); int order = i * 10;
                    Snippet changed = new Snippet(value.id(), groupId, value.name(), value.description(),
                            value.content(), order,
                            value.createdAt(), movedIds.contains(value.id()) ? Instant.now() : value.updatedAt());
                    chain = chain.thenCompose(ignored -> actions.saveSnippet(changed));
                }
                chain.whenComplete((done, error) -> Platform.runLater(actions::refresh));
                event.setDropCompleted(true);
            } else {
                List<SnippetGroup> movedGroups = draggedSnippetItems.stream()
                        .map(snippetGroupItems::get).filter(Objects::nonNull).toList();
                if (!movedGroups.isEmpty() && destination != null) {
                    List<SnippetGroup> ordered = new ArrayList<>(snippetSnapshot.groups());
                    Set<SnippetGroupId> movedIds = movedGroups.stream().map(SnippetGroup::id)
                            .collect(java.util.stream.Collectors.toSet());
                    ordered.removeIf(value -> movedIds.contains(value.id()));
                    int index = Math.max(0, ordered.indexOf(destination));
                    if (position == DropPosition.AFTER) index++;
                    ordered.addAll(index, movedGroups);
                    CompletionStage<?> chain = CompletableFuture.completedFuture(null);
                    for (int i = 0; i < ordered.size(); i++) {
                        SnippetGroup value = ordered.get(i); int order = i * 10;
                        chain = chain.thenCompose(ignored -> actions.saveGroup(
                                new SnippetGroup(value.id(), value.name(), order, value.color())));
                    }
                    chain.whenComplete((done, error) -> Platform.runLater(actions::refresh));
                    event.setDropCompleted(true);
                }
            }
            draggedSnippetItems = List.of(); event.consume();
        });
        cell.setOnDragDone(event -> {
            draggedSnippetItems = List.of();
            pressedSnippetSelection = List.of();
            clearDropTarget(cell);
        });
        return cell;
    }

    private ContextMenu snippetContextMenu(TreeItem<String> item) {
        Snippet snippet = snippetItems.get(item);
        SnippetGroup group = snippetGroupItems.get(item);
        if (snippet != null) {
            MenuItem execute = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.32c8d373afe08328"));
            execute.setOnAction(event -> actions.send(snippet, true));
            actions.showShortcut(execute, ShortcutAction.OPEN_SELECTED_LIBRARY_ITEM);
            MenuItem paste = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e7027d39deec594b"));
            paste.setOnAction(event -> actions.send(snippet, false));
            MenuItem edit = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.051836569928a9f9"));
            edit.setOnAction(event -> actions.edit(snippet));
            actions.showShortcut(edit, ShortcutAction.EDIT_SELECTED_LIBRARY_ITEM);
            MenuItem copy = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.63d90d977348ab1f"));
            copy.setOnAction(event -> actions.copy(snippet));
            actions.showShortcut(copy, ShortcutAction.COPY_SELECTED_LIBRARY_ITEM);
            MenuItem importText = snippetImportMenuItem();
            List<Snippet> selected = snippetTree.getSelectionModel().getSelectedItems().stream()
                    .map(snippetItems::get).filter(Objects::nonNull).toList();
            MenuItem delete = new MenuItem(selected.size() > 1 ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.435bf8a234fde6f2") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.2f9daa828907b93f"));
            delete.setOnAction(event -> actions.deleteSnippets(selected.isEmpty() ? List.of(snippet) : selected));
            actions.showShortcut(delete, ShortcutAction.DELETE_SELECTED_LIBRARY_ITEM);
            return ApplicationContextMenus.create(execute, paste, new SeparatorMenuItem(), edit, copy, delete,
                    new SeparatorMenuItem(), importText);
        }
        MenuItem create = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.cbd43842cef0016c"));
        create.setOnAction(event -> actions.createSnippet(Optional.ofNullable(group).map(SnippetGroup::id)));
        MenuItem importText = snippetImportMenuItem();
        if (group == null) return ApplicationContextMenus.create(create, importText);
        MenuItem rename = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d00b2a96c3b35930"));
        rename.setOnAction(event -> actions.editGroup(group));
        actions.showShortcut(rename, ShortcutAction.EDIT_SELECTED_LIBRARY_ITEM);
        MenuItem delete = new MenuItem(snippetTree.getSelectionModel().getSelectedItems().size() > 1
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.435bf8a234fde6f2") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.7605c1d82eed69a5"));
        delete.setOnAction(event -> deleteSnippetSelection());
        actions.showShortcut(delete, ShortcutAction.DELETE_SELECTED_LIBRARY_ITEM);
        return ApplicationContextMenus.create(create, importText, new SeparatorMenuItem(), rename, delete);
    }

    private MenuItem snippetImportMenuItem() {
        MenuItem item = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("config.import.title"));
        item.setOnAction(event -> actions.importText());
        return item;
    }

    void rebuildSnippetTree() {
        if (snippetTree == null) return;
        String query = snippetSearchField.getText() == null
                ? "" : snippetSearchField.getText().strip().toLowerCase(Locale.ROOT);
        snippetItems.clear();
        snippetGroupItems.clear();
        TreeItem<String> root = new TreeItem<>(com.ynpe.fastremote.i18n.Texts.text("legacy.java.928f87d4507bf9a0"));
        Map<SnippetGroupId, TreeItem<String>> groupNodes = new java.util.LinkedHashMap<>();
        snippetSnapshot.groups().stream().sorted(Comparator.comparingInt(SnippetGroup::sortOrder)
                        .thenComparing(SnippetGroup::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(group -> {
                    TreeItem<String> node = new TreeItem<>(group.name());
                    actions.registerExpansion(node, group.id());
                    groupNodes.put(group.id(), node);
                    snippetGroupItems.put(node, group);
                });
        snippetSnapshot.snippets().stream().filter(snippet -> query.isEmpty()
                        || snippet.name().toLowerCase(Locale.ROOT).contains(query)
                        || snippet.description().toLowerCase(Locale.ROOT).contains(query)
                        || snippet.content().toLowerCase(Locale.ROOT).contains(query)
                        || snippet.groupId().flatMap(id -> snippetSnapshot.groups().stream()
                                .filter(group -> group.id().equals(id)).findFirst()).map(SnippetGroup::name)
                                .orElse("").toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparingInt(Snippet::sortOrder).thenComparing(Snippet::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(snippet -> {
                    TreeItem<String> node = new TreeItem<>(snippet.name());
                    snippetItems.put(node, snippet);
                    snippet.groupId().map(groupNodes::get).ifPresentOrElse(
                            parent -> parent.getChildren().add(node), () -> root.getChildren().add(node));
                });
        groupNodes.values().forEach(root.getChildren()::add);
        actions.prepareSearch(root, snippetGroupItems, query);
        snippetTree.setRoot(root);
    }

}
