// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.Scene;
import javafx.geometry.Side;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/** Creates and styles FastRemote-owned right-click menus. */
final class ApplicationContextMenus {
    static final String STYLE_CLASS = "application-context-menu";
    private static final Object LIFETIME_INSTALLED = new Object();
    private static final Object SUBMENU_PREPARATION_INSTALLED = new Object();
    private static final Object SUBMENU_ITEM_PREPARATION_INSTALLED = new Object();
    private static ContextMenu activeMenu;
    private static Scene activeOwnerScene;
    private static Node activeOwnerNode;
    private static javafx.event.EventHandler<MouseEvent> activeOwnerPress;

    private ApplicationContextMenus() { }

    static ContextMenu create(MenuItem... items) {
        return style(new PreparedContextMenu(items));
    }

    /** Lets menu selection and native popup cleanup finish before entering a modal event loop. */
    static void runAfterHide(ContextMenu menu, Runnable action) {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(action, "action");
        menu.hide();
        Platform.runLater(action);
    }

    static void prepareBeforeShow(ContextMenu menu, Runnable preparation) {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(preparation, "preparation");
        if (!(menu instanceof PreparedContextMenu prepared)) {
            throw new IllegalArgumentException("menu was not created by ApplicationContextMenus");
        }
        prepared.preparations.add(preparation);
    }

    static ContextMenu style(ContextMenu menu) {
        Objects.requireNonNull(menu, "menu");
        if (!menu.getStyleClass().contains(STYLE_CLASS)) {
            menu.getStyleClass().add(STYLE_CLASS);
        }
        String languageTag = com.ynpe.fastremote.i18n.Texts.localizer().resolvedLanguage()
                .persistedTag().toLowerCase(java.util.Locale.ROOT);
        String family = InterfaceFontPolicy.interfaceFontFamily(languageTag).replace("'", "\\'");
        menu.setStyle("-fx-font-family: '" + family + "';");
        if (LocalizedTextInputContextMenus.isLinux()) ContextMenuScreenPlacement.install(menu);
        if (menu.getProperties().putIfAbsent(LIFETIME_INSTALLED, Boolean.TRUE) == null) {
            menu.setHideOnEscape(true);
            boolean linux = LocalizedTextInputContextMenus.isLinux();
            if (linux) {
                // JavaFX/GTK auto-hide can retain or lose its native mouse grab after pointer motion.
                menu.setAutoHide(false);
            }
            menu.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> activate(menu));
            menu.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> {
                deactivate(menu);
            });
        }
        return menu;
    }

    private static void activate(ContextMenu menu) {
        Window ownerWindow = menu.getOwnerWindow();
        activate(menu, ownerWindow == null ? null : ownerWindow.getScene(), menu.getOwnerNode());
    }

    private static void activate(ContextMenu menu, Scene ownerScene, Node ownerNode) {
        ContextMenu previous = activeMenu;
        if (previous != null && previous != menu) previous.hide();
        detachOwnerPress();
        activeMenu = menu;
        activeOwnerScene = ownerScene;
        activeOwnerNode = ownerNode;
        if (activeOwnerScene == null) return;
        activeOwnerPress = event -> {
            // Button-owned menus implement click-again-to-close in their action handler.
            if (activeOwnerNode instanceof ButtonBase owner
                    && isInside(event.getTarget(), owner)) return;
            menu.hide();
        };
        activeOwnerScene.addEventFilter(MouseEvent.MOUSE_PRESSED, activeOwnerPress);
    }

    private static void deactivate(ContextMenu menu) {
        if (activeMenu != menu) return;
        detachOwnerPress();
        activeMenu = null;
    }

    private static void detachOwnerPress() {
        if (activeOwnerScene != null && activeOwnerPress != null) {
            activeOwnerScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, activeOwnerPress);
        }
        activeOwnerScene = null;
        activeOwnerNode = null;
        activeOwnerPress = null;
    }

    static ContextMenu activeMenu() {
        return activeMenu;
    }

    static boolean isShowing(ContextMenu menu) {
        return menu != null && menu.isShowing();
    }

    static Menu styleSubmenu(Menu menu) {
        Objects.requireNonNull(menu, "menu");
        if (menu.getProperties().putIfAbsent(SUBMENU_PREPARATION_INSTALLED, Boolean.TRUE) == null) {
            menu.getItems().forEach(item -> installSubmenuItemPreparation(menu, item));
            menu.getItems().addListener((ListChangeListener<MenuItem>) change -> {
                while (change.next()) {
                    if (change.wasAdded()) {
                        change.getAddedSubList()
                                .forEach(item -> installSubmenuItemPreparation(menu, item));
                    }
                }
            });
        }
        return menu;
    }

    private static void installSubmenuItemPreparation(Menu source, MenuItem item) {
        if (item.getProperties().putIfAbsent(
                SUBMENU_ITEM_PREPARATION_INSTALLED, Boolean.TRUE) != null) return;
        item.parentPopupProperty().addListener((ignored, previous, candidate) -> {
            ContextMenu root = source.getParentPopup();
            if (candidate != null && root != null && candidate != root) {
                prepareSubmenu(candidate, root);
            }
        });
        if (item instanceof Menu nested) styleSubmenu(nested);
    }

    private static void prepareSubmenu(ContextMenu submenu, ContextMenu root) {
        submenu.setStyle(root.getStyle());
        submenu.setHideOnEscape(true);
        if (LocalizedTextInputContextMenus.isLinux()) {
            submenu.setAutoHide(false);
            ContextMenuScreenPlacement.install(submenu);
        }
        for (String styleClass : root.getStyleClass()) {
            if (!submenu.getStyleClass().contains(styleClass)) {
                submenu.getStyleClass().add(styleClass);
            }
        }
        if (root.getSkin() != null && root.getSkin().getNode() != null) {
            ThemeManager.prepareContextMenuBeforeShow(submenu, root.getSkin().getNode());
        }
    }

    private static final class PreparedContextMenu extends ContextMenu {
        private final List<Runnable> preparations = new ArrayList<>();

        private PreparedContextMenu(MenuItem... items) {
            super(items);
        }

        private void prepare(Node anchor) {
            preparations.forEach(Runnable::run);
            ThemeManager.prepareContextMenuBeforeShow(this, anchor);
        }

        @Override
        public void show(Node anchor, double screenX, double screenY) {
            prepare(anchor);
            super.show(anchor, screenX, screenY);
        }

        @Override
        public void show(Node anchor, Side side, double dx, double dy) {
            prepare(anchor);
            super.show(anchor, side, dx, dy);
        }
    }

    private static boolean isInside(Object target, Node ancestor) {
        if (!(target instanceof Node node)) return false;
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == ancestor) return true;
        }
        return false;
    }
}
