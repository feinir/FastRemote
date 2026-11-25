// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import com.ynpe.fastremote.domain.transfer.TransferControl;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.impl.DefaultSftpClient;
import org.apache.sshd.client.channel.ChannelSubsystem;
import org.apache.sshd.core.CoreModuleProperties;

/** Bounds live and closing subsystem channels, not just file-copy workers. */
final class SftpChannelBudget {
    // OpenSSH defaults to ten session channels. Leave one for the owning terminal, if any.
    private static final int MAX_CHANNELS = 9;
    static final org.apache.sshd.common.Property<java.time.Duration> REQUEST_TIMEOUT =
            org.apache.sshd.common.Property.duration("fastremote-sftp-request-timeout", java.time.Duration.ofSeconds(120));
    private final ClientSession session;
    private final Semaphore channels = new Semaphore(MAX_CHANNELS, true);
    // A scanner may wait for its children. Never let scanners occupy every channel.
    private final Semaphore scanners = new Semaphore(4, true);

    SftpChannelBudget(ClientSession session) { this.session = session; }

    Lease open() throws IOException { return open(() -> {}); }

    Lease open(TransferControl control) throws IOException {
        acquire(channels, control);
        try {
            control.checkpoint();
            var client = new DefaultSftpClient(session, null, null) {
                @Override public org.apache.sshd.common.util.buffer.Buffer receive(int id) throws IOException {
                    var response = receive(id, REQUEST_TIMEOUT.getRequired(session));
                    if (response == null) throw new org.apache.sshd.common.SshException(
                            "Timeout expired while waiting for SFTP response id=" + id);
                    return response;
                }

                @Override public org.apache.sshd.common.util.buffer.Buffer receive(
                        int id, java.time.Duration timeout) throws IOException {
                    var response = super.receive(id, timeout);
                    if (response == null && timeout != null && !timeout.isNegative() && !timeout.isZero()) {
                        // A timed-out request invalidates this operation's channel. Abort it
                        // locally: graceful close sends EOF and may escalate cleanup errors
                        // to the shared SSH session. Zero-duration polling is not a timeout.
                        getClientChannel().close(true);
                    }
                    return response;
                }

                @Override protected ChannelSubsystem createSftpChannelSubsystem(ClientSession connection) {
                    ChannelSubsystem channel = super.createSftpChannelSubsystem(connection);
                    // Bound read-ahead on each download so concurrent files do not queue
                    // megabytes ahead of directory responses and jump-host keepalives.
                    CoreModuleProperties.WINDOW_SIZE.set(channel, 256L * 1024);
                    return channel;
                }
            };
            // Keep raw bytes of non-UTF-8 names so listed entries can be renamed and deleted.
            client.setNameDecodingCharset(SftpFileNameCharset.INSTANCE);
            return new Lease(client);
        } catch (IOException | RuntimeException | Error failure) {
            channels.release();
            throw failure;
        }
    }

    ScanPermit scan(TransferControl control) throws IOException {
        acquire(scanners, control);
        return new ScanPermit();
    }

    private void acquire(Semaphore permits, TransferControl control) throws IOException {
        while (true) {
            control.checkpoint();
            if (!session.isOpen()) throw new IOException("SFTP session is closed");
            try {
                if (permits.tryAcquire(100, TimeUnit.MILLISECONDS)) return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("SFTP channel wait interrupted");
            }
        }
    }

    final class ScanPermit implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();
        @Override public void close() {
            if (closed.compareAndSet(false, true)) scanners.release();
        }
    }

    final class Lease implements java.io.Closeable {
        private final SftpClient client;
        private final AtomicBoolean closed = new AtomicBoolean();
        private Lease(SftpClient client) { this.client = client; }
        SftpClient client() { return client; }

        @Override public void close() throws IOException {
            if (!closed.compareAndSet(false, true)) return;
            try { client.close(); }
            finally {
                // DefaultSftpClient.close() only initiates the SSH close exchange. Releasing
                // immediately lets a following batch exceed the server's MaxSessions limit.
                client.getClientChannel().close(false).addListener(ignored -> channels.release());
            }
        }
    }
}
