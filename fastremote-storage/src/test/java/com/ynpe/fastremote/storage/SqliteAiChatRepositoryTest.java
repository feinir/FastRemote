// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.ai.AiChatMessage;
import com.ynpe.fastremote.domain.ai.AiChatImage;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteAiChatRepositoryTest {
    @TempDir Path directory;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1,2,3,4,5,6,7,8})
    void pinnedMigrationAndBulkOperationsPreserveMessages(int version) throws Exception {
        Path path = directory.resolve("pin-" + version + ".db");
        java.util.UUID first, second;
        try (var db = new FastRemoteDatabase(path)) {
            var repo = new SqliteAiChatRepository(db);
            first = repo.createSession("First").id(); second = repo.createSession("Second").id();
            repo.appendMessage(first, AiChatMessage.Role.USER, "Keep", Optional.empty());
            db.transaction(c -> { try (var st = c.createStatement()) {
                st.execute("ALTER TABLE ai_chat_sessions DROP COLUMN pinned");
                st.execute("PRAGMA user_version=" + version);
            } return null; });
        }
        try (var db = new FastRemoteDatabase(path)) {
            var repo = new SqliteAiChatRepository(db);
            assertTrue(!repo.findSession(first).orElseThrow().pinned());
            repo.setPinned(java.util.List.of(second), true);
            repo.appendMessage(first, AiChatMessage.Role.ASSISTANT, "Later", Optional.empty());
            assertEquals(second, repo.listHosts(10).getFirst().id());
            org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                    () -> repo.setPinned(java.util.List.of(first, java.util.UUID.randomUUID()), true));
            assertTrue(!repo.findSession(first).orElseThrow().pinned(), "Bulk pin rolls back on missing chat");
        }
        try (var db = new FastRemoteDatabase(path)) {
            var repo = new SqliteAiChatRepository(db);
            assertTrue(repo.findSession(second).orElseThrow().pinned());
            repo.setPinned(java.util.List.of(first, second), false);
            repo.renameSession(first, "Renamed");
            assertEquals(2, repo.listMessages(first).size());
            repo.deleteSessions(java.util.List.of(first, second));
            assertTrue(repo.listHosts(10).isEmpty()); assertTrue(repo.listMessages(first).isEmpty());
        }
    }

    @Test
    void associatedHostSurvivesRestartModelChangesAndRename() throws Exception {
        Path path = directory.resolve("associated.db");
        java.util.UUID id;
        var host = new com.ynpe.fastremote.domain.ai.AiHostAssociation(Optional.empty(), "", "dev", "192.0.2.10", 2222);
        try (var database = new FastRemoteDatabase(path)) {
            var repository = new SqliteAiChatRepository(database);
            id = repository.createSession("Task").id();
            assertEquals(Optional.of(host), repository.updateAssociatedHost(id, Optional.of(host)).associatedHost());
            repository.updateModelSelection(id, Optional.of("provider"), Optional.of("model"));
            repository.renameSession(id, "Renamed");
            repository.appendMessage(id, AiChatMessage.Role.USER, "Inspect", Optional.empty());
        }
        try (var database = new FastRemoteDatabase(path)) {
            var repository = new SqliteAiChatRepository(database);
            assertEquals(Optional.of(host), repository.findSession(id).orElseThrow().associatedHost());
            assertEquals(Optional.of(host), repository.listHosts(20).getFirst().associatedHost());
            var saved = new com.ynpe.fastremote.domain.ai.AiHostAssociation(
                    Optional.of(com.ynpe.fastremote.domain.catalog.HostId.create()), "Library host", "admin", "example.test");
            assertEquals(Optional.of(saved), repository.updateAssociatedHost(id, Optional.of(saved)).associatedHost());
            assertTrue(repository.updateAssociatedHost(id, Optional.empty()).associatedHost().isEmpty());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2, 3, 4, 5, 6, 7})
    void migratesEveryPreviousSchemaWithoutChangingChatContents(int version) throws Exception {
        Path path = directory.resolve("association-schema-" + version + ".db");
        java.util.UUID id;
        try (var database = new FastRemoteDatabase(path)) {
            var repository = new SqliteAiChatRepository(database);
            id = repository.createSession("Existing task").id();
            repository.appendMessage(id, AiChatMessage.Role.USER, "Keep this", Optional.empty());
            database.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    for (String column : java.util.List.of("associated_host_id", "associated_host_name", "associated_username", "associated_address", "associated_port")) {
                        statement.execute("ALTER TABLE ai_chat_sessions DROP COLUMN " + column);
                    }
                    statement.execute("PRAGMA user_version=" + version);
                }
                return null;
            });
        }
        try (var database = new FastRemoteDatabase(path)) {
            var repository = new SqliteAiChatRepository(database);
            assertEquals("Existing task", repository.findSession(id).orElseThrow().title());
            assertTrue(repository.findSession(id).orElseThrow().associatedHost().isEmpty());
            assertEquals("Keep this", repository.listMessages(id).getFirst().content());
            var host = new com.ynpe.fastremote.domain.ai.AiHostAssociation(Optional.empty(), "", "dev", "example.test");
            assertEquals(Optional.of(host), repository.updateAssociatedHost(id, Optional.of(host)).associatedHost());
        }
    }

    @Test
    void persistsOrdersTitlesAndDeletesChats() throws Exception {
        Path path = directory.resolve("ai-history.db");
        java.util.UUID firstId;
        try (var database = new FastRemoteDatabase(path)) {
            var repository = new SqliteAiChatRepository(database);
            var first = repository.createSession("新会话");
            firstId = first.id();
            assertEquals("新会话", first.title());
            var selected = repository.updateModelSelection(first.id(),
                    Optional.of("provider-a"), Optional.of("gpt-example"));
            assertEquals(Optional.of("provider-a"), selected.providerId());
            assertEquals(Optional.of("gpt-example"), selected.model());
            repository.appendMessage(first.id(), AiChatMessage.Role.USER, "检查服务器", Optional.of("检查服务器"));
            repository.appendMessage(first.id(), AiChatMessage.Role.TOOL, "✓ list_hosts 已完成", Optional.empty());
            repository.appendMessage(first.id(), AiChatMessage.Role.ASSISTANT, "检查完成", Optional.empty());
            var renamed = repository.renameSession(first.id(), "  发布检查  ");
            assertEquals("发布检查", renamed.title());
            assertEquals(3, renamed.messageCount());
            var second = repository.createSession("New chat");

            assertEquals(second.id(), repository.listHosts(20).getFirst().id());
            assertEquals(3, repository.findSession(first.id()).orElseThrow().messageCount());
        }

        try (var database = new FastRemoteDatabase(path)) {
            var repository = new SqliteAiChatRepository(database);
            var messages = repository.listMessages(firstId);
            assertEquals(java.util.List.of(
                    AiChatMessage.Role.USER, AiChatMessage.Role.TOOL, AiChatMessage.Role.ASSISTANT),
                    messages.stream().map(AiChatMessage::role).toList());
            assertEquals("发布检查", repository.findSession(firstId).orElseThrow().title());
            assertEquals(Optional.of("gpt-example"), repository.findSession(firstId).orElseThrow().model());
            repository.deleteSession(firstId);
            assertTrue(repository.findSession(firstId).isEmpty());
            assertTrue(repository.listMessages(firstId).isEmpty());
        }
    }

    @Test
    void persistsImageOnlyUserMessagesAndDeletesTheirBlobsWithTheSession() throws Exception {
        Path path = directory.resolve("ai-images.db");
        java.util.UUID sessionId;
        try (var database = new FastRemoteDatabase(path)) {
            var repository = new SqliteAiChatRepository(database);
            sessionId = repository.createSession("Image chat").id();
            var image = new AiChatImage(java.util.UUID.randomUUID(), "image/png",
                    new byte[] {1, 2, 3, 4}, 2, 2);
            repository.appendMessage(sessionId, AiChatMessage.Role.USER, "", java.util.List.of(image),
                    Optional.empty());
        }
        try (var database = new FastRemoteDatabase(path)) {
            var repository = new SqliteAiChatRepository(database);
            var message = repository.listMessages(sessionId).getFirst();
            assertEquals("", message.content());
            assertEquals(1, message.images().size());
            assertEquals(java.util.List.of(1, 2, 3, 4),
                    java.util.Arrays.stream(toUnsigned(message.images().getFirst().data())).boxed().toList());
            repository.deleteSession(sessionId);
            assertEquals(0, database.<Integer>query(connection -> {
                try (var statement = connection.createStatement();
                        var result = statement.executeQuery("SELECT count(*) FROM ai_chat_images")) {
                    return result.getInt(1);
                }
            }));
        }
    }

    private static int[] toUnsigned(byte[] bytes) {
        int[] values = new int[bytes.length];
        for (int index = 0; index < bytes.length; index++) values[index] = Byte.toUnsignedInt(bytes[index]);
        return values;
    }

    @Test
    void migratesAnExistingSchemaOneDatabaseForward() throws Exception {
        Path path = directory.resolve("schema-one.db");
        try (var database = new FastRemoteDatabase(path)) {
            database.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("DROP TABLE ai_chat_images");
                    statement.execute("DROP TABLE ai_chat_messages");
                    statement.execute("DROP TABLE ai_chat_sessions");
                    statement.execute("PRAGMA user_version=1");
                }
                return null;
            });
        }

        try (var database = new FastRemoteDatabase(path)) {
            assertEquals(10, database.<Integer>query(connection -> {
                try (var statement = connection.createStatement();
                        var result = statement.executeQuery("PRAGMA user_version")) {
                    return result.getInt(1);
                }
            }));
            assertEquals("新会话", new SqliteAiChatRepository(database).createSession("新会话").title());
        }
    }


    @Test
    void migratesSchemaThreeChatsWithAnEmptyModelSelection() throws Exception {
        Path path = directory.resolve("schema-three.db");
        java.util.UUID sessionId;
        try (var database = new FastRemoteDatabase(path)) {
            var repository = new SqliteAiChatRepository(database);
            sessionId = repository.createSession("Existing chat").id();
            database.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE old_ai_chat_sessions AS SELECT id, title, created_at, updated_at FROM ai_chat_sessions");
                    statement.execute("DROP TABLE ai_chat_images");
                    statement.execute("DROP TABLE ai_chat_messages");
                    statement.execute("DROP TABLE ai_chat_sessions");
                    statement.execute("ALTER TABLE old_ai_chat_sessions RENAME TO ai_chat_sessions");
                    statement.execute("PRAGMA user_version=3");
                }
                return null;
            });
        }

        try (var database = new FastRemoteDatabase(path)) {
            var session = new SqliteAiChatRepository(database).findSession(sessionId).orElseThrow();
            assertTrue(session.providerId().isEmpty());
            assertTrue(session.model().isEmpty());
        }
    }
}
