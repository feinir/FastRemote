// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import com.ynpe.fastremote.domain.catalog.GroupColor;
import java.util.Objects;

public record SnippetVariableGroup(
        SnippetVariableGroupId id,
        String name,
        int sortOrder,
        GroupColor color) {
    public SnippetVariableGroup {
        Objects.requireNonNull(id, "id");
        name = Objects.requireNonNull(name, "name").strip();
        Objects.requireNonNull(color, "color");
        if (name.isEmpty() || name.length() > 255) {
            throw new IllegalArgumentException("变量分组名称长度必须为 1 到 255");
        }
        if (sortOrder < 0) throw new IllegalArgumentException("sortOrder must not be negative");
    }
}
