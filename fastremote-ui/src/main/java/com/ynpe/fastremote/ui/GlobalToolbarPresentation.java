// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.ToolbarDisplayMode;
import com.ynpe.fastremote.i18n.Texts;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;

/** Toolbar presentation and its menu; never changes action order or availability. */
final class GlobalToolbarPresentation {
    private GlobalToolbarPresentation() { }

    static String label(ToolbarDisplayMode mode) {
        return Texts.text(switch (mode) {
            case ICONS_AND_TEXT -> "toolbar.iconsAndText";
            case ICONS_ONLY -> "toolbar.iconsOnly";
            case TEXT_ONLY -> "toolbar.textOnly";
        });
    }

    static ContextMenu install(ToolBar toolbar, Supplier<ToolbarDisplayMode> current,
            Consumer<ToolbarDisplayMode> save, Runnable customize) {
        flattenActionGroups(toolbar);
        ContextMenu menu = ApplicationContextMenus.create();
        ToggleGroup group = new ToggleGroup();
        for (ToolbarDisplayMode mode : ToolbarDisplayMode.values()) {
            RadioMenuItem item = new RadioMenuItem(label(mode));
            item.setToggleGroup(group);
            item.setUserData(mode);
            item.setOnAction(event -> save.accept(mode));
            menu.getItems().add(item);
        }
        MenuItem custom = new MenuItem(Texts.text("toolbar.customize"));
        custom.setOnAction(event -> customize.run());
        menu.getItems().addAll(new SeparatorMenuItem(), custom);
        ApplicationContextMenus.prepareBeforeShow(menu, () -> group.getToggles().stream()
                .filter(toggle -> toggle.getUserData() == current.get()).findFirst()
                .ifPresent(group::selectToggle));
        toolbar.setContextMenu(menu);
        if (toolbar.getParent() != null
                && toolbar.getParent().getStyleClass().contains("main-toolbar-row")) {
            toolbar.getParent().setOnContextMenuRequested(event -> {
                menu.show(toolbar, event.getScreenX(), event.getScreenY());
                event.consume();
            });
        }
        return menu;
    }

    // Each button must be an overflow item, rather than disappearing with its whole group.
    private static void flattenActionGroups(ToolBar toolbar) {
        var items = new java.util.ArrayList<Node>();
        for (Node item : toolbar.getItems()) {
            if (item instanceof javafx.scene.layout.HBox group
                    && group.getStyleClass().contains("toolbar-primary-actions")) {
                items.addAll(group.getChildren());
                group.getChildren().clear();
            } else {
                items.add(item);
            }
        }
        toolbar.getItems().setAll(items);
    }

    static void apply(ToolBar toolbar, ToolbarDisplayMode mode) {
        toolbar.getStyleClass().removeAll("toolbar-icons-and-text", "toolbar-icons-only", "toolbar-text-only");
        toolbar.getStyleClass().add(switch (mode) {
            case ICONS_AND_TEXT -> "toolbar-icons-and-text";
            case ICONS_ONLY -> "toolbar-icons-only";
            case TEXT_ONLY -> "toolbar-text-only";
        });
        for (Node item : toolbar.getItems()) apply(item, mode);
        toolbar.requestLayout();
    }

    private static void apply(Node node, ToolbarDisplayMode mode) {
        if (node instanceof ButtonBase button && button.getGraphic() != null) {
            button.setContentDisplay(button.getStyleClass().contains("toolbar-icon-button")
                    ? ContentDisplay.GRAPHIC_ONLY : switch (mode) {
                case ICONS_AND_TEXT -> ContentDisplay.LEFT;
                case ICONS_ONLY -> ContentDisplay.GRAPHIC_ONLY;
                case TEXT_ONLY -> ContentDisplay.TEXT_ONLY;
            });
        } else if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> apply(child, mode));
        }
    }
}
