// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.ai.AiChatSession;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class AiSessionListControllerTest {
    @Test void contextRequestUsesTheSameButtonOwnedMenuAndStyling() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            var now = Instant.now();
            var session = new AiChatSession(UUID.randomUUID(), "Menu fixture", now, now, 0);
            var secondSession = new AiChatSession(UUID.randomUUID(), "Second fixture", now.minusSeconds(1), now.minusSeconds(1), 0);
            var list = new ListView<AiChatSession>();
            var footer = new VBox();
            var pins = new ArrayList<UUID>();
            var controller = new AiSessionListController(list, footer, new AiSessionListController.Actions() {
                public CompletionStage<?> rename(UUID id, String title) { return CompletableFuture.completedFuture(null); }
                public CompletionStage<?> pin(List<UUID> ids, boolean pinned) {
                    pins.addAll(ids); return CompletableFuture.completedFuture(null);
                }
                public CompletionStage<?> delete(List<UUID> ids) { return CompletableFuture.completedFuture(null); }
                public boolean running(UUID id) { return true; }
                public javafx.stage.Window owner() { return null; }
                public void failed(Throwable error) { fail(error); }
            });
            controller.sessions(List.of(session, secondSession), session.id());
            var root = new VBox(list, footer);
            var stage = new javafx.stage.Stage();
            var scene = new javafx.scene.Scene(root, 320, 240);
            scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
            stage.setScene(scene);
            ContextMenu menu = null;
            try {
                stage.show(); root.applyCss(); root.layout();
                var more = (Button) list.lookup(".ai-history-action-button");
                var row = more.getParent();
                more.fire();
                menu = javafx.stage.Window.getWindows().stream().filter(window -> window instanceof ContextMenu)
                        .map(ContextMenu.class::cast).filter(value -> value.getOwnerNode() == more)
                        .findFirst().orElseThrow();
                assertTrue(menu.isShowing());
                menu.getSkin().getNode().applyCss();
                double scale = menu.getItems().getFirst().getGraphic().getScaleX();
                assertEquals(.52, scale, .001, "Keep the compact three-dot menu icons");
                assertTrue(menu.getItems().getLast().isDisable());
                menu.hide();
                row.fireEvent(new javafx.scene.input.ContextMenuEvent(
                        javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                        10, 10, stage.getX() + 20, stage.getY() + 20, false, null));
                assertTrue(menu.isShowing(), "Right click reopens the same menu instance");
                assertSame(more, menu.getOwnerNode(), "Both routes must inherit the button's popup styling");
                menu.getSkin().getNode().applyCss();
                assertEquals(scale, menu.getItems().getFirst().getGraphic().getScaleX());
                assertSame(menu, ApplicationContextMenus.activeMenu());
                var hidden = new java.util.concurrent.atomic.AtomicInteger();
                menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, event -> hidden.incrementAndGet());
                row.fireEvent(new javafx.scene.input.ContextMenuEvent(
                        javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                        10, 10, stage.getX() + 60, stage.getY() + 60, false, null));
                assertEquals(1, hidden.get(), "Repeated right click closes the old popup before reopening");
                assertTrue(menu.isShowing());
                var secondButton = list.lookupAll(".ai-history-action-button").stream()
                        .filter(node -> node != more).map(Button.class::cast).findFirst().orElseThrow();
                secondButton.getParent().fireEvent(new javafx.scene.input.ContextMenuEvent(
                        javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                        10, 10, stage.getX() + 80, stage.getY() + 80, false, null));
                assertFalse(menu.isShowing(), "Right click on another row closes the previous menu");
                var secondMenu = ApplicationContextMenus.activeMenu();
                assertNotNull(secondMenu); assertNotSame(menu, secondMenu);
                assertSame(secondButton, secondMenu.getOwnerNode());
                more.fire();
                assertFalse(secondMenu.isShowing());
                assertSame(menu, ApplicationContextMenus.activeMenu());
                more.fire();
                assertFalse(menu.isShowing(), "Clicking the same three-dot button again closes the menu");
                more.fire();
                menu.getItems().get(2).fire();
                assertEquals(List.of(session.id()), pins);
            } finally {
                if (menu != null) menu.hide();
                stage.close();
            }
            return null;
        });
        Platform.runLater(task); task.get(20, TimeUnit.SECONDS);
    }

    @BeforeAll static void start() throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException alreadyRunning) { }
        Platform.setImplicitExit(false);
    }
    @Test void batchSelectAllCancelAndRunningDeleteProtection() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            var now = Instant.now();
            var first = new AiChatSession(UUID.randomUUID(), "First", now, now, 0);
            var second = new AiChatSession(UUID.randomUUID(), "Second", now, now, 0);
            var list = new ListView<AiChatSession>(); list.getItems().setAll(first, second);
            var footer = new VBox();
            var controller = new AiSessionListController(list, footer, new AiSessionListController.Actions() {
                public CompletionStage<?> rename(UUID id, String title) { return CompletableFuture.completedFuture(null); }
                public CompletionStage<?> pin(List<UUID> ids, boolean pinned) { return CompletableFuture.completedFuture(null); }
                public CompletionStage<?> delete(List<UUID> ids) { fail("Running chat must not be deleted"); return null; }
                public boolean running(UUID id) { return id.equals(second.id()); }
                public javafx.stage.Window owner() { return null; }
                public void failed(Throwable error) { fail(error); }
            });
            var root = new VBox(list, footer);
            new javafx.scene.Scene(root, 260, 400);
            root.applyCss(); root.layout();
            var moreButtons = list.lookupAll(".ai-history-action-button").stream()
                    .filter(node -> node instanceof Button button && com.ynpe.fastremote.i18n.Texts
                            .text("ai.sessions.more").equals(button.getAccessibleText()))
                    .map(Button.class::cast).toList();
            assertEquals(2, moreButtons.size());
            for (Button more : moreButtons) assertNull(more.getTooltip());
            for (var node : list.lookupAll(".list-cell")) assertNull(((ListCell<?>) node).getTooltip());
            for (var node : list.lookupAll(".ai-history-title")) assertNull(((Label) node).getTooltip());
            controller.enterBatch(first.id()); assertTrue(footer.isManaged());
            assertEquals(Set.of(first.id()), controller.checked());
            var commands = (HBox) footer.getChildren().getLast();
            var remove = (Button) commands.getChildren().getLast(); assertFalse(remove.isDisabled());
            controller.toggle(second.id()); assertTrue(remove.isDisabled());
            var all = (CheckBox) ((HBox) footer.getChildren().getFirst()).getChildren().getFirst();
            assertTrue(all.isSelected()); all.fire(); assertTrue(controller.checked().isEmpty());
            all.fire(); assertEquals(2, controller.checked().size()); assertTrue(remove.isDisabled());
            list.getItems().remove(second); controller.update(first.id());
            assertEquals(Set.of(first.id()), controller.checked()); assertFalse(remove.isDisabled());
            controller.exitBatch(); assertFalse(footer.isManaged()); assertTrue(controller.checked().isEmpty());
            controller.sessions(List.of(first, second), first.id());
            controller.filter("SECOND"); assertEquals(List.of(second), list.getItems());
            controller.enterBatch(second.id());
            controller.filter("First"); assertTrue(controller.checked().isEmpty());
            controller.filter(""); assertEquals(2, list.getItems().size());
            controller.exitBatch();
            return null;
        }); Platform.runLater(task); task.get(20, TimeUnit.SECONDS);
    }
}
