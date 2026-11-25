// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.KeyboardShortcutSettings;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.Node;

/** Installs and replaces the accelerators for the single application shortcut scheme. */
final class KeyboardShortcutManager {
    private final Scene scene;
    private final Consumer<ShortcutAction> dispatcher;
    private final Set<KeyCombination> installed = new HashSet<>();
    private Map<ShortcutAction, KeyCombination> resolved = Map.of();
    private boolean suppressNextTypedEvent;
    private final InputMethodEnterState inputMethodEnterState = new InputMethodEnterState();
    private ShortcutAction releasedEnterAction;

    KeyboardShortcutManager(Scene scene, Consumer<ShortcutAction> dispatcher) {
        this.scene = Objects.requireNonNull(scene, "scene");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        scene.addEventFilter(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, event ->
                inputMethodEnterState.inputMethodTextChanged(event.getCommitted()));
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleContextShortcut);
        scene.addEventFilter(KeyEvent.KEY_TYPED, this::suppressTypedEventAfterShortcut);
        scene.addEventFilter(KeyEvent.KEY_RELEASED, this::handleKeyReleased);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> inputMethodEnterState.pointerPressed());
        scene.focusOwnerProperty().addListener((ignored, previous, focused) -> {
            inputMethodEnterState.reset();
            releasedEnterAction = null;
        });
    }

    void apply(KeyboardShortcutSettings settings) {
        Objects.requireNonNull(settings, "settings");
        installed.forEach(scene.getAccelerators()::remove);
        installed.clear();
        EnumMap<ShortcutAction, KeyCombination> next = resolve(settings);
        next.forEach((action, combination) -> {
            if (action.scope() != ShortcutAction.Scope.GLOBAL) return;
            scene.getAccelerators().put(combination, () -> dispatcher.accept(action));
            installed.add(combination);
        });
        resolved = Map.copyOf(next);
    }

    private void handleContextShortcut(KeyEvent event) {
        // A consumed KEY_PRESSED can still be followed by a separate KEY_TYPED. Clear stale state
        // before matching this press, then arm the one-shot filter only for an application action.
        suppressNextTypedEvent = false;
        releasedEnterAction = null;
        boolean inputMethodOwned = inputMethodEnterState.keyPressed(event.getCode());
        Node eventTarget = event.getTarget() instanceof Node node ? node : scene.getFocusOwner();
        if (inputMethodOwned && insideTextInput(eventTarget)) {
            // Stop TextField's built-in Enter ActionEvent as well as application shortcuts. The
            // preceding InputMethodEvent proves that this Enter committed text, so it must not
            // leak through to a search, selector, default dialog button, or other action.
            event.consume();
            return;
        }
        Optional<ShortcutAction> global = matchingGlobalAction(resolved, event);
        if (global.isPresent()) {
            if (insideTerminal(eventTarget) && terminalOwns(event)) return;
            suppressNextTypedEvent = true;
            event.consume();
            dispatcher.accept(global.get());
            return;
        }
        for (ShortcutAction action : ShortcutAction.values()) {
            if (action.scope() == ShortcutAction.Scope.GLOBAL) continue;
            KeyCombination combination = resolved.get(action);
            if (combination == null || !combination.match(event)) continue;
            boolean applicable = switch (action.scope()) {
                case TOOL_CONTEXT -> insideToolWindow(eventTarget);
                case LIBRARY_CONTEXT -> insideLibrary(eventTarget);
                case SFTP_CONTEXT -> insideSftpBrowser(eventTarget);
                case TERMINAL_CONTEXT -> false;
                case GLOBAL -> false;
            };
            if (!applicable) continue;
            if (event.getCode() == KeyCode.ENTER && insideTextInput(eventTarget)) {
                // Let the input method receive Enter and decide on release whether it was a
                // composition commit or an application shortcut.
                releasedEnterAction = action;
                return;
            }
            event.consume();
            dispatcher.accept(action);
            return;
        }
    }

    private void handleKeyReleased(KeyEvent event) {
        suppressNextTypedEvent = false;
        if (event.getCode() != KeyCode.ENTER) {
            inputMethodEnterState.keyReleased(event.getCode());
            return;
        }
        boolean inputMethodOwned = inputMethodEnterState.inputMethodOwnsKeyRelease(event.getCode());
        ShortcutAction action = releasedEnterAction;
        releasedEnterAction = null;
        if (inputMethodOwned || action == null) return;
        event.consume();
        dispatcher.accept(action);
    }

    private void suppressTypedEventAfterShortcut(KeyEvent event) {
        if (!suppressNextTypedEvent) return;
        suppressNextTypedEvent = false;
        event.consume();
    }

    static Optional<ShortcutAction> matchingGlobalAction(
            Map<ShortcutAction, KeyCombination> shortcuts, KeyEvent event) {
        Objects.requireNonNull(shortcuts, "shortcuts");
        Objects.requireNonNull(event, "event");
        return java.util.Arrays.stream(ShortcutAction.values())
                .filter(action -> action.scope() == ShortcutAction.Scope.GLOBAL)
                .filter(action -> {
                    KeyCombination combination = shortcuts.get(action);
                    return combination != null && combination.match(event);
                })
                .findFirst();
    }

    private static boolean insideSftpBrowser(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            // Local files reuse SFTP styling, but own their keyboard operations.
            if (current.getStyleClass().contains("local-file-workspace")) return false;
            if (current.getStyleClass().contains("sftp-file-browser")) return true;
        }
        return false;
    }

    private static boolean insideToolWindow(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current.getStyleClass().contains("component-dock")
                    || current.getStyleClass().contains("component-page")) return true;
        }
        return false;
    }

    private static boolean insideTextInput(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof TextInputControl) return true;
        }
        return false;
    }

    static boolean insideTerminal(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current.getStyleClass().contains("fastremote-terminal-surface")) return true;
        }
        return false;
    }

    private static boolean terminalOwns(KeyEvent event) {
        // Command remains the macOS application modifier. Ctrl/Alt and every shortcut on
        // Windows/Linux belong to the focused PTY so Vim, nano, Emacs, tmux, less and shells see
        // their native key chords before FastRemote's application actions.
        return !isMacOS() || !event.isMetaDown();
    }

    private static boolean isMacOS() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    static boolean insideLibrary(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            String id = current.getId();
            if ("hostLibraryPage".equals(id) || "snippetsPage".equals(id)
                    || "variableLibraryPage".equals(id) || "credentialVaultPage".equals(id)) {
                return true;
            }
        }
        return false;
    }

    KeyCombination combination(ShortcutAction action) {
        return resolved.get(action);
    }

    static EnumMap<ShortcutAction, KeyCombination> resolve(KeyboardShortcutSettings settings) {
        EnumMap<ShortcutAction, KeyCombination> result = new EnumMap<>(ShortcutAction.class);
        for (ShortcutAction action : ShortcutAction.values()) {
            String text = settings.overrides().getOrDefault(action.id(), action.defaultShortcut());
            KeyCombination combination;
            try {
                combination = KeyCombination.valueOf(text);
            } catch (IllegalArgumentException invalid) {
                combination = action.defaultCombination();
            }
            result.put(action, combination);
        }
        return result;
    }

    static String conflict(KeyboardShortcutSettings settings) {
        Map<ShortcutAction.Scope, Map<KeyCombination, ShortcutAction>> used = new EnumMap<>(
                ShortcutAction.Scope.class);
        for (ShortcutAction action : ShortcutAction.values()) {
            String text = settings.overrides().getOrDefault(action.id(), action.defaultShortcut());
            final KeyCombination combination;
            try {
                combination = KeyCombination.valueOf(text);
            } catch (IllegalArgumentException invalid) {
                return action.label() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.68392dafb73089f4");
            }
            ShortcutAction previous = used.computeIfAbsent(action.scope(), ignored -> new java.util.HashMap<>())
                    .putIfAbsent(combination, action);
            if (previous != null) return com.ynpe.fastremote.i18n.Texts.text(
                    "shortcut.conflict", previous.label(), action.label());
        }
        Map<KeyCombination, ShortcutAction> global = used.getOrDefault(
                ShortcutAction.Scope.GLOBAL, Map.of());
        Map<KeyCombination, ShortcutAction> terminal = used.getOrDefault(
                ShortcutAction.Scope.TERMINAL_CONTEXT, Map.of());
        for (Map.Entry<KeyCombination, ShortcutAction> entry : global.entrySet()) {
            ShortcutAction terminalAction = terminal.get(entry.getKey());
            if (terminalAction != null) {
                return com.ynpe.fastremote.i18n.Texts.text(
                        "shortcut.conflict", entry.getValue().label(), terminalAction.label());
            }
        }
        return "";
    }
}
