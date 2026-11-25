// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.domain.settings.SftpSettings;
import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import com.ynpe.fastremote.i18n.Localizer;
import com.ynpe.fastremote.i18n.Texts;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TextEditorWorkspaceTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void openingAndClosingLocalAndRemoteMixedNewlineFilesDoesNotPrompt() throws Exception {
        for (boolean remote : List.of(false, true)) {
            AtomicReference<TextEditorWorkspace> workspace = new AtomicReference<>();
            AtomicReference<WorkspacePaneCoordinator> panes = new AtomicReference<>();
            byte[] source = TextEncoding.UTF8_BOM.encode("first\r\nsecond\rthird\n");
            runOnFx(() -> {
                ConnectionWorkflow workflow = (ConnectionWorkflow) Proxy.newProxyInstance(
                        ConnectionWorkflow.class.getClassLoader(), new Class<?>[] { ConnectionWorkflow.class },
                        (proxy, method, arguments) -> CompletableFuture.completedFuture(
                                method.getName().equals("readLocalFile") ? source : null));
                panes.set(new WorkspacePaneCoordinator(new TabPane(),
                        tab -> workspace.get().requestClose(tab), ignored -> { }, () -> { }));
                workspace.set(new TextEditorWorkspace(panes.get(), workflow, () -> null,
                        SftpSettings::defaults, ignored -> { }, () -> { }, ignored -> { }));
                if (remote) {
                    RemoteFilesHandle files = (RemoteFilesHandle) Proxy.newProxyInstance(
                            RemoteFilesHandle.class.getClassLoader(), new Class<?>[] { RemoteFilesHandle.class },
                            (proxy, method, arguments) -> CompletableFuture.completedFuture(
                                    method.getName().equals("readFile") ? source : null));
                    workspace.get().openRemote(new RemoteFileEntry("note.txt", "/note.txt", false, false,
                            source.length, java.util.Optional.empty()), "host", files,
                            message -> { throw new AssertionError(message); }, ignored -> { }, () -> { }, null,
                            ignored -> { });
                } else {
                    workspace.get().openLocal(localEntry("note.txt"), message -> { throw new AssertionError(message); });
                }
            });
            runOnFx(() -> {
                try {
                    Tab tab = panes.get().logicalTabsInDisplayOrder().getFirst();
                    assertFalse(tab.getText().endsWith(" *"));
                    EmbeddedTextEditor editor = (EmbeddedTextEditor) ((BorderPane) tab.getContent()).getCenter();
                    editor.editorArea().appendText("modified");
                    assertTrue(tab.getText().endsWith(" *"));
                    editor.editorArea().undo();
                    assertFalse(tab.getText().endsWith(" *"));
                    workspace.get().requestClose(tab);
                    assertTrue(panes.get().logicalTabsInDisplayOrder().isEmpty());
                } finally {
                    workspace.get().dispose();
                }
            });
        }
    }

    @Test
    void localSavePreservesBomAndMixedDelimitersAndResetsDirtyBaseline() throws Exception {
        AtomicReference<TextEditorWorkspace> workspace = new AtomicReference<>();
        AtomicReference<WorkspacePaneCoordinator> panes = new AtomicReference<>();
        AtomicReference<byte[]> saved = new AtomicReference<>();
        runOnFx(() -> {
            ConnectionWorkflow workflow = (ConnectionWorkflow) Proxy.newProxyInstance(
                    ConnectionWorkflow.class.getClassLoader(), new Class<?>[] { ConnectionWorkflow.class },
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("readLocalFile")) return CompletableFuture.completedFuture(
                                TextEncoding.UTF8_BOM.encode("first\r\nsecond\rthird\n"));
                        if (method.getName().equals("writeLocalFileAtomic")) saved.set(((byte[]) arguments[1]).clone());
                        return CompletableFuture.completedFuture(null);
                    });
            panes.set(new WorkspacePaneCoordinator(new TabPane(),
                    tab -> workspace.get().requestClose(tab), ignored -> { }, () -> { }));
            workspace.set(new TextEditorWorkspace(panes.get(), workflow, () -> null,
                    SftpSettings::defaults, ignored -> { }, () -> { }, ignored -> { }));
            workspace.get().openLocal(localEntry("note.txt"), message -> { throw new AssertionError(message); });
        });
        runOnFx(() -> {
            Tab tab = panes.get().logicalTabsInDisplayOrder().getFirst();
            EmbeddedTextEditor editor = (EmbeddedTextEditor) ((BorderPane) tab.getContent()).getCenter();
            editor.editorArea().replaceText(0, 5, "FIRST");
            assertTrue(tab.getText().endsWith(" *"));
            editor.contextMenu().getItems().stream().filter(item -> "editor-context-save".equals(item.getId()))
                    .findFirst().orElseThrow().fire();
        });
        runOnFx(() -> {
            try {
                org.junit.jupiter.api.Assertions.assertArrayEquals(
                        TextEncoding.UTF8_BOM.encode("FIRST\r\nsecond\rthird\n"), saved.get());
                Tab tab = panes.get().logicalTabsInDisplayOrder().getFirst();
                assertFalse(tab.getText().endsWith(" *"));
                EmbeddedTextEditor editor = (EmbeddedTextEditor) ((BorderPane) tab.getContent()).getCenter();
                editor.editorArea().undo();
                assertTrue(tab.getText().endsWith(" *"));
                editor.editorArea().redo();
                assertFalse(tab.getText().endsWith(" *"));
                workspace.get().requestClose(tab);
                assertTrue(panes.get().logicalTabsInDisplayOrder().isEmpty());
            } finally {
                workspace.get().dispose();
            }
        });
    }

    @Test
    void symbolicLinkEditorKeepsAliasPathForOpenSaveAndReveal() throws Exception {
        var workspace = new AtomicReference<TextEditorWorkspace>();
        var panes = new AtomicReference<WorkspacePaneCoordinator>();
        var readPath = new AtomicReference<String>();
        var savedPath = new AtomicReference<String>();
        var revealedPath = new AtomicReference<String>();
        var saved = new AtomicReference<byte[]>();
        String alias = "/etc/nginx/sites-enabled/web.conf";
        runOnFx(() -> {
            ConnectionWorkflow workflow = (ConnectionWorkflow) Proxy.newProxyInstance(
                    ConnectionWorkflow.class.getClassLoader(), new Class<?>[]{ConnectionWorkflow.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("createEditorTemporaryFile")) {
                            saved.set(((byte[]) arguments[0]).clone());
                            return CompletableFuture.completedFuture(Path.of("/private/tmp/fixture-link-edit"));
                        }
                        return CompletableFuture.completedFuture(null);
                    });
            RemoteFilesHandle files = (RemoteFilesHandle) Proxy.newProxyInstance(
                    RemoteFilesHandle.class.getClassLoader(), new Class<?>[]{RemoteFilesHandle.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("readFile")) {
                            readPath.set((String) arguments[0]);
                            assertEquals(SftpSettings.defaults().maxEditableFileBytes(), ((Integer) arguments[1]).longValue());
                            return CompletableFuture.completedFuture("content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }
                        if (method.getName().equals("upload")) {
                            savedPath.set((String) arguments[1]);
                            return CompletableFuture.completedFuture(null);
                        }
                        throw new AssertionError("Editor must keep the alias, unexpected operation: " + method.getName());
                    });
            panes.set(new WorkspacePaneCoordinator(new TabPane(),
                    tab -> workspace.get().requestClose(tab), ignored -> { }, () -> { }));
            workspace.set(new TextEditorWorkspace(panes.get(), workflow, () -> null,
                    SftpSettings::defaults, ignored -> { }, () -> { }, ignored -> { }));
            workspace.get().openRemote(new RemoteFileEntry("web.conf", alias, false, true,
                    12, java.util.Optional.empty()), "sapi", files,
                    message -> { throw new AssertionError(message); }, ignored -> { }, () -> { }, null, revealedPath::set);
        });
        runOnFx(() -> {
            Tab tab = panes.get().logicalTabsInDisplayOrder().getFirst();
            BorderPane shell = (BorderPane) tab.getContent();
            HBox strip = (HBox) shell.getBottom();
            assertEquals(TextEditorWorkspace.remoteLocationLabel("sapi", alias),
                    ((javafx.scene.control.Label) strip.getChildren().getFirst()).getText());
            EmbeddedTextEditor editor = (EmbeddedTextEditor) shell.getCenter();
            editor.editorArea().appendText(" edited");
            editor.contextMenu().getItems().stream().filter(item -> "editor-context-reveal".equals(item.getId()))
                    .findFirst().orElseThrow().fire();
            Platform.runLater(() -> {
                javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                        .map(window -> window.getScene().lookup(".dialog-pane"))
                        .filter(javafx.scene.control.DialogPane.class::isInstance)
                        .map(javafx.scene.control.DialogPane.class::cast).findFirst().ifPresent(dialog ->
                                ((Button) dialog.lookupButton(dialog.getButtonTypes().getLast())).fire());
            });
            editor.contextMenu().getItems().stream().filter(item -> "editor-context-save".equals(item.getId()))
                    .findFirst().orElseThrow().fire();
        });
        runOnFx(() -> {
            try {
                assertEquals(alias, readPath.get());
                assertEquals(alias, savedPath.get());
                assertEquals(alias, revealedPath.get());
                assertEquals("content edited", new String(saved.get(), java.nio.charset.StandardCharsets.UTF_8));
                assertFalse(panes.get().logicalTabsInDisplayOrder().getFirst().getText().endsWith(" *"));
            } finally { workspace.get().dispose(); }
        });
    }

    @Test
    void remoteLocationIncludesHostNameAndFullPath() {
        String label = TextEditorWorkspace.remoteLocationLabel("U24", "/etc/ssh/sshd_config");
        assertTrue(label.endsWith(" - U24 | /etc/ssh/sshd_config"));
    }

    @Test
    void logContextsIdentifyLocalOrRemoteMachineAndFullPath() {
        Path local = Path.of("folder", "file.txt").toAbsolutePath().normalize();
        assertTrue(TextEditorWorkspace.localFileContext(local).contains(local.toString()));
        String remote = TextEditorWorkspace.remoteFileContext("156.251.16.73-sapi",
                "/var/log/app/server.log");
        assertEquals("156.251.16.73-sapi | /var/log/app/server.log", remote);
    }

    @Test
    void emptyUntitledDocumentsDoNotPromptButNonEmptyDocumentsDo() {
        assertFalse(TextEditorWorkspace.isDirty(false, "", ""));
        assertTrue(TextEditorWorkspace.isDirty(false, "", "draft"));
    }

    @Test
    void associatedFilesPromptOnlyWhenContentChanged() {
        assertFalse(TextEditorWorkspace.isDirty(true, "same", "same"));
        assertTrue(TextEditorWorkspace.isDirty(true, "before", "after"));
    }

    @Test
    void unsavedNoteDisablesAllPathContextActions() throws Exception {
        runOnFx(() -> {
            ConnectionWorkflow workflow = (ConnectionWorkflow) Proxy.newProxyInstance(
                    ConnectionWorkflow.class.getClassLoader(), new Class<?>[] { ConnectionWorkflow.class },
                    (proxy, method, arguments) -> method.getName().equals("close") ? null
                            : CompletableFuture.completedFuture(null));
            TabPane tabs = new TabPane();
            AtomicReference<TextEditorWorkspace> reference = new AtomicReference<>();
            WorkspacePaneCoordinator panes = new WorkspacePaneCoordinator(tabs,
                    tab -> reference.get().requestClose(tab), ignored -> { }, () -> { });
            TextEditorWorkspace workspace = new TextEditorWorkspace(
                    panes, workflow, () -> null, SftpSettings::defaults,
                    ignored -> { }, () -> { }, ignored -> { });
            reference.set(workspace);
            workspace.newDocument();

            Tab tab = panes.logicalTabsInDisplayOrder().getFirst();
            BorderPane shell = (BorderPane) tab.getContent();
            EmbeddedTextEditor editor = (EmbeddedTextEditor) shell.getCenter();
            assertEquals(null, shell.getTop());
            assertTrue(shell.getBottom().getStyleClass().contains("embedded-editor-file-strip"));
            HBox fileStrip = (HBox) shell.getBottom();
            assertEquals(Texts.text("editor.location.unassociated"),
                    ((javafx.scene.control.Label) fileStrip.getChildren().getFirst()).getText());
            assertEquals(List.of("editor-status-save"), fileStrip.getChildren().stream()
                    .filter(Button.class::isInstance).map(Button.class::cast)
                    .map(Button::getId).toList());
            for (String id : List.of("editor-context-wrap", "editor-context-syntax",
                    "editor-context-encoding", "editor-context-save",
                    "editor-context-save-close", "editor-context-close")) {
                assertTrue(editor.contextMenu().getItems().stream()
                        .anyMatch(candidate -> id.equals(candidate.getId())));
            }
            for (String id : List.of("editor-context-reveal", "editor-context-copy-path",
                    "editor-context-copy-name")) {
                MenuItem item = editor.contextMenu().getItems().stream()
                        .filter(candidate -> id.equals(candidate.getId())).findFirst().orElseThrow();
                assertTrue(item.isDisable());
            }
            workspace.dispose();
        });
    }

    @Test
    void remoteEditorEnablesFileActionsAndDelegatesSftpReveal() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> revealed = new AtomicReference<>();
        Platform.runLater(() -> {
            AtomicReference<TextEditorWorkspace> workspaceReference = new AtomicReference<>();
            try {
                ConnectionWorkflow workflow = (ConnectionWorkflow) Proxy.newProxyInstance(
                        ConnectionWorkflow.class.getClassLoader(), new Class<?>[] { ConnectionWorkflow.class },
                        (proxy, method, arguments) -> method.getName().equals("close") ? null
                                : CompletableFuture.completedFuture(null));
                RemoteFilesHandle files = (RemoteFilesHandle) Proxy.newProxyInstance(
                        RemoteFilesHandle.class.getClassLoader(), new Class<?>[] { RemoteFilesHandle.class },
                        (proxy, method, arguments) -> method.getName().equals("readFile")
                                ? CompletableFuture.completedFuture("content".getBytes(
                                        java.nio.charset.StandardCharsets.UTF_8))
                                : CompletableFuture.completedFuture(null));
                TabPane tabs = new TabPane();
                WorkspacePaneCoordinator panes = new WorkspacePaneCoordinator(tabs,
                        tab -> workspaceReference.get().requestClose(tab), ignored -> { }, () -> { });
                TextEditorWorkspace workspace = new TextEditorWorkspace(
                        panes, workflow, () -> null, SftpSettings::defaults,
                        ignored -> { }, () -> { }, ignored -> { });
                workspaceReference.set(workspace);
                RemoteFileEntry entry = new RemoteFileEntry("app.conf", "/etc/app/app.conf",
                        false, false, 7L, java.util.Optional.empty());
                workspace.openRemote(entry, "host", files, message -> failure.set(new AssertionError(message)),
                        ignored -> { }, () -> { }, null, revealed::set);
                Platform.runLater(() -> {
                    try {
                        Tab tab = panes.logicalTabsInDisplayOrder().getFirst();
                        BorderPane shell = (BorderPane) tab.getContent();
                        EmbeddedTextEditor editor = (EmbeddedTextEditor) shell.getCenter();
                        HBox fileStrip = (HBox) shell.getBottom();
                        assertEquals(TextEditorWorkspace.remoteLocationLabel(
                                        "host", "/etc/app/app.conf"),
                                ((javafx.scene.control.Label)
                                        fileStrip.getChildren().getFirst()).getText());
                        assertEquals(List.of("editor-status-save-local", "editor-status-save"),
                                fileStrip.getChildren().stream()
                                        .filter(Button.class::isInstance).map(Button.class::cast)
                                        .map(Button::getId).toList());
                        MenuItem reveal = editor.contextMenu().getItems().stream()
                                .filter(candidate -> "editor-context-reveal".equals(candidate.getId()))
                                .findFirst().orElseThrow();
                        assertFalse(editor.contextMenu().getItems().stream().anyMatch(
                                candidate -> "editor-context-show-in-local-files".equals(candidate.getId())));
                        assertFalse(reveal.isDisable());
                        reveal.fire();
                        assertEquals("/etc/app/app.conf", revealed.get());
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        workspace.dispose();
                        completed.countDown();
                    }
                });
            } catch (Throwable error) {
                failure.set(error);
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void localEditorShowsLocalFilesActionBeforePlatformRevealAndDelegatesPath() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Path> revealed = new AtomicReference<>();
        Platform.runLater(() -> {
            AtomicReference<TextEditorWorkspace> workspaceReference = new AtomicReference<>();
            try {
                ConnectionWorkflow workflow = (ConnectionWorkflow) Proxy.newProxyInstance(
                        ConnectionWorkflow.class.getClassLoader(), new Class<?>[] { ConnectionWorkflow.class },
                        (proxy, method, arguments) -> {
                            if (method.getName().equals("readLocalFile")) {
                                return CompletableFuture.completedFuture("content".getBytes(
                                        java.nio.charset.StandardCharsets.UTF_8));
                            }
                            if (method.getName().equals("close")) return null;
                            throw new UnsupportedOperationException(method.getName());
                        });
                TabPane tabs = new TabPane();
                WorkspacePaneCoordinator panes = new WorkspacePaneCoordinator(tabs,
                        tab -> workspaceReference.get().requestClose(tab), ignored -> { }, () -> { });
                TextEditorWorkspace workspace = new TextEditorWorkspace(
                        panes, workflow, () -> null, SftpSettings::defaults,
                        ignored -> { }, () -> { }, ignored -> { }, revealed::set);
                workspaceReference.set(workspace);
                LocalFileEntry entry = localEntry("example.txt");
                workspace.openLocal(entry, message -> failure.set(new AssertionError(message)));
                Platform.runLater(() -> {
                    try {
                        Tab tab = panes.logicalTabsInDisplayOrder().getFirst();
                        BorderPane shell = (BorderPane) tab.getContent();
                        EmbeddedTextEditor editor = (EmbeddedTextEditor) shell.getCenter();
                        List<MenuItem> items = editor.contextMenu().getItems();
                        int localFilesIndex = menuItemIndex(items, "editor-context-show-in-local-files");
                        int revealIndex = menuItemIndex(items, "editor-context-reveal");
                        assertEquals(revealIndex - 1, localFilesIndex);
                        items.get(localFilesIndex).fire();
                        assertEquals(entry.path().toAbsolutePath().normalize(), revealed.get());
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        workspace.dispose();
                        completed.countDown();
                    }
                });
            } catch (Throwable error) {
                failure.set(error);
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void droppedLocalFilesAreReadSequentially() throws Exception {
        LinkedBlockingQueue<CompletableFuture<byte[]>> pending = new LinkedBlockingQueue<>();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        AtomicReference<TextEditorWorkspace> workspaceReference = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                ConnectionWorkflow workflow = (ConnectionWorkflow) Proxy.newProxyInstance(
                        ConnectionWorkflow.class.getClassLoader(), new Class<?>[] { ConnectionWorkflow.class },
                        (proxy, method, arguments) -> {
                            if (method.getName().equals("readLocalFile")) {
                                CompletableFuture<byte[]> result = new CompletableFuture<>();
                                int current = active.incrementAndGet();
                                maximumActive.accumulateAndGet(current, Math::max);
                                result.whenComplete((ignored, error) -> active.decrementAndGet());
                                pending.add(result);
                                return result;
                            }
                            if (method.getName().equals("close")) return null;
                            throw new UnsupportedOperationException(method.getName());
                        });
                TabPane tabs = new TabPane();
                WorkspacePaneCoordinator panes = new WorkspacePaneCoordinator(tabs,
                        tab -> workspaceReference.get().requestClose(tab), ignored -> { }, () -> { });
                TextEditorWorkspace workspace = new TextEditorWorkspace(
                        panes, workflow, () -> null, SftpSettings::defaults,
                        ignored -> { }, () -> { }, ignored -> { });
                workspaceReference.set(workspace);
                List<LocalFileEntry> entries = List.of(
                        localEntry("first.txt"), localEntry("second.txt"), localEntry("third.txt"));
                workspace.openLocalBatch(entries, message -> failure.set(new AssertionError(message)));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                started.countDown();
            }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        for (int index = 0; index < 3; index++) {
            CompletableFuture<byte[]> read = pending.poll(5, TimeUnit.SECONDS);
            assertTrue(read != null);
            assertEquals(1, active.get());
            read.complete(("content-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        CountDownLatch verified = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                assertEquals(1, maximumActive.get());
                workspaceReference.get().dispose();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                verified.countDown();
            }
        });
        assertTrue(verified.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    private static LocalFileEntry localEntry(String name) {
        return new LocalFileEntry(name, Path.of("/tmp", name), false, false, 8L,
                java.util.Optional.empty());
    }

    private static int menuItemIndex(List<MenuItem> items, String id) {
        for (int index = 0; index < items.size(); index++) {
            if (id.equals(items.get(index).getId())) return index;
        }
        throw new AssertionError("Missing menu item: " + id);
    }

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void recentLocalFilesAreNewestFirstUniqueAndLimitedToTen() {
        List<Path> paths = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            TextEditorWorkspace.updateRecentLocalFiles(paths, Path.of("/tmp/file-" + index));
        }
        assertEquals(TextEditorWorkspace.RECENT_LOCAL_FILE_LIMIT, paths.size());
        assertEquals(Path.of("/tmp/file-11"), paths.getFirst());
        TextEditorWorkspace.updateRecentLocalFiles(paths, Path.of("/tmp/file-5"));
        assertEquals(Path.of("/tmp/file-5"), paths.getFirst());
        assertEquals(TextEditorWorkspace.RECENT_LOCAL_FILE_LIMIT, paths.size());
    }

    @Test
    void recentMenuProvidesOpenEntriesAndClearAction() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Menu menu = new Menu();
                AtomicReference<Path> opened = new AtomicReference<>();
                java.util.concurrent.atomic.AtomicBoolean cleared = new java.util.concurrent.atomic.AtomicBoolean();
                Path path = Path.of("/tmp/example_file.txt");
                TextEditorWorkspace.configureRecentMenu(menu, List.of(path), opened::set,
                        () -> cleared.set(true));
                assertFalse(menu.isDisable());
                assertEquals(3, menu.getItems().size());
                assertFalse(menu.getItems().getFirst().isMnemonicParsing());
                assertEquals(path.toString(), menu.getItems().getFirst().getText());
                menu.getItems().getFirst().fire();
                menu.getItems().getLast().fire();
                assertEquals(path, opened.get());
                assertTrue(cleared.get());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void launchFilesAlwaysCreateNewTabsAndUseTheConfiguredReadLimit() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger maximumBytes = new java.util.concurrent.atomic.AtomicInteger();
        Platform.runLater(() -> {
            try {
                TabPane tabs = new TabPane();
                AtomicReference<TextEditorWorkspace> workspaceReference = new AtomicReference<>();
                WorkspacePaneCoordinator panes = new WorkspacePaneCoordinator(tabs,
                        tab -> workspaceReference.get().requestClose(tab), ignored -> { }, () -> { });
                ConnectionWorkflow workflow = (ConnectionWorkflow) Proxy.newProxyInstance(
                        ConnectionWorkflow.class.getClassLoader(),
                        new Class<?>[] { ConnectionWorkflow.class },
                        (proxy, method, arguments) -> {
                            if (method.getName().equals("readLocalFile")) {
                                maximumBytes.set((Integer) arguments[1]);
                                return CompletableFuture.completedFuture("content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            }
                            if (method.getName().equals("close")) return null;
                            throw new UnsupportedOperationException(method.getName());
                        });
                TextEditorWorkspace workspace = new TextEditorWorkspace(
                        panes, workflow, () -> null, SftpSettings::defaults,
                        ignored -> { }, () -> { }, ignored -> { });
                workspaceReference.set(workspace);
                Path path = Path.of("/tmp/repeated-launch-file.txt");

                workspace.openLocalInNewTab(path, message -> failure.set(new AssertionError(message)));
                workspace.openLocalInNewTab(path, message -> failure.set(new AssertionError(message)));

                Platform.runLater(() -> {
                    try {
                        assertEquals(2, tabs.getTabs().size());
                        assertEquals(Math.toIntExact(SftpSettings.defaults().maxEditableFileBytes()),
                                maximumBytes.get());
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        workspace.dispose();
                        completed.countDown();
                    }
                });
            } catch (Throwable error) {
                failure.set(error);
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void editorDialogsUseConfiguredLanguageAndNaturalButtonWidths() throws Exception {
        Localizer previous = Texts.localizer();
        Texts.configure(Localizer.forLanguage(InterfaceLanguage.GERMAN));
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            try {
                ButtonType discard = new ButtonType(Texts.text("editor.discard"), ButtonBar.ButtonData.NO);
                alert.getButtonTypes().setAll(discard, ButtonType.CANCEL);
                TextEditorWorkspace.prepareDialog(alert);
                Button cancelButton = (Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL);
                Button discardButton = (Button) alert.getDialogPane().lookupButton(discard);
                assertEquals("Abbrechen", cancelButton.getText());
                assertEquals("Nicht speichern", discardButton.getText());
                assertEquals(Region.USE_PREF_SIZE, discardButton.getMinWidth());
                assertTrue(discardButton.getPrefWidth() > 100.0);
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                Texts.configure(previous);
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }
}
