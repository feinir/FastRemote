// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.ai.AiChatMessage;
import com.ynpe.fastremote.domain.ai.AiChatImage;
import com.ynpe.fastremote.domain.ai.AiChatRepository;
import com.ynpe.fastremote.domain.ai.AiChatSession;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SqliteAiChatRepository implements AiChatRepository {
    private static final String IMAGE_ONLY_CONTENT = "\u200B";
    private final FastRemoteDatabase database;

    public SqliteAiChatRepository(FastRemoteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override public List<com.ynpe.fastremote.domain.ai.AiPendingPrompt> loadPending(UUID id) throws IOException {
        try {
            byte[] payload = database.query(connection -> {
                try (var statement = connection.prepareStatement("SELECT pending FROM ai_chat_runtime WHERE session_id=?")) {
                    statement.setString(1, id.toString());
                    try (var result = statement.executeQuery()) { return result.next() ? result.getBytes(1) : null; }
                }
            });
            return AiPendingCodec.decode(payload);
        } catch (SQLException failure) { throw storageFailure("Unable to load AI queue", failure); }
    }

    @Override public void savePending(UUID id, List<com.ynpe.fastremote.domain.ai.AiPendingPrompt> prompts) throws IOException {
        byte[] payload = AiPendingCodec.encode(List.copyOf(prompts));
        try {
            database.transaction(connection -> {
                try (var statement = connection.prepareStatement("""
                        INSERT INTO ai_chat_runtime(session_id,pending) VALUES(?,?)
                        ON CONFLICT(session_id) DO UPDATE SET pending=excluded.pending
                        """)) {
                    statement.setString(1, id.toString()); statement.setBytes(2, payload); statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException failure) { throw storageFailure("Unable to save AI queue", failure); }
    }

    @Override public Optional<String> loadCodexThread(UUID id, String contextKey) throws IOException {
        try {
            return database.query(connection -> {
                try (var statement = connection.prepareStatement(
                        "SELECT codex_thread FROM ai_chat_runtime WHERE session_id=? AND codex_context=?")) {
                    statement.setString(1, id.toString()); statement.setString(2, contextKey);
                    try (var result = statement.executeQuery()) {
                        return result.next() ? Optional.ofNullable(result.getString(1)) : Optional.empty();
                    }
                }
            });
        } catch (SQLException failure) { throw storageFailure("Unable to load Codex thread", failure); }
    }

    @Override public void saveCodexThread(UUID id, String contextKey, String threadId) throws IOException {
        if (threadId.isBlank() || threadId.length() > 256) throw new IllegalArgumentException("Invalid Codex thread id");
        try {
            database.transaction(connection -> {
                try (var statement = connection.prepareStatement("""
                        INSERT INTO ai_chat_runtime(session_id,codex_context,codex_thread) VALUES(?,?,?)
                        ON CONFLICT(session_id) DO UPDATE SET codex_context=excluded.codex_context,codex_thread=excluded.codex_thread
                        """)) {
                    statement.setString(1, id.toString()); statement.setString(2, contextKey);
                    statement.setString(3, threadId); statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException failure) { throw storageFailure("Unable to save Codex thread", failure); }
    }

    @Override
    public List<AiChatSession> listHosts(int limit) throws IOException {
        if (limit < 1 || limit > 5000) throw new IllegalArgumentException("AI chat limit must be between 1 and 5000");
        try {
            return database.query(connection -> {
                List<AiChatSession> sessions = new ArrayList<>();
                try (var statement = connection.prepareStatement("""
                        SELECT s.id, s.title, s.pinned, s.provider_id, s.model, s.associated_host_id, s.associated_host_name,
                               s.associated_username, s.associated_address, s.associated_port, s.created_at, s.updated_at,
                               count(m.id) AS message_count
                        FROM ai_chat_sessions s LEFT JOIN ai_chat_messages m ON m.session_id=s.id
                        GROUP BY s.id, s.title, s.pinned, s.provider_id, s.model, s.associated_host_id, s.associated_host_name,
                               s.associated_username, s.associated_address, s.associated_port, s.created_at, s.updated_at
                        ORDER BY s.pinned DESC, s.updated_at DESC, s.created_at DESC LIMIT ?
                        """)) {
                    statement.setInt(1, limit);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) sessions.add(session(result));
                    }
                }
                return List.copyOf(sessions);
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to list AI chats", failure);
        }
    }

    @Override
    public AiChatSession createSession(String initialTitle) throws IOException {
        String safeTitle = Objects.requireNonNull(initialTitle, "initialTitle").strip();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        new AiChatSession(id, safeTitle, now, now, 0, Optional.empty(), Optional.empty());
        try {
            database.transaction(connection -> {
                try (var statement = connection.prepareStatement("""
                        INSERT INTO ai_chat_sessions(id, title, created_at, updated_at) VALUES(?, ?, ?, ?)
                        """)) {
                    statement.setString(1, id.toString());
                    statement.setString(2, safeTitle);
                    statement.setLong(3, now.toEpochMilli());
                    statement.setLong(4, now.toEpochMilli());
                    statement.executeUpdate();
                }
                return null;
            });
            return new AiChatSession(id, safeTitle, now, now, 0, Optional.empty(), Optional.empty());
        } catch (SQLException failure) {
            throw storageFailure("Unable to create AI chat", failure);
        }
    }

    @Override
    public Optional<AiChatSession> findSession(UUID id) throws IOException {
        Objects.requireNonNull(id, "id");
        try {
            return database.query(connection -> {
                try (var statement = connection.prepareStatement("""
                        SELECT s.id, s.title, s.pinned, s.provider_id, s.model, s.associated_host_id, s.associated_host_name,
                               s.associated_username, s.associated_address, s.associated_port, s.created_at, s.updated_at,
                               count(m.id) AS message_count
                        FROM ai_chat_sessions s LEFT JOIN ai_chat_messages m ON m.session_id=s.id
                        WHERE s.id=? GROUP BY s.id, s.title, s.pinned, s.provider_id, s.model, s.associated_host_id, s.associated_host_name,
                               s.associated_username, s.associated_address, s.associated_port, s.created_at, s.updated_at
                        """)) {
                    statement.setString(1, id.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        return result.next() ? Optional.of(session(result)) : Optional.empty();
                    }
                }
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to find AI chat", failure);
        }
    }

    @Override
    public AiChatSession renameSession(UUID id, String title) throws IOException {
        Objects.requireNonNull(id, "id");
        String safeTitle = Objects.requireNonNull(title, "title").strip();
        // Validate before opening the transaction and keep the same title boundary as persisted sessions.
        new AiChatSession(id, safeTitle, Instant.EPOCH, Instant.EPOCH, 0, Optional.empty(), Optional.empty());
        try {
            return database.transaction(connection -> {
                long now = Instant.now().toEpochMilli();
                try (var statement = connection.prepareStatement(
                        "UPDATE ai_chat_sessions SET title=?, updated_at=? WHERE id=?")) {
                    statement.setString(1, safeTitle);
                    statement.setLong(2, now);
                    statement.setString(3, id.toString());
                    if (statement.executeUpdate() != 1) throw new SQLException("AI chat session does not exist");
                }
                try (var statement = connection.prepareStatement("""
                        SELECT s.id, s.title, s.pinned, s.provider_id, s.model, s.associated_host_id, s.associated_host_name,
                               s.associated_username, s.associated_address, s.associated_port, s.created_at, s.updated_at,
                               count(m.id) AS message_count
                        FROM ai_chat_sessions s LEFT JOIN ai_chat_messages m ON m.session_id=s.id
                        WHERE s.id=? GROUP BY s.id, s.title, s.pinned, s.provider_id, s.model, s.associated_host_id, s.associated_host_name,
                               s.associated_username, s.associated_address, s.associated_port, s.created_at, s.updated_at
                        """)) {
                    statement.setString(1, id.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new SQLException("AI chat session does not exist");
                        return session(result);
                    }
                }
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to rename AI chat", failure);
        }
    }

    @Override
    public AiChatSession updateModelSelection(
            UUID id, Optional<String> providerId, Optional<String> model) throws IOException {
        Objects.requireNonNull(id, "id");
        Optional<String> safeProvider = Objects.requireNonNull(providerId, "providerId")
                .map(String::strip).filter(value -> !value.isEmpty());
        Optional<String> safeModel = Objects.requireNonNull(model, "model")
                .map(String::strip).filter(value -> !value.isEmpty());
        new AiChatSession(id, "selection", Instant.EPOCH, Instant.EPOCH, 0, safeProvider, safeModel);
        try {
            return database.transaction(connection -> {
                try (var statement = connection.prepareStatement(
                        "UPDATE ai_chat_sessions SET provider_id=?, model=? WHERE id=?")) {
                    if (safeProvider.isPresent()) statement.setString(1, safeProvider.orElseThrow());
                    else statement.setNull(1, java.sql.Types.VARCHAR);
                    if (safeModel.isPresent()) statement.setString(2, safeModel.orElseThrow());
                    else statement.setNull(2, java.sql.Types.VARCHAR);
                    statement.setString(3, id.toString());
                    if (statement.executeUpdate() != 1) throw new SQLException("AI chat session does not exist");
                }
                try (var statement = connection.prepareStatement("""
                        SELECT s.id, s.title, s.pinned, s.provider_id, s.model, s.associated_host_id, s.associated_host_name,
                               s.associated_username, s.associated_address, s.associated_port, s.created_at, s.updated_at,
                               count(m.id) AS message_count
                        FROM ai_chat_sessions s LEFT JOIN ai_chat_messages m ON m.session_id=s.id
                        WHERE s.id=?
                        GROUP BY s.id, s.title, s.pinned, s.provider_id, s.model, s.associated_host_id, s.associated_host_name,
                               s.associated_username, s.associated_address, s.associated_port, s.created_at, s.updated_at
                        """)) {
                    statement.setString(1, id.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new SQLException("AI chat session does not exist");
                        return session(result);
                    }
                }
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to update AI chat model selection", failure);
        }
    }

    @Override
    public AiChatSession updateAssociatedHost(UUID id,
            Optional<com.ynpe.fastremote.domain.ai.AiHostAssociation> host) throws IOException {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(host, "host");
        try {
            database.transaction(connection -> {
                try (var statement = connection.prepareStatement("""
                        UPDATE ai_chat_sessions SET associated_host_id=?, associated_host_name=?,
                            associated_username=?, associated_address=?, associated_port=? WHERE id=?
                        """)) {
                    statement.setString(1, host.flatMap(value -> value.savedHostId()).map(Object::toString).orElse(null));
                    statement.setString(2, host.map(value -> value.name()).orElse(null));
                    statement.setString(3, host.map(value -> value.username()).orElse(null));
                    statement.setString(4, host.map(value -> value.host()).orElse(null));
                    statement.setString(5, host.map(value -> Integer.toString(value.port())).orElse(null));
                    statement.setString(6, id.toString());
                    if (statement.executeUpdate() != 1) throw new SQLException("AI chat session does not exist");
                }
                return null;
            });
            return findSession(id).orElseThrow(() -> new IOException("AI chat session does not exist"));
        } catch (SQLException failure) { throw storageFailure("Unable to save associated host", failure); }
    }

    @Override
    public List<AiChatMessage> listMessages(UUID sessionId) throws IOException {
        Objects.requireNonNull(sessionId, "sessionId");
        try {
            return database.query(connection -> {
                List<AiChatMessage> messages = new ArrayList<>();
                Map<UUID, List<AiChatImage>> images = new LinkedHashMap<>();
                try (var statement = connection.prepareStatement("""
                        SELECT i.id, i.message_id, i.media_type, i.image_data, i.thumbnail_data,
                               i.pixel_width, i.pixel_height
                        FROM ai_chat_images i JOIN ai_chat_messages m ON m.id=i.message_id
                        WHERE m.session_id=? ORDER BY m.sequence_number, i.sort_order
                        """)) {
                    statement.setString(1, sessionId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            UUID messageId = UUID.fromString(result.getString("message_id"));
                            images.computeIfAbsent(messageId, ignored -> new ArrayList<>()).add(new AiChatImage(
                                    UUID.fromString(result.getString("id")), result.getString("media_type"),
                                    result.getBytes("image_data"), result.getInt("pixel_width"),
                                    result.getInt("pixel_height"), result.getBytes("thumbnail_data")));
                        }
                    }
                }
                try (var statement = connection.prepareStatement("""
                        SELECT id, session_id, role, content, sequence_number, created_at
                        FROM ai_chat_messages WHERE session_id=? ORDER BY sequence_number
                        """)) {
                    statement.setString(1, sessionId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            UUID messageId = UUID.fromString(result.getString("id"));
                            List<AiChatImage> messageImages = images.getOrDefault(messageId, List.of());
                            String content = result.getString("content");
                            if (content.equals(IMAGE_ONLY_CONTENT) && !messageImages.isEmpty()) content = "";
                            messages.add(new AiChatMessage(messageId,
                                UUID.fromString(result.getString("session_id")),
                                AiChatMessage.Role.valueOf(result.getString("role")),
                                content, result.getInt("sequence_number"),
                                Instant.ofEpochMilli(result.getLong("created_at")),
                                messageImages));
                        }
                    }
                }
                return List.copyOf(messages);
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to load AI chat messages", failure);
        }
    }

    @Override
    public AiChatSession appendMessage(UUID sessionId, AiChatMessage.Role role, String content,
            Optional<String> newTitle) throws IOException {
        return appendMessage(sessionId, role, content, List.of(), newTitle);
    }

    @Override
    public AiChatSession appendMessage(UUID sessionId, AiChatMessage.Role role, String content,
            List<AiChatImage> images, Optional<String> newTitle) throws IOException {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(role, "role");
        String safeContent = Objects.requireNonNull(content, "content");
        List<AiChatImage> safeImages = List.copyOf(Objects.requireNonNull(images, "images"));
        if (safeImages.size() > 4) throw new IllegalArgumentException("At most four images can be attached");
        Optional<String> safeTitle = Objects.requireNonNull(newTitle, "newTitle").map(String::strip)
                .filter(value -> !value.isEmpty());
        // Validate at the boundary before opening the transaction.
        new AiChatMessage(UUID.randomUUID(), sessionId, role, safeContent, 0, Instant.now(), safeImages);
        String persistedContent = safeContent.isEmpty() ? IMAGE_ONLY_CONTENT : safeContent;
        safeTitle.ifPresent(value -> new AiChatSession(sessionId, value, Instant.EPOCH, Instant.EPOCH, 0,
                Optional.empty(), Optional.empty()));
        try {
            return database.transaction(connection -> {
                long now = Instant.now().toEpochMilli();
                int sequence;
                try (var statement = connection.prepareStatement(
                        "SELECT coalesce(max(sequence_number), -1) + 1 FROM ai_chat_messages WHERE session_id=?")) {
                    statement.setString(1, sessionId.toString());
                    try (ResultSet result = statement.executeQuery()) { sequence = result.getInt(1); }
                }
                UUID messageId = UUID.randomUUID();
                try (var statement = connection.prepareStatement("""
                        INSERT INTO ai_chat_messages(id, session_id, role, content, sequence_number, created_at)
                        VALUES(?, ?, ?, ?, ?, ?)
                        """)) {
                    statement.setString(1, messageId.toString());
                    statement.setString(2, sessionId.toString());
                    statement.setString(3, role.name());
                    statement.setString(4, persistedContent);
                    statement.setInt(5, sequence);
                    statement.setLong(6, now);
                    statement.executeUpdate();
                }
                try (var statement = connection.prepareStatement("""
                        INSERT INTO ai_chat_images(id, message_id, media_type, image_data, thumbnail_data,
                                                   pixel_width, pixel_height, sort_order)
                        VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (int index = 0; index < safeImages.size(); index++) {
                        AiChatImage image = safeImages.get(index);
                        statement.setString(1, image.id().toString());
                        statement.setString(2, messageId.toString());
                        statement.setString(3, image.mediaType());
                        statement.setBytes(4, image.data());
                        statement.setBytes(5, image.thumbnailData());
                        statement.setInt(6, image.width());
                        statement.setInt(7, image.height());
                        statement.setInt(8, index);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                try (var statement = connection.prepareStatement(safeTitle.isPresent()
                        ? "UPDATE ai_chat_sessions SET title=?, updated_at=? WHERE id=?"
                        : "UPDATE ai_chat_sessions SET updated_at=? WHERE id=?")) {
                    int index = 1;
                    if (safeTitle.isPresent()) statement.setString(index++, safeTitle.orElseThrow());
                    statement.setLong(index++, now);
                    statement.setString(index, sessionId.toString());
                    if (statement.executeUpdate() != 1) throw new SQLException("AI chat session does not exist");
                }
                try (var statement = connection.prepareStatement("""
                        SELECT s.id, s.title, s.pinned, s.provider_id, s.model, s.associated_host_id, s.associated_host_name,
                               s.associated_username, s.associated_address, s.associated_port, s.created_at, s.updated_at,
                               count(m.id) AS message_count
                        FROM ai_chat_sessions s LEFT JOIN ai_chat_messages m ON m.session_id=s.id
                        WHERE s.id=? GROUP BY s.id, s.title, s.pinned, s.provider_id, s.model, s.associated_host_id, s.associated_host_name,
                               s.associated_username, s.associated_address, s.associated_port, s.created_at, s.updated_at
                        """)) {
                    statement.setString(1, sessionId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new SQLException("AI chat session does not exist");
                        return session(result);
                    }
                }
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to append AI chat message", failure);
        }
    }

    @Override
    public void deleteSession(UUID id) throws IOException {
        Objects.requireNonNull(id, "id");
        try {
            database.transaction(connection -> {
                try (var statement = connection.prepareStatement("DELETE FROM ai_chat_sessions WHERE id=?")) {
                    statement.setString(1, id.toString());
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException failure) {
            throw storageFailure("Unable to delete AI chat", failure);
        }
    }

    @Override
    public List<AiChatSession> setPinned(List<UUID> ids, boolean pinned) throws IOException {
        List<UUID> safe = List.copyOf(ids);
        try {
            return database.transaction(connection -> {
                try (var statement = connection.prepareStatement("UPDATE ai_chat_sessions SET pinned=? WHERE id=?")) {
                    for (UUID id : safe) {
                        statement.setBoolean(1, pinned);
                        statement.setString(2, id.toString());
                        if (statement.executeUpdate() != 1) throw new SQLException("AI chat no longer exists");
                    }
                }
                List<AiChatSession> changed = new ArrayList<>();
                for (UUID id : safe) {
                    try { changed.add(findSession(id).orElseThrow()); }
                    catch (IOException e) { throw new SQLException("Unable to reload AI chat", e); }
                }
                return List.copyOf(changed);
            });
        } catch (SQLException failure) { throw storageFailure("Unable to pin AI chats", failure); }
    }

    @Override
    public void deleteSessions(List<UUID> ids) throws IOException {
        List<UUID> safe = List.copyOf(ids);
        try {
            database.transaction(connection -> {
                try (var statement = connection.prepareStatement("DELETE FROM ai_chat_sessions WHERE id=?")) {
                    for (UUID id : safe) {
                        statement.setString(1, id.toString());
                        statement.executeUpdate();
                    }
                }
                return null;
            });
        } catch (SQLException failure) { throw storageFailure("Unable to delete AI chats", failure); }
    }

    private static AiChatSession session(ResultSet result) throws SQLException {
        return new AiChatSession(UUID.fromString(result.getString("id")), result.getString("title"),
                Instant.ofEpochMilli(result.getLong("created_at")),
                Instant.ofEpochMilli(result.getLong("updated_at")), result.getInt("message_count"),
                Optional.ofNullable(result.getString("provider_id")),
                Optional.ofNullable(result.getString("model")),
                associatedHost(result), result.getBoolean("pinned"));
    }

    private static Optional<com.ynpe.fastremote.domain.ai.AiHostAssociation> associatedHost(ResultSet result)
            throws SQLException {
        String address = result.getString("associated_address");
        if (address == null) return Optional.empty();
        return Optional.of(new com.ynpe.fastremote.domain.ai.AiHostAssociation(
                Optional.ofNullable(result.getString("associated_host_id"))
                        .map(com.ynpe.fastremote.domain.catalog.HostId::parse),
                result.getString("associated_host_name"), result.getString("associated_username"), address,
                result.getString("associated_port") == null ? 22 : Integer.parseInt(result.getString("associated_port"))));
    }

    private static IOException storageFailure(String message, SQLException failure) {
        return new IOException(message, failure);
    }
}
