// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import com.ynpe.fastremote.domain.transfer.FileTransferScheduler;
import com.ynpe.fastremote.domain.settings.SftpSettings;
import com.ynpe.fastremote.domain.transfer.TransferActivity;
import com.ynpe.fastremote.domain.transfer.TransferActivityListener;
import com.ynpe.fastremote.domain.transfer.TransferControl;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Independent per-SSH-session deletion queue whose channels are separate from file transfers. */
final class SftpDeleteQueue implements AutoCloseable {
    static final int MAX_DIRECTORY_ENTRIES = 100_000;
    private static final int MAX_TREE_ENTRIES = 1_000_000;
    private final FileTransferScheduler scheduler =
            new FileTransferScheduler(SftpSettings.DEFAULT_CONCURRENT_TRANSFERS);
    private final AccessFactory factory;
    private final Set<MinaRemoteFileService.RemoteDeleteAccess> accesses = new HashSet<>();
    private final ArrayDeque<MinaRemoteFileService.RemoteDeleteAccess> idle = new ArrayDeque<>();
    private int opening;
    private int operations;
    private int parallelism = SftpSettings.DEFAULT_CONCURRENT_TRANSFERS;
    private boolean closed;

    SftpDeleteQueue(AccessFactory factory) { this.factory = java.util.Objects.requireNonNull(factory, "factory"); }

    @FunctionalInterface
    interface AccessFactory { MinaRemoteFileService.RemoteDeleteAccess open() throws IOException; }

    void delete(String path, TransferActivityListener activity) throws IOException {
        delete(path, activity, SftpSettings.DEFAULT_CONCURRENT_TRANSFERS);
    }

    void delete(String path, TransferActivityListener activity, int configuredParallelism) throws IOException {
        updateParallelism(configuredParallelism);
        synchronized (this) {
            if (closed) throw new InterruptedIOException("Deletion queue is closed");
            operations++;
        }
        Failures failures = new Failures();
        try (var group = scheduler.openGroup(() -> {})) {
            try {
                scan(path, null, null, group, failures, activity, 0, new AtomicInteger(1));
                group.finish();
                failures.throwIfAny();
            } catch (IOException failure) {
                group.checkFailure();
                throw failure;
            }
        } finally { releaseIdleChannels(); }
    }

    private void updateParallelism(int configuredParallelism) {
        if (configuredParallelism < 1 || configuredParallelism > SftpSettings.MAX_CONCURRENT_TRANSFERS) {
            throw new IllegalArgumentException("configuredParallelism must be between 1 and "
                    + SftpSettings.MAX_CONCURRENT_TRANSFERS);
        }
        scheduler.setParallelism(configuredParallelism);
        List<MinaRemoteFileService.RemoteDeleteAccess> retired;
        synchronized (this) {
            parallelism = configuredParallelism;
            retired = retireExcessIdleChannels();
            notifyAll();
        }
        for (var access : retired) {
            try { access.close(); }
            catch (IOException ignored) { /* The retired channel is never reused. */ }
        }
    }

    private List<MinaRemoteFileService.RemoteDeleteAccess> retireExcessIdleChannels() {
        List<MinaRemoteFileService.RemoteDeleteAccess> retired = new ArrayList<>();
        while (accesses.size() > parallelism && !idle.isEmpty()) {
            var access = idle.removeFirst();
            accesses.remove(access);
            retired.add(access);
        }
        return retired;
    }

    private void releaseIdleChannels() {
        List<MinaRemoteFileService.RemoteDeleteAccess> snapshot = List.of();
        synchronized (this) {
            if (--operations == 0) {
                snapshot = List.copyOf(idle);
                idle.clear();
                accesses.removeAll(snapshot);
                notifyAll();
            }
        }
        // Do not reserve four SSH channels while the deletion queue is idle.
        for (var access : snapshot) {
            try { access.close(); }
            catch (IOException ignored) { /* The retired channel is never reused. */ }
        }
    }

    private void scan(String path, Directory parent, MinaRemoteFileService.RemoteDeleteAttributes listed,
            FileTransferScheduler.Group group, Failures failures,
            TransferActivityListener activity, int depth, AtomicInteger discovered) throws IOException {
        group.checkpoint();
        List<MinaRemoteFileService.RemoteDeleteChild> children;
        MinaRemoteFileService.RemoteDeleteAttributes attributes;
        try {
            if (depth > 256) {
                throw new IOException("Recursive deletion scan limit exceeded");
            }
            if (listed != null && (!listed.directory() || listed.symbolicLink())) {
                // Unlink never follows links; ordinary files need no extra per-file lstat round trip.
                attributes = listed;
                children = List.of();
            } else try (Lease lease = borrow(group::checkpoint)) {
                // Refresh directories before descent, in case an old listing now names a link.
                attributes = lease.access.attributes(path);
                children = attributes.directory() && !attributes.symbolicLink()
                        ? lease.access.children(path) : List.of();
                if (children.size() > MAX_DIRECTORY_ENTRIES) throw new IOException("Directory is too large to snapshot");
            }
            if (children.size() > MAX_TREE_ENTRIES - discovered.get()) {
                throw new IOException("Recursive deletion scan limit exceeded");
            }
            discovered.addAndGet(children.size());
            for (var child : children) validateName(child.name());
        } catch (IOException failure) {
            group.checkFailure();
            boolean missing = isMissing(failure);
            if (!missing) failures.add(path, failure);
            completeFromScanner(parent, missing, group, failures);
            return;
        }
        activity.processing(new TransferActivity(TransferActivity.Operation.DELETE,
                attributes.symbolicLink() ? TransferActivity.ObjectType.SYMBOLIC_LINK
                        : attributes.directory() ? TransferActivity.ObjectType.DIRECTORY : TransferActivity.ObjectType.FILE,
                path, path));
        if (!attributes.directory() || attributes.symbolicLink()) {
            group.submit(control -> {
                boolean removed = remove(path, false, control, failures);
                completeAncestors(parent, removed, control, failures);
            });
            return;
        }
        Directory directory = new Directory(path, parent);
        for (var child : children) {
            group.checkpoint();
            directory.pending.incrementAndGet();
            scan(path.endsWith("/") ? path + child.name() : path + "/" + child.name(),
                    directory, child.attributes(), group, failures, activity, depth + 1, discovered);
        }
        completeFromScanner(directory, true, group, failures);
    }

    private void completeFromScanner(Directory directory, boolean success,
            FileTransferScheduler.Group group, Failures failures) throws IOException {
        if (directory != null && directory.complete(success)) {
            group.submit(control -> finishDirectory(directory, control, failures));
        }
    }

    private void finishDirectory(Directory directory, TransferControl control, Failures failures) throws IOException {
        control.checkpoint();
        boolean success = !directory.failed.get() && remove(directory.path, true, control, failures);
        completeAncestors(directory.parent, success, control, failures);
    }

    private void completeAncestors(Directory directory, boolean success, TransferControl control,
            Failures failures) throws IOException {
        // The last child performs ready ancestor removals in its existing worker. Enqueuing and
        // waiting for parent jobs here would deadlock a saturated bounded queue.
        while (directory != null && directory.complete(success)) {
            control.checkpoint();
            success = !directory.failed.get() && remove(directory.path, true, control, failures);
            directory = directory.parent;
        }
    }

    private boolean remove(String path, boolean directory, TransferControl control, Failures failures)
            throws IOException {
        control.checkpoint();
        try (Lease lease = borrow(control)) {
            control.checkpoint();
            if (directory) lease.access.removeDirectory(path);
            else lease.access.remove(path);
            return true;
        } catch (IOException failure) {
            control.checkpoint();
            if (isMissing(failure)) return true;
            failures.add(path, failure);
            return false;
        }
    }

    private static boolean isMissing(IOException failure) {
        return failure instanceof org.apache.sshd.sftp.common.SftpException sftp
                && MinaRemoteFileService.isMissingStatus(sftp.getStatus());
    }

    private static void validateName(String name) throws IOException {
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/")
                || name.contains("\\") || name.indexOf('\0') >= 0) {
            throw new IOException("Unsafe remote directory entry");
        }
    }

    private Lease borrow(TransferControl control) throws IOException {
        while (true) {
            control.checkpoint();
            synchronized (this) {
                if (closed) throw new InterruptedIOException("Deletion queue is closed");
                var access = idle.poll();
                if (access != null) return new Lease(access);
                if (accesses.size() + opening < parallelism) { opening++; break; }
                try { wait(100); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Deletion interrupted");
                }
            }
        }
        MinaRemoteFileService.RemoteDeleteAccess access;
        try { access = factory.open(); }
        catch (IOException | RuntimeException | Error failure) {
            synchronized (this) { opening--; notifyAll(); }
            throw failure;
        }
        boolean reject;
        synchronized (this) {
            opening--;
            reject = closed;
            if (!reject) accesses.add(access);
            notifyAll();
        }
        if (reject) {
            access.close();
            throw new InterruptedIOException("Deletion queue is closed");
        }
        return new Lease(access);
    }

    @Override public void close() {
        List<MinaRemoteFileService.RemoteDeleteAccess> snapshot;
        synchronized (this) {
            if (closed) return;
            closed = true;
            snapshot = List.copyOf(accesses);
            accesses.clear();
            idle.clear();
            notifyAll();
        }
        scheduler.close();
        for (var access : snapshot) {
            try { access.close(); }
            catch (IOException ignored) { /* The owning SSH session is already closing. */ }
        }
    }

    private final class Lease implements AutoCloseable {
        private final MinaRemoteFileService.RemoteDeleteAccess access;
        private Lease(MinaRemoteFileService.RemoteDeleteAccess access) { this.access = access; }
        @Override public void close() throws IOException {
            boolean healthy = access.isOpen();
            boolean dispose;
            synchronized (SftpDeleteQueue.this) {
                dispose = closed || operations == 0 || !healthy || accesses.size() > parallelism;
                if (dispose) accesses.remove(access);
                else idle.add(access);
                SftpDeleteQueue.this.notifyAll();
            }
            if (dispose) access.close();
        }
    }

    private static final class Directory {
        private final String path;
        private final Directory parent;
        // The scanner owns one sentinel, preventing rmdir while more children can still be added.
        private final AtomicInteger pending = new AtomicInteger(1);
        private final AtomicBoolean failed = new AtomicBoolean();
        private Directory(String path, Directory parent) { this.path = path; this.parent = parent; }
        private boolean complete(boolean success) {
            if (!success) failed.set(true);
            return pending.decrementAndGet() == 0;
        }
    }

    private static final class Failures {
        private final List<IOException> details = new ArrayList<>();
        private int count;
        synchronized void add(String path, IOException failure) {
            count++;
            if (details.size() < 100) details.add(new IOException(path + ": " + failure.getMessage(), failure));
        }
        synchronized void throwIfAny() throws IOException {
            if (count == 0) return;
            IOException aggregate = new IOException("Remote deletion partially failed (" + count + " failures)");
            details.forEach(aggregate::addSuppressed);
            if (count > details.size()) aggregate.addSuppressed(new IOException(
                    (count - details.size()) + " additional deletion failures omitted"));
            throw aggregate;
        }
    }
}
