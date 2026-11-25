// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import java.util.Objects;
import java.util.UUID;

public record SnippetVariableGroupId(UUID value) {
    public SnippetVariableGroupId {
        Objects.requireNonNull(value, "value");
    }

    public static SnippetVariableGroupId create() {
        return new SnippetVariableGroupId(UUID.randomUUID());
    }

    public static SnippetVariableGroupId parse(String value) {
        return new SnippetVariableGroupId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
