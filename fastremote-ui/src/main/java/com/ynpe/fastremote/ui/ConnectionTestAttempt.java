// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** One independently cancellable SSH authentication test that never creates a workspace tab. */
public final class ConnectionTestAttempt implements AutoCloseable {
    private final CompletionStage<Void> result;
    private final Runnable cancelAction;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public ConnectionTestAttempt(CompletionStage<Void> result, Runnable cancelAction) {
        this.result = Objects.requireNonNull(result, "result");
        this.cancelAction = Objects.requireNonNull(cancelAction, "cancelAction");
    }

    public CompletionStage<Void> result() {
        return result;
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) cancelAction.run();
    }

    @Override
    public void close() {
        cancel();
    }
}
