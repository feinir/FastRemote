// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

/** One rendering policy, with selection and node maps owned independently by each host-library view. */
final class HostLibraryTree {
    private HostLibraryTree() { }

    static Optional<String> selectedNames(TreeView<String> tree,
            Map<TreeItem<String>, SavedHost> hosts, Map<TreeItem<String>, HostGroup> groups) {
        String names = tree.getSelectionModel().getSelectedItems().stream()
                .map(item -> {
                    SavedHost host = hosts.get(item);
                    HostGroup group = groups.get(item);
                    return host != null ? host.name() : group != null ? group.name() : "";
                })
                .filter(name -> !name.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"));
        return names.isEmpty() ? Optional.empty() : Optional.of(names);
    }

    static void rebuild(TreeView<String> tree, Map<TreeItem<String>, SavedHost> hosts,
            Map<TreeItem<String>, HostGroup> groups, HostCatalogSnapshot filtered,
            BiConsumer<TreeItem<String>, GroupId> registerExpansion,
            Consumer<TreeItem<String>> prepareSearch) {
        Set<HostId> selectedHosts = new HashSet<>();
        Set<GroupId> selectedGroups = new HashSet<>();
        for (TreeItem<String> item : List.copyOf(tree.getSelectionModel().getSelectedItems())) {
            SavedHost host = hosts.get(item);
            HostGroup group = groups.get(item);
            if (host != null) selectedHosts.add(host.id());
            if (group != null) selectedGroups.add(group.id());
        }
        hosts.clear();
        groups.clear();
        TreeItem<String> root = new TreeItem<>(com.ynpe.fastremote.i18n.Texts.text("action.hostLibrary"));
        root.setExpanded(true);
        Map<GroupId, TreeItem<String>> nodes = new HashMap<>();
        List<HostGroup> ordered = filtered.groups().stream()
                .sorted(Comparator.comparingInt(HostGroup::sortOrder)
                        .thenComparing(HostGroup::name, String.CASE_INSENSITIVE_ORDER)).toList();
        for (HostGroup group : ordered) {
            TreeItem<String> item = new TreeItem<>(group.name());
            registerExpansion.accept(item, group.id());
            nodes.put(group.id(), item);
            groups.put(item, group);
        }
        for (HostGroup group : ordered) {
            group.parentId().map(nodes::get).orElse(root).getChildren().add(nodes.get(group.id()));
        }
        filtered.hosts().stream().sorted(Comparator.comparingInt(SavedHost::sortOrder)
                .thenComparing(SavedHost::name, String.CASE_INSENSITIVE_ORDER)).forEach(host -> {
            TreeItem<String> item = new TreeItem<>(HostCatalogModel.hostDisplayName(host));
            hosts.put(item, host);
            host.groupId().map(nodes::get).orElse(root).getChildren().add(item);
        });
        prepareSearch.accept(root);
        tree.setRoot(root);
        // Restore only visible matches in this view; never import another view's selection.
        for (int row = 0; row < tree.getExpandedItemCount(); row++) {
            TreeItem<String> item = tree.getTreeItem(row);
            SavedHost host = hosts.get(item);
            HostGroup group = groups.get(item);
            if ((host != null && selectedHosts.contains(host.id()))
                    || (group != null && selectedGroups.contains(group.id()))) {
                tree.getSelectionModel().select(row);
            }
        }
        if (tree.getSelectionModel().isEmpty() && tree.getExpandedItemCount() > 0) {
            tree.getSelectionModel().select(0);
        }
    }
}
