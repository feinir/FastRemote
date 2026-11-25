// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.ynpe.fastremote.domain.catalog.*;
import com.ynpe.fastremote.domain.connection.*;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HostLibraryTreeTest {
    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException running) { started.countDown(); }
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    @Test void viewsKeepIndependentQueriesAndSelectionsWhileRefreshingTheSameCatalog() throws Exception {
        fx(() -> {
            View dock = new View(), dialog = new View();
            SavedHost alpha = host("Alpha", Optional.empty(), 0);
            SavedHost beta = host("Beta", Optional.empty(), 10);
            HostCatalogSnapshot snapshot = catalog(List.of(), List.of(alpha, beta));
            dock.refresh(snapshot); dialog.refresh(snapshot);
            dock.tree.getSelectionModel().clearAndSelect(0);
            dialog.tree.getSelectionModel().clearAndSelect(1);
            SavedHost gamma = host("Gamma", Optional.empty(), 20);
            HostCatalogSnapshot updated = catalog(List.of(), List.of(gamma, beta, alpha));
            dock.refresh(updated); dialog.refresh(updated);
            assertEquals(alpha.id(), dock.selected().id());
            assertEquals(beta.id(), dialog.selected().id());
            assertEquals(3, dock.hosts.size()); assertEquals(3, dialog.hosts.size());
            assertNotSame(dock.tree.getRoot(), dialog.tree.getRoot());
            dock.query = "alpha"; dock.refresh(updated);
            dialog.query = "beta"; dialog.refresh(updated);
            assertEquals(List.of(alpha), List.copyOf(dock.hosts.values()));
            assertEquals(List.of(beta), List.copyOf(dialog.hosts.values()));
            assertEquals(alpha.id(), dock.selected().id());
            assertEquals(beta.id(), dialog.selected().id());
            HostCatalogSnapshot deleted = catalog(List.of(), List.of(alpha, gamma));
            dock.refresh(deleted); dialog.refresh(deleted);
            assertEquals("alpha", dock.query); assertEquals("beta", dialog.query);
            assertEquals(alpha.id(), dock.selected().id());
            assertTrue(dialog.tree.getSelectionModel().isEmpty());
            assertEquals(0, dialog.tree.getExpandedItemCount());
        });
    }

    @Test void bothViewsHideEmptyGroupsExpandSearchResultsAndKeepLocalMultiSelection() throws Exception {
        fx(() -> {
            HostGroup parent = new HostGroup(GroupId.create(), Optional.empty(), "Production", 0);
            HostGroup child = new HostGroup(GroupId.create(), Optional.of(parent.id()), "Database", 10);
            HostGroup empty = new HostGroup(GroupId.create(), Optional.empty(), "Empty", 20);
            SavedHost alpha = host("Alpha", Optional.of(child.id()), 0);
            SavedHost beta = host("Beta", Optional.of(child.id()), 10);
            HostCatalogSnapshot snapshot = catalog(List.of(parent, child, empty), List.of(alpha, beta));
            View dock = new View(), dialog = new View();
            dock.query = "Production"; dialog.query = "Database";
            dock.refresh(snapshot); dialog.refresh(snapshot);
            for (View view : List.of(dock, dialog)) {
                assertEquals(1, view.tree.getRoot().getChildren().size());
                assertEquals(4, view.tree.getExpandedItemCount());
                assertTrue(view.tree.getTreeItem(0).isExpanded());
                assertTrue(view.tree.getTreeItem(1).isExpanded());
            }
            dock.tree.getSelectionModel().clearSelection();
            dock.tree.getSelectionModel().selectIndices(2, 3);
            dialog.tree.getSelectionModel().clearAndSelect(1);
            dock.refresh(snapshot); dialog.refresh(snapshot);
            assertEquals(2, dock.tree.getSelectionModel().getSelectedItems().size());
            assertEquals(child.id(), dialog.groups.get(dialog.tree.getSelectionModel().getSelectedItem()).id());
            dock.query = ""; dock.refresh(snapshot);
            assertEquals(2, dock.tree.getRoot().getChildren().size());
            assertFalse(dock.tree.getTreeItem(0).isExpanded());
            assertEquals(4, dialog.tree.getExpandedItemCount());
        });
    }

    private static final class View {
        final TreeView<String> tree = new TreeView<>();
        final Map<TreeItem<String>, SavedHost> hosts = new IdentityHashMap<>();
        final Map<TreeItem<String>, HostGroup> groups = new IdentityHashMap<>();
        String query = "";
        View() { tree.setShowRoot(false); tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); }
        void refresh(HostCatalogSnapshot snapshot) {
            HostLibraryTree.rebuild(tree, hosts, groups, HostCatalogModel.filteredCatalog(snapshot, query),
                    (item, id) -> item.setExpanded(false),
                    root -> LibraryTreeSupport.hideEmptyGroupsWhileSearching(root, groups, query));
        }
        SavedHost selected() { return hosts.get(tree.getSelectionModel().getSelectedItem()); }
    }
    private static SavedHost host(String name, Optional<GroupId> group, int order) {
        return new SavedHost(HostId.create(), group, name,
                new ConnectionProfile(new Host("example.test"), new Port(22), new Username("test"),
                        AuthenticationType.PASSWORD, Optional.empty()),
                Optional.empty(), Optional.empty(), false, order, Instant.EPOCH, Instant.EPOCH, HostIcon.SERVER);
    }
    private static HostCatalogSnapshot catalog(List<HostGroup> groups, List<SavedHost> hosts) {
        return new HostCatalogSnapshot(groups, hosts, List.of(), VaultStatus.NOT_CREATED);
    }
    private static void fx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task); task.get(5, TimeUnit.SECONDS);
    }
}
