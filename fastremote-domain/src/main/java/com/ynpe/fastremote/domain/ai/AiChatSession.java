// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persisted AI conversation metadata; message bodies are loaded separately. */
public record AiChatSession(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        int messageCount,
        Optional<String> providerId,
        Optional<String> model,
        Optional<AiHostAssociation> associatedHost,
        boolean pinned) {
    public AiChatSession(UUID id, String title, Instant createdAt, Instant updatedAt, int messageCount,
            Optional<String> providerId, Optional<String> model, Optional<AiHostAssociation> associatedHost) {
        this(id, title, createdAt, updatedAt, messageCount, providerId, model, associatedHost, false);
    }
    public AiChatSession(UUID id, String title, Instant createdAt, Instant updatedAt, int messageCount,
            Optional<String> providerId, Optional<String> model) {
        this(id, title, createdAt, updatedAt, messageCount, providerId, model, Optional.empty());
    }

    public AiChatSession(UUID id, String title, Instant createdAt, Instant updatedAt, int messageCount) {
        this(id, title, createdAt, updatedAt, messageCount, Optional.empty(), Optional.empty());
    }

    public AiChatSession {
        associatedHost = Objects.requireNonNull(associatedHost, "associatedHost");
        Objects.requireNonNull(id, "id");
        title = Objects.requireNonNull(title, "title").strip();
        if (title.isEmpty() || title.length() > 120) throw new IllegalArgumentException("Invalid AI chat title");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("AI chat update precedes creation");
        if (messageCount < 0) throw new IllegalArgumentException("Negative AI chat message count");
        providerId = boundedOptional(providerId, "providerId", 64);
        model = boundedOptional(model, "model", 200);
        if (providerId.isPresent() != model.isPresent()) {
            throw new IllegalArgumentException("AI chat provider and model must be set together");
        }
    }

    private static Optional<String> boundedOptional(Optional<String> value, String name, int maximum) {
        return Objects.requireNonNull(value, name).map(String::strip).filter(text -> !text.isEmpty()).map(text -> {
            if (text.length() > maximum) throw new IllegalArgumentException(name + " is too long");
            return text;
        });
    }
}
