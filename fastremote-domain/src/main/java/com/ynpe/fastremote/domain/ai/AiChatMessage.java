// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import java.time.Instant;
import java.util.Objects;
import java.util.List;
import java.util.UUID;

public record AiChatMessage(UUID id, UUID sessionId, Role role, String content, int sequence, Instant createdAt,
        List<AiChatImage> images) {
    public enum Role { USER, ASSISTANT, SYSTEM, TOOL }

    public AiChatMessage(UUID id, UUID sessionId, Role role, String content, int sequence, Instant createdAt) {
        this(id, sessionId, role, content, sequence, createdAt, List.of());
    }

    public AiChatMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(role, "role");
        content = Objects.requireNonNull(content, "content");
        images = List.copyOf(Objects.requireNonNull(images, "images"));
        if ((content.isEmpty() && images.isEmpty()) || content.length() > 512 * 1024) {
            throw new IllegalArgumentException("Invalid AI chat message content");
        }
        if (role != Role.USER && !images.isEmpty()) throw new IllegalArgumentException("Only user messages accept images");
        if (sequence < 0) throw new IllegalArgumentException("Negative AI chat message sequence");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
