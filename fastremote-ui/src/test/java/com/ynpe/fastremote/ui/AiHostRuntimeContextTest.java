// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.ynpe.fastremote.domain.connection.*;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.ui.WorkspaceViewModel.WorkspaceType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiHostRuntimeContextTest {
    private static OpenSession quick(String username, int port) {
        return new OpenSession("quick", new ConnectionProfile(new Host("192.0.2.10"), new Port(port),
                new Username(username), AuthenticationType.PASSWORD, Optional.empty()), WorkspaceType.TERMINAL);
    }

    @Test
    void quickIdentityIncludesPortAndUsernameAndStopsResolvingAfterClose() {
        var first = quick("ops", 22);
        var second = quick("ops", 2222);
        var otherUser = quick("admin", 22);
        var association = AiHostRuntimeContext.association(first);
        assertEquals("ops@192.0.2.10:22", association.displayName());
        assertNotEquals(association.targetId(), AiHostRuntimeContext.association(second).targetId());
        assertNotEquals(association.targetId(), AiHostRuntimeContext.association(otherUser).targetId());
        assertFalse(AiHostRuntimeContext.matches(second, association.targetId()));
        var resolved = AiHostRuntimeContext.resolve(List.of(second), AiHostRuntimeContext.association(second).targetId()).orElseThrow();
        assertEquals(2222, resolved.profile().port().value());
        second.closed.set(true);
        assertTrue(AiHostRuntimeContext.resolve(List.of(second), AiHostRuntimeContext.association(second).targetId()).isEmpty());
        assertEquals("ops@192.0.2.10:22", association.displayName());
    }

    @Test
    void ipv6EndpointIsBracketedAndPortSpecific() {
        var first = new com.ynpe.fastremote.domain.ai.AiHostAssociation(Optional.empty(), "", "ops", "2001:db8::1", 2222);
        var bracketed = new com.ynpe.fastremote.domain.ai.AiHostAssociation(Optional.empty(), "", "ops", "[2001:db8::1]", 2222);
        assertEquals("ops@[2001:db8::1]:2222", first.displayName());
        assertEquals(first.targetId(), bracketed.targetId());
        assertNotEquals(first.targetId(), new com.ynpe.fastremote.domain.ai.AiHostAssociation(Optional.empty(), "", "ops", "2001:db8::1", 22).targetId());
    }

    @Test
    void savedHostUsesCatalogIdentityAndDoesNotCollapseIntoQuickConnection() {
        var saved = quick("ops", 22);
        saved.catalogHostId = HostId.create();
        var quick = quick("ops", 22);
        assertEquals(saved.catalogHostId, AiHostRuntimeContext.association(saved).targetId());
        assertFalse(AiHostRuntimeContext.matches(quick, saved.catalogHostId));
        assertTrue(AiHostRuntimeContext.active(List.of(saved, quick), tab -> "unused").isEmpty());
    }
}
