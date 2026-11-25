// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/** Creates compact, semantically distinct graphics for workspace tab headers. */
final class WorkspaceTabGraphics {
    static final double ICON_SIZE = 16.0;
    private static final double ICON_SCALE = 0.72;

    private WorkspaceTabGraphics() { }

    static Node aiAssistant() {
        return icon(LucideIconView.Icon.AI, "workspace-tab-icon-tool");
    }

    static Node diagnosticLog() {
        return icon(LucideIconView.Icon.SCROLL_TEXT, "workspace-tab-icon-tool");
    }

    static Node localTerminal() {
        return icon(LucideIconView.Icon.SQUARE_TERMINAL, "workspace-tab-icon-local");
    }

    static Node localFiles() {
        return icon(LucideIconView.Icon.FOLDER, "workspace-tab-icon-local");
    }

    static Node editor() {
        return icon(LucideIconView.Icon.FILE_TEXT, "workspace-tab-icon-local");
    }

    static Node remoteTerminal(Region state) {
        return remote(state, LucideIconView.Icon.MONITOR_UP);
    }

    static Node remoteFiles(Region state) {
        return remote(state, LucideIconView.Icon.FOLDER_UP);
    }

    private static Node remote(Region state, LucideIconView.Icon icon) {
        HBox graphic = new HBox(4.0, state, icon(icon, "workspace-tab-icon-remote"));
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.getStyleClass().add("workspace-tab-graphic");
        return graphic;
    }

    private static LucideIconView icon(LucideIconView.Icon icon, String semanticStyle) {
        LucideIconView view = new LucideIconView(icon);
        view.setMinSize(ICON_SIZE, ICON_SIZE);
        view.setPrefSize(ICON_SIZE, ICON_SIZE);
        view.setMaxSize(ICON_SIZE, ICON_SIZE);
        view.setScaleX(ICON_SCALE);
        view.setScaleY(ICON_SCALE);
        view.getStyleClass().addAll("workspace-tab-icon", semanticStyle);
        return view;
    }
}
