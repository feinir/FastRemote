// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import com.ynpe.fastremote.domain.catalog.GroupColor;
import java.util.Objects;

/** One localized group definition in the packaged common-command catalog. */
public record BuiltinSnippetGroup(String builtinId, String name, int sortOrder, GroupColor color) {
    public BuiltinSnippetGroup {
        builtinId = requireId(builtinId);
        name = Objects.requireNonNull(name, "name").strip();
        Objects.requireNonNull(color, "color");
        if (name.isEmpty() || name.length() > 255) throw new IllegalArgumentException("Invalid group name");
        if (sortOrder < 0) throw new IllegalArgumentException("Negative group sort order");
    }

    static String requireId(String value) {
        String id = Objects.requireNonNull(value, "builtinId").strip();
        if (!id.matches("[a-z0-9][a-z0-9._-]{2,127}")) {
            throw new IllegalArgumentException("Invalid built-in identifier");
        }
        return id;
    }
}
