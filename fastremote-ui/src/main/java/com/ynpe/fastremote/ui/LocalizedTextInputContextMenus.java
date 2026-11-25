// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.i18n.Localizer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/** Installs FastRemote-localized context menus for every ordinary JavaFX text input. */
public final class LocalizedTextInputContextMenus implements AutoCloseable {
    private final Localizer localizer;
    private final Map<Window, ChangeListener<Scene>> observedWindows = new IdentityHashMap<>();
    private final Map<Window, javafx.event.EventHandler<WindowEvent>> observedWindowHiding =
            new IdentityHashMap<>();
    private final Map<Scene, javafx.event.EventHandler<ContextMenuEvent>> observedScenes =
            new IdentityHashMap<>();
    private final Map<Scene, javafx.event.EventHandler<MouseEvent>> observedMouseScenes =
            new IdentityHashMap<>();
    private final ListChangeListener<Window> windowsChanged = change -> {
        while (change.next()) {
            change.getRemoved().forEach(this::unobserveWindow);
            change.getAddedSubList().forEach(this::observeWindow);
        }
    };
    private final javafx.event.EventHandler<MouseEvent> activeInputPressed = event -> {
        if (event.getButton() == MouseButton.PRIMARY) hideActiveMenu();
    };
    private ContextMenu activeMenu;
    private Scene activeOwnerScene;
    private TextInputControl activeInput;
    private boolean closed;

    private LocalizedTextInputContextMenus(Localizer localizer) {
        this.localizer = Objects.requireNonNull(localizer, "localizer");
    }

    public static LocalizedTextInputContextMenus install(Localizer localizer) {
        requireJavaFxThread();
        LocalizedTextInputContextMenus installation = new LocalizedTextInputContextMenus(localizer);
        Window.getWindows().addListener(installation.windowsChanged);
        Window.getWindows().forEach(installation::observeWindow);
        return installation;
    }

    private void observeWindow(Window window) {
        if (closed || observedWindows.containsKey(window)) return;
        ChangeListener<Scene> listener = (ignored, previous, current) -> {
            uninstallScene(previous);
            installScene(current);
        };
        observedWindows.put(window, listener);
        window.sceneProperty().addListener(listener);
        javafx.event.EventHandler<WindowEvent> hiding = ignored -> {
            if (window.getScene() == activeOwnerScene) hideActiveMenu();
        };
        observedWindowHiding.put(window, hiding);
        window.addEventHandler(WindowEvent.WINDOW_HIDING, hiding);
        installScene(window.getScene());
    }

    private void unobserveWindow(Window window) {
        ChangeListener<Scene> listener = observedWindows.remove(window);
        if (listener != null) window.sceneProperty().removeListener(listener);
        javafx.event.EventHandler<WindowEvent> hiding = observedWindowHiding.remove(window);
        if (hiding != null) window.removeEventHandler(WindowEvent.WINDOW_HIDING, hiding);
        // Window.getWindows() is in the middle of publishing a removal here. Hiding a popup
        // synchronously would mutate that same list recursively and can leave GTK with a stale
        // input-method view. WINDOW_HIDING above closes the active popup before this callback.
        uninstallScene(window.getScene(), false);
    }

    private void installScene(Scene scene) {
        if (scene == null || observedScenes.containsKey(scene)) return;
        javafx.event.EventHandler<ContextMenuEvent> handler = this::showLocalizedMenu;
        observedScenes.put(scene, handler);
        scene.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, handler);
        javafx.event.EventHandler<MouseEvent> mouseHandler = this::recoverMissingContextMenuRequest;
        observedMouseScenes.put(scene, mouseHandler);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, mouseHandler);
    }

    private void uninstallScene(Scene scene) {
        uninstallScene(scene, true);
    }

    private void uninstallScene(Scene scene, boolean hideOwnedMenu) {
        if (scene == null) return;
        javafx.event.EventHandler<ContextMenuEvent> handler = observedScenes.remove(scene);
        if (handler != null) {
            scene.removeEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, handler);
        }
        javafx.event.EventHandler<MouseEvent> mouseHandler = observedMouseScenes.remove(scene);
        if (mouseHandler != null) scene.removeEventFilter(MouseEvent.MOUSE_RELEASED, mouseHandler);
        if (hideOwnedMenu && scene == activeOwnerScene) hideActiveMenu();
    }

    private void showLocalizedMenu(ContextMenuEvent event) {
        hideActiveMenu();
        TextInputControl input = textInput(event.getTarget());
        if (!usesApplicationMenu(input)) return;

        showLocalizedMenu(input, event.getScreenX(), event.getScreenY(), event.isKeyboardTrigger());
        event.consume();
    }

    private void recoverMissingContextMenuRequest(MouseEvent event) {
        if (!isLinux() || event.getButton() != MouseButton.SECONDARY) return;
        TextInputControl input = textInput(event.getTarget());
        if (!usesApplicationMenu(input)) return;
        double screenX = event.getScreenX();
        double screenY = event.getScreenY();
        Platform.runLater(() -> {
            if (closed || input.getScene() == null) return;
            if (activeInput == input && activeMenu != null && activeMenu.isShowing()) return;
            hideActiveMenu();
            showLocalizedMenu(input, screenX, screenY, false);
        });
    }

    static boolean isLinux() {
        String osName = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return osName.contains("linux");
    }

    private void showLocalizedMenu(TextInputControl input, double screenX, double screenY, boolean keyboardTrigger) {

        ContextMenu menu = createMenu(input, localizer);
        menu.setAutoHide(!isLinux());
        menu.setHideOnEscape(true);
        activeMenu = menu;
        activeOwnerScene = input.getScene();
        activeInput = input;
        input.addEventFilter(MouseEvent.MOUSE_PRESSED, activeInputPressed);
        menu.setOnHidden(ignored -> clearActiveMenu(menu));
        if (keyboardTrigger) {
            menu.show(input, Side.BOTTOM, 0.0, 0.0);
        } else {
            menu.show(input, screenX, screenY);
        }
    }

    private void hideActiveMenu() {
        ContextMenu previous = activeMenu;
        if (previous == null) return;
        clearActiveMenu(previous);
        previous.hide();
    }

    private void clearActiveMenu(ContextMenu expected) {
        if (activeMenu != expected) return;
        TextInputControl previousInput = activeInput;
        activeMenu = null;
        activeOwnerScene = null;
        activeInput = null;
        if (previousInput != null) {
            previousInput.removeEventFilter(MouseEvent.MOUSE_PRESSED, activeInputPressed);
        }
    }

    static boolean usesApplicationMenu(TextInputControl input) {
        return input != null && !input.isDisabled()
                // Preserve controls that deliberately own a specialized context menu or handler.
                && input.getContextMenu() == null && input.getOnContextMenuRequested() == null;
    }

    private static TextInputControl textInput(Object target) {
        if (!(target instanceof Node node)) return null;
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof TextInputControl input) return input;
        }
        return null;
    }

    static ContextMenu createMenu(TextInputControl input, Localizer localizer) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(localizer, "localizer");
        ContextMenu menu = ApplicationContextMenus.create();
        Runnable refresh = () -> populateMenu(menu, input, localizer);
        refresh.run();
        ApplicationContextMenus.prepareBeforeShow(menu, refresh);
        menu.getStyleClass().add("text-input-context-menu");
        return menu;
    }

    private static void populateMenu(
            ContextMenu menu, TextInputControl input, Localizer localizer) {
        boolean editable = input.isEditable();
        boolean password = input instanceof PasswordField;
        boolean hasSelection = input.getSelection().getLength() > 0;
        boolean hasText = !input.getText().isEmpty();
        boolean allSelected = hasText && input.getSelection().getLength() == input.getLength();

        MenuItem undo = item("undo", localizer, input::undo);
        MenuItem redo = item("redo", localizer, input::redo);
        MenuItem cut = item("cut", localizer, input::cut);
        MenuItem copy = item("copy", localizer, input::copy);
        MenuItem paste = item("paste", localizer, input::paste);
        MenuItem delete = item("delete", localizer, () -> input.replaceSelection(""));
        MenuItem selectAll = item("selectAll", localizer, input::selectAll);

        undo.setDisable(!editable || !input.isUndoable());
        redo.setDisable(!editable || !input.isRedoable());
        cut.setDisable(!editable || password || !hasSelection);
        copy.setDisable(password || !hasSelection);
        boolean clipboardHasString = !Platform.isFxApplicationThread()
                || Clipboard.getSystemClipboard().hasString();
        paste.setDisable(!editable || !clipboardHasString);
        delete.setDisable(!editable || !hasSelection);
        selectAll.setDisable(!hasText || allSelected);

        menu.getItems().setAll(undo, redo, cut, copy, paste, delete,
                new SeparatorMenuItem(), selectAll);
    }

    private static MenuItem item(String action, Localizer localizer, Runnable operation) {
        MenuItem item = new MenuItem(localizer.text("textInput.context." + action));
        item.setId("text-input-context-" + action);
        item.setOnAction(event -> operation.run());
        return item;
    }

    boolean isInstalledOn(Scene scene) {
        return observedScenes.containsKey(scene);
    }

    ContextMenu activeMenu() {
        return activeMenu;
    }

    @Override
    public void close() {
        requireJavaFxThread();
        if (closed) return;
        closed = true;
        hideActiveMenu();
        Window.getWindows().removeListener(windowsChanged);
        for (Map.Entry<Window, ChangeListener<Scene>> entry :
                java.util.List.copyOf(observedWindows.entrySet())) {
            entry.getKey().sceneProperty().removeListener(entry.getValue());
        }
        observedWindows.clear();
        for (Map.Entry<Window, javafx.event.EventHandler<WindowEvent>> entry :
                java.util.List.copyOf(observedWindowHiding.entrySet())) {
            entry.getKey().removeEventHandler(WindowEvent.WINDOW_HIDING, entry.getValue());
        }
        observedWindowHiding.clear();
        for (Map.Entry<Scene, javafx.event.EventHandler<ContextMenuEvent>> entry :
                java.util.List.copyOf(observedScenes.entrySet())) {
            entry.getKey().removeEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, entry.getValue());
        }
        observedScenes.clear();
        for (Map.Entry<Scene, javafx.event.EventHandler<MouseEvent>> entry :
                java.util.List.copyOf(observedMouseScenes.entrySet())) {
            entry.getKey().removeEventFilter(MouseEvent.MOUSE_RELEASED, entry.getValue());
        }
        observedMouseScenes.clear();
    }

    private static void requireJavaFxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("text input context menus must be managed on the JavaFX thread");
        }
    }
}
