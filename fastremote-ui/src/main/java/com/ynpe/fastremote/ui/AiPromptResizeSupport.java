// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Text;

/** Keeps resizing with the composer when it moves between the dock and its window. */
final class AiPromptResizeSupport {
    private AiPromptResizeSupport() { }

    static double heightAfterDrag(double start, double startY, double currentY, double minimum) {
        return Math.max(minimum, start + startY - currentY);
    }

    static void install(TextArea input, Region handle) {
        double[] drag = new double[2];
        boolean[] active = {false};
        boolean[] resized = {false};
        Runnable update = () -> {
            Text line = new Text("Ag");
            line.setFont(input.getFont());
            double row = Math.ceil(line.getLayoutBounds().getHeight());
            input.setMinHeight(row + 14);
            if (!resized[0]) input.setPrefHeight(row * 2 + 14);
            else input.setPrefHeight(Math.max(input.getMinHeight(), input.getPrefHeight()));
            input.setMaxHeight(Region.USE_PREF_SIZE);
        };
        input.setPrefRowCount(2);
        update.run();
        input.fontProperty().addListener((ignored, oldFont, font) -> update.run());
        handle.setPickOnBounds(true);
        handle.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            active[0] = true;
            drag[0] = event.getScreenY(); drag[1] = input.getHeight();
            event.consume();
        });
        handle.setOnMouseDragged(event -> {
            if (!active[0]) return;
            resized[0] = true;
            input.setPrefHeight(heightAfterDrag(drag[1], drag[0], event.getScreenY(), input.getMinHeight()));
            event.consume();
        });
        handle.setOnMouseReleased(event -> { active[0] = false; });
    }
}
