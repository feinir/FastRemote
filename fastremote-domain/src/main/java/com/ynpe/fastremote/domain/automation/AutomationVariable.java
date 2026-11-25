// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import com.ynpe.fastremote.domain.snippet.*;
import java.time.Instant;
import java.util.*;

/** Library entry with resolved group metadata; never persisted. */
public record AutomationVariable(
        SnippetVariableId id,
        Optional<SnippetVariableGroupId> groupId,
        String name,
        String variableName,
        SnippetVariableType type,
        String delimiter,
        String value,
        String description,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt,
        String groupName, String groupPath) {
    public static AutomationVariable describe(SnippetVariable value, List<SnippetVariableGroup> groups) {
        String name = groups.stream().filter(group -> value.groupId().filter(group.id()::equals).isPresent())
                .map(SnippetVariableGroup::name).findFirst().orElse("");
        return new AutomationVariable(value.id(), value.groupId(), value.name(), value.variableName(), value.type(), value.delimiter(), value.value(), value.description(), value.sortOrder(), value.createdAt(), value.updatedAt(), name, name);
    }
}
