// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.ApplicationTheme;
import java.util.Objects;
import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.skin.ContextMenuSkin;
import javafx.stage.PopupWindow;
import javafx.stage.Window;

/** Applies the selected built-in theme to every application-owned JavaFX window and popup. */
final class ThemeManager {
    private static ApplicationTheme current = ApplicationTheme.LIGHT;
    private static boolean trackingWindows;
    private static final java.util.Set<Scene> scenes = java.util.Collections.newSetFromMap(
            new java.util.WeakHashMap<>());
    private static final java.util.Set<Scene> rootThemedScenes = java.util.Collections.newSetFromMap(
            new java.util.WeakHashMap<>());
    private static final java.util.Set<Parent> styledRoots = java.util.Collections.newSetFromMap(
            new java.util.WeakHashMap<>());
    private static final java.util.Set<Window> observedWindows = java.util.Collections.newSetFromMap(
            new java.util.WeakHashMap<>());
    private static final Object SCENE_TRACKING_KEY = new Object();
    private ThemeManager() {}

    static void install(Scene mainScene) {
        Objects.requireNonNull(mainScene, "mainScene");
        if (!trackingWindows) {
            trackingWindows = true;
            Window.getWindows().addListener((ListChangeListener<Window>) change -> {
                while (change.next()) {
                    if (!change.wasAdded()) continue;
                    for (Window window : change.getAddedSubList()) {
                        if (!(window instanceof PopupWindow)) {
                            apply(window.getScene());
                            if (observedWindows.add(window)) {
                                window.sceneProperty().addListener(
                                        (ignored, previous, scene) -> apply(scene));
                            }
                        }
                    }
                }
            });
        }
        rootThemedScenes.add(mainScene);
        moveSceneStylesheetsToRoot(mainScene);
        scenes.add(mainScene);
        apply(mainScene);
    }

    static void setTheme(ApplicationTheme theme) {
        current = Objects.requireNonNull(theme, "theme");
        for (Scene scene : java.util.List.copyOf(scenes)) {
            if (isPopupScene(scene)) {
                scenes.remove(scene);
                continue;
            }
            applyAndPrepare(scene);
        }
        for (Parent root : java.util.List.copyOf(styledRoots)) {
            apply(root);
            prepare(root);
        }
        for (Window window : Window.getWindows()) {
            if (!(window instanceof PopupWindow)) applyAndPrepare(window.getScene());
        }
    }

    static ApplicationTheme currentTheme() {
        return current;
    }

    static void style(Parent root) {
        Objects.requireNonNull(root, "root");
        applyLocale(root);
        styledRoots.add(root);
        if (root.getProperties().putIfAbsent(SCENE_TRACKING_KEY, Boolean.TRUE) == null) {
            root.sceneProperty().addListener((ignored, previous, scene) -> prepare(root));
        }
        String base = MainView.stylesheet().toExternalForm();
        if (!root.getStylesheets().contains(base)) root.getStylesheets().add(base);
        apply(root);
        prepare(root);
    }

    /** Resolves the complete themed CSS tree before an owned window becomes visible. */
    static void prepare(Parent root) {
        Objects.requireNonNull(root, "root");
        Scene scene = root.getScene();
        if (scene == null) return;
        if (isPopupScene(scene)) return;
        apply(scene);
        root.applyCss();
        root.layout();
    }

    private static void apply(Scene scene) {
        if (scene == null) return;
        if (!isPopupScene(scene)) scenes.add(scene);
        if (rootThemedScenes.contains(scene)) {
            moveSceneStylesheetsToRoot(scene);
            Parent root = scene.getRoot();
            if (root != null) apply(root);
            return;
        }
        applyStylesheet(scene);
    }

    /** Completes popup styles and preferred-size CSS before ContextMenu creates a native window. */
    static void prepareContextMenuBeforeShow(ContextMenu menu, Node owner) {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(owner, "owner");
        Scene ownerScene = owner.getScene();
        Scene popupScene = menu.getScene();
        if (ownerScene == null || popupScene == null) return;
        if (LocalizedTextInputContextMenus.isLinux() && ownerScene.getWindow() != null) {
            // A new Popup starts at scale 1. Measuring before its GTK peer inherits the owner's
            // HiDPI scale changes pixel snapping after show and makes ContextMenuSkin shift it.
            menu.setRenderScaleX(ownerScene.getWindow().getRenderScaleX());
            menu.setRenderScaleY(ownerScene.getWindow().getRenderScaleY());
        }
        java.util.List<String> stylesheets = new java.util.ArrayList<>(ownerScene.getStylesheets());
        if (!popupScene.getStylesheets().equals(stylesheets)) {
            popupScene.getStylesheets().setAll(stylesheets);
        }
        if (menu.getSkin() == null) menu.setSkin(new ContextMenuSkin(menu));
        java.util.LinkedHashSet<String> inherited = new java.util.LinkedHashSet<>();
        for (Node currentNode = owner; currentNode != null; currentNode = currentNode.getParent()) {
            if (currentNode instanceof Parent parent) inherited.addAll(parent.getStylesheets());
        }
        if (menu.getStyleableNode() instanceof Parent popupRoot) {
            if (!popupRoot.getStylesheets().equals(java.util.List.copyOf(inherited))) {
                popupRoot.getStylesheets().setAll(inherited);
            }
        }
        Parent root = popupScene.getRoot();
        if (root != null) applyLocale(root);
        Node styleable = menu.getStyleableNode();
        if (styleable != null) styleable.applyCss();
        if (menu.getSkin() != null && menu.getSkin().getNode() != null) {
            if (menu.getSkin().getNode() instanceof Parent skinRoot
                    && !skinRoot.getStylesheets().equals(java.util.List.copyOf(inherited))) {
                skinRoot.getStylesheets().setAll(inherited);
            }
            menu.getSkin().getNode().applyCss();
            if (menu.getSkin().getNode() instanceof javafx.scene.layout.Region surface) {
                double width = surface.prefWidth(-1.0);
                double height = surface.prefHeight(width);
                surface.resize(width, height);
                surface.layout();
                menu.setPrefSize(width, height);
            }
            Parent popupRoot = popupScene.getRoot();
            if (popupRoot != null) {
                popupRoot.applyCss();
                popupRoot.autosize();
                popupRoot.layout();
            }
        }
        menu.prefWidth(-1.0);
        menu.prefHeight(-1.0);
    }

    private static void moveSceneStylesheetsToRoot(Scene scene) {
        Parent root = scene.getRoot();
        if (root == null || scene.getStylesheets().isEmpty()) return;
        for (String stylesheet : java.util.List.copyOf(scene.getStylesheets())) {
            if (!root.getStylesheets().contains(stylesheet)) root.getStylesheets().add(stylesheet);
        }
        scene.getStylesheets().clear();
    }

    private static void applyStylesheet(Scene scene) {
        Parent root = scene.getRoot();
        if (root != null) applyLocale(root);
        if (root != null && styledRoots.contains(root)) {
            // Dialog scenes bind their stylesheet list to the DialogPane list. Mutating both sides
            // directly corrupts JavaFX ContentBinding's change indexes during a theme switch.
            return;
        }
        replaceThemeStylesheet(scene.getStylesheets());
    }

    private static boolean isPopupScene(Scene scene) {
        return scene != null && scene.getWindow() instanceof PopupWindow;
    }

    private static void applyAndPrepare(Scene scene) {
        if (scene == null) return;
        if (isPopupScene(scene)) return;
        apply(scene);
        Parent root = scene.getRoot();
        if (root != null) {
            root.applyCss();
            root.layout();
        }
    }

    private static void apply(Parent root) {
        applyLocale(root);
        replaceThemeStylesheet(root.getStylesheets());
    }

    private static void replaceThemeStylesheet(javafx.collections.ObservableList<String> stylesheets) {
        String dark = MainView.darkStylesheet().toExternalForm();
        String slate = MainView.slateStylesheet().toExternalForm();
        java.util.List<String> updated = new java.util.ArrayList<>(stylesheets.size() + 1);
        stylesheets.stream().filter(stylesheet -> !dark.equals(stylesheet) && !slate.equals(stylesheet)).forEach(updated::add);
        if (current == ApplicationTheme.DARK) updated.add(dark);
        if (current == ApplicationTheme.SLATE) updated.add(slate);
        if (!stylesheets.equals(updated)) stylesheets.setAll(updated);
    }

    private static void applyLocale(Parent root) {
        InterfaceFontPolicy.apply(root);
    }
}
