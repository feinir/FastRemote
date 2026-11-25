// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import com.ynpe.fastremote.domain.connection.ConnectionException;
import com.ynpe.fastremote.domain.connection.ConnectionFailureType;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.security.HostKeyCheck;
import com.ynpe.fastremote.domain.security.HostKeyDecision;
import com.ynpe.fastremote.domain.security.HostKeyIdentity;
import com.ynpe.fastremote.domain.security.HostKeyPrompt;
import com.ynpe.fastremote.domain.security.HostKeyStatus;
import com.ynpe.fastremote.domain.security.KnownHostRepository;
import java.io.IOException;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;

final class StrictHostKeyVerifier implements ServerKeyVerifier {
    private final Host host;
    private final Port port;
    private final KnownHostRepository knownHosts;
    private final HostKeyPrompt prompt;
    private final AtomicReference<ConnectionException> failure = new AtomicReference<>();

    StrictHostKeyVerifier(Host host, Port port, KnownHostRepository knownHosts, HostKeyPrompt prompt) {
        this.host = host;
        this.port = port;
        this.knownHosts = knownHosts;
        this.prompt = prompt;
    }

    @Override
    public boolean verifyServerKey(ClientSession clientSession, SocketAddress remoteAddress, PublicKey serverKey) {
        HostKeyIdentity presented = new HostKeyIdentity(
                host,
                port,
                KeyUtils.getKeyType(serverKey),
                KeyUtils.getFingerPrint(serverKey),
                PublicKeyEntry.toString(serverKey));
        try {
            Optional<HostKeyIdentity> trusted = knownHosts.find(host, port);
            if (trusted.filter(presented::hasSameKey).isPresent()) {
                return true;
            }

            HostKeyStatus status = trusted.isEmpty() ? HostKeyStatus.UNKNOWN : HostKeyStatus.CHANGED;
            HostKeyDecision decision = prompt.decide(new HostKeyCheck(status, presented, trusted));
            if (decision == HostKeyDecision.TRUST_AND_SAVE) {
                knownHosts.save(presented);
                return true;
            }
            if (decision == HostKeyDecision.ACCEPT_ONCE) {
                return true;
            }
            failure.compareAndSet(null, rejected(status));
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failure.compareAndSet(null, new ConnectionException(
                    ConnectionFailureType.CANCELLED,
                    "Host key confirmation was cancelled.",
                    interrupted));
            return false;
        } catch (IOException storageFailure) {
            failure.compareAndSet(null, new ConnectionException(
                    ConnectionFailureType.HOST_KEY_REJECTED,
                    "The trusted-host database could not be read or updated; the connection was rejected.",
                    storageFailure));
            return false;
        } catch (RuntimeException unexpected) {
            failure.compareAndSet(null, new ConnectionException(
                    ConnectionFailureType.HOST_KEY_REJECTED,
                    "The server identity could not be verified; the connection was rejected.",
                    unexpected));
            return false;
        }
    }

    ConnectionException failure() {
        return failure.get();
    }

    private static ConnectionException rejected(HostKeyStatus status) {
        String message = status == HostKeyStatus.CHANGED
                ? "The server host key changed and was rejected."
                : "The unknown server host key was rejected.";
        return new ConnectionException(ConnectionFailureType.HOST_KEY_REJECTED, message);
    }
}
