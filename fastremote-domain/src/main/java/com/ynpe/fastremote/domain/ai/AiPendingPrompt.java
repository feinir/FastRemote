// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;
import java.util.*;

public record AiPendingPrompt(UUID id, String text, List<AiChatImage> images, boolean steering) {
    public AiPendingPrompt {
        Objects.requireNonNull(id); Objects.requireNonNull(text); images = List.copyOf(images);
        if ((text.isBlank() && images.isEmpty()) || text.length() > 512 * 1024 || images.size() > 4)
            throw new IllegalArgumentException("Invalid pending input");
    }
}
