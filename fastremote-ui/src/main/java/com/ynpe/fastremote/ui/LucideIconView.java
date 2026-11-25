// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/** A lightweight JavaFX rendering of the bundled Lucide 24-by-24 stroke geometry. */
public final class LucideIconView extends StackPane {
    public enum Icon {
        SQUARE_TERMINAL, SQUARE, CABLE, MONITOR_UP, ARROW_UP, SERVER, VAULT, SETTINGS, PANEL_LEFT, PANEL_RIGHT,
        CODE_XML, LOCK_KEYHOLE, BOT, AI, VARIABLE, CHAT_AI, CHAT_NEW, DISCUSS, FOLDER, FOLDER_UP, FOLDER_PLUS, PLUS, BRACES, STAR, REFRESH_CW, MENU,
        EXTERNAL_LINK, IMPORT, EXPORT, DATABASE, CLOUD, NETWORK, HARD_DRIVE, CONTAINER, MONITOR, ROUTER,
        SHIELD_CHECK, APPLE, COMMAND, PANELS_TOP_LEFT, BUG, BOXES, FILE, FILE_TEXT, FILE_CODE_2,
        GLOBE, FILE_TYPE_2, FILE_COG, FILE_JSON_2, FILE_SPREADSHEET, PRESENTATION, FILE_IMAGE,
        FILE_ARCHIVE, AUDIO_LINES, FILE_VIDEO, KEY_ROUND, SCROLL_TEXT, BINARY, TYPE, PACKAGE,
        FILE_TERMINAL, FILES, HISTORY, MESSAGE_SQUARE_MORE, COPY, SAVE, PENCIL, TRASH_2, LOCATE_FIXED,
        SEARCH, PIN, ELLIPSIS, LIST_CHECKS, WINDOWS, LINUX, FREEBSD, WRAP_TEXT, BRUSH_CLEANING, PLUG_ZAP
    }

    private final SVGPath path = new SVGPath();
    private Icon icon;

    public LucideIconView() {
        setAlignment(Pos.CENTER);
        setMinSize(24.0, 24.0);
        setPrefSize(24.0, 24.0);
        setMaxSize(24.0, 24.0);
        setMouseTransparent(true);
        getStyleClass().add("lucide-icon-view");
        path.getStyleClass().add("lucide-icon-stroke");
        path.setStroke(javafx.scene.paint.Color.web("#536176"));
        path.setFill(javafx.scene.paint.Color.TRANSPARENT);
        getChildren().add(path);
    }

    LucideIconView(Icon icon) {
        this();
        setIcon(icon);
    }

    public Icon getIcon() {
        return icon;
    }

    public void setIcon(Icon icon) {
        this.icon = Objects.requireNonNull(icon, "icon");
        path.fillProperty().unbind();
        boolean filled = icon == Icon.AI || icon == Icon.VARIABLE || icon == Icon.CHAT_AI || icon == Icon.DISCUSS || icon == Icon.CHAT_NEW;
        path.setStyle(filled ? "-fx-stroke-width: 0;" : "");
        if (filled) path.fillProperty().bind(path.strokeProperty());
        else path.setFill(javafx.scene.paint.Color.TRANSPARENT);
        path.setContent(LucideIconPaths.path(icon));
    }
}
