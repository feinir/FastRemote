// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.ai.AiChatSession;
import com.ynpe.fastremote.i18n.Texts;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Window;

/** Conversation menu and bounded bulk selection, independent of request execution. */
final class AiSessionListController {
    interface Actions {
        CompletionStage<?> rename(UUID id, String title);
        CompletionStage<?> pin(List<UUID> ids, boolean pinned);
        CompletionStage<?> delete(List<UUID> ids);
        boolean running(UUID id);
        Window owner();
        void failed(Throwable error);
    }
    private final ListView<AiChatSession> list;
    private final VBox footer;
    private final Actions actions;
    private final Set<UUID> checked = new LinkedHashSet<>();
    private final CheckBox all = new CheckBox(text("selectAll"));
    private final Button pin = button("pin", LucideIconView.Icon.PIN);
    private final Button delete = button("delete", LucideIconView.Icon.TRASH_2);
    private boolean batch;
    private boolean busy;
    private UUID active;
    private List<AiChatSession> sessions = List.of();
    private String query = "";

    AiSessionListController(ListView<AiChatSession> list, VBox footer, Actions actions) {
        this.list = list; this.footer = footer; this.actions = actions;
        Button cancel = new Button(text("cancel"));
        cancel.getStyleClass().add("ai-sidebar-action");
        cancel.setOnAction(event -> exitBatch());
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox selection = new HBox(6, all, spacer, cancel); selection.setAlignment(Pos.CENTER_LEFT);
        HBox commands = new HBox(8, pin, delete);
        for (Button button : List.of(pin, delete)) { button.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(button, Priority.ALWAYS); }
        footer.getChildren().setAll(selection, commands);
        all.setOnAction(event -> {
            checked.clear(); if (all.isSelected()) list.getItems().forEach(s -> checked.add(s.id()));
            refresh();
        });
        pin.setOnAction(event -> perform(actions.pin(List.copyOf(checked), true)));
        delete.setOnAction(event -> confirmDelete(List.copyOf(checked)));
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(AiChatSession session, boolean empty) {
                super.updateItem(session, empty); setText(null);
                setGraphic(empty || session == null ? null : row(session));
                setTooltip(null);
                setAccessibleText(empty || session == null ? null : session.title());
                if (getGraphic() instanceof Region region) region.prefWidthProperty().bind(widthProperty().subtract(8));
            }
        });
        list.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (batch) event.consume();
        });
        list.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (batch && (event.getCode() == javafx.scene.input.KeyCode.SPACE
                    || event.getCode() == javafx.scene.input.KeyCode.ENTER)) {
                var item = list.getFocusModel().getFocusedItem();
                if (item != null) toggle(item.id());
                event.consume();
            }
        });
        refresh();
    }

    private Node row(AiChatSession session) {
        Label title = new AiSessionTitle(MainWindowController.aiSessionDisplayTitle(session),
                MainWindowController.aiHistoryAge(session.updatedAt(), Instant.now()));
        title.getStyleClass().add("ai-history-title");
        title.setMinWidth(0); title.setMaxWidth(Double.MAX_VALUE); title.setPrefHeight(36);
        HBox row = new HBox(5); row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("ai-history-row");
        if (session.id().equals(active)) row.getStyleClass().add("ai-history-row-active");
        if (batch) {
            CheckBox check = new CheckBox(); check.setMouseTransparent(true);
            check.setFocusTraversable(false); check.setSelected(checked.contains(session.id()));
            check.setAccessibleText(session.title()); row.getChildren().add(check);
            row.setOnMouseClicked(event -> { toggle(session.id()); event.consume(); });
        }
        row.getChildren().add(title); HBox.setHgrow(title, Priority.ALWAYS);
        if (session.pinned()) {
            var indicator = new LucideIconView(LucideIconView.Icon.PIN);
            indicator.getStyleClass().add("tool-window-icon");
            var host = new StackPane(indicator); Tooltip.install(host, new Tooltip(text("pinned")));
            row.getChildren().add(host);
        }
        if (!batch) {
            Button more = button("more", LucideIconView.Icon.ELLIPSIS);
            more.setTooltip(null);
            more.getStyleClass().add("ai-history-action-button");
            ContextMenu menu = menu(session);
            more.addEventFilter(MouseEvent.MOUSE_PRESSED, MouseEvent::consume);
            more.setOnAction(event -> {
                if (menu.isShowing()) menu.hide();
                else menu.show(more, javafx.geometry.Side.BOTTOM, 0, 0);
            });
            more.setOnMouseClicked(event -> {
                if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    more.fire(); event.consume();
                }
            });
            // Popup CSS inherits from its owner; use the same button for both entry points.
            row.setOnContextMenuRequested(event -> {
                menu.hide();
                menu.show(more, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            row.getChildren().add(more);
        }
        return row;
    }

    private ContextMenu menu(AiChatSession session) {
        ContextMenu menu = ApplicationContextMenus.create();
        MenuItem bulk = item("batch", LucideIconView.Icon.LIST_CHECKS,
                () -> { menu.hide(); enterBatch(session.id()); });
        MenuItem rename = item("rename", LucideIconView.Icon.PENCIL,
                () -> ApplicationContextMenus.runAfterHide(menu, () -> rename(session)));
        MenuItem pinItem = item(session.pinned() ? "unpin" : "pin", LucideIconView.Icon.PIN,
                () -> perform(actions.pin(List.of(session.id()), !session.pinned())));
        MenuItem remove = item("delete", LucideIconView.Icon.TRASH_2,
                () -> ApplicationContextMenus.runAfterHide(menu, () -> confirmDelete(List.of(session.id()))));
        remove.setDisable(actions.running(session.id())); remove.getStyleClass().add("ai-session-delete-menu");
        menu.getItems().setAll(bulk, rename, pinItem, new SeparatorMenuItem(), remove);
        ApplicationContextMenus.prepareBeforeShow(menu, () -> remove.setDisable(actions.running(session.id())));
        return menu;
    }

    void sessions(List<AiChatSession> sessions, UUID active) {
        this.sessions = List.copyOf(sessions); this.active = active; applyFilter();
    }
    void filter(String text) { query = text.strip().toLowerCase(Locale.ROOT); applyFilter(); }
    private void applyFilter() {
        var visible = sessions.stream().filter(s -> MainWindowController.aiSessionDisplayTitle(s)
                        .toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(AiChatSession::pinned).reversed()
                        .thenComparing(AiChatSession::updatedAt, Comparator.reverseOrder())).toList();
        if (!list.getItems().equals(visible)) list.getItems().setAll(visible);
        update(active);
    }

    void enterBatch(UUID id) { batch = true; checked.clear(); checked.add(id); refresh(); }
    void exitBatch() { if (busy) return; batch = false; checked.clear(); refresh(); }
    boolean batch() { return batch; }
    Set<UUID> checked() { return Set.copyOf(checked); }
    void toggle(UUID id) { if (busy) return; if (!checked.remove(id)) checked.add(id); refresh(); }
    void update(UUID id) {
        active = id;
        Set<UUID> existing = new HashSet<>(); list.getItems().forEach(s -> existing.add(s.id()));
        checked.retainAll(existing); refresh();
    }
    private void refresh() {
        footer.setVisible(batch); footer.setManaged(batch); footer.setDisable(busy);
        all.setSelected(!list.getItems().isEmpty() && checked.size() == list.getItems().size());
        all.setIndeterminate(!checked.isEmpty() && checked.size() != list.getItems().size());
        pin.setDisable(checked.isEmpty() || busy);
        delete.setDisable(checked.isEmpty() || busy || checked.stream().anyMatch(actions::running));
        list.refresh();
    }
    private void rename(AiChatSession session) {
        TextInputDialog dialog = new TextInputDialog(MainWindowController.aiSessionDisplayTitle(session));
        dialog.initOwner(actions.owner()); dialog.setTitle(text("rename")); dialog.setHeaderText(text("rename"));
        dialog.getEditor().setTextFormatter(new TextFormatter<String>(change -> change.getControlNewText().length() <= 120 ? change : null));
        DialogSpacing.prepare(dialog);
        dialog.showAndWait().flatMap(value -> MainWindowController.aiRenamedTitle(session.title(), value))
                .ifPresent(value -> perform(actions.rename(session.id(), value)));
    }
    private void confirmDelete(List<UUID> ids) {
        if (ids.isEmpty() || ids.stream().anyMatch(actions::running)) return;
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.initOwner(actions.owner()); dialog.setTitle(text("delete"));
        dialog.setHeaderText(text("deleteConfirm").replace("{0}", Integer.toString(ids.size())));
        dialog.setContentText(text("deleteDetail")); DialogSpacing.prepare(dialog);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) perform(actions.delete(ids));
    }
    private void perform(CompletionStage<?> operation) {
        busy = true; refresh();
        operation.whenComplete((ignored, error) -> Platform.runLater(() -> {
            busy = false;
            if (error != null) actions.failed(error);
            else { batch = false; checked.clear(); }
            refresh();
        }));
    }
    private static String text(String key) { return Texts.text("ai.sessions." + key); }
    private static Button button(String key, LucideIconView.Icon icon) {
        Button button = new Button(); var graphic = new LucideIconView(icon);
        graphic.getStyleClass().add("tool-window-icon"); button.setGraphic(graphic);
        button.setTooltip(new Tooltip(text(key))); button.setAccessibleText(text(key)); return button;
    }
    private static MenuItem item(String key, LucideIconView.Icon icon, Runnable action) {
        MenuItem item = new MenuItem(text(key), new LucideIconView(icon)); item.setOnAction(event -> action.run()); return item;
    }
}
