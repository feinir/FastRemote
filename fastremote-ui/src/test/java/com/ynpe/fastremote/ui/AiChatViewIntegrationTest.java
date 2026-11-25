// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.*;

class AiChatViewIntegrationTest {
    @Test void conversationFrameIsRoundedAndQuieterThanComposerInEveryTheme() throws Exception {
        AiChatView view = fx(() -> {
            var chat = new AiChatView(ignored -> {}, ignored -> {}, ignored -> {});
            var host = new StackPane(chat); host.getStyleClass().add("ai-chat-host");
            var composer = new javafx.scene.layout.VBox(new javafx.scene.control.Label("Message input"));
            composer.getStyleClass().add("ai-composer"); composer.setPrefHeight(90);
            var root = new javafx.scene.layout.VBox(8, host, composer);
            javafx.scene.layout.VBox.setVgrow(host, javafx.scene.layout.Priority.ALWAYS);
            root.setStyle("-fx-background-color: -fr-bg; -fx-padding: 12px;");
            new Scene(root, 640, 480);
            root.getStylesheets().add(MainView.stylesheet().toExternalForm());
            chat.appendMessage(new AiChatView.Message("u", "USER", "User", "Check the server status.", true));
            chat.appendMessage(new AiChatView.Message("a", "ASSISTANT", "Assistant", "The server is running.\n\n```sh\nuptime\n```", true));
            root.applyCss(); root.layout();
            return chat;
        });
        try {
            ready(view);
            fx(() -> {
                var host = (StackPane) view.getParent();
                var root = (javafx.scene.layout.VBox) host.getParent();
                var composer = (javafx.scene.layout.VBox) root.getChildren().getLast();
                for (String theme : List.of("light", "dark", "slate")) {
                    root.getStylesheets().setAll(MainView.stylesheet().toExternalForm());
                    if (!theme.equals("light")) root.getStylesheets().add(AiChatView.class
                            .getResource("theme-" + theme + ".css").toExternalForm());
                    root.applyCss(); root.layout();
                    var border = host.getBorder().getStrokes().getFirst();
                    assertEquals(1.0, border.getWidths().getTop());
                    assertEquals(12.0, border.getRadii().getTopLeftHorizontalRadius());
                    var background = (javafx.scene.paint.Color) host.getBackground().getFills().getFirst().getFill();
                    var outline = (javafx.scene.paint.Color) border.getTopStroke();
                    var inputOutline = (javafx.scene.paint.Color) composer.getBorder().getStrokes().getFirst().getTopStroke();
                    assertTrue(Math.abs(outline.getBrightness() - background.getBrightness())
                            < Math.abs(inputOutline.getBrightness() - background.getBrightness()), theme);
                    assertTrue(view.getBoundsInParent().getMinX() >= 4);
                    var pixels = root.snapshot(null, null);
                    var image = new java.awt.image.BufferedImage((int) pixels.getWidth(), (int) pixels.getHeight(),
                            java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < image.getHeight(); y++)
                        for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, pixels.getPixelReader().getArgb(x, y));
                    var output = java.nio.file.Path.of("build/chat-frame-preview/" + theme + ".png");
                    java.nio.file.Files.createDirectories(output.getParent());
                    javax.imageio.ImageIO.write(image, "png", output.toFile());
                }
                return null;
            });
        } finally { fx(() -> { view.close(); return null; }); }
    }

    // Copy assertions must never overwrite the desktop user's clipboard.
    private final java.util.concurrent.atomic.AtomicReference<String> copiedText = new java.util.concurrent.atomic.AtomicReference<>();
    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS)); Platform.setImplicitExit(false);
        fx(() -> javafx.scene.text.Font.loadFont(Objects.requireNonNull(AiChatView.class.getResource(
                "/com/ynpe/fastremote/terminal/fonts/JetBrainsMonoNL-Regular.ttf")).toExternalForm(), 12));
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(20, TimeUnit.SECONDS);
    }
    private static void ready(AiChatView view) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!fx(view::readyForTesting)) { assertTrue(System.nanoTime() < until, "Document worker did not finish"); Thread.sleep(25); }
        fx(() -> { view.applyCss(); view.layout(); return null; });
    }
    private AiChatView create() throws Exception {
        return fx(() -> {
            AiChatView view = new AiChatView(message -> fail(message), ignored -> { }, copiedText::set);
            StackPane root = new StackPane(view); Scene scene = new Scene(root, 640, 480);
            InterfaceFontPolicy.apply(root);
            scene.getStylesheets().add(Objects.requireNonNull(AiChatView.class.getResource("main.css")).toExternalForm());
            root.applyCss(); root.resize(640, 480); root.layout(); return view;
        });
    }
    @Test void nativeSelectionCrossesParagraphCodeTableAndMessageBoundaries() throws Exception {
        AiChatView view = create();
        try {
            fx(() -> {
                view.appendMessage(new AiChatView.Message("one", "ASSISTANT", "AI", "Hello\n\n```java\nint n = 1;\n```\n\n| A | B |\n|---|---|\n|甲|乙|", true));
                view.appendMessage(new AiChatView.Message("two", "USER", "Me", "再见🙂", true)); return null;
            }); ready(view);
            fx(() -> {
                var content = view.documentView(); String text = content.document().text();
                content.select(text.indexOf("Hello"), text.length());
                assertEquals("Hello\nint n = 1;\nA\tB\n甲\t乙\n再见🙂", content.selectedText());
                content.copySelection(); assertEquals(content.selectedText(), copiedText.get());
                assertEquals(text, content.queryAccessibleAttribute(javafx.scene.AccessibleAttribute.TEXT));
                return null;
            });
        } finally { fx(() -> { view.close(); return null; }); }
    }
    @Test void streamingOperationsStayInEventOrderAcrossUserSteering() throws Exception {
        AiChatView view = create();
        try {
            fx(() -> {
                view.appendMessage(new AiChatView.Message("a", "ASSISTANT", "AI", "Before", true, "stream:run"));
                view.appendMessage(new AiChatView.Message("t", "TOOL", "Tool", "Inspecting host", false, "stream:run"));
                view.appendMessage(new AiChatView.Message("u", "USER", "Me", "Use host B", true, "stream:run"));
                view.appendMessage(new AiChatView.Message("b", "ASSISTANT", "AI", "After", false, "stream:run"));
                return null;
            }); ready(view);
            fx(() -> {
                String text = view.documentView().document().text();
                assertTrue(text.indexOf("Before") < text.indexOf("Inspecting host"));
                assertTrue(text.indexOf("Inspecting host") < text.indexOf("Use host B"));
                assertTrue(text.indexOf("Use host B") < text.indexOf("After")); return null;
            });
        } finally { fx(() -> { view.close(); return null; }); }
    }

    @Test void streamingPreservesSelectionAndClearRejectsOldParserResults() throws Exception {
        AiChatView view = create();
        try {
            fx(() -> { view.appendMessage(new AiChatView.Message("m", "ASSISTANT", "AI", "**hello", false)); return null; }); ready(view);
            fx(() -> { var d = view.documentView(); int pos = d.document().text().indexOf("hello"); d.select(pos, pos + 5);
                view.updateMessage("m", "**hello** world", true); return null; }); ready(view);
            fx(() -> { assertEquals("hello", view.documentView().selectedText());
                view.updateMessage("m", "x".repeat(512 * 1024), false); view.clearConversation();
                view.appendMessage(new AiChatView.Message("new", "USER", "Me", "fresh", true)); return null; }); ready(view);
            fx(() -> { assertEquals("fresh", view.documentView().document().text()); return null; });
        } finally { fx(() -> { view.close(); view.close(); return null; }); }
    }
    @Test void historyAndSceneGraphStayBounded() throws Exception {
        AiChatView view = create();
        try {
            fx(() -> { for (int i = 0; i < 120; i++) view.appendMessage(new AiChatView.Message("m" + i, "ASSISTANT", "AI", ("line " + i + "\n\n").repeat(100), true)); return null; }); ready(view);
            fx(() -> { assertEquals(100, view.messageCount()); assertTrue(view.documentView().document().rows().size() >= 10000);
                assertTrue(view.documentView().visibleCellCount() < 100); return null; });
        } finally { fx(() -> { view.close(); return null; }); }
    }
    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, javafx.geometry.Point2D point, MouseButton button, boolean down) {
        return new MouseEvent(type, point.getX(), point.getY(), point.getX(), point.getY(), button, 1,
                false, false, false, false, down, false, button == MouseButton.SECONDARY,
                false, false, true, null);
    }
    @Test void pointerDragCrossesNativeCellsAndSecondaryClickPreservesSelection() throws Exception {
        AiChatView view = create();
        try {
            fx(() -> { view.appendMessage(new AiChatView.Message("m", "ASSISTANT", "AI", "Hello world\n\n```sh\necho test\n```\n\n| A | B |\n|---|---|\n|甲|乙|\n\nFinish", true)); return null; }); ready(view);
            fx(() -> {
                var content = view.documentView(); content.applyCss(); content.layout();
                var texts = content.lookupAll(".chat-text").stream().map(n -> (javafx.scene.text.Text)n).toList();
                var hello = texts.stream().filter(t -> t.getText().equals("Hello world")).findFirst().orElseThrow();
                var finish = texts.stream().filter(t -> t.getText().equals("Finish")).findFirst().orElseThrow();
                var hb = hello.getBoundsInLocal(); var fb = finish.getBoundsInLocal();
                var start = hello.localToScene(hb.getMinX() + .1, (hb.getMinY() + hb.getMaxY()) / 2);
                var end = finish.localToScene(fb.getMaxX() - .1, (fb.getMinY() + fb.getMaxY()) / 2);
                Event.fireEvent(content, mouse(MouseEvent.MOUSE_PRESSED, start, MouseButton.PRIMARY, true));
                Event.fireEvent(content, mouse(MouseEvent.MOUSE_DRAGGED, end, MouseButton.PRIMARY, true));
                Event.fireEvent(content, mouse(MouseEvent.MOUSE_RELEASED, end, MouseButton.PRIMARY, false));
                assertEquals("Hello world\necho test\nA\tB\n甲\t乙\nFinish", content.selectedText());
                Event.fireEvent(content, mouse(MouseEvent.MOUSE_PRESSED, start, MouseButton.SECONDARY, false));
                assertTrue(content.selectedText().endsWith("Finish"));
                return null;
            });
        } finally { fx(() -> { view.close(); return null; }); }
    }
    @Test void unicodeKeyboardSelectionAndDocumentSnapshots() throws Exception {
        AiChatView view = create();
        try {
            fx(() -> {
                view.appendMessage(new AiChatView.Message("previous", "ASSISTANT", "AI", "Ready when you are.", true));
                view.appendMessage(new AiChatView.Message("u", "USER", "Me", "Show a compact example with code and a table.", true));
                view.appendMessage(new AiChatView.Message("m", "ASSISTANT", "AI", "# Native chat\n\n**Streaming** text, 中文 and 🙂.\n\n> A selectable quotation\n\n```java\nString greeting = \"Hello\";\nSystem.out.println(greeting);\n```\n\n| Platform | Renderer |\n|---|---|\n| Windows ARM64 | Native |\n| macOS / Linux | Native |\n\nSelection crosses blocks. 👩‍💻", true)); return null; }); ready(view);
            fx(() -> {
                var content = view.documentView(); int end = content.document().text().length(); content.select(end, end);
                Event.fireEvent(content, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.LEFT, true, false, false, false));
                assertEquals("👩‍💻", content.selectedText());
                String snapshots = System.getenv("FASTREMOTE_CHAT_SNAPSHOTS");
                if (snapshots != null) {
                    var root = (StackPane)view.getScene().getRoot(); root.resize(760, 620); root.applyCss(); root.layout();
                    view.scrollToBottom(); root.layout();
                    assertTrue(content.lookupAll(".chat-row").size() >= 10, "A short document must fit the viewport");
                    var target = java.nio.file.Path.of(snapshots); java.nio.file.Files.createDirectories(target);
                    javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png", target.resolve("chat-light.png").toFile());
                    view.getScene().getStylesheets().add(Objects.requireNonNull(AiChatView.class.getResource("theme-dark.css")).toExternalForm());
                    root.applyCss(); root.layout();
                    javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png", target.resolve("chat-dark.png").toFile());
                }
                return null;
            });
        } finally { fx(() -> { view.close(); return null; }); }
    }
    private static javafx.scene.control.Button codeCopy(AiChatView view, String language) {
        return view.lookupAll(".chat-code-copy").stream().map(n -> (javafx.scene.control.Button)n)
                .filter(b -> ((javafx.scene.control.Label)b.getParent().getChildrenUnmodifiable().getFirst()).getText().equals(language))
                .findFirst().orElseThrow();
    }
    @Test void independentCodeButtonsCopyLatestRawBlockAndPreserveDocumentSelection() throws Exception {
        AiChatView view = create();
        try {
            fx(() -> { view.appendMessage(new AiChatView.Message("m", "ASSISTANT", "AI", "Intro\n\n```sh\n  echo one  \n\n\techo two\n```\n\n```java\nint n;\n```", false)); return null; }); ready(view);
            fx(() -> {
                var content = view.documentView(); content.select(0, 5);
                var button = codeCopy(view, "sh");
                Event.fireEvent(button, mouse(MouseEvent.MOUSE_PRESSED, button.localToScene(5, 5), MouseButton.PRIMARY, true));
                button.fire();
                assertEquals("  echo one  \n\n\techo two\n", copiedText.get());
                assertEquals("Intro", content.selectedText());
                codeCopy(view, "java").fire(); assertEquals("int n;\n", copiedText.get());
                assertFalse(content.document().text().contains("Copy"));
                view.updateMessage("m", "Intro\n\n```sh\necho updated\n```\n\n```java\nint n;\n```", true); return null;
            }); ready(view);
            fx(() -> { codeCopy(view, "sh").fire(); assertEquals("echo updated\n", copiedText.get());
                assertEquals("Intro", view.documentView().selectedText()); return null; });
        } finally { fx(() -> { view.close(); return null; }); }
    }
    @Test void humanBubbleAlignsRightAcrossParagraphsAndResizesWhileCodeLinesStayCompact() throws Exception {
        AiChatView view = create();
        try {
            fx(() -> {
                view.appendMessage(new AiChatView.Message("u", "USER", "Me", "A short question\n\nA longer second paragraph with 中文 and 🙂.", true));
                view.appendMessage(new AiChatView.Message("a", "ASSISTANT", "AI", "Answer\n\n```sh\necho one\necho two\necho three\n```", true)); return null;
            }); ready(view);
            fx(() -> {
                var root = (StackPane)view.getScene().getRoot();
                for (double width : new double[]{640, 320, 760}) {
                    root.resize(width, 480); root.applyCss(); root.layout();
                    var users = view.lookupAll(".chat-user").stream().map(n -> (javafx.scene.layout.VBox)n).toList();
                    assertEquals(2, users.size());
                    double bubbleWidth = ((javafx.scene.layout.Region)users.getFirst().getChildren().getFirst()).getWidth();
                    for (var user : users) {
                        var bubble = (javafx.scene.layout.Region)user.getChildren().getFirst();
                        assertEquals(bubbleWidth, bubble.getWidth(), 1);
                        var bounds = bubble.localToScene(bubble.getLayoutBounds());
                        assertTrue(bounds.getMinX() > 35, "Human messages should sit on the right");
                        assertEquals(width - 14, bounds.getMaxX(), 2);
                        assertTrue(bubble.getWidth() <= (width - 28) * .85);
                    }
                    var answer = view.lookupAll(".chat-text").stream().map(n -> (javafx.scene.text.Text)n)
                            .filter(t -> t.getText().equals("Answer")).findFirst().orElseThrow();
                    assertEquals(26, answer.localToScene(answer.getBoundsInLocal()).getMinX(), 1);
                    var code = view.lookupAll(".chat-row").stream().map(n -> (javafx.scene.layout.Region)n)
                            .filter(n -> n.getStyleClass().contains("chat-code") && !n.getStyleClass().contains("chat-code-first")
                                    && !n.getStyleClass().contains("chat-code-last")).findFirst().orElseThrow();
                    assertTrue(code.getHeight() <= 21, "Ordinary code lines should not have paragraph spacing");
                }
                return null;
            });
        } finally { fx(() -> { view.close(); return null; }); }
    }
    @Test void reasoningAndToolGroupsAreCollapsedAfterAnswerAndKeyboardExpandable() throws Exception {
        AiChatView view = create();
        try {
            fx(() -> {
                view.appendMessage(new AiChatView.Message("r", "REASONING", "AI", "private thought", true, "turn"));
                view.appendMessage(new AiChatView.Message("t", "TOOL", "Tool", "operation result", true, "turn"));
                view.appendMessage(new AiChatView.Message("a", "ASSISTANT", "AI", "answer", true, "turn")); return null;
            }); ready(view);
            fx(() -> { var content = view.documentView(); var doc = content.document(); assertFalse(doc.text().contains("private thought")); assertFalse(doc.text().contains("operation result"));
                int index = doc.index("fold:turn:REASONING"); assertTrue(index > doc.index("a:0"));
                content.select(doc.start(index), doc.start(index));
                Event.fireEvent(content, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false)); return null;
            }); ready(view);
            fx(() -> { assertTrue(view.documentView().document().text().contains("private thought")); return null; });
        } finally { fx(() -> { view.close(); return null; }); }
    }
}
