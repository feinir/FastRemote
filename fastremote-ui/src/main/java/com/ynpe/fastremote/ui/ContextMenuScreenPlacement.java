// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.WindowEvent;

/** Fits native menus before GTK maps them, rather than relying on visible-window autoFix. */
final class ContextMenuScreenPlacement {
    private static final Object INSTALLED = new Object();

    private ContextMenuScreenPlacement() { }

    static void install(ContextMenu menu) {
        if (menu.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null) return;
        // ContextMenuSkin snapshots preferred size at Menu.ON_SHOWING and shifts a left/up
        // popup at ON_SHOWN if that size shrank. Complete CSS before that snapshot too.
        menu.addEventFilter(Menu.ON_SHOWING, ignored -> {
            Parent root = menu.getScene().getRoot();
            root.applyCss();
            root.autosize();
            root.layout();
        });
        menu.addEventHandler(WindowEvent.WINDOW_SHOWING, ignored -> place(menu));
    }

    private static void place(ContextMenu menu) {
        if (!menu.isAutoFix() || !Double.isFinite(menu.getAnchorX())
                || !Double.isFinite(menu.getAnchorY())) return;
        Screen screen = Screen.getScreensForRectangle(menu.getAnchorX(), menu.getAnchorY(), 1, 1)
                .stream().findFirst().orElse(Screen.getPrimary());
        Rectangle2D area = screen.getVisualBounds();
        Parent root = menu.getScene().getRoot();
        root.applyCss();
        // The owner is now assigned: the standard skin can constrain tall menus and retain scrolling.
        if (menu.getSkin() != null && menu.getSkin().getNode() instanceof Region surface) {
            double width = surface.prefWidth(-1);
            double height = Math.min(surface.prefHeight(width), area.getHeight());
            surface.resize(width, height);
            surface.layout();
            menu.setPrefSize(width, height);
        }
        root.autosize();
        root.layout();
        Bounds layout = root.getLayoutBounds();
        Bounds visual = root.getBoundsInLocal();
        boolean contentAnchor = switch (menu.getAnchorLocation()) {
            case CONTENT_TOP_LEFT, CONTENT_TOP_RIGHT, CONTENT_BOTTOM_LEFT, CONTENT_BOTTOM_RIGHT -> true;
            default -> false;
        };
        double width = contentAnchor ? layout.getWidth()
                : Math.max(layout.getMaxX(), visual.getMaxX()) - Math.min(layout.getMinX(), visual.getMinX());
        double height = contentAnchor ? layout.getHeight()
                : Math.max(layout.getMaxY(), visual.getMaxY()) - Math.min(layout.getMinY(), visual.getMinY());
        boolean right = switch (menu.getAnchorLocation()) {
            case CONTENT_TOP_RIGHT, CONTENT_BOTTOM_RIGHT, WINDOW_TOP_RIGHT, WINDOW_BOTTOM_RIGHT -> true;
            default -> false;
        };
        boolean bottom = switch (menu.getAnchorLocation()) {
            case CONTENT_BOTTOM_LEFT, CONTENT_BOTTOM_RIGHT, WINDOW_BOTTOM_LEFT, WINDOW_BOTTOM_RIGHT -> true;
            default -> false;
        };
        menu.setAnchorX(fit(menu.getAnchorX(), width, area.getMinX(), area.getMaxX(), right));
        menu.setAnchorY(fit(menu.getAnchorY(), height, area.getMinY(), area.getMaxY(), bottom));
    }

    static double fit(double anchor, double size, double min, double max, boolean trailing) {
        // Match PopupWindow's leading/trailing-edge priority, including oversized content.
        return trailing ? Math.min(max, Math.max(anchor, min + size))
                : Math.max(min, Math.min(anchor, max - size));
    }
}
