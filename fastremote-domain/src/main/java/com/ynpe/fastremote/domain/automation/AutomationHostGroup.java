// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import com.ynpe.fastremote.domain.catalog.*;
import java.util.*;

/** Host group ancestry, ordered from the outermost group to this group. */
public record AutomationHostGroup(GroupId id, Optional<GroupId> parentId, String name,
        String path, List<GroupId> groupIds, int sortOrder) {
    public AutomationHostGroup { groupIds = List.copyOf(groupIds); }

    public static List<AutomationHostGroup> describe(List<HostGroup> groups) {
        Map<GroupId, HostGroup> byId = new HashMap<>();
        groups.forEach(group -> byId.put(group.id(), group));
        return groups.stream().map(group -> {
            List<HostGroup> ancestry = new ArrayList<>();
            Set<GroupId> seen = new HashSet<>();
            HostGroup current = group;
            while (current != null && seen.add(current.id())) {
                ancestry.addFirst(current);
                current = current.parentId().map(byId::get).orElse(null);
            }
            return new AutomationHostGroup(group.id(), group.parentId(), group.name(),
                    String.join(" / ", ancestry.stream().map(HostGroup::name).toList()),
                    ancestry.stream().map(HostGroup::id).toList(), group.sortOrder());
        }).toList();
    }
}
