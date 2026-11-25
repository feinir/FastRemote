// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import com.ynpe.fastremote.domain.snippet.*;
import java.time.Instant;
import java.util.*;

/** Library entry with resolved group metadata; never persisted. */
public record AutomationCommand(
        SnippetId id,
        Optional<SnippetGroupId> groupId,
        String name,
        String description,
        String content,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt,
        String groupName, String groupPath) {
    public static AutomationCommand describe(Snippet value, List<SnippetGroup> groups) {
        String name = groups.stream().filter(group -> value.groupId().filter(group.id()::equals).isPresent())
                .map(SnippetGroup::name).findFirst().orElse("");
        return new AutomationCommand(value.id(), value.groupId(), value.name(), value.description(), value.content(), value.sortOrder(), value.createdAt(), value.updatedAt(), name, name);
    }
}
