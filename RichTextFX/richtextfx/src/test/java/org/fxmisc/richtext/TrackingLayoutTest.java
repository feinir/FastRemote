package org.fxmisc.richtext;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.PathElement;
import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.SegmentOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class TrackingLayoutTest {
    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try { Platform.startup(latch::countDown); } catch (IllegalStateException running) { latch.countDown(); }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test void mapsGraphemesWithoutSplittingSurrogatesCombiningMarksOrEmoji() {
        String text = "A中e\u0301👩‍💻🇨🇳Z";
        TrackingMap map = new TrackingMap(text);
        var matcher = java.util.regex.Pattern.compile("\\X").matcher(text);
        int count = 0;
        while (matcher.find()) {
            assertEquals(matcher.start() > 0, map.gapBefore(matcher.start()));
            for (int i = matcher.start() + 1; i < matcher.end(); i++) assertFalse(map.gapBefore(i));
            count++;
        }
        assertEquals(text.length() + count - 1, map.display(text.length()));
        for (int i = 0; i <= text.length(); i++) assertEquals(i, map.document(map.display(i)));
    }

    @Test void caretRangeAndMouseUseTheSameExpandedGeometry() throws Exception {
        fx(() -> {
            var text = paragraph("AB中e\u0301👩‍💻Z");
            StackPane root = mount(text, 1000);
            double originalEnd = x(text.documentCaretShape(text.getParagraph().length(), true));
            text.setTextSpacing(3, 3);
            root.applyCss(); root.layout(); text.layout();
            double expandedEnd = x(text.documentCaretShape(text.getParagraph().length(), true));
            assertEquals(15, expandedEnd - originalEnd, 0.8);
            double boundary = x(text.documentCaretShape(1, true));
            var shape = text.documentCaretShape(1, true);
            double y = (((MoveTo) shape[0]).getY() + ((LineTo) shape[1]).getY()) / 2;
            assertEquals(1, text.hit(boundary + .1, y).getInsertionIndex());
            assertTrue(text.hit(boundary + .1, y).getCharacterIndex().isPresent());
            assertTrue(text.getRangeShape(1, 3).length >= 5);
            text.setTextSpacing(1, 0); root.applyCss(); root.layout(); text.layout();
            assertEquals(originalEnd, x(text.documentCaretShape(text.getParagraph().length(), true)), .8);
            text.dispose();
        });
    }

    @Test void wrappingAndFontChangesInvalidateLineMetrics() throws Exception {
        fx(() -> {
            var text = paragraph("abcdefghijklmnopqrstuvwxyz 0123456789");
            StackPane root = mount(text, 100);
            int before = text.getLineCount();
            text.setTextSpacing(3, 3); root.applyCss(); root.layout(); text.layout();
            int after = text.getLineCount();
            assertTrue(after > before, before + " -> " + after);
            for (int i = 0; i < text.getParagraph().length(); i++) {
                assertTrue(text.getLineStartPosition(i) <= i);
                assertTrue(text.getLineEndPosition(i) >= i);
            }
            text.setStyle("-fx-font-size: 24px;"); root.applyCss(); root.applyCss(); root.layout(); text.layout();
            assertTrue(text.getLineCount() > after);
            text.dispose();
        });
    }

    @Test void largeDocumentsKeepParagraphVirtualizationAndDisposeSpacingListeners() throws Exception {
        fx(() -> {
            CodeArea area = new CodeArea();
            try {
                area.replaceText("abcdef0123456789\n".repeat(10_000));
                area.setTextSpacing(3, 2);
                StackPane root = new StackPane(area);
                new Scene(root, 600, 400); root.applyCss(); root.resize(600, 400); root.layout();
                assertTrue(area.getVisibleParagraphs().size() < 100);
                area.moveTo(area.getLength()); area.requestFollowCaret(); root.layout();
                assertEquals("abcdef0123456789", area.getParagraph(area.getParagraphs().size() - 2).getText());
                area.setTextSpacing(1, 0); root.layout();
                assertEquals(170_000, area.getLength());
            } finally { area.dispose(); }
        });
    }

    @Test void trackingPreservesStylesWithoutSyntheticBold() throws Exception {
        fx(() -> {
            var text = paragraph("A中e\u0301👩‍💻Z");
            StackPane root = mount(text, 1000);
            text.setTextSpacing(3, 2);
            root.applyCss(); root.layout(); text.layout();
            var nodes = text.getChildren().stream().filter(n -> n instanceof TextExt).map(n -> (TextExt) n).toList();
            assertEquals(5, nodes.size());
            assertEquals("e\u0301", nodes.get(2).getText());
            assertEquals("👩‍💻", nodes.get(3).getText());
            assertTrue(nodes.stream().allMatch(n -> n.getStroke() == null));
            text.dispose();
        });
    }

    @Test void longSingleLineRetainsTextAndCanToggleTracking() throws Exception {
        fx(() -> {
            var text = paragraph("abc012中\t".repeat(250));
            StackPane root = mount(text, 500);
            text.setTextSpacing(3, 2); root.applyCss(); root.layout(); text.layout();
            assertTrue(text.getLineCount() > 10);
            assertEquals(2000, text.getParagraph().length());
            text.setTextSpacing(1, 0); root.applyCss(); root.layout(); text.layout();
            assertEquals(1, text.getChildren().stream().filter(n -> n instanceof TextExt).count());
            text.dispose();
        });
    }

    @Test void signedTenthsChangeActualCaretPositionsAndKeepHitTestingAligned() throws Exception {
        fx(() -> {
            // Keep glyph advances wider than the overlapping -5 px hit regions.
            String family = java.util.List.of("Liberation Mono", "DejaVu Sans Mono", "Consolas", "Menlo", "Courier New")
                    .stream().filter(javafx.scene.text.Font.getFamilies()::contains).findFirst().orElseThrow();
            var text = paragraph("abcdefghij", "-fx-font-family: '" + family + "'; -fx-font-size: 24px;");
            StackPane root = mount(text, 1000);
            double natural = x(text.documentCaretShape(10, true));
            for (double spacing : new double[]{0.1, 0.2, -0.1, -0.2, -1.3, -5, 5, 0}) {
                text.setTextSpacing(1, spacing); root.applyCss(); root.layout(); text.layout();
                assertEquals(natural + 9 * spacing, x(text.documentCaretShape(10, true)), .04, "spacing=" + spacing);
                for (int i = 1; i < 10; i++) {
                    var caret = text.documentCaretShape(i, true);
                    double y = (((MoveTo) caret[0]).getY() + ((LineTo) caret[1]).getY()) / 2;
                    assertEquals(i, text.hit(x(caret) + .01, y).getInsertionIndex(), "spacing=" + spacing + ", index=" + i + ", natural=" + natural
                            + ", caret=" + x(caret) + ", font=" + text.getChildren().stream()
                                    .filter(n -> n instanceof TextExt).map(n -> ((TextExt)n).getFont()).findFirst());
                }
            }
            text.dispose();
        });
    }

    @Test void realCodeAreaLinePitchSurvivesCssAndLiveUpdatesIncludingNegativeTenths() throws Exception {
        fx(() -> {
            CodeArea area = new CodeArea();
            try {
                // The -15 px boundary requires a natural line height larger than 15 px.
                area.setStyle("-fx-font-size: 24px;");
                area.setWrapText(true);
                area.replaceText("abcdefghij\nabcdefghij\nabcdefghij\n".repeat(100));
                StackPane root = new StackPane(area);
                new Scene(root, 500, 400); root.resize(500, 400); root.applyCss(); root.layout();
                double original = paragraphPitch(area);
                for (double spacing : new double[]{1.1, 1.2, -1.3, 15, -15, 1}) {
                    area.setTextSpacing(spacing, 0);
                    root.applyCss(); root.layout();
                    assertEquals(original + spacing - 1, paragraphPitch(area), .02, "spacing=" + spacing);
                    for (var box : area.lookupAll(".paragraph-box")) {
                        var text = (TextFlowExt) box.lookup(".paragraph-text");
                        assertEquals(spacing, text.getLineSpacing(), .001);
                    }
                }
            } finally { area.dispose(); }
        });
    }

    @Test void negativeTrackingKeepsWrappedUnicodeNavigationAndSelectionInDocumentCoordinates() throws Exception {
        fx(() -> {
            String value = "A中e\u0301👩‍💻Z abc def ".repeat(5);
            var text = paragraph(value);
            StackPane root = mount(text, 140);
            text.setTextSpacing(-1.3, -0.7); root.applyCss(); root.layout(); text.layout();
            assertTrue(text.getLineCount() > 2);
            TextFlowLayout metrics = new TextFlowLayout(text);
            int displayLength = text.getChildren().stream().filter(javafx.scene.Node::isManaged)
                    .mapToInt(node -> node instanceof javafx.scene.text.Text t ? t.getText().length() : 1).sum();
            assertEquals(displayLength, java.util.stream.IntStream.range(0, metrics.getLineCount())
                    .map(metrics::getLineLength).sum());
            var matcher = java.util.regex.Pattern.compile("\\X").matcher(value);
            while (matcher.find()) {
                int offset = matcher.start();
                assertTrue(text.getLineStartPosition(offset) <= offset, "start at " + offset);
                assertTrue(text.getLineEndPosition(offset) >= offset, "end at " + offset);
                assertTrue(text.getRangeShape(offset, matcher.end()).length >= 5);
            }
            text.setTextSpacing(-1.2, -0.7); root.applyCss(); root.layout(); text.layout();
            assertEquals(-1.2, text.getLineSpacing());
            assertEquals(value, text.getParagraph().getText());
            text.dispose();
        });
    }

    private static double paragraphPitch(CodeArea area) {
        var boxes = area.lookupAll(".paragraph-box").stream().map(node -> (ParagraphBox<?, ?, ?>) node)
                .filter(box -> box.getIndex() >= 0)
                .sorted(java.util.Comparator.comparingInt(ParagraphBox::getIndex)).toList();
        assertTrue(boxes.size() >= 2);
        return boxes.get(1).localToScene(0, 0).getY() - boxes.get(0).localToScene(0, 0).getY();
    }

    private static ParagraphText<Void, String, String> paragraph(String value) {
        return paragraph(value, "-fx-font-family: monospace;");
    }
    private static ParagraphText<Void, String, String> paragraph(String value, String fontStyle) {
        return new ParagraphText<>(new Paragraph<>(null, SegmentOps.styledTextOps(), value, ""), segment -> {
            TextExt text = new TextExt(segment.getSegment());
            text.setStyle(fontStyle);
            return text;
        });
    }
    private static StackPane mount(ParagraphText<?, ?, ?> text, double width) {
        StackPane root = new StackPane(text);
        new Scene(root, width, 600); root.applyCss(); root.resize(width, 600); root.applyCss(); root.layout(); text.layout();
        return root;
    }
    private static double x(PathElement[] shape) { return ((MoveTo) shape[0]).getX(); }
    private static void fx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch (Throwable t) { failure.set(t); } finally { done.countDown(); } });
        assertTrue(done.await(30, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
