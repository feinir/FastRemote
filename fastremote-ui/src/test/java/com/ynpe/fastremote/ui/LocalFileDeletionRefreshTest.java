// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.Username;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LocalFileDeletionRefreshTest {
    private static final Path ROOT = Path.of(System.getProperty("user.home"))
            .toAbsolutePath().getRoot().resolve("local-delete-test");
    private static final Path REMOVED = ROOT.resolve("removed");
    private static final Path KEPT = ROOT.resolve("kept");

    @BeforeAll
    static void startToolkit() throws Exception {
        CompletableFuture<Void> started = new CompletableFuture<>();
        try { Platform.startup(() -> started.complete(null)); }
        catch (IllegalStateException alreadyStarted) { started.complete(null); }
        started.get(5, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
    }

    @Test
    void trashDeletionUpdatesTheLoadedLocalTreeInPlaceAndKeepsSiblings() throws Exception {
        Fixture fixture = new Fixture();
        LocalFileWorkspaceController controller = localController(fixture, ROOT);
        try {
            awaitFx(() -> controller.currentDirectory() != null && !tree(controller).getRoot().getChildren().isEmpty());
            fx(() -> { tree(controller).getRoot().getChildren().getFirst().setExpanded(true); return null; });
            awaitFx(() -> SftpBrowserModel.findLocalTreeItem(tree(controller).getRoot(), REMOVED) != null);
            TreeItem<LocalFileEntry> previousRoot = fx(() -> tree(controller).getRoot().getChildren().getFirst());
            TreeItem<LocalFileEntry> kept = fx(() -> SftpBrowserModel.findLocalTreeItem(previousRoot, KEPT));

            fx(() -> { delete(controller, List.of(entry(REMOVED))); return null; });

            awaitFx(() -> SftpBrowserModel.findLocalTreeItem(tree(controller).getRoot(), REMOVED) == null
                    && controller.currentDirectory() != null
                    && tree(controller).getSelectionModel().getSelectedItem() == previousRoot);
            fx(() -> {
                assertSame(previousRoot, tree(controller).getRoot().getChildren().getFirst());
                assertSame(kept, SftpBrowserModel.findLocalTreeItem(tree(controller).getRoot(), KEPT));
                assertTrue(previousRoot.isExpanded());
                assertEquals(ROOT, controller.currentDirectory());
                return null;
            });
        } finally { fx(() -> { controller.close(); return null; }); }
    }

    @Test
    void enteringALocalFolderExpandsItsTreeNodeFromTheLoadedListing() throws Exception {
        Fixture fixture = new Fixture();
        Path keptChild = KEPT.resolve("child");
        fixture.directories.add(keptChild);
        LocalFileWorkspaceController controller = localController(fixture, ROOT);
        try {
            awaitFx(() -> ROOT.equals(controller.currentDirectory())
                    && SftpBrowserModel.findLocalTreeItem(tree(controller).getRoot(), KEPT) != null);
            int listingsBefore = fixture.listings;
            fx(() -> { controller.navigateTo(KEPT); return null; });
            awaitFx(() -> {
                TreeItem<LocalFileEntry> kept = SftpBrowserModel.findLocalTreeItem(tree(controller).getRoot(), KEPT);
                return kept != null && kept.isExpanded()
                        && SftpBrowserModel.findLocalTreeItem(kept, keptChild) != null;
            });
            assertEquals(listingsBefore + 1, fixture.listings, "the tree reused the file list's listing");
        } finally { fx(() -> { controller.close(); return null; }); }
    }

    @Test
    void enteringAnSftpLocalFolderExpandsItsTreeNodeFromTheLoadedListing() throws Exception {
        Fixture fixture = new Fixture();
        Path keptChild = KEPT.resolve("child");
        fixture.directories.add(keptChild);
        OpenSession session = session();
        MainWindowController controller = mainController(fixture);
        try {
            TreeItem<LocalFileEntry> kept = fx(() -> {
                session.currentLocalPath = ROOT.toString();
                session.localTable = new TableView<>();
                session.localPathField = new TextField(session.currentLocalPath);
                session.localTree = new TreeView<>(new TreeItem<>());
                TreeItem<LocalFileEntry> root = new TreeItem<>(entry(ROOT));
                TreeItem<LocalFileEntry> keptItem = MainWindowController.localTreeItem(entry(KEPT));
                root.getChildren().add(keptItem);
                root.setExpanded(true);
                session.localTree.getRoot().getChildren().add(root);
                session.currentLocalPath = KEPT.toString();
                controller.refreshLocalAfterDeletion(session, Set.of());
                return keptItem;
            });
            awaitFx(() -> kept.isExpanded() && SftpBrowserModel.findLocalTreeItem(kept, keptChild) != null);
            assertEquals(1, fixture.listings, "the tree reused the file list's listing");
        } finally { fx(() -> { session.closed.set(true); session.fileLog.close(); return null; }); }
    }

    @Test
    void populateTreeChildrenFillsUnloadedNodesAndMergesLoadedOnes() throws Exception {
        fx(() -> {
            TreeItem<LocalFileEntry> unloaded = MainWindowController.localTreeItem(entry(ROOT));
            SftpBrowserModel.populateTreeChildren(unloaded, List.of(entry(KEPT)),
                    LocalFileEntry::path, MainWindowController::localTreeItem);
            assertEquals(KEPT, unloaded.getChildren().getFirst().getValue().path());
            TreeItem<LocalFileEntry> kept = unloaded.getChildren().getFirst();
            SftpBrowserModel.populateTreeChildren(unloaded, List.of(entry(KEPT), entry(REMOVED)),
                    LocalFileEntry::path, MainWindowController::localTreeItem);
            assertSame(kept, unloaded.getChildren().getFirst());
            assertEquals(2, unloaded.getChildren().size());
            return null;
        });
    }

    @Test
    void deletingCurrentLocalDirectoryReturnsToItsParentWithoutRecreatingIt() throws Exception {
        Fixture fixture = new Fixture();
        LocalFileWorkspaceController controller = localController(fixture, REMOVED);
        try {
            awaitFx(() -> REMOVED.equals(controller.currentDirectory())
                    && SftpBrowserModel.findLocalTreeItem(tree(controller).getRoot(), REMOVED) != null);
            fx(() -> { delete(controller, List.of(entry(REMOVED))); return null; });
            awaitFx(() -> ROOT.equals(controller.currentDirectory())
                    && SftpBrowserModel.findLocalTreeItem(tree(controller).getRoot(), REMOVED) == null);
        } finally { fx(() -> { controller.close(); return null; }); }
    }

    @Test
    void oldLocalTreeLoadCannotCompleteAfterTheTreeIsRebuiltOrClosed() throws Exception {
        for (boolean close : List.of(false, true)) {
            Fixture fixture = new Fixture();
            CompletableFuture<List<LocalFileEntry>> stale = new CompletableFuture<>();
            fixture.nextListing = stale;
            LocalFileWorkspaceController controller = new LocalFileWorkspaceController(
                    fixture.workflow, new Actions(), ROOT);
            TreeItem<LocalFileEntry> root = fx(() -> MainWindowController.localTreeItem(entry(ROOT)));
            var result = fx(() -> controller.ensureTreeNode(root, REMOVED).toCompletableFuture());
            fx(() -> {
                if (close) controller.close();
                else invoke(controller, "rebuildTree", new Class<?>[0]);
                stale.complete(List.of(entry(REMOVED)));
                return null;
            });
            awaitFx(result::isDone);
            assertTrue(result.isCompletedExceptionally());
            fx(() -> {
                assertTrue(SftpBrowserModel.hasTreePlaceholder(root));
                controller.close();
                return null;
            });
        }
    }

    @Test
    void sftpLocalDeletionLeavesDeletedAncestorAndRefreshesListAndTree() throws Exception {
        Fixture fixture = new Fixture();
        OpenSession session = session();
        MainWindowController controller = mainController(fixture);
        try {
            fx(() -> {
                session.currentLocalPath = REMOVED.resolve("nested").toString();
                session.localTable = new TableView<>();
                session.localPathField = new TextField(session.currentLocalPath);
                session.localTree = new TreeView<>(new TreeItem<>());
                TreeItem<LocalFileEntry> root = new TreeItem<>(entry(ROOT));
                root.getChildren().add(new TreeItem<>(entry(REMOVED)));
                session.localTree.getRoot().getChildren().add(root);
                fixture.directories.remove(REMOVED);
                controller.refreshLocalAfterDeletion(session, Set.of(REMOVED));
                return null;
            });
            awaitFx(() -> session.localTable.getItems().stream().anyMatch(item -> item.path().equals(KEPT)));
            fx(() -> {
                assertEquals(ROOT.toString(), session.currentLocalPath);
                assertEquals(ROOT.toString(), session.localPathField.getText());
                assertNull(SftpBrowserModel.findLocalTreeItem(session.localTree.getRoot(), REMOVED));
                return null;
            });
        } finally { fx(() -> { session.closed.set(true); session.fileLog.close(); return null; }); }
    }

    @Test
    void sftpLocalDeletionOnlyMergesCurrentFolderChildrenAndKeepsTreeState() throws Exception {
        Fixture fixture = new Fixture();
        Path keptChild = KEPT.resolve("child");
        fixture.directories.add(keptChild);
        OpenSession session = session();
        MainWindowController controller = mainController(fixture);
        try {
            TreeItem<LocalFileEntry>[] items = fx(() -> {
                session.currentLocalPath = ROOT.toString();
                session.localTable = new TableView<>();
                session.localPathField = new TextField(session.currentLocalPath);
                session.localTree = new TreeView<>(new TreeItem<>());
                TreeItem<LocalFileEntry> root = new TreeItem<>(entry(ROOT));
                TreeItem<LocalFileEntry> kept = new TreeItem<>(entry(KEPT));
                kept.getChildren().add(MainWindowController.localTreeItem(entry(keptChild)));
                root.getChildren().addAll(new TreeItem<>(entry(REMOVED)), kept);
                root.setExpanded(true);
                kept.setExpanded(true);
                session.localTree.getRoot().getChildren().add(root);
                fixture.directories.remove(REMOVED);
                controller.refreshLocalAfterDeletion(session, Set.of(REMOVED));
                @SuppressWarnings("unchecked")
                TreeItem<LocalFileEntry>[] result = new TreeItem[] {root, kept};
                return result;
            });
            awaitFx(() -> session.localTable.getItems().stream().anyMatch(item -> item.path().equals(KEPT)));
            fx(() -> {
                assertSame(items[0], session.localTree.getRoot().getChildren().getFirst());
                assertEquals(List.of(items[1]), items[0].getChildren());
                assertTrue(items[0].isExpanded() && items[1].isExpanded());
                assertEquals(keptChild, items[1].getChildren().getFirst().getValue().path());
                return null;
            });
        } finally { fx(() -> { session.closed.set(true); session.fileLog.close(); return null; }); }
    }

    @Test
    void mergeTreeChildrenKeepsSurvivorsAddsNewFoldersAndLeavesUnloadedNodes() throws Exception {
        fx(() -> {
            Path added = ROOT.resolve("added");
            TreeItem<LocalFileEntry> parent = new TreeItem<>(entry(ROOT));
            TreeItem<LocalFileEntry> kept = MainWindowController.localTreeItem(entry(KEPT));
            parent.getChildren().addAll(new TreeItem<>(entry(REMOVED)), kept);
            SftpBrowserModel.mergeTreeChildren(parent, List.of(entry(KEPT), entry(added)),
                    LocalFileEntry::path, MainWindowController::localTreeItem);
            assertSame(kept, parent.getChildren().get(0));
            assertEquals(added, parent.getChildren().get(1).getValue().path());
            assertEquals(2, parent.getChildren().size());

            TreeItem<LocalFileEntry> unloaded = MainWindowController.localTreeItem(entry(KEPT));
            SftpBrowserModel.mergeTreeChildren(unloaded, List.of(entry(added)),
                    LocalFileEntry::path, MainWindowController::localTreeItem);
            assertTrue(SftpBrowserModel.hasTreePlaceholder(unloaded));
            return null;
        });
    }

    @Test
    void olderSftpRootRefreshCannotOverwriteNewerTree() throws Exception {
        Fixture fixture = new Fixture();
        List<CompletableFuture<List<LocalFileEntry>>> requests = new ArrayList<>();
        fixture.rootRequests = requests;
        MainWindowController controller = mainController(fixture);
        OpenSession session = session();
        try {
            fx(() -> {
                session.currentLocalPath = ROOT.toString();
                session.localTree = new TreeView<>(new TreeItem<>());
                controller.refreshLocalTreeRoots(session);
                controller.refreshLocalTreeRoots(session);
                requests.get(1).complete(List.of(entry(ROOT)));
                return null;
            });
            awaitFx(() -> !session.localTree.getRoot().getChildren().isEmpty());
            TreeItem<LocalFileEntry> newest = fx(() -> session.localTree.getRoot().getChildren().getFirst());
            fx(() -> { requests.getFirst().complete(List.of(entry(REMOVED))); return null; });
            fx(() -> { assertSame(newest, session.localTree.getRoot().getChildren().getFirst()); return null; });
        } finally { fx(() -> { session.closed.set(true); session.fileLog.close(); return null; }); }
    }

    @Test
    void renamingDirectoryPreservesCurrentRelativePathWithoutChangingSiblings() {
        Path renamed = ROOT.resolve("renamed");
        assertEquals(renamed.resolve("nested"), SftpBrowserModel.localDirectoryAfterRename(
                REMOVED.resolve("nested"), REMOVED, renamed));
        assertEquals(KEPT, SftpBrowserModel.localDirectoryAfterRename(KEPT, REMOVED, renamed));
    }

    @Test
    void deletionNavigationKeepsUnaffectedOrFailedPathsAndHandlesNestedSelections() {
        assertEquals(KEPT, SftpBrowserModel.localDirectoryAfterDeletion(KEPT, Set.of(REMOVED)));
        assertEquals(REMOVED, SftpBrowserModel.localDirectoryAfterDeletion(REMOVED, Set.of()));
        assertEquals(ROOT, SftpBrowserModel.localDirectoryAfterDeletion(REMOVED.resolve("nested"),
                List.of(REMOVED.resolve("nested"), REMOVED)));
        assertEquals(ROOT, SftpBrowserModel.localDirectoryAfterDeletion(REMOVED.resolve("nested"),
                List.of(REMOVED, REMOVED.resolve("nested"))));
    }

    private static LocalFileWorkspaceController localController(Fixture fixture, Path path) throws Exception {
        return fx(() -> LocalFileWorkspaceView.load(fixture.workflow, new Actions(), path).controller());
    }

    @SuppressWarnings("unchecked")
    private static TreeView<LocalFileEntry> tree(LocalFileWorkspaceController controller) {
        try {
            var field = LocalFileWorkspaceController.class.getDeclaredField("tree");
            field.setAccessible(true);
            return (TreeView<LocalFileEntry>) field.get(controller);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void delete(LocalFileWorkspaceController controller, List<LocalFileEntry> entries) throws Exception {
        // Enter after the user's confirmation; exercise the actual asynchronous trash/delete completion.
        invoke(controller, "deleteNext", new Class<?>[] {List.class, int.class}, entries, 0);
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object... arguments) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(target, arguments);
    }

    private static MainWindowController mainController(Fixture fixture) throws Exception {
        return fx(() -> new MainWindowController(fixture.workflow, unused(HostCatalogWorkflow.class),
                unused(SettingsWorkflow.class), unused(SnippetWorkflow.class)));
    }

    private static <T> T unused(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> { throw new AssertionError("Unexpected call: " + method.getName()); }));
    }

    private static OpenSession session() {
        return new OpenSession("test", new ConnectionProfile(new Host("example.test"), new Port(22),
                new Username("user"), AuthenticationType.PASSWORD, Optional.empty()), WorkspaceViewModel.WorkspaceType.SFTP);
    }

    private static LocalFileEntry entry(Path path) {
        return new LocalFileEntry(path.getFileName().toString(), path, true, false, 0, Optional.empty());
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(5, TimeUnit.SECONDS);
    }

    private static void awaitFx(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!fx(condition::getAsBoolean)) {
            assertTrue(System.nanoTime() < deadline, "UI did not reach expected state");
            Thread.sleep(10);
        }
    }

    private static final class Actions implements LocalFileWorkspaceController.Actions {
        public void openPairedTerminal(Path directory) { }
        public void edit(LocalFileEntry entry) { }
        public void status(String message, boolean error) { }
        public void addFavorite(Path directory) { }
        public void directoryChanged(Path directory) { }
    }

    private static final class Fixture {
        final Set<Path> directories = new LinkedHashSet<>(List.of(ROOT, REMOVED, KEPT));
        CompletableFuture<List<LocalFileEntry>> nextListing;
        int listings;
        List<CompletableFuture<List<LocalFileEntry>>> rootRequests;
        final ConnectionWorkflow workflow = (ConnectionWorkflow) Proxy.newProxyInstance(
                ConnectionWorkflow.class.getClassLoader(), new Class<?>[] {ConnectionWorkflow.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "localRoots" -> {
                        if (rootRequests == null) yield CompletableFuture.completedFuture(List.of(entry(ROOT)));
                        var request = new CompletableFuture<List<LocalFileEntry>>();
                        rootRequests.add(request);
                        yield request;
                    }
                    case "listLocal" -> {
                        listings++;
                        if (nextListing != null) {
                            var request = nextListing;
                            nextListing = null;
                            yield request;
                        }
                        Path path = (Path) args[0];
                        yield CompletableFuture.completedFuture(directories.stream()
                                .filter(child -> path.equals(child.getParent())).map(LocalFileDeletionRefreshTest::entry).toList());
                    }
                    case "moveLocalToTrash" -> {
                        directories.remove((Path) args[0]);
                        yield CompletableFuture.completedFuture(true);
                    }
                    default -> throw new AssertionError("Unexpected call: " + method.getName());
                });
    }
}
