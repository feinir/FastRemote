// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import com.ynpe.fastremote.domain.ai.AiHostAssociation;
import java.util.List;
import java.util.Objects;

/** One host currently open in the workspace; multiple ports of a quick connection share one identity. */
public record AutomationActiveHost(com.ynpe.fastremote.domain.catalog.HostId id, AiHostAssociation host, List<String> workspaceIds, java.util.Optional<AutomationHost> savedHost) {
    public AutomationActiveHost(com.ynpe.fastremote.domain.catalog.HostId id, AiHostAssociation host, List<String> workspaceIds) {
        this(id, host, workspaceIds, java.util.Optional.empty());
    }
    public AutomationActiveHost(AiHostAssociation host, List<String> workspaceIds) {
        this(host.targetId(), host, workspaceIds);
    }
    public AutomationActiveHost {
        Objects.requireNonNull(savedHost, "savedHost");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(host, "host");
        workspaceIds = List.copyOf(workspaceIds);
    }
    public String hostId() { return host.targetId().toString(); }
}
