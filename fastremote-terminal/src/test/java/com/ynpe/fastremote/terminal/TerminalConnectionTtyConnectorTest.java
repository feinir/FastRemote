// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.terminal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import com.techsenger.jeditermfx.core.util.TermSize;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import com.ynpe.fastremote.domain.terminal.TerminalTermination;
import org.junit.jupiter.api.Test;

class TerminalConnectionTtyConnectorTest {
    @Test
    void bridgesUtf8InputOutputResizeAndIdempotentClose() throws Exception {
        FakeConnection connection = new FakeConnection("你好\n".getBytes(StandardCharsets.UTF_8));
        AtomicLong received = new AtomicLong();
        AtomicLong sent = new AtomicLong();
        TerminalInputGateway gateway = new TerminalInputGateway(connection.output(), sent::addAndGet);
        TerminalConnectionTtyConnector connector = new TerminalConnectionTtyConnector(
                connection, gateway, ignored -> { }, received::addAndGet);

        char[] chars = new char[8];
        int count = connector.read(chars, 0, chars.length);
        assertEquals("你好\n", new String(chars, 0, count));
        assertEquals("你好\n".getBytes(StandardCharsets.UTF_8).length, received.get());

        connector.write("pwd\n");
        assertArrayEquals("pwd\n".getBytes(StandardCharsets.UTF_8), connection.written.toByteArray());
        assertEquals(4L, sent.get());

        connector.resize(new TermSize(120, 40));
        assertEquals(120, connection.columns);
        assertEquals(40, connection.rows);

        connector.close();
        connector.close();
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(2).toNanos();
        while (connection.closeCount == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertEquals(1, connection.closeCount);
        assertFalse(connector.isConnected());
    }

    @Test
    void reportsNormalEofAndAbnormalDisconnectDeterministically() throws Exception {
        AtomicReference<TerminalTermination> observed = new AtomicReference<>();
        FakeConnection normal = new FakeConnection(new byte[0]);
        normal.termination = TerminalTermination.eof();
        TerminalConnectionTtyConnector normalConnector =
                new TerminalConnectionTtyConnector(normal, observed::set);

        assertEquals(0, normalConnector.waitFor());
        assertEquals(TerminalTermination.Kind.NORMAL, observed.get().kind());

        FakeConnection disconnected = new FakeConnection(new byte[0]);
        disconnected.termination = TerminalTermination.disconnected();
        TerminalConnectionTtyConnector disconnectedConnector =
                new TerminalConnectionTtyConnector(disconnected, observed::set);
        assertEquals(1, disconnectedConnector.waitFor());
        assertEquals(TerminalTermination.Kind.NETWORK_FAILURE, observed.get().kind());
    }

    @Test
    void frameworkCloseAfterRemoteEofDoesNotRaceTheTransportExitStatus() throws Exception {
        FakeConnection connection = new FakeConnection(new byte[0]);
        TerminalConnectionTtyConnector connector = new TerminalConnectionTtyConnector(connection);
        assertEquals(-1, connector.read(new char[1], 0, 1));

        connector.close();
        Thread.sleep(50L);

        assertEquals(0, connection.closeCount);
    }

    private static final class FakeConnection implements TerminalConnection {
        private final InputStream input;
        private final ByteArrayOutputStream written = new ByteArrayOutputStream();
        private boolean open = true;
        private int columns;
        private int rows;
        private volatile int closeCount;
        private TerminalTermination termination = TerminalTermination.exited(0);

        private FakeConnection(byte[] input) {
            this.input = new ByteArrayInputStream(input);
        }

        private FakeConnection(InputStream input) {
            this.input = input;
        }

        @Override
        public InputStream input() {
            return input;
        }

        @Override
        public OutputStream output() {
            return written;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void resize(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
        }

        @Override
        public CompletionStage<TerminalTermination> termination() {
            return CompletableFuture.completedFuture(termination);
        }

        @Override
        public TerminalTermination waitForTermination() {
            return termination;
        }

        @Override
        public void close() {
            if (open) {
                open = false;
                closeCount++;
            }
        }
    }
}
