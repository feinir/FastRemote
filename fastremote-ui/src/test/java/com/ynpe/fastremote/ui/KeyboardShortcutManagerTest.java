// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.KeyboardShortcutSettings;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.event.Event;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.InputMethodHighlight;
import javafx.scene.input.InputMethodTextRun;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyboardShortcutManagerTest {
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
    void defaultsResolveEveryActionWithoutCollisions() {
        var resolved = KeyboardShortcutManager.resolve(KeyboardShortcutSettings.defaults());
        assertEquals(ShortcutAction.values().length, resolved.size());
        for (ShortcutAction.Scope scope : ShortcutAction.Scope.values()) {
            var combinations = resolved.entrySet().stream()
                    .filter(entry -> entry.getKey().scope() == scope).map(Map.Entry::getValue).toList();
            assertEquals(combinations.size(), new java.util.HashSet<>(combinations).size());
        }
        assertTrue(KeyboardShortcutManager.conflict(KeyboardShortcutSettings.defaults()).isEmpty());
    }

    @Test
    void toolWindowDefaultsUseMnemonicKeysInsteadOfSystemScreenshotShortcuts() {
        assertEquals("Shortcut+Shift+P", ShortcutAction.OPEN_COMMAND_PALETTE.defaultShortcut());
        assertEquals("Shortcut+Shift+O", ShortcutAction.OPEN_SNIPPET_LIBRARY.defaultShortcut());
        assertEquals(ShortcutAction.Scope.GLOBAL, ShortcutAction.OPEN_SNIPPET_LIBRARY.scope());
        assertEquals("Shortcut+Alt+B", ShortcutAction.TOGGLE_HOST_LIBRARY.defaultShortcut());
        assertEquals("Shortcut+Alt+Q", ShortcutAction.TOGGLE_QUICK_CONNECT.defaultShortcut());
        assertEquals("Shortcut+Alt+C", ShortcutAction.TOGGLE_SNIPPETS.defaultShortcut());
        assertEquals("Shortcut+Alt+V", ShortcutAction.TOGGLE_CREDENTIALS.defaultShortcut());
        assertEquals("Shortcut+Shift+I", ShortcutAction.TOGGLE_AI_ASSISTANT.defaultShortcut());
        assertEquals("Shortcut+Alt+G", ShortcutAction.TOGGLE_DIAGNOSTIC_LOG.defaultShortcut());
        assertEquals("Enter", ShortcutAction.OPEN_SELECTED_LIBRARY_ITEM.defaultShortcut());
        assertEquals("Shortcut+E", ShortcutAction.EDIT_SELECTED_LIBRARY_ITEM.defaultShortcut());
        assertEquals("Shortcut+D", ShortcutAction.COPY_SELECTED_LIBRARY_ITEM.defaultShortcut());
        assertEquals("Shortcut+E", ShortcutAction.SFTP_EDIT.defaultShortcut());
        assertEquals(ShortcutAction.deleteDefault(System.getProperty("os.name", "")),
                ShortcutAction.DELETE_SELECTED_LIBRARY_ITEM.defaultShortcut());
    }

    @Test
    void aiAndLogGlobalShortcutsDoNotOverlapEditorOrTerminalDefaults() {
        var panelShortcuts = java.util.Set.of(
                ShortcutAction.TOGGLE_AI_ASSISTANT.defaultCombination(),
                ShortcutAction.TOGGLE_DIAGNOSTIC_LOG.defaultCombination());
        var editorShortcuts = java.util.Set.of(
                javafx.scene.input.KeyCombination.valueOf("Shortcut+X"),
                javafx.scene.input.KeyCombination.valueOf("Shortcut+C"),
                javafx.scene.input.KeyCombination.valueOf("Shortcut+V"),
                javafx.scene.input.KeyCombination.valueOf("Shortcut+F"),
                javafx.scene.input.KeyCombination.valueOf("Shortcut+R"),
                javafx.scene.input.KeyCombination.valueOf("Shortcut+A"));
        var terminalShortcuts = java.util.Set.of(
                ShortcutAction.TERMINAL_COPY.defaultCombination(),
                ShortcutAction.TERMINAL_PASTE.defaultCombination(),
                ShortcutAction.TERMINAL_FIND.defaultCombination());

        assertTrue(java.util.Collections.disjoint(panelShortcuts, editorShortcuts));
        assertTrue(java.util.Collections.disjoint(panelShortcuts, terminalShortcuts));
    }

    @Test
    void renameUsesTheNativePlatformConvention() {
        assertEquals("Enter", ShortcutAction.renameDefault("Mac OS X"));
        assertEquals("F2", ShortcutAction.renameDefault("Windows 11"));
        assertEquals("F2", ShortcutAction.renameDefault("Linux"));
        assertEquals(ShortcutAction.renameDefault(System.getProperty("os.name", "")),
                ShortcutAction.SFTP_RENAME.defaultShortcut());
        assertEquals("Shortcut+Backspace", ShortcutAction.deleteDefault("Mac OS X"));
        assertEquals("Delete", ShortcutAction.deleteDefault("Windows 11"));
        assertEquals("Delete", ShortcutAction.deleteDefault("Linux"));
    }

    @Test
    void libraryShortcutsApplyToEveryCatalogPageButNotOtherToolPages() {
        for (String id : java.util.List.of(
                "hostLibraryPage", "snippetsPage", "variableLibraryPage", "credentialVaultPage")) {
            VBox page = new VBox();
            page.setId(id);
            Pane child = new Pane();
            page.getChildren().add(child);
            assertTrue(KeyboardShortcutManager.insideLibrary(child), id);
        }
        VBox quickConnect = new VBox();
        quickConnect.setId("quickConnectPage");
        Pane child = new Pane();
        quickConnect.getChildren().add(child);
        assertFalse(KeyboardShortcutManager.insideLibrary(child));
    }

    @Test
    void reportsOverlappingUserOverrides() {
        var settings = new KeyboardShortcutSettings(Map.of(
                ShortcutAction.OPEN_LOCAL_TERMINAL.id(), "Shortcut+Shift+L"));
        String conflict = KeyboardShortcutManager.conflict(settings);
        assertFalse(conflict.isEmpty());
        assertTrue(conflict.contains("快速连接"));
    }

    @Test
    void quickConnectUsesPlatformShortcutShiftLAndPreservesUserOverrides() {
        assertEquals("Shortcut+Shift+L", ShortcutAction.OPEN_QUICK_CONNECT.defaultShortcut());
        assertEquals(ShortcutAction.Scope.GLOBAL, ShortcutAction.OPEN_QUICK_CONNECT.scope());
        boolean mac = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
        KeyEvent pressed = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.L,
                true, !mac, false, mac);
        var defaults = KeyboardShortcutManager.resolve(KeyboardShortcutSettings.defaults());
        assertEquals(java.util.Optional.of(ShortcutAction.OPEN_QUICK_CONNECT),
                KeyboardShortcutManager.matchingGlobalAction(defaults, pressed));
        KeyEvent libraryPressed = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.O,
                true, !mac, false, mac);
        assertEquals(java.util.Optional.of(ShortcutAction.OPEN_SNIPPET_LIBRARY),
                KeyboardShortcutManager.matchingGlobalAction(defaults, libraryPressed));

        var customized = KeyboardShortcutManager.resolve(new KeyboardShortcutSettings(Map.of(
                ShortcutAction.OPEN_QUICK_CONNECT.id(), "Shortcut+Shift+Q")));
        assertEquals(javafx.scene.input.KeyCombination.valueOf("Shortcut+Shift+Q"),
                customized.get(ShortcutAction.OPEN_QUICK_CONNECT));
        assertTrue(KeyboardShortcutManager.matchingGlobalAction(customized, pressed).isEmpty());
    }

    @Test
    void hostLibraryAndHiddenFilesUseDistinctHShortcutsAndKeepOverrides() {
        assertEquals("Shortcut+Shift+H", ShortcutAction.OPEN_HOST_LIBRARY.defaultShortcut());
        assertEquals("Alt+Shift+H", ShortcutAction.SFTP_TOGGLE_HIDDEN.defaultShortcut());
        assertEquals(ShortcutAction.Scope.GLOBAL, ShortcutAction.OPEN_HOST_LIBRARY.scope());
        assertEquals(ShortcutAction.Scope.SFTP_CONTEXT, ShortcutAction.SFTP_TOGGLE_HIDDEN.scope());
        boolean mac = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
        var host = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.H, true, !mac, false, mac);
        var hidden = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.H, true, false, true, false);
        var defaults = KeyboardShortcutManager.resolve(KeyboardShortcutSettings.defaults());
        assertEquals(java.util.Optional.of(ShortcutAction.OPEN_HOST_LIBRARY),
                KeyboardShortcutManager.matchingGlobalAction(defaults, host));
        assertTrue(KeyboardShortcutManager.matchingGlobalAction(defaults, hidden).isEmpty());
        assertTrue(defaults.get(ShortcutAction.SFTP_TOGGLE_HIDDEN).match(hidden));
        var customized = KeyboardShortcutManager.resolve(new KeyboardShortcutSettings(Map.of(
                ShortcutAction.OPEN_HOST_LIBRARY.id(), "Shortcut+Shift+M",
                ShortcutAction.SFTP_TOGGLE_HIDDEN.id(), "Alt+Shift+J")));
        assertEquals(javafx.scene.input.KeyCombination.valueOf("Shortcut+Shift+M"),
                customized.get(ShortcutAction.OPEN_HOST_LIBRARY));
        assertEquals(javafx.scene.input.KeyCombination.valueOf("Alt+Shift+J"),
                customized.get(ShortcutAction.SFTP_TOGGLE_HIDDEN));
    }

    @Test
    void invalidPersistedValueFallsBackDuringRuntimeResolution() {
        var settings = new KeyboardShortcutSettings(Map.of(
                ShortcutAction.OPEN_SETTINGS.id(), "Ctrl+Shift"));
        assertEquals(ShortcutAction.OPEN_SETTINGS.defaultCombination(),
                KeyboardShortcutManager.resolve(settings).get(ShortcutAction.OPEN_SETTINGS));
    }

    @Test
    void applicationShortcutCanBeResolvedBeforeFocusContextIsApplied() {
        var resolved = KeyboardShortcutManager.resolve(KeyboardShortcutSettings.defaults());
        boolean mac = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
        for (var expected : java.util.List.of(
                ShortcutAction.TOGGLE_HOST_LIBRARY,
                ShortcutAction.TOGGLE_SNIPPETS,
                ShortcutAction.TOGGLE_CREDENTIALS)) {
            KeyCode key = KeyCode.valueOf(expected.defaultShortcut().substring(
                    expected.defaultShortcut().length() - 1));
            KeyEvent pressed = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key,
                    false, !mac, true, mac);
            assertEquals(java.util.Optional.of(expected),
                    KeyboardShortcutManager.matchingGlobalAction(resolved, pressed));
        }
    }

    @Test
    void terminalSurfaceMarkerCoversAllDescendants() {
        VBox terminal = new VBox();
        terminal.getStyleClass().add("fastremote-terminal-surface");
        Pane canvas = new Pane();
        terminal.getChildren().add(canvas);

        assertTrue(KeyboardShortcutManager.insideTerminal(canvas));
        assertFalse(KeyboardShortcutManager.insideTerminal(new Pane()));
    }

    @Test
    void focusedTerminalConsumesControlTabBeforeTheApplicationTabShortcut() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                VBox terminal = new VBox();
                terminal.getStyleClass().add("fastremote-terminal-surface");
                Pane canvas = new Pane();
                terminal.getChildren().add(canvas);
                Scene scene = new Scene(terminal);
                AtomicInteger dispatched = new AtomicInteger();
                AtomicInteger terminalPresses = new AtomicInteger();
                KeyboardShortcutManager manager = new KeyboardShortcutManager(
                        scene, ignored -> dispatched.incrementAndGet());
                manager.apply(KeyboardShortcutSettings.defaults());
                canvas.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    terminalPresses.incrementAndGet();
                    event.consume();
                });

                Event.fireEvent(canvas, new KeyEvent(KeyEvent.KEY_PRESSED, "\t", "\t", KeyCode.TAB,
                        false, true, false, false));

                assertEquals(1, terminalPresses.get());
                assertEquals(0, dispatched.get());
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
    void consumedApplicationShortcutAlsoConsumesItsFollowingTypedCharacter() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Pane terminal = new Pane();
                Scene scene = new Scene(terminal);
                AtomicInteger dispatched = new AtomicInteger();
                AtomicInteger terminalCharacters = new AtomicInteger();
                KeyboardShortcutManager manager = new KeyboardShortcutManager(scene, ignored ->
                        dispatched.incrementAndGet());
                manager.apply(KeyboardShortcutSettings.defaults());
                terminal.addEventHandler(KeyEvent.KEY_TYPED, ignored -> terminalCharacters.incrementAndGet());

                boolean mac = System.getProperty("os.name", "")
                        .toLowerCase(java.util.Locale.ROOT).contains("mac");
                KeyEvent pressed = new KeyEvent(KeyEvent.KEY_PRESSED, "P", "P", KeyCode.P,
                        true, !mac, false, mac);
                Event.fireEvent(terminal, pressed);
                KeyEvent typed = new KeyEvent(KeyEvent.KEY_TYPED, "P", "P", KeyCode.UNDEFINED,
                        true, !mac, false, mac);
                Event.fireEvent(terminal, typed);

                assertEquals(1, dispatched.get());
                assertEquals(0, terminalCharacters.get());
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
    void everyLibrarySearchLeavesAnImeOwnedEnterToTheInputMethod() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                for (String id : java.util.List.of(
                        "hostLibraryPage", "snippetsPage", "variableLibraryPage", "credentialVaultPage")) {
                    VBox page = new VBox();
                    page.setId(id);
                    TextField search = new TextField();
                    page.getChildren().add(search);
                    Scene scene = new Scene(page);
                    AtomicInteger dispatched = new AtomicInteger();
                    AtomicInteger committedEvents = new AtomicInteger();
                    AtomicInteger textFieldActions = new AtomicInteger();
                    AtomicInteger leakedEnterPresses = new AtomicInteger();
                    KeyboardShortcutManager manager = new KeyboardShortcutManager(
                            scene, ignored -> dispatched.incrementAndGet());
                    manager.apply(KeyboardShortcutSettings.defaults());
                    search.addEventHandler(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, event -> {
                        if ("SQL".equals(event.getCommitted())) committedEvents.incrementAndGet();
                    });
                    search.setOnAction(event -> textFieldActions.incrementAndGet());
                    search.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                        if (event.getCode() == KeyCode.ENTER) leakedEnterPresses.incrementAndGet();
                    });

                    Event.fireEvent(search, new InputMethodEvent(
                            InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                            java.util.List.of(new InputMethodTextRun(
                                    "SQL", InputMethodHighlight.UNSELECTED_RAW)), "", 3));
                    Event.fireEvent(search, new InputMethodEvent(
                            InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, java.util.List.of(), "SQL", 0));
                    KeyEvent pressed = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                            false, false, false, false);
                    Event.fireEvent(search, pressed);
                    Event.fireEvent(search, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
                            false, false, false, false));

                    assertEquals(0, dispatched.get(), id);
                    assertEquals(0, textFieldActions.get(), id);
                    assertEquals(0, leakedEnterPresses.get(), id);
                    assertEquals(1, committedEvents.get(), id);
                }
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
    void normalReleasedEnterStillOpensTheSelectedLibraryItem() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                VBox page = new VBox();
                page.setId("hostLibraryPage");
                TextField search = new TextField();
                page.getChildren().add(search);
                Scene scene = new Scene(page);
                AtomicInteger dispatched = new AtomicInteger();
                KeyboardShortcutManager manager = new KeyboardShortcutManager(scene, action -> {
                    if (action == ShortcutAction.OPEN_SELECTED_LIBRARY_ITEM) dispatched.incrementAndGet();
                });
                manager.apply(KeyboardShortcutSettings.defaults());

                Event.fireEvent(search, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                Event.fireEvent(search, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
                        false, false, false, false));

                assertEquals(1, dispatched.get());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

}
