// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.ui.WorkspaceViewModel.WorkspaceType;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LibraryAndAiEntryPointTest {
    @Test void dockCopiesEverySelectedNameOnItsOwnLine() throws Exception { copyNames(false); }

    @Test void dialogCopiesEverySelectedNameOnItsOwnLine() throws Exception { copyNames(true); }

    @SuppressWarnings("unchecked")
    private void copyNames(boolean dialog) throws Exception {
        fx(() -> {
            MainWindowController main = controller(new AtomicInteger());
            TreeItem<String> root = new TreeItem<>("root");
            root.setExpanded(true);
            TreeItem<String> groupItem = new TreeItem<>("display group");
            TreeItem<String> first = new TreeItem<>("display host with address");
            TreeItem<String> second = new TreeItem<>("another display host");
            root.getChildren().addAll(List.of(groupItem, first, second));
            TreeView<String> tree = new TreeView<>(root);
            tree.setShowRoot(false);
            tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            HostGroup group = new HostGroup(GroupId.create(), Optional.empty(), "生产环境", 0);
            var profile = new com.ynpe.fastremote.domain.connection.ConnectionProfile(
                    new com.ynpe.fastremote.domain.connection.Host("example.test"),
                    new com.ynpe.fastremote.domain.connection.Port(22),
                    new com.ynpe.fastremote.domain.connection.Username("fixture"),
                    com.ynpe.fastremote.domain.connection.AuthenticationType.PASSWORD, Optional.empty());
            SavedHost host = SavedHost.create("数据库", Optional.empty(), profile);
            SavedHost duplicateName = SavedHost.create("数据库", Optional.empty(), profile);
            Map<TreeItem<String>, SavedHost> hosts = (Map<TreeItem<String>, SavedHost>) get(main, "hostItems");
            Map<TreeItem<String>, HostGroup> groups = (Map<TreeItem<String>, HostGroup>) get(main, "groupItems");
            hosts.put(first, host); hosts.put(second, duplicateName); groups.put(groupItem, group);
            set(main, "hostTree", tree);
            var selectContext = MainWindowController.class.getDeclaredMethod("selectContextTreeItem", TreeView.class, TreeItem.class);
            selectContext.setAccessible(true);
            var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            Map<javafx.scene.input.DataFormat, Object> previous = new HashMap<>();
            for (var type : clipboard.getContentTypes()) previous.put(type, clipboard.getContent(type));
            try {
                // Selection click order differs from tree order; equal names must remain separate lines.
                tree.getSelectionModel().selectIndices(2, 0, 1);
                selectContext.invoke(null, tree, first);
                ContextMenu menu = hostMenu(main, dialog, tree, first, hosts, groups);
                var copyGroup = menu.getItems().subList(menu.getItems().size() - 3, menu.getItems().size());
                assertInstanceOf(SeparatorMenuItem.class, copyGroup.getFirst());
                assertEquals(com.ynpe.fastremote.i18n.Texts.text("hostLibrary.copyName"), copyGroup.get(1).getText());
                assertEquals(com.ynpe.fastremote.i18n.Texts.text("host.copyIp"), copyGroup.getLast().getText());
                copyGroup.get(1).fire();
                assertEquals("生产环境\n数据库\n数据库", clipboard.getString());
                copyGroup.getLast().fire();
                assertEquals(String.join(System.lineSeparator(), "example.test", "example.test"), clipboard.getString());
                tree.getSelectionModel().clearAndSelect(0);
                var groupMenu = hostMenu(main, dialog, tree, groupItem, hosts, groups);
                assertEquals(com.ynpe.fastremote.i18n.Texts.text("hostLibrary.copyName"),
                        groupMenu.getItems().getLast().getText());
                groupMenu.getItems().getLast().fire();
                assertEquals("生产环境", clipboard.getString());
                // Right-clicking an unselected row must copy only that newly selected row.
                selectContext.invoke(null, tree, second);
                hostMenu(main, dialog, tree, second, hosts, groups).getItems().getLast().fire();
                assertEquals("example.test", clipboard.getString());
            } finally {
                clipboard.setContent(previous);
            }
            return null;
        });
    }

    private static ContextMenu hostMenu(MainWindowController main, boolean dialog, TreeView<String> tree,
            TreeItem<String> item, Map<TreeItem<String>, SavedHost> hosts,
            Map<TreeItem<String>, HostGroup> groups) throws Exception {
        ContextMenu menu;
        if (dialog) {
            var method = MainWindowController.class.getDeclaredMethod("hostLibraryContextMenu",
                    TreeView.class, TreeItem.class, Map.class, Map.class, javafx.stage.Window.class);
            method.setAccessible(true);
            menu = (ContextMenu) method.invoke(main, tree, item, hosts, groups, null);
        } else {
            var method = MainWindowController.class.getDeclaredMethod("catalogContextMenu", TreeItem.class);
            method.setAccessible(true);
            menu = (ContextMenu) method.invoke(main, item);
        }
        return menu;
    }

    @Test void terminalAndSftpTabsCopyTheirOwnConnectionAddress() throws Exception {
        fx(() -> {
            MainWindowController main = controller(new AtomicInteger());
            TabPane tabs = new TabPane();
            set(main, "workspaceTabs", tabs);
            set(main, "workspacePanes", new WorkspacePaneCoordinator(tabs, ignored -> {}, ignored -> {}, () -> {}));
            @SuppressWarnings("unchecked") Map<Tab, OpenSession> sessions =
                    (Map<Tab, OpenSession>) get(main, "openSessions");
            var install = MainWindowController.class.getDeclaredMethod("installWorkspaceTabContextMenu", Tab.class);
            install.setAccessible(true);
            var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            Map<javafx.scene.input.DataFormat, Object> previous = new HashMap<>();
            for (var type : clipboard.getContentTypes()) previous.put(type, clipboard.getContent(type));
            try {
                for (WorkspaceType type : List.of(WorkspaceType.TERMINAL, WorkspaceType.SFTP)) {
                    String address = type == WorkspaceType.TERMINAL ? "203.0.113.20" : "files.example.test";
                    var profile = new com.ynpe.fastremote.domain.connection.ConnectionProfile(
                            new com.ynpe.fastremote.domain.connection.Host(address),
                            new com.ynpe.fastremote.domain.connection.Port(2222),
                            new com.ynpe.fastremote.domain.connection.Username("fixture"),
                            com.ynpe.fastremote.domain.connection.AuthenticationType.PASSWORD, Optional.empty());
                    Tab tab = new Tab("Host display name");
                    tabs.getTabs().add(tab);
                    sessions.put(tab, new OpenSession("Host display name", profile, type));
                    install.invoke(main, tab);
                    MenuItem copyIp = tab.getContextMenu().getItems().getLast();
                    assertEquals(com.ynpe.fastremote.i18n.Texts.text("host.copyIp"), copyIp.getText());
                    copyIp.fire();
                    assertEquals(address, clipboard.getString());
                }
            } finally {
                clipboard.setContent(previous);
            }
            return null;
        });
    }

    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test void railFirstOpenInitializesChatOnceAndReopeningKeepsTheView() throws Exception {
        fx(() -> {
            AtomicInteger loads = new AtomicInteger();
            MainWindowController main = controller(loads);
            FXMLLoader loader = new FXMLLoader(MainView.fxml(), com.ynpe.fastremote.i18n.Texts.localizer().bundle());
            loader.setControllerFactory(type -> {
                if (type == MainWindowController.class) return main;
                try { return type.getDeclaredConstructor().newInstance(); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            try {
                assertNull(get(main, "aiChatView"));
                invoke(main, "toggleAiAssistant");
                Object chat = get(main, "aiChatView");
                assertNotNull(chat);
                assertEquals(1, loads.get());
                invoke(main, "toggleAiAssistant");
                invoke(main, "toggleAiAssistant");
                assertSame(chat, get(main, "aiChatView"));
                assertEquals(1, loads.get());
            } finally { main.close(); stage.close(); }
            return null;
        });
    }

    @Test void hostDialogGroupMenusExposeTheSameShortcutsAsDockMenus() throws Exception {
        fx(() -> {
            MainWindowController main = controller(new AtomicInteger());
            TreeView<String> tree = new TreeView<>();
            TreeItem<String> groupItem = new TreeItem<>("group");
            tree.setRoot(groupItem);
            tree.getSelectionModel().select(groupItem);
            HostGroup group = new HostGroup(GroupId.create(), Optional.empty(), "group", 0);
            Map<TreeItem<String>, SavedHost> hosts = new IdentityHashMap<>();
            Map<TreeItem<String>, HostGroup> groups = new IdentityHashMap<>();
            groups.put(groupItem, group);
            var method = MainWindowController.class.getDeclaredMethod("hostLibraryContextMenu",
                    TreeView.class, TreeItem.class, Map.class, Map.class, javafx.stage.Window.class);
            method.setAccessible(true);
            ContextMenu menu = (ContextMenu) method.invoke(main, tree, groupItem, hosts, groups, new Stage());
            for (ShortcutAction action : List.of(ShortcutAction.CREATE_HOST,
                    ShortcutAction.EDIT_SELECTED_LIBRARY_ITEM, ShortcutAction.COPY_SELECTED_LIBRARY_ITEM,
                    ShortcutAction.DELETE_SELECTED_LIBRARY_ITEM)) {
                assertTrue(menu.getItems().stream().anyMatch(item ->
                        action.defaultCombination().equals(item.getAccelerator())), action.name());
            }
            return null;
        });
    }

    @Test void dialogKeyboardUsesCustomBindingWithoutInterceptingSearchEditing() throws Exception {
        fx(() -> {
            MainWindowController main = controller(new AtomicInteger());
            javafx.scene.layout.BorderPane root = new javafx.scene.layout.BorderPane();
            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            set(main, "rootPane", root);
            HostGroup group = new HostGroup(GroupId.create(), Optional.empty(), "group", 0);
            set(main, "catalog", new HostCatalogSnapshot(List.of(group), List.of(), List.of(),
                    com.ynpe.fastremote.domain.vault.VaultStatus.NOT_CREATED));
            var shortcuts = new com.ynpe.fastremote.domain.settings.KeyboardShortcutSettings(
                    Map.of(ShortcutAction.OPEN_SELECTED_LIBRARY_ITEM.id(), "F6"));
            var window = new com.ynpe.fastremote.domain.settings.WindowSettings(false, false, Optional.empty(),
                    com.ynpe.fastremote.domain.settings.ApplicationTheme.LIGHT, shortcuts);
            set(main, "persistedWorkspaceSettings",
                    com.ynpe.fastremote.domain.settings.GlobalSettings.defaults().withWindowSettings(window));
            FutureTask<Void> assertions = new FutureTask<>(() -> {
                Dialog<?> dialog = (Dialog<?>) get(main, "hostLibraryDialog");
                try {
                    @SuppressWarnings("unchecked") TreeView<String> tree = (TreeView<String>)
                            dialog.getDialogPane().lookup(".catalog-tree");
                    TreeItem<String> item = tree.getRoot().getChildren().getFirst();
                    tree.getSelectionModel().select(item);
                    item.setExpanded(false);
                    tree.fireEvent(key(javafx.scene.input.KeyCode.F6));
                    assertTrue(item.isExpanded(), "Custom open key expands the selected group");
                    tree.fireEvent(key(javafx.scene.input.KeyCode.F6));
                    assertFalse(item.isExpanded(), "Each press dispatches only once");
                    TextField search = (TextField) get(main, "hostLibrarySearchField");
                    search.fireEvent(key(javafx.scene.input.KeyCode.F6));
                    assertFalse(item.isExpanded(), "Text input keeps its editing keys");
                    return null;
                } finally { dialog.close(); }
            });
            Platform.runLater(assertions);
            try {
                invoke(main, "showHostLibraryDialog");
                assertions.get(5, TimeUnit.SECONDS);
            } finally { stage.close(); }
            return null;
        });
    }

    private static javafx.scene.input.KeyEvent key(javafx.scene.input.KeyCode code) {
        return new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                "", "", code, false, false, false, false);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    private static MainWindowController controller(AtomicInteger loads) {
        AiAssistantWorkflow ai = (AiAssistantWorkflow) Proxy.newProxyInstance(AiAssistantWorkflow.class.getClassLoader(),
                new Class<?>[]{AiAssistantWorkflow.class}, (proxy, method, args) -> {
                    if (method.getName().equals("listChatSessions")) loads.incrementAndGet();
                    if (CompletionStage.class.isAssignableFrom(method.getReturnType())) return new CompletableFuture<>();
                    return null;
                });
        return new MainWindowController(unused(ConnectionWorkflow.class), unused(HostCatalogWorkflow.class),
                unused(SettingsWorkflow.class), unused(SnippetWorkflow.class), unused(LocalTerminalWorkflow.class), ai, null);
    }

    @SuppressWarnings("unchecked") private static <T> T unused(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (CompletionStage.class.isAssignableFrom(method.getReturnType())) return new CompletableFuture<>();
            return null;
        });
    }
    private static Object get(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target);
    }
    private static <T> T fx(Callable<T> call) throws Exception {
        FutureTask<T> task = new FutureTask<>(call); Platform.runLater(task); return task.get(20, TimeUnit.SECONDS);
    }
}
