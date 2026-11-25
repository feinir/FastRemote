// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.catalog;

import java.util.Objects;
import java.util.Optional;

public record HostGroup(GroupId id, Optional<GroupId> parentId, String name, int sortOrder, GroupColor color) {
    public HostGroup {
        Objects.requireNonNull(id, "id");
        parentId = Objects.requireNonNull(parentId, "parentId");
        name = Objects.requireNonNull(name, "name").strip();
        Objects.requireNonNull(color, "color");
        if (name.isEmpty() || name.length() > 255) {
            throw new IllegalArgumentException("Group name must contain 1 to 255 characters");
        }
        if (parentId.isPresent() && parentId.get().equals(id)) {
            throw new IllegalArgumentException("A group cannot be its own parent");
        }
    }

    public HostGroup(GroupId id, Optional<GroupId> parentId, String name, int sortOrder) {
        this(id, parentId, name, sortOrder, GroupColor.BLUE);
    }

    public static HostGroup root(String name) {
        return new HostGroup(GroupId.create(), Optional.empty(), name, 0, GroupColor.BLUE);
    }
}
