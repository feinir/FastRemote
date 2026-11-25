// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import java.util.*;

public record AiSteeringInput(UUID id, String text, List<AiImageInput> images) {
    public AiSteeringInput {
        Objects.requireNonNull(id); Objects.requireNonNull(text);
        images = List.copyOf(images);
        if ((text.isBlank() && images.isEmpty()) || text.length() > 512 * 1024 || images.size() > 4)
            throw new IllegalArgumentException("Invalid steering input");
    }
}
