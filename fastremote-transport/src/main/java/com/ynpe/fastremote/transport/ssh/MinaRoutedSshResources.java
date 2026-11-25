// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;

/** Owns every SSH client, authenticated session and internal loopback tunnel in one routed workspace connection. */
final class MinaRoutedSshResources implements AutoCloseable {
    private final List<SshClient> clients = new ArrayList<>();
    private final List<ClientSession> sessions = new ArrayList<>();
    private final List<Closeable> tunnels = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    void add(SshClient client, ClientSession session) {
        clients.add(client);
        sessions.add(session);
    }

    void addTunnel(Closeable tunnel) {
        tunnels.add(tunnel);
    }

    ClientSession lastSession() {
        return sessions.getLast();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (int index = tunnels.size() - 1; index >= 0; index--) {
            try { tunnels.get(index).close(); } catch (java.io.IOException | RuntimeException ignored) { }
        }
        for (int index = sessions.size() - 1; index >= 0; index--) {
            try { sessions.get(index).close(true); } catch (RuntimeException ignored) { }
        }
        for (int index = clients.size() - 1; index >= 0; index--) {
            try { clients.get(index).stop(); } catch (RuntimeException ignored) { }
        }
    }
}
