// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import com.ynpe.fastremote.domain.catalog.HostId;
import java.util.Objects;

public record AutomationHost(HostId id, String name, String username, String host, int port, java.util.Optional<com.ynpe.fastremote.domain.catalog.GroupId> groupId,
        String groupName, String groupPath, java.util.List<com.ynpe.fastremote.domain.catalog.GroupId> groupIds) {
    public AutomationHost(HostId id, String name, String username, String host, int port) {
        this(id, name, username, host, port, java.util.Optional.empty(), "", "", java.util.List.of());
    }
    public AutomationHost {
        Objects.requireNonNull(id, "id");
        groupId = Objects.requireNonNull(groupId, "groupId");
        groupName = Objects.requireNonNull(groupName, "groupName");
        groupPath = Objects.requireNonNull(groupPath, "groupPath");
        groupIds = java.util.List.copyOf(groupIds);
        name = Objects.requireNonNull(name, "name").strip();
        username = Objects.requireNonNull(username, "username").strip();
        host = Objects.requireNonNull(host, "host").strip();
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port is out of range");
    }

    public String endpoint() {
        return username + "@" + host + ':' + port;
    }
}
