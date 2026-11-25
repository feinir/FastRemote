// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ynpe.fastremote.domain.catalog.HostId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiWorkspaceContextTest {
    @Test
    void aiToolRoundLimitDefaultsToTwentyAndRemainsBounded() {
        assertEquals(1024L * 1024L * 1024L, AiSettings.defaults().maxTransferBytes());
        assertEquals(20, AiSettings.defaults().maxToolRounds());
        AiSettings defaults = AiSettings.defaults();
        assertThrows(IllegalArgumentException.class, () -> new AiSettings(
                defaults.providers(), defaults.selectedProviderId(), defaults.builtInPermissions(),
                defaults.mcpPermissions(), defaults.allowedLocalRoots(), defaults.allowedRemoteRoots(),
                0, defaults.mcpServer()));
        assertThrows(IllegalArgumentException.class, () -> new AiSettings(
                defaults.providers(), defaults.selectedProviderId(), defaults.builtInPermissions(),
                defaults.mcpPermissions(), defaults.allowedLocalRoots(), defaults.allowedRemoteRoots(),
                101, defaults.mcpServer()));
        assertThrows(IllegalArgumentException.class, () -> new AiSettings(
                defaults.providers(), defaults.selectedProviderId(), defaults.builtInPermissions(),
                defaults.mcpPermissions(), defaults.allowedLocalRoots(), defaults.allowedRemoteRoots(),
                AiSettings.MIN_MAX_TRANSFER_BYTES - 1, defaults.maxToolRounds(), defaults.mcpServer()));
    }

    @Test
    void distinguishesNoneLocalAndRemoteWithoutBindingLocalContextToASession() {
        assertEquals(AiWorkspaceContext.Kind.NONE, AiWorkspaceContext.none().kind());
        assertEquals(AiWorkspaceContext.Kind.LOCAL_TERMINAL,
                AiWorkspaceContext.localTerminal("本地终端").kind());
        HostId id = HostId.create();
        assertEquals(Optional.of(id), AiWorkspaceContext.remote("SFTP · staging", Optional.of(id)).remoteHostId());
        assertThrows(IllegalArgumentException.class, () -> new AiWorkspaceContext(
                AiWorkspaceContext.Kind.LOCAL_TERMINAL, "local", Optional.of(id)));
    }
}
