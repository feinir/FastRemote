// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletionStage;

/** UI-facing owner of one SFTP-only SSH connection. */
public final class SftpHandle implements AutoCloseable {
    private final RemoteFilesHandle remoteFiles;
    private final Runnable closeAction;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletionStage<Void> termination;

    public SftpHandle(RemoteFilesHandle remoteFiles, Runnable closeAction, CompletionStage<Void> termination) {
        this.remoteFiles = Objects.requireNonNull(remoteFiles, "remoteFiles");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
        this.termination = Objects.requireNonNull(termination, "termination");
    }

    public RemoteFilesHandle remoteFiles() { return remoteFiles; }

    public CompletionStage<Void> termination() { return termination; }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) closeAction.run();
    }
}
