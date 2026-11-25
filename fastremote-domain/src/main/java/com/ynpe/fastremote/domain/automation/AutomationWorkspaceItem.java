// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import java.util.Objects;

/** Secret-free description of one currently visible workspace surface. */
public record AutomationWorkspaceItem(
        String id,
        AutomationWorkspaceKind kind,
        String title,
        String location,
        boolean active,
        boolean dirty,
        boolean textReadable,
        boolean textWritable,
        boolean logReadable) {
    public AutomationWorkspaceItem {
        id = bounded(id, "id", 200);
        kind = Objects.requireNonNull(kind, "kind");
        title = bounded(title, "title", 1_000);
        location = optionalBounded(location, "location", 4_096);
    }

    private static String bounded(String value, String name, int maximum) {
        String safe = Objects.requireNonNull(value, name).strip();
        if (safe.isEmpty() || safe.length() > maximum) throw new IllegalArgumentException(name + " is invalid");
        return safe;
    }

    private static String optionalBounded(String value, String name, int maximum) {
        String safe = Objects.requireNonNull(value, name).strip();
        if (safe.length() > maximum) throw new IllegalArgumentException(name + " is invalid");
        return safe;
    }
}
