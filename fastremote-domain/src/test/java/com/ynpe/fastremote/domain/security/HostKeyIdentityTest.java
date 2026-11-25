// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import org.junit.jupiter.api.Test;

class HostKeyIdentityTest {
    @Test
    void comparesAlgorithmAndEncodedKeyRatherThanDisplayFingerprint() {
        HostKeyIdentity first = identity("ssh-ed25519", "SHA256:first", "AAAA");
        HostKeyIdentity sameKey = identity("ssh-ed25519", "SHA256:other-display", "AAAA");
        HostKeyIdentity changed = identity("ssh-ed25519", "SHA256:changed", "BBBB");

        assertTrue(first.hasSameKey(sameKey));
        assertFalse(first.hasSameKey(changed));
    }

    private static HostKeyIdentity identity(String algorithm, String fingerprint, String key) {
        return new HostKeyIdentity(new Host("example.com"), new Port(22), algorithm, fingerprint, key);
    }
}
