// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import com.ynpe.fastremote.domain.transfer.TransferActivityListener;
import com.ynpe.fastremote.transport.ssh.MinaRemoteFileService.RemoteDeleteAccess;
import com.ynpe.fastremote.transport.ssh.MinaRemoteFileService.RemoteDeleteAttributes;
import com.ynpe.fastremote.transport.ssh.MinaRemoteFileService.RemoteDeleteChild;

@Timeout(20)
class SftpDeleteQueueTest {
    @Test
    void sharesFourWorkersAcrossSubdirectoriesAndDeletesParentsLast() throws Exception {
        Fixture fixture = new Fixture();
        fixture.directory("/root");
        for (String dir : List.of("a", "b", "empty")) fixture.directory("/root/" + dir);
        for (int index = 0; index < 12; index++) fixture.file("/root/" + (index < 2 ? "a/" : "b/") + index);
        try (var queue = new SftpDeleteQueue(fixture::open); var scanner = Executors.newSingleThreadExecutor()) {
            Future<?> operation = scanner.submit(() -> { queue.delete("/root", TransferActivityListener.NONE); return null; });
            try {
                assertTrue(fixture.entered.await(5, TimeUnit.SECONDS));
                assertEquals(4, fixture.active.get());
                assertTrue(fixture.nodes.containsKey("/root/a"));
                assertTrue(fixture.nodes.containsKey("/root/b"));
            } finally { fixture.release.countDown(); }
            operation.get(5, TimeUnit.SECONDS);
            assertEquals(4, fixture.peak.get());
            assertTrue(fixture.opened.get() <= 4);
            assertEquals(fixture.opened.get(), fixture.closed.get(), "idle deletion channels must be released");
            assertTrue(fixture.nodes.isEmpty());
            assertEquals("/root", fixture.removedDirectories.getLast());
            assertEquals(1, fixture.listCounts.get("/root").get());
            assertEquals(1, fixture.listCounts.get("/root/a").get());
            assertEquals(1, fixture.listCounts.get("/root/b").get());
        }
        assertEquals(fixture.opened.get(), fixture.closed.get());
    }

    @Test
    void usesTheConfiguredTransferParallelismForOneRemoteService() throws Exception {
        Fixture fixture = new Fixture(3);
        fixture.directory("/root");
        for (int index = 0; index < 12; index++) fixture.file("/root/" + index);
        try (var queue = new SftpDeleteQueue(fixture::open); var scanner = Executors.newSingleThreadExecutor()) {
            Future<?> operation = scanner.submit(() -> {
                queue.delete("/root", TransferActivityListener.NONE, 3);
                return null;
            });
            try {
                assertTrue(fixture.entered.await(5, TimeUnit.SECONDS));
                assertEquals(3, fixture.active.get());
            } finally { fixture.release.countDown(); }
            operation.get(5, TimeUnit.SECONDS);
            assertEquals(3, fixture.peak.get());
            assertTrue(fixture.opened.get() <= 3);
            assertTrue(fixture.nodes.isEmpty());
        }
    }

    @Test
    void multipleRootsShareTheSameFourChannels() throws Exception {
        Fixture fixture = new Fixture();
        for (String root : List.of("/a", "/b")) {
            fixture.directory(root);
            for (int index = 0; index < 20; index++) fixture.file(root + "/" + index);
        }
        try (var queue = new SftpDeleteQueue(fixture::open); var scanners = Executors.newFixedThreadPool(2)) {
            Future<?> first = scanners.submit(() -> { queue.delete("/a", TransferActivityListener.NONE); return null; });
            Future<?> second = scanners.submit(() -> { queue.delete("/b", TransferActivityListener.NONE); return null; });
            try { assertTrue(fixture.entered.await(5, TimeUnit.SECONDS)); }
            finally { fixture.release.countDown(); }
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(4, fixture.peak.get());
            assertTrue(fixture.nodes.isEmpty());
        }
    }

    @Test
    void continuesAfterFailureAndMissingFilesButPreservesFailedAncestorsAndLinks() throws Exception {
        Fixture fixture = new Fixture();
        fixture.release.countDown();
        fixture.directory("/root");
        fixture.directory("/root/fails");
        fixture.file("/root/fails/denied");
        fixture.file("/root/fails/gone");
        fixture.directory("/root/success");
        fixture.file("/root/success/ok");
        fixture.nodes.put("/root/link", new RemoteDeleteAttributes(true, true));
        fixture.directory("/outside");
        fixture.file("/outside/safe");
        fixture.denied.add("/root/fails/denied");
        fixture.missingOnRemove.add("/root/fails/gone");
        try (var queue = new SftpDeleteQueue(fixture::open)) {
            IOException failure = assertThrows(IOException.class, () -> queue.delete("/root", TransferActivityListener.NONE));
            assertEquals(1, failure.getSuppressed().length);
            assertTrue(failure.getSuppressed()[0].getMessage().contains("/root/fails/denied"));
            assertTrue(fixture.nodes.containsKey("/root"));
            assertTrue(fixture.nodes.containsKey("/root/fails"));
            assertFalse(fixture.nodes.containsKey("/root/success"));
            assertFalse(fixture.nodes.containsKey("/root/link"));
            assertFalse(fixture.listCounts.containsKey("/root/link"));
            assertTrue(fixture.nodes.containsKey("/outside/safe"));
            queue.delete("/already-gone", TransferActivityListener.NONE);
        }
    }

    @Test
    void closeCancelsQueuedWorkAndDrainsWorkersWithoutRemovingParents() throws Exception {
        Fixture fixture = new Fixture();
        fixture.directory("/root");
        for (int index = 0; index < 100; index++) fixture.file("/root/" + index);
        try (var queue = new SftpDeleteQueue(fixture::open); var scanner = Executors.newSingleThreadExecutor()) {
            Future<?> operation = scanner.submit(() -> { queue.delete("/root", TransferActivityListener.NONE); return null; });
            assertTrue(fixture.entered.await(5, TimeUnit.SECONDS));
            queue.close();
            queue.close();
            assertThrows(ExecutionException.class, () -> operation.get(5, TimeUnit.SECONDS));
            assertEquals(0, fixture.active.get());
            assertEquals(4, fixture.removeAttempts.get());
            assertTrue(fixture.nodes.containsKey("/root"));
            assertEquals(fixture.opened.get(), fixture.closed.get());
            assertThrows(IOException.class, () -> queue.delete("/root", TransferActivityListener.NONE));
        }
    }

    @Test
    void boundsFailureDetailsWhileStillAttemptingAllSiblings() throws Exception {
        Fixture fixture = new Fixture();
        fixture.release.countDown();
        fixture.directory("/root");
        for (int index = 0; index < 200; index++) {
            fixture.file("/root/" + index);
            fixture.denied.add("/root/" + index);
        }
        try (var queue = new SftpDeleteQueue(fixture::open)) {
            IOException failure = assertThrows(IOException.class, () -> queue.delete("/root", TransferActivityListener.NONE));
            assertEquals(200, fixture.removeAttempts.get());
            assertEquals(101, failure.getSuppressed().length);
            assertTrue(failure.getMessage().contains("200 failures"));
            assertTrue(fixture.nodes.containsKey("/root"));
        }
    }

    @Test
    void rejectsUnsafeListingBeforeDeletingAnythingFromThatDirectory() throws Exception {
        Fixture fixture = new Fixture();
        fixture.directory("/root");
        fixture.unsafeListing = true;
        try (var queue = new SftpDeleteQueue(fixture::open)) {
            assertThrows(IOException.class, () -> queue.delete("/root", TransferActivityListener.NONE));
            assertEquals(0, fixture.removeAttempts.get());
            assertTrue(fixture.nodes.containsKey("/root"));
        }
    }

    private static final class Fixture {
        private final Map<String, RemoteDeleteAttributes> nodes = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> listCounts = new ConcurrentHashMap<>();
        private final Set<String> denied = ConcurrentHashMap.newKeySet();
        private final Set<String> missingOnRemove = ConcurrentHashMap.newKeySet();
        private final List<String> removedDirectories = new CopyOnWriteArrayList<>();
        private final CountDownLatch entered;
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger peak = new AtomicInteger();
        private final AtomicInteger removeAttempts = new AtomicInteger();
        private final AtomicInteger opened = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();
        private boolean unsafeListing;
        Fixture() { this(4); }
        Fixture(int requiredWorkers) { entered = new CountDownLatch(requiredWorkers); }
        void directory(String path) { nodes.put(path, new RemoteDeleteAttributes(true, false)); }
        void file(String path) { nodes.put(path, new RemoteDeleteAttributes(false, false)); }

        RemoteDeleteAccess open() {
            opened.incrementAndGet();
            return new RemoteDeleteAccess() {
                private volatile boolean open = true;
                @Override public RemoteDeleteAttributes attributes(String path) throws IOException {
                    var value = nodes.get(path);
                    if (value == null) throw new SftpException(SftpConstants.SSH_FX_NO_SUCH_FILE, "missing");
                    return value;
                }
                @Override public List<RemoteDeleteChild> children(String path) {
                    listCounts.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
                    if (unsafeListing) return List.of(new RemoteDeleteChild("../outside", new RemoteDeleteAttributes(false, false)));
                    return nodes.entrySet().stream().filter(entry -> entry.getKey().startsWith(path + "/")
                            && !entry.getKey().substring(path.length() + 1).contains("/"))
                            .sorted(Map.Entry.comparingByKey()).map(entry -> new RemoteDeleteChild(
                                    entry.getKey().substring(path.length() + 1), entry.getValue())).toList();
                }
                @Override public void remove(String path) throws IOException {
                    removeAttempts.incrementAndGet();
                    peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                    entered.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("fixture timeout");
                        if (denied.contains(path)) throw new IOException("fixture permission denied");
                        nodes.remove(path);
                        if (missingOnRemove.contains(path)) throw new SftpException(SftpConstants.SSH_FX_NO_SUCH_FILE, "missing");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException("fixture cancelled");
                    } finally { active.decrementAndGet(); }
                }
                @Override public void removeDirectory(String path) {
                    assertTrue(nodes.keySet().stream().noneMatch(child -> child.startsWith(path + "/")), path);
                    nodes.remove(path);
                    removedDirectories.add(path);
                }
                @Override public boolean isMissing(IOException failure) { return failure instanceof SftpException; }
                @Override public boolean isOpen() { return open; }
                @Override public synchronized void close() { if (open) { open = false; closed.incrementAndGet(); } }
            };
        }
    }
}
