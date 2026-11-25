// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import java.util.Objects;
import java.util.UUID;

public record SnippetGroupId(UUID value) {
    public SnippetGroupId {
        Objects.requireNonNull(value, "value");
    }

    public static SnippetGroupId create() {
        return new SnippetGroupId(UUID.randomUUID());
    }

    public static SnippetGroupId parse(String value) {
        return new SnippetGroupId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
