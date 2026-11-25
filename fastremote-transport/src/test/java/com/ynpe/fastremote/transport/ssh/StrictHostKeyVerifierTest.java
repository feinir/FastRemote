// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.security.HostKeyDecision;
import com.ynpe.fastremote.domain.security.HostKeyIdentity;
import com.ynpe.fastremote.domain.security.HostKeyStatus;
import com.ynpe.fastremote.domain.security.KnownHostRepository;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StrictHostKeyVerifierTest {
    private Host host;
    private Port port;
    private KeyPair firstKey;
    private KeyPair secondKey;

    @BeforeEach
    void setUp() throws Exception {
        host = new Host("example.test");
        port = new Port(22);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        firstKey = generator.generateKeyPair();
        secondKey = generator.generateKeyPair();
    }

    @Test
    void rejectsUnknownHostUnlessUserExplicitlyAccepts() {
        MemoryKnownHosts repository = new MemoryKnownHosts();
        StrictHostKeyVerifier verifier = new StrictHostKeyVerifier(
                host, port, repository, check -> {
                    repository.lastStatus = check.status();
                    return HostKeyDecision.REJECT;
                });

        assertFalse(verifier.verifyServerKey(null, null, firstKey.getPublic()));
        assertEquals(HostKeyStatus.UNKNOWN, repository.lastStatus);
        assertEquals("HOST_KEY_REJECTED", verifier.failure().failureType().name());
    }

    @Test
    void savesExplicitlyTrustedKeyAndThenMatchesWithoutPrompt() {
        MemoryKnownHosts repository = new MemoryKnownHosts();
        AtomicInteger prompts = new AtomicInteger();
        StrictHostKeyVerifier first = new StrictHostKeyVerifier(host, port, repository, check -> {
            prompts.incrementAndGet();
            repository.lastStatus = check.status();
            return HostKeyDecision.TRUST_AND_SAVE;
        });
        assertTrue(first.verifyServerKey(null, null, firstKey.getPublic()));

        StrictHostKeyVerifier second = new StrictHostKeyVerifier(host, port, repository, check -> {
            prompts.incrementAndGet();
            return HostKeyDecision.REJECT;
        });
        assertTrue(second.verifyServerKey(null, null, firstKey.getPublic()));
        assertEquals(1, prompts.get());
        assertNull(second.failure());
    }

    @Test
    void identifiesChangedKeyAsHighRiskDecision() {
        MemoryKnownHosts repository = new MemoryKnownHosts();
        new StrictHostKeyVerifier(host, port, repository, check -> HostKeyDecision.TRUST_AND_SAVE)
                .verifyServerKey(null, null, firstKey.getPublic());

        StrictHostKeyVerifier changed = new StrictHostKeyVerifier(host, port, repository, check -> {
            repository.lastStatus = check.status();
            return HostKeyDecision.REJECT;
        });
        assertFalse(changed.verifyServerKey(null, null, secondKey.getPublic()));
        assertEquals(HostKeyStatus.CHANGED, repository.lastStatus);
    }

    private final class MemoryKnownHosts implements KnownHostRepository {
        private HostKeyIdentity identity;
        private HostKeyStatus lastStatus;

        @Override
        public Optional<HostKeyIdentity> find(Host requestedHost, Port requestedPort) {
            return Optional.ofNullable(identity);
        }

        @Override
        public void save(HostKeyIdentity saved) throws IOException {
            identity = saved;
        }
    }
}
