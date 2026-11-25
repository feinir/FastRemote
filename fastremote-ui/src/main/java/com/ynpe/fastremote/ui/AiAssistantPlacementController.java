// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;

/** Moves the one live AI view between a dock, a workspace tab, and a dialog. */
final class AiAssistantPlacementController {
    private final Node view;
    private final TabPane workspaceTabs;
    private final StackPane parkingHost = new StackPane();
    private final Runnable workspaceChanged;
    private Tab workspaceTab;
    private StackPane lastDockHost;
    private AiAssistantDialog dialog;
    private boolean returnToWorkspace;
    private boolean closingForShutdown;

    AiAssistantPlacementController(Node view, TabPane workspaceTabs, Runnable workspaceChanged) {
        this.view = Objects.requireNonNull(view, "view");
        this.workspaceTabs = Objects.requireNonNull(workspaceTabs, "workspaceTabs");
        this.workspaceChanged = Objects.requireNonNull(workspaceChanged, "workspaceChanged");
    }

    void openWorkspace() {
        if (isDialogOpen()) closeDialogToDock();
        if (workspaceTab != null && workspaceTabs.getTabs().contains(workspaceTab)) {
            workspaceTabs.getSelectionModel().select(workspaceTab);
            return;
        }
        detachView();
        workspaceTab = new Tab(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1b0049fcfd4163fe"), view);
        workspaceTab.setGraphic(WorkspaceTabGraphics.aiAssistant());
        workspaceTab.setOnCloseRequest(event -> {
            event.consume();
            closeWorkspace();
        });
        workspaceTabs.getTabs().add(workspaceTab);
        workspaceTabs.getSelectionModel().select(workspaceTab);
        setPlacementStyle("ai-placement-workspace");
        workspaceChanged.run();
    }

    void closeWorkspace() {
        if (workspaceTab == null) return;
        workspaceTab.setContent(null);
        workspaceTabs.getTabs().remove(workspaceTab);
        workspaceTab = null;
        detachView();
        parkingHost.getChildren().setAll(view);
        setPlacementStyle("ai-placement-parked");
        workspaceChanged.run();
    }

    boolean closeWorkspaceIfOpen() {
        if (!isWorkspaceOpen()) return false;
        closeWorkspace();
        return true;
    }

    void attachDock(StackPane host) {
        Objects.requireNonNull(host, "host");
        if (isDialogOpen()) closeDialogToDock();
        closeWorkspace();
        detachView();
        host.getChildren().setAll(view);
        lastDockHost = host;
        setPlacementStyle("ai-placement-dock");
    }

    void openDialog(Window owner) {
        Objects.requireNonNull(owner, "owner");
        if (isDialogOpen()) {
            dialog.focus();
            return;
        }
        returnToWorkspace = isWorkspaceOpen();
        if (returnToWorkspace) closeWorkspace();
        detachView();
        setPlacementStyle("ai-placement-dialog");
        dialog = new AiAssistantDialog(owner, this::onDialogHidden);
        dialog.show(view);
        workspaceChanged.run();
    }

    boolean isDialogOpen() { return dialog != null && dialog.isShowing(); }

    void focusDialog() {
        if (isDialogOpen()) dialog.focus();
    }

    void closeDialogToDock() {
        if (!isDialogOpen()) return;
        returnToWorkspace = false;
        dialog.close();
    }

    void closeDialogForShutdown() {
        if (!isDialogOpen()) return;
        closingForShutdown = true;
        dialog.close();
    }

    private void onDialogHidden() {
        dialog = null;
        if (closingForShutdown) {
            parkingHost.getChildren().setAll(view);
            setPlacementStyle("ai-placement-parked");
            return;
        }
        if (returnToWorkspace) {
            returnToWorkspace = false;
            openWorkspace();
        } else if (lastDockHost != null && lastDockHost.getChildren().isEmpty()) {
            attachDock(lastDockHost);
        } else {
            parkingHost.getChildren().setAll(view);
            setPlacementStyle("ai-placement-parked");
        }
        workspaceChanged.run();
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
        javafx.scene.control.Button button = new javafx.scene.control.Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.508fc0ef8a3e70e0"));
        button.setOnAction(event -> focusWorkspace.run());
        javafx.scene.control.Label label = new javafx.scene.control.Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d3844471c7d6b68c"));
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
        view.getStyleClass().removeAll("ai-placement-dock", "ai-placement-workspace", "ai-placement-dialog",
                "ai-placement-parked");
        view.getStyleClass().add(style);
    }
}
