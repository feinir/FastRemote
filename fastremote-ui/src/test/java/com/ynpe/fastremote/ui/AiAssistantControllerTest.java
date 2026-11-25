// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class AiAssistantControllerTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
        // Dialog tests share the FX toolkit; resolve FXML imports independently of
        // the native event thread's mutable context class loader.
        FXMLLoader.setDefaultClassLoader(AiAssistantControllerTest.class.getClassLoader());
    }

    @Test void runningComposerShowsIndependentSendStopAndQueueInDockAndDialogWidths() throws Exception {
        runFx(() -> {
            try {
                var loader = new FXMLLoader(MainView.aiAssistantFxml(), ResourceBundle.getBundle(
                        com.ynpe.fastremote.i18n.Localizer.BUNDLE_NAME, java.util.Locale.SIMPLIFIED_CHINESE));
                var page = (javafx.scene.layout.VBox) loader.load();
                AiAssistantController controller = loader.getController();
                var calls = new java.util.ArrayList<String>();
                controller.bind(new NoOpActions() {
                    @Override public void sendOrInterrupt() { calls.add("send"); }
                    @Override public void stop() { calls.add("stop"); }
                    @Override public void steer() { calls.add("steer"); }
                });
                var queue = new AiPromptQueue();
                queue.add("检查本机 Nginx 配置并列出需要调整的内容", List.of());
                var pending = queue.add("先检查日志，不要修改配置", List.of());
                queue.state(pending.id(), AiPromptQueue.State.WAITING);
                controller.updateExecutionControls(true, false, false, queue);
                var chat = new AiChatView(ignored -> {}, ignored -> {}, ignored -> {});
                controller.conversationHost().getChildren().setAll(chat);
                chat.appendMessage(new AiChatView.Message("a", "ASSISTANT", "AI", "正在读取配置。返回的内容会逐步显示在这里。", false, "stream:preview"));
                var scene = new javafx.scene.Scene(page, 980, 720);
                scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
                controller.setSessionsCollapsed(true);
                try {
                    for (int width : List.of(420, 980)) {
                        page.resize(width, 720); page.applyCss(); page.layout();
                        var send = (javafx.scene.control.Button) page.lookup("#aiSendButton");
                        var stop = (javafx.scene.control.Button) page.lookup("#aiStopButton");
                        var options = (javafx.scene.control.MenuButton) page.lookup("#aiSendOptions");
                        assertTrue(stop.isVisible()); assertTrue(options.isVisible());
                        assertTrue(page.lookup("#aiPendingScroll").getBoundsInLocal().getHeight() <= 144);
                        assertTrue(send.localToScene(send.getBoundsInLocal()).getMaxX() <= width + 1, "Send control overflow at " + width);
                        assertTrue(options.localToScene(options.getBoundsInLocal()).getMaxX() <= width + 1, "Send options overflow at " + width);
                        send.fire(); stop.fire(); options.getItems().getFirst().fire();
                        var pixels = page.snapshot(null, null);
                        var output = java.nio.file.Path.of("build/ai-stream-preview/composer-" + width + ".png");
                        java.nio.file.Files.createDirectories(output.getParent());
                        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(pixels, null), "png", output.toFile());
                    }
                    assertEquals(List.of("send", "stop", "steer", "send", "stop", "steer"), calls);
                    controller.updateExecutionControls(false, false, false, queue);
                    assertFalse(page.lookup("#aiStopButton").isManaged());
                } finally { chat.close(); controller.close(); }
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }

    @Test
    void forcedCollapseDoesNotOverwritePreferenceAndToggleKeepsFocusInAi() throws Exception {
        runFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(MainView.aiAssistantFxml(), ResourceBundle.getBundle(
                        com.ynpe.fastremote.i18n.Localizer.BUNDLE_NAME, java.util.Locale.SIMPLIFIED_CHINESE));
                var page = (javafx.scene.layout.VBox) loader.load();
                AiAssistantController controller = loader.getController();
                var saved = new java.util.ArrayList<Boolean>();
                controller.bind(new NoOpActions() {
                    @Override public void sessionsCollapsedChanged(boolean collapsed) { saved.add(collapsed); }
                });
                var scene = new javafx.scene.Scene(page, 900, 700);
                var stage = new javafx.stage.Stage();
                stage.setScene(scene); stage.show();
                try {
                controller.setSessionsCollapsed(true);
                assertTrue(saved.isEmpty());
                var icons = (javafx.scene.layout.HBox) loader.getNamespace().get("aiCollapsedToolbar");
                var expand = (javafx.scene.control.Button) icons.getChildren().getFirst();
                expand.requestFocus();
                expand.fire();
                assertFalse(controller.sessionsCollapsed());
                assertEquals(java.util.List.of(false), saved);
                assertSame(controller.promptField(), scene.getFocusOwner());
                controller.collapseSessions();
                assertEquals(java.util.List.of(false), saved);
                controller.setSessionsCollapsed(false);
                controller.toggleSessionsButton().fire();
                assertEquals(java.util.List.of(false, true), saved);
                assertSame(controller.promptField(), scene.getFocusOwner());
                } finally { stage.close(); }
            } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
        });
    }

    @Test
    void hostAssociationCanBeClearedFromSearchResults() throws Exception {
        runFx(() -> {
            var node = new javafx.scene.control.MenuButton();
            var changes = new java.util.ArrayList<java.util.Optional<com.ynpe.fastremote.domain.ai.AiHostAssociation>>();
            var selector = new AiHostAssociationSelector(node, changes::add);
            var host = new com.ynpe.fastremote.domain.ai.AiHostAssociation(
                    java.util.Optional.empty(), "", "ops_team", "192.0.2.10", 22);
            selector.update(List.of(), List.of(), List.of(host), java.util.Optional.of(host));
            assertFalse(node.isMnemonicParsing());
            assertEquals("ops_team@192.0.2.10:22", node.getText());
            var picker = selector.picker();
            picker.searchField().setText("not-matching-any-host");
            var empty = picker.results().getItems().stream().filter(option -> option.id().isEmpty()).findFirst().orElseThrow();
            assertEquals(com.ynpe.fastremote.i18n.Texts.text("ai.host.none"), empty.buttonText());
            picker.results().getSelectionModel().select(empty);
            picker.searchField().fireEvent(new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_RELEASED, "", "", javafx.scene.input.KeyCode.ENTER,
                    false, false, false, false));
            assertTrue(picker.selectedHostId().isEmpty());
            assertEquals(List.of(java.util.Optional.empty()), changes);
            selector.update(List.of(), List.of(), List.of(host), java.util.Optional.empty());
            assertEquals(empty.buttonText(), node.getText());
        });
    }

    @Test
    void promptDragSurvivesReparentingAndHasOneRowMinimumWithoutUpperLimit() throws Exception {
        runFx(() -> {
            var input = new javafx.scene.control.TextArea();
            var handle = new javafx.scene.layout.Region();
            AiPromptResizeSupport.install(input, handle);
            double minimum = input.getMinHeight();
            assertEquals(2, input.getPrefRowCount());
            assertEquals((minimum - 14) * 2 + 14, input.getPrefHeight());
            var dock = new javafx.scene.layout.VBox(handle, input);
            var window = new javafx.scene.layout.VBox();
            dock.getChildren().clear(); window.getChildren().setAll(handle, input);
            input.resize(300, input.getPrefHeight());
            handle.fireEvent(promptMouse(javafx.scene.input.MouseEvent.MOUSE_PRESSED, 500));
            handle.fireEvent(promptMouse(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, -500));
            assertTrue(input.getPrefHeight() > 1000);
            handle.fireEvent(promptMouse(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, 5000));
            assertEquals(minimum, input.getPrefHeight());
            handle.fireEvent(promptMouse(javafx.scene.input.MouseEvent.MOUSE_RELEASED, 5000));
        });
    }

    private static javafx.scene.input.MouseEvent promptMouse(javafx.event.EventType<javafx.scene.input.MouseEvent> type, double y) {
        return new javafx.scene.input.MouseEvent(type, 0, 0, 0, y,
                javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, false, false, true, null);
    }

    @Test
    void dockHeaderAndChatShareTheContentMinimumOnEitherSide() throws Exception {
        runFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(MainView.aiAssistantFxml(), ResourceBundle.getBundle(
                        com.ynpe.fastremote.i18n.Localizer.BUNDLE_NAME, java.util.Locale.SIMPLIFIED_CHINESE));
                javafx.scene.layout.VBox page = loader.load();
                ((AiAssistantController) loader.getController()).bind(new NoOpActions());
                try (AiChatView chat = new AiChatView(ignored -> { })) {
                    ((javafx.scene.layout.StackPane) loader.getNamespace().get("aiConversationHost"))
                            .getChildren().setAll(chat);
                    var header = new javafx.scene.control.Label("AI 助手");
                    header.setMaxWidth(Double.MAX_VALUE);
                    var host = new javafx.scene.layout.StackPane(page);
                    var dock = new javafx.scene.layout.VBox(header, host);
                    dock.setMinWidth(MainWindowController.dockMinimumWidth(
                            MainWindowController.ToolWindow.AI_ASSISTANT));
                    var center = new javafx.scene.layout.StackPane();
                    var split = new javafx.scene.control.SplitPane(dock, center);
                    new javafx.scene.Scene(split, 900, 700);
                    split.getStylesheets().add(MainView.stylesheet().toExternalForm());
                    for (boolean right : new boolean[]{false, true}) {
                        split.getItems().setAll(right ? List.of(center, dock) : List.of(dock, center));
                        split.applyCss();
                        split.resize(900, 700);
                        split.setDividerPositions(right ? .99 : .01);
                        split.layout(); dock.layout(); host.layout(); page.layout();
                        assertTrue(page.minWidth(-1) > 245, "exercise the composer minimum above the old dock limit");
                        assertTrue(dock.getWidth() >= page.minWidth(-1), "dock must respect the chat minimum");
                        assertEquals(header.getWidth(), page.getWidth(), 1.0);
                        assertTrue(page.getLayoutX() + page.getWidth() <= host.getWidth() + 1);
                        MainWindowController.lockDockWidthForReveal(dock, 245);
                        assertTrue(dock.getMaxWidth() >= page.minWidth(-1), "floating/reopened dock shares the minimum");
                    }
                }
                ((AiAssistantController) loader.getController()).close();
            } catch (java.io.IOException error) { throw new AssertionError(error); }
        });
    }

    @Test
    @Timeout(10)
    void includedViewEncodesAndStagesAnImageWithoutBlockingTheFxThread() throws Exception {
        AtomicReference<AiAssistantController> controller = new AtomicReference<>();
        CountDownLatch staged = new CountDownLatch(1);
        runFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(MainView.aiAssistantFxml(), ResourceBundle.getBundle(
                        com.ynpe.fastremote.i18n.Localizer.BUNDLE_NAME, java.util.Locale.SIMPLIFIED_CHINESE));
                loader.load();
                AiAssistantController value = loader.getController();
                value.bind(new NoOpActions() {
                    @Override public void imageStateChanged() { if (value.hasImages()) staged.countDown(); }
                });
                value.setImagesAllowed(true);
                WritableImage image = new WritableImage(640, 480);
                image.getPixelWriter().setArgb(0, 0, 0xff336699);
                value.stageClipboardImage(image);
                controller.set(value);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        });

        assertTrue(staged.await(5, TimeUnit.SECONDS));
        AtomicReference<List<com.ynpe.fastremote.ai.AiImageInput>> images = new AtomicReference<>();
        runFx(() -> images.set(controller.get().takeImages()));
        assertEquals(1, images.get().size());
        assertEquals("image/png", images.get().getFirst().mediaType());
        assertEquals(640, images.get().getFirst().width());
        assertTrue(images.get().getFirst().thumbnailData().length < images.get().getFirst().data().length);
        runFx(controller.get()::close);
    }

    @Test
    void originalImageWindowIsFixedAndFitsTheDisplayedImage() throws Exception {
        runFx(() -> {
            for (int[] size : new int[][]{{160, 90}, {640, 480}, {2200, 1000}, {500, 1520}}) {
                var dialog = AiAssistantController.originalImageDialog(new WritableImage(size[0], size[1]));
                try {
                    dialog.show();
                    var pane = dialog.getDialogPane();
                    pane.applyCss();
                    pane.layout();
                    var stage = (javafx.stage.Stage) pane.getScene().getWindow();
                    org.junit.jupiter.api.Assertions.assertFalse(dialog.isResizable());
                    org.junit.jupiter.api.Assertions.assertFalse(stage.isResizable());
                    var view = (javafx.scene.image.ImageView) pane.getContent();
                    double scale = Math.min(1, Math.min(1100.0 / size[0], 760.0 / size[1]));
                    assertEquals(size[0] * scale, view.getBoundsInLocal().getWidth(), 1);
                    assertEquals(size[1] * scale, view.getBoundsInLocal().getHeight(), 1);
                    assertTrue(pane.getWidth() >= view.getBoundsInLocal().getWidth());
                    assertTrue(pane.getHeight() >= view.getBoundsInLocal().getHeight());
                    assertEquals(pane.prefWidth(-1), pane.getWidth(), 1);
                    assertEquals(pane.prefHeight(pane.getWidth()), pane.getHeight(), 1);
                } finally {
                    dialog.close();
                }
            }
        });
    }

    @Test
    void hiddenSidebarPreservesSelectionAndRestoresExpandedWidth() throws Exception {
        runFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(MainView.aiAssistantFxml(), ResourceBundle.getBundle(
                        com.ynpe.fastremote.i18n.Localizer.BUNDLE_NAME, java.util.Locale.SIMPLIFIED_CHINESE));
                var page = (javafx.scene.layout.VBox) loader.load();
                AiAssistantController controller = loader.getController();
                controller.bind(new NoOpActions());

                var now = java.time.Instant.now();
                var chat = new com.ynpe.fastremote.domain.ai.AiChatSession(java.util.UUID.randomUUID(), "A complete chat title", now, now, 0);
                controller.updateSessions(java.util.List.of(chat));
                controller.sessionList().getSelectionModel().select(chat);
                var scene = new javafx.scene.Scene(page, 900, 700);
                scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
                page.applyCss(); page.resize(900, 700); page.layout();
                var split = (javafx.scene.control.SplitPane) loader.getNamespace().get("aiChatSplit");
                split.layout();
                double width = split.getItems().getFirst().getBoundsInParent().getWidth();
                for (int i = 0; i < 2; i++) {
                    controller.collapseSessions();
                    controller.collapseSessions();
                    page.layout(); split.layout();
                    controller.sessionList().layout();
                    assertTrue(controller.sessionsCollapsed());
                    assertEquals(1, split.getItems().size());
                    assertSame(loader.getNamespace().get("aiChatMain"), split.getItems().getFirst());
                    var icons = (javafx.scene.layout.HBox) loader.getNamespace().get("aiCollapsedToolbar");
                    assertTrue(icons.isVisible());
                    icons.layout();
                    for (javafx.scene.Node action : icons.getChildren()) {
                        var button = (javafx.scene.control.Button) action;
                        button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), true);
                        button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), true);
                        page.applyCss(); page.layout(); icons.layout();
                        assertTrue(button.getHeight() <= 28, "Collapsed action must fit above the toolbar divider");
                        var bounds = button.localToScene(button.getBoundsInLocal());
                        var toolbar = (javafx.scene.layout.HBox) icons.getParent();
                        var toolbarBounds = toolbar.localToScene(toolbar.getBoundsInLocal());
                        assertTrue(bounds.getMaxY() <= toolbarBounds.getMaxY() - 1,
                                "Hover/focus background must not cover the bottom divider");
                    }
                    assertSame(((javafx.scene.control.Label) loader.getNamespace().get("aiChatTitleLabel")).getParent(), icons.getParent());
                    assertEquals("AI助手", controller.toggleSessionsButton().getText());
                    assertSame(chat, controller.sessionList().getSelectionModel().getSelectedItem());
                    controller.toggleSessionsButton().fire(); page.layout(); split.layout();
                    assertFalse(controller.sessionsCollapsed());
                    assertEquals(2, split.getItems().size());
                    assertFalse(((javafx.scene.layout.HBox) loader.getNamespace().get("aiCollapsedToolbar")).isManaged());
                    assertEquals(width, split.getItems().getFirst().getBoundsInParent().getWidth(), 3);
                    assertSame(chat, controller.sessionList().getSelectionModel().getSelectedItem());
                }
            } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
        });
    }

    @Test
    void sidebarCanResizeAndKeepsTitlesToTwoLinesWithSmallTrailingTime() throws Exception {
        runFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(MainView.aiAssistantFxml(), ResourceBundle.getBundle(
                        com.ynpe.fastremote.i18n.Localizer.BUNDLE_NAME, java.util.Locale.SIMPLIFIED_CHINESE));
                var page = (javafx.scene.layout.VBox) loader.load();
                var controller = (AiAssistantController) loader.getController();
                controller.bind(new NoOpActions());
                var split = (javafx.scene.control.SplitPane) loader.getNamespace().get("aiChatSplit");
                var scene = new javafx.scene.Scene(page, 900, 700);
                scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
                page.applyCss(); page.resize(900, 700); page.layout(); split.layout();
                page.resize(1600, 700); page.layout(); split.layout();
                assertTrue(split.getItems().getFirst().getBoundsInParent().getWidth() <= 260.0,
                        "Automatic conversation sidebar width must not exceed 260px");
                var divider = split.lookup(".split-pane-divider");
                divider.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                        1, 1, 1, 1, javafx.scene.input.MouseButton.PRIMARY, 1,
                        false, false, false, false, true, false, false, false, false, true, null));
                double before = split.getItems().getFirst().getBoundsInParent().getWidth();
                split.setDividerPositions(.4); split.layout();
                assertTrue(split.getItems().getFirst().getBoundsInParent().getWidth() > before);
                page.resize(1800, 700); page.layout(); split.layout();
                assertTrue(split.getItems().getFirst().getBoundsInParent().getWidth() > 260.0,
                        "Manual sidebar sizing must survive resize without a 260px cap");
                assertEquals("新建对话", controller.newChatButton().getText());
                assertTrue(controller.sessionList().isVisible());
                var title = new AiSessionTitle("一个很长的对话标题用于检查两行显示和末尾省略号".repeat(8), "12分钟前");
                title.setTooltip(new javafx.scene.control.Tooltip(title.getText()));
                var host = new javafx.scene.layout.StackPane(title);
                new javafx.scene.Scene(host, 170, 36).getStylesheets().add(MainView.stylesheet().toExternalForm());
                title.setMaxSize(170, 36); host.applyCss(); host.resize(170, 36); host.layout(); title.layout();
                var flow = (javafx.scene.text.TextFlow) title.getGraphic();
                var caption = (javafx.scene.text.Text) flow.getChildren().getFirst();
                var age = (javafx.scene.text.Text) flow.getChildren().getLast();
                assertTrue(caption.getText().endsWith("..."));
                assertEquals("  12分钟前", age.getText());
                assertTrue(age.getFont().getSize() < caption.getFont().getSize());
                assertEquals(title.getText(), title.getTooltip().getText());
                assertTrue(flow.prefHeight(170) <= 36);
                controller.close();
            } catch (java.io.IOException error) { throw new AssertionError(error); }
        });
    }

    @Test
    void hostPickerExcludesDirectConnectionDeduplicatesQuickHostsAndRetainsClosedAssociation() throws Exception {
        runFx(() -> {
            var saved = com.ynpe.fastremote.domain.catalog.SavedHost.create("Library host", java.util.Optional.empty(),
                    new com.ynpe.fastremote.domain.connection.ConnectionProfile(
                            new com.ynpe.fastremote.domain.connection.Host("saved.example"),
                            new com.ynpe.fastremote.domain.connection.Port(22),
                            new com.ynpe.fastremote.domain.connection.Username("ops"),
                            com.ynpe.fastremote.domain.connection.AuthenticationType.PASSWORD, java.util.Optional.empty()));
            var quick = new com.ynpe.fastremote.domain.ai.AiHostAssociation(java.util.Optional.empty(), "", "ops", "192.0.2.10");
            var selector = new AiHostAssociationSelector(new javafx.scene.control.MenuButton(), ignored -> { });
            selector.update(java.util.List.of(saved), java.util.List.of(), java.util.List.of(quick, quick), java.util.Optional.of(quick));
            assertEquals(3, selector.picker().results().getItems().size());
            assertEquals(1, selector.picker().results().getItems().stream().filter(option -> option.id().isEmpty()).count());
            assertEquals(java.util.Optional.of(quick.targetId()), selector.picker().selectedHostId());
            selector.update(java.util.List.of(saved), java.util.List.of(), java.util.List.of(), java.util.Optional.of(quick));
            assertEquals(3, selector.picker().results().getItems().size());
            assertEquals(java.util.Optional.of(quick.targetId()), selector.picker().selectedHostId());
        });
    }

    @Test
    void selectingConversationLoadsItOnceWithoutWaitingForRowClick() throws Exception {
        runFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(MainView.aiAssistantFxml(), ResourceBundle.getBundle(
                        com.ynpe.fastremote.i18n.Localizer.BUNDLE_NAME, java.util.Locale.ENGLISH));
                loader.load();
                var controller = (AiAssistantController) loader.getController();
                var selected = new java.util.ArrayList<java.util.UUID>();
                controller.bind(new NoOpActions() {
                    @Override public void selectChat(java.util.UUID id) { selected.add(id); }
                });
                var now = java.time.Instant.now();
                var first = new com.ynpe.fastremote.domain.ai.AiChatSession(java.util.UUID.randomUUID(), "First", now, now, 0);
                var second = new com.ynpe.fastremote.domain.ai.AiChatSession(java.util.UUID.randomUUID(), "Second", now, now, 0);
                controller.updateSessions(java.util.List.of(first, second));
                controller.sessionList().getSelectionModel().select(first);
                controller.sessionList().getSelectionModel().select(second);
                controller.sessionList().getSelectionModel().select(second);
                controller.sessionList().refresh();
                assertEquals(java.util.List.of(first.id(), second.id()), selected);
                controller.close();
            } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        });
    }

    private static void runFx(Runnable action) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable error) { failure.set(error); }
            finally { completed.countDown(); }
        });
        // Cold FXML/CSS initialization under Windows x64 translation can exceed five seconds.
        // These assertions verify layout, not startup performance; keep the wait bounded.
        assertTrue(completed.await(30, TimeUnit.SECONDS), "JavaFX layout task did not finish within 30 seconds");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static class NoOpActions implements AiAssistantController.Actions {
        @Override public void togglePlacement() { }
        @Override public void createChat() { }
        @Override public void configure() { }
        @Override public void sendOrInterrupt() { }
        @Override public void imageStateChanged() { }
        @Override public void imageError(String message) { throw new AssertionError(message); }
    }
}
