// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.kex.KexState;
import org.apache.sshd.client.session.AbstractClientSession;
import org.apache.sshd.client.session.ClientConnectionService;
import org.apache.sshd.client.session.ClientConnectionServiceFactory;
import org.apache.sshd.common.Service;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.buffer.Buffer;

/** Incoming channel traffic is also proof of life when keepalive replies queue behind file data. */
final class TrafficAwareClientConnectionService extends ClientConnectionService {
    static final ClientConnectionServiceFactory FACTORY = new ClientConnectionServiceFactory() {
        @Override public Service create(Session session) throws IOException {
            var service = new TrafficAwareClientConnectionService((AbstractClientSession) session);
            service.addPortForwardingEventListenerManager(this);
            return service;
        }
    };

    private final AtomicBoolean incomingTraffic = new AtomicBoolean();
    private int silentTicks;

    private TrafficAwareClientConnectionService(AbstractClientSession session) throws IOException {
        super(session);
    }

    @Override public void process(int cmd, Buffer buffer) throws Exception {
        // Only decrypted, validated inbound SSH packets reach this service. Outbound writes
        // cannot mask a dead connection. This also covers direct-tcpip data on jump hosts.
        incomingTraffic.set(true);
        super.process(cmd, buffer);
    }
    @Override protected boolean sendHeartBeat() {
        if (getSession().getKexState() != KexState.DONE) {
            silentTicks = 0;
            return false;
        }
        if (incomingTraffic.getAndSet(false)) {
            silentTicks = 0;
        }
        // Traffic proves liveness but must not postpone the configured keepalive cadence.
        if (outstandingHeartbeats.get() > 0) {
            if (++silentTicks >= heartbeatMaxNoReply) {
                getSession().exceptionCaught(new SshException("SSH peer did not respond within the keepalive timeout"));
            }
            // Keep at most one pending global request. Slow replies must not accumulate
            // an unbounded queue while other channel traffic proves the peer is alive.
            return false;
        }
        silentTicks = 0;
        return super.sendHeartBeat();
    }
}
