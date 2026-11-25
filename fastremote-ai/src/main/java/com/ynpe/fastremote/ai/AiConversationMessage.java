// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import java.util.Objects;
import java.util.List;

public record AiConversationMessage(Role role, String text, List<AiImageInput> images) {
    public enum Role { USER, ASSISTANT }
    public AiConversationMessage(Role role, String text) {
        this(role, text, List.of());
    }
    public AiConversationMessage {
        Objects.requireNonNull(role, "role");
        text = Objects.requireNonNull(text, "text");
        if (text.length() > 512 * 1024) throw new IllegalArgumentException("Conversation message is too large");
        images = List.copyOf(Objects.requireNonNull(images, "images"));
        if (role == Role.ASSISTANT && !images.isEmpty()) {
            throw new IllegalArgumentException("Assistant history cannot contain user image inputs");
        }
        if (text.isBlank() && images.isEmpty()) throw new IllegalArgumentException("Conversation message is empty");
    }
}
