// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.TextArea;

/** Text area whose JavaFX skin always reserves and displays its vertical scroll bar. */
public final class AlwaysVisibleVerticalTextArea extends TextArea {
    private Skin<?> configuredSkin;

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        Skin<?> currentSkin = getSkin();
        if (currentSkin == null || currentSkin == configuredSkin) return;
        Node scrollNode = lookup(".scroll-pane");
        if (scrollNode instanceof ScrollPane scrollPane) {
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
            configuredSkin = currentSkin;
        }
    }
}
