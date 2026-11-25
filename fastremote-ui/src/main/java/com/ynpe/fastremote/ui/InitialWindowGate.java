// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** One-shot gate that keeps the primary Stage hidden until its initial workspace is ready. */
final class InitialWindowGate {
    private final CompletableFuture<Void> ready = new CompletableFuture<>();

    CompletionStage<Void> ready() {
        return ready;
    }

    void markReady() {
        ready.complete(null);
    }
}
