// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** One independently cancellable queued or running SSH connection attempt. */
public final class ConnectionAttempt implements AutoCloseable {
    private final CompletionStage<TerminalHandle> result;
    private final Runnable cancelAction;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public ConnectionAttempt(CompletionStage<TerminalHandle> result, Runnable cancelAction) {
        this.result = Objects.requireNonNull(result, "result");
        this.cancelAction = Objects.requireNonNull(cancelAction, "cancelAction");
    }

    public CompletionStage<TerminalHandle> result() {
        return result;
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            cancelAction.run();
        }
    }

    @Override
    public void close() {
        cancel();
    }
}
