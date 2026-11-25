// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui.chat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ChatMarkdownTest {
    private final ChatMarkdown parser = new ChatMarkdown();
    private ChatDocument document(String source) {
        var rows = new ArrayList<ChatDocument.Row>(); int i = 0;
        for (var block : parser.parse(source)) rows.add(new ChatDocument.Row("r" + i++, "m", "ASSISTANT", block.style(), block.cells(), "", ""));
        return new ChatDocument(rows);
    }
    @Test void rendersBlocksAndFormattingWithoutExecutingHtmlOrLoadingImages() {
        var doc = document("# Heading\n\n**bold** *em* ~~gone~~ [site](https://example.com)\n\n> quote\n\n- one\n- two\n\n```java\nint n = 1;\n```\n\n<script>alert(1)</script>\n\n![alt](https://example.com/tracker.png)");
        assertTrue(doc.text().contains("bold em gone site")); assertTrue(doc.text().contains("• one\n• two"));
        assertTrue(doc.text().contains("int n = 1;")); assertTrue(doc.text().contains("<script>alert(1)</script>"));
        assertTrue(doc.text().endsWith("alt"));
        assertTrue(doc.rows().stream().flatMap(r -> r.cells().stream()).flatMap(c -> c.runs().stream()).anyMatch(r -> r.style().contains("bold")));
    }
    @Test void tablesRemainSelectableTabSeparatedText() {
        var doc = document("| A | B |\n|---|---|\n| 甲 | 乙 |");
        assertEquals("A\tB\n甲\t乙", doc.text());
        assertEquals(2, doc.rows().getLast().cells().size());
    }
    @Test void virtualizationDoesNotInsertCharactersIntoLongParagraphsOrSplitSurrogates() {
        String text = "中文🙂".repeat(2000);
        var doc = document(text); assertEquals(text, doc.text()); assertTrue(doc.rows().size() > 2);
        for (var row : doc.rows()) { assertTrue(row.text().length() <= 2048); assertFalse(Character.isLowSurrogate(row.text().charAt(0))); }
    }
    @Test void unfinishedFencesAndDelimitersRemainVisibleWhileStreaming() {
        assertEquals("echo hello", document("```sh\necho hello").text());
        assertEquals("**hello", document("**hello").text());
        assertEquals("hello", document("**hello**").text());
    }
    @Test void pathologicalNestingAndShortLineFloodStayBounded() {
        String flood = "x\n\n".repeat(10000);
        var doc = document(flood); assertEquals(flood, doc.text()); assertTrue(doc.rows().size() < 30);
        var nested = document("> ".repeat(10000) + "text"); assertTrue(nested.text().contains("text"));
    }
    @Test void oversizedTableCellsKeepTheirCopyTextWhenWrapped() {
        String cell = "字".repeat(6000);
        var doc = document("| A | B |\n|---|---|\n| " + cell + " | end |");
        assertTrue(doc.text().contains(cell + "\tend")); assertTrue(doc.rows().stream().allMatch(r -> r.text().length() <= 2048));
    }
    @Test void emptyAndNestedListsAreValidDocuments() {
        assertEquals("", document("").text());
        assertTrue(document("1. a\n   - b\n2. c").text().contains("2. c"));
    }
    @Test void codeCopyMetadataPreservesWhitespaceAndSeparatesBlocksWithoutAddingDocumentText() {
        String first = "  echo one  \n\n\techo two\n";
        var blocks = parser.parse("```sh title\n" + first + "```\n\ntext\n\n```java\nint n;\n```");
        var code = blocks.stream().flatMap(b -> b.code().stream()).toList();
        assertEquals(List.of(new ChatDocument.CodeSnippet("sh", first), new ChatDocument.CodeSnippet("java", "int n;\n")), code);
        assertEquals(2, blocks.stream().filter(b -> b.style().contains("code-first")).count());
        assertEquals(2, blocks.stream().filter(b -> b.style().contains("code-last")).count());
        assertEquals("  echo one  \n\n\techo two\ntext\nint n;", document("```sh\n" + first + "```\n\ntext\n\n```java\nint n;\n```").text());
    }
    @Test void emptyIndentedAndNestedCodeBlocksKeepTheirCopyAction() {
        assertEquals("", parser.parse("```\n```").getFirst().code().orElseThrow().text());
        assertEquals("echo hello\n", parser.parse("    echo hello\n").getFirst().code().orElseThrow().text());
        assertEquals("echo nested\n", parser.parse("- ```sh\n  echo nested\n  ```").getFirst().code().orElseThrow().text());
    }
}
