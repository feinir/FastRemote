// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.transfer;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * File slots shared by the trees in one transfer queue. At most 32 scanners and 16 outstanding
 * files per scanner (including paused files); virtual workers never use an unbounded executor.
 */
public final class FileTransferScheduler implements AutoCloseable {
    private static final int MAX_GROUPS = 32;
    private static final int MAX_PENDING_PER_GROUP = 16;
    private final Set<Group> groups = new HashSet<>();
    private int parallelism;
    private int active;
    private boolean closed;

    public FileTransferScheduler(int parallelism) { setParallelism(parallelism); }

    public synchronized void setParallelism(int value) {
        if (value < 1 || value > 16) throw new IllegalArgumentException("Parallelism must be between 1 and 16");
        parallelism = value;
        notifyAll();
    }

    public synchronized Group openGroup(TransferControl control) throws IOException {
        return openGroup(control, true);
    }

    /** For protocol tasks that must finish an in-flight request before cleaning up its effects. */
    public synchronized Group openCooperativeGroup(TransferControl control) throws IOException {
        return openGroup(control, false);
    }

    private Group openGroup(TransferControl control, boolean interruptWorkers) throws IOException {
        if (closed) throw new IOException("Transfer queue is closed");
        if (groups.size() >= MAX_GROUPS) throw new IOException("Too many unfinished directory transfers");
        Group group = new Group(Objects.requireNonNull(control, "control"), interruptWorkers);
        groups.add(group);
        return group;
    }

    private synchronized boolean acquire() throws IOException {
        if (closed) throw new InterruptedIOException("Transfer queue is closed");
        if (active < parallelism) { active++; return true; }
        await(this);
        return false;
    }

    private synchronized void release() { active--; notifyAll(); }

    @Override
    public void close() {
        List<Group> snapshot;
        synchronized (this) {
            closed = true;
            snapshot = List.copyOf(groups);
            notifyAll();
        }
        snapshot.forEach(Group::cancel);
    }

    @FunctionalInterface
    public interface FileTask {
        void run(TransferControl control) throws IOException;
    }

    /** One parent task; scanning applies backpressure and completion waits for actual worker cleanup. */
    public final class Group implements AutoCloseable {
        private final TransferControl control;
        private final boolean interruptWorkers;
        private final Thread owner = Thread.currentThread();
        private final Set<Thread> workers = new HashSet<>();
        private volatile IOException failure;
        private volatile boolean stopped;
        private boolean finished;
        private boolean retired;

        private Group(TransferControl control, boolean interruptWorkers) {
            this.control = control;
            this.interruptWorkers = interruptWorkers;
        }

        public void checkpoint() throws IOException {
            checkFailure();
            control.checkpoint();
            checkFailure();
        }

        public void checkFailure() throws IOException {
            if (failure != null) throw failure;
            if (stopped || Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Transfer cancelled");
            }
        }

        public void submit(FileTask task) throws IOException {
            Objects.requireNonNull(task, "task");
            while (true) {
                checkpoint();
                Thread worker;
                synchronized (this) {
                    checkFailure();
                    if (finished || retired) throw new IOException("Transfer group is finished");
                    if (workers.size() >= MAX_PENDING_PER_GROUP) { awaitWorkers(); continue; }
                    worker = Thread.ofVirtual().name("fastremote-transfer-file").unstarted(() -> run(task));
                    workers.add(worker);
                }
                worker.start();
                return;
            }
        }

        private void run(FileTask task) {
            try (SlotControl slot = new SlotControl()) {
                slot.checkpoint();
                task.run(slot);
            } catch (IOException | RuntimeException | Error problem) {
                fail(problem instanceof IOException io ? io : new IOException("File transfer failed", problem));
            } finally {
                boolean remove;
                synchronized (this) {
                    workers.remove(Thread.currentThread());
                    remove = retired && workers.isEmpty();
                    notifyAll();
                }
                if (remove) synchronized (FileTransferScheduler.this) { groups.remove(this); }
            }
        }

        private void fail(IOException problem) {
            synchronized (this) {
                if (stopped) return;
                if (failure == null) failure = problem;
            }
            cancel();
        }

        private void cancel() {
            List<Thread> snapshot;
            synchronized (this) {
                // Closing the group after failure must not interrupt siblings a second time
                // while they are closing handles and removing temporary files.
                if (finished || stopped) return;
                stopped = true;
                snapshot = List.copyOf(workers);
                // The owner may be a pooled thread. Do not interrupt it after this group returns.
                if (!retired && owner != Thread.currentThread()) owner.interrupt();
                notifyAll();
            }
            if (interruptWorkers) {
                for (Thread worker : snapshot) if (worker != Thread.currentThread()) worker.interrupt();
            }
        }

        public void finish() throws IOException {
            while (true) {
                checkpoint();
                synchronized (this) {
                    if (workers.isEmpty()) { checkFailure(); finished = true; return; }
                    awaitWorkers();
                }
            }
        }

        private void awaitWorkers() throws IOException {
            try { await(this); }
            catch (InterruptedIOException interrupted) { checkFailure(); throw interrupted; }
        }

        @Override
        public void close() throws IOException {
            if (!finished) cancel();
            boolean interrupted = Thread.interrupted();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            try {
                synchronized (this) {
                    while (!workers.isEmpty()) {
                        if (System.nanoTime() >= deadline) throw new IOException("Timed out closing file transfers");
                        try { wait(100); }
                        catch (InterruptedException ignored) { interrupted = true; }
                    }
                }
            } finally {
                boolean remove;
                synchronized (this) { retired = true; remove = workers.isEmpty(); }
                if (remove) synchronized (FileTransferScheduler.this) { groups.remove(this); }
                if (interrupted && failure == null) Thread.currentThread().interrupt();
            }
        }

        private final class SlotControl implements TransferControl, AutoCloseable {
            private boolean held;

            @Override
            public void checkpoint() throws IOException {
                while (true) {
                    checkFailure();
                    // The gate releases our slot atomically with deciding to wait for resume.
                    control.checkpoint(this::close);
                    checkFailure();
                    if (held) return;
                    held = acquire();
                }
            }

            @Override public void close() { if (held) { held = false; release(); } }
        }
    }

    private static void await(Object monitor) throws InterruptedIOException {
        try { monitor.wait(100); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Transfer interrupted");
        }
    }
}
