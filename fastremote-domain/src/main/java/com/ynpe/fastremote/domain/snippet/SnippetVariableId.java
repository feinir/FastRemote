// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import java.util.Objects;
import java.util.UUID;

public record SnippetVariableId(UUID value) {
    public SnippetVariableId {
        Objects.requireNonNull(value, "value");
    }

    public static SnippetVariableId create() {
        return new SnippetVariableId(UUID.randomUUID());
    }

    public static SnippetVariableId parse(String value) {
        return new SnippetVariableId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
