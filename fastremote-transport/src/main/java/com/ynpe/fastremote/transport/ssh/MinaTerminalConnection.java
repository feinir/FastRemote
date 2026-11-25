// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import com.ynpe.fastremote.domain.transfer.RemoteFileService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import com.ynpe.fastremote.domain.terminal.TerminalTermination;
import com.ynpe.fastremote.domain.terminal.RemoteCommandResult;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;

final class MinaTerminalConnection implements TerminalConnection {
    private final SshClient client;
    private final MinaRoutedSshResources routedResources;
    private final ClientSession session;
    private final ChannelShell channel;
    private final InputStream input;
    private final OutputStream output;
    private final Runnable afterClose;
    private final RemoteFileService remoteFiles;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean idleTimedOut = new AtomicBoolean();
    private final AtomicLong lastTerminalActivityNanos = new AtomicLong(System.nanoTime());
    private final CompletableFuture<TerminalTermination> termination = new CompletableFuture<>();
    private final long idleTimeoutNanos;
    private volatile Charset commandOutputCharset;
    private volatile boolean commandOutputCharsetChecked;

    MinaTerminalConnection(
            SshClient client,
            ClientSession session,
            ChannelShell channel,
            Duration idleTimeout,
            Runnable afterClose) {
        this.client = Objects.requireNonNull(client, "client");
        this.routedResources = null;
        this.session = Objects.requireNonNull(session, "session");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.afterClose = Objects.requireNonNull(afterClose, "afterClose");
        InputStream channelInput = Objects.requireNonNull(channel.getInvertedOut(), "channel output stream");
        OutputStream channelOutput = Objects.requireNonNull(channel.getInvertedIn(), "channel input stream");
        input = new ActivityInputStream(channelInput);
        output = new ActivityOutputStream(channelOutput);
        idleTimeoutNanos = idleTimeout == null ? 0L : idleTimeout.toNanos();
        remoteFiles = new MinaRemoteFileService(session);
        session.addSessionListener(new SessionListener() {
            @Override
            public void sessionClosed(Session ignored) {
                termination.complete(idleTimedOut.get()
                        ? TerminalTermination.idleTimeout()
                        : TerminalTermination.disconnected());
            }
        });
        channel.addChannelListener(new ChannelListener() {
            @Override
            public void channelClosed(Channel ignored, Throwable reason) {
                termination.complete(terminationFromClosedChannel());
            }
        });
    }

    MinaTerminalConnection(
            MinaRoutedSshResources routedResources,
            ClientSession session,
            ChannelShell channel,
            Duration idleTimeout,
            Runnable afterClose) {
        this.client = null;
        this.routedResources = Objects.requireNonNull(routedResources, "routedResources");
        this.session = Objects.requireNonNull(session, "session");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.afterClose = Objects.requireNonNull(afterClose, "afterClose");
        InputStream channelInput = Objects.requireNonNull(channel.getInvertedOut(), "channel output stream");
        OutputStream channelOutput = Objects.requireNonNull(channel.getInvertedIn(), "channel input stream");
        input = new ActivityInputStream(channelInput);
        output = new ActivityOutputStream(channelOutput);
        idleTimeoutNanos = idleTimeout == null ? 0L : idleTimeout.toNanos();
        remoteFiles = new MinaRemoteFileService(session);
        session.addSessionListener(new SessionListener() {
            @Override
            public void sessionClosed(Session ignored) {
                termination.complete(idleTimedOut.get()
                        ? TerminalTermination.idleTimeout()
                        : TerminalTermination.disconnected());
            }
        });
        channel.addChannelListener(new ChannelListener() {
            @Override
            public void channelClosed(Channel ignored, Throwable reason) {
                termination.complete(terminationFromClosedChannel());
            }
        });
    }

    @Override
    public RemoteFileService remoteFiles() {
        return remoteFiles;
    }

    @Override
    public InputStream input() {
        return input;
    }

    @Override
    public OutputStream output() {
        return output;
    }

    @Override
    public RemoteCommandResult executeCommand(String command, Duration timeout, int maximumOutputBytes)
            throws IOException, InterruptedException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(timeout, "timeout");
        if (command.isBlank()) throw new IllegalArgumentException("command must not be blank");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        if (maximumOutputBytes < 1) throw new IllegalArgumentException("maximumOutputBytes must be positive");
        if (!isOpen()) throw new IOException("SSH session is closed");

        OutputBudget outputBudget = new OutputBudget(maximumOutputBytes);
        BoundedOutputStream stdout = new BoundedOutputStream(maximumOutputBytes, outputBudget);
        BoundedOutputStream stderr = new BoundedOutputStream(maximumOutputBytes, outputBudget);
        try (ChannelExec exec = session.createExecChannel(command)) {
            exec.setOut(stdout);
            exec.setErr(stderr);
            long deadline = System.nanoTime() + timeout.toNanos();
            exec.open().verify(timeout);
            while (exec.isOpen() && System.nanoTime() < deadline) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Interrupted while reading remote command output");
                }
                exec.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 200L);
            }
            if (exec.isOpen()) {
                exec.close(true);
                throw new IOException("Remote command timed out");
            }
            Charset preferred = null;
            if (stdout.requiresFallback() || stderr.requiresFallback()) {
                preferred = detectCommandCharset();
            }
            return new RemoteCommandResult(
                    stdout.text(preferred), stderr.text(preferred), Objects.requireNonNullElse(exec.getExitStatus(), -1),
                    stdout.truncated() || stderr.truncated());
        }
    }

    private Charset detectCommandCharset() {
        if (commandOutputCharsetChecked) return commandOutputCharset;
        synchronized (this) {
            if (commandOutputCharsetChecked) return commandOutputCharset;
            try {
                try {
                    commandOutputCharset = detectWindowsCommandCharset();
                } catch (IOException ignored) {
                    // Continue with the POSIX locale probe.
                }
                if (commandOutputCharset == null) {
                    try {
                        commandOutputCharset = detectPosixLocaleCharset();
                    } catch (IOException ignored) {
                        // Fall through to conservative best-effort decoding.
                    }
                }
            } catch (RuntimeException ignored) {
                // Restricted shells and unknown environments fall through to best-effort decoding.
            } finally {
                commandOutputCharsetChecked = true;
            }
            return commandOutputCharset;
        }
    }

    private Charset detectWindowsCommandCharset() throws IOException {
        BoundedOutputStream output = runCharsetProbe("cmd.exe /d /q /c chcp");
        return output == null ? null : charsetForWindowsCodePage(output.asciiCodePage());
    }

    private Charset detectPosixLocaleCharset() throws IOException {
        BoundedOutputStream output = runCharsetProbe("locale charmap");
        if (output == null) return null;
        String name = output.asciiToken();
        try {
            return name.isEmpty() || !Charset.isSupported(name) ? null : Charset.forName(name);
        } catch (IllegalArgumentException unsupported) {
            return null;
        }
    }

    private BoundedOutputStream runCharsetProbe(String command) throws IOException {
        BoundedOutputStream output = new BoundedOutputStream(4 * 1024, new OutputBudget(4 * 1024));
        try (ChannelExec probe = session.createExecChannel(command)) {
            probe.setOut(output);
            probe.setErr(new BoundedOutputStream(256, new OutputBudget(256)));
            probe.open().verify(Duration.ofSeconds(2));
            probe.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), Duration.ofSeconds(2).toMillis());
            if (probe.isOpen()) probe.close(true);
            return Objects.requireNonNullElse(probe.getExitStatus(), -1) == 0 ? output : null;
        }
    }

    private static Charset charsetForWindowsCodePage(int codePage) {
        String name = switch (codePage) {
            case 65001 -> "UTF-8";
            case 936, 54936 -> "GB18030";
            case 950 -> "Big5";
            case 932 -> "Shift_JIS";
            case 949 -> "x-windows-949";
            case 874 -> "windows-874";
            case 1250, 1251, 1252, 1253, 1254, 1255, 1256, 1257, 1258 -> "windows-" + codePage;
            case 437, 737, 775, 850, 852, 855, 857, 858, 860, 861, 862, 863, 864, 865, 866, 869 -> "IBM" + codePage;
            default -> null;
        };
        return name == null || !Charset.isSupported(name) ? null : Charset.forName(name);
    }

    @Override
    public boolean isOpen() {
        return !closed.get() && channel.isOpen() && session.isOpen();
    }

    @Override
    public void resize(int columns, int rows) {
        if (closed.get() || columns < 1 || rows < 1) {
            return;
        }
        try {
            channel.sendWindowChange(columns, rows);
        } catch (IOException failure) {
            System.getLogger(MinaTerminalConnection.class.getName())
                    .log(System.Logger.Level.WARNING, "Unable to resize SSH terminal", failure);
        }
    }

    @Override
    public java.util.concurrent.CompletionStage<TerminalTermination> termination() {
        return termination;
    }

    @Override
    public TerminalTermination waitForTermination() throws InterruptedException {
        if (!channel.isOpen()) termination.complete(terminationFromClosedChannel());
        try {
            return termination.get();
        } catch (ExecutionException impossible) {
            throw new IllegalStateException("Terminal termination signal failed", impossible.getCause());
        }
    }

    private TerminalTermination terminationFromClosedChannel() {
        Integer status = channel.getExitStatus();
        if (idleTimedOut.get()) {
            return TerminalTermination.idleTimeout();
        }
        if (status != null) {
            return TerminalTermination.exited(status);
        }
        return session.isOpen() ? TerminalTermination.eof() : TerminalTermination.disconnected();
    }

    void closeIfIdle(long nowNanos) {
        if (idleTimeoutNanos <= 0L || closed.get()) return;
        if (nowNanos - lastTerminalActivityNanos.get() < idleTimeoutNanos) return;
        if (idleTimedOut.compareAndSet(false, true)) close();
    }

    private void markActivity() {
        lastTerminalActivityNanos.set(System.nanoTime());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            input.close();
        } catch (IOException ignored) {
            // The channel close below owns the actual network resource.
        }
        try {
            output.close();
        } catch (IOException ignored) {
            // The channel close below owns the actual network resource.
        }
        channel.close(false);
        session.close(false);
        termination.complete(idleTimedOut.get()
                ? TerminalTermination.idleTimeout()
                : TerminalTermination.disconnected());
        if (routedResources == null) client.stop();
        else routedResources.close();
        afterClose.run();
    }

    private final class ActivityInputStream extends FilterInputStream {
        private ActivityInputStream(InputStream delegate) {
            super(delegate);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) markActivity();
            return value;
        }

        @Override
        public int read(byte[] values, int offset, int length) throws IOException {
            int read = super.read(values, offset, length);
            if (read > 0) markActivity();
            return read;
        }
    }

    private final class ActivityOutputStream extends FilterOutputStream {
        private ActivityOutputStream(OutputStream delegate) {
            super(delegate);
        }

        @Override
        public void write(int value) throws IOException {
            super.write(value);
            markActivity();
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            out.write(values, offset, length);
            if (length > 0) markActivity();
        }
    }

    private static final class BoundedOutputStream extends OutputStream {
        private final byte[] bytes;
        private final OutputBudget budget;
        private int size;
        private boolean truncated;

        private BoundedOutputStream(int maximumBytes, OutputBudget budget) {
            bytes = new byte[maximumBytes];
            this.budget = budget;
        }

        @Override
        public void write(int value) {
            if (size < bytes.length && budget.claim(1) == 1) bytes[size++] = (byte) value;
            else truncated = true;
        }

        @Override
        public void write(byte[] values, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, values.length);
            int copied = budget.claim(Math.min(length, bytes.length - size));
            if (copied > 0) {
                System.arraycopy(values, offset, bytes, size, copied);
                size += copied;
            }
            if (copied < length) truncated = true;
        }

        private String text(Charset preferred) {
            return RemoteCommandOutputDecoder.decode(bytes, size, truncated, preferred);
        }

        private boolean requiresFallback() {
            return !RemoteCommandOutputDecoder.isStrictUtf8(bytes, size, truncated);
        }

        private int asciiCodePage() {
            int value = -1;
            int current = 0;
            int digits = 0;
            for (int index = 0; index < size; index++) {
                int next = Byte.toUnsignedInt(bytes[index]);
                if (next >= '0' && next <= '9') {
                    current = current * 10 + next - '0';
                    digits++;
                } else {
                    if (digits >= 3 && digits <= 5) value = current;
                    current = 0;
                    digits = 0;
                }
            }
            if (digits >= 3 && digits <= 5) value = current;
            return value;
        }

        private String asciiToken() {
            StringBuilder value = new StringBuilder(Math.min(size, 64));
            for (int index = 0; index < size && value.length() < 64; index++) {
                int next = Byte.toUnsignedInt(bytes[index]);
                boolean allowed = next >= 'A' && next <= 'Z'
                        || next >= 'a' && next <= 'z'
                        || next >= '0' && next <= '9'
                        || next == '-' || next == '_' || next == '.';
                if (allowed) value.append((char) next);
                else if (!value.isEmpty()) break;
            }
            return value.toString();
        }

        private boolean truncated() {
            return truncated;
        }
    }

    private static final class OutputBudget {
        private int remaining;

        private OutputBudget(int maximumBytes) {
            remaining = maximumBytes;
        }

        private synchronized int claim(int requested) {
            int granted = Math.min(requested, remaining);
            remaining -= granted;
            return granted;
        }
    }
}
