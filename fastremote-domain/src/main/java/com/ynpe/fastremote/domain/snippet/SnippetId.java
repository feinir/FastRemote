// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import java.util.Objects;
import java.util.UUID;

public record SnippetId(UUID value) {
    public SnippetId {
        Objects.requireNonNull(value, "value");
    }

    public static SnippetId create() {
        return new SnippetId(UUID.randomUUID());
    }

    public static SnippetId parse(String value) {
        return new SnippetId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
