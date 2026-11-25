// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;

/** Creates the shared icon-only button used to select a private-key or certificate file. */
final class FileSelectionButton {
    static final double WIDTH = 40.0;

    private FileSelectionButton() {}

    static Button create(String accessibleText) {
        Button button = new Button();
        button.setAccessibleText(accessibleText);
        button.setTooltip(new Tooltip(accessibleText));
        button.setGraphic(new LucideIconView(LucideIconView.Icon.FOLDER));
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.getStyleClass().add("compact-button");
        button.setMinWidth(WIDTH);
        button.setPrefWidth(WIDTH);
        button.setMaxWidth(WIDTH);
        return button;
    }
}
