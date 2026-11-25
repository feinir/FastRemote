// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.terminal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.concurrent.atomic.AtomicBoolean;

final class TerminalInputGateway {
    private final OutputStream output;
    private final LongConsumer byteCounter;
    private Runnable outsideInput = () -> {};
    void observeOutsideInput(Runnable listener) { outsideInput = Objects.requireNonNull(listener); }
    private final AtomicBoolean inputEnabled = new AtomicBoolean(true);

    TerminalInputGateway(OutputStream output) {
        this(output, ignored -> { });
    }

    TerminalInputGateway(OutputStream output, LongConsumer byteCounter) {
        this.output = Objects.requireNonNull(output, "output");
        this.byteCounter = Objects.requireNonNull(byteCounter, "byteCounter");
    }

    void write(byte[] bytes) throws IOException {
        if (writeBytes(bytes)) outsideInput.run();
    }
    void writeAutomation(byte[] bytes) throws IOException {
        if (!writeBytes(bytes)) throw new IOException("Terminal input is disabled");
    }
    private synchronized boolean writeBytes(byte[] bytes) throws IOException {
        if (!inputEnabled.get()) return false;
        output.write(bytes);
        output.flush();
        byteCounter.accept(bytes.length);
        return true;
    }

    void setInputEnabled(boolean enabled) { inputEnabled.set(enabled); }

    void write(String text) throws IOException {
        write(text.getBytes(StandardCharsets.UTF_8));
    }
}
