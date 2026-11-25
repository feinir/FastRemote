// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;

/** Positions a searchable selector below its control when possible, otherwise above it. */
final class SearchableSelectorPopupLayout {
    private SearchableSelectorPopupLayout() {
    }

    static boolean opensUpward(Bounds anchor, double popupHeight) {
        Screen screen = Screen.getScreensForRectangle(
                anchor.getMinX(), anchor.getMinY(), anchor.getWidth(), anchor.getHeight()).stream()
                .findFirst().orElse(Screen.getPrimary());
        return opensUpward(anchor, screen.getVisualBounds(), popupHeight);
    }

    static boolean opensUpward(Bounds anchor, Rectangle2D visualBounds, double popupHeight) {
        double below = visualBounds.getMaxY() - anchor.getMaxY();
        double above = anchor.getMinY() - visualBounds.getMinY();
        return popupHeight > below && above > below;
    }

    static double preferredHeight(Region popupContent) {
        popupContent.applyCss();
        popupContent.layout();
        return popupContent.prefHeight(popupContent.getPrefWidth());
    }

    static void arrange(VBox popupContent, Node search, Node results, boolean opensUpward) {
        if (opensUpward) popupContent.getChildren().setAll(results, search);
        else popupContent.getChildren().setAll(search, results);
    }
}
