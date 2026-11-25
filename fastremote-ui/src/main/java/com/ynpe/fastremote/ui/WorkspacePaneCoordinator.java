// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

/** Owns top-level workspace tabs and their recursively split draggable content panes. */
final class WorkspacePaneCoordinator {
    private static final double DRAG_ICON_HEIGHT = 26.0;
    static final DataFormat PANE_FORMAT = new DataFormat("application/x-fastremote-workspace-pane");
    static final DataFormat WORKSPACE_FORMAT =
            new DataFormat("application/x-fastremote-workspace-tab");
    private final TabPane tabs;
    private final Consumer<Tab> closePane;
    private final Predicate<Tab> confirmWorkspaceClose;
    private final Consumer<Tab> installTopLevelContextMenu;
    private final Function<Tab, Optional<String>> paneHostName;
    private final Runnable changed;
    private final Map<Tab, Group> groupsByOuter = new IdentityHashMap<>();
    private final Map<Tab, Group> groupsByPane = new IdentityHashMap<>();
    private final Map<String, Tab> panesByDragId = new LinkedHashMap<>();
    private final Map<String, Tab> workspacesByDragId = new LinkedHashMap<>();
    private Tab draggingPane;
    private Tab draggingWorkspace;
    private Node tabDropPreviewCell;
    private Group tabDropPreviewGroup;
    private TabDropZone tabDropPreviewZone;

    enum TabDropZone {
        BEFORE,
        MERGE,
        AFTER
    }

    WorkspacePaneCoordinator(TabPane tabs, Consumer<Tab> closePane,
            Consumer<Tab> installTopLevelContextMenu, Runnable changed) {
        this(tabs, closePane, ignored -> true, installTopLevelContextMenu, changed);
    }

    WorkspacePaneCoordinator(TabPane tabs, Consumer<Tab> closePane,
            Predicate<Tab> confirmWorkspaceClose,
            Consumer<Tab> installTopLevelContextMenu, Runnable changed) {
        this(tabs, closePane, confirmWorkspaceClose, installTopLevelContextMenu,
                ignored -> Optional.empty(), changed);
    }

    WorkspacePaneCoordinator(TabPane tabs, Consumer<Tab> closePane,
            Predicate<Tab> confirmWorkspaceClose,
            Consumer<Tab> installTopLevelContextMenu,
            Function<Tab, Optional<String>> paneHostName, Runnable changed) {
        this.tabs = Objects.requireNonNull(tabs, "tabs");
        this.closePane = Objects.requireNonNull(closePane, "closePane");
        this.confirmWorkspaceClose = Objects.requireNonNull(confirmWorkspaceClose,
                "confirmWorkspaceClose");
        this.installTopLevelContextMenu = Objects.requireNonNull(installTopLevelContextMenu,
                "installTopLevelContextMenu");
        this.paneHostName = Objects.requireNonNull(paneHostName, "paneHostName");
        this.changed = Objects.requireNonNull(changed, "changed");
        installTopLevelTabDropTargets();
    }

    void addPane(Tab pane) {
        addPane(pane, null, true);
    }

    void addPane(Tab pane, Tab insertionAnchor, boolean activate) {
        Objects.requireNonNull(pane, "pane");
        if (groupsByPane.containsKey(pane)) {
            if (activate) selectPane(pane);
            return;
        }
        Group group = new Group(pane);
        groupsByOuter.put(group.outer, group);
        groupsByPane.put(pane, group);
        int anchorIndex = insertionAnchor == null
                ? -1 : tabs.getTabs().indexOf(topLevelFor(insertionAnchor));
        int insertionIndex = anchorIndex < 0 ? tabs.getTabs().size() : anchorIndex + 1;
        tabs.getTabs().add(insertionIndex, group.outer);
        installTopLevelContextMenu.accept(group.outer);
        if (activate) selectPane(pane);
        changed.run();
    }

    boolean addPaneBeside(Tab pane, Tab targetPane, WorkspaceLayoutModel.DropEdge edge,
            boolean activate) {
        Objects.requireNonNull(pane, "pane");
        Group target = groupsByPane.get(targetPane);
        if (target == null || groupsByPane.containsKey(pane)) return false;
        PaneView view = new PaneView(pane);
        target.attach(pane, view, targetPane, Objects.requireNonNull(edge, "edge"));
        groupsByPane.put(pane, target);
        if (activate) target.setActive(pane);
        target.render();
        if (activate) tabs.getSelectionModel().select(target.outer);
        changed.run();
        return true;
    }

    boolean containsPane(Tab pane) {
        return groupsByPane.containsKey(pane);
    }

    boolean isWorkspaceTab(Tab tab) {
        return groupsByOuter.containsKey(tab);
    }

    Tab selectedPane() {
        Tab selected = tabs.getSelectionModel().getSelectedItem();
        Group group = groupsByOuter.get(selected);
        return group == null ? selected : group.active;
    }

    Tab activePane(Tab topLevel) {
        Group group = groupsByOuter.get(topLevelFor(topLevel));
        return group == null ? topLevel : group.active;
    }

    void selectPane(Tab pane) {
        Group group = groupsByPane.get(pane);
        if (group == null) {
            if (pane != null && tabs.getTabs().contains(pane)) tabs.getSelectionModel().select(pane);
            return;
        }
        group.setActive(pane);
        tabs.getSelectionModel().select(group.outer);
    }

    Tab topLevelFor(Tab tab) {
        Group paneGroup = groupsByPane.get(tab);
        if (paneGroup != null) return paneGroup.outer;
        return tab;
    }

    List<Tab> panes(Tab topLevel) {
        Group group = groupsByOuter.get(topLevelFor(topLevel));
        return group == null ? List.of(topLevel) : group.layout.itemsInVisualOrder();
    }

    List<Tab> logicalTabsInDisplayOrder() {
        List<Tab> result = new ArrayList<>();
        for (Tab tab : tabs.getTabs()) {
            Group group = groupsByOuter.get(tab);
            if (group == null) result.add(tab);
            else result.addAll(group.layout.itemsInVisualOrder());
        }
        return List.copyOf(result);
    }

    int paneCount(Tab topLevel) {
        Group group = groupsByOuter.get(topLevelFor(topLevel));
        return group == null ? 1 : group.layout.size();
    }

    boolean expandWorkspaceToTabs(Tab topLevel) {
        Group source = groupsByOuter.get(topLevelFor(topLevel));
        if (source == null || source.layout.size() <= 1) return false;
        List<Tab> panes = List.copyOf(source.layout.itemsInVisualOrder());
        Tab active = source.active;
        int insertionIndex = tabs.getTabs().indexOf(source.outer);
        tabs.getTabs().remove(source.outer);
        groupsByOuter.remove(source.outer);
        source.outer.setOnCloseRequest(null);
        for (Tab pane : panes) {
            PaneView view = source.detach(pane);
            Group target = new Group(pane, view);
            groupsByPane.put(pane, target);
            groupsByOuter.put(target.outer, target);
            tabs.getTabs().add(insertionIndex++, target.outer);
            installTopLevelContextMenu.accept(target.outer);
        }
        selectPane(active);
        changed.run();
        return true;
    }

    boolean removePane(Tab pane) {
        Group group = groupsByPane.remove(pane);
        if (group == null) return false;
        group.remove(pane);
        if (group.layout.isEmpty()) {
            groupsByOuter.remove(group.outer);
            group.outer.setOnCloseRequest(null);
            tabs.getTabs().remove(group.outer);
        } else {
            group.render();
        }
        changed.run();
        return true;
    }

    void updatePaneContent(Tab pane, Node content) {
        pane.setContent(content);
        Group group = groupsByPane.get(pane);
        if (group != null) group.views.get(pane).root.setCenter(content);
    }

    void refreshPaneHeader(Tab pane) {
        Group group = groupsByPane.get(pane);
        if (group != null) group.refreshHeader();
    }

    void requestCloseWorkspace(Tab topLevel) {
        Tab workspace = topLevelFor(topLevel);
        if (!confirmWorkspaceClose.test(workspace)) return;
        closeWorkspace(workspace);
    }

    void closeWorkspace(Tab topLevel) {
        Tab workspace = topLevelFor(topLevel);
        Group group = groupsByOuter.get(workspace);
        if (group == null) {
            closePane.accept(workspace);
            return;
        }
        for (Tab pane : List.copyOf(group.layout.itemsInVisualOrder())) closePane.accept(pane);
    }

    boolean mergeWorkspaceInto(Tab sourceTopLevel, Tab targetTopLevel,
            WorkspaceLayoutModel.DropEdge edge) {
        Group source = groupsByOuter.get(topLevelFor(sourceTopLevel));
        Group target = groupsByOuter.get(topLevelFor(targetTopLevel));
        if (source == null || target == null || source == target) return false;
        Tab targetPane = target.active;
        for (Tab pane : List.copyOf(source.layout.itemsInVisualOrder())) {
            transfer(pane, targetPane, edge);
            targetPane = pane;
            edge = WorkspaceLayoutModel.DropEdge.BOTTOM;
        }
        return true;
    }

    boolean dropWorkspaceOnTab(Tab sourceTopLevel, Tab targetTopLevel, TabDropZone zone) {
        Group source = groupsByOuter.get(topLevelFor(sourceTopLevel));
        Group target = groupsByOuter.get(topLevelFor(targetTopLevel));
        if (source == null || target == null || source == target) return false;
        return switch (Objects.requireNonNull(zone, "zone")) {
            case BEFORE -> reorderWorkspace(source.outer, target.outer, false);
            case AFTER -> reorderWorkspace(source.outer, target.outer, true);
            case MERGE -> mergeWorkspaceInto(source.outer, target.outer,
                    WorkspaceLayoutModel.DropEdge.RIGHT);
        };
    }

    boolean dropPaneOnTab(Tab pane, Tab targetTopLevel, TabDropZone zone) {
        Group source = groupsByPane.get(pane);
        Group target = groupsByOuter.get(topLevelFor(targetTopLevel));
        if (source == null || target == null) return false;
        return switch (Objects.requireNonNull(zone, "zone")) {
            case MERGE -> source != target
                    && transfer(pane, target.active, WorkspaceLayoutModel.DropEdge.RIGHT);
            case BEFORE, AFTER -> {
                if (source.layout.size() <= 1) yield false;
                int targetIndex = tabs.getTabs().indexOf(target.outer);
                if (targetIndex < 0) yield false;
                int insertionIndex = targetIndex + (zone == TabDropZone.AFTER ? 1 : 0);
                yield movePaneToNewWorkspace(pane, insertionIndex);
            }
        };
    }

    static TabDropZone tabDropZone(double x, double width) {
        double normalized = x / Math.max(1.0, width);
        if (normalized < 0.25) return TabDropZone.BEFORE;
        if (normalized > 0.75) return TabDropZone.AFTER;
        return TabDropZone.MERGE;
    }

    private boolean reorderWorkspace(Tab source, Tab target, boolean after) {
        if (source == target || !tabs.getTabs().contains(source) || !tabs.getTabs().contains(target)) {
            return false;
        }
        tabs.getTabs().remove(source);
        int targetIndex = tabs.getTabs().indexOf(target);
        tabs.getTabs().add(targetIndex + (after ? 1 : 0), source);
        tabs.getSelectionModel().select(source);
        changed.run();
        return true;
    }

    private boolean transfer(Tab pane, Tab targetPane, WorkspaceLayoutModel.DropEdge edge) {
        Group source = groupsByPane.get(pane);
        Group target = groupsByPane.get(targetPane);
        if (source == null || target == null) return false;
        if (source == target) {
            if (source.layout.move(pane, targetPane, edge)) {
                source.setActive(pane);
                source.render();
                changed.run();
                return true;
            }
            return false;
        }
        PaneView view = source.detach(pane);
        groupsByPane.remove(pane);
        if (source.layout.isEmpty()) {
            groupsByOuter.remove(source.outer);
            source.outer.setOnCloseRequest(null);
            tabs.getTabs().remove(source.outer);
        } else {
            source.render();
        }
        target.attach(pane, view, targetPane, edge);
        groupsByPane.put(pane, target);
        target.setActive(pane);
        target.render();
        tabs.getSelectionModel().select(target.outer);
        changed.run();
        return true;
    }

    boolean movePaneToNewWorkspace(Tab pane, int insertionIndex) {
        Group source = groupsByPane.get(pane);
        if (source == null) return false;
        if (source.layout.size() == 1) {
            tabs.getTabs().remove(source.outer);
            tabs.getTabs().add(Math.clamp(insertionIndex, 0, tabs.getTabs().size()), source.outer);
            tabs.getSelectionModel().select(source.outer);
            changed.run();
            return true;
        }
        PaneView view = source.detach(pane);
        source.render();
        Group target = new Group(pane, view);
        groupsByPane.put(pane, target);
        groupsByOuter.put(target.outer, target);
        tabs.getTabs().add(Math.clamp(insertionIndex, 0, tabs.getTabs().size()), target.outer);
        installTopLevelContextMenu.accept(target.outer);
        selectPane(pane);
        changed.run();
        return true;
    }

    private void installTopLevelTabDropTargets() {
        tabs.addEventFilter(DragEvent.DRAG_OVER, event -> {
            if (!(event.getTarget() instanceof Node target)) return;
            Node tabCell = topLevelTabCell(target);
            Group targetGroup = groupForTabCell(tabCell);
            if (targetGroup != null) {
                javafx.geometry.Point2D local = tabCell.sceneToLocal(
                        event.getSceneX(), event.getSceneY());
                TabDropZone zone = tabDropZone(local.getX(), tabCell.getBoundsInLocal().getWidth());
                if (targetGroup.canDropOnTab(event, zone)) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    showTabDropPreview(tabCell, targetGroup, zone);
                } else {
                    clearTabDropPreview();
                }
                if (isWorkspaceDrag(event) || draggedPane(event) != null) event.consume();
                return;
            }
            clearTabDropPreview();
            if (!isTabHeaderBlank(target)) return;
            Tab workspace = draggedWorkspace(event);
            if (workspace != null && isAfterLastTab(event.getSceneX())) {
                Node lastCell = lastTopLevelTabCell();
                if (lastCell != null && tabs.getTabs().getLast() != workspace) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    showLastTabDropPreview(lastCell);
                }
                event.consume();
                return;
            }
            Tab pane = draggedPane(event);
            if (pane != null) {
                event.acceptTransferModes(TransferMode.MOVE);
                tabs.getStyleClass().add("workspace-tabs-drop-new");
                event.consume();
            }
        });
        tabs.addEventFilter(DragEvent.DRAG_EXITED, event -> {
            clearTabDropPreview();
            groupsByOuter.values().forEach(group -> group.hoverSelect.stop());
            tabs.getStyleClass().remove("workspace-tabs-drop-new");
        });
        tabs.addEventFilter(DragEvent.DRAG_DROPPED, event -> {
            if (!(event.getTarget() instanceof Node target)) return;
            Node tabCell = topLevelTabCell(target);
            Group targetGroup = groupForTabCell(tabCell);
            if (targetGroup != null) {
                javafx.geometry.Point2D local = tabCell.sceneToLocal(
                        event.getSceneX(), event.getSceneY());
                TabDropZone zone = tabDropZone(local.getX(), tabCell.getBoundsInLocal().getWidth());
                boolean recognized = isWorkspaceDrag(event) || draggedPane(event) != null;
                boolean completed = targetGroup.dropOnTab(event, zone);
                clearTabDropPreview();
                event.setDropCompleted(completed);
                if (recognized) event.consume();
                return;
            }
            if (!isTabHeaderBlank(target)) return;
            Tab workspace = draggedWorkspace(event);
            if (workspace != null && isAfterLastTab(event.getSceneX())) {
                boolean completed = moveWorkspaceToEnd(workspace);
                clearTabDropPreview();
                event.setDropCompleted(completed);
                event.consume();
                return;
            }
            Tab pane = draggedPane(event);
            if (pane == null) return;
            boolean completed = movePaneToNewWorkspace(pane, tabs.getTabs().size());
            tabs.getStyleClass().remove("workspace-tabs-drop-new");
            event.setDropCompleted(completed);
            event.consume();
        });
    }

    boolean moveWorkspaceToEnd(Tab workspace) {
        Group group = groupsByOuter.get(topLevelFor(workspace));
        if (group == null || tabs.getTabs().getLast() == group.outer) return false;
        tabs.getTabs().remove(group.outer);
        tabs.getTabs().add(group.outer);
        tabs.getSelectionModel().select(group.outer);
        changed.run();
        return true;
    }

    private Node topLevelTabCell(Node target) {
        Node candidate = null;
        for (Node node = target; node != null; node = node.getParent()) {
            if (candidate == null && node.getStyleClass().contains("tab")) candidate = node;
            if (node == tabs) return candidate;
            if (node instanceof TabPane) return null;
        }
        return null;
    }

    private Group groupForTabCell(Node tabCell) {
        if (tabCell == null) return null;
        for (Group group : groupsByOuter.values()) {
            for (Node node = group.tabHeader; node != null; node = node.getParent()) {
                if (node == tabCell) return group;
            }
        }
        return null;
    }

    private Node lastTopLevelTabCell() {
        if (tabs.getTabs().isEmpty()) return null;
        return tabCellFor(groupsByOuter.get(tabs.getTabs().getLast()));
    }

    private Node tabCellFor(Group group) {
        if (group == null) return null;
        for (Node node = group.tabHeader; node != null && node != tabs; node = node.getParent()) {
            if (node.getStyleClass().contains("tab")) return node;
        }
        return null;
    }

    private boolean isAfterLastTab(double sceneX) {
        Node lastCell = lastTopLevelTabCell();
        if (lastCell == null) return false;
        return sceneX >= lastCell.localToScene(lastCell.getBoundsInLocal()).getMaxX();
    }

    private void showTabDropPreview(Node tabCell, Group group, TabDropZone zone) {
        if (tabDropPreviewCell == tabCell && tabDropPreviewGroup == group
                && tabDropPreviewZone == zone) return;
        clearTabDropPreview();
        tabDropPreviewCell = tabCell;
        tabDropPreviewGroup = group;
        tabDropPreviewZone = zone;
        tabCell.getStyleClass().add("workspace-tab-cell-drop-"
                + zone.name().toLowerCase(java.util.Locale.ROOT));
        if (zone == TabDropZone.MERGE) group.hoverSelect.playFromStart();
    }

    private void showLastTabDropPreview(Node lastCell) {
        if (tabDropPreviewCell == lastCell && tabDropPreviewGroup == null
                && tabDropPreviewZone == TabDropZone.AFTER) return;
        clearTabDropPreview();
        tabDropPreviewCell = lastCell;
        tabDropPreviewZone = TabDropZone.AFTER;
        lastCell.getStyleClass().add("workspace-tab-cell-drop-after");
    }

    private void clearTabDropPreview() {
        if (tabDropPreviewCell != null) {
            tabDropPreviewCell.getStyleClass().removeAll(
                    "workspace-tab-cell-drop-before",
                    "workspace-tab-cell-drop-merge",
                    "workspace-tab-cell-drop-after");
        }
        if (tabDropPreviewGroup != null) tabDropPreviewGroup.hoverSelect.stop();
        tabDropPreviewCell = null;
        tabDropPreviewGroup = null;
        tabDropPreviewZone = null;
    }

    private void installDragView(Dragboard board, Tab pane) {
        Node graphic = pane.getGraphic();
        if (graphic == null) return;
        double height = graphic.getBoundsInLocal().getHeight();
        if (height <= 0.0) return;
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        double scale = DRAG_ICON_HEIGHT / height;
        parameters.setTransform(new Scale(scale, scale));
        Image image = graphic.snapshot(parameters, null);
        board.setDragView(image, image.getWidth() / 2.0, image.getHeight() / 2.0);
    }

    private boolean isTabHeaderBlank(Node target) {
        boolean insideHeader = false;
        for (Node node = target; node != null; node = node.getParent()) {
            if (node.getStyleClass().contains("tab")) return false;
            if (node.getStyleClass().contains("control-buttons-tab")
                    || node.getStyleClass().contains("tab-down-button")) return false;
            if (node.getStyleClass().contains("tab-header-area")) insideHeader = true;
            if (node == tabs) break;
        }
        return insideHeader;
    }

    private final class Group {
        private final Tab outer = new Tab();
        private final HBox tabHeader = new HBox(4.0);
        private final Label tabCount = new Label();
        private final Label tabTitle = new Label();
        private final BorderPane layoutHost = new BorderPane();
        private final WorkspaceLayoutModel<Tab> layout = new WorkspaceLayoutModel<>();
        private final Map<Tab, PaneView> views = new IdentityHashMap<>();
        private final PauseTransition hoverSelect = new PauseTransition(Duration.millis(350));
        private Node layoutTree;
        private Tab active;

        private Group(Tab first) {
            this(first, null);
        }

        private Group(Tab first, PaneView existingView) {
            active = first;
            layout.addInitial(first);
            views.put(first, existingView == null ? new PaneView(first) : existingView);
            layoutHost.getStyleClass().add("multi-pane-workspace");
            tabHeader.setAlignment(Pos.CENTER_LEFT);
            tabHeader.setMinWidth(0.0);
            tabHeader.getStyleClass().add("workspace-tab-drop-target");
            tabCount.getStyleClass().addAll("workspace-tab-title", "workspace-tab-count");
            tabCount.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            tabTitle.getStyleClass().add("workspace-tab-title");
            tabTitle.setMinWidth(0.0);
            tabTitle.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
            HBox.setHgrow(tabTitle, javafx.scene.layout.Priority.ALWAYS);
            outer.getStyleClass().add("workspace-container-tab");
            outer.setGraphic(tabHeader);
            outer.setContent(layoutHost);
            outer.setClosable(true);
            outer.setOnCloseRequest(event -> {
                event.consume();
                requestCloseWorkspace(outer);
            });
            hoverSelect.setOnFinished(ignored -> {
                boolean acceptsPane = draggingPane != null
                        && groupsByPane.get(draggingPane) != this;
                Group sourceWorkspace = groupsByOuter.get(draggingWorkspace);
                boolean acceptsWorkspace = sourceWorkspace != null && sourceWorkspace != this;
                if (acceptsPane || acceptsWorkspace) tabs.getSelectionModel().select(outer);
            });
            installTabDragAndDrop();
            render();
        }

        private void installTabDragAndDrop() {
            tabHeader.setOnDragDetected(event -> {
                draggingWorkspace = outer;
                String id = UUID.randomUUID().toString();
                workspacesByDragId.clear();
                workspacesByDragId.put(id, outer);
                ClipboardContent content = new ClipboardContent();
                content.put(WORKSPACE_FORMAT, id);
                Dragboard board = tabHeader.startDragAndDrop(TransferMode.MOVE);
                installDragView(board, active);
                board.setContent(content);
                event.consume();
            });
            tabHeader.setOnDragDone(event -> {
                draggingWorkspace = null;
                workspacesByDragId.clear();
                WorkspacePaneCoordinator.this.clearTabDropPreview();
                event.consume();
            });
        }

        private boolean canDropOnTab(DragEvent event, TabDropZone zone) {
            Tab sourceOuter = draggedWorkspace(event);
            Group sourceGroup = groupsByOuter.get(sourceOuter);
            if (sourceGroup != null && sourceGroup != this) {
                return true;
            }
            Tab sourcePane = draggedPane(event);
            Group paneGroup = groupsByPane.get(sourcePane);
            if (sourcePane == null || paneGroup == null) return false;
            return switch (zone) {
                case MERGE -> paneGroup != this;
                case BEFORE, AFTER -> paneGroup.layout.size() > 1;
            };
        }

        private boolean dropOnTab(DragEvent event, TabDropZone zone) {
            Tab sourceOuter = draggedWorkspace(event);
            if (sourceOuter != null) return dropWorkspaceOnTab(sourceOuter, outer, zone);
            Tab sourcePane = draggedPane(event);
            if (sourcePane == null) return false;
            return dropPaneOnTab(sourcePane, outer, zone);
        }

        private void setActive(Tab pane) {
            if (!layout.contains(pane)) return;
            boolean activeChanged = active != pane;
            active = pane;
            boolean multiple = layout.size() > 1;
            views.forEach((item, view) -> {
                view.root.pseudoClassStateChanged(
                        javafx.css.PseudoClass.getPseudoClass("active"), multiple && item == pane);
                view.header.setManaged(multiple);
                view.header.setVisible(multiple);
            });
            refreshHeader();
            if (activeChanged) changed.run();
        }

        private void refreshHeader() {
            if (active == null) return;
            String title = active.getText();
            boolean multiple = layout.size() > 1;
            String count = "[" + layout.size() + "]";
            String displayTitle = multiple ? count + " " + title : title;
            outer.setText(displayTitle);
            tabCount.setText(count);
            tabTitle.setText(title);
            Node graphic = active.getGraphic();
            if (multiple && graphic != null) tabHeader.getChildren().setAll(tabCount, graphic, tabTitle);
            else if (multiple) tabHeader.getChildren().setAll(tabCount, tabTitle);
            else if (graphic != null) tabHeader.getChildren().setAll(graphic, tabTitle);
            else tabHeader.getChildren().setAll(tabTitle);
            tabHeader.setAccessibleText(displayTitle);
            outer.setTooltip(active.getTooltip());
        }

        private void render() {
            layoutHost.setCenter(null);
            releaseSplitTree(layoutTree);
            layoutTree = buildLayoutNode(layout.root());
            layoutHost.setCenter(layoutTree);
            setActive(active == null || !layout.contains(active)
                    ? layout.itemsInVisualOrder().getFirst() : active);
        }

        private Node buildLayoutNode(WorkspaceLayoutModel.LayoutNode<Tab> node) {
            if (node instanceof WorkspaceLayoutModel.PaneNode<Tab> pane) {
                return views.get(pane.item()).container;
            }
            WorkspaceLayoutModel.SplitNode<Tab> branch =
                    (WorkspaceLayoutModel.SplitNode<Tab>) node;
            SplitPane split = new SplitPane();
            split.setOrientation(branch.axis() == WorkspaceLayoutModel.Axis.HORIZONTAL
                    ? Orientation.HORIZONTAL : Orientation.VERTICAL);
            split.getStyleClass().add("workspace-pane-split");
            branch.children().stream().map(this::buildLayoutNode).forEach(split.getItems()::add);
            double[] positions = new double[branch.children().size() - 1];
            for (int index = 0; index < positions.length; index++) {
                positions[index] = (index + 1.0) / branch.children().size();
            }
            split.setDividerPositions(positions);
            return split;
        }

        private void releaseSplitTree(Node node) {
            if (!(node instanceof SplitPane split)) return;
            List<Node> children = List.copyOf(split.getItems());
            split.getItems().clear();
            children.forEach(this::releaseSplitTree);
        }

        private void remove(Tab pane) {
            PaneView view = views.remove(pane);
            layout.remove(pane);
            if (pane == active) active = layout.isEmpty() ? null : layout.itemsInVisualOrder().getFirst();
        }

        private PaneView detach(Tab pane) {
            PaneView view = views.remove(pane);
            layout.remove(pane);
            if (pane == active) active = layout.isEmpty() ? null : layout.itemsInVisualOrder().getFirst();
            return view;
        }

        private void attach(Tab pane, PaneView view, Tab targetPane,
                WorkspaceLayoutModel.DropEdge edge) {
            views.put(pane, view);
            if (!layout.add(pane, targetPane, edge)) throw new IllegalStateException("Pane is already attached");
        }
    }

    private final class PaneView {
        private final Tab pane;
        private final BorderPane root = new BorderPane();
        private final StackPane container = new StackPane();
        private final Region dropPreview = new Region();
        private final Label title = new Label();
        private final HBox header;

        private PaneView(Tab pane) {
            this.pane = pane;
            title.textProperty().bind(pane.textProperty());
            pane.textProperty().addListener((ignored, before, after) -> {
                Group group = groupsByPane.get(pane);
                if (group != null && group.active == pane) group.refreshHeader();
            });
            pane.graphicProperty().addListener((ignored, before, after) -> {
                Group group = groupsByPane.get(pane);
                if (group != null && group.active == pane) group.refreshHeader();
            });
            title.getStyleClass().add("workspace-pane-title");
            title.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(title, Priority.ALWAYS);
            Label grip = new Label("⋮⋮");
            grip.getStyleClass().add("workspace-pane-grip");
            Button close = new Button("×");
            close.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("workspace.pane.close"));
            close.setTooltip(new Tooltip(com.ynpe.fastremote.i18n.Texts.text("workspace.pane.close")));
            close.getStyleClass().add("workspace-pane-close");
            close.disableProperty().bind(pane.closableProperty().not());
            close.setOnAction(event -> closePane.accept(pane));
            header = new HBox(6.0, grip, title, close);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setPadding(new Insets(0, 4, 0, 6));
            header.getStyleClass().add("workspace-pane-header");
            header.setOnDragDetected(event -> {
                draggingPane = pane;
                String id = UUID.randomUUID().toString();
                panesByDragId.clear();
                panesByDragId.put(id, pane);
                ClipboardContent content = new ClipboardContent();
                content.put(PANE_FORMAT, id);
                Dragboard board = header.startDragAndDrop(TransferMode.MOVE);
                installDragView(board, pane);
                board.setContent(content);
                event.consume();
            });
            header.setOnDragDone(event -> {
                draggingPane = null;
                panesByDragId.clear();
                clearPaneDropPreviews();
                event.consume();
            });
            root.setTop(header);
            root.setCenter(pane.getContent());
            root.getStyleClass().add("workspace-pane");
            dropPreview.getStyleClass().add("workspace-pane-drop-preview");
            dropPreview.setMouseTransparent(true);
            dropPreview.setVisible(false);
            container.getChildren().addAll(root, dropPreview);
            installPaneDropTarget();
            root.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
                Group group = groupsByPane.get(pane);
                if (group != null) group.setActive(pane);
            });
            MenuItem closeItem = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("workspace.pane.close"));
            closeItem.setOnAction(event -> closePane.accept(pane));
            MenuItem separate = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("workspace.pane.moveToNew"));
            separate.setOnAction(event -> moveToNewWorkspace(pane));
            MenuItem copyName = new MenuItem(com.ynpe.fastremote.i18n.Texts.text("workspace.pane.copyName"));
            copyName.setOnAction(event -> paneHostName.apply(pane).ifPresent(name -> {
                ClipboardContent content = new ClipboardContent();
                content.putString(name);
                Clipboard.getSystemClipboard().setContent(content);
            }));
            ContextMenu paneMenu = ApplicationContextMenus.create(closeItem, separate, copyName);
            ApplicationContextMenus.prepareBeforeShow(paneMenu, () -> {
                separate.setDisable(paneCount(pane) == 1);
                boolean hostPane = paneHostName.apply(pane).isPresent();
                copyName.setVisible(hostPane);
            });
            header.setOnContextMenuRequested(event -> {
                paneMenu.show(header, event.getScreenX(), event.getScreenY());
                event.consume();
            });
        }

        private void installPaneDropTarget() {
            container.setOnDragOver(event -> {
                Tab source = draggedPane(event);
                if (source == null) return;
                if (source == pane) {
                    hideDropPreview();
                } else {
                    WorkspaceLayoutModel.DropEdge edge = directionalDropEdge(event, container);
                    event.acceptTransferModes(TransferMode.MOVE);
                    showDropPreview(edge);
                }
                event.consume();
            });
            container.setOnDragExited(event -> {
                Bounds bounds = container.localToScene(container.getBoundsInLocal());
                if (!bounds.contains(event.getSceneX(), event.getSceneY())) hideDropPreview();
            });
            container.setOnDragDropped(event -> {
                Tab source = draggedPane(event);
                boolean completed = source != null && source != pane
                        && transfer(source, pane, directionalDropEdge(event, container));
                hideDropPreview();
                event.setDropCompleted(completed);
                if (source != null) event.consume();
            });
        }

        private void showDropPreview(WorkspaceLayoutModel.DropEdge edge) {
            dropPreview.setVisible(true);
            if (edge == WorkspaceLayoutModel.DropEdge.LEFT
                    || edge == WorkspaceLayoutModel.DropEdge.RIGHT) {
                dropPreview.setMaxSize(Math.max(1.0, container.getWidth() / 2.0), Double.MAX_VALUE);
                StackPane.setAlignment(dropPreview, edge == WorkspaceLayoutModel.DropEdge.LEFT
                        ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
            } else {
                dropPreview.setMaxSize(Double.MAX_VALUE, Math.max(1.0, container.getHeight() / 2.0));
                StackPane.setAlignment(dropPreview, edge == WorkspaceLayoutModel.DropEdge.TOP
                        ? Pos.TOP_CENTER : Pos.BOTTOM_CENTER);
            }
        }

        private void hideDropPreview() {
            dropPreview.setVisible(false);
        }
    }

    static WorkspaceLayoutModel.DropEdge directionalDropEdge(double x, double y,
            double width, double height) {
        double horizontal = x / Math.max(1.0, width) - 0.5;
        double vertical = y / Math.max(1.0, height) - 0.5;
        if (Math.abs(horizontal) > Math.abs(vertical)) {
            return horizontal < 0.0
                    ? WorkspaceLayoutModel.DropEdge.LEFT : WorkspaceLayoutModel.DropEdge.RIGHT;
        }
        return vertical < 0.0
                ? WorkspaceLayoutModel.DropEdge.TOP : WorkspaceLayoutModel.DropEdge.BOTTOM;
    }

    private WorkspaceLayoutModel.DropEdge directionalDropEdge(DragEvent event, Region target) {
        javafx.geometry.Point2D local = target.sceneToLocal(event.getSceneX(), event.getSceneY());
        return directionalDropEdge(local.getX(), local.getY(), target.getWidth(), target.getHeight());
    }

    private void clearPaneDropPreviews() {
        groupsByOuter.values().forEach(group ->
                group.views.values().forEach(PaneView::hideDropPreview));
    }

    private Tab draggedPane(DragEvent event) {
        Object id = event.getDragboard().getContent(PANE_FORMAT);
        return id instanceof String value ? panesByDragId.get(value) : null;
    }

    private Tab draggedWorkspace(DragEvent event) {
        Object id = event.getDragboard().getContent(WORKSPACE_FORMAT);
        return id instanceof String value ? workspacesByDragId.get(value) : null;
    }

    private boolean isWorkspaceDrag(DragEvent event) {
        return event.getDragboard().hasContent(WORKSPACE_FORMAT);
    }

    static boolean isWorkspaceDragPayload(Dragboard dragboard) {
        return dragboard != null && isWorkspaceDragPayload(dragboard.getContentTypes());
    }

    static boolean isWorkspaceDragPayload(Set<DataFormat> contentTypes) {
        return contentTypes != null
                && (contentTypes.contains(PANE_FORMAT) || contentTypes.contains(WORKSPACE_FORMAT));
    }

    private void moveToNewWorkspace(Tab pane) {
        Group source = groupsByPane.get(pane);
        if (source == null || source.layout.size() == 1) return;
        int sourceIndex = tabs.getTabs().indexOf(source.outer);
        movePaneToNewWorkspace(pane, sourceIndex + 1);
    }
}
