// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiChatRepository {
    List<AiChatSession> listHosts(int limit) throws IOException;
    AiChatSession createSession(String initialTitle) throws IOException;
    Optional<AiChatSession> findSession(UUID id) throws IOException;
    AiChatSession renameSession(UUID id, String title) throws IOException;
    AiChatSession updateModelSelection(
            UUID id, Optional<String> providerId, Optional<String> model) throws IOException;
    default AiChatSession updateAssociatedHost(UUID id, Optional<AiHostAssociation> host) throws IOException {
        throw new UnsupportedOperationException("AI host association persistence is unavailable");
    }
    List<AiChatMessage> listMessages(UUID sessionId) throws IOException;
    AiChatSession appendMessage(UUID sessionId, AiChatMessage.Role role, String content,
            Optional<String> newTitle) throws IOException;
    default AiChatSession appendMessage(UUID sessionId, AiChatMessage.Role role, String content,
            List<AiChatImage> images, Optional<String> newTitle) throws IOException {
        if (!images.isEmpty()) throw new UnsupportedOperationException("Chat image persistence is unavailable");
        return appendMessage(sessionId, role, content, newTitle);
    }
    default List<AiChatSession> setPinned(List<UUID> ids, boolean pinned) throws IOException {
        throw new UnsupportedOperationException("AI chat pinning is unavailable");
    }
    default void deleteSessions(List<UUID> ids) throws IOException {
        for (UUID id : ids) deleteSession(id);
    }
    default List<AiPendingPrompt> loadPending(UUID sessionId) throws IOException { return List.of(); }
    default void savePending(UUID sessionId, List<AiPendingPrompt> pending) throws IOException {
        if (!pending.isEmpty()) throw new UnsupportedOperationException("Pending input persistence unavailable");
    }
    default Optional<String> loadCodexThread(UUID sessionId, String contextKey) throws IOException { return Optional.empty(); }
    default void saveCodexThread(UUID sessionId, String contextKey, String threadId) throws IOException { }
    void deleteSession(UUID id) throws IOException;
}
