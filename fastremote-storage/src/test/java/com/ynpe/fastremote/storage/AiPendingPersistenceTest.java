// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.*;
import com.ynpe.fastremote.domain.ai.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiPendingPersistenceTest {
    @TempDir Path directory;
    @Test void upgradesNineAndRestoresQueueImagesAndContextBoundThread() throws Exception {
        Path path = directory.resolve("queue.db"); UUID session;
        var image = new AiChatImage(UUID.randomUUID(), "image/png", new byte[] {1,2}, 1, 1, new byte[] {3});
        var entry = new AiPendingPrompt(UUID.randomUUID(), "中途追加\n中文", List.of(image), true);
        try (var db = new FastRemoteDatabase(path)) {
            var repo = new SqliteAiChatRepository(db); session = repo.createSession("Task").id();
            repo.appendMessage(session, AiChatMessage.Role.USER, "Existing", Optional.empty());
            db.transaction(connection -> { try (var statement = connection.createStatement()) {
                statement.execute("DROP TABLE ai_chat_runtime"); statement.execute("PRAGMA user_version=9");
            } return null; });
        }
        try (var db = new FastRemoteDatabase(path)) {
            var repo = new SqliteAiChatRepository(db);
            assertTrue(repo.loadPending(session).isEmpty());
            repo.savePending(session, List.of(entry)); repo.saveCodexThread(session, "host-a/model", "thread-1");
        }
        try (var db = new FastRemoteDatabase(path)) {
            var repo = new SqliteAiChatRepository(db);
            var actual = repo.loadPending(session).getFirst();
            assertEquals(entry.id(), actual.id()); assertEquals(entry.text(), actual.text()); assertTrue(actual.steering());
            assertArrayEquals(image.data(), actual.images().getFirst().data());
            assertArrayEquals(image.thumbnailData(), actual.images().getFirst().thumbnailData());
            assertEquals(Optional.of("thread-1"), repo.loadCodexThread(session, "host-a/model"));
            assertTrue(repo.loadCodexThread(session, "host-b/model").isEmpty());
            assertEquals("Existing", repo.listMessages(session).getFirst().content());
            repo.deleteSession(session); assertTrue(repo.loadPending(session).isEmpty());
            assertTrue(repo.loadCodexThread(session, "host-a/model").isEmpty());
        }
    }
    @Test void rejectsOversizedOrTruncatedPayloadsWithoutAllocatingDeclaredLength() throws Exception {
        assertThrows(java.io.IOException.class, () -> AiPendingCodec.decode(new byte[] {0,0,0,1,127,-1,-1,-1}));
        var entry = new AiPendingPrompt(UUID.randomUUID(), "abc", List.of(), false);
        byte[] bytes = AiPendingCodec.encode(List.of(entry));
        assertThrows(java.io.IOException.class, () -> AiPendingCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> AiPendingCodec.encode(Collections.nCopies(9, entry)));
    }
}
