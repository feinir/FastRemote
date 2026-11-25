// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.Username;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DisconnectedWorkspaceReconnectTest {
    @BeforeAll static void startFx() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        try { Platform.startup(() -> ready.complete(null)); }
        catch (IllegalStateException started) { ready.complete(null); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @Test void clickingTerminalOrSftpTitleThenEnterReconnectsTheSelectedSession() throws Exception {
        onFx(() -> {
            var terminal = session(WorkspaceViewModel.WorkspaceType.TERMINAL);
            var sftp = session(WorkspaceViewModel.WorkspaceType.SFTP);
            var terminalTab = new Tab("terminal", new StackPane());
            var sftpContent = new StackPane();
            sftpContent.setDisable(true); // retained SFTP content cannot receive focus
            var sftpTab = new Tab("sftp", sftpContent);
            var connectedTab = new Tab("connected", new TextField());
            var tabs = new TabPane(connectedTab, terminalTab, sftpTab);
            var scene = new Scene(tabs, 640, 320);
            tabs.applyCss(); tabs.resize(640, 320); tabs.layout();
            var reconnected = new AtomicReference<OpenSession>();
            var calls = new AtomicInteger();
            java.util.function.Supplier<OpenSession> selected = () ->
                    tabs.getSelectionModel().getSelectedItem() == terminalTab ? terminal
                    : tabs.getSelectionModel().getSelectedItem() == sftpTab ? sftp : null;
            DisconnectedWorkspaceReconnect.installTabHeader(tabs,
                    () -> MainWindowController.canReconnectDisconnectedWorkspace(selected.get()),
                    () -> {
                        reconnected.set(selected.get()); calls.incrementAndGet();
                        selected.get().state = WorkspaceViewModel.ConnectionState.CONNECTING;
                    });
            for (Tab tab : new Tab[]{terminalTab, sftpTab}) {
                tabs.getSelectionModel().select(connectedTab);
                Node header = tabs.lookupAll(".tab").stream().filter(node ->
                        node.lookup(".tab-label") instanceof Label label && label.getText().equals(tab.getText()))
                        .findFirst().orElseThrow();
                Event.fireEvent(header, new MouseEvent(MouseEvent.MOUSE_PRESSED, 2, 2, 2, 2,
                        MouseButton.PRIMARY, 1, false, false, false, false, true, false, false,
                        false, false, true, new PickResult(header, 2, 2)));
                assertSame(tab, tabs.getSelectionModel().getSelectedItem());
                assertSame(tabs, scene.getFocusOwner(), "clicking the real tab skin focuses TabPane");
                scene.getFocusOwner().fireEvent(enter(false, false, false, false));
                assertSame(tab == terminalTab ? terminal : sftp, reconnected.get());
                int count = calls.get();
                tabs.fireEvent(enter(false, false, false, false));
                assertEquals(count, calls.get(), "holding Enter must not reconnect again while connecting");
            }
            assertEquals(2, calls.get());
        });
    }

    @Test void headerShortcutDoesNotCaptureContentButtonsFieldsOrNestedTabs() throws Exception {
        onFx(() -> {
            var calls = new AtomicInteger();
            var delivered = new AtomicInteger();
            var close = new Button("close");
            var input = new TextField();
            var nestedTabs = new TabPane(new Tab("editor", new TextField()));
            var content = new VBox(close, input, nestedTabs);
            var tabs = new TabPane(new Tab("disconnected", content));
            var sidebar = new TextField();
            var root = new VBox(tabs, sidebar);
            new Scene(root, 640, 320);
            root.applyCss(); root.resize(640, 320); root.layout();
            DisconnectedWorkspaceReconnect.installTabHeader(tabs, () -> true, calls::incrementAndGet);
            for (Node target : new Node[]{close, input, nestedTabs, nestedTabs.lookup(".tab-header-area"), sidebar}) {
                target.addEventFilter(KeyEvent.KEY_PRESSED, e -> delivered.incrementAndGet());
                target.fireEvent(enter(false, false, false, false));
            }
            assertEquals(0, calls.get());
            assertEquals(6, delivered.get()); // nested header also travels through its TabPane
            tabs.fireEvent(enter(true, false, false, false));
            tabs.fireEvent(enter(false, true, false, false));
            tabs.fireEvent(enter(false, false, true, false));
            tabs.fireEvent(enter(false, false, false, true));
            assertEquals(0, calls.get());
            tabs.getChildrenUnmodifiable().stream()
                    .filter(node -> node.getStyleClass().contains("tab-header-area"))
                    .findFirst().orElseThrow().fireEvent(enter(false, false, false, false));
            assertEquals(1, calls.get());
        });
    }

    @Test void onlyRetainedDisconnectedSessionsAreEligible() throws Exception {
        onFx(() -> {
            assertFalse(MainWindowController.canReconnectDisconnectedWorkspace(null));
            for (var type : WorkspaceViewModel.WorkspaceType.values()) {
                var session = session(type);
                assertTrue(MainWindowController.canReconnectDisconnectedWorkspace(session));
                for (var state : WorkspaceViewModel.ConnectionState.values()) {
                    session.state = state;
                    assertEquals(state == WorkspaceViewModel.ConnectionState.DISCONNECTED,
                            MainWindowController.canReconnectDisconnectedWorkspace(session));
                }
                session.state = WorkspaceViewModel.ConnectionState.DISCONNECTED;
                session.connectedOnce = false;
                assertFalse(MainWindowController.canReconnectDisconnectedWorkspace(session));
                session.connectedOnce = true;
                session.closed.set(true);
                assertFalse(MainWindowController.canReconnectDisconnectedWorkspace(session));
            }
        });
    }

    private static OpenSession session(WorkspaceViewModel.WorkspaceType type) {
        var session = new OpenSession("host", new ConnectionProfile(new Host("example.test"),
                new Port(22), new Username("user"), AuthenticationType.PASSWORD, Optional.empty()), type);
        session.connectedOnce = true;
        session.state = WorkspaceViewModel.ConnectionState.DISCONNECTED;
        return session;
    }

    private static void onFx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    @Test void reconnectsOnlyWhileDisconnectedAndPassesEnterThroughAfterStateChanges() {
        var state = new AtomicReference<>(WorkspaceViewModel.ConnectionState.CONNECTED);
        var calls = new AtomicInteger();
        var terminalKeys = new AtomicInteger();
        var terminal = new StackPane();
        var area = new StackPane(terminal);
        terminal.addEventHandler(KeyEvent.KEY_PRESSED, event -> terminalKeys.incrementAndGet());
        DisconnectedWorkspaceReconnect.install(area,
                () -> state.get() == WorkspaceViewModel.ConnectionState.DISCONNECTED,
                () -> { calls.incrementAndGet(); state.set(WorkspaceViewModel.ConnectionState.CONNECTING); });

        terminal.fireEvent(enter(false, false, false, false));
        assertEquals(1, terminalKeys.get());
        assertEquals(0, calls.get());
        state.set(WorkspaceViewModel.ConnectionState.DISCONNECTED);
        terminal.fireEvent(enter(false, false, false, false));
        assertEquals(1, calls.get());
        assertEquals(1, terminalKeys.get(), "Reconnect Enter must not reach terminal input");
        terminal.fireEvent(enter(false, false, false, false));
        assertEquals(1, calls.get(), "Connecting must not trigger reconnect or cancel");
        assertEquals(2, terminalKeys.get());
        state.set(WorkspaceViewModel.ConnectionState.CONNECTED);
        terminal.fireEvent(enter(false, false, false, false));
        assertEquals(3, terminalKeys.get());
        state.set(WorkspaceViewModel.ConnectionState.DISCONNECTED);
        terminal.fireEvent(enter(false, false, false, false));
        assertEquals(2, calls.get(), "A later disconnect must allow another reconnect");
    }

    @Test void ignoresModifiedEnterOtherKeysAndInputOutsideTheTerminal() {
        var calls = new AtomicInteger();
        var delivered = new AtomicInteger();
        var terminal = new StackPane();
        var area = new StackPane(terminal);
        var sibling = new StackPane();
        var workspace = new StackPane(area, sibling);
        workspace.addEventHandler(KeyEvent.KEY_PRESSED, event -> delivered.incrementAndGet());
        DisconnectedWorkspaceReconnect.install(area, () -> true, calls::incrementAndGet);
        terminal.fireEvent(enter(true, false, false, false));
        terminal.fireEvent(enter(false, true, false, false));
        terminal.fireEvent(enter(false, false, true, false));
        terminal.fireEvent(enter(false, false, false, true));
        terminal.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.A, false, false, false, false));
        sibling.fireEvent(enter(false, false, false, false));
        assertEquals(0, calls.get());
        assertEquals(6, delivered.get());
    }

    private static KeyEvent enter(boolean shift, boolean control, boolean alt, boolean meta) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, shift, control, alt, meta);
    }
}
