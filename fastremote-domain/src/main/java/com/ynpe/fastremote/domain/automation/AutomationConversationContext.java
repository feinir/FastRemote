// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import com.ynpe.fastremote.domain.ai.AiHostAssociation;
import java.util.Optional;
import java.util.UUID;

/** The selected FastRemote AI conversation, distinct from the foreground terminal and MCP client session. */
public record AutomationConversationContext(Optional<UUID> conversationId, Optional<AiHostAssociation> associatedHost,
        Optional<com.ynpe.fastremote.domain.catalog.HostId> associatedHostId, Optional<AutomationHost> savedHost) {
    public AutomationConversationContext(Optional<UUID> conversationId, Optional<AiHostAssociation> host,
            Optional<com.ynpe.fastremote.domain.catalog.HostId> associatedHostId) {
        this(conversationId, host, associatedHostId, Optional.empty());
    }
    public AutomationConversationContext(Optional<UUID> conversationId, Optional<AiHostAssociation> host) {
        this(conversationId, host, host.map(AiHostAssociation::targetId));
    }
    public AutomationConversationContext {
        java.util.Objects.requireNonNull(savedHost, "savedHost");
        java.util.Objects.requireNonNull(conversationId, "conversationId");
        java.util.Objects.requireNonNull(associatedHost, "associatedHost");
        java.util.Objects.requireNonNull(associatedHostId, "associatedHostId");
        if (!associatedHostId.equals(associatedHost.map(AiHostAssociation::targetId))) {
            throw new IllegalArgumentException("Associated host id does not match its metadata");
        }
    }
    public static AutomationConversationContext none() {
        return new AutomationConversationContext(Optional.empty(), Optional.empty());
    }
}
