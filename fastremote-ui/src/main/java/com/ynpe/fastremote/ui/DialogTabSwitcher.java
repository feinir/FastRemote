// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.List;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Shared segmented tabs for compact application dialogs. */
final class DialogTabSwitcher {
    record Page(String title, Node content) {
        Page {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(content, "content");
        }
    }

    private DialogTabSwitcher() {}

    static VBox create(List<Page> pages) {
        Objects.requireNonNull(pages, "pages");
        if (pages.isEmpty()) throw new IllegalArgumentException("dialog tabs require at least one page");

        ToggleGroup selection = new ToggleGroup();
        HBox switcher = new HBox();
        switcher.getStyleClass().add("dialog-tab-switcher");
        StackPane content = new StackPane();
        content.setMinSize(0.0, 0.0);
        content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        for (int index = 0; index < pages.size(); index++) {
            Page page = pages.get(index);
            ToggleButton button = new ToggleButton(page.title());
            button.setToggleGroup(selection);
            button.getStyleClass().add("dialog-tab-button");
            switcher.getChildren().add(button);

            Node pageContent = page.content();
            boolean selected = index == 0;
            pageContent.setVisible(selected);
            pageContent.setManaged(selected);
            content.getChildren().add(pageContent);
            if (selected) button.setSelected(true);
            button.setOnAction(event -> select(pages, page, content));
        }
        selection.selectedToggleProperty().addListener((ignored, previous, selected) -> {
            if (selected == null && previous != null) selection.selectToggle(previous);
        });

        VBox shell = new VBox(8.0, switcher, content);
        shell.getStyleClass().add("dialog-tab-shell");
        shell.setMinSize(0.0, 0.0);
        shell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(content, Priority.ALWAYS);
        return shell;
    }

    private static void select(List<Page> pages, Page selected, StackPane content) {
        for (int index = 0; index < pages.size(); index++) {
            boolean visible = pages.get(index) == selected;
            Node pageContent = content.getChildren().get(index);
            pageContent.setVisible(visible);
            pageContent.setManaged(visible);
        }
    }
}
