// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.ynpe.fastremote.domain.settings.GlobalSettings;
import com.ynpe.fastremote.domain.settings.SftpBookmarks;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SharedSettingsRefreshTest {
    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test void consecutiveLocalAddsAreVisibleBeforeDiskCompletionAndOldCompletionCannotReplaceNewState() throws Exception {
        List<GlobalSettings> writes = new ArrayList<>();
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        MainWindowController main = fx(() -> controller(writes, pending));
        Path first = Path.of("/first").toAbsolutePath();
        Path second = Path.of("/second").toAbsolutePath();
        fx(() -> {
            invoke(main, "addGlobalLocalFavorite", new Class<?>[]{Path.class}, first);
            invoke(main, "addGlobalLocalFavorite", new Class<?>[]{Path.class}, second);
            assertEquals(List.of(first.toString(), second.toString()), settings(main).sftpSettings().globalBookmarks().localPaths());
            assertEquals(settings(main), writes.get(1));
            // Simulate an independent layout update while the earlier bookmark write is in flight.
            set(main, "persistedWorkspaceSettings", settings(main).withShowSftpDirectoryTree(!settings(main).showSftpDirectoryTree()));
            return null;
        });
        GlobalSettings latest = fx(() -> settings(main));
        pending.getFirst().complete(null);
        fx(() -> { assertSame(latest, settings(main)); return null; });
        pending.get(1).complete(null);
        fx(() -> { assertSame(latest, settings(main)); return null; });
    }

    @Test void globalLocalAndRemoteChangesAreVisibleThroughExistingSftpMenuConsumersAndFailureRollsBack() throws Exception {
        List<GlobalSettings> writes = new ArrayList<>();
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        MainWindowController main = fx(() -> controller(writes, pending));
        GlobalSettings before = fx(() -> settings(main));
        var bookmarks = new SftpBookmarks(List.of("/local-new"), List.of("/remote-new"));
        CompletionStage<?> result = fx(() -> (CompletionStage<?>) invoke(main, "saveGlobalBookmarks",
                new Class<?>[]{SftpBookmarks.class, String.class}, bookmarks, "saved"));
        fx(() -> {
            assertEquals(bookmarks, settings(main).sftpSettings().globalBookmarks());
            for (OpenSession session : List.of(session(), session())) {
                for (boolean local : List.of(true, false)) {
                    ContextMenu menu = new ContextMenu();
                    invoke(main, "rebuildFavoriteMenu", new Class<?>[]{ContextMenu.class, OpenSession.class, boolean.class}, menu, session, local);
                    assertTrue(menu.getItems().stream().anyMatch(item -> (local ? "/local-new" : "/remote-new").equals(item.getText())));
                }
            }
            return null;
        });
        pending.getFirst().completeExceptionally(new IllegalStateException("fixture write failure"));
        fx(() -> { assertSame(before, settings(main)); assertTrue(result.toCompletableFuture().isCompletedExceptionally()); return null; });
    }

    @Test void existingLocalViewsShowSharedAdditionsAndDeletionsWithoutKeepingPrivateFavorites() throws Exception {
        fx(() -> {
            List<String> shared = new ArrayList<>(List.of("/old"));
            var actions = proxy(LocalFileWorkspaceController.Actions.class, (method, args) -> {
                if (method.equals("favoritePaths")) return List.copyOf(shared);
                return null;
            });
            var first = new LocalFileWorkspaceController(unused(LocalFileWorkflow.class), actions, Path.of("/"));
            var second = new LocalFileWorkspaceController(unused(LocalFileWorkflow.class), actions, Path.of("/"));
            for (var view : List.of(first, second)) set(view, "favoritesButton", new Button());
            assertTrue(menuPaths(first).contains("/old"));
            shared.add("/new_folder");
            assertTrue(menuPaths(second).contains("/new_folder"));
            ContextMenu favorites = (ContextMenu) get(second, "favoritesMenu");
            assertFalse(favorites.getItems().stream().filter(item -> "/new_folder".equals(item.getText()))
                    .findFirst().orElseThrow().isMnemonicParsing());
            shared.remove("/old");
            for (var view : List.of(first, second)) {
                assertFalse(menuPaths(view).contains("/old"));
                assertTrue(menuPaths(view).contains("/new_folder"));
            }
            return null;
        });
    }

    @Test void hostFavoritesAddedOnEitherSideReachAnotherOpenSessionForTheSameHost() throws Exception {
        List<GlobalSettings> writes = new ArrayList<>();
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        MainWindowController main = fx(() -> controller(writes, pending));
        OpenSession source = session();
        OpenSession other = session();
        fx(() -> {
            invoke(main, "addBookmarkPath", new Class<?>[]{OpenSession.class, boolean.class, String.class}, source, true, "/host_local");
            invoke(main, "addBookmarkPath", new Class<?>[]{OpenSession.class, boolean.class, String.class}, source, false, "/host_remote");
            for (boolean local : List.of(true, false)) {
                ContextMenu menu = new ContextMenu();
                invoke(main, "rebuildFavoriteMenu", new Class<?>[]{ContextMenu.class, OpenSession.class, boolean.class}, menu, other, local);
                assertFalse(menu.getItems().stream()
                        .filter(item -> (local ? "/host_local" : "/host_remote").equals(item.getText()))
                        .findFirst().orElseThrow().isMnemonicParsing());
            }
            assertEquals(new SftpBookmarks(List.of("/host_local"), List.of("/host_remote")),
                    writes.getLast().sftpSettings().bookmarksFor(source.bookmarkKey));
            return null;
        });
        pending.forEach(future -> future.complete(null));
        fx(() -> null);
    }

    @Test void globalHiddenFilesSettingRefreshesEveryConnectedSftpView() throws Exception {
        List<GlobalSettings> writes = new ArrayList<>();
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        MainWindowController main = fx(() -> controller(writes, pending));
        OpenSession first = session();
        OpenSession second = session();
        fx(() -> {
            first.state = second.state = WorkspaceViewModel.ConnectionState.CONNECTED;
            first.sftp = second.sftp = new SftpHandle(unused(RemoteFilesHandle.class), () -> {}, new CompletableFuture<>());
            @SuppressWarnings("unchecked")
            var sessions = (java.util.Map<javafx.scene.control.Tab, OpenSession>) get(main, "openSessions");
            sessions.put(new javafx.scene.control.Tab(), first);
            sessions.put(new javafx.scene.control.Tab(), second);
            invoke(main, "setHiddenFiles", new Class<?>[]{OpenSession.class, boolean.class}, first,
                    !settings(main).sftpSettings().showHiddenFiles());
            return null;
        });
        pending.getFirst().complete(null);
        fx(() -> {
            assertEquals(1, first.localTreeGeneration);
            assertEquals(1, second.localTreeGeneration);
            return null;
        });
    }

    @Test void savingDockWidthsPreservesDiagnosticLogSettingsAndBookmarks() throws Exception {
        List<GlobalSettings> writes = new ArrayList<>();
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        MainWindowController main = fx(() -> controller(writes, pending));
        fx(() -> {
            var logs = new com.ynpe.fastremote.domain.settings.DiagnosticLogSettings(false, "/custom-log");
            var initial = settings(main).withDiagnosticLogSettings(logs);
            initial = initial.withSftpSettings(initial.sftpSettings().withHostBookmarks("fixture",
                    new SftpBookmarks(List.of("/local"), List.of("/remote"))));
            set(main, "persistedWorkspaceSettings", initial);
            set(main, "workspaceSettingsLoaded", true);
            invoke(main, "saveDockDimensions", new Class<?>[0]);
            assertEquals(logs, writes.getFirst().diagnosticLogSettings());
            assertEquals(initial.sftpSettings(), writes.getFirst().sftpSettings());
            assertEquals(logs, settings(main).diagnosticLogSettings());
            return null;
        });
        pending.getFirst().complete(null);
        fx(() -> null);
    }

    private static OpenSession session() {
        return new OpenSession("fixture", new com.ynpe.fastremote.domain.connection.ConnectionProfile(
                new com.ynpe.fastremote.domain.connection.Host("example.test"),
                new com.ynpe.fastremote.domain.connection.Port(22),
                new com.ynpe.fastremote.domain.connection.Username("fixture"),
                com.ynpe.fastremote.domain.connection.AuthenticationType.PASSWORD, java.util.Optional.empty()),
                WorkspaceViewModel.WorkspaceType.SFTP);
    }

    private static List<String> menuPaths(LocalFileWorkspaceController view) throws Exception {
        invoke(view, "rebuildFavorites", new Class<?>[0]);
        return ((ContextMenu) get(view, "favoritesMenu")).getItems().stream().map(item -> item.getText()).toList();
    }
    private static MainWindowController controller(List<GlobalSettings> writes, List<CompletableFuture<Void>> pending) throws Exception {
        SettingsWorkflow workflow = proxy(SettingsWorkflow.class, (method, args) -> {
            if (!method.equals("save")) throw new AssertionError(method);
            writes.add((GlobalSettings) args[0]);
            var future = new CompletableFuture<Void>();
            pending.add(future);
            return future;
        });
        var main = new MainWindowController(unused(ConnectionWorkflow.class), unused(HostCatalogWorkflow.class), workflow, unused(SnippetWorkflow.class));
        set(main, "primaryStatusLabel", new Button());
        set(main, "statusIndicator", new Label());
        return main;
    }
    private static GlobalSettings settings(MainWindowController main) throws Exception { return (GlobalSettings) get(main, "persistedWorkspaceSettings"); }
    private static Object get(Object target, String name) throws Exception { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void set(Object target, String name, Object value) throws Exception { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception { var method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args); }
    private interface Call { Object invoke(String method, Object[] args) throws Throwable; }
    private static <T> T proxy(Class<T> type, Call call) { return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> call.invoke(m.getName(), a))); }
    private static <T> T unused(Class<T> type) { return proxy(type, (m, a) -> { throw new AssertionError(m); }); }
    private static <T> T fx(Callable<T> action) throws Exception { var future = new FutureTask<>(action); Platform.runLater(future); return future.get(5, TimeUnit.SECONDS); }
}
