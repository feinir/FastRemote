// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDirectoryLaunchTest {
    @TempDir Path directory;

    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
    }

    @Test void queuesUntilSettingsReadyThenOpensLocalFilesAtEachRequestedDirectory() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            List<Path> listed = new ArrayList<>();
            ConnectionWorkflow workflow = (ConnectionWorkflow) Proxy.newProxyInstance(
                    ConnectionWorkflow.class.getClassLoader(), new Class<?>[] {ConnectionWorkflow.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("listLocal")) listed.add((Path) args[0]);
                        // Keep asynchronous loading pending: only launch routing is under test.
                        return new CompletableFuture<>();
                    });
            MainWindowController controller = new MainWindowController(workflow,
                    unused(HostCatalogWorkflow.class), unused(SettingsWorkflow.class), unused(SnippetWorkflow.class));
            TabPane tabs = new TabPane();
            set(controller, "workspaceTabs", tabs);
            set(controller, "workspaceIntro", new StackPane());
            set(controller, "workspacePanes", new WorkspacePaneCoordinator(tabs, ignored -> {}, ignored -> {}, () -> {}));
            controller.requestInitialLocalFileDirectories(List.of(directory.resolve("child/..")));
            assertTrue(tabs.getTabs().isEmpty());
            assertTrue(listed.isEmpty());
            set(controller, "workspaceSettingsLoaded", true);
            var drain = MainWindowController.class.getDeclaredMethod("openRequestedInitialEditorFiles");
            drain.setAccessible(true);
            drain.invoke(controller);
            assertEquals(List.of(directory), listed);
            Path second = directory.resolve("second");
            controller.requestAdditionalLocalFileDirectories(List.of(second));
            assertEquals(List.of(directory, second), listed);
            var files = MainWindowController.class.getDeclaredField("localFileWorkspaces");
            files.setAccessible(true);
            Map<?, ?> fileWorkspaces = (Map<?, ?>) files.get(controller);
            assertEquals(2, fileWorkspaces.size());
            var terminals = MainWindowController.class.getDeclaredField("localTerminals");
            terminals.setAccessible(true);
            assertTrue(((Map<?, ?>) terminals.get(controller)).isEmpty());
            drain.invoke(controller);
            assertEquals(2, fileWorkspaces.size());
            fileWorkspaces.values().forEach(value -> ((LocalFileWorkspaceController) value).close());
            return null;
        });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T unused(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> { throw new AssertionError(method.getName()); }));
    }
}
