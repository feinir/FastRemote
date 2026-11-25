// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui.chat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatDocumentTest {
    private static ChatDocument.Row row(String key, String message, String text) {
        return new ChatDocument.Row(key, message, "ASSISTANT", "", List.of(ChatDocument.Cell.text(text)), "", "");
    }
    @Test void copySpansBlocksTableCellsAndMessages() {
        ChatDocument document = new ChatDocument(List.of(row("a", "1", "hello"),
                new ChatDocument.Row("table", "1", "ASSISTANT", "table", List.of(ChatDocument.Cell.text("A"), ChatDocument.Cell.text("B")), "", ""),
                row("b", "2", "世界👩‍💻")));
        assertEquals("hello\nA\tB\n世界👩‍💻", document.text());
        assertEquals(8, document.cellStart(1, 1));
        assertEquals(2, document.rowAt(11));
    }
    @Test void anchorSurvivesEarlierMessageGrowthAndHistoryTrimming() {
        var old = new ChatDocument(List.of(row("a", "1", "hello"), row("b", "2", "world")));
        var next = new ChatDocument(List.of(row("a", "1", "hello longer"), row("b", "2", "world!")));
        assertEquals(15, next.resolve(old.anchor(8), old));
        var trimmed = new ChatDocument(List.of(row("b", "2", "world!")));
        assertEquals(2, trimmed.resolve(old.anchor(8), old));
        assertEquals(-1, trimmed.resolve(old.anchor(1), old));
    }
    @Test void completedMarkdownRemapsPositionsAroundRemovedDelimiters() {
        assertEquals(3, ChatDocument.remap(5, "**hello", "hello"));
        assertEquals(5, ChatDocument.remap(7, "**hello", "hello"));
    }
    @Test void rejectsAmbiguousRowIdentities() {
        assertThrows(IllegalArgumentException.class, () -> new ChatDocument(List.of(row("a", "1", "x"), row("a", "2", "y"))));
    }
}
