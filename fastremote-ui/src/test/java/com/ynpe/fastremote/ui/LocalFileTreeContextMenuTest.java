// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DialogPane;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalFileTreeContextMenuTest {
    private static final Path ROOT = Path.of(System.getProperty("user.home")).toAbsolutePath()
            .getRoot().resolve("local-menu-test");
    private static final Path NODE = ROOT.resolve("node");
    private static final Path OTHER = ROOT.resolve("other");
    private static final Path SELECTED = ROOT.resolve("selected.txt");
    private Fixture fixture;
    private LocalFileWorkspaceView.Loaded loaded;
    private Map<DataFormat, Object> clipboard;

    @BeforeAll
    static void startToolkit() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        try { Platform.startup(() -> ready.complete(null)); }
        catch (IllegalStateException started) { ready.complete(null); }
        ready.get(5, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
    }

    @BeforeEach
    void load() throws Exception {
        fx(() -> {
            clipboard = new LinkedHashMap<>();
            Clipboard system = Clipboard.getSystemClipboard();
            system.getContentTypes().forEach(type -> {
                Object content = system.getContent(type);
                // X11 may advertise a format whose owner cannot supply its content.
                if (content != null) clipboard.put(type, content);
            });
            system.clear();
            fixture = new Fixture();
            loaded = LocalFileWorkspaceView.load(fixture, fixture, ROOT);
            return null;
        });
        awaitFx(() -> ROOT.equals(loaded.controller().currentDirectory()));
        fx(() -> { table().getSelectionModel().select(entry(SELECTED, false)); return null; });
    }

    @AfterEach
    void close() throws Exception {
        fx(() -> {
            if (loaded != null) loaded.controller().close();
            if (clipboard != null) Clipboard.getSystemClipboard().setContent(clipboard);
            return null;
        });
    }

    @Test
    void fileAndDirectoryMenusDisplayTheSameCommonKeyboardShortcuts() throws Exception {
        fx(() -> {
            for (ContextMenu menu : List.of(treeMenu(),
                    loaded.controller().contextMenu(entry(SELECTED, false), false))) {
                for (var shortcut : Map.of("cut", "Shortcut+X", "copy", "Shortcut+C",
                        "paste", "Shortcut+V", "delete", "Delete", "refresh", "F5",
                        "newFolder", "Shortcut+Shift+N", "rename",
                        ShortcutAction.renameDefault(System.getProperty("os.name", ""))).entrySet()) {
                    assertEquals(javafx.scene.input.KeyCombination.valueOf(shortcut.getValue()),
                            item(menu, shortcut.getKey()).getAccelerator());
                }
                var hidden = menu.getItems().stream().filter(value -> com.ynpe.fastremote.i18n.Texts.text("sftp.context.hiddenFiles").equals(value.getText())).findFirst().orElseThrow();
                assertEquals(javafx.scene.input.KeyCombination.valueOf("Alt+Shift+H"), hidden.getAccelerator());
            }
            var menu = loaded.controller().contextMenu(entry(SELECTED, false), false);
            var edit = menu.getItems().stream().filter(value -> "editor.edit".equals(value.getId()))
                    .findFirst().orElseThrow();
            assertEquals(javafx.scene.input.KeyCombination.valueOf("Shortcut+E"), edit.getAccelerator());
            return null;
        });
    }

    @Test
    void refreshShortcutRunsOnceWithoutDispatchingToRemoteSftp() throws Exception {
        int reads = fx(() -> fixture.rootReads);
        fx(() -> {
            var remoteDispatches = new java.util.concurrent.atomic.AtomicInteger();
            var scene = new javafx.scene.Scene(loaded.root());
            var manager = new KeyboardShortcutManager(scene, ignored -> remoteDispatches.incrementAndGet());
            manager.apply(com.ynpe.fastremote.domain.settings.KeyboardShortcutSettings.defaults());
            var event = new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "", "", javafx.scene.input.KeyCode.F5, false, false, false, false);
            javafx.event.Event.fireEvent(table(), event);
            assertEquals(0, remoteDispatches.get());
            return null;
        });
        awaitFx(() -> fixture.rootReads > reads);
        fx(() -> { assertEquals(reads + 1, fixture.rootReads); return null; });
    }

    @Test
    void treeMenuUsesClickedNodeForCopyAndFavoriteRegardlessOfTableSelection() throws Exception {
        fx(() -> {
            ContextMenu menu = treeMenu();
            for (String key : List.of("newFolder", "newFile", "delete", "rename", "copy", "cut", "refresh")) {
                assertFalse(item(menu, key).isDisable(), key);
            }
            item(menu, "copyPath").fire();
            assertEquals(NODE.toString(), Clipboard.getSystemClipboard().getString());
            item(menu, "copyName").fire();
            assertEquals("node", Clipboard.getSystemClipboard().getString());
            item(menu, "copy").fire();
            assertEquals(List.of(NODE.toFile()), Clipboard.getSystemClipboard().getFiles());
            item(menu, "addFavorite").fire();
            assertEquals(List.of(NODE), fixture.favorites);
            assertEquals(ROOT, loaded.controller().currentDirectory());
            return null;
        });
    }

    @Test
    void paletteFreezesSelectionAndDirectoryAndRejectsAClosedWorkspace() throws Exception {
        var rejected = new java.util.concurrent.atomic.AtomicInteger();
        var entries = fx(() -> loaded.controller().paletteEntries(
                com.ynpe.fastremote.i18n.Localizer.forLanguage(
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.ENGLISH),
                false, table(), "Local files", () -> true, rejected::incrementAndGet));
        assertEquals(8, entries.size());
        fx(() -> {
            table().getSelectionModel().clearSelection();
            loaded.controller().navigateTo(OTHER);
            return null;
        });
        awaitFx(() -> OTHER.equals(loaded.controller().currentDirectory()));
        fx(() -> {
            entries.stream().filter(entry -> entry.usageKey().endsWith(":COPY_PATH")).findFirst().orElseThrow().action().run();
            assertEquals(SELECTED.toString(), Clipboard.getSystemClipboard().getString());
            var favorite = entries.stream().filter(entry -> entry.usageKey().endsWith(":FAVORITE")).findFirst().orElseThrow();
            favorite.action().run();
            assertEquals(List.of(ROOT), fixture.favorites);
            loaded.controller().close();
            favorite.action().run();
            assertEquals(1, rejected.get());
            assertEquals(List.of(ROOT), fixture.favorites);
            return null;
        });
    }

    @Test
    void treeCreationUsesCapturedDirectoryAndActualNamesAfterNavigation() throws Exception {
        ContextMenu menu = fx(this::treeMenu);
        fx(() -> {
            fixture.entries.put(NODE.resolve("NewFolder"), true);
            loaded.controller().navigateTo(OTHER);
            return null;
        });
        awaitFx(() -> OTHER.equals(loaded.controller().currentDirectory()));
        String proposed = answerDialog(() -> item(menu, "newFolder").fire(), null, ButtonType.OK);
        assertEquals("NewFolder (2)", proposed);
        awaitFx(() -> fixture.calls.contains("mkdir:" + NODE.resolve("NewFolder (2)")));
        answerDialog(() -> item(menu, "newFile").fire(), "created.txt", ButtonType.OK);
        awaitFx(() -> fixture.calls.contains("create:" + NODE.resolve("created.txt")));
    }

    @Test
    void renameUsesSiblingPathAndMovesCurrentDescendantToNewLocation() throws Exception {
        fx(() -> { loaded.controller().navigateTo(NODE.resolve("nested")); return null; });
        awaitFx(() -> NODE.resolve("nested").equals(loaded.controller().currentDirectory()));
        ContextMenu menu = fx(this::treeMenu);
        answerDialog(() -> item(menu, "rename").fire(), "renamed", ButtonType.OK);
        awaitFx(() -> ROOT.resolve("renamed/nested").equals(loaded.controller().currentDirectory()));
        fx(() -> {
            assertTrue(fixture.calls.contains("rename:" + NODE + "->" + ROOT.resolve("renamed")));
            assertTrue(fixture.entries.containsKey(SELECTED));
            return null;
        });
    }

    @Test
    void deleteTargetsTreeNodeAndKeepsUnrelatedSelectedFile() throws Exception {
        ContextMenu menu = fx(this::treeMenu);
        answerDialog(() -> item(menu, "delete").fire(), null, ButtonType.OK);
        awaitFx(() -> fixture.calls.contains("trash:" + NODE));
        fx(() -> {
            assertTrue(fixture.entries.containsKey(SELECTED));
            assertFalse(fixture.entries.containsKey(NODE));
            return null;
        });
    }

    @Test
    void pasteFreezesDestinationAcrossNavigationAndRefreshesTree() throws Exception {
        fx(() -> {
            ClipboardContent content = new ClipboardContent();
            content.putFiles(List.of(SELECTED.toFile(), ROOT.resolve("second.txt").toFile()));
            Clipboard.getSystemClipboard().setContent(content);
            return null;
        });
        ContextMenu menu = fx(this::treeMenu);
        int reads = fx(() -> fixture.rootReads);
        fx(() -> {
            fixture.copyGate = new CompletableFuture<>();
            item(menu, "paste").fire();
            return null;
        });
        awaitFx(() -> fixture.calls.contains("copy:" + SELECTED + "->" + NODE.resolve("selected.txt")));
        fx(() -> { loaded.controller().navigateTo(OTHER); return null; });
        awaitFx(() -> OTHER.equals(loaded.controller().currentDirectory()));
        fx(() -> { fixture.copyGate.complete(null); fixture.copyGate = null; return null; });
        awaitFx(() -> fixture.calls.contains("copy:" + ROOT.resolve("second.txt") + "->" + NODE.resolve("second.txt"))
                && fixture.rootReads > reads);
    }

    @Test
    void cutTreeNodeMovesItToTheChosenDestinationAndRelocatesCurrentDirectory() throws Exception {
        fx(() -> { loaded.controller().navigateTo(NODE.resolve("nested")); return null; });
        awaitFx(() -> NODE.resolve("nested").equals(loaded.controller().currentDirectory()));
        fx(() -> {
            item(treeMenu(), "cut").fire();
            ContextMenu destination = loaded.controller().contextMenu(entry(OTHER, true), true);
            item(destination, "paste").fire();
            return null;
        });
        awaitFx(() -> OTHER.resolve("node/nested").equals(loaded.controller().currentDirectory()));
        fx(() -> {
            assertTrue(fixture.calls.contains("move:" + NODE + "->" + OTHER.resolve("node")));
            return null;
        });
    }

    @Test
    void pasteChecksConflictsInsideTheClickedNodeAndRespectsCancel() throws Exception {
        fx(() -> {
            fixture.entries.put(NODE.resolve("selected.txt"), false);
            ClipboardContent content = new ClipboardContent();
            content.putFiles(List.of(SELECTED.toFile()));
            Clipboard.getSystemClipboard().setContent(content);
            return null;
        });
        ContextMenu menu = fx(this::treeMenu);
        answerDialog(() -> item(menu, "paste").fire(), null, ButtonType.CANCEL);
        fx(() -> {
            assertTrue(fixture.calls.stream().noneMatch(call -> call.startsWith("copy:")));
            assertTrue(fixture.entries.containsKey(NODE.resolve("selected.txt")));
            return null;
        });
    }

    @Test
    void changedSystemClipboardReplacesInternalCutSelection() throws Exception {
        fx(() -> {
            item(treeMenu(), "cut").fire();
            ClipboardContent content = new ClipboardContent();
            content.putFiles(List.of(SELECTED.toFile()));
            Clipboard.getSystemClipboard().setContent(content);
            item(treeMenu(), "paste").fire();
            return null;
        });
        awaitFx(() -> fixture.calls.contains("copy:" + SELECTED + "->" + NODE.resolve("selected.txt")));
        fx(() -> {
            assertTrue(fixture.calls.stream().noneMatch(call -> call.startsWith("move:")));
            item(treeMenu(), "copyName").fire();
            assertTrue(item(treeMenu(), "paste").isDisable());
            return null;
        });
    }

    @Test
    void blankAreaAndFilesystemRootNeverUseUnrelatedTableSelection() throws Exception {
        fx(() -> {
            ContextMenu blank = loaded.controller().contextMenu(null, true);
            ContextMenu root = loaded.controller().contextMenu(entry(ROOT.getRoot(), true), true);
            for (ContextMenu menu : List.of(blank, root)) {
                assertTrue(item(menu, "delete").isDisable());
                assertTrue(item(menu, "rename").isDisable());
                assertTrue(item(menu, "cut").isDisable());
            }
            assertFalse(item(blank, "newFolder").isDisable());
            assertFalse(item(root, "newFile").isDisable());
            return null;
        });
    }

    @Test
    void treeRefreshAndMutationRefreshBothReadTheTreeAgain() throws Exception {
        int reads = fx(() -> fixture.rootReads);
        fx(() -> { item(treeMenu(), "refresh").fire(); return null; });
        awaitFx(() -> fixture.rootReads > reads);
        int afterRefresh = fx(() -> fixture.rootReads);
        ContextMenu menu = fx(this::treeMenu);
        answerDialog(() -> item(menu, "newFile").fire(), "fresh.txt", ButtonType.OK);
        awaitFx(() -> fixture.calls.contains("create:" + NODE.resolve("fresh.txt"))
                && fixture.rootReads > afterRefresh);
    }

    private ContextMenu treeMenu() { return loaded.controller().contextMenu(entry(NODE, true), true); }
    private static MenuItem item(ContextMenu menu, String suffix) {
        return menu.getItems().stream().filter(value -> ("localFiles." + suffix).equals(value.getId()))
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private TableView<LocalFileEntry> table() throws Exception {
        var field = LocalFileWorkspaceController.class.getDeclaredField("table");
        field.setAccessible(true);
        return (TableView<LocalFileEntry>) field.get(loaded.controller());
    }

    private static String answerDialog(Runnable action, String answer, ButtonType choice) throws Exception {
        CompletableFuture<String> answered = new CompletableFuture<>();
        ListChangeListener<Window> listener = change -> {
            while (change.next()) for (Window window : change.getAddedSubList()) {
                Platform.runLater(() -> {
                    if (answered.isDone() || window.getScene() == null) return;
                    var node = window.getScene().getRoot();
                    DialogPane pane = node instanceof DialogPane dialog ? dialog : (DialogPane) node.lookup(".dialog-pane");
                    if (pane == null) return;
                    TextField editor = (TextField) pane.lookup(".text-field");
                    String original = editor == null ? "" : editor.getText();
                    if (editor != null && answer != null) editor.setText(answer);
                    ((Button) pane.lookupButton(choice)).fire();
                    answered.complete(original);
                });
            }
        };
        fx(() -> { Window.getWindows().addListener(listener); return null; });
        try {
            fx(() -> { action.run(); return null; });
            return answered.get(5, TimeUnit.SECONDS);
        } finally { fx(() -> { Window.getWindows().removeListener(listener); return null; }); }
    }

    private static LocalFileEntry entry(Path path, boolean directory) {
        return new LocalFileEntry(path.getFileName() == null ? path.toString() : path.getFileName().toString(),
                path, directory, false, 0, Optional.empty());
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

    private static final class Fixture implements LocalFileWorkflow, LocalFileWorkspaceController.Actions {
        final Map<Path, Boolean> entries = new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();
        final List<Path> favorites = new ArrayList<>();
        int rootReads;
        CompletableFuture<Void> copyGate;
        Fixture() {
            for (Path path : List.of(ROOT, NODE, NODE.resolve("nested"), OTHER)) entries.put(path, true);
            entries.put(SELECTED, false);
            entries.put(ROOT.resolve("second.txt"), false);
        }
        public CompletionStage<List<LocalFileEntry>> localRoots() {
            rootReads++;
            return CompletableFuture.completedFuture(List.of(entry(ROOT, true)));
        }
        public CompletionStage<List<LocalFileEntry>> listLocal(Path directory) {
            return CompletableFuture.completedFuture(entries.entrySet().stream()
                    .filter(value -> directory.equals(value.getKey().getParent()))
                    .map(value -> entry(value.getKey(), value.getValue())).toList());
        }
        public CompletionStage<Void> createLocalDirectory(Path path) {
            calls.add("mkdir:" + path); entries.put(path, true); return done();
        }
        public CompletionStage<Void> createLocalFile(Path path) {
            calls.add("create:" + path); entries.put(path, false); return done();
        }
        public CompletionStage<Boolean> moveLocalToTrash(Path path) {
            calls.add("trash:" + path); entries.keySet().removeIf(item -> item.startsWith(path));
            return CompletableFuture.completedFuture(true);
        }
        public CompletionStage<Void> deleteLocal(Path path) { throw new AssertionError("Unexpected permanent delete"); }
        public CompletionStage<Void> renameLocal(Path source, Path target) {
            calls.add("rename:" + source + "->" + target);
            Map<Path, Boolean> renamed = new LinkedHashMap<>();
            entries.forEach((path, directory) -> renamed.put(path.startsWith(source)
                    ? target.resolve(source.relativize(path)) : path, directory));
            entries.clear(); entries.putAll(renamed); return done();
        }
        public CompletionStage<Void> copyLocal(Path source, Path target, boolean replace) {
            calls.add("copy:" + source + "->" + target); entries.put(target, false);
            return copyGate == null ? done() : copyGate;
        }
        public CompletionStage<Void> moveLocal(Path source, Path target, boolean replace) {
            calls.add("move:" + source + "->" + target);
            return renameLocal(source, target);
        }
        public CompletionStage<Void> revealLocal(Path path, boolean selectFile) { return done(); }
        public void openPairedTerminal(Path directory) { }
        public void edit(LocalFileEntry entry) { }
        public void status(String message, boolean error) { if (error) throw new AssertionError(message); }
        public void addFavorite(Path directory) { favorites.add(directory); }
        public void directoryChanged(Path directory) { }
        private static CompletionStage<Void> done() { return CompletableFuture.completedFuture(null); }
    }
}
