// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.terminal;

import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.core.util.TermSize;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import com.ynpe.fastremote.domain.terminal.TerminalTermination;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

final class TerminalConnectionTtyConnector implements TtyConnector {
    private static final ThreadPoolExecutor CLOSE_EXECUTOR = new ThreadPoolExecutor(
            1,
            2,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64),
            Thread.ofPlatform().name("fastremote-terminal-close-", 0).daemon(true).factory(),
            new ThreadPoolExecutor.CallerRunsPolicy());

    static {
        CLOSE_EXECUTOR.allowCoreThreadTimeOut(true);
    }
    private final TerminalConnection connection;
    private final Reader reader;
    private final TerminalInputGateway inputGateway;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean remoteEof = new AtomicBoolean();
    private final Consumer<TerminalTermination> terminationListener;
    private final Consumer<String> outputObserver;

    TerminalConnectionTtyConnector(TerminalConnection connection) {
        this(connection, new TerminalInputGateway(connection.output()), ignored -> {});
    }

    TerminalConnectionTtyConnector(
            TerminalConnection connection, Consumer<TerminalTermination> terminationListener) {
        this(connection, new TerminalInputGateway(connection.output()), terminationListener);
    }

    TerminalConnectionTtyConnector(
            TerminalConnection connection,
            TerminalInputGateway inputGateway,
            Consumer<TerminalTermination> terminationListener) {
        this(connection, inputGateway, terminationListener, ignored -> { }, ignored -> { });
    }

    TerminalConnectionTtyConnector(
            TerminalConnection connection,
            TerminalInputGateway inputGateway,
            Consumer<TerminalTermination> terminationListener,
            LongConsumer receivedByteCounter) {
        this(connection, inputGateway, terminationListener, receivedByteCounter, ignored -> { });
    }

    TerminalConnectionTtyConnector(
            TerminalConnection connection,
            TerminalInputGateway inputGateway,
            Consumer<TerminalTermination> terminationListener,
            LongConsumer receivedByteCounter,
            Consumer<String> outputObserver) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.inputGateway = Objects.requireNonNull(inputGateway, "inputGateway");
        this.terminationListener = Objects.requireNonNull(terminationListener, "terminationListener");
        this.outputObserver = Objects.requireNonNull(outputObserver, "outputObserver");
        reader = new InputStreamReader(new CountingInputStream(connection.input(), receivedByteCounter),
                StandardCharsets.UTF_8);
    }

    private static final class CountingInputStream extends FilterInputStream {
        private final LongConsumer counter;

        private CountingInputStream(InputStream input, LongConsumer counter) {
            super(Objects.requireNonNull(input, "input"));
            this.counter = Objects.requireNonNull(counter, "counter");
        }

        @Override public int read() throws IOException {
            int value = super.read();
            if (value >= 0) counter.accept(1L);
            return value;
        }

        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = super.read(bytes, offset, length);
            if (count > 0) counter.accept(count);
            return count;
        }
    }

    @Override
    public int read(char[] buffer, int offset, int length) throws IOException {
        int count = reader.read(buffer, offset, length);
        if (count < 0) {
            remoteEof.set(true);
        } else if (count > 0) {
            try {
                outputObserver.accept(new String(buffer, offset, count));
            } catch (RuntimeException ignored) {
                // Working-directory inference is optional and must never interrupt terminal output.
            }
        }
        return count;
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        inputGateway.write(bytes);
    }

    @Override
    public void write(String value) throws IOException {
        write(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return !closed.get() && connection.isOpen();
    }

    @Override
    public void resize(@NotNull TermSize termSize) {
        connection.resize(termSize.getColumns(), termSize.getRows());
    }

    @Override
    public int waitFor() throws InterruptedException {
        TerminalTermination termination = connection.waitForTermination();
        terminationListener.accept(termination);
        return termination.exitCode().orElse(termination.kind() == TerminalTermination.Kind.NORMAL ? 0 : 1);
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return "FastRemote SSH terminal";
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            // JediTermFX calls close after its reader sees EOF. At that point MINA may still be
            // processing the peer's exit-status and CHANNEL_CLOSE; closing here would race that
            // event and turn a normal shell exit into a local/network disconnect.
            if (!remoteEof.get()) CLOSE_EXECUTOR.execute(connection::close);
        }
    }
}
