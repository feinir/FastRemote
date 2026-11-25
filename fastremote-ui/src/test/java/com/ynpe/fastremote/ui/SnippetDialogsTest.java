// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SnippetDialogsTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    @Test
    void editorUsesTheSharedEnglishBaselineLabelColumn() {
        assertEquals(85.0, SnippetDialogs.FORM_LABEL_WIDTH);
        assertEquals(44.0, SnippetDialogs.FORM_ROW_HEIGHT);
        assertEquals(240.0, SnippetDialogs.COMMAND_CONTENT_HEIGHT);
        assertEquals(720.0, SnippetDialogs.DIALOG_WIDTH);
        assertEquals(760.0, SnippetDialogs.DIALOG_HEIGHT);
    }

    @Test
    void templateHelpListsEveryTemplateAndSupportedVariant() {
        assertEquals(5, SnippetDialogs.TEMPLATE_HELP_LINES.size());
        assertEquals(List.of("input", "variable", "session", "now", "random"),
                SnippetDialogs.TEMPLATE_HELP_LINES.stream()
                        .map(line -> line.substring(2, line.indexOf(',')))
                        .toList());
        assertTrue(SnippetDialogs.TEMPLATE_HELP_LINES.getFirst().contains(
                "default=1.1.1.1,variable=server_list,required=true"));
        assertTrue(SnippetDialogs.TEMPLATE_HELP_LINES.get(1).contains("index=0"));

        String session = SnippetDialogs.TEMPLATE_HELP_LINES.get(2);
        List.of("name", "host", "port", "username").forEach(field -> assertTrue(session.contains(field)));

        String random = SnippetDialogs.TEMPLATE_HELP_LINES.get(4);
        List.of("string,10", "number,10", "hex,10", "lower,10", "upper,10", "IP", "float,3.4",
                        "int,7", "int,3-7", "uuid}}", "uuid,non")
                .forEach(variant -> assertTrue(random.contains(variant)));
    }

    @Test
    void encodingHelpEndsWithTheExamplesOnTheSameLine() {
        assertTrue(SnippetDialogs.ENCODING_HELP.endsWith(
                "示例：{{variable,backup_dir|url}}、{{random,uuid|upper}}"));
        assertFalse(SnippetDialogs.ENCODING_HELP.contains("\n"));
    }

    @Test
    void editableCandidateChoiceOpensWithTheDownArrowAndUsesItsFocusedStyle() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                RecordingComboBox choices = new RecordingComboBox();
                SnippetDialogs.configureCandidateChoice(choices);

                assertTrue(choices.isEditable());
                assertTrue(choices.getStyleClass().contains("snippet-input-choice"));
                KeyEvent down = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN,
                        false, false, false, false);
                choices.getEditor().fireEvent(down);

                assertEquals(1, choices.showCalls);
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void commandFieldsKeepFixedWidthAndOneSharedFrameInBothThemes() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                ComboBox<String> choices = new ComboBox<>();
                SnippetDialogs.configureCandidateChoice(choices);
                javafx.scene.control.TextField text = new javafx.scene.control.TextField("https://www.baidu.com");
                SnippetDialogs.configureInputField(text);
                var root = new javafx.scene.layout.VBox(10, choices, text);
                var scene = new javafx.scene.Scene(root, 700, 160);
                scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
                for (boolean dark : new boolean[] {false, true}) {
                    if (dark) scene.getStylesheets().add(MainView.darkStylesheet().toExternalForm());
                    for (String value : List.of("https://www.baidu.com", "https://example.com/" + "long-path/".repeat(80))) {
                        choices.getItems().setAll(value, "https://www.google.com");
                        choices.getSelectionModel().selectFirst();
                        root.applyCss();
                        root.layout();
                        assertEquals(300, choices.getWidth(), 0.1);
                        assertEquals(300, text.getWidth(), 0.1);
                        assertEquals(text.getHeight(), choices.getHeight(), 0.1);
                        assertEquals(text.getBorder().getStrokes().getFirst().getTopStroke(),
                                choices.getBorder().getStrokes().getFirst().getTopStroke());
                        assertTrue(choices.getEditor().getBorder() == null
                                || choices.getEditor().getBorder().getStrokes().stream()
                                        .allMatch(stroke -> stroke.getWidths().getTop() == 0));
                        assertTrue(choices.getEditor().getEffect() == null);
                        assertEquals(value, choices.getEditor().getText());
                    }
                    choices.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("editor-focused"), true);
                    text.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), true);
                    root.applyCss();
                    assertEquals(text.getBorder().getStrokes().getFirst().getTopStroke(),
                            choices.getBorder().getStrokes().getFirst().getTopStroke());
                    choices.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("editor-focused"), false);
                    text.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), false);
                }
            } catch (Throwable error) { failure.set(error); }
            finally { completed.countDown(); }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static final class RecordingComboBox extends ComboBox<String> {
        private int showCalls;

        @Override
        public void show() {
            showCalls++;
        }
    }
}
