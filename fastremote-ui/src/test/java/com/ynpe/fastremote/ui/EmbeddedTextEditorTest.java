// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.settings.SftpSettings;
import com.ynpe.fastremote.domain.settings.EditorTheme;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EmbeddedTextEditorTest {
    @Test
    void commandPalettePathPasteWritesAtTheEditorSelectionWithoutSubmitting() throws Exception {
        onFx(() -> {
            EmbeddedTextEditor editor = new EmbeddedTextEditor(SftpSettings.defaults(), "note.txt");
            try {
                editor.setText("before after");
                editor.editorArea().selectRange(7, 12);
                editor.insertText("/srv/app");
                assertEquals("before /srv/app", editor.text());
            } finally {
                editor.dispose();
            }
        });
    }

    @Test
    void preservesMixedNewlinesThroughEditsUndoRedoAndReload() throws Exception {
        onFx(() -> {
            EmbeddedTextEditor editor = new EmbeddedTextEditor(SftpSettings.defaults(), "notes.txt");
            String raw = "first\r\nsecond\rthird\nfourth\r\n";
            editor.setText(raw);
            EditorFileContent baseline = editor.content();
            assertEquals(raw, baseline.fileText());
            assertEquals("first\nsecond\nthird\nfourth\n", editor.text());
            assertFalse(editor.editorArea().isUndoAvailable());
            editor.editorArea().replaceText(5, 19, "!");
            assertEquals("first!fourth\r\n", editor.content().fileText());
            editor.editorArea().undo();
            assertEquals(baseline, editor.content());
            editor.editorArea().redo();
            assertEquals("first!fourth\r\n", editor.content().fileText());
            editor.editorArea().undo();
            editor.setText("loaded\rnew");
            assertFalse(editor.editorArea().isUndoAvailable());
            assertFalse(editor.editorArea().isRedoAvailable());
            editor.editorArea().appendText("\nline");
            assertEquals("loaded\rnew\rline", editor.content().fileText());
            editor.dispose();
        });
    }

    @Test
    void compoundEditsAndUndoPreserveUntouchedDelimiters() throws Exception {
        onFx(() -> {
            EmbeddedTextEditor editor = new EmbeddedTextEditor(SftpSettings.defaults(), "notes.txt");
            editor.setText("a\r\nb\rc\nd");
            EditorFileContent original = editor.content();
            var changes = editor.editorArea().createMultiChange(2);
            changes.replaceTextAbsolutely(1, 2, "");
            changes.replaceTextAbsolutely(4, 5, "\nX\n");
            changes.commit();
            assertEquals("ab\rc\r\nX\r\nd", editor.content().fileText());
            editor.editorArea().undo();
            assertEquals(original, editor.content());
            editor.editorArea().redo();
            assertEquals("ab\rc\r\nX\r\nd", editor.content().fileText());
            editor.dispose();
        });
    }

    @Test
    void uniformFilesRetainFormatAndManualRevertMatchesBaseline() throws Exception {
        onFx(() -> {
            for (String delimiter : List.of("\n", "\r\n", "\r")) {
                EmbeddedTextEditor editor = new EmbeddedTextEditor(SftpSettings.defaults(), "notes.txt");
                editor.setText("a" + delimiter + "b" + delimiter);
                EditorFileContent original = editor.content();
                editor.editorArea().replaceText(0, 1, "z");
                assertFalse(original.equals(editor.content()));
                assertEquals("z" + delimiter + "b" + delimiter, editor.content().fileText());
                editor.editorArea().replaceText(0, 1, "a");
                assertEquals(original, editor.content());
                editor.dispose();
            }
        });
    }

    @Test
    void delimiterOnlyChangesRemainDirtyEvenWhenNormalizedTextMatches() throws Exception {
        onFx(() -> {
            EmbeddedTextEditor editor = new EmbeddedTextEditor(SftpSettings.defaults(), "notes.txt");
            editor.setText("a\r\nb\rc");
            EditorFileContent baseline = editor.content();
            editor.editorArea().deleteText(3, 4);
            editor.editorArea().insertText(3, "\n");
            assertEquals(baseline.text(), editor.text());
            assertFalse(baseline.equals(editor.content()));
            assertEquals("a\r\nb\r\nc", editor.content().fileText());
            editor.editorArea().undo();
            editor.editorArea().undo();
            assertEquals(baseline, editor.content());
            editor.dispose();
        });
    }

    @Test
    void undoHistoryHasTransactionAndLargeEditLimits() throws Exception {
        onFx(() -> {
            var area = new org.fxmisc.richtext.CodeArea();
            var binding = new EditorNewlineBinding(area);
            binding.load("x\r\n");
            for (int i = 0; i < 510; i++) area.appendText("\n");
            int undone = 0;
            while (area.isUndoAvailable()) { area.undo(); undone++; }
            assertEquals(500, undone);
            assertEquals("x\r\n" + "\r\n".repeat(10), binding.content().fileText());
            binding.load("x\r\n");
            area.appendText("y".repeat(4 * 1024 * 1024 + 1));
            assertFalse(area.isUndoAvailable());
            area.appendText("\n");
            assertTrue(area.isUndoAvailable());
            area.undo();
            assertEquals(1, binding.content().endings().size());
            binding.close();
            area.dispose();
        });
    }

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void providesLineNumbersRealWrappingAndDarkDefaultTheme() throws Exception {
        onFx(() -> {
            EmbeddedTextEditor editor = new EmbeddedTextEditor(SftpSettings.defaults(), "notes.txt");
            assertNotNull(editor.editorArea().getParagraphGraphicFactory());
            assertTrue(editor.isWrapText());
            assertTrue(editor.getStyleClass().contains("editor-theme-dark"));
            assertTrue(editor.findControl().getStyleClass().contains("editor-search-combo-dark"));
            assertTrue(editor.findControl().getEditor().getContextMenu()
                    .getStyleClass().contains("editor-context-dark"));
            assertTrue(editor.contextMenu().getStyleClass().contains("editor-context-dark"));
            assertEquals(ScrollPane.ScrollBarPolicy.ALWAYS,
                    editor.editorScrollPane().getVbarPolicy());
            editor.setWrapText(false);
            editor.setWrapText(true);
            assertTrue(editor.editorArea().isWrapText());
            SftpSettings defaults = SftpSettings.defaults();
            SftpSettings light = new SftpSettings(defaults.showHiddenFiles(), defaults.concurrentTransfers(),
                    defaults.globalBookmarks(), defaults.hostBookmarks(), defaults.maxEditableFileBytes(),
                    defaults.editorFontFamily(), defaults.editorFontSize(), EditorTheme.LIGHT);
            editor.applySettings(light);
            assertTrue(editor.getStyleClass().contains("editor-theme-light"));
            StackPane editorLayer = (StackPane) editor.getCenter();
            assertTrue(editorLayer.getStyleClass().contains("embedded-editor-layer"));
            assertTrue(editorLayer.getChildren().getFirst().getStyleClass().contains("embedded-editor-scroll"));
            assertTrue(editor.findControl().getStyleClass().contains("editor-search-combo-light"));
            assertTrue(editor.findControl().getEditor().getContextMenu()
                    .getStyleClass().contains("editor-context-light"));
            assertTrue(editor.contextMenu().getStyleClass().contains("editor-context-light"));
        });
    }

    @Test
    void independentLightThemeCoversVirtualBlankAreaAndOverridesDarkApplicationMenu() throws Exception {
        String baseCss = new String(MainView.stylesheet().openStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        String darkCss = new String(MainView.darkStylesheet().openStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(baseCss.contains(".editor-theme-light .embedded-editor-layer"));
        assertTrue(baseCss.contains(".editor-theme-light .embedded-editor-scroll"));
        assertTrue(baseCss.contains(".editor-theme-light .virtual-flow"));
        assertTrue(baseCss.contains(
                ".embedded-text-editor.editor-theme-light .embedded-editor-search .check-box:selected > .box"));
        assertTrue(baseCss.contains(
                ".embedded-text-editor.editor-theme-light .embedded-editor-search .check-box:selected > .box > .mark"));
        assertTrue(darkCss.contains(
                ".context-menu.embedded-editor-context-menu.editor-context-light"));
        assertTrue(darkCss.contains("editor-context-light .menu-item .label"));
        assertTrue(darkCss.contains("editor-context-light .separator > .line"));
        assertTrue(darkCss.contains("-fx-border-width: 1px 0 0 0;"));
    }

    @Test
    void lineNumbersUseConfiguredItalicFaceOrSansFallback() {
        assertEquals("JetBrains Mono", EmbeddedTextEditor.lineNumberFontFamily(
                "JetBrains Mono", List.of("JetBrains Mono Regular", "JetBrains Mono Italic")));
        assertEquals(InterfaceFontPolicy.interfaceFontFamily(
                        com.ynpe.fastremote.i18n.Texts.localizer().resolvedLanguage().persistedTag()),
                EmbeddedTextEditor.lineNumberFontFamily(
                "Bitmap Mono", List.of("Bitmap Mono Regular")));
    }

    @Test
    void contextMenuDisplaysStandardEditorShortcuts() throws Exception {
        onFx(() -> {
            var items = new EmbeddedTextEditor(SftpSettings.defaults(), "notes.txt")
                    .contextMenu().getItems();
            assertEquals(KeyCombination.valueOf("Shortcut+X"), items.get(0).getAccelerator());
            assertEquals(KeyCombination.valueOf("Shortcut+C"), items.get(1).getAccelerator());
            assertEquals(KeyCombination.valueOf("Shortcut+V"), items.get(2).getAccelerator());
            assertEquals(KeyCombination.valueOf("Shortcut+F"), items.get(4).getAccelerator());
            assertEquals(KeyCombination.valueOf("Shortcut+R"), items.get(5).getAccelerator());
            assertEquals(KeyCombination.valueOf("Shortcut+A"), items.get(7).getAccelerator());
        });
    }

    @Test
    void findAndReplaceShortcutsAreNotHandledAgainInsideTheEditor() throws Exception {
        onFx(() -> {
            EmbeddedTextEditor editor = new EmbeddedTextEditor(SftpSettings.defaults(), "notes.txt");

            KeyEvent find = shortcutPressed(KeyCode.F);
            Event.fireEvent(editor, find);
            assertFalse(find.isConsumed());
            assertFalse(editor.searchPanel().isVisible());

            KeyEvent replace = shortcutPressed(KeyCode.R);
            Event.fireEvent(editor, replace);
            assertFalse(replace.isConsumed());
            assertFalse(editor.searchPanel().isVisible());
        });
    }

    @Test
    void lightEditorPopupUsesTheActualSeparatorSkinNodeWithLightColorAndSpacing() throws Exception {
        onFx(() -> {
            SftpSettings defaults = SftpSettings.defaults();
            SftpSettings light = new SftpSettings(defaults.showHiddenFiles(), defaults.concurrentTransfers(),
                    defaults.globalBookmarks(), defaults.hostBookmarks(), defaults.maxEditableFileBytes(),
                    defaults.editorFontFamily(), defaults.editorFontSize(), EditorTheme.LIGHT);
            EmbeddedTextEditor editor = new EmbeddedTextEditor(light, "notes.txt");
            javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(editor);
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 700, 500);
            scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
            ThemeManager.install(scene);
            ThemeManager.setTheme(com.ynpe.fastremote.domain.settings.ApplicationTheme.DARK);
            javafx.stage.Stage stage = new javafx.stage.Stage();
            try {
                stage.setScene(scene);
                stage.show();
                editor.showFind();
                ContextMenu searchInputMenu = editor.findControl().getEditor().getContextMenu();
                searchInputMenu.show(editor.findControl().getEditor(), 200, 200);
                javafx.scene.Parent popupRoot = (javafx.scene.Parent) searchInputMenu.getSkin().getNode();
                assertNotNull(popupRoot);
                popupRoot.applyCss();
                popupRoot.layout();

                javafx.scene.control.Separator separator =
                        (javafx.scene.control.Separator) popupRoot.lookup(".separator");
                assertNotNull(separator);
                javafx.scene.layout.Region line =
                        (javafx.scene.layout.Region) separator.lookup(".line");
                assertNotNull(line);
                assertEquals(new javafx.geometry.Insets(4.0, 3.0, 4.0, 3.0), separator.getPadding());
                assertEquals(1.0, line.getHeight(), 0.01);
                assertTrue(line.getBackground().getFills().stream()
                        .allMatch(fill -> javafx.scene.paint.Color.TRANSPARENT.equals(fill.getFill())));
                javafx.scene.layout.BorderStroke stroke = line.getBorder().getStrokes().getFirst();
                assertEquals(javafx.scene.paint.Color.web("#d8dee8"), stroke.getTopStroke());
                assertEquals(1.0, stroke.getWidths().getTop(), 0.01);
                assertTrue(line.getBoundsInParent().getMinY() >= 4.0);
                assertTrue(separator.getHeight() - line.getBoundsInParent().getMaxY() >= 4.0);
            } finally {
                editor.findControl().getEditor().getContextMenu().hide();
                stage.hide();
                ThemeManager.setTheme(com.ynpe.fastremote.domain.settings.ApplicationTheme.LIGHT);
            }
        });
    }

    @Test
    void cyclesMatchesAndSharesBoundedSearchAndReplaceHistory() throws Exception {
        onFx(() -> {
            var searches = FXCollections.observableArrayList("older search");
            var replacements = FXCollections.<String>observableArrayList();
            EmbeddedTextEditor editor = new EmbeddedTextEditor(SftpSettings.defaults(), "notes.txt");
            editor.setSearchHistories(searches, replacements);
            editor.setText("alpha beta alpha");
            editor.showFind();
            editor.findControl().getEditor().setText("alpha");

            editor.findNextForTest();
            assertEquals(0, editor.editorArea().getSelection().getStart());
            assertEquals("alpha", editor.findControl().getEditor().getText());
            editor.findNextForTest();
            assertEquals(11, editor.editorArea().getSelection().getStart());
            assertEquals("alpha", editor.findControl().getEditor().getText());
            editor.findNextForTest();
            assertEquals(0, editor.editorArea().getSelection().getStart());
            editor.findPreviousForTest();
            assertEquals(11, editor.editorArea().getSelection().getStart());

            editor.replaceControl().getEditor().setText("omega");
            editor.replaceCurrentForTest();
            editor.replaceAllForTest();
            assertEquals("omega beta omega", editor.text());
            assertEquals("alpha", searches.getFirst());
            assertEquals("omega", replacements.getFirst());

            EmbeddedTextEditor second = new EmbeddedTextEditor(SftpSettings.defaults(), "other.txt");
            second.setSearchHistories(searches, replacements);
            assertEquals("alpha", second.findControl().getItems().getFirst());
            assertEquals("omega", second.replaceControl().getItems().getFirst());
        });
    }

    @Test
    void searchPanelUsesCompactLeftAlignedRowsAndUniformContentSizedButtons() throws Exception {
        onFx(() -> {
            EmbeddedTextEditor editor = new EmbeddedTextEditor(SftpSettings.defaults(), "notes.txt");
            assertEquals(380.0, editor.searchPanel().getMinWidth());
            assertEquals(447.0, editor.searchPanel().getPrefWidth());
            assertEquals(513.0, editor.searchPanel().getMaxWidth());

            HBox buttonRow = (HBox) editor.searchPanel().getChildren().get(3);
            HBox optionRow = (HBox) editor.searchPanel().getChildren().get(4);
            assertEquals(72.0, buttonRow.getPadding().getLeft());
            assertEquals(72.0, optionRow.getPadding().getLeft());
            for (String id : List.of("#embedded-editor-find-previous", "#embedded-editor-find-next",
                    "#embedded-editor-replace-one", "#embedded-editor-replace-all")) {
                Button button = (Button) editor.lookup(id);
                assertNotNull(button);
                assertTrue(button.getStyleClass().contains("embedded-editor-search-action"));
                assertFalse(button.getStyleClass().contains("embedded-editor-search-primary"));
                assertEquals(Region.USE_COMPUTED_SIZE, button.getPrefWidth());
            }
        });
    }

    @Test
    void spacingUpdatesWithoutChangingContentSelectionOrUndo() throws Exception {
        onFx(() -> {
            EmbeddedTextEditor editor = new EmbeddedTextEditor(SftpSettings.defaults(), "note.txt");
            try {
                editor.setText("A中e\u0301👩‍💻Z\r\nsecond line");
                var area = editor.editorArea();
                StackPane root = new StackPane(editor);
                new javafx.scene.Scene(root, 700, 450);
                root.applyCss(); root.resize(700, 450); root.layout();
                area.selectRange(1, 4);
                var content = editor.content();
                editor.applySettings(SftpSettings.defaults().withEditorTextAppearance(
                        new com.ynpe.fastremote.domain.settings.TextAppearance(3, 3)));
                root.applyCss(); root.layout();
                assertEquals(content, editor.content());
                assertEquals("中e\u0301", area.getSelectedText());
                assertEquals(3, area.getLetterSpacing());
                assertEquals(3, area.getParagraphLineSpacing());
                assertTrue(area.lookupAll(".lineno").stream().anyMatch(node ->
                        node instanceof javafx.scene.control.Label label && label.getPadding().getTop() == 1.5));
                assertFalse(area.isUndoAvailable());
                assertFalse(area.getStyle().contains("bold"));
                area.replaceSelection("changed");
                area.undo();
                assertEquals(content, editor.content());
                editor.applySettings(SftpSettings.defaults());
                root.applyCss(); root.layout();
                assertEquals(0, area.getLetterSpacing());
                assertEquals(1, area.getParagraphLineSpacing());
            } finally { editor.dispose(); }
        });
    }

    @Test
    void typographyControlsNotifyAndResetEachSurfaceToItsOwnDefaults() throws Exception {
        onFx(() -> {
            for (boolean terminal : new boolean[]{true, false}) {
                var controls = new TextAppearanceControls(
                        new com.ynpe.fastremote.domain.settings.TextAppearance(2, 2),
                        terminal, () -> "Monospaced", () -> 14);
                var changes = new java.util.concurrent.atomic.AtomicInteger();
                controls.onChange(changes::incrementAndGet);
                @SuppressWarnings("unchecked") var spacing = (javafx.scene.control.Spinner<Double>) controls.lookup("#text-letter-spacing");
                spacing.increment();
                assertEquals(2.1, controls.value().letterSpacing());
                spacing.getEditor().setText("-0.7");
                assertEquals(-0.7, controls.value().letterSpacing());
                spacing.commitValue();
                spacing.increment();
                assertEquals(-0.6, controls.value().letterSpacing());
                assertEquals(null, controls.lookup("#text-weight"));
                assertTrue(changes.get() > 0);
                ((Button) controls.lookup("#text-appearance-reset")).fire();
                assertEquals(terminal ? com.ynpe.fastremote.domain.settings.TextAppearance.terminalDefaults()
                        : com.ynpe.fastremote.domain.settings.TextAppearance.editorDefaults(), controls.value());
            }
        });
    }

    @Test
    void typographySlidersSynchronizeTenthsBoundsAndPendingInput() throws Exception {
        onFx(() -> {
            for (boolean terminal : new boolean[]{true, false}) {
                var controls = new TextAppearanceControls(
                        com.ynpe.fastremote.domain.settings.TextAppearance.terminalDefaults(),
                        terminal, () -> "Monospaced", () -> 14);
                for (String axis : new String[]{"line", "letter"}) {
                    String id = "#text-" + axis + "-spacing";
                    @SuppressWarnings("unchecked") var spinner =
                            (javafx.scene.control.Spinner<Double>) controls.lookup(id);
                    var slider = (javafx.scene.control.Slider) controls.lookup(id + "-slider");
                    double limit = axis.equals("line") ? 15 : 5;
                    assertEquals(-limit, slider.getMin());
                    assertEquals(limit, slider.getMax());
                    assertTrue(slider.isShowTickMarks() && slider.isShowTickLabels());
                    assertFalse(slider.isSnapToTicks());
                    assertEquals(0.1, slider.getBlockIncrement());
                    slider.adjustValue(-1.26);
                    assertEquals(-1.3, spinner.getValue(), .0001);
                    assertEquals("-1.3", spinner.getEditor().getText());
                    spinner.getEditor().setText("-0,7");
                    assertEquals(-0.7, slider.getValue(), .0001);
                    assertEquals("-0,7", spinner.getEditor().getText());
                    spinner.commitValue();
                    spinner.increment();
                    assertEquals(-0.6, slider.getValue(), .0001);
                    slider.setValue(100);
                    assertEquals(limit, spinner.getValue());
                    spinner.increment();
                    assertEquals(limit, slider.getValue());
                    slider.setValue(-100);
                    spinner.decrement();
                    assertEquals(-limit, spinner.getValue());
                    spinner.getEditor().setText("99.0");
                    assertEquals(limit, slider.getValue());
                    spinner.commitValue();
                    assertEquals(String.format(java.util.Locale.ROOT, "%.1f", limit), spinner.getEditor().getText());
                    slider.setValue(0);
                    spinner.getEditor().setText("-");
                    assertEquals("-", spinner.getEditor().getText());
                    ((Button) controls.lookup("#text-appearance-reset")).fire();
                    double expected = terminal || axis.equals("letter") ? 0 : 1;
                    assertEquals(expected, slider.getValue());
                    assertEquals(String.format(java.util.Locale.ROOT, "%.1f", expected), spinner.getEditor().getText());
                }
            }
        });
    }

    @Test
    void typographyPreviewFillsAvailableWidthAndFollowsInterfaceTheme() throws Exception {
        onFx(() -> {
            for (boolean terminal : new boolean[]{true, false}) {
                var controls = new TextAppearanceControls(
                        com.ynpe.fastremote.domain.settings.TextAppearance.terminalDefaults(),
                        terminal, () -> "Monospaced", () -> 14);
                StackPane root = new StackPane(controls);
                var scene = new javafx.scene.Scene(root, 640, 400);
                String light = EmbeddedTextEditor.class.getResource("main.css").toExternalForm();
                String dark = EmbeddedTextEditor.class.getResource("theme-dark.css").toExternalForm();
                scene.getStylesheets().add(light);
                for (boolean darkTheme : new boolean[]{false, true, false}) {
                    if (darkTheme) scene.getStylesheets().add(dark);
                    else scene.getStylesheets().remove(dark);
                    for (double width : new double[]{640, 960, 520}) {
                        root.resize(width, 400); root.applyCss(); root.layout();
                        var preview = (TextSpacingPreview) controls.lookup("#text-spacing-preview");
                        var canvas = (javafx.scene.canvas.Canvas) preview.getChildrenUnmodifiable().getFirst();
                        assertEquals(controls.getWidth(), preview.getWidth(), .01);
                        assertEquals(width, preview.getWidth(), .01);
                        assertEquals(preview.getWidth() - preview.getInsets().getLeft() - preview.getInsets().getRight(),
                                canvas.getWidth(), .01);
                        assertEquals(javafx.scene.paint.Color.web(darkTheme ? "#232427" : "#f8f9fb"),
                                preview.getBackground().getFills().getFirst().getFill());
                        assertEquals(javafx.scene.paint.Color.web(darkTheme ? "#cbcdd3" : "#1e293b"),
                                canvas.getGraphicsContext2D().getFill());
                        assertTrue(canvas.getHeight() > 0);
                    }
                }
            }
        });
    }

    private static void onFx(Runnable action) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() instanceof Exception exception) throw exception;
        if (failure.get() instanceof Error error) throw error;
    }

    private static KeyEvent shortcutPressed(KeyCode code) {
        boolean mac = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, !mac, false, mac);
    }
}
