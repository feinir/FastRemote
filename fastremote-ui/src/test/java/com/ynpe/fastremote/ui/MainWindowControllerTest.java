// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.RecentConnection;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.catalog.HostIcon;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.Username;
import com.ynpe.fastremote.domain.connection.ConnectionProgress;
import com.ynpe.fastremote.domain.connection.ConnectionException;
import com.ynpe.fastremote.domain.connection.ConnectionFailureType;
import com.ynpe.fastremote.domain.credential.CredentialGroup;
import com.ynpe.fastremote.domain.credential.CredentialGroupId;
import com.ynpe.fastremote.domain.credential.CredentialScope;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import com.ynpe.fastremote.domain.settings.HostTabTitlePart;
import com.ynpe.fastremote.domain.settings.DockSide;
import com.ynpe.fastremote.domain.settings.SftpBookmarks;
import com.ynpe.fastremote.domain.settings.SftpSettings;
import com.ynpe.fastremote.domain.snippet.SnippetGroupId;
import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import com.ynpe.fastremote.domain.vault.SecretId;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import com.ynpe.fastremote.domain.vault.VaultException;
import com.ynpe.fastremote.domain.vault.VaultFailureType;
import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.ModelProviderType;
import com.ynpe.fastremote.domain.ai.AiSettings;
import com.ynpe.fastremote.domain.ai.AiReasoningEffort;
import com.ynpe.fastremote.domain.ai.AiModelCapabilities;
import com.ynpe.fastremote.domain.ai.AiInputModality;
import com.ynpe.fastremote.ui.WorkspaceViewModel.WorkspaceType;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.security.SecureRandom;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.Set;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableColumn;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.geometry.Rectangle2D;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainWindowControllerTest {
    @Test
    void configurationUsesFreshLocalAiSettingsWithoutWaitingForAssistantWarmup() {
        AiSettings cached = AiSettings.defaults();
        AiSettings loaded = new AiSettings(
                cached.providers(), cached.selectedProviderId(), cached.builtInPermissions(),
                cached.mcpPermissions(), cached.allowedLocalRoots(), cached.allowedRemoteRoots(),
                cached.maxTransferBytes(), cached.maxToolRounds() + 1, cached.mcpServer());

        assertSame(loaded, MainWindowController.configurationAiSettings(loaded, null, cached));
    }

    @Test
    void configurationFallsBackToLastKnownAiSettingsWhenLocalLoadingFails() {
        AiSettings cached = AiSettings.defaults();

        assertSame(cached, MainWindowController.configurationAiSettings(
                null, new IllegalStateException("unavailable"), cached));
    }

    @TempDir
    Path temporaryDirectory;

    @Test
    void droppedFileBatchWarnsAfterFiveAndRejectsAfterFifty() {
        assertEquals(MainWindowController.DroppedFileBatchPolicy.OPEN,
                MainWindowController.droppedFileBatchPolicy(5));
        assertEquals(MainWindowController.DroppedFileBatchPolicy.CONFIRM,
                MainWindowController.droppedFileBatchPolicy(6));
        assertEquals(MainWindowController.DroppedFileBatchPolicy.CONFIRM,
                MainWindowController.droppedFileBatchPolicy(50));
        assertEquals(MainWindowController.DroppedFileBatchPolicy.REJECT,
                MainWindowController.droppedFileBatchPolicy(51));
    }

    @Test
    void droppedFileInspectionIgnoresFoldersAndFilesOverTheEditorLimit() throws Exception {
        Path accepted = Files.writeString(temporaryDirectory.resolve("accepted.txt"), "ok");
        Path oversized = Files.write(temporaryDirectory.resolve("oversized.txt"), new byte[5]);
        Path folder = Files.createDirectory(temporaryDirectory.resolve("folder"));

        MainWindowController.DroppedFileScan scan = MainWindowController.inspectDroppedFiles(
                List.of(accepted, oversized, folder, accepted), 4L);

        assertEquals(List.of(accepted), scan.eligible().stream().map(LocalFileEntry::path).toList());
        assertEquals(2, scan.ignored());
    }

    @Test
    void externalFileDropRecognizesOnlyTerminalDescendantsAsTerminalTargets() {
        StackPane terminalHost = new StackPane();
        terminalHost.getStyleClass().add("terminal-host");
        StackPane terminalCanvas = new StackPane();
        terminalHost.getChildren().add(terminalCanvas);
        StackPane editor = new StackPane();

        assertTrue(MainWindowController.isTerminalDropTarget(terminalHost));
        assertTrue(MainWindowController.isTerminalDropTarget(terminalCanvas));
        assertFalse(MainWindowController.isTerminalDropTarget(editor));
        assertFalse(MainWindowController.isTerminalDropTarget(null));
    }

    @Test
    void terminalPathDropLeavesWorkspacePayloadForTheOuterSplitHandler() {
        assertTrue(MainWindowController.shouldBubbleTerminalPathDrop(
                Set.of(WorkspacePaneCoordinator.PANE_FORMAT)));
        assertTrue(MainWindowController.shouldBubbleTerminalPathDrop(
                Set.of(WorkspacePaneCoordinator.WORKSPACE_FORMAT)));
        assertFalse(MainWindowController.shouldBubbleTerminalPathDrop(
                Set.of(TerminalPathDragSupport.LOCAL_PATHS)));
        assertFalse(MainWindowController.shouldBubbleTerminalPathDrop(
                Set.of(TerminalPathDragSupport.REMOTE_PATHS)));
    }

    @Test
    void editorDropFallbackRunsOnlyAfterAnUnconsumedChildDrop() {
        javafx.event.EventType<javafx.event.Event> drop =
                new javafx.event.EventType<>("drop-fallback-routing");
        StackPane root = new StackPane();
        StackPane sftpTarget = new StackPane();
        root.getChildren().add(sftpTarget);
        AtomicInteger editorFallbacks = new AtomicInteger();
        MainWindowController.addDropFallbackHandler(root, drop,
                ignored -> editorFallbacks.incrementAndGet());
        sftpTarget.addEventHandler(drop, javafx.event.Event::consume);

        javafx.event.Event.fireEvent(sftpTarget, new javafx.event.Event(drop));

        assertEquals(0, editorFallbacks.get());

        StackPane ordinaryTarget = new StackPane();
        root.getChildren().setAll(ordinaryTarget);
        javafx.event.Event.fireEvent(ordinaryTarget, new javafx.event.Event(drop));

        assertEquals(1, editorFallbacks.get());
    }

    @Test
    void statusTrafficFormatsReceivedAndSentTotals() {
        String rendered = MainWindowController.trafficStatusText(1536L, 2048L, 512L, 1024L);
        assertTrue(rendered.contains("1.5 KiB"));
        assertTrue(rendered.contains("2.0 KiB"));
        assertTrue(rendered.contains("512 B/s"));
        assertTrue(rendered.contains("1.0 KiB/s"));
        assertEquals(2_000L, MainWindowController.trafficRate(1_000L, 500_000_000L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void localTerminalTextDoesNotIncreaseNetworkTrafficAcrossSamples() throws Exception {
        var submitted = new java.util.concurrent.FutureTask<Void>(() -> {
            MainWindowController controller = new MainWindowController(
                    (ConnectionWorkflow) Proxy.newProxyInstance(ConnectionWorkflow.class.getClassLoader(),
                            new Class<?>[] {ConnectionWorkflow.class}, (proxy, method, arguments) -> null),
                    (HostCatalogWorkflow) Proxy.newProxyInstance(HostCatalogWorkflow.class.getClassLoader(),
                            new Class<?>[] {HostCatalogWorkflow.class}, (proxy, method, arguments) -> null),
                    (SettingsWorkflow) Proxy.newProxyInstance(SettingsWorkflow.class.getClassLoader(),
                            new Class<?>[] {SettingsWorkflow.class}, (proxy, method, arguments) -> null),
                    (SnippetWorkflow) Proxy.newProxyInstance(SnippetWorkflow.class.getClassLoader(),
                            new Class<?>[] {SnippetWorkflow.class}, (proxy, method, arguments) -> null));
            var labelField = MainWindowController.class.getDeclaredField("trafficStatisticsLabel");
            labelField.setAccessible(true);
            javafx.scene.control.Label label = new javafx.scene.control.Label();
            labelField.set(controller, label);
            var terminalsField = MainWindowController.class.getDeclaredField("localTerminals");
            terminalsField.setAccessible(true);
            var terminals = (java.util.Map<javafx.scene.control.Tab, LocalTerminalHandle>)
                    terminalsField.get(controller);
            var received = new java.util.concurrent.atomic.AtomicLong(3_145_728L);
            var sent = new java.util.concurrent.atomic.AtomicLong(1_048_576L);
            StackPane terminalNode = new StackPane();
            terminals.put(new javafx.scene.control.Tab(), new LocalTerminalHandle(
                    terminalNode, terminalNode, () -> {}, new CompletableFuture<>(), ignored -> {},
                    input -> CompletableFuture.completedFuture(null), received::get, sent::get));
            var sample = MainWindowController.class.getDeclaredMethod("sampleTraffic");
            sample.setAccessible(true);
            var receivedField = MainWindowController.class.getDeclaredField("totalReceivedBytes");
            var sentField = MainWindowController.class.getDeclaredField("totalSentBytes");
            receivedField.setAccessible(true);
            sentField.setAccessible(true);
            for (int attempt = 0; attempt < 3; attempt++) {
                sample.invoke(controller);
                assertEquals(0L, receivedField.getLong(controller));
                assertEquals(0L, sentField.getLong(controller));
                assertEquals(MainWindowController.trafficStatusText(0L, 0L, 0L, 0L), label.getText());
                received.addAndGet(4096L);
                sent.addAndGet(1024L);
            }
            terminals.clear();
            sample.invoke(controller);
            assertEquals(0L, receivedField.getLong(controller));
            assertEquals(0L, sentField.getLong(controller));
            return null;
        });
        Platform.runLater(submitted);
        submitted.get(10, TimeUnit.SECONDS);
    }

    @Test
    void quickSaveMatchesTheExistingHostByHostPortAndAccount() {
        ConnectionProfile firstProfile = new ConnectionProfile(new Host("Example.COM"), new Port(22),
                new Username("ops"), AuthenticationType.PASSWORD, Optional.empty());
        SavedHost first = SavedHost.create("first", Optional.empty(), firstProfile);
        ConnectionProfile latestProfile = new ConnectionProfile(new Host("example.com"), new Port(22),
                new Username("ops"), AuthenticationType.PRIVATE_KEY, Optional.of(Path.of("id_ed25519")));

        assertEquals(first.id(), MainWindowController.quickSaveMatch(List.of(first), latestProfile)
                .orElseThrow().id());
        assertTrue(MainWindowController.quickSaveMatch(List.of(first), new ConnectionProfile(
                new Host("example.com"), new Port(23), new Username("ops"),
                AuthenticationType.PASSWORD, Optional.empty())).isEmpty());
    }

    @Test
    void commandPaletteDoesNotOfferGoToForEditors() {
        assertFalse(MainWindowController.supportsFavoritePathGoTo(
                MainWindowController.PaletteWorkspace.EDITOR));
    }

    @Test
    void globalMenuShortcutHelperInstallsVisibleAccelerator() {
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem("Save");
        MainWindowController.setMenuShortcut(item, "Shortcut+S");
        assertEquals(javafx.scene.input.KeyCombination.valueOf("Shortcut+S"), item.getAccelerator());
    }

    @Test
    void nativeEditingShortcutsAreOwnedOnlyByTheFocusedTextControl() {
        javafx.scene.control.MenuItem cut = new javafx.scene.control.MenuItem("Cut");
        javafx.scene.control.MenuItem copy = new javafx.scene.control.MenuItem("Copy");
        javafx.scene.control.MenuItem paste = new javafx.scene.control.MenuItem("Paste");
        MainWindowController.setMenuShortcut(cut, "Shortcut+X");
        MainWindowController.setMenuShortcut(copy, "Shortcut+C");
        MainWindowController.setMenuShortcut(paste, "Shortcut+V");

        MainWindowController.letFocusedTextControlOwnNativeShortcuts(cut, copy, paste);

        assertNull(cut.getAccelerator());
        assertNull(copy.getAccelerator());
        assertNull(paste.getAccelerator());
    }

    @Test
    void builtinCommandImportDefaultsToOverwritingExistingCommands() {
        CheckBox overwrite = MainWindowController.builtinOverwriteCheckBox("Overwrite existing commands");

        assertTrue(overwrite.isSelected());
        assertEquals("Overwrite existing commands", overwrite.getText());
    }

    @Test
    void goToRemotePathsMergeGlobalAndHostFavoritesInStableOrder() {
        assertEquals(List.of("/global", "/shared", "/host"),
                MainWindowController.mergedFavoritePaths(
                        List.of("/global", "/shared"), List.of("/shared", "/host")));
    }

    @Test
    void commandPaletteSearchesEveryFavoritePathOnce() {
        SftpSettings defaults = SftpSettings.defaults();
        SftpSettings settings = new SftpSettings(defaults.showHiddenFiles(), defaults.concurrentTransfers(),
                new SftpBookmarks(List.of("/local", "/shared"), List.of("/remote", "/shared")),
                java.util.Map.of(
                        "host-a", new SftpBookmarks(List.of("D:\\work"), List.of("/host-a")),
                        "host-b", new SftpBookmarks(List.of(), List.of("/host-b", "/remote"))),
                defaults.maxEditableFileBytes(), defaults.editorFontFamily(), defaults.editorFontSize(),
                defaults.editorTheme(), defaults.doubleClickFileAction());

        List<String> paths = MainWindowController.allFavoritePaths(settings);

        assertEquals(6, paths.size());
        assertEquals(List.of("/local", "/shared", "/remote"), paths.subList(0, 3));
        assertTrue(paths.containsAll(List.of("D:\\work", "/host-a", "/host-b")));
        assertEquals(1, paths.stream().filter("/remote"::equals).count());
    }

    @Test
    void sftpPaletteUsesLocalOnlyForFocusInsideLocalBrowserAndDefaultsRemote() {
        OpenSession session = new OpenSession("host", new ConnectionProfile(
                new Host("example.test"), new Port(22), new Username("user"),
                AuthenticationType.PASSWORD, Optional.empty()),
                WorkspaceType.SFTP);
        session.localTable = new javafx.scene.control.TableView<>();
        session.remoteTable = new javafx.scene.control.TableView<>();
        assertTrue(MainWindowController.localSftpFocus(session, session.localTable));
        assertFalse(MainWindowController.localSftpFocus(session, session.remoteTable));
        assertFalse(MainWindowController.localSftpFocus(session, new javafx.scene.control.Label("log")));
        assertFalse(MainWindowController.localSftpFocus(session, null));
        var localToolbarButton = new javafx.scene.control.Button("Refresh");
        session.localBrowser = new javafx.scene.layout.VBox(localToolbarButton, session.localTable);
        assertTrue(MainWindowController.localSftpFocus(session, localToolbarButton));
    }

    @Test
    void sftpPaletteSnapshotsEachSideAndTreeTargetsBeforeFocusAndSelectionChange() {
        OpenSession session = new OpenSession("host", new ConnectionProfile(
                new Host("example.test"), new Port(22), new Username("user"),
                AuthenticationType.PASSWORD, Optional.empty()), WorkspaceType.SFTP);
        session.currentLocalPath = "/local";
        session.currentRemotePath = "/remote";
        session.localTable = new javafx.scene.control.TableView<>();
        session.remoteTable = new javafx.scene.control.TableView<>();
        var local = new LocalFileEntry("a.txt", java.nio.file.Path.of("/local/a.txt"), false, false, 0, Optional.empty());
        var remote = new com.ynpe.fastremote.domain.transfer.RemoteFileEntry("b.txt", "/remote/b.txt", false, false, 0, Optional.empty());
        session.localTable.getItems().add(local);
        session.remoteTable.getItems().add(remote);
        session.localTable.getSelectionModel().select(local);
        session.remoteTable.getSelectionModel().select(remote);
        var left = SftpPaletteSelection.capture(session, true, session.localTable);
        var right = SftpPaletteSelection.capture(session, false, session.remoteTable);
        session.localTable.getSelectionModel().clearSelection();
        session.remoteTable.getSelectionModel().clearSelection();
        session.currentRemotePath = "/changed";
        assertEquals(List.of(local.path().toString()), left.paths());
        assertEquals(List.of("/remote/b.txt"), right.paths());
        assertEquals("/remote", right.directory());
        assertEquals(List.of("b.txt"), right.names());
        var node = new javafx.scene.control.TreeItem<>(new com.ynpe.fastremote.domain.transfer.RemoteFileEntry(
                "folder", "/remote/folder", true, false, 0, Optional.empty()));
        session.remoteTree = new javafx.scene.control.TreeView<>(node);
        session.remoteTree.getSelectionModel().select(node);
        var tree = SftpPaletteSelection.capture(session, false, session.remoteTree);
        assertEquals("/remote/folder", tree.directory());
        assertEquals(List.of("/remote/folder"), tree.paths());
        var blank = SftpPaletteSelection.capture(session, false, session.remoteTable);
        assertEquals(List.of("/changed"), blank.paths());
        assertFalse(blank.hasSelection());
    }

    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    @Test
    void sftpDropCommitsOnTheNextFxTurnAfterDragDone() throws Exception {
        OpenSession session = new OpenSession("host", new ConnectionProfile(
                new Host("example.test"), new Port(22), new Username("user"),
                AuthenticationType.PASSWORD, Optional.empty()), WorkspaceType.SFTP);
        AtomicInteger starts = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);

        var submitted = new java.util.concurrent.FutureTask<Void>(() -> {
            MainWindowController.beginSftpDrag(session);
            MainWindowController.deferSftpDropUntilDragDone(session, () -> {
                starts.incrementAndGet();
                started.countDown();
            });
            assertEquals(0, starts.get());
            MainWindowController.finishSftpDrag(session);
            assertEquals(0, starts.get());
            return null;
        });
        Platform.runLater(submitted);
        submitted.get(5, TimeUnit.SECONDS);
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertEquals(1, starts.get());
        assertNull(session.pendingSftpDropAction);
    }

    @Test
    void startingAnotherSftpDragDiscardsAnUnfinishedDropAction() {
        OpenSession session = new OpenSession("host", new ConnectionProfile(
                new Host("example.test"), new Port(22), new Username("user"),
                AuthenticationType.PASSWORD, Optional.empty()), WorkspaceType.SFTP);
        session.pendingSftpDropAction = () -> { };
        session.draggedLocalEntries = List.of(new LocalFileEntry(
                "old", Path.of("old"), false, false, 1L, Optional.empty()));

        MainWindowController.beginSftpDrag(session);

        assertNull(session.pendingSftpDropAction);
        assertTrue(session.draggedLocalEntries.isEmpty());
        assertTrue(session.draggedRemoteEntries.isEmpty());
    }

    @Test
    void sessionGroupIsNotAConnectTargetOnDoubleClick() {
        TreeItem<String> group = new TreeItem<>("生产环境");

        assertTrue(MainWindowController.connectTarget(group, java.util.Map.of()).isEmpty());
        assertTrue(MainWindowController.connectTarget(null, java.util.Map.of()).isEmpty());
    }

    @Test
    void sftpToolbarActionsUseDistinctIconOnlyButtonsWithAccessibleLabels() {
        Button up = MainWindowController.sftpActionButton("Up", LucideIconView.Icon.FOLDER_UP);
        Button upload = MainWindowController.sftpActionButton("Upload", LucideIconView.Icon.EXPORT);
        Button favorites = MainWindowController.sftpFavoriteButton("Favorites");

        assertEquals("", up.getText());
        assertEquals(ContentDisplay.GRAPHIC_ONLY, up.getContentDisplay());
        assertEquals("Up", up.getAccessibleText());
        assertEquals("Up", up.getTooltip().getText());
        assertTrue(up.getStyleClass().contains("sftp-toolbar-icon-button"));
        assertTrue(up.getGraphic() instanceof LucideIconView);
        assertTrue(upload.getGraphic() instanceof LucideIconView);
        assertEquals(LucideIconView.Icon.FOLDER_UP, ((LucideIconView) up.getGraphic()).getIcon());
        assertEquals(LucideIconView.Icon.EXPORT, ((LucideIconView) upload.getGraphic()).getIcon());
        assertNotEquals(((LucideIconView) up.getGraphic()).getIcon(),
                ((LucideIconView) upload.getGraphic()).getIcon());
        assertEquals(Button.class, favorites.getClass());
        assertEquals(LucideIconView.Icon.STAR, ((LucideIconView) favorites.getGraphic()).getIcon());
    }

    @Test
    void reconnectUsesAConnectionIconDistinctFromDirectoryRefresh() {
        LucideIconView reconnect = MainWindowController.workspaceReconnectIcon();

        assertEquals(LucideIconView.Icon.PLUG_ZAP, reconnect.getIcon());
        assertNotEquals(LucideIconView.Icon.REFRESH_CW, reconnect.getIcon());
    }

    @Test
    void workspaceNewTerminalButtonUsesTheSharedCompactIconAction() {
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<Button> invokedWith =
                new java.util.concurrent.atomic.AtomicReference<>();

        Button button = MainWindowController.workspaceIconButton(
                LucideIconView.Icon.PLUS, "New local terminal", action -> {
                    invokedWith.set(action);
                    calls.incrementAndGet();
                });

        assertEquals("", button.getText());
        assertEquals(ContentDisplay.GRAPHIC_ONLY, button.getContentDisplay());
        assertEquals("New local terminal", button.getAccessibleText());
        assertEquals("New local terminal", button.getTooltip().getText());
        assertTrue(button.getStyleClass().contains("workspace-rail-action"));
        assertTrue(button.getStyleClass().contains("workspace-icon-button"));
        assertTrue(button.getGraphic().getStyleClass().contains("workspace-action-icon"));
        assertEquals(LucideIconView.Icon.PLUS,
                ((LucideIconView) button.getGraphic()).getIcon());

        button.fire();

        assertEquals(1, calls.get());
        assertSame(button, invokedWith.get());
    }

    @Test
    void remoteWorkspaceNewSessionButtonOpensAnotherSessionOfTheSameType() {
        java.util.concurrent.atomic.AtomicReference<WorkspaceType> requestedType =
                new java.util.concurrent.atomic.AtomicReference<>();

        Button terminal = MainWindowController.workspaceNewSessionButton(
                WorkspaceType.TERMINAL, requestedType::set);

        assertEquals("", terminal.getText());
        assertEquals(com.ynpe.fastremote.i18n.Texts.text("workspace.newTerminalSession"),
                terminal.getAccessibleText());
        assertEquals(com.ynpe.fastremote.i18n.Texts.text("workspace.newTerminalSession"),
                terminal.getTooltip().getText());
        assertEquals(LucideIconView.Icon.PLUS,
                ((LucideIconView) terminal.getGraphic()).getIcon());
        terminal.fire();
        assertEquals(WorkspaceType.TERMINAL, requestedType.get());

        Button sftp = MainWindowController.workspaceNewSessionButton(
                WorkspaceType.SFTP, requestedType::set);

        assertEquals(com.ynpe.fastremote.i18n.Texts.text("workspace.newSftpSession"),
                sftp.getAccessibleText());
        assertEquals(LucideIconView.Icon.PLUS,
                ((LucideIconView) sftp.getGraphic()).getIcon());
        sftp.fire();
        assertEquals(WorkspaceType.SFTP, requestedType.get());
    }

    @Test
    void remoteWorkspaceCounterpartButtonEndsItsStatusToolGroup() {
        StackPane refresh = new StackPane();
        StackPane firstSeparator = new StackPane();
        StackPane counterpart = new StackPane();
        StackPane goTo = new StackPane();
        StackPane duplicate = new StackPane();
        StackPane create = new StackPane();
        StackPane secondSeparator = new StackPane();
        StackPane toggle = new StackPane();

        assertEquals(List.of(refresh, firstSeparator, goTo, duplicate, create, counterpart,
                        secondSeparator, toggle),
                MainWindowController.orderedWorkspaceStatusTools(WorkspaceType.TERMINAL,
                        refresh, firstSeparator, counterpart, goTo, duplicate, create,
                        secondSeparator, toggle));
        assertEquals(List.of(refresh, firstSeparator, goTo, create, counterpart,
                        secondSeparator, toggle),
                MainWindowController.orderedWorkspaceStatusTools(WorkspaceType.SFTP,
                        refresh, firstSeparator, counterpart, goTo, null, create,
                        secondSeparator, toggle));
    }

    @Test
    void blankWorkspaceHeaderMenuPlacesConnectionDialogsAfterEditor() {
        javafx.scene.control.MenuItem localTerminal = new javafx.scene.control.MenuItem("Local Terminal");
        javafx.scene.control.MenuItem localFiles = new javafx.scene.control.MenuItem("Local Files");
        javafx.scene.control.MenuItem editor = new javafx.scene.control.MenuItem("Editor");
        javafx.scene.control.MenuItem quickConnect = new javafx.scene.control.MenuItem("Quick Connect");
        javafx.scene.control.MenuItem hostLibrary = new javafx.scene.control.MenuItem("Host Library");
        javafx.scene.control.SeparatorMenuItem firstSeparator = new javafx.scene.control.SeparatorMenuItem();
        javafx.scene.control.MenuItem aiAssistant = new javafx.scene.control.MenuItem("AI Assistant");
        javafx.scene.control.MenuItem diagnosticLog = new javafx.scene.control.MenuItem("Diagnostic Log");
        javafx.scene.control.SeparatorMenuItem secondSeparator = new javafx.scene.control.SeparatorMenuItem();
        javafx.scene.control.MenuItem toolbar = new javafx.scene.control.MenuItem("Toolbar");
        javafx.scene.control.MenuItem statusBar = new javafx.scene.control.MenuItem("Status Bar");
        javafx.scene.control.SeparatorMenuItem thirdSeparator = new javafx.scene.control.SeparatorMenuItem();
        javafx.scene.control.MenuItem closeAll = new javafx.scene.control.MenuItem("Close All");

        assertEquals(List.of(localTerminal, localFiles, editor, quickConnect, hostLibrary, firstSeparator,
                        aiAssistant, diagnosticLog, secondSeparator, toolbar, statusBar,
                        thirdSeparator, closeAll),
                MainWindowController.orderedWorkspaceBlankHeaderMenuItems(
                        localTerminal, localFiles, editor, quickConnect, hostLibrary, firstSeparator,
                        aiAssistant, diagnosticLog, secondSeparator, toolbar, statusBar,
                        thirdSeparator, closeAll));
    }

    @Test
    void sftpMoreMenuGroupsBookmarksTerminalAndEditorActionsInThatOrder() {
        javafx.scene.control.MenuItem manage = new javafx.scene.control.MenuItem("Manage bookmarks");
        javafx.scene.control.MenuItem openInTerminal = new javafx.scene.control.MenuItem("Open in terminal");
        javafx.scene.control.MenuItem openNewSftp = new javafx.scene.control.MenuItem("Open new SFTP");
        javafx.scene.control.MenuItem editNew = new javafx.scene.control.MenuItem("Edit in new workspace");
        javafx.scene.control.MenuItem editCurrent = new javafx.scene.control.MenuItem("Edit in current workspace");

        javafx.scene.control.Menu more = MainWindowController.sftpMoreMenu(
                manage, openInTerminal, openNewSftp, List.of(editNew, editCurrent));

        assertEquals("sftp-context-more", more.getId());
        assertEquals(7, more.getItems().size());
        assertSame(manage, more.getItems().get(0));
        assertTrue(more.getItems().get(1) instanceof javafx.scene.control.SeparatorMenuItem);
        assertSame(openInTerminal, more.getItems().get(2));
        assertSame(openNewSftp, more.getItems().get(3));
        assertTrue(more.getItems().get(4) instanceof javafx.scene.control.SeparatorMenuItem);
        assertSame(editNew, more.getItems().get(5));
        assertSame(editCurrent, more.getItems().get(6));
    }

    @Test
    void sftpContextMenuPlacesAddFavoriteBeforeGoToTerminalAndMore() {
        javafx.scene.control.Menu go = new javafx.scene.control.Menu("Go to");
        javafx.scene.control.MenuItem addFavorite = new javafx.scene.control.MenuItem("Add favorite");
        javafx.scene.control.MenuItem goToTerminal = new javafx.scene.control.MenuItem("Go to terminal");
        javafx.scene.control.Menu more = new javafx.scene.control.Menu("More");

        assertEquals(List.of(go, addFavorite, goToTerminal, more),
                MainWindowController.sftpContextMenuTopItems(go, addFavorite, goToTerminal, more));
    }

    @Test
    void workspaceTabMenuPlacesSameTabOpenActionsAfterMoveToLast() {
        List<javafx.scene.control.MenuItem> items = java.util.stream.IntStream.range(0, 18)
                .mapToObj(index -> new javafx.scene.control.MenuItem("item-" + index))
                .toList();

        assertEquals(items, MainWindowController.orderedWorkspaceTabMenuItems(
                items.get(0), items.get(1), items.get(2), items.get(3), items.get(4),
                items.get(5), items.get(6), items.get(7), items.get(8), items.get(9),
                items.get(10), items.get(11), items.get(12), items.get(13), items.get(14),
                items.get(15), items.get(16), items.get(17)));
    }

    @Test
    void relatedPanelPlacementUsesSourcePaneOnlyForCurrentWorkspaceMode() {
        javafx.scene.control.Tab source = new javafx.scene.control.Tab("Source");

        assertEquals(source, MainWindowController.relatedPanelInsertionAnchor(true, source));
        assertNull(MainWindowController.relatedPanelInsertionAnchor(false, source));
        assertNull(MainWindowController.relatedPanelInsertionAnchor(true, null));

        var settings = com.ynpe.fastremote.domain.settings.WindowSettings.defaults()
                .withRelatedPanelPlacement(false, true, false);
        assertFalse(MainWindowController.openPairedPanelInCurrentWorkspace(
                settings, WorkspaceType.SFTP));
        assertTrue(MainWindowController.openPairedPanelInCurrentWorkspace(
                settings, WorkspaceType.TERMINAL));

        assertNull(MainWindowController.remoteEditorInsertionAnchor(settings, source,
                MainWindowController.RemoteEditorPlacement.CONFIGURED));
        assertNull(MainWindowController.remoteEditorInsertionAnchor(settings, source,
                MainWindowController.RemoteEditorPlacement.NEW_WORKSPACE));
        assertEquals(source, MainWindowController.remoteEditorInsertionAnchor(settings, source,
                MainWindowController.RemoteEditorPlacement.CURRENT_WORKSPACE));

        var currentWorkspaceSettings = settings.withRelatedPanelPlacement(false, true, true);
        assertEquals(source, MainWindowController.remoteEditorInsertionAnchor(
                currentWorkspaceSettings, source,
                MainWindowController.RemoteEditorPlacement.CONFIGURED));
        assertNull(MainWindowController.remoteEditorInsertionAnchor(
                currentWorkspaceSettings, source,
                MainWindowController.RemoteEditorPlacement.NEW_WORKSPACE));

        assertEquals(source, MainWindowController.localEditorInsertionAnchor(settings, source));
        var separateLocalEditor = settings.withRelatedPanelPlacement(
                false, true, false, false, true, true);
        assertNull(MainWindowController.localEditorInsertionAnchor(separateLocalEditor, source));
    }

    @Test
    void remoteTerminalDuplicateOnlyCarriesAnInferredWorkingDirectory() {
        OpenSession session = new OpenSession("host", new ConnectionProfile(
                new Host("example.com"), new Port(22), new Username("user"),
                AuthenticationType.PASSWORD, Optional.empty()), WorkspaceType.TERMINAL);

        assertNull(MainWindowController.remoteDuplicateInitialPath(session));
        session.latestInferredWorkingDirectory = "   ";
        assertNull(MainWindowController.remoteDuplicateInitialPath(session));
        session.latestInferredWorkingDirectory = "/srv/app";
        assertEquals("/srv/app", MainWindowController.remoteDuplicateInitialPath(session));
    }

    @Test
    void inferredTildePathExpandsAgainstTheCanonicalRemoteHome() {
        assertEquals("/home/zephyr/FastRemote",
                MainWindowController.expandRemoteHomePath("~/FastRemote", "/home/zephyr"));
        assertEquals("/home/zephyr",
                MainWindowController.expandRemoteHomePath("~", "/home/zephyr/"));
        assertEquals("/FastRemote",
                MainWindowController.expandRemoteHomePath("~/FastRemote", "/"));
        assertEquals("~/FastRemote",
                MainWindowController.expandRemoteHomePath("~/FastRemote", "."));
        assertEquals("~other/FastRemote",
                MainWindowController.expandRemoteHomePath("~other/FastRemote", "/home/zephyr"));
    }

    @Test
    void quickConnectDialogKeepsRequestedButtonOrderAndConnectAsTheDefaultAction() {
        List<javafx.scene.control.ButtonType> buttons = MainWindowController.quickConnectDialogButtonTypes();

        assertEquals(List.of("测试连接", "存入主机库", "取消", "连接"),
                buttons.stream().map(javafx.scene.control.ButtonType::getText).toList());
        assertEquals(javafx.scene.control.ButtonBar.ButtonData.OK_DONE,
                buttons.getLast().getButtonData());
    }

    @Test
    void quickConnectionTestPortValidationUsesTheSameUserFacingRange() {
        assertEquals(22, MainWindowController.parseQuickPort(" 22 "));
        assertEquals(65535, MainWindowController.parseQuickPort("65535"));
        assertEquals("端口必须是 1 到 65535 的整数",
                org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                        () -> MainWindowController.parseQuickPort("0")).getMessage());
    }

    @Test
    void batchDeletionContinuesAfterFailuresAndReportsEachFailedItem() {
        List<String> attempts = new ArrayList<>();

        List<MainWindowController.DeletionFailure<String>> failures = MainWindowController
                .deleteContinuing(List.of("first", "blocked", "last"), item -> {
                    attempts.add(item);
                    if (item.equals("blocked")) {
                        return CompletableFuture.failedFuture(new java.io.IOException("Permission denied"));
                    }
                    return CompletableFuture.completedFuture(null);
                }).join();

        assertEquals(List.of("first", "blocked", "last"), attempts);
        assertEquals(List.of("blocked"), failures.stream()
                .map(MainWindowController.DeletionFailure::item).toList());
    }

    @Test
    void sftpMarqueeDoesNotCaptureScrollBarsOrTableChrome() {
        StackPane scrollBar = new StackPane();
        scrollBar.getStyleClass().add("scroll-bar");
        StackPane corner = new StackPane();
        corner.getStyleClass().add("corner");
        StackPane blankContent = new StackPane();

        assertTrue(MainWindowController.blocksFileTableMarquee(scrollBar));
        assertTrue(MainWindowController.blocksFileTableMarquee(corner));
        assertFalse(MainWindowController.blocksFileTableMarquee(blankContent));
    }

    @Test
    void aiRailButtonIsHiddenWhileTheWorkspaceTabIsOpen() {
        assertFalse(MainWindowController.aiRailButtonVisible(true));
        assertTrue(MainWindowController.aiRailButtonVisible(false));
    }

    @Test
    void diagnosticLogRailButtonAndPlacementIconFollowTheLivePlacement() {
        assertFalse(MainWindowController.diagnosticLogRailButtonVisible(true));
        assertTrue(MainWindowController.diagnosticLogRailButtonVisible(false));
        assertEquals(LucideIconView.Icon.PANELS_TOP_LEFT,
                MainWindowController.diagnosticLogPlacementIcon(false, DockSide.RIGHT));
        assertEquals(LucideIconView.Icon.PANEL_LEFT,
                MainWindowController.diagnosticLogPlacementIcon(true, DockSide.LEFT));
        assertEquals(LucideIconView.Icon.PANEL_RIGHT,
                MainWindowController.diagnosticLogPlacementIcon(true, DockSide.RIGHT));
    }

    @Test
    void emptyGeneratedAiSessionTitlesFollowTheCurrentInterfaceLanguage() {
        com.ynpe.fastremote.i18n.Localizer previous = com.ynpe.fastremote.i18n.Texts.localizer();
        try {
            com.ynpe.fastremote.i18n.Texts.configure(com.ynpe.fastremote.i18n.Localizer.forLanguage(
                    com.ynpe.fastremote.domain.settings.InterfaceLanguage.GERMAN));
            Instant now = Instant.parse("2026-08-27T00:00:00Z");
            com.ynpe.fastremote.domain.ai.AiChatSession oldChineseDefault = new com.ynpe.fastremote.domain.ai.AiChatSession(
                    java.util.UUID.randomUUID(), "新会话", now, now, 0);
            com.ynpe.fastremote.domain.ai.AiChatSession existingConversation = new com.ynpe.fastremote.domain.ai.AiChatSession(
                    java.util.UUID.randomUUID(), "新会话", now, now, 1);
            com.ynpe.fastremote.domain.ai.AiChatSession customEmptyTitle = new com.ynpe.fastremote.domain.ai.AiChatSession(
                    java.util.UUID.randomUUID(), "Production notes", now, now, 0);

            assertEquals("Neuer Chat", MainWindowController.aiSessionDisplayTitle(oldChineseDefault));
            assertEquals("新会话", MainWindowController.aiSessionDisplayTitle(existingConversation));
            assertEquals("Production notes", MainWindowController.aiSessionDisplayTitle(customEmptyTitle));
        } finally {
            com.ynpe.fastremote.i18n.Texts.configure(previous);
        }
    }

    @Test
    void aiPlacementButtonReflectsTheCurrentPlacementAndConfiguredDockSide() {
        assertEquals("在主工作区打开", MainWindowController.aiPlacementLabel(false));
        assertEquals(LucideIconView.Icon.PANELS_TOP_LEFT,
                MainWindowController.aiPlacementIcon(false, DockSide.RIGHT));
        assertEquals("停靠到侧边栏", MainWindowController.aiPlacementLabel(true));
        assertEquals(LucideIconView.Icon.PANEL_LEFT,
                MainWindowController.aiPlacementIcon(true, DockSide.LEFT));
        assertEquals(LucideIconView.Icon.PANEL_RIGHT,
                MainWindowController.aiPlacementIcon(true, DockSide.RIGHT));
    }

    @Test
    void aiPromptUsesEnterToSendAndShiftEnterToInsertALineBreak() {
        AtomicInteger sends = new AtomicInteger();
        AtomicInteger lineBreaks = new AtomicInteger();
        KeyEvent shiftEnter = new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
                true, false, false, false);
        assertTrue(MainWindowController.handleAiPromptKeyReleased(
                shiftEnter, false, lineBreaks::incrementAndGet, sends::incrementAndGet));
        assertTrue(shiftEnter.isConsumed());
        assertEquals(1, lineBreaks.get());
        assertEquals(0, sends.get());

        KeyEvent enter = new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
                false, false, false, false);
        assertTrue(MainWindowController.handleAiPromptKeyReleased(
                enter, false, lineBreaks::incrementAndGet, sends::incrementAndGet));
        assertTrue(enter.isConsumed());
        assertEquals(1, lineBreaks.get());
        assertEquals(1, sends.get());

        KeyEvent letter = new KeyEvent(KeyEvent.KEY_RELEASED, "a", "a", KeyCode.A,
                false, false, false, false);
        assertFalse(MainWindowController.handleAiPromptKeyReleased(
                letter, false, lineBreaks::incrementAndGet, sends::incrementAndGet));
        assertFalse(letter.isConsumed());
    }

    @Test
    void aiPromptLeavesKeysToAnActiveInputMethodComposition() {
        AtomicInteger sends = new AtomicInteger();
        KeyEvent enter = new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
                false, false, false, false);

        assertFalse(MainWindowController.handleAiPromptKeyReleased(
                enter, true, () -> { }, sends::incrementAndGet));
        assertFalse(enter.isConsumed());
        assertEquals(0, sends.get());

        KeyEvent space = new KeyEvent(KeyEvent.KEY_RELEASED, " ", " ", KeyCode.SPACE,
                false, false, false, false);
        assertFalse(MainWindowController.handleAiPromptKeyReleased(
                space, true, () -> { }, sends::incrementAndGet));
        assertFalse(space.isConsumed());
    }

    @Test
    void toolWindowDropIndicatorUsesTheTargetButtonHalf() {
        assertTrue(MainWindowController.toolWindowDropBefore(0.0, 26.0));
        assertTrue(MainWindowController.toolWindowDropBefore(12.9, 26.0));
        assertFalse(MainWindowController.toolWindowDropBefore(13.0, 26.0));
        assertFalse(MainWindowController.toolWindowDropBefore(25.9, 26.0));
    }

    @Test
    void dockDisplayModeToggleAlternatesBothWays() {
        assertEquals(com.ynpe.fastremote.domain.settings.DockDisplayMode.FLOATING,
                MainWindowController.nextDockDisplayMode(
                        com.ynpe.fastremote.domain.settings.DockDisplayMode.EMBEDDED));
        assertEquals(com.ynpe.fastremote.domain.settings.DockDisplayMode.EMBEDDED,
                MainWindowController.nextDockDisplayMode(
                        com.ynpe.fastremote.domain.settings.DockDisplayMode.FLOATING));
        assertEquals(LucideIconView.Icon.EXTERNAL_LINK,
                MainWindowController.dockDisplayModeActionIcon(
                        DockSide.LEFT, com.ynpe.fastremote.domain.settings.DockDisplayMode.EMBEDDED));
        assertEquals(LucideIconView.Icon.EXTERNAL_LINK,
                MainWindowController.dockDisplayModeActionIcon(
                        DockSide.RIGHT, com.ynpe.fastremote.domain.settings.DockDisplayMode.EMBEDDED));
        assertEquals(LucideIconView.Icon.PANEL_LEFT,
                MainWindowController.dockDisplayModeActionIcon(
                        DockSide.LEFT, com.ynpe.fastremote.domain.settings.DockDisplayMode.FLOATING));
        assertEquals(LucideIconView.Icon.PANEL_RIGHT,
                MainWindowController.dockDisplayModeActionIcon(
                        DockSide.RIGHT, com.ynpe.fastremote.domain.settings.DockDisplayMode.FLOATING));
        assertNotEquals(LucideIconView.Icon.PANELS_TOP_LEFT,
                MainWindowController.dockDisplayModeActionIcon(
                        DockSide.RIGHT, com.ynpe.fastremote.domain.settings.DockDisplayMode.FLOATING));
    }

    @Test
    void successfulLanguageChangeStatusExplicitlyRequiresRestart() {
        com.ynpe.fastremote.i18n.Localizer localizer = com.ynpe.fastremote.i18n.Localizer.forLanguage(
                com.ynpe.fastremote.domain.settings.InterfaceLanguage.SIMPLIFIED_CHINESE);

        assertEquals("语言设置已保存。重启 FastRemote 后生效。",
                MainWindowController.settingsSavedStatus(
                        localizer,
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.ENGLISH,
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.SIMPLIFIED_CHINESE));
        assertEquals(localizer.text("legacy.java.19594093c677fedd"),
                MainWindowController.settingsSavedStatus(
                        localizer,
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.ENGLISH,
                        com.ynpe.fastremote.domain.settings.InterfaceLanguage.ENGLISH));
    }

    @Test
    void toolWindowCanMoveAcrossDockSidesWithoutChangingOtherAssignments() {
        com.ynpe.fastremote.domain.settings.GlobalSettings settings =
                com.ynpe.fastremote.domain.settings.GlobalSettings.defaults();

        com.ynpe.fastremote.domain.settings.GlobalSettings moved = MainWindowController.withToolDock(
                settings, MainWindowController.ToolWindow.HOST_LIBRARY, DockSide.RIGHT);

        assertEquals(DockSide.RIGHT, moved.hostLibraryDock());
        assertEquals(settings.quickConnectDock(), moved.quickConnectDock());
        assertEquals(settings.snippetsDock(), moved.snippetsDock());
        assertEquals(settings.variableLibraryDock(), moved.variableLibraryDock());
        assertEquals(settings.credentialVaultDock(), moved.credentialVaultDock());
        assertEquals(settings.aiAssistantDock(), moved.aiAssistantDock());
        assertEquals(settings.diagnosticLogDock(), moved.diagnosticLogDock());
    }

    @Test
    void sceneLevelDropResolverFindsButtonsAndEmptySpaceOnTheOppositeRail() {
        com.ynpe.fastremote.domain.settings.GlobalSettings settings =
                MainWindowController.withToolDock(
                        com.ynpe.fastremote.domain.settings.GlobalSettings.defaults(),
                        MainWindowController.ToolWindow.SNIPPETS, DockSide.RIGHT);
        var buttons = new java.util.EnumMap<MainWindowController.ToolWindow, javafx.geometry.Bounds>(
                MainWindowController.ToolWindow.class);
        buttons.put(MainWindowController.ToolWindow.SNIPPETS,
                new javafx.geometry.BoundingBox(970.0, 40.0, 30.0, 30.0));
        var rails = new java.util.EnumMap<DockSide, javafx.geometry.Bounds>(DockSide.class);
        rails.put(DockSide.LEFT, new javafx.geometry.BoundingBox(0.0, 0.0, 30.0, 700.0));
        rails.put(DockSide.RIGHT, new javafx.geometry.BoundingBox(970.0, 0.0, 30.0, 700.0));

        MainWindowController.ToolWindowDropTarget before = MainWindowController.toolWindowDropTarget(
                985.0, 45.0, MainWindowController.ToolWindow.HOST_LIBRARY,
                buttons, rails, settings).orElseThrow();
        assertEquals(DockSide.RIGHT, before.side());
        assertEquals(MainWindowController.ToolWindow.SNIPPETS, before.tool());
        assertTrue(before.before());

        MainWindowController.ToolWindowDropTarget emptyRail = MainWindowController.toolWindowDropTarget(
                985.0, 500.0, MainWindowController.ToolWindow.HOST_LIBRARY,
                buttons, rails, settings).orElseThrow();
        assertEquals(DockSide.RIGHT, emptyRail.side());
        org.junit.jupiter.api.Assertions.assertNull(emptyRail.tool());
        assertFalse(emptyRail.before());
    }

    @Test
    void emptyToolRailHidesUntilAButtonOrDragPreviewNeedsIt() {
        assertFalse(MainWindowController.toolRailShouldBeVisible(false, false));
        assertTrue(MainWindowController.toolRailShouldBeVisible(true, false));
        assertTrue(MainWindowController.toolRailShouldBeVisible(false, true));
    }

    @Test
    void hiddenToolRailKeepsAThirtyPixelEdgeDropTarget() {
        javafx.geometry.Bounds workbench = new javafx.geometry.BoundingBox(100.0, 20.0, 1000.0, 700.0);

        javafx.geometry.Bounds left = MainWindowController.hiddenToolRailDropBounds(
                DockSide.LEFT, workbench);
        javafx.geometry.Bounds right = MainWindowController.hiddenToolRailDropBounds(
                DockSide.RIGHT, workbench);

        assertEquals(100.0, left.getMinX());
        assertEquals(30.0, left.getWidth());
        assertEquals(1070.0, right.getMinX());
        assertEquals(30.0, right.getWidth());
        assertTrue(left.contains(105.0, 100.0));
        assertTrue(right.contains(1095.0, 100.0));
    }

    @Test
    void hidingGlobalToolbarAlsoRemovesItsLayoutSpace() {
        ToolBar toolbar = new ToolBar();
        CheckMenuItem menuItem = new CheckMenuItem();

        MainWindowController.setGlobalToolbarVisible(toolbar, menuItem, false);
        assertFalse(toolbar.isVisible());
        assertFalse(toolbar.isManaged());
        assertFalse(menuItem.isSelected());

        MainWindowController.setGlobalToolbarVisible(toolbar, menuItem, true);
        assertTrue(toolbar.isVisible());
        assertTrue(toolbar.isManaged());
        assertTrue(menuItem.isSelected());
    }

    @Test
    void hidingGlobalStatusBarAlsoRemovesItsLayoutSpace() {
        javafx.scene.layout.HBox statusBar = new javafx.scene.layout.HBox();
        CheckMenuItem menuItem = new CheckMenuItem();

        MainWindowController.setGlobalStatusBarVisible(statusBar, menuItem, false);
        assertFalse(statusBar.isVisible());
        assertFalse(statusBar.isManaged());
        assertFalse(menuItem.isSelected());

        MainWindowController.setGlobalStatusBarVisible(statusBar, menuItem, true);
        assertTrue(statusBar.isVisible());
        assertTrue(statusBar.isManaged());
        assertTrue(menuItem.isSelected());
    }

    @Test
    void reasoningControlsOnlyAppearForKnownResponsesReasoningModels() {
        ModelProviderConfig responses = new ModelProviderConfig("openai", "OpenAI",
                ModelProviderType.OPENAI_RESPONSES, URI.create("https://api.openai.com/v1"), "",
                Optional.empty(), false);
        ModelProviderConfig compatible = new ModelProviderConfig("compatible", "兼容",
                ModelProviderType.OPENAI_COMPATIBLE, URI.create("https://example.com/v1"), "",
                Optional.empty(), false);
        ModelProviderConfig ollama = new ModelProviderConfig("ollama", "Ollama",
                ModelProviderType.OLLAMA, URI.create("http://localhost:11434/v1"), "",
                Optional.empty(), false);
        ModelProviderConfig chatGpt = com.ynpe.fastremote.domain.ai.ChatGptCodexProvider.CONFIG;

        assertEquals(List.of(AiReasoningEffort.NONE, AiReasoningEffort.LOW, AiReasoningEffort.MEDIUM,
                        AiReasoningEffort.HIGH, AiReasoningEffort.XHIGH, AiReasoningEffort.MAX),
                MainWindowController.reasoningEfforts(responses, "gpt-5.6-sol"));
        assertTrue(MainWindowController.reasoningEfforts(responses, "gpt-4.1").isEmpty());
        assertTrue(MainWindowController.reasoningEfforts(compatible, "gpt-5.6-sol").isEmpty());
        assertEquals(List.of(AiReasoningEffort.NONE, AiReasoningEffort.LOW, AiReasoningEffort.MEDIUM,
                        AiReasoningEffort.HIGH, AiReasoningEffort.MAX),
                MainWindowController.reasoningEfforts(ollama, "qwen3.6:27b-mx"));
        assertTrue(MainWindowController.supportsReasoningDisplay(ollama, "qwen3.6:27b-mx"));
        assertTrue(MainWindowController.supportsReasoningDisplay(responses, "gpt-5.6-sol"));
        assertTrue(MainWindowController.supportsReasoningDisplay(chatGpt, "gpt-5.6-sol"));
        assertFalse(MainWindowController.supportsReasoningDisplay(compatible, "gpt-5.6-sol"));
        assertTrue(MainWindowController.supportsPendingAiPrompts(responses));
        assertTrue(MainWindowController.supportsPendingAiPrompts(ollama));
        assertTrue(MainWindowController.supportsPendingAiPrompts(compatible));
        assertTrue(MainWindowController.supportsImageInput(responses, null));
        assertTrue(MainWindowController.supportsImageInput(chatGpt,
                new AiModelCapabilities("gpt-test", List.of(), Optional.empty(),
                        List.of(AiInputModality.TEXT, AiInputModality.IMAGE))));
        assertFalse(MainWindowController.supportsImageInput(chatGpt,
                new AiModelCapabilities("text-only", List.of(), Optional.empty(),
                        List.of(AiInputModality.TEXT))));
        assertFalse(MainWindowController.supportsImageInput(compatible, null));
    }
    @Test
    void createsCompactChatTitlesAndRelativeHistoryAges() {
        assertEquals("检查 服务器 状态", MainWindowController.aiChatTitle("  检查\n服务器   状态  "));
        assertEquals("12345678901234567890123456789012…",
                MainWindowController.aiChatTitle("123456789012345678901234567890123456"));
        Instant now = Instant.parse("2026-08-25T10:00:00Z");
        assertEquals("刚刚", MainWindowController.aiHistoryAge(now.minusSeconds(20), now));
        assertEquals("3小时前", MainWindowController.aiHistoryAge(now.minusSeconds(3 * 3600), now));
        assertEquals("8天前", MainWindowController.aiHistoryAge(now.minusSeconds(8 * 86400), now));
        assertEquals(Optional.of("新的标题"), MainWindowController.aiRenamedTitle("原标题", "  新的标题  "));
        assertTrue(MainWindowController.aiRenamedTitle("原标题", "原标题").isEmpty());
        assertTrue(MainWindowController.aiRenamedTitle("原标题", "   ").isEmpty());
    }
    @Test
    void fallsBackWhenTheModelReturnsAnUnusableChatTitle() {
        String prompt = "检查服务器状态";
        assertEquals("服务器巡检", MainWindowController.usableAiChatTitle("  服务器巡检 ", prompt));
        assertEquals(prompt, MainWindowController.usableAiChatTitle(null, prompt));
        assertEquals(prompt, MainWindowController.usableAiChatTitle("  ", prompt));
        assertEquals(prompt, MainWindowController.usableAiChatTitle("a".repeat(121), prompt));
        assertEquals(prompt, MainWindowController.usableAiChatTitle(
                com.ynpe.fastremote.i18n.Texts.text("ai.chat.newSession"), prompt));
    }

    @Test
    void formatsTheVisibleTranscriptForCopyAllWithoutLosingMarkdown() {
        List<MainWindowController.AiTranscriptEntry> transcript = List.of(
                new MainWindowController.AiTranscriptEntry("你", "运行 `pwd`"),
                new MainWindowController.AiTranscriptEntry("AI", "```sh\npwd\n```"));

        assertEquals("你：\n运行 `pwd`\n\nAI：\n```sh\npwd\n```",
                MainWindowController.aiTranscriptText(transcript));
    }

    @Test
    void promptResizeGrowsUpwardShrinksDownwardAndHonorsBounds() {
        assertEquals(120.0, AiPromptResizeSupport.heightAfterDrag(80.0, 500.0, 460.0, 32.0));
        assertEquals(32.0, AiPromptResizeSupport.heightAfterDrag(80.0, 500.0, 600.0, 32.0));
        assertEquals(400.0, AiPromptResizeSupport.heightAfterDrag(200.0, 500.0, 300.0, 32.0));
    }

    @Test
    void aiTargetResolvesAQuickConnectionToItsUniqueSavedHost() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("target.example.com"), new Port(22), new Username("deploy"),
                AuthenticationType.PASSWORD, Optional.empty());
        SavedHost saved = SavedHost.create("production", Optional.empty(), profile);
        OpenSession open = new OpenSession("production", profile, WorkspaceType.SFTP);

        assertEquals(Optional.of(saved.id()), MainWindowController.resolveAiTarget(open, List.of(saved)));
        assertEquals(saved.id(), open.catalogHostId);
        assertEquals(saved.id().toString(), open.bookmarkKey);
    }

    @Test
    void hostLibraryCopyNameUsesHostOrGroupName() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("target.example.com"), new Port(22), new Username("deploy"),
                AuthenticationType.PASSWORD, Optional.empty());
        SavedHost host = SavedHost.create("生产-数据库", Optional.empty(), profile);
        HostGroup group = HostGroup.root("生产环境");

        assertEquals(Optional.of("生产-数据库"), MainWindowController.catalogItemName(host, null));
        assertEquals(Optional.of("生产环境"), MainWindowController.catalogItemName(null, group));
        assertEquals(Optional.empty(), MainWindowController.catalogItemName(null, null));
    }

    @Test
    void aiTargetDoesNotGuessWhenMultipleSavedHostsMatch() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("shared.example.com"), new Port(22), new Username("deploy"),
                AuthenticationType.PASSWORD, Optional.empty());
        SavedHost first = SavedHost.create("first", Optional.empty(), profile);
        SavedHost second = SavedHost.create("second", Optional.empty(), profile);
        OpenSession open = new OpenSession("", profile, WorkspaceType.TERMINAL);

        assertEquals(Optional.empty(), MainWindowController.resolveAiTarget(open, List.of(first, second)));
    }

    @Test
    void aiTargetRestoresTheSavedHostIdFromAnExistingBookmarkKey() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("bookmark.example.com"), new Port(22), new Username("deploy"),
                AuthenticationType.PASSWORD, Optional.empty());
        SavedHost saved = SavedHost.create("bookmark-target", Optional.empty(), profile);
        OpenSession open = new OpenSession("renamed-runtime-label", profile, WorkspaceType.SFTP);
        open.bookmarkKey = saved.id().toString();

        assertEquals(Optional.of(saved.id()), MainWindowController.resolveAiTarget(open, List.of(saved)));
        assertEquals(saved.id(), open.catalogHostId);
    }

    @Test
    void toolWindowsDeclareTheRequestedInitialFocusTargets() {
        assertEquals(MainWindowController.ToolInitialFocus.HOST,
                MainWindowController.ToolWindow.QUICK_CONNECT.initialFocus);
        assertEquals(MainWindowController.ToolInitialFocus.SESSION_SEARCH,
                MainWindowController.ToolWindow.HOST_LIBRARY.initialFocus);
        assertEquals(MainWindowController.ToolInitialFocus.CREDENTIAL_SEARCH,
                MainWindowController.ToolWindow.CREDENTIAL_VAULT.initialFocus);
        assertEquals(MainWindowController.ToolInitialFocus.SNIPPET_SEARCH,
                MainWindowController.ToolWindow.SNIPPETS.initialFocus);
        assertEquals(MainWindowController.ToolInitialFocus.VARIABLE_SEARCH,
                MainWindowController.ToolWindow.VARIABLE_LIBRARY.initialFocus);
        assertEquals(MainWindowController.ToolInitialFocus.AI_PROMPT,
                MainWindowController.ToolWindow.AI_ASSISTANT.initialFocus);
    }

    @Test
    void libraryGroupExpansionKeysPreserveSessionsAndSeparateOtherLibraries() {
        java.util.UUID sharedValue = java.util.UUID.randomUUID();
        String sessionKey = MainWindowController.hostGroupExpansionKey(new GroupId(sharedValue));
        String credentialKey = MainWindowController.credentialGroupExpansionKey(
                new CredentialGroupId(sharedValue));
        String snippetKey = MainWindowController.snippetGroupExpansionKey(new SnippetGroupId(sharedValue));

        assertEquals(sharedValue.toString(), sessionKey);
        assertNotEquals(sessionKey, credentialKey);
        assertNotEquals(sessionKey, snippetKey);
        assertNotEquals(credentialKey, snippetKey);

        Set<String> collapsed = MainWindowController.updatedCollapsedGroupKeys(
                Set.of(sessionKey), credentialKey, false);
        assertEquals(Set.of(sessionKey, credentialKey), collapsed);
        assertEquals(Set.of(sessionKey), MainWindowController.updatedCollapsedGroupKeys(
                collapsed, credentialKey, true));
    }

    @Test
    void welcomePageWaitsForTheConfiguredStartupTerminal() {
        assertFalse(MainWindowController.welcomeVisible(false, true));
        assertFalse(MainWindowController.welcomeVisible(true, false));
        assertTrue(MainWindowController.welcomeVisible(false, false));
    }

    @Test
    void counterpartLookupUsesOnlyTheSharedPairingIdAndRequestedType() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("pair.example.com"), new Port(22), new Username("user"),
                AuthenticationType.PASSWORD, Optional.empty());
        java.util.UUID pairingId = java.util.UUID.randomUUID();
        OpenSession terminal = new OpenSession(
                "pair", profile, WorkspaceViewModel.WorkspaceType.TERMINAL, pairingId);
        OpenSession sftp = new OpenSession(
                "pair", profile, WorkspaceViewModel.WorkspaceType.SFTP, pairingId);
        sftp.tabs.put(WorkspaceViewModel.WorkspaceType.SFTP, null);
        OpenSession unrelated = new OpenSession(
                "pair", profile, WorkspaceViewModel.WorkspaceType.SFTP, java.util.UUID.randomUUID());
        unrelated.tabs.put(WorkspaceViewModel.WorkspaceType.SFTP, null);

        assertSame(sftp, MainWindowController.findPairedSession(
                terminal, WorkspaceViewModel.WorkspaceType.SFTP, List.of(unrelated, sftp)));
        sftp.closed.set(true);
        org.junit.jupiter.api.Assertions.assertNull(MainWindowController.findPairedSession(
                terminal, WorkspaceViewModel.WorkspaceType.SFTP, List.of(unrelated, sftp)));
    }

    @Test
    void newPairedSftpUsesTheLatestInferredDirectoryFromItsTerminal() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("pair.example.com"), new Port(22), new Username("user"),
                AuthenticationType.PASSWORD, Optional.empty());
        OpenSession terminal = new OpenSession(
                "pair", profile, WorkspaceViewModel.WorkspaceType.TERMINAL);
        terminal.latestInferredWorkingDirectory = "/srv/releases/current";

        assertEquals(Optional.of("/srv/releases/current"), MainWindowController.pairedSftpInitialPath(
                terminal, WorkspaceViewModel.WorkspaceType.SFTP));

        OpenSession sftp = new OpenSession("pair", profile, WorkspaceViewModel.WorkspaceType.SFTP);
        sftp.latestInferredWorkingDirectory = "/ignored";
        assertTrue(MainWindowController.pairedSftpInitialPath(
                sftp, WorkspaceViewModel.WorkspaceType.TERMINAL).isEmpty());
        terminal.latestInferredWorkingDirectory = null;
        assertTrue(MainWindowController.pairedSftpInitialPath(
                terminal, WorkspaceViewModel.WorkspaceType.SFTP).isEmpty());
    }

    @Test
    void initialRemotePathTargetsTheWorkspaceThatWillConsumeIt() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("path.example.com"), new Port(22), new Username("user"),
                AuthenticationType.PASSWORD, Optional.empty());
        OpenSession terminal = new OpenSession(
                "path", profile, WorkspaceViewModel.WorkspaceType.TERMINAL);
        OpenSession sftp = new OpenSession(
                "path", profile, WorkspaceViewModel.WorkspaceType.SFTP);

        MainWindowController.applyInitialRemotePath(terminal, "/srv/releases");
        MainWindowController.applyInitialRemotePath(sftp, "/srv/releases");

        assertEquals("/srv/releases", terminal.pendingTerminalDirectory);
        assertEquals(".", terminal.currentRemotePath);
        assertEquals("/srv/releases", sftp.currentRemotePath);
        assertTrue(sftp.initialRemotePathFallbackPending);
    }

    @Test
    void sftpTerminalActionsUseTheSelectedDirectoryOrTheFilesParent() {
        java.nio.file.Path folder = java.nio.file.Path.of(
                System.getProperty("java.io.tmpdir"), "releases").toAbsolutePath().normalize();
        java.nio.file.Path current = java.nio.file.Path.of(
                System.getProperty("java.io.tmpdir"), "current").toAbsolutePath().normalize();
        LocalFileEntry localFolder = new LocalFileEntry(
                "releases", folder, true, false, 0L, Optional.empty());
        LocalFileEntry localFile = new LocalFileEntry(
                "app.zip", folder.resolve("app.zip"), false, false, 1L, Optional.empty());
        RemoteFileEntry remoteFolder = new RemoteFileEntry(
                "releases", "/srv/releases", true, false, 0L, Optional.empty());
        RemoteFileEntry remoteFile = new RemoteFileEntry(
                "app.zip", "/srv/releases/app.zip", false, false, 1L, Optional.empty());
        LocalFileEntry localBlank = SftpBrowserModel.withLocalNavigation(
                current, List.of()).get(1);
        RemoteFileEntry remoteBlank = SftpBrowserModel.withRemoteNavigation(
                "/srv/current", List.of()).get(1);

        assertEquals(folder, MainWindowController.localTerminalDirectory(
                localFolder, "/ignored").orElseThrow());
        assertEquals(folder, MainWindowController.localTerminalDirectory(
                localFile, "/ignored").orElseThrow());
        assertEquals(current,
                MainWindowController.localTerminalDirectory(null, current.toString()).orElseThrow());
        assertEquals(current,
                MainWindowController.localTerminalDirectory(
                        localBlank, current.toString()).orElseThrow(),
                "a blank table row represents the current list directory, not a file in it");
        assertEquals("/srv/releases",
                MainWindowController.remoteTerminalDirectory(remoteFolder, "/ignored"));
        assertEquals("/srv/releases",
                MainWindowController.remoteTerminalDirectory(remoteFile, "/ignored"));
        assertEquals("/srv/current",
                MainWindowController.remoteTerminalDirectory(null, "/srv/current"));
        assertEquals("/srv/current",
                MainWindowController.remoteTerminalDirectory(remoteBlank, "/srv/current"),
                "a blank table row represents the current list directory, not its parent");
    }

    @Test
    void sftpContextGoToFavoritesKeepToolbarOrderingAndCapTheCombinedListAtTwenty() {
        List<String> global = java.util.stream.IntStream.range(0, 14)
                .mapToObj(index -> "/global/" + index).toList();
        List<String> host = java.util.stream.IntStream.range(0, 25)
                .mapToObj(index -> "/host/" + index).toList();

        MainWindowController.FavoritePathSections favorites =
                MainWindowController.limitedFavoritePathSections(global, host, 20);

        assertEquals(global.subList(0, 10), favorites.global());
        assertEquals(host.subList(0, 10), favorites.host());
        assertEquals(20, favorites.size());
    }

    @Test
    void sftpContextGoToFavoritesUseRemainingCapacityForHostAndHandleEmptyGlobalList() {
        List<String> host = java.util.stream.IntStream.range(0, 25)
                .mapToObj(index -> "/host/" + index).toList();

        MainWindowController.FavoritePathSections mixed =
                MainWindowController.limitedFavoritePathSections(
                        List.of("/global/a", "/global/b"), host, 20);
        MainWindowController.FavoritePathSections hostOnly =
                MainWindowController.limitedFavoritePathSections(List.of(), host, 20);

        assertEquals(2, mixed.global().size());
        assertEquals(18, mixed.host().size());
        assertEquals(20, mixed.size());
        assertTrue(hostOnly.global().isEmpty());
        assertEquals(host.subList(0, 20), hostOnly.host());
    }

    @Test
    void unavailableInferredInitialDirectoryFallsBackOnlyOnce() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("pair.example.com"), new Port(22), new Username("user"),
                AuthenticationType.PASSWORD, Optional.empty());
        OpenSession sftp = new OpenSession("pair", profile, WorkspaceViewModel.WorkspaceType.SFTP);
        sftp.currentRemotePath = "/missing/inferred/path";
        sftp.initialRemotePathFallbackPending = true;

        assertTrue(MainWindowController.consumeInitialRemotePathFallback(sftp, "/missing/inferred/path"));
        assertEquals(".", sftp.currentRemotePath);
        assertFalse(MainWindowController.consumeInitialRemotePathFallback(sftp, "."));
    }

    @Test
    void remoteDirectoryFailuresExposeTheDeepestEnglishDetail() {
        Throwable failure = new CompletionException(new IOException(
                "Remote directory operation failed", new SocketTimeoutException("Read Timeout")));

        assertEquals("Read Timeout", MainWindowController.remoteDirectoryFailureMessage(failure));
        assertEquals("Permission denied", MainWindowController.remoteDirectoryFailureMessage(
                new IOException("Permission denied")));
        assertEquals("Read Timeout server", MainWindowController.remoteDirectoryFailureMessage(
                new SocketTimeoutException("Read Timeout\r\nserver")));
    }

    @Test
    void remoteDirectoryFailuresUseTheLocalizedFallbackOnlyWhenUnknown() {
        assertEquals("远程目录读取失败，请检查路径和访问权限",
                MainWindowController.remoteDirectoryFailureMessage(new IOException("Unknown error")));
        assertEquals("远程目录读取失败，请检查路径和访问权限",
                MainWindowController.remoteDirectoryFailureMessage(new RuntimeException()));
    }

    @Test
    void sshAndSftpFailuresKeepNetworkDetailsInEnglish() {
        Throwable connectionFailure = new ConnectionException(
                ConnectionFailureType.CONNECTION_FAILED,
                "The SSH connection could not be established.",
                new SocketTimeoutException("Read Timeout"));

        assertEquals("Read Timeout", MainWindowController.sshSftpFailureMessage(
                connectionFailure, "SSH connection failed"));
        assertEquals("SocketTimeoutException", MainWindowController.sshSftpFailureMessage(
                new SocketTimeoutException(), "SSH connection failed"));
        assertEquals("SSH connection failed", MainWindowController.sshSftpFailureMessage(
                new IOException("password=must-not-be-logged"), "SSH connection failed"));
    }

    @Test
    void failedSftpTransferRetainsItsNetworkCauseForTheLog() throws InterruptedException {
        SocketTimeoutException timeout = new SocketTimeoutException("Read Timeout");
        RemoteFilesHandle files = (RemoteFilesHandle) Proxy.newProxyInstance(
                RemoteFilesHandle.class.getClassLoader(), new Class<?>[]{RemoteFilesHandle.class},
                (proxy, method, arguments) -> CompletableFuture.failedFuture(timeout));
        SftpTransferJob job = new SftpTransferJob(
                SftpTransferJob.Direction.UPLOAD, "source", "/target", Path.of("source"), "/target",
                files, ignored -> new SftpConflictDecision(
                        com.ynpe.fastremote.domain.transfer.TransferConflictChoice.SKIP, false));

        try (var scheduler = new com.ynpe.fastremote.domain.transfer.FileTransferScheduler(4)) {
            job.start(scheduler).handle((ignored, error) -> null).toCompletableFuture().join();
        }
        CountDownLatch uiDrained = new CountDownLatch(1);
        Platform.runLater(uiDrained::countDown);
        assertTrue(uiDrained.await(5, TimeUnit.SECONDS));

        assertEquals(SftpTransferState.FAILED, job.state());
        assertSame(timeout, job.failure());
    }

    @Test
    void fallbackInvalidatesTheInferredDirectoryTreeSynchronization() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("pair.example.com"), new Port(22), new Username("user"),
                AuthenticationType.PASSWORD, Optional.empty());
        OpenSession sftp = new OpenSession("pair", profile, WorkspaceViewModel.WorkspaceType.SFTP);
        String inferred = "/etc/ssh/invalid-dialog-output";
        sftp.currentRemotePath = inferred;
        sftp.initialRemotePathFallbackPending = true;
        sftp.remoteDirectoryRequest = 1L;

        assertTrue(MainWindowController.remoteTreeSynchronizationStillCurrent(sftp, inferred, 1L));
        assertTrue(MainWindowController.consumeInitialRemotePathFallback(sftp, inferred));
        sftp.remoteDirectoryRequest = 2L;
        assertFalse(MainWindowController.remoteTreeSynchronizationStillCurrent(sftp, inferred, 1L));

        sftp.currentRemotePath = "/root";
        assertTrue(MainWindowController.remoteTreeSynchronizationStillCurrent(sftp, "/root/", 2L));
    }

    @Test
    void remotePropertiesDialogReservesLocalizedPermissionGridWidth() {
        assertEquals(530.0, RemoteFilePropertiesDialog.PREFERRED_CONTENT_WIDTH);
    }

    @Test
    void startupDockHideDoesNotOverwriteTheLoadedPixelWidth() {
        assertEquals(245.0, MainWindowController.dockWidthAfterHiding(false, 245.0, 1380.0));
        assertEquals(312.0, MainWindowController.dockWidthAfterHiding(true, 245.0, 312.0));
    }

    @Test
    void dockRevealLocksItsFirstLayoutToTheSavedPixelWidth() {
        StackPane dock = new StackPane();

        MainWindowController.lockDockWidthForReveal(dock, 245.0);

        assertEquals(245.0, dock.getPrefWidth());
        assertEquals(245.0, dock.getMaxWidth());
    }

    @Test
    void historyRenameKeepsTheMeasuredTitleSlotWidthOnlyWhileEditing() {
        StackPane titleHost = new StackPane();
        titleHost.setMinWidth(0.0);
        titleHost.setPrefWidth(Region.USE_COMPUTED_SIZE);
        titleHost.setMaxWidth(Double.MAX_VALUE);
        titleHost.resize(226.0, 24.0);
        StackPane editor = new StackPane();

        Runnable restore = MainWindowController.holdAiHistoryEditorWidth(titleHost, editor);

        assertEquals(226.0, titleHost.getMinWidth());
        assertEquals(226.0, titleHost.getPrefWidth());
        assertEquals(226.0, titleHost.getMaxWidth());
        assertEquals(226.0, editor.getMinWidth());
        assertEquals(226.0, editor.getPrefWidth());
        assertEquals(226.0, editor.getMaxWidth());

        restore.run();
        assertEquals(0.0, titleHost.getMinWidth());
        assertEquals(Region.USE_COMPUTED_SIZE, titleHost.getPrefWidth());
        assertEquals(Double.MAX_VALUE, titleHost.getMaxWidth());
    }

    @Test
    void embeddedDockNeverKeepsTheFloatingResizeCursor() {
        assertSame(javafx.scene.Cursor.DEFAULT,
                MainWindowController.floatingDockCursor(false, DockSide.RIGHT, 0.0, 245.0));
        assertSame(javafx.scene.Cursor.DEFAULT,
                MainWindowController.floatingDockCursor(false, DockSide.LEFT, 244.0, 245.0));
        assertSame(javafx.scene.Cursor.H_RESIZE,
                MainWindowController.floatingDockCursor(true, DockSide.RIGHT, 0.0, 245.0));
        assertSame(javafx.scene.Cursor.H_RESIZE,
                MainWindowController.floatingDockCursor(true, DockSide.LEFT, 244.0, 245.0));
        assertSame(javafx.scene.Cursor.DEFAULT,
                MainWindowController.floatingDockCursor(true, DockSide.RIGHT, 20.0, 245.0));
    }

    @Test
    void floatingSurfacesWaitForOwnedDialogsToCloseAndMainFocusToReturn() {
        assertTrue(MainWindowController.floatingDockHideBlocked(true, false, false));
        assertTrue(MainWindowController.floatingDockHideBlocked(false, true, false));
        assertTrue(MainWindowController.floatingDockHideBlocked(false, false, true));
        assertFalse(MainWindowController.floatingDockHideBlocked(false, false, false));
        assertTrue(MainWindowController.floatingSurfaceFocusChangeBlocked(true, false));
        assertTrue(MainWindowController.floatingSurfaceFocusChangeBlocked(false, true));
        assertFalse(MainWindowController.floatingSurfaceFocusChangeBlocked(false, false));
    }

    @Test
    void disablingTheFocusedAiSendButtonReturnsFocusToTheAvailablePrompt() {
        assertTrue(MainWindowController.shouldFocusAiPromptBeforeDisablingSend(true, true, false));
        assertFalse(MainWindowController.shouldFocusAiPromptBeforeDisablingSend(false, true, false));
        assertFalse(MainWindowController.shouldFocusAiPromptBeforeDisablingSend(true, false, false));
        assertFalse(MainWindowController.shouldFocusAiPromptBeforeDisablingSend(true, true, true));
    }

    @Test
    void executingSnippetUsesWindowsCrLfButPasteRemainsUnchanged() {
        assertEquals(3, MainWindowController.TRAFFIC_LOG_INTERVAL_SECONDS);
        assertEquals(ServerOverview.SystemFamily.WINDOWS, MainWindowController.snippetSystemFamily(
                true, null, ServerOverview.SystemFamily.WINDOWS));
        assertEquals("echo one\r\necho two\r\n", MainWindowController.snippetPayload(
                "echo one\necho two", true, LocalTerminalHandle.ShellFamily.COMMAND_PROMPT));
        assertEquals("echo one\r\necho two\r\n", MainWindowController.snippetPayload(
                "echo one\r\necho two\r\n", true, LocalTerminalHandle.ShellFamily.COMMAND_PROMPT));
        assertEquals("echo one\recho two\r", MainWindowController.snippetPayload(
                "echo one\necho two", true, LocalTerminalHandle.ShellFamily.POWERSHELL));
        assertEquals("echo one\recho two\r", MainWindowController.snippetPayload(
                "echo one\r\necho two\r\n", true, LocalTerminalHandle.ShellFamily.POWERSHELL));
        assertEquals("echo one\necho two", MainWindowController.snippetPayload(
                "echo one\necho two", false, LocalTerminalHandle.ShellFamily.COMMAND_PROMPT));
    }

    @Test
    void aiCommandUsesAWindowsProbeFromAnyOpenTabForTheSameSavedHost() {
        HostId id = HostId.create();
        ConnectionProfile profile = new ConnectionProfile(
                new Host("windows.example.com"), new Port(22), new Username("user"),
                AuthenticationType.PASSWORD, Optional.empty());
        OpenSession manual = new OpenSession("Windows", profile, WorkspaceType.TERMINAL);
        manual.catalogHostId = id;
        manual.serverOverview = new ServerOverview("Windows Server", ServerOverview.SystemFamily.WINDOWS);
        OpenSession ai = new OpenSession("Windows", profile, WorkspaceType.TERMINAL);
        ai.catalogHostId = id;
        ai.serverOverview = new ServerOverview("Unavailable", ServerOverview.SystemFamily.UNKNOWN);

        ServerOverview.SystemFamily family = MainWindowController.knownSystemFamily(
                id, ai, List.of(ai, manual));

        assertSame(ServerOverview.SystemFamily.WINDOWS, family);
        assertEquals("dir\r\n", MainWindowController.snippetPayload("dir", true,
                MainWindowController.commandShellFamily(
                        family, LocalTerminalHandle.ShellFamily.UNKNOWN)));
        assertEquals(LocalTerminalHandle.ShellFamily.POWERSHELL,
                MainWindowController.commandShellFamily(
                        family, LocalTerminalHandle.ShellFamily.POWERSHELL));
        assertEquals(LocalTerminalHandle.ShellFamily.COMMAND_PROMPT,
                ObservedTerminalCommands.submissionFamily(LocalTerminalHandle.ShellFamily.UNKNOWN, () -> family));
        assertEquals(LocalTerminalHandle.ShellFamily.POWERSHELL,
                ObservedTerminalCommands.submissionFamily(LocalTerminalHandle.ShellFamily.POWERSHELL, () -> family));
        assertEquals(LocalTerminalHandle.ShellFamily.UNKNOWN,
                ObservedTerminalCommands.submissionFamily(LocalTerminalHandle.ShellFamily.UNKNOWN,
                        () -> ServerOverview.SystemFamily.POSIX));
    }

    @Test
    void credentialSearchMatchesNameGroupNameAndUsername() {
        CredentialGroupId groupId = CredentialGroupId.random();
        CredentialGroup group = new CredentialGroup(groupId, "生产环境", 0, GroupColor.BLUE);
        GlobalCredential credential = new GlobalCredential(
                SecretId.create(), "数据库凭证", "root", AuthenticationType.PASSWORD,
                CredentialScope.REUSABLE, Optional.empty(), Optional.of(groupId), 0);

        assertTrue(HostCatalogModel.credentialMatchesSearch(credential, List.of(group), "数据库"));
        assertTrue(HostCatalogModel.credentialMatchesSearch(credential, List.of(group), "生产"));
        assertTrue(HostCatalogModel.credentialMatchesSearch(credential, List.of(group), "root"));
    }

    @Test
    void sessionSearchMatchesParentAndChildGroupNames() {
        GroupId parentId = GroupId.create();
        GroupId childId = GroupId.create();
        HostGroup parent = new HostGroup(parentId, Optional.empty(), "生产环境", 0, GroupColor.BLUE);
        HostGroup child = new HostGroup(childId, Optional.of(parentId), "数据库", 0, GroupColor.GREEN);
        SavedHost host = new SavedHost(
                HostId.create(), Optional.of(childId), "主服务器",
                new ConnectionProfile(new Host("db.example.com"), new Port(22022), new Username("deploy"),
                        AuthenticationType.PASSWORD, Optional.empty()),
                Optional.empty(), Optional.empty(), false, 0,
                Instant.EPOCH, Instant.EPOCH, HostIcon.SERVER);
        HostCatalogSnapshot snapshot = new HostCatalogSnapshot(
                List.of(parent, child), List.of(host), List.of(), List.of(), List.of(), VaultStatus.LOCKED);

        assertEquals(List.of(host), HostCatalogModel.filteredCatalog(snapshot, "生产环境").hosts());
        assertEquals(List.of(host), HostCatalogModel.filteredCatalog(snapshot, "数据库").hosts());
    }

    @Test
    void workspaceCounterpartIconUsesItsVisualWidthInsteadOfTheLucideSourceWidth() {
        StackPane slot = (StackPane) WorkspaceViewModel.workspaceCounterpartIcon(
                WorkspaceViewModel.WorkspaceType.TERMINAL);

        assertEquals(14.0, slot.getMinWidth());
        assertEquals(14.0, slot.getPrefWidth());
        assertEquals(14.0, slot.getMaxWidth());
        assertTrue(slot.getChildren().getFirst() instanceof LucideIconView);
    }

    @Test
    void localTerminalShowsTheSandboxNoticeOnlyInSandboxMode() throws Exception {
        var test = new java.util.concurrent.FutureTask<Void>(() -> {
            String title = WorkspaceViewModel.localTerminalTitle();
            StackPane ordinaryHost = new StackPane();
            var ordinary = LocalTerminalPresentation.createTab(title, ordinaryHost, false, ignored -> { });
            assertEquals("本地终端", ordinary.getText());
            assertSame(ordinaryHost, ordinary.getContent());

            StackPane sandboxedHost = new StackPane();
            var sandboxed = LocalTerminalPresentation.createTab(title, sandboxedHost, true, ignored -> { });
            assertEquals(ordinary.getText(), sandboxed.getText());
            var content = (javafx.scene.layout.BorderPane) sandboxed.getContent();
            assertSame(sandboxedHost, content.getCenter());
            var notice = (javafx.scene.layout.HBox) content.getTop();
            assertEquals("当前运行在沙盒模式下，部分本地终端命令不可用。",
                    ((javafx.scene.control.Label) notice.getChildren().getFirst()).getText());
            var download = (javafx.scene.control.Hyperlink) notice.getChildren().getLast();
            assertEquals("前往 YNPE 下载完整版", download.getText());
            assertEquals("https://download.ynpe.com/community/", download.getTooltip().getText());
            return null;
        });
        Platform.runLater(test);
        test.get(5, TimeUnit.SECONDS);
    }

    @Test
    void treeCellGraphicCacheKeepsTheStyledNodeForSelectionAndExpansionRefreshes() {
        var cache = new LibraryTreeSupport.TreeCellGraphicCache<Object>();
        TreeItem<String> first = new TreeItem<>("第一组");
        TreeItem<String> second = new TreeItem<>("第二组");
        AtomicInteger renders = new AtomicInteger();

        Object firstGraphic = cache.valueFor(first, ignored -> {
            renders.incrementAndGet();
            return new Object();
        });
        assertSame(firstGraphic, cache.valueFor(first, ignored -> {
            renders.incrementAndGet();
            return new Object();
        }));
        cache.valueFor(second, ignored -> {
            renders.incrementAndGet();
            return new Object();
        });

        assertEquals(2, renders.get());
    }

    @Test
    void libraryTreeExpandAndCollapseActionsVisitEveryGroupButKeepHiddenRootVisible() {
        TreeItem<String> root = new TreeItem<>("隐藏根节点");
        TreeItem<String> parent = new TreeItem<>("一级分组");
        TreeItem<String> child = new TreeItem<>("二级分组");
        child.getChildren().add(new TreeItem<>("项目"));
        parent.getChildren().add(child);
        root.getChildren().add(parent);

        LibraryTreeSupport.setAllExpanded(root, false, true);
        assertTrue(root.isExpanded());
        assertTrue(parent.isExpanded());
        assertTrue(child.isExpanded());

        LibraryTreeSupport.setAllExpanded(root, false, false);
        assertTrue(root.isExpanded());
        assertFalse(parent.isExpanded());
        assertFalse(child.isExpanded());
    }

    @Test
    void sftpGraphicCacheKeepsTheNodeWhenRefreshCreatesEquivalentEntries() {
        var cache = new LibraryTreeSupport.KeyedGraphicCache<String, Object>();
        AtomicInteger renders = new AtomicInteger();
        Object first = cache.valueFor("folder", () -> {
            renders.incrementAndGet();
            return new Object();
        });

        assertSame(first, cache.valueFor(new String("folder"), () -> {
            renders.incrementAndGet();
            return new Object();
        }));
        cache.valueFor("pdf", () -> {
            renders.incrementAndGet();
            return new Object();
        });
        assertEquals(2, renders.get());
    }

    @Test
    void restoredWindowBoundsRemainVisibleWhenTheSavedDisplayIsGone() {
        var restored = WorkspaceViewModel.visibleWindowBounds(
                new com.ynpe.fastremote.domain.settings.WindowBounds(-1800.0, 100.0, 1420.0, 860.0),
                List.of(new Rectangle2D(0.0, 25.0, 1280.0, 775.0)), 1080.0, 680.0);

        assertEquals(0.0, restored.x());
        assertEquals(25.0, restored.y());
        assertEquals(1280.0, restored.width());
        assertEquals(775.0, restored.height());
    }

    @Test
    void mainTabCommandsComputeSidesAndMoveWithoutReplacingItems() {
        ArrayList<String> tabs = new ArrayList<>(List.of("一", "二", "三", "四"));

        assertEquals(List.of("一", "二"), WorkspaceViewModel.itemsBefore(tabs, "三"));
        assertEquals(List.of("四"), WorkspaceViewModel.itemsAfter(tabs, "三"));
        assertTrue(WorkspaceViewModel.moveItem(tabs, "三", 0));
        assertEquals(List.of("三", "一", "二", "四"), tabs);
        assertTrue(WorkspaceViewModel.moveItem(tabs, "三", tabs.size() - 1));
        assertEquals(List.of("一", "二", "四", "三"), tabs);
        assertFalse(WorkspaceViewModel.moveItem(tabs, "三", tabs.size() - 1));
    }

    @Test
    void homePageShowsEightMostRecentlyConnectedHostsFromBothHistorySources() {
        GroupId parentId = GroupId.create();
        GroupId childId = GroupId.create();
        HostGroup parent = new HostGroup(parentId, Optional.empty(), "生产", 0, GroupColor.BLUE);
        HostGroup child = new HostGroup(childId, Optional.of(parentId), "数据库", 0, GroupColor.GREEN);
        ConnectionProfile profile = new ConnectionProfile(
                new Host("db.example.com"), new Port(22022), new Username("deploy"),
                AuthenticationType.PASSWORD, Optional.empty());
        List<SavedHost> hosts = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new SavedHost(
                        HostId.create(), Optional.of(childId), "主机-" + index, profile,
                        Optional.empty(), Optional.empty(), false, index,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index), HostIcon.SERVER))
                .toList();
        List<RecentConnection> connections = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new RecentConnection(
                        RecentConnection.Source.SAVED_HOST,
                        hosts.get(index).id().toString(),
                        Optional.of(hosts.get(index).id()),
                        hosts.get(index).name(),
                        profile.host(), profile.port(), profile.username(), profile.authenticationType(),
                        Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index), HostIcon.SERVER))
                .toList();
        HostCatalogSnapshot snapshot = new HostCatalogSnapshot(
                List.of(parent, child), hosts, connections, List.of(), List.of(), VaultStatus.LOCKED);

        List<RecentConnection> recent = HostCatalogModel.homeRecentConnections(snapshot);

        assertEquals(8, recent.size());
        assertEquals("主机-9", recent.getFirst().name());
        assertEquals("主机-2", recent.getLast().name());
        assertTrue(HostCatalogModel.homeRecentConnectionDetail(recent.getFirst(), snapshot)
                .startsWith("生产 / 数据库 · 密码 · deploy@db.example.com:22022 · "));
    }

    @Test
    void transferColumnsKeepTheirDesignedInitialWidthButRemainResizable() {
        TableColumn<Object, String> column = new TableColumn<>("目标");

        MainWindowController.configureTransferColumnWidth(column, 300.0);

        assertEquals(52.0, column.getMinWidth());
        assertEquals(300.0, column.getPrefWidth());
        assertTrue(column.isResizable());
    }

    @Test
    void transferColumnsUseTheScreenshotDefaultWidthsRoundedToFivePixels() {
        assertEquals(List.of(65.0, 175.0, 150.0, 100.0, 100.0, 90.0, 80.0, 85.0, 85.0),
                MainWindowController.defaultTransferColumnWidths());
    }

    @Test
    void remoteAndLocalFileTablesUseWidthsSuitedToTheirMetadata() {
        assertEquals(List.of(195.0, 95.0, 170.0),
                MainWindowController.defaultSftpFileColumnWidths());
        assertEquals(List.of(220.0, 95.0, 125.0, 170.0, 170.0),
                MainWindowController.defaultLocalFileColumnWidths());
        assertEquals(5, MainWindowController.localFileTable().getColumns().size());
    }

    @Test
    void connectingSftpWorkspaceUsesAnEmptyTransferModelBeforeQueueCreation() {
        assertTrue(MainWindowController.transferItems(null).isEmpty());
    }

    @Test
    void recreatedSftpBrowserKeepsItsCurrentRemoteDirectory() {
        assertEquals("/srv/releases", MainWindowController.remoteBrowserInitialPath("/srv/releases"));
        assertEquals(".", MainWindowController.remoteBrowserInitialPath(""));
        assertEquals(".", MainWindowController.remoteBrowserInitialPath(null));
    }

    @Test
    void recreatedSftpBrowserKeepsItsCurrentLocalDirectory() {
        assertEquals("/Users/test/project",
                MainWindowController.localBrowserInitialPath("/Users/test/project", "/Users/test"));
        assertEquals("/Users/test", MainWindowController.localBrowserInitialPath(".", "/Users/test"));
        assertEquals("/Users/test", MainWindowController.localBrowserInitialPath("", "/Users/test"));
    }

    @Test
    void toolbarNewHostStartsAtTheUngroupedRoot() {
        assertTrue(MainWindowController.toolbarNewHostGroup().isEmpty());
    }

    @Test
    void quickConnectionAlwaysSavesToTheUngroupedRoot() {
        assertTrue(MainWindowController.quickHostSaveGroup().isEmpty());
    }

    @Test
    void generatedMasterPasswordHasRequiredLengthAndCharacterGroups() {
        String password = MainWindowController.generateRandomMasterPassword(new SecureRandom());

        assertEquals(12, password.length());
        assertTrue(password.matches("[A-Za-z0-9]{12}"));
        assertTrue(password.chars().anyMatch(Character::isLowerCase));
        assertTrue(password.chars().anyMatch(Character::isUpperCase));
        assertTrue(password.chars().anyMatch(Character::isDigit));
    }

    @Test
    void validatesMasterPasswordBeforeClosingTheDialog() {
        assertEquals(
                "请输入主密码",
                MainWindowController.validateVaultPasswordInput(true, "", "").orElseThrow());
        assertEquals(
                "主密码至少需要 8 个字符",
                MainWindowController.validateVaultPasswordInput(true, "short", "short").orElseThrow());
        assertEquals(
                "两次输入的主密码不一致",
                MainWindowController.validateVaultPasswordInput(
                        true, "long-enough-password", "different-password").orElseThrow());
        assertTrue(MainWindowController.validateVaultPasswordInput(
                true, "long-enough-password", "long-enough-password").isEmpty());
        assertTrue(MainWindowController.validateVaultPasswordInput(
                true, "12345678", "12345678").isEmpty());
        assertEquals(
                "请输入主密码",
                MainWindowController.validateVaultPasswordInput(false, "", "").orElseThrow());
        assertTrue(MainWindowController.validateVaultPasswordInput(false, "unlock", "").isEmpty());
    }

    @Test
    void translatesPortableExportPasswordFailuresIntoChinese() {
        assertEquals("主密码错误或凭证库配置损坏，请重新输入",
                MainWindowController.exportVaultVerificationMessage(new VaultException(
                        VaultFailureType.INVALID_PASSWORD_OR_CORRUPTED, "English internal message")));
        assertEquals("凭证库尚未创建，无法导出凭证",
                MainWindowController.exportVaultVerificationMessage(new VaultException(
                        VaultFailureType.NOT_CREATED, "English internal message")));
        assertEquals("主密码验证失败，请重新输入",
                MainWindowController.exportVaultVerificationMessage(
                        new IllegalStateException("English internal message")));
    }

    @Test
    void selectsCreateUnlockOrNoActionForSessionSaveByVaultState() {
        assertEquals(MainWindowController.HostSaveVaultAction.CREATE,
                MainWindowController.sessionSaveVaultAction(VaultStatus.NOT_CREATED));
        assertEquals(MainWindowController.HostSaveVaultAction.UNLOCK,
                MainWindowController.sessionSaveVaultAction(VaultStatus.LOCKED));
        assertEquals(MainWindowController.HostSaveVaultAction.NONE,
                MainWindowController.sessionSaveVaultAction(VaultStatus.UNLOCKED));
    }

    @Test
    void labelsRememberedUnlockActionsAsLocalSaveOrClear() {
        assertEquals("主密码", MainWindowController.masterPasswordManagerTitle());
        assertEquals("保存到本机", MainWindowController.rememberedUnlockActionLabel(false));
        assertEquals("清除本机保存", MainWindowController.rememberedUnlockActionLabel(true));
    }

    @Test
    void hostLibraryDialogAllowsUserResizing() {
        assertTrue(MainWindowController.allowsHostLibraryResize());
        assertEquals(480.0, MainWindowController.SESSION_MANAGER_INITIAL_WIDTH);
        assertEquals(700.0, MainWindowController.SESSION_MANAGER_INITIAL_HEIGHT);
        assertEquals(360.0, MainWindowController.sessionManagerMinimumWidth());
        assertEquals(360.0, MainWindowController.SESSION_MANAGER_MINIMUM_HEIGHT);
    }

    @Test
    void multiSelectionEditingPrefersTheContextClickedItemOrFallsBackToTheFirstSelection() {
        assertEquals("右键项", MainWindowController.editTarget("右键项", List.of("第一项", "第二项")));
        assertEquals("第一项", MainWindowController.editTarget(null, List.of("第一项", "第二项")));
    }

    @Test
    void bulkDeletionContinuesAfterFailureAndOrdersChildGroupsBeforeParents() {
        GroupId parentId = GroupId.create();
        HostGroup parent = new HostGroup(parentId, Optional.empty(), "一级", 0, GroupColor.BLUE);
        HostGroup child = new HostGroup(
                GroupId.create(), Optional.of(parentId), "二级", 0, GroupColor.GREEN);
        assertEquals(List.of(child, parent), MainWindowController.groupsForBulkDeletion(List.of(parent, child)));

        AtomicInteger attempted = new AtomicInteger();
        List<Throwable> failures = MainWindowController.runDeleteOperations(List.of(
                () -> {
                    attempted.incrementAndGet();
                    return CompletableFuture.failedFuture(new IllegalStateException("first failed"));
                },
                () -> {
                    attempted.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                })).toCompletableFuture().join();

        assertEquals(2, attempted.get());
        assertEquals(1, failures.size());
    }

    @Test
    void quickConnectSeparatesManualAndCredentialLibrarySources() {
        assertEquals(245.0, MainWindowController.dockMinimumWidth(
                MainWindowController.ToolWindow.QUICK_CONNECT));
        assertEquals(245.0, MainWindowController.dockMinimumWidth(
                MainWindowController.ToolWindow.HOST_LIBRARY));
        assertEquals("本次输入", MainWindowController.QuickCredentialSource.MANUAL.toString());
        assertEquals("从凭证库选择", MainWindowController.QuickCredentialSource.LIBRARY.toString());
        assertEquals("保存到主机库", MainWindowController.sessionSaveDialogHeader());
        assertEquals("host-password", MainWindowController.quickSecret(
                com.ynpe.fastremote.domain.connection.AuthenticationType.PASSWORD,
                "host-password", "key-passphrase"));
        assertEquals("key-passphrase", MainWindowController.quickSecret(
                com.ynpe.fastremote.domain.connection.AuthenticationType.PRIVATE_KEY,
                "host-password", "key-passphrase"));
        assertFalse(MainWindowController.manualQuickFieldsDisabled(
                MainWindowController.QuickCredentialSource.MANUAL));
        assertTrue(MainWindowController.manualQuickFieldsDisabled(
                MainWindowController.QuickCredentialSource.LIBRARY));
        assertEquals(LocalizedTextInputContextMenus.isLinux(), MainWindowController.quickConnectDialogResizable());
        assertEquals(600.0, MainWindowController.QUICK_CONNECT_DIALOG_WIDTH);
        assertEquals(95.0, MainWindowController.QUICK_CONNECT_COMPACT_BUTTON_WIDTH);
        assertEquals(675.0, MainWindowController.QUICK_CONNECT_DIALOG_HEIGHT);
        assertEquals(List.of("目标主机", "连接端口", "登录用户", "凭证来源", "认证方式",
                        "主机密码", "证书", "证书口令", "凭证库凭证", "兼容模式", "连接路径"),
                MainWindowController.quickConnectDialogFieldLabels());
        assertEquals(100.0, MainWindowController.quickFormFieldLeft());
        assertEquals(484.0, MainWindowController.quickFormHeight(11));
        assertEquals(540.0, MainWindowController.VAULT_DIALOG_WIDTH);
    }

    @Test
    void quickConnectionCopiesSecretWithoutConsumingTheCurrentInput() {
        String hostPassword = "host-password";
        String privateKeyPassphrase = "key-passphrase";

        char[] connectionCopy = MainWindowController.copyQuickSecret(
                com.ynpe.fastremote.domain.connection.AuthenticationType.PASSWORD,
                hostPassword, privateKeyPassphrase);
        assertArrayEquals(hostPassword.toCharArray(), connectionCopy);

        Arrays.fill(connectionCopy, '\0');
        assertEquals(hostPassword, MainWindowController.quickSecret(
                com.ynpe.fastremote.domain.connection.AuthenticationType.PASSWORD,
                hostPassword, privateKeyPassphrase));
    }

    @Test
    void temporaryWorkspaceCredentialIsSessionScopedCopiedAndCleared() {
        char[] secret = "first-host-password".toCharArray();
        byte[] privateKey = {1, 2, 3};
        var credential = new TemporaryConnectionCredential(secret, privateKey);
        var secondWorkspace = credential.copy();

        Arrays.fill(secret, '\0');
        Arrays.fill(privateKey, (byte) 0);
        assertArrayEquals("first-host-password".toCharArray(), credential.copySecret());
        assertArrayEquals("first-host-password".toCharArray(), secondWorkspace.copySecret());
        assertArrayEquals(new byte[] {1, 2, 3}, secondWorkspace.copyPrivateKey());

        credential.close();
        assertArrayEquals("first-host-password".toCharArray(), secondWorkspace.copySecret());
        secondWorkspace.close();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, secondWorkspace::copySecret);
    }

    @Test
    void resolvesClearBeforeAfterAndIntoDropTargets() {
        assertEquals(LibraryTreeSupport.DropPosition.BEFORE,
                LibraryTreeSupport.dropPosition(2, 20, false));
        assertEquals(LibraryTreeSupport.DropPosition.AFTER,
                LibraryTreeSupport.dropPosition(18, 20, false));
        assertEquals(LibraryTreeSupport.DropPosition.BEFORE,
                LibraryTreeSupport.dropPosition(2, 20, true));
        assertEquals(LibraryTreeSupport.DropPosition.INTO,
                LibraryTreeSupport.dropPosition(10, 20, true));
        assertEquals(LibraryTreeSupport.DropPosition.AFTER,
                LibraryTreeSupport.dropPosition(18, 20, true));
    }

    @Test
    void flatLibrariesOnlyAcceptDropsThatHaveVisibleAndPersistablePositions() {
        TreeItem<String> draggedItem = new TreeItem<>("dragged item");
        TreeItem<String> targetItem = new TreeItem<>("target item");
        TreeItem<String> draggedGroup = new TreeItem<>("dragged group");
        TreeItem<String> targetGroup = new TreeItem<>("target group");
        var items = new HashMap<TreeItem<String>, Object>();
        items.put(draggedItem, new Object());
        items.put(targetItem, new Object());
        var groups = new HashMap<TreeItem<String>, Object>();
        groups.put(draggedGroup, new Object());
        groups.put(targetGroup, new Object());

        assertTrue(LibraryTreeSupport.validFlatLibraryDropTarget(
                targetItem, LibraryTreeSupport.DropPosition.BEFORE, draggedItem, items, groups));
        assertTrue(LibraryTreeSupport.validFlatLibraryDropTarget(
                targetItem, LibraryTreeSupport.DropPosition.AFTER, draggedItem, items, groups));
        assertTrue(LibraryTreeSupport.validFlatLibraryDropTarget(
                targetGroup, LibraryTreeSupport.DropPosition.INTO, draggedItem, items, groups));
        assertTrue(LibraryTreeSupport.validFlatLibraryDropTarget(
                targetGroup, LibraryTreeSupport.DropPosition.AFTER, draggedGroup, items, groups));
        assertFalse(LibraryTreeSupport.validFlatLibraryDropTarget(
                targetGroup, LibraryTreeSupport.DropPosition.BEFORE, draggedItem, items, groups));
        assertFalse(LibraryTreeSupport.validFlatLibraryDropTarget(
                targetGroup, LibraryTreeSupport.DropPosition.INTO, draggedGroup, items, groups));
        TreeItem<String> secondDraggedItem = new TreeItem<>("second dragged item");
        items.put(secondDraggedItem, new Object());
        assertTrue(LibraryTreeSupport.validFlatLibraryDropTarget(
                targetGroup, LibraryTreeSupport.DropPosition.INTO,
                List.of(draggedItem, secondDraggedItem), items, groups));
        assertFalse(LibraryTreeSupport.validFlatLibraryDropTarget(
                targetGroup, LibraryTreeSupport.DropPosition.INTO,
                List.of(draggedItem, draggedGroup), items, groups));
    }

    @Test
    void draggingSelectedLibraryItemKeepsTheWholeSelectionInTreeOrder() {
        TreeItem<String> first = new TreeItem<>("first");
        TreeItem<String> second = new TreeItem<>("second");
        TreeItem<String> unselected = new TreeItem<>("unselected");
        var items = new HashMap<TreeItem<String>, Object>();
        items.put(first, new Object());
        items.put(second, new Object());
        items.put(unselected, new Object());
        var groups = new HashMap<TreeItem<String>, Object>();

        assertEquals(List.of(first, second), LibraryTreeSupport.dragSelection(
                first, List.of(first, second), items, groups));
        assertEquals(List.of(unselected), LibraryTreeSupport.dragSelection(
                unselected, List.of(first, second), items, groups));
    }

    @Test
    void parsesOnlyFourDigitPosixPermissions() {
        assertEquals(0755, RemoteFilePropertiesDialog.parsePermissions("0755"));
        assertEquals(04755, RemoteFilePropertiesDialog.parsePermissions("4755"));
        org.junit.jupiter.api.Assertions.assertNull(RemoteFilePropertiesDialog.parsePermissions("755"));
        org.junit.jupiter.api.Assertions.assertNull(RemoteFilePropertiesDialog.parsePermissions("0888"));
    }

    @Test
    void flatGroupSortingUsesTheWholeTargetCellForBeforeOrAfter() {
        assertEquals(LibraryTreeSupport.DropPosition.BEFORE,
                LibraryTreeSupport.flatLibraryDropPosition(8, 20, true, true));
        assertEquals(LibraryTreeSupport.DropPosition.AFTER,
                LibraryTreeSupport.flatLibraryDropPosition(12, 20, true, true));
        assertEquals(LibraryTreeSupport.DropPosition.INTO,
                LibraryTreeSupport.flatLibraryDropPosition(10, 20, true, false));
    }

    @Test
    void buildsConfigurableTopLevelWorkspaceTitlesAndConnectionDetails() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("db.example.com"), new Port(22022), new Username("deploy"),
                AuthenticationType.PASSWORD, Optional.empty());

        assertEquals("生产数据库", WorkspaceViewModel.workspaceTabTitle(
                "生产数据库", profile, Set.of(HostTabTitlePart.NAME),
                WorkspaceViewModel.WorkspaceType.TERMINAL));
        assertEquals("生产数据库-deploy-db.example.com-22022", WorkspaceViewModel.workspaceTabTitle(
                "生产数据库", profile, Set.of(HostTabTitlePart.values()),
                WorkspaceViewModel.WorkspaceType.TERMINAL));
        assertEquals("db.example.com", WorkspaceViewModel.workspaceTabTitle(
                "", profile, Set.of(), WorkspaceViewModel.WorkspaceType.TERMINAL));
        assertEquals("生产数据库", WorkspaceViewModel.workspaceTabTitle(
                "生产数据库", profile, Set.of(HostTabTitlePart.NAME),
                WorkspaceViewModel.WorkspaceType.SFTP));
        assertEquals("生产数据库-deploy@db.example.com:22022",
                WorkspaceViewModel.connectionDetail("生产数据库", profile));
        assertEquals("deploy@db.example.com:22022",
                WorkspaceViewModel.connectionDetail("", profile));
        assertEquals("生产数据库",
                WorkspaceViewModel.connectionLogName("生产数据库", profile));
        assertEquals("db.example.com",
                WorkspaceViewModel.connectionLogName("", profile));
        assertEquals("SFTP", WorkspaceViewModel.workspaceCounterpartLabel(
                WorkspaceViewModel.WorkspaceType.TERMINAL));
        assertEquals("终端", WorkspaceViewModel.workspaceCounterpartLabel(
                WorkspaceViewModel.WorkspaceType.SFTP));
        assertFalse(WorkspaceViewModel.workspaceActivityInitiallyVisible(
                WorkspaceViewModel.WorkspaceType.TERMINAL, false));
        assertFalse(WorkspaceViewModel.workspaceActivityInitiallyVisible(
                WorkspaceViewModel.WorkspaceType.TERMINAL, true));
        assertTrue(WorkspaceViewModel.workspaceActivityInitiallyVisible(
                WorkspaceViewModel.WorkspaceType.SFTP, false));
        assertEquals(0.87, WorkspaceViewModel.workspaceActivityDivider(
                WorkspaceViewModel.WorkspaceType.TERMINAL, 1_000.0, 130.0));
        assertEquals(0.935, WorkspaceViewModel.workspaceActivityDivider(
                WorkspaceViewModel.WorkspaceType.TERMINAL, 2_000.0, 130.0));
        assertEquals(0.82, WorkspaceViewModel.workspaceActivityDivider(
                WorkspaceViewModel.WorkspaceType.SFTP, 1_000.0, 180.0));
        assertEquals(100.0, WorkspaceViewModel.terminalActivityDefaultHeight());
        assertEquals("workspace-tab-status-connecting", WorkspaceViewModel.workspaceStatusStyle(
                WorkspaceViewModel.ConnectionState.CONNECTING));
        assertEquals("workspace-tab-status-connected", WorkspaceViewModel.workspaceStatusStyle(
                WorkspaceViewModel.ConnectionState.CONNECTED));
        assertEquals("workspace-tab-status-disconnected", WorkspaceViewModel.workspaceStatusStyle(
                WorkspaceViewModel.ConnectionState.DISCONNECTED));
        assertFalse(WorkspaceViewModel.shouldRetainWorkspace(
                false, WorkspaceViewModel.ConnectionState.DISCONNECTED));
        assertFalse(WorkspaceViewModel.shouldRetainWorkspace(
                true, WorkspaceViewModel.ConnectionState.CONNECTED));
        assertTrue(WorkspaceViewModel.shouldRetainWorkspace(
                true, WorkspaceViewModel.ConnectionState.DISCONNECTED));
        assertTrue(WorkspaceViewModel.shouldRetainWorkspace(
                true, WorkspaceViewModel.ConnectionState.CONNECTING));
    }

    @Test
    void floatingActivityHeightCannotBeExpandedByItsStackPaneParent() {
        javafx.scene.layout.Region floating = new javafx.scene.layout.Region();

        MainWindowController.setFixedFloatingHeight(floating, 164.0);

        assertEquals(164.0, floating.getPrefHeight());
        assertEquals(164.0, floating.getMaxHeight());
    }

    @Test
    void failedReconnectReplacesTheBusyBannerWithAFreshEnabledAction() {
        javafx.scene.control.Button busyReconnect = new javafx.scene.control.Button("Reconnect");
        busyReconnect.setDisable(true);
        javafx.scene.layout.HBox busyBanner = new javafx.scene.layout.HBox(busyReconnect);
        busyBanner.getStyleClass().add("workspace-disconnect-banner");
        javafx.scene.layout.Region retainedContent = new javafx.scene.layout.Region();
        javafx.scene.layout.VBox shell = new javafx.scene.layout.VBox(busyBanner, retainedContent);

        javafx.scene.control.Button retry = new javafx.scene.control.Button("Reconnect");
        javafx.scene.layout.HBox failedBanner = new javafx.scene.layout.HBox(retry);
        failedBanner.getStyleClass().add("workspace-disconnect-banner");
        MainWindowController.replaceWorkspaceDisconnectBanner(shell, failedBanner);

        assertSame(failedBanner, shell.getChildren().getFirst());
        assertFalse(retry.isDisabled());
        assertFalse(shell.getChildren().contains(busyBanner));
        assertTrue(shell.getChildren().contains(retainedContent));
    }

    @Test
    void reconnectActionCanCancelAnUnreachableAttemptAndThenRetry() {
        java.util.concurrent.atomic.AtomicInteger reconnects = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger cancellations = new java.util.concurrent.atomic.AtomicInteger();
        javafx.scene.control.Button action = new javafx.scene.control.Button();

        MainWindowController.configureWorkspaceReconnectAction(
                action, true, reconnects::incrementAndGet, cancellations::incrementAndGet);
        assertFalse(action.isDisabled());
        assertEquals(com.ynpe.fastremote.i18n.Texts.text("workspace.disconnect.cancelReconnect"), action.getText());
        action.fire();

        MainWindowController.configureWorkspaceReconnectAction(
                action, false, reconnects::incrementAndGet, cancellations::incrementAndGet);
        assertFalse(action.isDisabled());
        assertEquals(com.ynpe.fastremote.i18n.Texts.text("workspace.disconnect.reconnect"), action.getText());
        action.fire();

        assertEquals(1, cancellations.get());
        assertEquals(1, reconnects.get());
    }

    @Test
    void terminalFocusHandlerLeavesSecondaryClicksToThePopup() {
        AtomicInteger focusRequests = new AtomicInteger();
        StackPane terminalHost = new StackPane();

        MainWindowController.installTerminalFocusHandler(terminalHost, focusRequests::incrementAndGet);
        terminalHost.getOnMouseClicked().handle(terminalClick(MouseButton.SECONDARY));
        assertEquals(0, focusRequests.get(), "a right click must not queue a focus request behind the popup");
        terminalHost.getOnMouseClicked().handle(terminalClick(MouseButton.PRIMARY));

        assertEquals(1, focusRequests.get());
    }

    private static MouseEvent terminalClick(MouseButton button) {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0, button, 1, false, false, false, false,
                false, false, false, false, button == MouseButton.SECONDARY, true, null);
    }

    @Test
    void automationWorkspaceTabsArePrefixedAndRemainInTheBackgroundByDefault() {
        assertEquals("AI-> production",
                MainWindowController.automationWorkspaceTabTitle("production", true));
        assertEquals("production",
                MainWindowController.automationWorkspaceTabTitle("production", false));
        assertFalse(MainWindowController.shouldActivateAutomationWorkspace(true, false));
        assertTrue(MainWindowController.shouldActivateAutomationWorkspace(true, true));
        assertTrue(MainWindowController.shouldActivateAutomationWorkspace(false, false));
    }

    @Test
    void pairedWorkspaceIsInsertedImmediatelyAfterItsSourceTab() {
        javafx.scene.control.Tab first = new javafx.scene.control.Tab("first");
        javafx.scene.control.Tab source = new javafx.scene.control.Tab("source");
        javafx.scene.control.Tab last = new javafx.scene.control.Tab("last");

        assertEquals(2, MainWindowController.insertionIndexAfter(List.of(first, source, last), source));
        assertEquals(3, MainWindowController.insertionIndexAfter(List.of(first, source, last), null));
    }

    @Test
    void customWorkspaceHeaderKeepsTextForTheOverflowMenu() {
        javafx.scene.control.Tab tab = new javafx.scene.control.Tab();
        javafx.scene.control.Label graphic = new javafx.scene.control.Label("visible");

        MainWindowController.configureWorkspaceTabHeader(tab, "AI-> production", graphic);

        assertEquals("AI-> production", tab.getText());
        assertSame(graphic, tab.getGraphic());
    }

    @Test
    void mapsConnectionMilestonesAndFailuresWithoutExposingExceptionDetails() {
        assertEquals("正在建立网络连接", MainWindowController.connectionProgressMessage(
                ConnectionProgress.NETWORK_CONNECTING));
        assertEquals("正在验证主机密钥", MainWindowController.connectionProgressMessage(
                ConnectionProgress.HOST_KEY_VALIDATING));
        assertEquals("正在进行密码认证", MainWindowController.connectionProgressMessage(
                ConnectionProgress.PASSWORD_AUTHENTICATING));
        assertEquals("正在进行证书认证", MainWindowController.connectionProgressMessage(
                ConnectionProgress.PRIVATE_KEY_AUTHENTICATING));
        assertEquals("SSH 认证失败，请检查用户名、凭据及服务器允许的认证方式",
                MainWindowController.connectionFailureMessage(new ConnectionException(
                        ConnectionFailureType.AUTHENTICATION_FAILED, "secret backend detail")));
        assertEquals("证书解码失败，请检查证书格式和口令",
                MainWindowController.connectionFailureMessage(new ConnectionException(
                        ConnectionFailureType.KEY_LOADING_FAILED, "private key parser detail")));
    }

    @Test
    void classifiesSftpEntriesAndMatchesOnlyWholeRemotePathSegments() {
        assertEquals(23, SftpFileIconResolver.Type.values().length);
        assertEquals(SftpFileIconResolver.Type.FOLDER, SftpFileIconResolver.resolve("src", true));
        assertEquals(SftpFileIconResolver.Type.TEXT, SftpFileIconResolver.resolve("notes.TXT", false));
        assertEquals(SftpFileIconResolver.Type.SHELL, SftpFileIconResolver.resolve("deploy.sh", false));
        assertEquals(SftpFileIconResolver.Type.SOURCE, SftpFileIconResolver.resolve("main.c", false));
        assertEquals(SftpFileIconResolver.Type.SOURCE, SftpFileIconResolver.resolve("server.h", false));
        assertEquals(SftpFileIconResolver.Type.PDF, SftpFileIconResolver.resolve("manual.pdf", false));
        assertEquals(SftpFileIconResolver.Type.WEB, SftpFileIconResolver.resolve("index.html", false));
        assertEquals(SftpFileIconResolver.Type.CONFIG, SftpFileIconResolver.resolve("sshd.conf", false));
        assertEquals(SftpFileIconResolver.Type.SHELL, SftpFileIconResolver.resolve(".bashrc", false));
        assertEquals(SftpFileIconResolver.Type.SOURCE, SftpFileIconResolver.resolve("Dockerfile", false));
        assertEquals(SftpFileIconResolver.Type.FILE, SftpFileIconResolver.resolve("no-extension", false));
        assertTrue(SftpBrowserModel.remotePathContains("/var", "/var/log/app"));
        assertTrue(SftpBrowserModel.remotePathContains("/", "/var/log"));
        assertFalse(SftpBrowserModel.remotePathContains("/var/log", "/var/logger"));
        assertEquals("/C:", SftpBrowserModel.normalizeRemotePath("/C:/"));
        assertTrue(SftpBrowserModel.remotePathContains("/C:/", "/C:/Applications/SecureCRT"));
        assertFalse(SftpBrowserModel.remotePathContains("/C:/", "/D:/Data"));
    }

    @Test
    void missingSftpTreePathsAreAddedAsLazyNodesWithoutReloadingTheParent() {
        java.nio.file.Path localParentPath = java.nio.file.Path.of("/srv");
        TreeItem<LocalFileEntry> localParent = new TreeItem<>(new LocalFileEntry(
                "srv", localParentPath, true, false, 0L, java.util.Optional.empty()));
        TreeItem<LocalFileEntry> local = MainWindowController.addLazyLocalTreeChild(
                localParent, java.nio.file.Path.of("/srv/apps/releases"));
        assertEquals(java.nio.file.Path.of("/srv/apps"), local.getValue().path());
        assertEquals(1, local.getChildren().size());
        assertEquals(null, local.getChildren().getFirst().getValue());

        TreeItem<com.ynpe.fastremote.domain.transfer.RemoteFileEntry> remoteParent =
                new TreeItem<>(new com.ynpe.fastremote.domain.transfer.RemoteFileEntry(
                        "root", "/", true, false, 0L, java.util.Optional.empty()));
        TreeItem<com.ynpe.fastremote.domain.transfer.RemoteFileEntry> remote =
                MainWindowController.addLazyRemoteTreeChild(remoteParent, "/var/log/app");
        assertEquals("/var", remote.getValue().path());
        assertEquals(1, remote.getChildren().size());
        assertEquals(null, remote.getChildren().getFirst().getValue());
    }

    @Test
    void formatsLocalAndRemoteFileTimesWithOneSecondPrecision() {
        assertEquals("2026年8月23日 14:05:09", MainWindowController.formatSftpFileTime(
                java.time.Instant.parse("2026-08-23T07:05:09.987Z"),
                java.time.ZoneId.of("Asia/Bangkok")));
    }

    @Test
    void statusMessagesAlwaysIncludeTheirModuleAndOptionalConnectionContext() {
        assertEquals("[全局] 配置已保存", MainWindowController.statusMessage(
                MainWindowController.StatusModule.GLOBAL, "", "配置已保存"));
        assertEquals("[终端][生产机] 正在进行密码认证",
                MainWindowController.statusMessage(MainWindowController.StatusModule.TERMINAL,
                        "生产机", "正在进行密码认证"));
        assertEquals("[SFTP][example.com] SFTP 会话已建立",
                MainWindowController.statusMessage(MainWindowController.StatusModule.SFTP,
                        "example.com", "SFTP 会话已建立"));
    }

    @Test
    void statusAndAiFailuresNeverExposeMultilineHtmlPayloads() {
        String payload = "error creating thread: MCP handshake failed\n<html><head><style>"
                + "data:image/png;base64," + "A".repeat(2_000);

        assertEquals("error creating thread: MCP handshake failed",
                StatusMessagePresentation.summary(payload));
        assertEquals("模型调用失败",
                StatusMessagePresentation.aiFailure(payload, "模型调用失败"));

        String longMessage = "错".repeat(51);
        String summary = StatusMessagePresentation.summary(longMessage);
        assertEquals(50, summary.codePointCount(0, summary.length()));
        assertTrue(summary.endsWith("…"));
        assertFalse(summary.contains("\n"));
    }

    @Test
    void statusSummaryUsesOneHundredDisplayColumnsWithoutTruncatingDiagnosticLog() {
        assertEquals("a".repeat(100), StatusMessagePresentation.summary("a".repeat(100)));
        assertEquals("a".repeat(99) + "…", StatusMessagePresentation.summary("a".repeat(101)));
        assertEquals("中".repeat(50), StatusMessagePresentation.summary("中".repeat(50)));
        assertEquals("中".repeat(49) + "…", StatusMessagePresentation.summary("中".repeat(51)));

        String completeDiagnostic = "详细日志" + "x".repeat(150);
        assertEquals(completeDiagnostic, StatusMessagePresentation.diagnostic(completeDiagnostic));
    }

    @Test
    void summarizesCurrentDirectoryCountsAndRegularFileBytes() {
        SftpBrowserModel.DirectoryStatistics statistics =
                SftpBrowserModel.directoryStatistics(3, 2, 1, 1536);

        assertEquals(5, statistics.total());
        assertEquals("文件 3 · 文件夹 2 · 链接 1 · 总项目 5 · 文件大小 1.5 KiB",
                statistics.summary());
    }

    @Test
    void emptySftpDirectoriesAlwaysExposeParentNavigationRow() {
        Path local = Path.of("build", "empty").toAbsolutePath().normalize();
        List<LocalFileEntry> localRows = SftpBrowserModel.withLocalNavigation(local, List.of());
        assertEquals(3, localRows.size());
        assertEquals("..", localRows.getFirst().name());
        assertEquals(local.getParent(), localRows.getFirst().path());
        assertTrue(SftpBrowserModel.blankFileEntry(localRows.get(1).name()));
        assertTrue(SftpBrowserModel.blankFileEntry(localRows.get(2).name()));
        assertTrue(SftpBrowserModel.blankTableItem(localRows.get(1)));

        List<RemoteFileEntry> remoteRows = SftpBrowserModel.withRemoteNavigation("/root/empty", List.of());
        assertEquals(3, remoteRows.size());
        assertEquals("..", remoteRows.getFirst().name());
        assertEquals("/root", remoteRows.getFirst().path());
        assertTrue(SftpBrowserModel.blankFileEntry(remoteRows.get(1).name()));
        assertTrue(SftpBrowserModel.blankFileEntry(remoteRows.get(2).name()));
        assertTrue(SftpBrowserModel.blankTableItem(remoteRows.get(2)));
    }

    @Test
    void expandingNestedSftpTreeItemReachesTheLoaderRegisteredOnItsRoot() {
        TreeItem<String> root = new TreeItem<>("root");
        TreeItem<String> directory = new TreeItem<>("directory");
        directory.getChildren().add(new TreeItem<>());
        root.getChildren().add(directory);
        ArrayList<TreeItem<String>> loaded = new ArrayList<>();
        SftpBrowserModel.installTreeBranchLoader(root, loaded::add);

        directory.setExpanded(true);

        assertEquals(List.of(directory), loaded);
    }

    @Test
    void sftpTreeReplacesItsPlaceholderForEmptyFileOnlyAndDirectoryListings() {
        Path base = Path.of("build", "tree").toAbsolutePath().normalize();
        LocalFileEntry file = new LocalFileEntry(
                "readme.txt", base.resolve("readme.txt"), false, false, 12L, Optional.empty(), false);
        LocalFileEntry directory = new LocalFileEntry(
                "visible", base.resolve("visible"), true, false, 0L, Optional.empty(), false);
        LocalFileEntry hiddenDirectory = new LocalFileEntry(
                ".hidden", base.resolve(".hidden"), true, false, 0L, Optional.empty(), true);

        assertTrue(SftpBrowserModel.visibleTreeDirectories(
                List.<LocalFileEntry>of(), LocalFileEntry::directory, LocalFileEntry::hidden, false).isEmpty());
        assertTrue(SftpBrowserModel.visibleTreeDirectories(
                List.of(file), LocalFileEntry::directory, LocalFileEntry::hidden, false).isEmpty());
        assertEquals(List.of(directory), SftpBrowserModel.visibleTreeDirectories(
                List.of(file, directory, hiddenDirectory),
                LocalFileEntry::directory, LocalFileEntry::hidden, false));
        assertEquals(List.of(directory, hiddenDirectory), SftpBrowserModel.visibleTreeDirectories(
                List.of(file, directory, hiddenDirectory),
                LocalFileEntry::directory, LocalFileEntry::hidden, true));

        TreeItem<LocalFileEntry> treeItem = new TreeItem<>(directory);
        treeItem.getChildren().add(new TreeItem<>());
        SftpBrowserModel.replaceTreeChildren(treeItem, List.of());
        assertTrue(treeItem.getChildren().isEmpty());

        TreeItem<LocalFileEntry> child = new TreeItem<>(hiddenDirectory);
        SftpBrowserModel.replaceTreeChildren(treeItem, List.of(child));
        assertEquals(List.of(child), treeItem.getChildren());
    }

    @Test
    void successfulDirectoryDeletionPrunesStaleTreeNodesBeforeReload() {
        Path removedPath = Path.of("build", "removed").toAbsolutePath().normalize();
        TreeItem<LocalFileEntry> localRoot = new TreeItem<>();
        localRoot.getChildren().add(new TreeItem<>(new LocalFileEntry(
                "removed", removedPath, true, false, 0L, Optional.empty(), false)));

        SftpBrowserModel.pruneLocalTreeEntries(localRoot, Set.of(removedPath));

        assertTrue(localRoot.getChildren().isEmpty());

        TreeItem<RemoteFileEntry> remoteRoot = new TreeItem<>(new RemoteFileEntry(
                "/", "/", true, false, 0L, Optional.empty()));
        remoteRoot.getChildren().add(new TreeItem<>(new RemoteFileEntry(
                "removed", "/root/removed/", true, false, 0L, Optional.empty())));

        SftpBrowserModel.pruneRemoteTreeEntries(remoteRoot, Set.of("/root/removed"));

        assertTrue(remoteRoot.getChildren().isEmpty());
    }

    @Test
    void toolbarShowsOnlyInactiveAndTrialActivationStates() {
        var inactive = new com.ynpe.fastremote.domain.license.LicenseSnapshot(
                com.ynpe.fastremote.domain.license.LicenseStatus.NEEDS_ACTIVATION, "",
                Optional.empty(), Optional.empty(), Optional.empty(), Set.of(), "");
        var trial = new com.ynpe.fastremote.domain.license.LicenseSnapshot(
                com.ynpe.fastremote.domain.license.LicenseStatus.TRIAL_ACTIVE, "trial",
                Optional.empty(), Optional.empty(), Optional.empty(), Set.of(), "trial-lease");
        var licensed = new com.ynpe.fastremote.domain.license.LicenseSnapshot(
                com.ynpe.fastremote.domain.license.LicenseStatus.LICENSED_ONLINE, "online",
                Optional.empty(), Optional.empty(), Optional.empty(), Set.of(), "license-lease");

        assertEquals(MainWindowController.LicenseActivationStatus.INACTIVE,
                MainWindowController.licenseActivationStatus(inactive, null));
        assertEquals(MainWindowController.LicenseActivationStatus.INACTIVE,
                MainWindowController.licenseActivationStatus(null, new IllegalStateException("offline")));
        assertEquals(MainWindowController.LicenseActivationStatus.TRIAL,
                MainWindowController.licenseActivationStatus(trial, null));
        assertEquals(MainWindowController.LicenseActivationStatus.HIDDEN,
                MainWindowController.licenseActivationStatus(licensed, null));
    }

    @Test
    void vaultToolbarUsesTheActivationKeyOnlyWhileUnlocked() {
        assertEquals(LucideIconView.Icon.LOCK_KEYHOLE,
                MainWindowController.vaultToolbarIcon(VaultStatus.NOT_CREATED));
        assertEquals(LucideIconView.Icon.LOCK_KEYHOLE,
                MainWindowController.vaultToolbarIcon(VaultStatus.LOCKED));
        assertEquals(LucideIconView.Icon.KEY_ROUND,
                MainWindowController.vaultToolbarIcon(VaultStatus.UNLOCKED));
    }

    @Test
    void exitConfirmationCountsWorkingTabsButExcludesAiAndLogs() {
        javafx.scene.control.Tab terminal = new javafx.scene.control.Tab("Terminal");
        javafx.scene.control.Tab sftp = new javafx.scene.control.Tab("SFTP");
        javafx.scene.control.Tab ai = new javafx.scene.control.Tab("AI");
        javafx.scene.control.Tab log = new javafx.scene.control.Tab("Log");

        int count = MainWindowController.countExitRelevantTabs(
                List.of(terminal, sftp, ai, log), tab -> tab == ai || tab == log);

        assertEquals(2, count);
        assertTrue(MainWindowController.shouldConfirmExit(true, count));
        assertFalse(MainWindowController.shouldConfirmExit(false, count));
        assertFalse(MainWindowController.shouldConfirmExit(true, 1));
    }

    @Test
    void workspaceCloseConfirmationCoversMultiplePanesAndUnfinishedTransfers() {
        assertFalse(MainWindowController.shouldConfirmWorkspaceClose(1, false));
        assertTrue(MainWindowController.shouldConfirmWorkspaceClose(2, false));
        assertTrue(MainWindowController.shouldConfirmWorkspaceClose(1, true));
        assertTrue(MainWindowController.shouldConfirmWorkspaceClose(3, true));
    }
}
