// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** One independently cancellable SFTP-only connection attempt. */
public final class SftpConnectionAttempt implements AutoCloseable {
    private final CompletionStage<SftpHandle> result;
    private final Runnable cancelAction;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public SftpConnectionAttempt(CompletionStage<SftpHandle> result, Runnable cancelAction) {
        this.result = Objects.requireNonNull(result, "result");
        this.cancelAction = Objects.requireNonNull(cancelAction, "cancelAction");
    }
    public CompletionStage<SftpHandle> result() { return result; }
    public void cancel() { if (cancelled.compareAndSet(false, true)) cancelAction.run(); }
    @Override public void close() { cancel(); }
}
