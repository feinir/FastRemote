// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import java.util.Objects;
import java.util.Optional;

/** One localized command definition in the packaged common-command catalog. */
public record BuiltinSnippet(
        String builtinId,
        Optional<String> groupBuiltinId,
        String name,
        String description,
        String content,
        int sortOrder) {
    public BuiltinSnippet {
        builtinId = BuiltinSnippetGroup.requireId(builtinId);
        groupBuiltinId = Objects.requireNonNull(groupBuiltinId, "groupBuiltinId")
                .map(BuiltinSnippetGroup::requireId);
        name = Objects.requireNonNull(name, "name").strip();
        description = Objects.requireNonNull(description, "description").strip();
        content = Objects.requireNonNull(content, "content").replace("\r\n", "\n").replace('\r', '\n');
        if (name.isEmpty() || name.length() > 255) throw new IllegalArgumentException("Invalid command name");
        if (description.length() > 1_024) throw new IllegalArgumentException("Command description is too long");
        if (content.isEmpty() || content.length() > Snippet.MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Invalid command content length");
        }
        if (sortOrder < 0) throw new IllegalArgumentException("Negative command sort order");
    }
}
