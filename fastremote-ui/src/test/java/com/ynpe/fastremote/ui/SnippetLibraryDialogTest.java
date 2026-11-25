// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.ynpe.fastremote.domain.settings.KeyboardShortcutSettings;
import com.ynpe.fastremote.domain.snippet.*;
import com.ynpe.fastremote.i18n.Texts;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SnippetLibraryDialogTest {
    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException running) { started.countDown(); }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test void executionHintFollowsAllThreeInterfaceThemes() throws Exception {
        fx(() -> {
            Stage owner = new Stage();
            owner.setScene(new Scene(new VBox(), 500, 500));
            ThemeManager.install(owner.getScene());
            SnippetLibraryDialog dialog = new SnippetLibraryDialog(owner,
                    KeyboardShortcutSettings.defaults(), () -> {}, () -> {});
            try {
                var hint = dialog.controller().helpLabel();
                assertTrue(hint.getStyleClass().contains("label"));
                for (var theme : com.ynpe.fastremote.domain.settings.ApplicationTheme.values()) {
                    ThemeManager.setTheme(theme);
                    dialog.window().getScene().getRoot().applyCss();
                    String expected = switch (theme) {
                        case LIGHT -> "#1e293b";
                        case DARK -> "#cbcdd3";
                        case SLATE -> "#e7ebf2";
                    };
                    assertEquals(javafx.scene.paint.Color.web(expected), hint.getTextFill());
                }
            } finally {
                dialog.close(); owner.close();
                ThemeManager.setTheme(com.ynpe.fastremote.domain.settings.ApplicationTheme.LIGHT);
            }
        });
    }

    @Test void dialogSharesTheViewButKeepsSearchAndSelectionIndependent() throws Exception {
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("SnippetLibraryView.fxml"),
                    Texts.localizer().bundle());
            VBox dockRoot = loader.load();
            SnippetLibraryController dock = loader.getController();
            RecordingActions dockActions = new RecordingActions();
            dock.bind(dockActions);
            Stage owner = new Stage();
            owner.setScene(new Scene(dockRoot, 500, 500));
            owner.show();
            RecordingActions actions = new RecordingActions();
            SnippetLibraryDialog dialog = new SnippetLibraryDialog(owner, KeyboardShortcutSettings.defaults(),
                    () -> actions.events.add("import"), () -> actions.events.add("export"));
            javafx.stage.Window managerWindow = dialog.window();
            try {
                SnippetLibraryController view = dialog.controller();
                view.bind(actions);
                SnippetSnapshot snapshot = snapshot();
                dock.update(snapshot); view.update(snapshot);
                dialog.show();
                assertTrue(((Stage) dialog.window()).isResizable());
                DialogPane pane = (DialogPane) dialog.window().getScene().getRoot();
                assertEquals(480.0, DialogSpacing.configuredResizableWindowWidth(pane));
                assertEquals(680.0, DialogSpacing.configuredResizableWindowHeight(pane));
                assertEquals(Texts.text("legacy.java.1cbb8f00f07db977"), pane.getHeaderText());
                VBox content = (VBox) pane.getContent();
                assertSame(view.searchField(), content.getChildren().get(1));
                assertSame(view.helpLabel(), content.getChildren().get(2));
                assertSame(view.tree(), content.getChildren().get(3));
                var toolbar = (javafx.scene.layout.HBox) content.getChildren().getFirst();
                assertEquals(5, toolbar.getChildren().size());
                assertFalse(toolbar.getChildren().contains(view.searchField()));
                for (int index : List.of(1, 2)) {
                    Button button = (Button) toolbar.getChildren().get(index);
                    assertEquals(ContentDisplay.GRAPHIC_ONLY, button.getContentDisplay());
                    assertNotNull(button.getTooltip());
                    assertNotNull(button.getAccessibleText());
                }
                assertTrue(pane.getStyleClass().contains(DialogSpacing.CONTENT_TO_BUTTON_BAR));
                assertNotSame(dock.tree(), view.tree());
                view.searchField().setText("needle");
                assertEquals("", dock.searchField().getText());
                assertEquals(2, dock.tree().getRoot().getChildren().size());
                assertEquals(1, view.tree().getRoot().getChildren().size());
                TreeItem<String> group = view.tree().getRoot().getChildren().getFirst();
                assertTrue(group.isExpanded());
                assertEquals("Ops", group.getValue());
                assertEquals(2, group.getChildren().size());

                view.searchField().requestFocus();
                press(view.searchField(), KeyCode.DOWN);
                assertSame(view.tree(), dialog.window().getScene().getFocusOwner());
                assertSame(group, view.tree().getSelectionModel().getSelectedItem());
                press(view.tree(), KeyCode.UP);
                assertSame(view.searchField(), dialog.window().getScene().getFocusOwner());
                assertEquals(6, view.searchField().getCaretPosition());
                assertEquals(0, view.searchField().getSelection().getLength());
                press(view.searchField(), KeyCode.DOWN);
                press(view.tree(), KeyCode.ENTER);
                assertFalse(group.isExpanded());
                press(view.tree(), KeyCode.ENTER);
                assertTrue(group.isExpanded());
                view.tree().getSelectionModel().clearAndSelect(1);
                view.tree().getFocusModel().focus(1);
                pane.applyCss();
                pane.layout();
                TreeCell<?> commandCell = view.tree().lookupAll(".tree-cell").stream()
                        .filter(TreeCell.class::isInstance).map(TreeCell.class::cast)
                        .filter(cell -> cell.getIndex() == 1).findFirst().orElseThrow();
                var point = commandCell.localToScreen(4, 4);
                commandCell.fireEvent(new javafx.scene.input.ContextMenuEvent(
                        javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                        4, 4, point.getX(), point.getY(), false, null));
                ContextMenu menu = javafx.stage.Window.getWindows().stream()
                        .filter(ContextMenu.class::isInstance).map(ContextMenu.class::cast)
                        .filter(javafx.stage.Window::isShowing).findFirst().orElseThrow();
                try {
                    menu.getItems().get(0).fire();
                    menu.getItems().get(1).fire();
                    assertEquals(List.of("send:true:First", "send:false:First"), actions.events);
                    assertTrue(dialog.window().isShowing());
                    actions.events.clear();
                } finally { menu.hide(); }
                press(view.tree(), KeyCode.ENTER);
                assertEquals(List.of("send:true:First"), actions.events);
                assertFalse(managerWindow.isShowing());
                dialog.show();
                view.tree().getSelectionModel().clearAndSelect(2);
                view.tree().getFocusModel().focus(2);
                press(view.tree(), KeyCode.DOWN);
                assertSame(view.searchField(), dialog.window().getScene().getFocusOwner());
                assertEquals(0, view.searchField().getSelection().getLength());
                assertEquals(6, view.searchField().getCaretPosition());
                assertNull(dock.tree().getSelectionModel().getSelectedItem());

                view.searchField().setText("missing");
                press(view.searchField(), KeyCode.DOWN);
                assertSame(view.searchField(), dialog.window().getScene().getFocusOwner());
                assertEquals(0, view.tree().getExpandedItemCount());
                view.searchField().clear();
                assertEquals(2, view.tree().getRoot().getChildren().size());
                assertFalse(view.tree().getRoot().getChildren().getFirst().isExpanded());

                for (String label : List.of("legacy.java.576d81bb0631b165", "legacy.fxml.76420433f22d1c80")) {
                    dialog.window().getScene().getRoot().lookupAll(".button").stream()
                            .filter(Button.class::isInstance).map(Button.class::cast)
                            .filter(button -> Texts.text(label).equals(button.getText()))
                            .findFirst().orElseThrow().fire();
                }
                assertEquals(List.of("send:true:First", "import", "export"), actions.events);
                SnippetSnapshot empty = new SnippetSnapshot(List.of(), List.of(), List.of());
                dock.update(empty); view.update(empty);
                assertEquals(0, dock.tree().getExpandedItemCount());
                assertEquals(0, view.tree().getExpandedItemCount());
            } finally { dialog.close(); owner.close(); }
            assertFalse(managerWindow.isShowing());
        });
    }

    @Test void shownDialogKeepsCompactGeometryAndFooterSpacing() throws Exception {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        fx(() -> {
            Stage owner = new Stage();
            owner.setScene(new Scene(new VBox(), 500, 500));
            owner.show();
            SnippetLibraryDialog dialog = new SnippetLibraryDialog(owner, KeyboardShortcutSettings.defaults(),
                    () -> { }, () -> { });
            dialog.controller().bind(new RecordingActions());
            dialog.controller().update(snapshot());
            dialog.show();
            Runnable verifyGeometry = () -> {
                try {
                    Scene scene = dialog.window().getScene();
                    assertEquals(480, scene.getWidth(), 2);
                    assertEquals(680, scene.getHeight(), 2);
                    DialogPane pane = (DialogPane) scene.getRoot();
                    var tree = dialog.controller().tree();
                    var content = (VBox) pane.getContent();
                    assertEquals(10, content.getPadding().getTop(), 0.1);
                    double searchInset = dialog.controller().searchField().localToScene(
                            dialog.controller().searchField().getBoundsInLocal()).getMinY()
                            - content.localToScene(content.getBoundsInLocal()).getMinY();
                    assertTrue(searchInset >= 9, "search top inset: " + searchInset);
                    var toolbar = content.getChildren().getFirst();
                    var search = dialog.controller().searchField();
                    assertTrue(search.localToScene(search.getBoundsInLocal()).getMinY()
                            > toolbar.localToScene(toolbar.getBoundsInLocal()).getMaxY());
                    assertEquals(content.getWidth() - 40, search.getWidth(), 1);
                    var close = pane.lookupButton(ButtonType.CLOSE);
                    double gap = close.localToScene(close.getBoundsInLocal()).getMinY()
                            - tree.localToScene(tree.getBoundsInLocal()).getMaxY();
                    assertTrue(gap >= 0 && gap <= 24, "tree-to-close gap: " + gap);
                    completed.complete(null);
                } catch (Throwable failure) {
                    completed.completeExceptionally(failure);
                } finally {
                    dialog.close();
                    owner.close();
                }
            };
            new javafx.animation.AnimationTimer() {
                private long started;
                private long stableSince;
                private double width = -1;
                private double height = -1;
                @Override public void handle(long now) {
                    if (started == 0) started = now;
                    var pane = dialog.window().getScene().getRoot();
                    var scene = dialog.window().getScene();
                    // DialogSpacing relaxes the content minimum after native placement (500 ms).
                    // Check stable final geometry instead of racing that deferred layout at 300 ms.
                    boolean ready = dialog.window().getOpacity() == 1.0
                            && ((DialogPane) pane).getMinWidth() == 360.0 && !pane.isNeedsLayout();
                    if (!ready || scene.getWidth() != width || scene.getHeight() != height) {
                        stableSince = now;
                        width = scene.getWidth();
                        height = scene.getHeight();
                    }
                    if ((ready && now - stableSince >= TimeUnit.MILLISECONDS.toNanos(250))
                            || now - started >= TimeUnit.SECONDS.toNanos(3)) {
                        stop();
                        verifyGeometry.run();
                    }
                }
            }.start();
        });
        completed.get(10, TimeUnit.SECONDS);
    }

    @Test void escapeClosesFromSearchAndTree() throws Exception {
        fx(() -> {
            Stage owner = new Stage();
            owner.setScene(new Scene(new VBox(), 500, 500));
            owner.show();
            SnippetLibraryDialog dialog = new SnippetLibraryDialog(owner, KeyboardShortcutSettings.defaults(),
                    () -> {}, () -> {});
            try {
                dialog.controller().bind(new RecordingActions());
                dialog.controller().update(snapshot());
                dialog.show();
                var window = dialog.window();
                press(dialog.controller().searchField(), KeyCode.ESCAPE);
                assertFalse(window.isShowing());
                dialog.show();
                press(dialog.controller().tree(), KeyCode.ESCAPE);
                assertFalse(window.isShowing());
            } finally { dialog.close(); owner.close(); }
        });
    }

    @Test void sharedControllerDispatchesSelectedCommandsAndGroups() throws Exception {
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("SnippetLibraryView.fxml"),
                    Texts.localizer().bundle());
            loader.load();
            SnippetLibraryController view = loader.getController();
            RecordingActions actions = new RecordingActions();
            view.bind(actions); view.update(snapshot());
            view.searchField().setText("Ops");
            TreeItem<String> group = view.tree().getRoot().getChildren().getFirst();
            view.tree().getSelectionModel().select(group);
            view.createSnippet();
            view.shortcut(ShortcutAction.EDIT_SELECTED_LIBRARY_ITEM);
            assertTrue(actions.createdGroup.isPresent());
            assertEquals(List.of("editGroup"), actions.events);
            view.tree().getSelectionModel().clearAndSelect(1);
            view.shortcut(ShortcutAction.EDIT_SELECTED_LIBRARY_ITEM);
            view.shortcut(ShortcutAction.COPY_SELECTED_LIBRARY_ITEM);
            view.tree().getSelectionModel().select(2);
            view.shortcut(ShortcutAction.DELETE_SELECTED_LIBRARY_ITEM);
            assertEquals(2, actions.deleted.size());
            assertEquals(List.of("editGroup", "edit:First", "copy:First", "delete"), actions.events);
            // Context menus and drag/drop are installed by the same controller in both hosts.
            var cell = view.tree().getCellFactory().call(view.tree());
            assertNotNull(cell.getOnContextMenuRequested());
            assertNotNull(cell.getOnDragDetected());
            assertNotNull(cell.getOnDragDropped());
            assertNotNull(cell.getOnDragDone());
            view.tree().getContextMenu().getItems().get(1).fire();
            assertEquals("import", actions.events.getLast());
        });
    }

    private static SnippetSnapshot snapshot() {
        var group = new SnippetGroup(SnippetGroupId.create(), "Ops", 0);
        var empty = new SnippetGroup(SnippetGroupId.create(), "Empty", 10);
        Instant now = Instant.now();
        return new SnippetSnapshot(List.of(group, empty), List.of(
                new Snippet(SnippetId.create(), Optional.of(group.id()), "First", "needle", "echo one", 0, now, now),
                new Snippet(SnippetId.create(), Optional.of(group.id()), "Second", "", "echo needle", 10, now, now)), List.of());
    }

    private static final class RecordingActions implements SnippetLibraryController.Actions {
        final List<String> events = new ArrayList<>();
        List<Snippet> deleted = List.of();
        Optional<SnippetGroupId> createdGroup = Optional.empty();
        public void importBuiltins() { events.add("builtins"); }
        public void createSnippet(Optional<SnippetGroupId> group) { createdGroup = group; }
        public void createGroup() { events.add("createGroup"); }
        public void importText() { events.add("import"); }
        public void send(Snippet snippet, boolean execute) { events.add("send:" + execute + ":" + snippet.name()); }
        public void edit(Snippet snippet) { events.add("edit:" + snippet.name()); }
        public void copy(Snippet snippet) { events.add("copy:" + snippet.name()); }
        public void editGroup(SnippetGroup group) { events.add("editGroup"); }
        public void deleteSnippets(List<Snippet> snippets) { deleted = snippets; }
        public void deleteSelection(List<Snippet> snippets, List<SnippetGroup> groups) {
            deleted = snippets; events.add("delete");
        }
        public void refresh() { events.add("refresh"); }
        public CompletionStage<Snippet> saveSnippet(Snippet snippet) { return CompletableFuture.completedFuture(snippet); }
        public CompletionStage<SnippetGroup> saveGroup(SnippetGroup group) { return CompletableFuture.completedFuture(group); }
        public void registerExpansion(TreeItem<String> item, SnippetGroupId group) { item.setExpanded(false); }
        public void prepareSearch(TreeItem<String> root, Map<TreeItem<String>, SnippetGroup> groups, String query) {
            LibraryTreeSupport.hideEmptyGroupsWhileSearching(root, groups, query);
        }
        public void showShortcut(MenuItem item, ShortcutAction action) { }
    }

    private static void press(javafx.scene.Node node, KeyCode code) {
        node.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }
    private interface CheckedAction { void run() throws Exception; }
    private static void fx(CheckedAction task) throws Exception {
        var future = new FutureTask<Void>(() -> { task.run(); return null; });
        Platform.runLater(future); future.get(10, TimeUnit.SECONDS);
    }
}
