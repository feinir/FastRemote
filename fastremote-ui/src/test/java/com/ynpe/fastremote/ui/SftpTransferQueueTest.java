// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.transfer.TransferConflict;
import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;
import com.ynpe.fastremote.domain.transfer.TransferConflictResolver;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SftpTransferQueueTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    @Test
    void pausedTreeYieldsFileSlotsToNextJobAndResumesWithTheSameScheduler() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch reachCheckpoint = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);
        CountDownLatch firstFinished = new CountDownLatch(1);
        var schedulers = java.util.concurrent.ConcurrentHashMap.newKeySet();
        try (ExecutorService scanners = Executors.newFixedThreadPool(2)) {
            RemoteFilesHandle files = (RemoteFilesHandle) Proxy.newProxyInstance(
                    RemoteFilesHandle.class.getClassLoader(), new Class<?>[]{RemoteFilesHandle.class},
                    (proxy, method, arguments) -> {
                        assertEquals("uploadTree", method.getName());
                        var control = (com.ynpe.fastremote.domain.transfer.TransferControl) arguments[2];
                        var scheduler = (com.ynpe.fastremote.domain.transfer.FileTransferScheduler) arguments[6];
                        schedulers.add(scheduler);
                        boolean first = ((Path) arguments[0]).getFileName().toString().equals("first");
                        return CompletableFuture.runAsync(() -> {
                            try (var group = scheduler.openGroup(control)) {
                                group.submit(worker -> {
                                    if (first) {
                                        firstStarted.countDown();
                                        try {
                                            if (!reachCheckpoint.await(5, TimeUnit.SECONDS)) {
                                                throw new IOException("checkpoint timeout");
                                            }
                                        } catch (InterruptedException interrupted) {
                                            Thread.currentThread().interrupt();
                                            throw new IOException(interrupted);
                                        }
                                        worker.checkpoint();
                                        firstFinished.countDown();
                                    } else secondFinished.countDown();
                                });
                                group.finish();
                            } catch (IOException failure) {
                                throw new java.util.concurrent.CompletionException(failure);
                            }
                        }, scanners);
                    });
            try (SftpTransferQueue queue = new SftpTransferQueue(files, () -> 1,
                    ignored -> new SftpConflictDecision(TransferConflictChoice.OVERWRITE, false))) {
                queue.upload(List.of(localFile("first"), localFile("second")), "/target");
                assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
                SftpTransferJob first = queue.jobs().get(0);
                queue.pause(List.of(first));
                reachCheckpoint.countDown();
                assertTrue(secondFinished.await(5, TimeUnit.SECONDS));
                assertEquals(SftpTransferState.PAUSED, first.state());
                assertEquals(1, firstFinished.getCount());
                queue.resume(List.of(first));
                assertTrue(firstFinished.await(5, TimeUnit.SECONDS));
                assertEquals(1, schedulers.size());
            }
        }
    }

    @Test
    void retriesCompletedAndFailedSelectionsAsFreshJobsWithoutReplacingHistory() throws Exception {
        AtomicInteger uploadCalls = new AtomicInteger();
        RemoteFilesHandle files = (RemoteFilesHandle) Proxy.newProxyInstance(
                RemoteFilesHandle.class.getClassLoader(), new Class<?>[]{RemoteFilesHandle.class},
                (proxy, method, arguments) -> {
                    if (!method.getName().equals("uploadTree")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    uploadCalls.incrementAndGet();
                    Path source = (Path) arguments[0];
                    return source.getFileName().toString().startsWith("failed")
                            ? CompletableFuture.failedFuture(new IOException("Read Timeout"))
                            : CompletableFuture.completedFuture(null);
                });
        SftpTransferQueue queue = new SftpTransferQueue(files, () -> 2,
                ignored -> new SftpConflictDecision(TransferConflictChoice.OVERWRITE, false));
        queue.upload(List.of(
                localFile("completed.txt"),
                localFile("failed.txt")), "/target");
        drainJavaFx();

        List<SftpTransferJob> originals = List.copyOf(queue.jobs());
        assertEquals(List.of(SftpTransferState.COMPLETED, SftpTransferState.FAILED),
                originals.stream().map(SftpTransferJob::state).toList());

        queue.retry(originals);
        drainJavaFx();

        assertEquals(4, uploadCalls.get());
        assertEquals(4, queue.jobs().size());
        assertSame(originals.get(0), queue.jobs().get(0));
        assertSame(originals.get(1), queue.jobs().get(1));
        assertNotSame(originals.get(0), queue.jobs().get(2));
        assertNotSame(originals.get(1), queue.jobs().get(3));
        assertEquals(originals.stream().map(SftpTransferJob::source).toList(),
                queue.jobs().subList(2, 4).stream().map(SftpTransferJob::source).toList());
        assertEquals(List.of(SftpTransferState.COMPLETED, SftpTransferState.FAILED),
                originals.stream().map(SftpTransferJob::state).toList());
        assertFalse(queue.hasUnfinishedTransfers());
    }

    @Test
    void reportsRunningTransferAsUnfinishedUntilItCompletes() throws Exception {
        CompletableFuture<Void> transfer = new CompletableFuture<>();
        RemoteFilesHandle files = (RemoteFilesHandle) Proxy.newProxyInstance(
                RemoteFilesHandle.class.getClassLoader(), new Class<?>[]{RemoteFilesHandle.class},
                (proxy, method, arguments) -> {
                    if (!method.getName().equals("uploadTree")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    return transfer;
                });
        SftpTransferQueue queue = new SftpTransferQueue(files, () -> 1,
                ignored -> new SftpConflictDecision(TransferConflictChoice.OVERWRITE, false));

        queue.upload(List.of(localFile("running.txt")), "/target");
        assertTrue(queue.hasUnfinishedTransfers());

        transfer.complete(null);
        drainJavaFx();
        assertFalse(queue.hasUnfinishedTransfers());
    }

    @Test
    void closingQueueCancelsTheRunningOperationHandle() throws Exception {
        AtomicBoolean operationCancelled = new AtomicBoolean();
        CompletableFuture<Void> transfer = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                operationCancelled.set(mayInterruptIfRunning);
                return super.cancel(mayInterruptIfRunning);
            }
        };
        RemoteFilesHandle files = (RemoteFilesHandle) Proxy.newProxyInstance(
                RemoteFilesHandle.class.getClassLoader(), new Class<?>[]{RemoteFilesHandle.class},
                (proxy, method, arguments) -> {
                    if (!method.getName().equals("uploadTree")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    return transfer;
                });
        SftpTransferQueue queue = new SftpTransferQueue(files, () -> 1,
                ignored -> new SftpConflictDecision(TransferConflictChoice.OVERWRITE, false));

        queue.upload(List.of(localFile("running.txt")), "/target");
        queue.close();

        assertTrue(operationCancelled.get());
        assertTrue(transfer.isCancelled());
        assertEquals(SftpTransferState.CANCELLED, queue.jobs().getFirst().state());
    }

    @Test
    void appliesUploadConflictChoiceToEveryJobInTheSameBatch() throws Exception {
        assertBatchConflictChoice(true, TransferConflictChoice.OVERWRITE);
    }

    @Test
    void appliesDownloadConflictChoiceToEveryJobInTheSameBatch() throws Exception {
        assertBatchConflictChoice(false, TransferConflictChoice.RENAME);
    }

    @Test
    void serializesConcurrentConflictsBeforeRememberingTheBatchChoice() throws Exception {
        AtomicInteger prompts = new AtomicInteger();
        List<TransferConflictChoice> choices = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch resolveTogether = new CountDownLatch(1);
        CountDownLatch resolved = new CountDownLatch(2);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            RemoteFilesHandle files = (RemoteFilesHandle) Proxy.newProxyInstance(
                    RemoteFilesHandle.class.getClassLoader(), new Class<?>[]{RemoteFilesHandle.class},
                    (proxy, method, arguments) -> {
                        if (!method.getName().equals("uploadTree")) {
                            throw new UnsupportedOperationException(method.getName());
                        }
                        TransferConflictResolver resolver = (TransferConflictResolver) arguments[4];
                        return CompletableFuture.runAsync(() -> {
                            workersReady.countDown();
                            await(resolveTogether);
                            choices.add(resolver.resolve(new TransferConflict("source", "target", false)));
                            resolved.countDown();
                        }, workers);
                    });
            SftpTransferQueue queue = new SftpTransferQueue(files, () -> 2, ignored -> {
                prompts.incrementAndGet();
                return new SftpConflictDecision(TransferConflictChoice.OVERWRITE, true);
            });

            queue.upload(List.of(localFile("first.txt"), localFile("second.txt")), "/target");
            assertTrue(workersReady.await(5, TimeUnit.SECONDS));
            resolveTogether.countDown();
            assertTrue(resolved.await(5, TimeUnit.SECONDS));
            drainJavaFx();

            assertEquals(1, prompts.get());
            assertEquals(2, choices.size());
            assertTrue(choices.stream().allMatch(choice -> choice == TransferConflictChoice.OVERWRITE));
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void asksAgainWhenTheChoiceIsNotAppliedToTheBatch() throws Exception {
        AtomicInteger prompts = new AtomicInteger();
        RemoteFilesHandle files = (RemoteFilesHandle) Proxy.newProxyInstance(
                RemoteFilesHandle.class.getClassLoader(), new Class<?>[]{RemoteFilesHandle.class},
                (proxy, method, arguments) -> {
                    if (!method.getName().equals("uploadTree")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    TransferConflictResolver resolver = (TransferConflictResolver) arguments[4];
                    resolver.resolve(new TransferConflict("source", "target", false));
                    return CompletableFuture.completedFuture(null);
                });
        SftpTransferQueue queue = new SftpTransferQueue(files, () -> 2, ignored -> {
            prompts.incrementAndGet();
            return new SftpConflictDecision(TransferConflictChoice.SKIP, false);
        });

        queue.upload(List.of(localFile("first.txt"), localFile("second.txt")), "/target");
        drainJavaFx();

        assertEquals(2, prompts.get());
    }

    private static void assertBatchConflictChoice(boolean upload, TransferConflictChoice batchChoice) throws Exception {
        AtomicInteger prompts = new AtomicInteger();
        List<TransferConflictChoice> choices = java.util.Collections.synchronizedList(new ArrayList<>());
        RemoteFilesHandle files = (RemoteFilesHandle) Proxy.newProxyInstance(
                RemoteFilesHandle.class.getClassLoader(), new Class<?>[]{RemoteFilesHandle.class},
                (proxy, method, arguments) -> {
                    if (!method.getName().equals(upload ? "uploadTree" : "downloadTree")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    TransferConflictResolver resolver = (TransferConflictResolver) arguments[4];
                    choices.add(resolver.resolve(new TransferConflict("source", "target", false)));
                    return CompletableFuture.completedFuture(null);
                });
        SftpTransferQueue queue = new SftpTransferQueue(files, () -> 2, ignored -> {
            prompts.incrementAndGet();
            return new SftpConflictDecision(batchChoice, true);
        });

        if (upload) {
            queue.upload(List.of(localFile("first.txt"), localFile("second.txt")), "/target");
        } else {
            queue.download(List.of(remoteFile("first.txt"), remoteFile("second.txt")), Path.of("target"));
        }
        drainJavaFx();

        assertEquals(1, prompts.get());
        assertEquals(List.of(batchChoice, batchChoice), choices);
        assertEquals(List.of(SftpTransferState.COMPLETED, SftpTransferState.COMPLETED),
                queue.jobs().stream().map(SftpTransferJob::state).toList());
    }

    private static LocalFileEntry localFile(String name) {
        return new LocalFileEntry(name, Path.of(name), false, false, 1L, Optional.<Instant>empty());
    }

    private static com.ynpe.fastremote.domain.transfer.RemoteFileEntry remoteFile(String name) {
        return new com.ynpe.fastremote.domain.transfer.RemoteFileEntry(
                name, "/source/" + name, false, false, 1L, Optional.<Instant>empty());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for transfer test");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void drainJavaFx() throws InterruptedException {
        CountDownLatch drained = new CountDownLatch(1);
        Platform.runLater(drained::countDown);
        assertTrue(drained.await(5, TimeUnit.SECONDS));
    }
}
