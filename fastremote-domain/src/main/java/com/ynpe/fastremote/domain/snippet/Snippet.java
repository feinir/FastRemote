// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record Snippet(
        SnippetId id,
        Optional<SnippetGroupId> groupId,
        String name,
        String description,
        String content,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt) {
    public static final int MAX_CONTENT_LENGTH = 256 * 1024;

    public Snippet {
        Objects.requireNonNull(id, "id");
        groupId = Objects.requireNonNull(groupId, "groupId");
        name = Objects.requireNonNull(name, "name").strip();
        description = Objects.requireNonNull(description, "description").strip();
        content = Objects.requireNonNull(content, "content").replace("\r\n", "\n").replace('\r', '\n');
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (name.isEmpty() || name.length() > 255) throw new IllegalArgumentException("命令名称长度必须为 1 到 255");
        if (description.length() > 1_024) throw new IllegalArgumentException("命令说明不能超过 1024 个字符");
        if (content.isEmpty()) throw new IllegalArgumentException("命令内容不能为空");
        if (content.length() > MAX_CONTENT_LENGTH) throw new IllegalArgumentException("命令内容不能超过 256 KiB");
        if (sortOrder < 0) throw new IllegalArgumentException("sortOrder must not be negative");
    }
}
