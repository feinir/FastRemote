// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.connection.*;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import com.ynpe.fastremote.i18n.Texts;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HostLibraryDialogLifecycleTest {
    @BeforeAll
    static void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void cancelHostEditThenCloseLibraryOnce() throws Exception { exercise(false, false); }

    @Test
    void saveHostEditThenCloseLibraryOnce() throws Exception { exercise(true, false); }

    @Test
    void cancelHostEditThenEscapeFromLibraryTree() throws Exception { exercise(false, true); }

    @Test
    void saveHostEditThenEscapeFromLibraryTree() throws Exception { exercise(true, true); }

    private void exercise(boolean save, boolean escape) throws Exception {
        AtomicInteger saves = new AtomicInteger();
        SavedHost host = SavedHost.create("fixture", Optional.empty(), new ConnectionProfile(
                new Host("example.test"), new Port(22), new Username("fixture"),
                AuthenticationType.PASSWORD, Optional.empty()));
        MainWindowController main = new MainWindowController(unused(ConnectionWorkflow.class),
                HostCatalogWorkflow.class.cast(Proxy.newProxyInstance(HostCatalogWorkflow.class.getClassLoader(),
                        new Class<?>[] {HostCatalogWorkflow.class}, (proxy, method, args) -> {
                            if (method.getName().equals("saveHost")) {
                                saves.incrementAndGet();
                                // Exercise the real save dispatch without reading or writing user data.
                                return new CompletableFuture<>();
                            }
                            throw new AssertionError("Unexpected call: " + method.getName());
                        })), unused(SettingsWorkflow.class), unused(SnippetWorkflow.class));
        Stage owner = fx(() -> {
            BorderPane root = new BorderPane();
            Stage stage = new Stage();
            stage.setTitle("FastRemote dialog regression fixture");
            stage.setScene(new Scene(root, 800, 600));
            stage.show();
            set(main, "rootPane", root);
            set(main, "catalog", new HostCatalogSnapshot(List.of(), List.of(host), List.of(), VaultStatus.LOCKED));
            set(main, "persistedWorkspaceSettings", com.ynpe.fastremote.domain.settings.GlobalSettings.defaults());
            return stage;
        });
        FutureTask<Void> libraryLoop = new FutureTask<>(() -> {
            var method = MainWindowController.class.getDeclaredMethod("showHostLibraryDialog");
            method.setAccessible(true);
            method.invoke(main);
            return null;
        });
        Platform.runLater(libraryLoop);
        try {
            Dialog<?> library = fx(() -> (Dialog<?>) get(main, "hostLibraryDialog"));
            ContextMenu menu = fx(() -> {
                @SuppressWarnings("unchecked")
                TreeView<String> tree = (TreeView<String>) library.getDialogPane().lookup(".catalog-tree");
                TreeItem<String> item = tree.getRoot().getChildren().getFirst();
                tree.getSelectionModel().select(item);
                var method = MainWindowController.class.getDeclaredMethod("hostLibraryContextMenu",
                        TreeView.class, TreeItem.class, Map.class, Map.class, Window.class);
                method.setAccessible(true);
                ContextMenu context = (ContextMenu) method.invoke(main, tree, item,
                        Map.of(item, host), Map.of(), library.getDialogPane().getScene().getWindow());
                context.show(tree, Side.RIGHT, 0, 0);
                assertTrue(context.isShowing(), "the test menu must actually be visible");
                assertSame(context, ApplicationContextMenus.activeMenu(), "the visible menu must be active");
                tree.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                        "", "", javafx.scene.input.KeyCode.ESCAPE, false, false, false, false));
                assertFalse(context.isShowing(), "Escape must dismiss the open context menu first");
                assertTrue(library.isShowing(), "dismissing a menu must keep the library open");
                context.show(tree, Side.RIGHT, 0, 0);
                return context;
            });
            FutureTask<Void> selectEdit = new FutureTask<>(() -> {
                menu.getItems().stream().filter(item -> Texts.text("legacy.java.50158b7fd5855d55").equals(item.getText()))
                        .findFirst().orElseThrow().fire();
                return null;
            });
            Platform.runLater(selectEdit);
            fx(() -> null);
            fx(() -> {
                Stage editorWindow = Window.getWindows().stream().filter(window -> window instanceof Stage stage
                                && Texts.text("legacy.java.50158b7fd5855d55").equals(stage.getTitle()))
                        .map(Stage.class::cast).findFirst().orElseThrow();
                try {
                    assertFalse(menu.isShowing(), "the context menu must close before entering the editor loop");
                    assertNotSame(menu, ApplicationContextMenus.activeMenu());
                    assertSame(library.getDialogPane().getScene().getWindow(), editorWindow.getOwner());
                } finally {
                    DialogPane editor = (DialogPane) editorWindow.getScene().getRoot();
                    ButtonType action = save ? editor.getButtonTypes().stream()
                            .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                            .findFirst().orElseThrow() : ButtonType.CANCEL;
                    ((Button) editor.lookupButton(action)).fire();
                }
                assertFalse(editorWindow.isShowing());
                return null;
            });
            selectEdit.get(5, TimeUnit.SECONDS);
            fx(() -> null); // Allow the child modal loop and queued owner-focus restoration to finish.
            fx(() -> {
                assertEquals(save ? 1 : 0, saves.get());
                assertTrue(library.isShowing());
                Window window = library.getDialogPane().getScene().getWindow();
                if (escape) {
                    var tree = library.getDialogPane().lookup(".catalog-tree");
                    tree.requestFocus();
                    tree.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                            "", "", javafx.scene.input.KeyCode.ESCAPE, false, false, false, false));
                } else {
                    window.fireEvent(new javafx.stage.WindowEvent(window, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                }
                assertFalse(library.isShowing(), "a single close action must close the library");
                return null;
            });
            libraryLoop.get(5, TimeUnit.SECONDS);
        } finally {
            fx(() -> {
                for (Window window : List.copyOf(Window.getWindows()).reversed()) {
                    if (window instanceof Stage stage && stage != owner) stage.close();
                }
                owner.close();
                return null;
            });
        }
    }


    private static <T> T unused(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> { throw new AssertionError("Unexpected call: " + method.getName()); }));
    }

    private static Object get(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
