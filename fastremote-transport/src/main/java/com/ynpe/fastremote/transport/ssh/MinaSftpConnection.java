// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import com.ynpe.fastremote.domain.transfer.RemoteFileService;
import com.ynpe.fastremote.domain.transfer.SftpConnection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;

/** Authenticated SSH session used exclusively for SFTP subsystem requests. */
final class MinaSftpConnection implements SftpConnection {
    private final SshClient client;
    private final MinaRoutedSshResources routedResources;
    private final ClientSession session;
    private final RemoteFileService remoteFiles;
    private final Runnable afterClose;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicReference<Throwable> terminationFailure = new java.util.concurrent.atomic.AtomicReference<>();
    private final CompletableFuture<Void> termination = new CompletableFuture<>();

    MinaSftpConnection(SshClient client, ClientSession session, Runnable afterClose) {
        this.client = Objects.requireNonNull(client, "client");
        this.routedResources = null;
        this.session = Objects.requireNonNull(session, "session");
        this.afterClose = Objects.requireNonNull(afterClose, "afterClose");
        remoteFiles = new MinaRemoteFileService(session);
        session.addSessionListener(new SessionListener() {
            @Override public void sessionException(Session ignored, Throwable failure) {
                terminationFailure.compareAndSet(null, failure);
            }
            @Override public void sessionClosed(Session ignored) {
                Throwable failure = terminationFailure.get();
                if (failure == null) termination.complete(null);
                else termination.completeExceptionally(failure);
            }
        });
    }

    MinaSftpConnection(MinaRoutedSshResources routedResources, ClientSession session, Runnable afterClose) {
        this.client = null;
        this.routedResources = Objects.requireNonNull(routedResources, "routedResources");
        this.session = Objects.requireNonNull(session, "session");
        this.afterClose = Objects.requireNonNull(afterClose, "afterClose");
        remoteFiles = new MinaRemoteFileService(session);
        session.addSessionListener(new SessionListener() {
            @Override public void sessionException(Session ignored, Throwable failure) {
                terminationFailure.compareAndSet(null, failure);
            }
            @Override public void sessionClosed(Session ignored) {
                Throwable failure = terminationFailure.get();
                if (failure == null) termination.complete(null);
                else termination.completeExceptionally(failure);
            }
        });
    }

    @Override public RemoteFileService remoteFiles() { return remoteFiles; }

    @Override public boolean isOpen() { return !closed.get() && session.isOpen(); }

    @Override public CompletionStage<Void> termination() { return termination; }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        session.close(false);
        if (routedResources == null) client.stop();
        else routedResources.close();
        termination.complete(null);
        afterClose.run();
    }
}
