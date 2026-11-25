// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import static org.junit.jupiter.api.Assertions.*;

import com.ynpe.fastremote.domain.transfer.FileTransferScheduler;
import com.ynpe.fastremote.domain.transfer.TransferActivityListener;
import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.Handle;
import org.apache.sshd.sftp.server.SftpEventListener;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystem;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class ParallelSftpTransferTest {
    @TempDir Path directory;
    private SshServer server;
    private SshClient client;
    private ClientSession session;
    private MinaRemoteFileService files;
    private final FileProbe probe = new FileProbe();
    private final List<CompletableFuture<Void>> subsystemCompletions = new CopyOnWriteArrayList<>();

    @BeforeEach
    void connectFixture() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        var keys = new SimpleGeneratorHostKeyProvider(directory.resolve("fixture-host-key"));
        server.setKeyPairProvider(keys);
        server.setPasswordAuthenticator((user, password, connection) -> password.equals("fixture"));
        var subsystem = new SftpSubsystemFactory() {
            @Override public Command createSubsystem(ChannelSession channel) throws IOException {
                var completed = new CompletableFuture<Void>();
                var command = new SftpSubsystem(channel, this) {
                    @Override public void run() {
                        subsystemCompletions.add(completed);
                        try { super.run(); }
                        finally { completed.complete(null); }
                    }
                };
                getRegisteredListeners().forEach(command::addSftpEventListener);
                return command;
            }
        };
        subsystem.addSftpEventListener(probe);
        server.setSubsystemFactories(List.of(subsystem));
        server.start();
        var publicKey = keys.loadKeys(null).iterator().next().getPublic();
        client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((connection, address, key) -> key.equals(publicKey));
        MinaSshSessionConnector.configureClient(client, com.ynpe.fastremote.domain.settings.ConnectionSettings.defaults());
        client.start();
        session = client.connect("fixture", "127.0.0.1", server.getPort()).verify(5000).getSession();
        session.addPasswordIdentity("fixture");
        session.auth().verify(5000);
        files = new MinaRemoteFileService(session);
    }

    @AfterEach
    void closeFixture() throws Exception {
        probe.release.countDown();
        if (session != null) session.close();
        if (client != null) client.stop();
        if (server != null) server.stop(true);
    }

    @Test
    void reportsAcknowledgedFileOutcomesAndFinalRenamedTargets() throws Exception {
        probe.release.countDown();
        Path source = Files.writeString(directory.resolve("observed-input"), "中文-content");
        Path target = Files.writeString(directory.resolve("observed-target"), "old");
        var results = new CopyOnWriteArrayList<String>();
        var listener = new TransferActivityListener() {
            @Override public void processing(com.ynpe.fastremote.domain.transfer.TransferActivity activity) { }
            @Override public void completed(String from, String to, String state, String detail) { results.add(state + ":" + to); }
        };
        try (var scheduler = new FileTransferScheduler(2)) {
            files.uploadTree(source, target.toString(), () -> {}, (a,b) -> {},
                    conflict -> TransferConflictChoice.SKIP, listener, scheduler);
            assertEquals(List.of("SKIPPED:" + target), results);
            results.clear();
            files.uploadTree(source, target.toString(), () -> {}, (a,b) -> {},
                    conflict -> TransferConflictChoice.RENAME, listener, scheduler);
            Path renamed = directory.resolve("observed-target (1)");
            assertEquals(List.of("SUCCEEDED:" + renamed), results);
            assertEquals(Files.readString(source), Files.readString(renamed));
            results.clear();
            Path local = directory.resolve("observed-download");
            files.downloadTree(renamed.toString(), local, () -> {}, (a,b) -> {},
                    conflict -> TransferConflictChoice.CANCEL, listener, scheduler);
            assertEquals(List.of("SUCCEEDED:" + local), results);
            assertEquals(Files.readString(source), Files.readString(local));
        }
    }

    @Test
    void requestTimeoutAbortsOnlyItsChannelAndOtherOperationsRemainUsable() throws Exception {
        probe.release.countDown();
        var connection = new MinaSftpConnection(client, session, () -> {});
        try (var lease = new SftpChannelBudget(session).open()) {
            assertNull(((org.apache.sshd.sftp.client.impl.DefaultSftpClient) lease.client()).receive(Integer.MAX_VALUE, java.time.Duration.ZERO));
            assertTrue(lease.client().isOpen(), "Nonblocking polling must not abort a healthy channel");
        }
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Path stalled = Files.writeString(directory.resolve("stalled"), "stalled");
        Path healthy = Files.writeString(directory.resolve("healthy"), "healthy content");
        ((SftpSubsystemFactory) server.getSubsystemFactories().getFirst()).addSftpEventListener(new SftpEventListener() {
            @Override public void opening(ServerSession remote, String handle, Handle file) throws IOException {
                if (!file.getFile().equals(stalled)) return;
                entered.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            }
        });
        SftpChannelBudget.REQUEST_TIMEOUT.set(session, java.time.Duration.ofMillis(300));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var failed = executor.submit(() -> files.readFile(stalled.toString(), 1024));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertInstanceOf(IOException.class,
                        assertThrows(ExecutionException.class, () -> failed.get(3, TimeUnit.SECONDS)).getCause());
                assertTrue(session.isOpen());
                assertFalse(connection.termination().toCompletableFuture().isDone());
                assertEquals("healthy content", new String(files.readFile(healthy.toString(), 1024), java.nio.charset.StandardCharsets.UTF_8));
            } finally { release.countDown(); }
        }
        assertTrue(session.isOpen());
        assertFalse(connection.termination().toCompletableFuture().isDone());
        var reason = new IOException("fixture transport failure");
        session.exceptionCaught(reason);
        var failure = assertThrows(ExecutionException.class,
                () -> connection.termination().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertSame(reason, failure.getCause());
    }

    @Test
    void incomingTrafficDoesNotPostponeScheduledKeepalives() throws Exception {
        probe.release.countDown();
        var heartbeats = new AtomicInteger();
        server.setGlobalRequestHandlers(List.of((service, request, reply, buffer) -> {
            heartbeats.incrementAndGet();
            return org.apache.sshd.common.channel.RequestHandler.Result.Unsupported;
        }));
        session.close(true).await(5000);
        org.apache.sshd.core.CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, java.time.Duration.ofMillis(100));
        session = client.connect("fixture", "127.0.0.1", server.getPort()).verify(5000).getSession();
        session.addPasswordIdentity("fixture");
        session.auth().verify(5000);
        files = new MinaRemoteFileService(session);
        Path source = Files.writeString(directory.resolve("keepalive-cadence"), "alive");
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(650);
        while (System.nanoTime() < deadline) {
            files.readFile(source.toString(), 1024);
            Thread.sleep(20);
        }
        assertTrue(heartbeats.get() >= 3, "Inbound traffic must not double or suppress the keepalive interval");
        assertTrue(session.isOpen());
    }

    @Test
    void delayedKeepaliveRepliesDoNotDisconnectAnActiveDownloadButSilentPeersStillClose() throws Exception {
        probe.release.countDown();
        var heartbeats = new AtomicInteger();
        var firstHeartbeat = new CountDownLatch(1);
        var silentClosed = new CountDownLatch(1);
        server.setGlobalRequestHandlers(List.of((service, request, reply, buffer) -> {
            heartbeats.incrementAndGet();
            firstHeartbeat.countDown();
            // Pretend to have replied; intentionally send nothing, like a congested reply queue.
            return org.apache.sshd.common.channel.RequestHandler.Result.Replied;
        }));
        session.close(true).await(5000);
        org.apache.sshd.core.CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, java.time.Duration.ofMillis(100));
        session = client.connect("fixture", "127.0.0.1", server.getPort()).verify(5000).getSession();
        session.addPasswordIdentity("fixture");
        session.auth().verify(5000);
        session.addSessionListener(new org.apache.sshd.common.session.SessionListener() {
            @Override public void sessionClosed(org.apache.sshd.common.session.Session ignored) {
                silentClosed.countDown();
            }
        });
        files = new MinaRemoteFileService(session);
        ((SftpSubsystemFactory) server.getSubsystemFactories().getFirst()).addSftpEventListener(new SftpEventListener() {
            @Override public void reading(ServerSession connection, String handle, FileHandle file,
                    long offset, byte[] data, int dataOffset, int length) throws IOException {
                try { Thread.sleep(50); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new java.io.InterruptedIOException("fixture interrupted");
                }
            }
        });
        assertTrue(firstHeartbeat.await(3, TimeUnit.SECONDS));
        long start = System.nanoTime();
        Path source = Files.write(directory.resolve("slow-download"), new byte[1024 * 1024]);
        Path target = directory.resolve("slow-downloaded");
        files.download(source.toString(), target);
        assertEquals(-1, Files.mismatch(source, target));
        assertTrue(System.nanoTime() - start > TimeUnit.MILLISECONDS.toNanos(600),
                "Transfer must outlive the silent-peer deadline");
        assertEquals(1, heartbeats.get(), "Delayed replies must not accumulate global requests");
        assertTrue(session.isOpen(), "Inbound SFTP data must keep the session alive");
        assertTrue(silentClosed.await(3, TimeUnit.SECONDS), "A genuinely silent peer must still time out");
    }

    @Test
    void linkContentCanBeReadDownloadedAndSavedWithoutReplacingRelativeLinkChain() throws Exception {
        probe.release.countDown();
        Path source = Files.writeString(directory.resolve("link-source"), "content of the linked file");
        Path alias = Files.createSymbolicLink(directory.resolve("alias"), source.getFileName());
        Path chain = Files.createSymbolicLink(directory.resolve("chain"), alias.getFileName());
        assertEquals(Files.readString(source), new String(files.readFile(chain.toString(), 1024),
                java.nio.charset.StandardCharsets.UTF_8));
        AtomicLong total = new AtomicLong();
        AtomicLong done = new AtomicLong();
        Path downloaded = directory.resolve("downloaded-alias");
        files.downloadTree(chain.toString(), downloaded, () -> {}, (bytes, copied) -> {
            total.set(bytes); done.set(copied);
        }, ignored -> TransferConflictChoice.OVERWRITE);
        assertEquals(-1, Files.mismatch(source, downloaded));
        assertEquals(Files.size(source), total.get());
        assertEquals(total.get(), done.get());
        files.writeFileAtomic(chain.toString(), "edited".getBytes(java.nio.charset.StandardCharsets.UTF_8), true);
        assertEquals("edited", Files.readString(source));
        Path edited = Files.writeString(directory.resolve("editor-upload"), "saved by editor upload");
        files.upload(edited, chain.toString());
        assertEquals("saved by editor upload", Files.readString(source));
        assertTrue(Files.isSymbolicLink(alias));
        assertTrue(Files.isSymbolicLink(chain));
        assertEquals(alias.getFileName(), Files.readSymbolicLink(chain));
        assertEquals(source.getFileName(), Files.readSymbolicLink(alias));
    }

    @Test
    void oversizedDanglingAndCyclicLinksFailWithoutReadingOrReplacingLinks() throws Exception {
        probe.release.countDown();
        var reads = new AtomicInteger();
        ((SftpSubsystemFactory) server.getSubsystemFactories().getFirst()).addSftpEventListener(new SftpEventListener() {
            @Override public void reading(ServerSession connection, String handle, FileHandle file,
                    long offset, byte[] data, int dataOffset, int length) { reads.incrementAndGet(); }
        });
        Path source = Files.writeString(directory.resolve("large-source"), "large linked content");
        Path alias = Files.createSymbolicLink(directory.resolve("large-alias"), source.getFileName());
        IOException oversized = assertThrows(IOException.class, () -> files.readFile(alias.toString(), 4));
        assertTrue(oversized.getMessage().contains("size limit"));
        assertEquals(0, reads.get());
        Path dangling = Files.createSymbolicLink(directory.resolve("dangling"), Path.of("missing"));
        Path cycle = Files.createSymbolicLink(directory.resolve("cycle"), Path.of("cycle"));
        for (Path link : List.of(dangling, cycle)) {
            assertThrows(IOException.class, () -> files.readFile(link.toString(), 1024));
            assertThrows(IOException.class, () -> files.writeFileAtomic(link.toString(), new byte[]{1}, true));
            assertThrows(IOException.class, () -> files.upload(source, link.toString()));
            assertTrue(Files.isSymbolicLink(link));
        }
        assertEquals("large linked content", Files.readString(source));
        assertTrue(session.isOpen());
    }

    @Test
    void selectedDirectoryLinkDownloadsContentAndNestedLinksCannotRecurseBack() throws Exception {
        probe.release.countDown();
        Path source = Files.createDirectory(directory.resolve("linked-directory"));
        Files.writeString(source.resolve("file"), "content");
        Files.createSymbolicLink(source.resolve("back"), source);
        Path external = Files.createDirectory(directory.resolve("external-directory"));
        Files.writeString(external.resolve("external-file"), "external content");
        Files.createSymbolicLink(source.resolve("external"), external);
        Path alias = Files.createSymbolicLink(directory.resolve("directory-alias"), source.getFileName());
        Path target = directory.resolve("directory-download");
        files.downloadTree(alias.toString(), target, () -> {}, (a, b) -> {},
                ignored -> TransferConflictChoice.OVERWRITE);
        assertEquals("content", Files.readString(target.resolve("file")));
        assertFalse(Files.exists(target.resolve("back")));
        assertEquals("external content", Files.readString(target.resolve("external/external-file")));
    }

    @Test
    void propertiesExposeAbsoluteTargetsForRelativeAbsoluteAndDanglingLinksWithoutReadingContent() throws Exception {
        probe.release.countDown();
        Path folder = Files.createDirectory(directory.resolve("links"));
        Path source = Files.writeString(directory.resolve("properties-source"), "content");
        Path relative = Files.createSymbolicLink(folder.resolve("relative"), Path.of("../properties-source"));
        Path absolute = Files.createSymbolicLink(folder.resolve("absolute"), source);
        Path dangling = Files.createSymbolicLink(folder.resolve("dangling"), Path.of("../missing"));
        var reads = new AtomicInteger();
        ((SftpSubsystemFactory) server.getSubsystemFactories().getFirst()).addSftpEventListener(new SftpEventListener() {
            @Override public void reading(ServerSession connection, String handle, FileHandle file,
                    long offset, byte[] data, int dataOffset, int length) { reads.incrementAndGet(); }
        });
        assertEquals(MinaRemoteFileService.displayPath(source.toString()), files.readAttributes(relative.toString()).symbolicLinkTarget().orElseThrow());
        assertEquals(MinaRemoteFileService.displayPath(source.toString()), files.readAttributes(absolute.toString()).symbolicLinkTarget().orElseThrow());
        assertEquals(MinaRemoteFileService.displayPath(directory.resolve("missing").toString()),
                files.readAttributes(dangling.toString()).symbolicLinkTarget().orElseThrow());
        assertTrue(files.readAttributes(source.toString()).symbolicLinkTarget().isEmpty());
        assertEquals(0, reads.get());
    }

    @Test
    void cancelledChannelWaitDoesNotLeakOrOverReleasePermits() throws Exception {
        var budget = new SftpChannelBudget(session);
        var leases = new java.util.ArrayList<SftpChannelBudget.Lease>();
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        var waiting = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            try {
                for (int index = 0; index < 9; index++) leases.add(budget.open());
                Future<?> pending = executor.submit(() -> {
                    try (var lease = budget.open(() -> {
                        waiting.countDown();
                        if (cancelled.get()) throw new java.io.InterruptedIOException("cancelled");
                    })) { fail("A tenth channel must wait"); }
                    return null;
                });
                assertTrue(waiting.await(5, TimeUnit.SECONDS));
                cancelled.set(true);
                assertInstanceOf(java.io.InterruptedIOException.class,
                        assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS)).getCause());
                leases.getFirst().close();
                leases.getFirst().close();
                try (var replacement = budget.open()) {
                    assertTrue(replacement.client().isOpen());
                    // Eight original channels + replacement still consume all nine permits.
                    Future<?> interrupted = executor.submit(() -> {
                        Thread.currentThread().interrupt();
                        assertThrows(java.io.InterruptedIOException.class, budget::open);
                    });
                    interrupted.get(5, TimeUnit.SECONDS);
                }
            } finally {
                for (var lease : leases) lease.close();
            }
        }
    }

    @Test
    void closingSessionUnblocksScannerWaitingForAChannel() throws Exception {
        var budget = new SftpChannelBudget(session);
        var waiting = new CountDownLatch(1);
        try (var scan1 = budget.scan(() -> {});
                var scan2 = budget.scan(() -> {});
                var scan3 = budget.scan(() -> {});
                var scan4 = budget.scan(() -> {});
                var executor = Executors.newSingleThreadExecutor()) {
            Future<?> pending = executor.submit(() -> {
                try (var next = budget.scan(waiting::countDown)) { fail("Scanner must wait"); }
                return null;
            });
            assertTrue(waiting.await(5, TimeUnit.SECONDS));
            session.close(true).await(5000);
            assertInstanceOf(IOException.class,
                    assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS)).getCause());
        }
    }

    @Test
    void multipleDirectoryScannersLeaveCapacityForTheirChildren() throws Exception {
        org.apache.sshd.core.CoreModuleProperties.MAX_CONCURRENT_CHANNELS.set(server, 9);
        probe.release.countDown();
        Path source = Files.createDirectory(directory.resolve("many-children"));
        for (int i = 0; i < 24; i++) Files.writeString(source.resolve("child-" + i), "content-" + i);
        try (var scheduler = new FileTransferScheduler(16);
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var jobs = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 9; i++) {
                Path target = directory.resolve("tree-target-" + i);
                jobs.add(executor.submit(() -> {
                    start.await();
                    files.uploadTree(source, target.toString(), () -> {}, (a, b) -> {},
                            ignored -> TransferConflictChoice.OVERWRITE, TransferActivityListener.NONE, scheduler);
                    assertTreeEquals(source, target);
                    return null;
                }));
            }
            start.countDown();
            for (var job : jobs) job.get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void queuedUploadsAndRefreshBurstsRespectOpenSshChannelLimit() throws Exception {
        // MINA checks current size > configured limit before registering the next channel.
        org.apache.sshd.core.CoreModuleProperties.MAX_CONCURRENT_CHANNELS.set(server, 9);
        probe.release.countDown();
        // Keep readdir pressure independent of the fixture server's stat/rename race on .part files.
        Path refreshDirectory = Files.createDirectory(directory.resolve("refresh-directory"));
        Files.writeString(refreshDirectory.resolve("stable-entry"), "content");
        try (var scheduler = new FileTransferScheduler(16);
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int batch = 0; batch < 3; batch++) {
                var start = new CountDownLatch(1);
                var work = new java.util.ArrayList<Future<?>>();
                for (int index = 0; index < 9; index++) {
                    Path source = Files.write(directory.resolve("burst-source-" + batch + "-" + index),
                            new byte[128 * 1024]);
                    Path target = directory.resolve("burst-target-" + batch + "-" + index);
                    work.add(executor.submit(() -> {
                        start.await();
                        files.uploadTree(source, target.toString(), () -> {}, (a, b) -> {},
                                ignored -> TransferConflictChoice.OVERWRITE, TransferActivityListener.NONE, scheduler);
                        assertEquals(-1, Files.mismatch(source, target));
                        return null;
                    }));
                }
                for (int index = 0; index < 24; index++) {
                    work.add(executor.submit(() -> {
                        start.await();
                        files.list(refreshDirectory.toString());
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> task : work) task.get(15, TimeUnit.SECONDS);
            }
        }
        awaitServerSubsystems();
        try (var paths = Files.walk(directory)) {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }

    @Test
    void newUploadsAndDownloadsPreserveSourceExecutablePermissionsAcrossTransferPaths() throws Exception {
        Assumptions.assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        probe.release.countDown();
        for (boolean upload : new boolean[]{true, false}) {
            for (int route = 0; route < 3; route++) {
                Path source = Files.writeString(directory.resolve("source-" + upload + route), "executable content");
                Path target = directory.resolve("target-" + upload + route);
                var expected = java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-x--x");
                Files.setPosixFilePermissions(source, expected);
                transfer(upload, route, source, target);
                assertEquals("executable content", Files.readString(target));
                assertEquals(expected, Files.getPosixFilePermissions(target));
            }
        }
    }

    @Test
    void overwritePreservesDestinationPermissionsInsteadOfSourcePermissions() throws Exception {
        Assumptions.assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        probe.release.countDown();
        for (boolean upload : new boolean[]{true, false}) {
            for (int route = 0; route < 3; route++) {
                for (String mode : List.of("rwxr-x---", "rw-r-----")) {
                    String suffix = upload + "-" + route + "-" + mode;
                    Path source = Files.writeString(directory.resolve("source-" + suffix), "replacement");
                    Path target = Files.writeString(directory.resolve("target-" + suffix), "original");
                    Files.setPosixFilePermissions(source, java.nio.file.attribute.PosixFilePermissions.fromString(
                            mode.startsWith("rwx") ? "rw-------" : "rwxr-xr-x"));
                    var expected = java.nio.file.attribute.PosixFilePermissions.fromString(mode);
                    Files.setPosixFilePermissions(target, expected);
                    transfer(upload, route, source, target);
                    assertEquals("replacement", Files.readString(target));
                    assertEquals(expected, Files.getPosixFilePermissions(target));
                }
            }
        }
    }

    @Test
    void editorAtomicSaveKeepsExistingExecutablePermissions() throws Exception {
        Assumptions.assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        probe.release.countDown();
        Path target = Files.writeString(directory.resolve("script.sh"), "original");
        var mode = java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-x---");
        Files.setPosixFilePermissions(target, mode);
        files.writeFileAtomic(target.toString(), "replacement".getBytes(java.nio.charset.StandardCharsets.UTF_8), true);
        assertEquals("replacement", Files.readString(target));
        assertEquals(mode, Files.getPosixFilePermissions(target));
    }

    @Test
    void rejectedPermissionUpdateLeavesOriginalContentAndCleansTemporaryUpload() throws Exception {
        Assumptions.assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        probe.release.countDown();
        Path source = Files.writeString(directory.resolve("source.txt"), "replacement");
        Path target = Files.writeString(directory.resolve("target.txt"), "original");
        var mode = java.nio.file.attribute.PosixFilePermissions.fromString("rwx------");
        Files.setPosixFilePermissions(target, mode);
        ((SftpSubsystemFactory) server.getSubsystemFactories().getFirst()).addSftpEventListener(new SftpEventListener() {
            @Override public void modifyingAttributes(ServerSession session, Path path, java.util.Map<String, ?> attrs)
                    throws IOException {
                if (path.getFileName().toString().endsWith(".part")) throw new IOException("fixture chmod denied");
            }
        });
        assertThrows(IOException.class, () -> files.upload(source, target.toString()));
        assertEquals("original", Files.readString(target));
        assertEquals(mode, Files.getPosixFilePermissions(target));
        assertThrows(IOException.class, () -> files.writeFileAtomic(target.toString(), new byte[]{1}, true));
        assertEquals("original", Files.readString(target));
        Path fresh = directory.resolve("new-target");
        assertThrows(IOException.class, () -> files.upload(source, fresh.toString()));
        assertFalse(Files.exists(fresh));
        try (var children = Files.list(directory)) {
            assertFalse(children.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }

    @Test
    void recursiveTransfersKeepExecutableBitsOnNestedFiles() throws Exception {
        Assumptions.assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        probe.release.countDown();
        for (boolean upload : new boolean[]{true, false}) {
            for (int route : new int[]{1, 2}) {
                Path source = Files.createDirectories(directory.resolve("nested-source-" + upload + route));
                Path script = Files.writeString(Files.createDirectories(source.resolve("bin")).resolve("tool"), "tool");
                var expected = java.nio.file.attribute.PosixFilePermissions.fromString("rwx--x---");
                Files.setPosixFilePermissions(script, expected);
                Path target = directory.resolve("nested-target-" + upload + route);
                transfer(upload, route, source, target);
                assertEquals("tool", Files.readString(target.resolve("bin/tool")));
                assertEquals(expected, Files.getPosixFilePermissions(target.resolve("bin/tool")));
            }
        }
    }

    @Test
    void renamedConflictsUseSourcePermissionsWithoutChangingTheExistingTarget() throws Exception {
        Assumptions.assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        probe.release.countDown();
        for (boolean upload : new boolean[]{true, false}) {
            Path source = Files.writeString(directory.resolve("rename-source-" + upload), "new");
            Path target = Files.writeString(directory.resolve("rename-target-" + upload + ".sh"), "old");
            var sourceMode = java.nio.file.attribute.PosixFilePermissions.fromString("rwx------");
            var targetMode = java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----");
            Files.setPosixFilePermissions(source, sourceMode);
            Files.setPosixFilePermissions(target, targetMode);
            if (upload) files.uploadTree(source, target.toString(), () -> {}, (a, b) -> {},
                    ignored -> TransferConflictChoice.RENAME);
            else files.downloadTree(source.toString(), target, () -> {}, (a, b) -> {},
                    ignored -> TransferConflictChoice.RENAME);
            Path renamed = directory.resolve("rename-target-" + upload + " (1).sh");
            assertEquals("new", Files.readString(renamed));
            assertEquals(sourceMode, Files.getPosixFilePermissions(renamed));
            assertEquals("old", Files.readString(target));
            assertEquals(targetMode, Files.getPosixFilePermissions(target));
        }
    }

    private void transfer(boolean upload, int route, Path source, Path target) throws IOException {
        if (route == 0) {
            if (upload) files.upload(source, target.toString());
            else files.download(source.toString(), target);
        } else if (route == 1) {
            if (upload) files.uploadTree(source, target.toString(), () -> {}, (a, b) -> {},
                    ignored -> TransferConflictChoice.OVERWRITE);
            else files.downloadTree(source.toString(), target, () -> {}, (a, b) -> {},
                    ignored -> TransferConflictChoice.OVERWRITE);
        } else {
            try (var scheduler = new FileTransferScheduler(2)) {
                if (upload) files.uploadTree(source, target.toString(), () -> {}, (a, b) -> {},
                        ignored -> TransferConflictChoice.OVERWRITE, TransferActivityListener.NONE, scheduler);
                else files.downloadTree(source.toString(), target, () -> {}, (a, b) -> {},
                        ignored -> TransferConflictChoice.OVERWRITE, TransferActivityListener.NONE, scheduler);
            }
        }
    }

    @Test
    void deletesThroughFourIndependentChannelsWhileAllTransferSlotsAreOccupied() throws Exception {
        Path source = createTree("busy-upload");
        Path remote = directory.resolve("busy-target");
        Path deleting = createTree("parallel-delete");
        CountDownLatch deletingFour = new CountDownLatch(4);
        CountDownLatch releaseDeletes = new CountDownLatch(1);
        AtomicInteger activeDeletes = new AtomicInteger();
        AtomicInteger peakDeletes = new AtomicInteger();
        ((SftpSubsystemFactory) server.getSubsystemFactories().getFirst()).addSftpEventListener(new SftpEventListener() {
            @Override public void removing(ServerSession session, Path path, boolean isDirectory) throws IOException {
                if (!path.startsWith(deleting)) return;
                if (isDirectory) {
                    try (var children = Files.list(path)) { assertTrue(children.findAny().isEmpty()); }
                    return;
                }
                peakDeletes.accumulateAndGet(activeDeletes.incrementAndGet(), Math::max);
                deletingFour.countDown();
                try {
                    if (!releaseDeletes.await(10, TimeUnit.SECONDS)) throw new IOException("fixture delete timeout");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
            }
            @Override public void removed(ServerSession session, Path path, boolean isDirectory, Throwable failure) {
                if (path.startsWith(deleting) && !isDirectory) activeDeletes.decrementAndGet();
            }
        });
        try (var scheduler = new FileTransferScheduler(4); var executor = Executors.newFixedThreadPool(2)) {
            Future<?> upload = executor.submit(() -> {
                files.uploadTree(source, remote.toString(), () -> {}, (a, b) -> {},
                        ignored -> TransferConflictChoice.OVERWRITE, TransferActivityListener.NONE, scheduler);
                return null;
            });
            try {
                assertTrue(probe.entered.await(5, TimeUnit.SECONDS));
                Future<?> deletion = executor.submit(() -> { files.delete(deleting.toString()); return null; });
                assertTrue(deletingFour.await(5, TimeUnit.SECONDS));
                assertEquals(4, peakDeletes.get());
                assertFalse(upload.isDone());
                releaseDeletes.countDown();
                deletion.get(5, TimeUnit.SECONDS);
                assertFalse(Files.exists(deleting));
                assertFalse(upload.isDone());
            } finally {
                releaseDeletes.countDown();
                probe.release.countDown();
            }
            upload.get(10, TimeUnit.SECONDS);
            assertTreeEquals(source, remote);
        }
    }

    @Test
    void realDeletionPreservesFailedDirectoryAndNeverTraversesASymbolicLink() throws Exception {
        Path deleting = createTree("failed-delete");
        Path denied = deleting.resolve("file-0.bin");
        Path outside = directory.resolve("outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("safe.txt"), "must remain");
        boolean links = Files.getFileAttributeView(directory,
                java.nio.file.attribute.PosixFileAttributeView.class) != null;
        if (links) Files.createSymbolicLink(deleting.resolve("outside-link"), outside);
        ((SftpSubsystemFactory) server.getSubsystemFactories().getFirst()).addSftpEventListener(new SftpEventListener() {
            @Override public void removing(ServerSession session, Path path, boolean isDirectory) throws IOException {
                if (path.equals(denied)) throw new IOException("fixture permission denied");
            }
        });
        IOException failure = assertThrows(IOException.class, () -> files.delete(deleting.toString()));
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("file-0.bin"));
        assertTrue(Files.exists(denied));
        assertTrue(Files.isDirectory(deleting));
        assertFalse(Files.exists(deleting.resolve("nested")));
        assertEquals("must remain", Files.readString(outside.resolve("safe.txt")));
        if (links) assertFalse(Files.exists(deleting.resolve("outside-link"), java.nio.file.LinkOption.NOFOLLOW_LINKS));
        try (var children = Files.list(deleting)) { assertEquals(List.of(denied), children.toList()); }
    }

    @Test
    void uploadsAndDownloadsNestedFilesWithFourRealSftpChannelsAndExactProgress() throws Exception {
        Path source = createTree("source");
        Path remote = directory.resolve("remote");
        Path downloaded = directory.resolve("downloaded");
        try (var scheduler = new FileTransferScheduler(4);
                var executor = Executors.newSingleThreadExecutor()) {
            for (boolean upload : new boolean[]{true, false}) {
                probe.reset();
                AtomicLong total = new AtomicLong();
                AtomicLong copied = new AtomicLong();
                com.ynpe.fastremote.domain.transfer.TransferProgressListener progress = (size, done) -> {
                    assertTrue(size >= total.getAndSet(size));
                    assertTrue(done >= copied.getAndSet(done));
                    assertTrue(done <= size);
                };
                Future<?> operation = executor.submit(() -> {
                    if (upload) files.uploadTree(source, remote.toString(), () -> {}, progress,
                            ignored -> TransferConflictChoice.OVERWRITE, TransferActivityListener.NONE, scheduler);
                    else files.downloadTree(remote.toString(), downloaded, () -> {}, progress,
                            ignored -> TransferConflictChoice.OVERWRITE, TransferActivityListener.NONE, scheduler);
                    return null;
                });
                try {
                    assertTrue(probe.entered.await(5, TimeUnit.SECONDS), "four files must start before any finishes");
                    assertEquals(4, probe.active.get());
                } finally { probe.release.countDown(); }
                operation.get(10, TimeUnit.SECONDS);
                assertEquals(4, probe.peak.get());
                assertEquals(0, probe.active.get());
                assertEquals(8L * 256 * 1024, total.get());
                assertEquals(total.get(), copied.get());
                assertTreeEquals(source, upload ? remote : downloaded);
            }
        }
    }

    @Test
    void twoTreesAndSingleFilesShareFourSlotsAcrossUploadsAndDownloads() throws Exception {
        Path source = createTree("mixed-source");
        Path uploadTarget = directory.resolve("mixed-remote");
        Path downloadTarget = directory.resolve("mixed-local");
        try (var scheduler = new FileTransferScheduler(4);
                var executor = Executors.newFixedThreadPool(3)) {
            Future<?> upload = executor.submit(() -> {
                files.uploadTree(source, uploadTarget.toString(), () -> {}, (a, b) -> {},
                        ignored -> TransferConflictChoice.OVERWRITE, TransferActivityListener.NONE, scheduler);
                return null;
            });
            Future<?> download = executor.submit(() -> {
                files.downloadTree(source.toString(), downloadTarget, () -> {}, (a, b) -> {},
                        ignored -> TransferConflictChoice.OVERWRITE, TransferActivityListener.NONE, scheduler);
                return null;
            });
            Future<?> single = executor.submit(() -> {
                files.uploadTree(source.resolve("file-0.bin"), directory.resolve("single.bin").toString(),
                        () -> {}, (a, b) -> {}, ignored -> TransferConflictChoice.OVERWRITE,
                        TransferActivityListener.NONE, scheduler);
                return null;
            });
            try { assertTrue(probe.entered.await(5, TimeUnit.SECONDS)); }
            finally { probe.release.countDown(); }
            upload.get(10, TimeUnit.SECONDS);
            download.get(10, TimeUnit.SECONDS);
            single.get(10, TimeUnit.SECONDS);
            assertEquals(4, probe.peak.get());
            assertEquals(0, probe.active.get());
            assertTreeEquals(source, uploadTarget);
            assertTreeEquals(source, downloadTarget);
        }
    }

    @Test
    void preservesEmptyDirectoriesAndDirectorySkipAndRenameDecisions() throws Exception {
        probe.release.countDown();
        Path source = createTree("conflicts-source");
        Path target = directory.resolve("conflicts-target");
        Files.createDirectories(target);
        Files.writeString(target.resolve("keep.txt"), "original");
        try (var scheduler = new FileTransferScheduler(4)) {
            files.uploadTree(source, target.toString(), () -> {}, (a, b) -> {},
                    ignored -> TransferConflictChoice.SKIP, TransferActivityListener.NONE, scheduler);
            try (var children = Files.list(target)) { assertEquals(1, children.count()); }
            files.uploadTree(source, target.toString(), () -> {}, (a, b) -> {},
                    ignored -> TransferConflictChoice.RENAME, TransferActivityListener.NONE, scheduler);
            assertTreeEquals(source, directory.resolve("conflicts-target (1)"));
            assertEquals("original", Files.readString(target.resolve("keep.txt")));
            Path download = directory.resolve("conflicts-download");
            Files.writeString(download, "existing file");
            files.downloadTree(source.toString(), download, () -> {}, (a, b) -> {},
                    ignored -> TransferConflictChoice.OVERWRITE, TransferActivityListener.NONE, scheduler);
            assertTreeEquals(source, download);
        }
    }

    @RepeatedTest(5)
    void cancellationCleansPartialFilesAndDoesNotCommitIncompleteTargets() throws Exception {
        probe.release.countDown();
        Path source = createTree("cancel-source");
        for (boolean upload : new boolean[]{true, false}) {
            Path target = directory.resolve(upload ? "cancel-remote" : "cancel-local");
            AtomicInteger chunks = new AtomicInteger();
            IOException cancellation;
            try (var scheduler = new FileTransferScheduler(4)) {
                var progress = (com.ynpe.fastremote.domain.transfer.TransferProgressListener) (total, copied) -> {
                    if (copied > 0) chunks.incrementAndGet();
                };
                var control = (com.ynpe.fastremote.domain.transfer.TransferControl) () -> {
                    if (chunks.get() > 0) throw new java.io.InterruptedIOException("fixture cancellation");
                };
                cancellation = assertThrows(IOException.class, () -> {
                    if (upload) files.uploadTree(source, target.toString(), control, progress,
                            ignored -> TransferConflictChoice.OVERWRITE, TransferActivityListener.NONE, scheduler);
                    else files.downloadTree(source.toString(), target, control, progress,
                            ignored -> TransferConflictChoice.OVERWRITE, TransferActivityListener.NONE, scheduler);
                });
            }
            assertTrue(chunks.get() > 0);
            // Client channel.close(false) is asynchronous. In this in-process fixture the
            // Windows directory entry may remain delete-pending until the server releases
            // its last handle. Join server cleanup, not a sleep or a retry of the assertion:
            // a genuinely orphaned .part must still fail the checks below.
            awaitServerSubsystems();
            try (var paths = Files.walk(target)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    assertFalse(path.getFileName().toString().contains(".part"),
                            () -> path + " cancellation=" + cancellation + " suppressed="
                                    + java.util.Arrays.toString(cancellation.getSuppressed()) + " events=" + probe.cleanupEvents);
                    assertEquals(-1L, Files.mismatch(source.resolve(target.relativize(path)), path));
                }
            }
        }
    }

    private void awaitServerSubsystems() throws Exception {
        CompletableFuture.allOf(subsystemCompletions.toArray(CompletableFuture[]::new))
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void cancellationDuringRemoteOpenWaitsForResponseAndRemovesLateCreatedFile() throws Exception {
        probe.release.countDown();
        Path source = directory.resolve("opening-source.bin");
        Files.write(source, new byte[256 * 1024]);
        Path target = directory.resolve("opening-target.bin");
        var opening = new CountDownLatch(1);
        var allowOpen = new CountDownLatch(1);
        ((SftpSubsystemFactory) server.getSubsystemFactories().getFirst()).addSftpEventListener(new SftpEventListener() {
            @Override public void opening(ServerSession session, String handle, Handle file) throws IOException {
                if (!(file instanceof FileHandle)) return;
                opening.countDown();
                try {
                    if (!allowOpen.await(5, TimeUnit.SECONDS)) throw new IOException("fixture open timeout");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
            }
        });
        try (var scheduler = new FileTransferScheduler(1); var scanner = Executors.newSingleThreadExecutor()) {
            Future<?> result = scanner.submit(() -> {
                files.uploadTree(source, target.toString(), () -> {}, (a, b) -> {},
                        ignored -> TransferConflictChoice.OVERWRITE, TransferActivityListener.NONE, scheduler);
                return null;
            });
            try {
                assertTrue(opening.await(5, TimeUnit.SECONDS));
                scheduler.close();
                assertThrows(TimeoutException.class, () -> result.get(100, TimeUnit.MILLISECONDS));
            } finally { allowOpen.countDown(); }
            assertInstanceOf(IOException.class,
                    assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS)).getCause());
        }
        awaitServerSubsystems();
        assertFalse(Files.exists(target));
        try (var entries = Files.list(directory)) {
            assertFalse(entries.anyMatch(path -> path.toString().endsWith(".part")));
        }
    }

    @Test
    void retriesFailedUploadCleanupAfterWorkerChannelCloses() throws Exception {
        probe.release.countDown();
        Path source = directory.resolve("cleanup-source.bin");
        Files.write(source, new byte[256 * 1024]);
        Path target = directory.resolve("cleanup-target.bin");
        AtomicInteger removals = new AtomicInteger();
        AtomicInteger chunks = new AtomicInteger();
        ((SftpSubsystemFactory) server.getSubsystemFactories().getFirst()).addSftpEventListener(new SftpEventListener() {
            @Override public void removing(ServerSession session, Path path, boolean isDirectory) throws IOException {
                if (path.toString().contains(".part") && removals.getAndIncrement() == 0) {
                    throw new IOException("fixture handle still closing");
                }
            }
        });
        try (var scheduler = new FileTransferScheduler(1)) {
            assertThrows(IOException.class, () -> files.uploadTree(source, target.toString(), () -> {
                if (Thread.currentThread().isVirtual() && chunks.get() > 0) {
                    throw new java.io.InterruptedIOException("fixture cancellation");
                }
            }, (total, copied) -> { if (copied > 0) chunks.incrementAndGet(); },
                    ignored -> TransferConflictChoice.OVERWRITE, TransferActivityListener.NONE, scheduler));
        }
        assertEquals(2, removals.get());
        assertFalse(Files.exists(target));
        try (var paths = Files.list(directory)) {
            assertFalse(paths.anyMatch(path -> path.toString().endsWith(".part")));
        }
    }

    private Path createTree(String name) throws IOException {
        Path root = directory.resolve(name);
        Files.createDirectories(root.resolve("nested/empty"));
        for (int index = 0; index < 8; index++) {
            byte[] bytes = new byte[256 * 1024];
            java.util.Arrays.fill(bytes, (byte) (index + 1));
            Files.write(root.resolve((index < 4 ? "" : "nested/") + "file-" + index + ".bin"), bytes);
        }
        return root;
    }

    private static void assertTreeEquals(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path other = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) assertTrue(Files.isDirectory(other), other.toString());
                else assertEquals(-1L, Files.mismatch(path, other), other.toString());
            }
        }
        try (var paths = Files.walk(target)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }

    private static final class FileProbe implements SftpEventListener {
        private final java.util.Queue<String> cleanupEvents = new ConcurrentLinkedQueue<>();
        @Override public void removed(ServerSession session, Path path, boolean directory, Throwable failure) {
            cleanupEvents.add(path.getFileName() + ":" + (failure == null ? "removed" : failure));
        }
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger peak = new AtomicInteger();
        private final Set<Handle> handles = ConcurrentHashMap.newKeySet();
        private volatile CountDownLatch entered = new CountDownLatch(4);
        private volatile CountDownLatch release = new CountDownLatch(1);

        void reset() {
            assertEquals(0, active.get());
            peak.set(0);
            entered = new CountDownLatch(4);
            release = new CountDownLatch(1);
        }

        @Override public void opening(ServerSession session, String handle, Handle file) throws IOException {
            if (!(file instanceof FileHandle)) return;
            handles.add(file);
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("fixture open timeout");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(interrupted);
            }
        }

        @Override public void closed(ServerSession session, String handle, Handle file, Throwable failure) {
            if (handles.remove(file)) active.decrementAndGet();
        }
    }
}
