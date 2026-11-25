// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

/** Moves the one live diagnostic log view between its dock and a singleton workspace tab. */
final class DiagnosticLogPlacementController {
    private final Node view;
    private final TabPane workspaceTabs;
    private final StackPane parkingHost = new StackPane();
    private final Runnable placementChanged;
    private Tab workspaceTab;

    DiagnosticLogPlacementController(Node view, TabPane workspaceTabs, Runnable placementChanged) {
        this.view = Objects.requireNonNull(view, "view");
        this.workspaceTabs = Objects.requireNonNull(workspaceTabs, "workspaceTabs");
        this.placementChanged = Objects.requireNonNull(placementChanged, "placementChanged");
    }

    void openWorkspace() {
        if (isWorkspaceOpen()) {
            workspaceTabs.getSelectionModel().select(workspaceTab);
            return;
        }
        detachView();
        workspaceTab = new Tab(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bb1d19b85634606e"), view);
        workspaceTab.setGraphic(WorkspaceTabGraphics.diagnosticLog());
        workspaceTab.setOnCloseRequest(event -> {
            event.consume();
            closeWorkspace();
        });
        workspaceTabs.getTabs().add(workspaceTab);
        workspaceTabs.getSelectionModel().select(workspaceTab);
        setPlacementStyle("diagnostic-log-placement-workspace");
        placementChanged.run();
    }

    void closeWorkspace() {
        if (workspaceTab == null) return;
        workspaceTab.setContent(null);
        workspaceTabs.getTabs().remove(workspaceTab);
        workspaceTab = null;
        detachView();
        parkingHost.getChildren().setAll(view);
        setPlacementStyle("diagnostic-log-placement-parked");
        placementChanged.run();
    }

    boolean closeWorkspaceIfOpen() {
        if (!isWorkspaceOpen()) return false;
        closeWorkspace();
        return true;
    }

    void attachDock(StackPane host) {
        Objects.requireNonNull(host, "host");
        closeWorkspace();
        detachView();
        host.getChildren().setAll(view);
        setPlacementStyle("diagnostic-log-placement-dock");
    }

    boolean isWorkspaceOpen() {
        return workspaceTab != null && workspaceTabs.getTabs().contains(workspaceTab);
    }

    boolean isWorkspaceTab(Tab tab) {
        return tab != null && tab == workspaceTab;
    }

    Optional<Tab> workspaceTab() {
        return Optional.ofNullable(workspaceTab);
    }

    Node workspacePlaceholder(Runnable focusWorkspace) {
        javafx.scene.control.Button button = new javafx.scene.control.Button(
                com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.focusWorkspace"));
        button.setOnAction(event -> focusWorkspace.run());
        javafx.scene.control.Label label = new javafx.scene.control.Label(
                com.ynpe.fastremote.i18n.Texts.text("diagnostic.log.workspaceOpen"));
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(9, label, button);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        box.getStyleClass().add("ai-workspace-placeholder");
        return box;
    }

    private void detachView() {
        if (workspaceTab != null && workspaceTab.getContent() == view) workspaceTab.setContent(null);
        if (view.getParent() instanceof Pane pane) pane.getChildren().remove(view);
        parkingHost.getChildren().remove(view);
    }

    private void setPlacementStyle(String style) {
        view.getStyleClass().removeAll("diagnostic-log-placement-dock",
                "diagnostic-log-placement-workspace", "diagnostic-log-placement-parked");
        view.getStyleClass().add(style);
    }
}
