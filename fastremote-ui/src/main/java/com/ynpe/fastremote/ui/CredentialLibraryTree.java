// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.LibraryTreeSupport.*;
import static com.ynpe.fastremote.ui.HostCatalogModel.*;
import static com.ynpe.fastremote.ui.UiFailureMessages.messageFor;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.vault.SecretId;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import com.ynpe.fastremote.domain.credential.CredentialGroup;
import com.ynpe.fastremote.domain.credential.CredentialGroupId;
import com.ynpe.fastremote.ui.LucideIconView.Icon;
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
import javafx.scene.layout.VBox;

/** Library view and drag state; edits are routed through narrow actions. */
final class CredentialLibraryTree {
    interface Actions {
        void prepareLibrarySearch(TreeItem<String> root, Map<TreeItem<String>, ?> groups, String query);
        void registerGroupExpansion(TreeItem<String> item, CredentialGroupId id);
        ContextMenu credentialLibraryContextMenu();
        ContextMenu credentialGroupContextMenu(CredentialGroup group);
        ContextMenu credentialContextMenu(GlobalCredential credential);
        void editReusableCredential(GlobalCredential credential);
        void refreshCatalog();
    }
    private final TreeView<String> credentialTree;
    private final TextField credentialSearchField;
    private final Map<TreeItem<String>,GlobalCredential> credentialItems;
    private final Map<TreeItem<String>,CredentialGroup> credentialGroupItems;
    private final java.util.function.Supplier<HostCatalogSnapshot> catalog;
    private final HostCatalogWorkflow catalogWorkflow;
    private final Actions actions;
    private List<TreeItem<String>> draggedCredentialItems = List.of();
    private List<TreeItem<String>> pressedCredentialSelection = List.of();
    private final javafx.scene.layout.VBox credentialEmpty;
    CredentialLibraryTree(TreeView<String> tree, TextField search, Map<TreeItem<String>,GlobalCredential> items,
            Map<TreeItem<String>,CredentialGroup> groups, java.util.function.Supplier<HostCatalogSnapshot> snapshot,
            HostCatalogWorkflow workflow, Actions actions, javafx.scene.layout.VBox empty) {
        this.credentialTree=tree; this.credentialSearchField=search; this.credentialItems=items;
        this.credentialGroupItems=groups; this.catalog=snapshot; this.catalogWorkflow=workflow; this.actions=actions;
        this.credentialEmpty=empty;
    }
    void initialize() {
        SearchTreeNavigation.install(credentialSearchField, credentialTree, () -> {});
        credentialTree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        credentialTree.setContextMenu(withExpansionActions(actions.credentialLibraryContextMenu(), credentialTree));
        credentialTree.setCellFactory(ignored -> createCredentialTreeCell());
        credentialTree.setOnMouseClicked(event -> {
            GlobalCredential selected = credentialItems.get(credentialTree.getSelectionModel().getSelectedItem());
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && selected != null) {
                actions.editReusableCredential(selected);
                event.consume();
            }
        });
        credentialSearchField.textProperty().addListener((ignored, previous, value) -> rebuild());
    }

    TreeCell<String> createCredentialTreeCell() {
        TreeCell<String> cell = new StableGraphicTreeCell() {
            @Override protected Node createGraphic(TreeItem<String> treeItem) {
                CredentialGroup group = credentialGroupItems.get(treeItem);
                LucideIconView icon;
                if (group != null) {
                    icon = new LucideIconView(Icon.FOLDER);
                    icon.getStyleClass().setAll("catalog-item-icon", "catalog-group-icon", groupColorClass(group.color()));
                } else {
                    icon = new LucideIconView(Icon.LOCK_KEYHOLE);
                    icon.getStyleClass().setAll("catalog-item-icon", "credential-lock-icon");
                }
                return icon;
            }
        };

        cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            TreeItem<String> item = cell.getTreeItem();
            pressedCredentialSelection = event.getButton() == MouseButton.PRIMARY && item != null
                    && credentialTree.getSelectionModel().getSelectedItems().contains(item)
                    ? List.copyOf(credentialTree.getSelectionModel().getSelectedItems()) : List.of();
        });
        cell.setOnContextMenuRequested(event -> {
            TreeItem<String> item = cell.getTreeItem();
            if (item == null) return;
            if (!credentialTree.getSelectionModel().getSelectedItems().contains(item)) {
                credentialTree.getSelectionModel().clearAndSelect(cell.getIndex());
            }
            GlobalCredential credential = credentialItems.get(item);
            ContextMenu menu = withExpansionActions(
                    credential == null ? actions.credentialGroupContextMenu(credentialGroupItems.get(item))
                            : actions.credentialContextMenu(credential),
                    credentialTree);
            menu.show(cell, event.getScreenX(), event.getScreenY()); event.consume();
        });
        cell.setOnDragDetected(event -> {
            if (cell.getTreeItem() == null || event.getButton() != MouseButton.PRIMARY) return;
            draggedCredentialItems = dragSelection(cell.getTreeItem(),
                    pressedCredentialSelection.isEmpty()
                            ? List.copyOf(credentialTree.getSelectionModel().getSelectedItems())
                            : pressedCredentialSelection,
                    credentialItems, credentialGroupItems);
            if (draggedCredentialItems.size() == 1
                    && !credentialTree.getSelectionModel().getSelectedItems().contains(cell.getTreeItem()))
                credentialTree.getSelectionModel().clearAndSelect(cell.getIndex());
            Dragboard board = cell.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent(); content.putString("fastremote/credential"); board.setContent(content);
            event.consume();
        });
        cell.setOnDragOver(event -> {
            DropPosition position = flatLibraryDropPosition(event.getY(), cell.getHeight(),
                    credentialGroupItems.containsKey(cell.getTreeItem()),
                    draggedCredentialItems.stream().allMatch(credentialGroupItems::containsKey));
            if (validFlatLibraryDropTarget(cell.getTreeItem(), position, draggedCredentialItems,
                    credentialItems, credentialGroupItems)) {
                event.acceptTransferModes(TransferMode.MOVE);
                showDropTarget(cell, position);
                event.consume();
            } else clearDropTarget(cell);
        });
        cell.setOnDragExited(event -> clearDropTarget(cell));
        cell.setOnDragDropped(event -> {
            TreeItem<String> target = cell.getTreeItem();
            DropPosition position = flatLibraryDropPosition(event.getY(), cell.getHeight(),
                    credentialGroupItems.containsKey(target),
                    draggedCredentialItems.stream().allMatch(credentialGroupItems::containsKey));
            clearDropTarget(cell);
            if (!validFlatLibraryDropTarget(target, position, draggedCredentialItems,
                    credentialItems, credentialGroupItems)) return;
            CredentialGroup destination = credentialGroupItems.get(target);
            List<GlobalCredential> movedCredentials = draggedCredentialItems.stream()
                    .map(credentialItems::get).filter(Objects::nonNull).toList();
            if (!movedCredentials.isEmpty()) {
                GlobalCredential targetCredential = credentialItems.get(target);
                Optional<CredentialGroupId> groupId = destination != null ? Optional.of(destination.id())
                        : targetCredential == null ? Optional.empty() : targetCredential.groupId();
                Set<com.ynpe.fastremote.domain.vault.SecretId> movedIds = movedCredentials.stream()
                        .map(GlobalCredential::id).collect(java.util.stream.Collectors.toSet());
                List<GlobalCredential> ordered = catalog.get().credentials().stream()
                        .filter(value -> value.groupId().equals(groupId) && !movedIds.contains(value.id()))
                        .sorted(Comparator.comparingInt(GlobalCredential::sortOrder)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                int index = targetCredential == null ? ordered.size() : Math.max(0, ordered.indexOf(targetCredential));
                if (targetCredential != null && position == DropPosition.AFTER) index++;
                ordered.addAll(index, movedCredentials);
                CompletionStage<?> chain = CompletableFuture.completedFuture(null);
                for (int i = 0; i < ordered.size(); i++) {
                    GlobalCredential value = ordered.get(i); int order = i * 10;
                    chain = chain.thenCompose(ignored -> catalogWorkflow.moveReusableCredential(value, groupId, order));
                }
                chain.whenComplete((done, error) -> Platform.runLater(actions::refreshCatalog));
                event.setDropCompleted(true);
            } else {
                List<CredentialGroup> movedGroups = draggedCredentialItems.stream()
                        .map(credentialGroupItems::get).filter(Objects::nonNull).toList();
                if (!movedGroups.isEmpty() && destination != null) {
                    List<CredentialGroup> ordered = new ArrayList<>(catalog.get().credentialGroups());
                    Set<CredentialGroupId> movedIds = movedGroups.stream().map(CredentialGroup::id)
                            .collect(java.util.stream.Collectors.toSet());
                    ordered.removeIf(value -> movedIds.contains(value.id()));
                    int index = Math.max(0, ordered.indexOf(destination));
                    if (position == DropPosition.AFTER) index++;
                    ordered.addAll(index, movedGroups);
                    CompletionStage<?> chain = CompletableFuture.completedFuture(null);
                    for (int i = 0; i < ordered.size(); i++) {
                        CredentialGroup value = ordered.get(i); int order = i * 10;
                        chain = chain.thenCompose(ignored -> catalogWorkflow.saveCredentialGroup(
                                new CredentialGroup(value.id(), value.name(), order, value.color())));
                    }
                    chain.whenComplete((done, error) -> Platform.runLater(actions::refreshCatalog));
                    event.setDropCompleted(true);
                }
            }
            draggedCredentialItems = List.of(); event.consume();
        });
        cell.setOnDragDone(event -> {
            draggedCredentialItems = List.of();
            pressedCredentialSelection = List.of();
            clearDropTarget(cell);
        });
        return cell;
    }

    void rebuild() {
        if (credentialTree == null) return;
        String query = credentialSearchField.getText() == null ? "" : credentialSearchField.getText().strip().toLowerCase(Locale.ROOT);
        credentialItems.clear(); credentialGroupItems.clear();
        TreeItem<String> root = new TreeItem<>(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bb2b5d546de68220"));
        Map<CredentialGroupId, TreeItem<String>> groups = new java.util.LinkedHashMap<>();
        catalog.get().credentialGroups().stream().sorted(Comparator.comparingInt(CredentialGroup::sortOrder)).forEach(group -> {
            TreeItem<String> item = new TreeItem<>(group.name());
            actions.registerGroupExpansion(item, group.id());
            groups.put(group.id(), item); credentialGroupItems.put(item, group);
        });
        catalog.get().credentials().stream().filter(value -> credentialMatchesSearch(
                        value, catalog.get().credentialGroups(), query))
                .sorted(Comparator.comparingInt(GlobalCredential::sortOrder)).forEach(value -> {
                    TreeItem<String> item = new TreeItem<>(value.label() + "  ·  " + value.username());
                    credentialItems.put(item, value);
                    value.groupId().map(groups::get).ifPresentOrElse(parent -> parent.getChildren().add(item),
                            () -> root.getChildren().add(item));
                });
        groups.values().forEach(root.getChildren()::add);
        actions.prepareLibrarySearch(root, credentialGroupItems, query);
        credentialTree.setRoot(root);
        boolean hasContent = !root.getChildren().isEmpty();
        credentialTree.setVisible(hasContent); credentialTree.setManaged(hasContent);
        credentialEmpty.setVisible(!hasContent); credentialEmpty.setManaged(!hasContent);
    }
    private static String groupColorClass(GroupColor color) { return "catalog-group-"+color.name().toLowerCase(Locale.ROOT); }

}
