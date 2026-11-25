// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import com.ynpe.fastremote.domain.catalog.HostId;
import java.util.Objects;
import java.util.Optional;

/** Non-secret snapshot of the conversation association when an AI request starts. */
public record AiWorkspaceContext(Kind kind, String label, Optional<HostId> remoteHostId,
        Optional<java.util.UUID> conversationId, Optional<AiHostAssociation> associatedHost) {
    public AiWorkspaceContext(Kind kind, String label, Optional<HostId> remoteHostId) {
        this(kind, label, remoteHostId, Optional.empty(), Optional.empty());
    }

    public AiWorkspaceContext withConversation(java.util.UUID id, Optional<AiHostAssociation> host) {
        return new AiWorkspaceContext(kind, label, remoteHostId, Optional.of(id), host);
    }

    public com.ynpe.fastremote.domain.automation.AutomationConversationContext conversationContext() {
        return new com.ynpe.fastremote.domain.automation.AutomationConversationContext(conversationId, associatedHost);
    }
    public AiWorkspaceContext {
        kind = Objects.requireNonNull(kind, "kind");
        label = Objects.requireNonNull(label, "label").strip();
        remoteHostId = Objects.requireNonNull(remoteHostId, "remoteHostId");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(associatedHost, "associatedHost");
        if (kind != Kind.REMOTE && remoteHostId.isPresent()) {
            throw new IllegalArgumentException("Only remote context may contain a host id");
        }
    }

    public static AiWorkspaceContext none() {
        return new AiWorkspaceContext(Kind.NONE, "", Optional.empty());
    }

    public static AiWorkspaceContext localTerminal(String label) {
        return new AiWorkspaceContext(Kind.LOCAL_TERMINAL, label, Optional.empty());
    }

    public static AiWorkspaceContext remote(String label, Optional<HostId> hostId) {
        return new AiWorkspaceContext(Kind.REMOTE, label, hostId);
    }

    public enum Kind { NONE, LOCAL_TERMINAL, REMOTE }
}
