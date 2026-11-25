// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

class WorkspaceTabGraphicsTest {
    @Test
    void compactIconsUseDistinctSemanticsForEveryWorkspaceType() {
        assertIcon(WorkspaceTabGraphics.aiAssistant(), LucideIconView.Icon.AI);
        assertIcon(WorkspaceTabGraphics.diagnosticLog(), LucideIconView.Icon.SCROLL_TEXT);
        assertIcon(WorkspaceTabGraphics.localTerminal(), LucideIconView.Icon.SQUARE_TERMINAL);
        assertIcon(WorkspaceTabGraphics.editor(), LucideIconView.Icon.FILE_TEXT);
    }

    @Test
    void remoteGraphicsKeepTheStatusDotBeforeTheirDistinctIcon() {
        Region terminalState = new Region();
        HBox terminal = (HBox) WorkspaceTabGraphics.remoteTerminal(terminalState);
        assertSame(terminalState, terminal.getChildren().getFirst());
        assertIcon(terminal.getChildren().get(1), LucideIconView.Icon.MONITOR_UP);

        Region fileState = new Region();
        HBox files = (HBox) WorkspaceTabGraphics.remoteFiles(fileState);
        assertSame(fileState, files.getChildren().getFirst());
        assertIcon(files.getChildren().get(1), LucideIconView.Icon.FOLDER_UP);
    }

    private static void assertIcon(Node node, LucideIconView.Icon expected) {
        LucideIconView icon = (LucideIconView) node;
        assertEquals(expected, icon.getIcon());
        assertEquals(WorkspaceTabGraphics.ICON_SIZE, icon.getPrefWidth());
        assertEquals(WorkspaceTabGraphics.ICON_SIZE, icon.getPrefHeight());
        assertTrue(icon.getScaleX() < 1.0);
        assertEquals(icon.getScaleX(), icon.getScaleY());
    }
}
