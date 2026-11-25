// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Owns the one full FastRemote process and forwards bounded launch requests from later invocations. */
final class SingleInstanceBroker implements AutoCloseable {
    private static final int PROTOCOL_VERSION = 2;
    private static final int MAX_MESSAGE_BYTES = 16 * 1024;
    private static final int MAX_PENDING_REQUESTS = 64;
    private static final Duration FORWARD_TIMEOUT = Duration.ofSeconds(8);
    private static final String LOCK_FILE = "instance.lock";
    private static final String METADATA_FILE = "instance.properties";

    private final Path metadataPath;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final ServerSocket server;
    private final String token;
    private final Thread acceptThread;
    private final ArrayBlockingQueue<LaunchRequest> pending =
            new ArrayBlockingQueue<>(MAX_PENDING_REQUESTS);
    private final Object dispatchMonitor = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private Consumer<LaunchRequest> consumer;

    private SingleInstanceBroker(
            Path metadataPath, FileChannel lockChannel, FileLock lock,
            ServerSocket server, String token) throws IOException {
        this.metadataPath = metadataPath;
        this.lockChannel = lockChannel;
        this.lock = lock;
        this.server = server;
        this.token = token;
        writeMetadata(metadataPath, server.getLocalPort(), token);
        acceptThread = Thread.ofPlatform().daemon().name("fastremote-instance-broker").start(this::acceptLoop);
    }

    /** Returns the primary broker, or {@code null} after forwarding to an existing primary. */
    static SingleInstanceBroker startOrForward(Path applicationDataDirectory, LaunchRequest request)
            throws IOException {
        Objects.requireNonNull(applicationDataDirectory, "applicationDataDirectory");
        Objects.requireNonNull(request, "request");
        Path directory = applicationDataDirectory.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        restrictPermissions(directory, true);
        Path lockPath = directory.resolve(LOCK_FILE);
        FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        restrictPermissions(lockPath, false);
        FileLock acquired = null;
        try {
            acquired = channel.tryLock();
        } catch (OverlappingFileLockException ignored) {
            // A second launcher in the same JVM is treated exactly like another process.
        }
        Path metadata = directory.resolve(METADATA_FILE);
        if (acquired == null) {
            channel.close();
            forwardWithRetry(metadata, request);
            return null;
        }

        try {
            ServerSocket server = new ServerSocket();
            server.setReuseAddress(false);
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 16);
            String token = randomToken();
            return new SingleInstanceBroker(metadata, channel, acquired, server, token);
        } catch (IOException | RuntimeException failure) {
            acquired.close();
            channel.close();
            throw failure;
        }
    }

    void installConsumer(Consumer<LaunchRequest> nextConsumer) {
        Objects.requireNonNull(nextConsumer, "nextConsumer");
        List<LaunchRequest> queued = new ArrayList<>();
        synchronized (dispatchMonitor) {
            if (consumer != null) throw new IllegalStateException("Launch request consumer is already installed");
            consumer = nextConsumer;
            pending.drainTo(queued);
        }
        queued.forEach(nextConsumer);
    }

    private void acceptLoop() {
        while (!closed.get()) {
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(3000);
                boolean accepted = receive(socket);
                socket.getOutputStream().write(accepted ? 1 : 0);
                socket.getOutputStream().flush();
            } catch (IOException failure) {
                if (!closed.get()) {
                    System.getLogger(SingleInstanceBroker.class.getName()).log(
                            System.Logger.Level.WARNING, "Unable to receive a local launch request", failure);
                }
            }
        }
    }

    private boolean receive(Socket socket) throws IOException {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        int length = input.readInt();
        if (length <= 0 || length > MAX_MESSAGE_BYTES) return false;
        byte[] payload = input.readNBytes(length);
        if (payload.length != length) throw new EOFException("Incomplete launch request");
        DecodedRequest decoded = decode(payload);
        if (!MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                decoded.token().getBytes(StandardCharsets.UTF_8))) return false;
        return dispatch(decoded.request());
    }

    private boolean dispatch(LaunchRequest request) {
        Consumer<LaunchRequest> current;
        synchronized (dispatchMonitor) {
            current = consumer;
            if (current == null) return pending.offer(request);
        }
        current.accept(request);
        return true;
    }

    private static void forwardWithRetry(Path metadataPath, LaunchRequest request) throws IOException {
        long deadline = System.nanoTime() + FORWARD_TIMEOUT.toNanos();
        IOException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                Metadata metadata = readMetadata(metadataPath);
                forward(metadata, request);
                return;
            } catch (IOException failure) {
                lastFailure = failure;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while forwarding launch request", interrupted);
                }
            }
        }
        throw new IOException("FastRemote is already running but did not accept the launch request", lastFailure);
    }

    private static void forward(Metadata metadata, LaunchRequest request) throws IOException {
        byte[] payload = encode(metadata.token(), request);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), metadata.port()), 1000);
            socket.setSoTimeout(3000);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(payload.length);
            output.write(payload);
            output.flush();
            int acknowledgement = socket.getInputStream().read();
            if (acknowledgement != 1) throw new IOException("Primary instance rejected launch request");
        }
    }

    private static byte[] encode(String token, LaunchRequest request) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(request.localDirectories().isEmpty() ? PROTOCOL_VERSION : 3);
            output.writeUTF(token);
            output.writeBoolean(request.forceNewTerminal());
            output.writeBoolean(request.initialDirectory().isPresent());
            if (request.initialDirectory().isPresent()) {
                output.writeUTF(request.initialDirectory().orElseThrow().toString());
            }
            output.writeInt(request.files().size());
            for (Path file : request.files()) output.writeUTF(file.toString());
            if (!request.localDirectories().isEmpty()) {
                output.writeInt(request.localDirectories().size());
                for (Path directory : request.localDirectories()) output.writeUTF(directory.toString());
            }
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length > MAX_MESSAGE_BYTES) throw new IOException("Launch request is too large");
        return payload;
    }

    private static DecodedRequest decode(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = input.readInt();
            if (version != PROTOCOL_VERSION && version != 3) throw new IOException("Unsupported launch protocol version");
            String token = input.readUTF();
            boolean forceNewTerminal = input.readBoolean();
            String location = input.readBoolean() ? input.readUTF() : null;
            int fileCount = input.readInt();
            if (fileCount < 0 || fileCount > LaunchRequest.MAX_FILES) {
                throw new IOException("Invalid launch request file count");
            }
            List<String> files = new ArrayList<>(fileCount);
            for (int index = 0; index < fileCount; index++) files.add(input.readUTF());
            List<String> directories = new ArrayList<>();
            if (version == 3) {
                int count = input.readInt();
                if (count < 0 || count > LaunchRequest.MAX_FILES || count + fileCount > LaunchRequest.MAX_FILES) {
                    throw new IOException("Invalid launch request path count");
                }
                for (int index = 0; index < count; index++) directories.add(input.readUTF());
            }
            if (input.available() != 0) throw new IOException("Unexpected launch request data");
            return new DecodedRequest(token,
                    LaunchRequest.fromForwarded(forceNewTerminal, location, files, directories));
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void writeMetadata(Path path, int port, String token) throws IOException {
        Properties values = new Properties();
        values.setProperty("version", Integer.toString(PROTOCOL_VERSION));
        values.setProperty("port", Integer.toString(port));
        values.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        values.setProperty("token", token);
        Path temporary = Files.createTempFile(path.getParent(), "instance-", ".tmp");
        restrictPermissions(temporary, false);
        try {
            try (var output = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
                values.store(output, null);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            restrictPermissions(path, false);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Metadata readMetadata(Path path) throws IOException {
        Properties values = new Properties();
        try (var input = Files.newInputStream(path)) {
            values.load(input);
        }
        try {
            int version = Integer.parseInt(values.getProperty("version", ""));
            int port = Integer.parseInt(values.getProperty("port", ""));
            String token = values.getProperty("token", "");
            if (version != PROTOCOL_VERSION || port < 1 || port > 65535 || token.isBlank()) {
                throw new IOException("Invalid instance metadata");
            }
            return new Metadata(port, token);
        } catch (NumberFormatException failure) {
            throw new IOException("Invalid instance metadata", failure);
        }
    }

    private static void restrictPermissions(Path path, boolean directory) {
        try {
            Set<PosixFilePermission> permissions = directory
                    ? Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE)
                    : Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Windows application data is already scoped to the current user. Authentication still
            // protects the loopback endpoint when POSIX permissions are unavailable.
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            server.close();
        } catch (IOException ignored) { }
        try {
            Files.deleteIfExists(metadataPath);
        } catch (IOException ignored) { }
        try {
            lock.release();
        } catch (IOException ignored) { }
        try {
            lockChannel.close();
        } catch (IOException ignored) { }
    }

    private record Metadata(int port, String token) { }
    private record DecodedRequest(String token, LaunchRequest request) { }
}
