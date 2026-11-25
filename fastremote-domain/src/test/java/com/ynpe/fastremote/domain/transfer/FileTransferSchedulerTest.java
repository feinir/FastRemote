// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
class FileTransferSchedulerTest {
    @Test
    void cooperativeCancellationWaitsForRequestThenCleansUpWithoutInterruptingIt() throws Exception {
        try (var scheduler = new FileTransferScheduler(1); var scanner = Executors.newSingleThreadExecutor()) {
            var requestStarted = new CountDownLatch(1);
            var response = new CountDownLatch(1);
            var cleaned = new CountDownLatch(1);
            var interrupts = new AtomicInteger();
            var continued = new AtomicInteger();
            Future<?> result = scanner.submit(() -> {
                try (var group = scheduler.openCooperativeGroup(() -> {})) {
                    group.submit(control -> {
                        requestStarted.countDown();
                        try {
                            try { await(response); }
                            catch (InterruptedIOException interrupted) { interrupts.incrementAndGet(); throw interrupted; }
                            control.checkpoint();
                            continued.incrementAndGet();
                        } finally { cleaned.countDown(); }
                    });
                    group.finish();
                }
                return null;
            });
            try {
                assertTrue(requestStarted.await(5, TimeUnit.SECONDS));
                scheduler.close();
                assertFalse(cleaned.await(100, TimeUnit.MILLISECONDS));
                assertFalse(result.isDone());
            } finally { response.countDown(); }
            assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
            assertEquals(0, interrupts.get());
            assertEquals(0, continued.get());
            assertEquals(0, cleaned.getCount());
        }
    }

    @Test
    void oneTreeFillsFourSlotsAndMultipleTreesShareTheSameLimit() throws Exception {
        for (int trees : new int[]{1, 2}) {
            try (FileTransferScheduler scheduler = new FileTransferScheduler(4);
                    ExecutorService scanners = Executors.newFixedThreadPool(2)) {
                CountDownLatch entered = new CountDownLatch(4);
                CountDownLatch release = new CountDownLatch(1);
                AtomicInteger active = new AtomicInteger();
                AtomicInteger peak = new AtomicInteger();
                AtomicInteger completed = new AtomicInteger();
                var futures = new java.util.ArrayList<Future<?>>();
                for (int tree = 0; tree < trees; tree++) futures.add(scanners.submit(() -> {
                    try (var group = scheduler.openGroup(() -> {})) {
                        for (int file = 0; file < 12; file++) group.submit(control -> {
                            int running = active.incrementAndGet();
                            peak.accumulateAndGet(running, Math::max);
                            entered.countDown();
                            try { await(release); control.checkpoint(); completed.incrementAndGet(); }
                            finally { active.decrementAndGet(); }
                        });
                        group.finish();
                    }
                    return null;
                }));
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    assertEquals(4, active.get());
                } finally { release.countDown(); }
                for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
                assertEquals(4, peak.get());
                assertEquals(trees * 12, completed.get());
                assertEquals(0, active.get());
            }
        }
    }

    @Test
    void scannerStopsAtBoundedPendingCapacityUntilAFileFinishes() throws Exception {
        try (FileTransferScheduler scheduler = new FileTransferScheduler(1);
                ExecutorService scanner = Executors.newSingleThreadExecutor()) {
            CountDownLatch capacity = new CountDownLatch(1);
            CountDownLatch beyondCapacity = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<?> future = scanner.submit(() -> {
                try (var group = scheduler.openGroup(() -> {})) {
                    for (int index = 0; index < 17; index++) {
                        group.submit(control -> await(release));
                        if (index == 15) capacity.countDown();
                        if (index == 16) beyondCapacity.countDown();
                    }
                    group.finish();
                }
                return null;
            });
            try {
                assertTrue(capacity.await(5, TimeUnit.SECONDS));
                assertFalse(beyondCapacity.await(150, TimeUnit.MILLISECONDS));
            } finally { release.countDown(); }
            future.get(5, TimeUnit.SECONDS);
            assertEquals(0, beyondCapacity.getCount());
        }
    }

    @Test
    void failureStopsSiblingsAndWaitsForTheirCleanupBeforeReturning() throws Exception {
        try (FileTransferScheduler scheduler = new FileTransferScheduler(2);
                ExecutorService scanner = Executors.newSingleThreadExecutor()) {
            CountDownLatch entered = new CountDownLatch(2);
            AtomicInteger cleaned = new AtomicInteger();
            Future<?> future = scanner.submit(() -> {
                try (var group = scheduler.openGroup(() -> {})) {
                    group.submit(control -> {
                        entered.countDown();
                        try { await(entered); throw new IOException("fixture failure"); }
                        finally { cleaned.incrementAndGet(); }
                    });
                    group.submit(control -> {
                        entered.countDown();
                        try { await(new CountDownLatch(1)); }
                        finally { cleaned.incrementAndGet(); }
                    });
                    group.finish();
                }
                return null;
            });
            ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertEquals("fixture failure", failure.getCause().getMessage());
            assertEquals(2, cleaned.get());
            try (var next = scheduler.openGroup(() -> {})) {
                next.submit(control -> {});
                next.finish();
            }
        }
    }

    @Test
    void closingSchedulerCancelsRunningAndQueuedFilesAndIsIdempotent() throws Exception {
        try (FileTransferScheduler scheduler = new FileTransferScheduler(1);
                ExecutorService scanner = Executors.newSingleThreadExecutor()) {
            CountDownLatch entered = new CountDownLatch(1);
            AtomicInteger started = new AtomicInteger();
            AtomicInteger cleaned = new AtomicInteger();
            Future<?> future = scanner.submit(() -> {
                try (var group = scheduler.openGroup(() -> {})) {
                    for (int index = 0; index < 30; index++) group.submit(control -> {
                        started.incrementAndGet();
                        entered.countDown();
                        try { await(new CountDownLatch(1)); }
                        finally { cleaned.incrementAndGet(); }
                    });
                    group.finish();
                }
                return null;
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            scheduler.close();
            scheduler.close();
            assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertEquals(1, started.get());
            assertEquals(1, cleaned.get());
            assertThrows(IOException.class, () -> scheduler.openGroup(() -> {}));
        }
    }

    @Test
    void repeatedCancellationDoesNotInterruptWorkerCleanup() throws Exception {
        try (FileTransferScheduler scheduler = new FileTransferScheduler(1);
                ExecutorService scanner = Executors.newSingleThreadExecutor()) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch cleaning = new CountDownLatch(1);
            CountDownLatch releaseCleanup = new CountDownLatch(1);
            AtomicInteger cleanupInterrupts = new AtomicInteger();
            Future<?> future = scanner.submit(() -> {
                try (var group = scheduler.openGroup(() -> {})) {
                    group.submit(control -> {
                        entered.countDown();
                        try { await(new CountDownLatch(1)); }
                        finally {
                            Thread.interrupted();
                            cleaning.countDown();
                            try { await(releaseCleanup); }
                            catch (InterruptedIOException interrupted) { cleanupInterrupts.incrementAndGet(); }
                        }
                    });
                    group.finish();
                }
                return null;
            });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                scheduler.close();
                assertTrue(cleaning.await(5, TimeUnit.SECONDS));
                scheduler.close();
            } finally { releaseCleanup.countDown(); }
            assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertEquals(0, cleanupInterrupts.get());
        }
    }

    @Test
    void pausedFileReleasesItsSlotForAnotherTreeAndReacquiresOnResume() throws Exception {
        try (FileTransferScheduler scheduler = new FileTransferScheduler(1);
                ExecutorService scanners = Executors.newFixedThreadPool(2)) {
            PauseGate gate = new PauseGate();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch nextCheckpoint = new CountDownLatch(1);
            CountDownLatch completed = new CountDownLatch(1);
            Future<?> first = scanners.submit(() -> {
                try (var group = scheduler.openGroup(gate)) {
                    group.submit(control -> {
                        entered.countDown();
                        await(nextCheckpoint);
                        control.checkpoint();
                        completed.countDown();
                    });
                    group.finish();
                }
                return null;
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            gate.pause();
            nextCheckpoint.countDown();
            try {
                Future<?> second = scanners.submit(() -> {
                    try (var group = scheduler.openGroup(() -> {})) {
                        group.submit(control -> {});
                        group.finish();
                    }
                    return null;
                });
                second.get(5, TimeUnit.SECONDS);
                assertEquals(1, completed.getCount());
            } finally { gate.resume(); }
            first.get(5, TimeUnit.SECONDS);
            assertEquals(0, completed.getCount());
        }
    }

    @Test
    void waitsForActualWorkerCleanupWhenScannerIsInterrupted() throws Exception {
        try (FileTransferScheduler scheduler = new FileTransferScheduler(1)) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch cleaned = new CountDownLatch(1);
            CountDownLatch returned = new CountDownLatch(1);
            Thread scanner = Thread.ofPlatform().start(() -> {
                try (var group = scheduler.openGroup(() -> {})) {
                    group.submit(control -> {
                        entered.countDown();
                        try { await(new CountDownLatch(1)); }
                        finally { cleaned.countDown(); }
                    });
                    group.finish();
                } catch (IOException expected) {
                    if (cleaned.getCount() == 0) returned.countDown();
                }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            scanner.interrupt();
            assertTrue(returned.await(5, TimeUnit.SECONDS));
            scanner.join(1000);
            assertFalse(scanner.isAlive());
        }
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IOException("fixture timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("fixture interrupted");
        }
    }

    private static final class PauseGate implements TransferControl {
        private boolean paused;
        synchronized void pause() { paused = true; }
        synchronized void resume() { paused = false; notifyAll(); }
        @Override public void checkpoint() throws IOException { checkpoint(() -> {}); }
        @Override public void checkpoint(Runnable beforePause) throws IOException {
            while (true) {
                synchronized (this) { if (!paused) return; }
                beforePause.run();
                synchronized (this) {
                    if (!paused) continue;
                    try { wait(); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException("pause interrupted");
                    }
                }
            }
        }
    }
}
