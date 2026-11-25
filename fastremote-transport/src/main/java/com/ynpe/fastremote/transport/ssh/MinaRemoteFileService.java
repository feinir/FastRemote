// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import com.ynpe.fastremote.domain.transfer.FileTransferScheduler;
import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import com.ynpe.fastremote.domain.transfer.RemoteFileAttributes;
import com.ynpe.fastremote.domain.transfer.RemoteFileService;
import com.ynpe.fastremote.domain.transfer.TransferConflict;
import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;
import com.ynpe.fastremote.domain.transfer.TransferConflictResolver;
import com.ynpe.fastremote.domain.transfer.TransferControl;
import com.ynpe.fastremote.domain.transfer.TransferActivity;
import com.ynpe.fastremote.domain.transfer.TransferActivityListener;
import com.ynpe.fastremote.domain.transfer.TransferProgressListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.extensions.openssh.OpenSSHPosixRenameExtension;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;

final class MinaRemoteFileService implements RemoteFileService {
    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    private final ClientSession session;
    private final SftpChannelBudget channels;
    private final SftpDeleteQueue deletions;

    MinaRemoteFileService(ClientSession session) {
        this.session = session;
        channels = new SftpChannelBudget(session);
        deletions = new SftpDeleteQueue(() -> {
            var lease = channels.open();
            return remoteDeleteAccess(lease.client(), SftpDeleteQueue.MAX_DIRECTORY_ENTRIES, lease);
        });
        session.addSessionListener(new org.apache.sshd.common.session.SessionListener() {
            @Override public void sessionClosed(org.apache.sshd.common.session.Session ignored) {
                deletions.close();
            }
        });
    }

    @Override
    public String canonicalPath(String path) throws IOException {
        requireOpen();
        try (var lease = channels.open()) {
            SftpClient client = lease.client();
            return displayPath(client.canonicalPath(protocolPath(path)));
        }
    }

    private static String resolveSymbolicLink(SftpClient client, String path) throws IOException {
        String current = client.canonicalPath(path);
        var visited = new java.util.HashSet<String>();
        for (int depth = 0; depth < 40; depth++) {
            if (!visited.add(current)) throw new IOException("Symbolic link cycle: " + path);
            if (!client.lstat(current).isSymbolicLink()) return current;
            String link = client.readLink(current);
            if (link == null || link.isBlank()) throw new IOException("Symbolic link target is empty: " + current);
            boolean absolute = link.startsWith("/") || link.matches("^[A-Za-z]:[/\\\\].*");
            int separator = current.lastIndexOf('/');
            current = client.canonicalPath(absolute ? link
                    : current.substring(0, separator + 1) + link);
        }
        throw new IOException("Symbolic link depth exceeds limit: " + path);
    }

    @Override
    public List<RemoteFileEntry> list(String directory) throws IOException {
        requireOpen();
        List<RemoteFileEntry> result = new ArrayList<>();
        try (var lease = channels.open()) {
            SftpClient client = lease.client();
            String protocolDirectory = client.canonicalPath(protocolPath(directory));
            String canonical = displayPath(protocolDirectory);
            for (SftpClient.DirEntry entry : client.readDir(protocolDirectory)) {
                if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) {
                    continue;
                }
                var attributes = entry.getAttributes();
                Optional<Instant> modified = attributes.getModifyTime() == null
                        ? Optional.empty()
                        : Optional.of(attributes.getModifyTime().toInstant());
                String child = listedChildPath(canonical, entry.getFilename());
                result.add(new RemoteFileEntry(
                        entry.getFilename(),
                        child,
                        attributes.isDirectory(),
                        attributes.isSymbolicLink(),
                        Math.max(0L, attributes.getSize()),
                        modified));
            }
        }
        result.sort(Comparator.comparing(RemoteFileEntry::directory).reversed()
                .thenComparing(RemoteFileEntry::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    @Override
    public RemoteFileAttributes readAttributes(String path) throws IOException {
        requireOpen();
        try (var lease = channels.open()) {
            SftpClient client = lease.client();
            SftpClient.Attributes attributes = client.lstat(protocolPath(path));
            String display = displayPath(path);
            String normalized = display.endsWith("/") && display.length() > 1
                    ? display.substring(0, display.length() - 1) : display;
            int separator = normalized.lastIndexOf('/');
            String name = separator < 0 ? normalized : normalized.substring(separator + 1);
            if (name.isBlank()) name = "/";
            Optional<Instant> modified = attributes.getModifyTime() == null
                    ? Optional.empty() : Optional.of(attributes.getModifyTime().toInstant());
            String owner = attributes.getOwner();
            if (owner == null || owner.isBlank()) owner = Integer.toString(attributes.getUserId());
            String group = attributes.getGroup();
            if (group == null || group.isBlank()) group = Integer.toString(attributes.getGroupId());
            return new RemoteFileAttributes(name, normalized, attributes.isDirectory(), attributes.isSymbolicLink(),
                    Math.max(0L, attributes.getSize()), modified, owner, group,
                    attributes.getPermissions() & 07777,
                    attributes.isSymbolicLink() ? Optional.of(fullSymbolicLinkTarget(
                            displayPath(client.canonicalPath(protocolPath(separator < 0 ? "."
                                    : separator == 0 ? "/" : normalized.substring(0, separator)))),
                            client.readLink(protocolPath(path)))) : Optional.empty());
        }
    }

    static String fullSymbolicLinkTarget(String parent, String link) throws IOException {
        if (link == null || link.isBlank()) throw new IOException("Symbolic link target is empty");
        String target = displayPath(link);
        String joined = target.startsWith("/") ? target : parent + "/" + target;
        var parts = new java.util.ArrayDeque<String>();
        for (String part : joined.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!parts.isEmpty() && !parts.getLast().endsWith(":")) parts.removeLast();
            } else parts.addLast(part);
        }
        return "/" + String.join("/", parts);
    }

    @Override
    public void setPermissions(String path, int permissions) throws IOException {
        requireOpen();
        if (permissions < 0 || permissions > 07777) throw new IllegalArgumentException("Invalid POSIX permissions");
        try (var lease = channels.open()) {
            SftpClient client = lease.client();
            client.setStat(protocolPath(path), new SftpClient.Attributes().perms(permissions));
        }
    }

    @Override
    public void upload(Path localFile, String remoteFile) throws IOException {
        requireOpen();
        if (!Files.isRegularFile(localFile) || !Files.isReadable(localFile)) {
            throw new IOException("The selected local file cannot be read");
        }
        try (var lease = channels.open()) {
            SftpClient client = lease.client();
            String target = protocolPath(remoteFile);
            if (remoteExists(client, target) && client.lstat(target).isDirectory()) {
                throw new IOException("Remote target is a directory");
            }
            uploadFile(client, localFile, target, () -> {}, new ProgressCounter((done, total) -> {}), null);
        }
    }

    @Override
    public void download(String remoteFile, Path localFile) throws IOException {
        requireOpen();
        Path absolute = localFile.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent == null || !Files.isDirectory(parent) || Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The selected download directory is unavailable");
        }
        try (var lease = channels.open()) {
            SftpClient client = lease.client();
            downloadFile(client, protocolPath(remoteFile), absolute, () -> {},
                    new ProgressCounter((done, total) -> {}));
        }
    }

    @Override
    public byte[] readFile(String remoteFile, int maximumBytes) throws IOException {
        requireOpen();
        if (maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
        try (var lease = channels.open()) {
            SftpClient client = lease.client();
            String target = protocolPath(remoteFile);
            SftpClient.Attributes attributes = client.stat(target);
            if (!attributes.isRegularFile()) throw new IOException("Remote path is not a regular file");
            if (attributes.getSize() > maximumBytes) throw new IOException("Remote text file exceeds the size limit");
            try (InputStream input = client.read(target);
                    var output = new java.io.ByteArrayOutputStream((int) Math.min(attributes.getSize(), maximumBytes))) {
                byte[] buffer = new byte[Math.min(COPY_BUFFER_SIZE, maximumBytes + 1)];
                int total = 0;
                for (int count; (count = input.read(buffer)) >= 0;) {
                    if (count == 0) continue;
                    total += count;
                    if (total > maximumBytes) throw new IOException("Remote text file exceeds the size limit");
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        }
    }

    @Override
    public void writeFileAtomic(String remoteFile, byte[] content, boolean overwrite) throws IOException {
        requireOpen();
        byte[] safe = java.util.Objects.requireNonNull(content, "content");
        try (var lease = channels.open()) {
            SftpClient client = lease.client();
            String target = protocolPath(remoteFile);
            boolean targetExists = remoteExists(client, target);
            if (!overwrite && targetExists) throw new IOException("Remote target already exists");
            if (targetExists && client.lstat(target).isSymbolicLink()) {
                // Atomic rename must replace the referent, never the link itself.
                target = resolveSymbolicLink(client, target);
                if (!client.stat(target).isRegularFile()) throw new IOException("Remote path is not a regular file");
            }
            OptionalInt permissions = OptionalInt.empty();
            if (targetExists) {
                SftpClient.Attributes targetAttributes = client.lstat(target);
                if (targetAttributes.isRegularFile()) permissions = TransferFilePermissions.remote(targetAttributes);
                if (targetAttributes.isDirectory() && !targetAttributes.isSymbolicLink()) {
                    throw new IOException("Remote target is a directory");
                }
            }
            String temporary = target + ".fastremote-" + java.util.UUID.randomUUID() + ".part";
            try {
                try (OutputStream output = client.write(temporary, SftpClient.OpenMode.Write,
                        SftpClient.OpenMode.Create, SftpClient.OpenMode.Exclusive)) {
                    output.write(safe);
                }
                TransferFilePermissions.applyRemote(client, temporary, permissions);
                if (overwrite) replaceRemoteFile(replaceAccess(client), temporary, target);
                else client.rename(temporary, target);
            } finally {
                removeRemoteIfPresent(client, temporary);
            }
        }
    }

    private static RemoteReplaceAccess replaceAccess(SftpClient client) {
        OpenSSHPosixRenameExtension posixRename = client.getExtension(OpenSSHPosixRenameExtension.class);
        return new RemoteReplaceAccess() {
            @Override public int version() { return client.getVersion(); }
            @Override public boolean supportsPosixRename() {
                return posixRename != null && posixRename.isSupported();
            }
            @Override public boolean exists(String path) throws IOException {
                if (!remoteExists(client, path)) return false;
                SftpClient.Attributes attributes = client.lstat(path);
                if (attributes.isDirectory() && !attributes.isSymbolicLink()) {
                    throw new IOException("Remote target is a directory");
                }
                return true;
            }
            @Override public void atomicOverwrite(String source, String target) throws IOException {
                client.rename(source, target, SftpClient.CopyMode.Overwrite, SftpClient.CopyMode.Atomic);
            }
            @Override public void posixRename(String source, String target) throws IOException {
                posixRename.posixRename(source, target);
            }
            @Override public void rename(String source, String target) throws IOException {
                client.rename(source, target);
            }
            @Override public void remove(String path) throws IOException { client.remove(path); }
        };
    }

    static void replaceRemoteFile(RemoteReplaceAccess access, String temporary, String target) throws IOException {
        if (access.version() >= SftpConstants.SFTP_V5) {
            access.atomicOverwrite(temporary, target);
            return;
        }
        if (access.supportsPosixRename()) {
            access.posixRename(temporary, target);
            return;
        }

        if (!access.exists(target)) {
            access.rename(temporary, target);
            return;
        }
        String backup = target + ".fastremote-" + java.util.UUID.randomUUID() + ".bak";
        access.rename(target, backup);
        try {
            access.rename(temporary, target);
        } catch (IOException | RuntimeException installFailure) {
            try {
                access.rename(backup, target);
            } catch (IOException | RuntimeException rollbackFailure) {
                installFailure.addSuppressed(rollbackFailure);
            }
            throw installFailure;
        }
        try {
            access.remove(backup);
        } catch (IOException cleanupFailure) {
            throw new IOException("Remote file was updated, but its compatibility backup could not be removed: "
                    + backup, cleanupFailure);
        }
    }

    interface RemoteReplaceAccess {
        int version();
        boolean supportsPosixRename();
        boolean exists(String path) throws IOException;
        void atomicOverwrite(String source, String target) throws IOException;
        void posixRename(String source, String target) throws IOException;
        void rename(String source, String target) throws IOException;
        void remove(String path) throws IOException;
    }

    @Override
    public void uploadTree(Path localSource, String remoteTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts) throws IOException {
        uploadTree(localSource, remoteTarget, control, progress, conflicts, TransferActivityListener.NONE);
    }

    @Override
    public void uploadTree(Path localSource, String remoteTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts,
            TransferActivityListener activity) throws IOException {
        requireOpen();
        Path source = localSource.toAbsolutePath().normalize();
        if (!Files.exists(source)) throw new IOException("The selected local object is unavailable");
        ProgressCounter counter = new ProgressCounter(progress);
        try (var lease = channels.open(control)) {
            SftpClient client = lease.client();
            uploadEntry(client, source, protocolPath(remoteTarget), control, conflicts, counter, activity, null, 0);
        }
    }

    @Override
    public void downloadTree(String remoteSource, Path localTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts) throws IOException {
        downloadTree(remoteSource, localTarget, control, progress, conflicts, TransferActivityListener.NONE);
    }

    @Override
    public void downloadTree(String remoteSource, Path localTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts,
            TransferActivityListener activity) throws IOException {
        requireOpen();
        Path target = localTarget.toAbsolutePath().normalize();
        ProgressCounter counter = new ProgressCounter(progress);
        try (var lease = channels.open(control)) {
            SftpClient client = lease.client();
            downloadEntry(client, protocolPath(remoteSource), target, control, conflicts, counter, activity, null, 0, java.util.Set.of());
        }
    }

    @Override
    public void uploadTree(Path localSource, String remoteTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts,
            TransferActivityListener activity, FileTransferScheduler scheduler) throws IOException {
        requireOpen();
        ProgressCounter counter = new ProgressCounter(progress);
        try (TreeTransfer tree = new TreeTransfer(scheduler.openCooperativeGroup(control))) {
            FileTransferScheduler.Group group = tree.group;
            try {
                try (var scan = channels.scan(group::checkpoint);
                        var lease = channels.open(group::checkpoint)) {
                    SftpClient client = lease.client();
                    uploadEntry(client, localSource.toAbsolutePath().normalize(), protocolPath(remoteTarget),
                            group::checkpoint, conflicts, counter, activity, tree, 0);
                }
                group.finish();
            } catch (java.io.UncheckedIOException failure) {
                group.checkFailure();
                throw failure.getCause();
            } catch (IOException failure) {
                group.checkFailure();
                throw failure;
            }
        }
    }

    @Override
    public void downloadTree(String remoteSource, Path localTarget, TransferControl control,
            TransferProgressListener progress, TransferConflictResolver conflicts,
            TransferActivityListener activity, FileTransferScheduler scheduler) throws IOException {
        requireOpen();
        ProgressCounter counter = new ProgressCounter(progress);
        try (TreeTransfer tree = new TreeTransfer(scheduler.openCooperativeGroup(control))) {
            FileTransferScheduler.Group group = tree.group;
            try {
                try (var scan = channels.scan(group::checkpoint);
                        var lease = channels.open(group::checkpoint)) {
                    SftpClient client = lease.client();
                    downloadEntry(client, protocolPath(remoteSource), localTarget.toAbsolutePath().normalize(),
                            group::checkpoint, conflicts, counter, activity, tree, 0, java.util.Set.of());
                }
                group.finish();
            } catch (java.io.UncheckedIOException failure) {
                group.checkFailure();
                throw failure.getCause();
            } catch (IOException failure) {
                group.checkFailure();
                throw failure;
            }
        }
    }

    @Override
    public void createDirectory(String path) throws IOException {
        requireOpen();
        try (var lease = channels.open()) {
            SftpClient client = lease.client();
            client.mkdir(protocolPath(path));
        }
    }

    @Override
    public void createFile(String path) throws IOException {
        requireOpen();
        try (var lease = channels.open();
                OutputStream ignored = lease.client().write(protocolPath(path), SftpClient.OpenMode.Write,
                        SftpClient.OpenMode.Create, SftpClient.OpenMode.Exclusive)) {
            // Creating and closing the handle deliberately creates a zero-byte file.
        }
    }

    @Override
    public void delete(String path) throws IOException {
        delete(path, TransferActivityListener.NONE);
    }

    @Override
    public void delete(String path, TransferActivityListener activity) throws IOException {
        requireOpen();
        deletions.delete(protocolPath(path), activity);
    }

    @Override
    public void delete(String path, TransferActivityListener activity, int parallelism) throws IOException {
        requireOpen();
        deletions.delete(protocolPath(path), activity, parallelism);
    }

    @Override
    public void rename(String source, String target) throws IOException {
        requireOpen();
        try (var lease = channels.open()) {
            SftpClient client = lease.client();
            client.rename(protocolPath(source), protocolPath(target));
        }
    }

    private void uploadEntry(SftpClient client, Path source, String requestedTarget,
            TransferControl control, TransferConflictResolver conflicts, ProgressCounter counter,
            TransferActivityListener activity, TreeTransfer tree, int depth) throws IOException {
        if (depth > 256) throw new IOException("Directory nesting exceeds transfer limit");
        control.checkpoint();
        if (tree != null) tree.awaitTarget(requestedTarget);
        BasicFileAttributes attributes = Files.readAttributes(
                source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        boolean directory = attributes.isDirectory();
        activity.processing(new TransferActivity(TransferActivity.Operation.UPLOAD,
                activityType(directory, attributes.isSymbolicLink()), source.toString(), requestedTarget));
        if (attributes.isSymbolicLink() && Files.isDirectory(source)) {
            activity.completed(source.toString(), requestedTarget, "SKIPPED", "Directory symbolic link"); return;
        }
        String target = resolveRemoteConflict(client, source.toString(), requestedTarget, directory, conflicts, tree);
        if (target == null) { activity.completed(source.toString(), requestedTarget.toString(), "SKIPPED", "Conflict policy"); return; }
        if (directory) {
            if (!remoteExists(client, target)) client.mkdir(target);
            try (var children = Files.list(source)) {
                var iterator = children.iterator();
                while (iterator.hasNext()) {
                    Path child = iterator.next();
                    uploadEntry(client, child, joinRemote(target, child.getFileName().toString()),
                            control, conflicts, counter, activity, tree, depth + 1);
                }
            }
            activity.completed(source.toString(), target, "SCANNED", "Directory enumerated; children may still be transferring");
            return;
        }
        long size = Files.size(source);
        counter.discover(size);
        if (tree == null) reportTransfer(activity, source.toString(), target,
                () -> uploadFile(client, source, target, control, counter, null));
        else tree.submit(target, workerControl -> {
            try (var lease = channels.open(workerControl)) {
                SftpClient worker = lease.client();
                reportTransfer(activity, source.toString(), target,
                        () -> uploadFile(worker, source, target, workerControl, counter, tree));
            }
        });
    }

    @FunctionalInterface private interface ReportedTransfer { String run() throws IOException; }
    private static void reportTransfer(TransferActivityListener listener, String source, String target,
            ReportedTransfer action) throws IOException {
        try { String actualTarget = action.run(); listener.completed(source, actualTarget, "SUCCEEDED", "Server/file API acknowledged; checksum not verified"); }
        catch (IOException | RuntimeException failure) {
            listener.completed(source, target, "FAILED", "Transfer failed; partial effects may remain"); throw failure;
        }
    }

    private static String uploadFile(SftpClient client, Path source, String target,
            TransferControl control, ProgressCounter counter, TreeTransfer tree) throws IOException {
        var existing = remoteAttributesIfPresent(client, target);
        if (existing.isPresent() && existing.orElseThrow().isSymbolicLink()) {
            target = resolveSymbolicLink(client, target);
            if (!client.stat(target).isRegularFile()) throw new IOException("Remote path is not a regular file");
            existing = remoteAttributesIfPresent(client, target);
        }
        var targetAttributes = existing.filter(SftpClient.Attributes::isRegularFile);
        OptionalInt permissions = targetAttributes.isPresent()
                ? TransferFilePermissions.remote(targetAttributes.orElseThrow()) : TransferFilePermissions.local(source);
        String temporary = target + ".fastremote-" + java.util.UUID.randomUUID() + ".part";
        boolean committed = false;
        try {
            try (InputStream input = Files.newInputStream(source);
                    OutputStream output = client.write(temporary, SftpClient.OpenMode.Write,
                            SftpClient.OpenMode.Create, SftpClient.OpenMode.Truncate)) {
                copy(input, output, control, counter);
            }
            control.checkpoint();
            // Set permissions before committing so a rejected chmod leaves the original file intact.
            TransferFilePermissions.applyRemote(client, temporary, permissions);
            control.checkpoint();
            if (remoteExists(client, target) && client.lstat(target).isDirectory()) deleteRemote(client, target);
            replaceRemoteFile(replaceAccess(client), temporary, target);
            committed = true;
        } finally {
            boolean interrupted = Thread.interrupted();
            try {
                if (!committed && !removeRemoteIfPresent(client, temporary) && tree != null) tree.retryCleanup(temporary);
            }
            finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
        return target;
    }

    private void downloadEntry(SftpClient client, String source, Path requestedTarget,
            TransferControl control, TransferConflictResolver conflicts, ProgressCounter counter,
            TransferActivityListener activity, TreeTransfer tree, int depth, java.util.Set<String> ancestors) throws IOException {
        if (depth > 256) throw new IOException("Directory nesting exceeds transfer limit");
        control.checkpoint();
        if (tree != null) tree.awaitTarget(requestedTarget.toString());
        SftpClient.Attributes attributes = client.lstat(source);
        boolean symbolicLink = attributes.isSymbolicLink();
        if (symbolicLink) attributes = client.stat(source);
        boolean directory = attributes.isDirectory();
        activity.processing(new TransferActivity(TransferActivity.Operation.DOWNLOAD,
                activityType(directory, symbolicLink), source, requestedTarget.toString()));
        String directorySource = directory ? resolveSymbolicLink(client, source) : source;
        // Follow directory links as content, but never recurse into an ancestor again.
        if (directory && ancestors.contains(directorySource)) {
            activity.completed(source, requestedTarget.toString(), "SKIPPED", "Symbolic link cycle"); return;
        }
        if (!directory && !attributes.isRegularFile()) throw new IOException("Remote path is not a regular file");
        Path target = resolveLocalConflict(source, requestedTarget, directory, conflicts, tree);
        if (target == null) { activity.completed(source.toString(), requestedTarget.toString(), "SKIPPED", "Conflict policy"); return; }
        if (directory) {
            Files.createDirectories(target);
            var childrenAncestors = new java.util.HashSet<>(ancestors);
            childrenAncestors.add(directorySource);
            for (SftpClient.DirEntry entry : client.readDir(directorySource)) {
                String name = entry.getFilename();
                if (name.equals(".") || name.equals("..")) continue;
                downloadEntry(client, joinRemote(directorySource, name), target.resolve(name), control, conflicts, counter,
                        activity, tree, depth + 1, childrenAncestors);
            }
            activity.completed(source, target.toString(), "SCANNED", "Directory enumerated; children may still be transferring");
            return;
        }
        counter.discover(Math.max(0L, attributes.getSize()));
        if (tree == null) reportTransfer(activity, source, target.toString(),
                () -> { downloadFile(client, source, target, control, counter); return target.toString(); });
        else tree.submit(target.toString(), workerControl -> {
            try (var lease = channels.open(workerControl)) {
                SftpClient worker = lease.client();
                reportTransfer(activity, source, target.toString(),
                        () -> { downloadFile(worker, source, target, workerControl, counter); return target.toString(); });
            }
        });
    }

    private static void downloadFile(SftpClient client, String source, Path target,
            TransferControl control, ProgressCounter counter) throws IOException {
        Path parent = target.getParent();
        if (parent == null) throw new IOException("The selected download directory is unavailable");
        Files.createDirectories(parent);
        OptionalInt permissions = Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                ? TransferFilePermissions.local(target, LinkOption.NOFOLLOW_LINKS)
                : TransferFilePermissions.remote(client.stat(source));
        Path temporary = Files.createTempFile(parent, ".fastremote-download-", ".part");
        try {
            try (InputStream input = client.read(source);
                    OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
                copy(input, output, control, counter);
            }
            control.checkpoint();
            TransferFilePermissions.applyLocal(temporary, permissions);
            control.checkpoint();
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    && Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                deleteLocalTarget(target);
            }
            moveReplacing(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void copy(InputStream input, OutputStream output, TransferControl control,
            ProgressCounter counter) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int read;
        while (true) {
            control.checkpoint();
            read = input.read(buffer);
            if (read < 0) break;
            if (read == 0) continue;
            output.write(buffer, 0, read);
            counter.transferred(read);
        }
    }

    private static String resolveRemoteConflict(SftpClient client, String source, String target,
            boolean directory, TransferConflictResolver resolver, TreeTransfer tree) throws IOException {
        if (!remoteExists(client, target)) return target;
        TransferConflictChoice choice = resolver.resolve(new TransferConflict(source, target, directory));
        if (choice == TransferConflictChoice.OVERWRITE) {
            SftpClient.Attributes targetAttributes = client.lstat(target);
            boolean targetDirectory = targetAttributes.isDirectory() && !targetAttributes.isSymbolicLink();
            if (directory && !targetDirectory) deleteRemote(client, target);
        }
        return switch (choice) {
            case OVERWRITE -> target;
            case SKIP -> null;
            case RENAME -> uniqueRemoteTarget(client, target, directory, tree);
            case CANCEL -> throw new java.io.InterruptedIOException("Transfer cancelled by conflict decision");
        };
    }

    private static Path resolveLocalConflict(String source, Path target, boolean directory,
            TransferConflictResolver resolver, TreeTransfer tree) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return target;
        TransferConflictChoice choice = resolver.resolve(new TransferConflict(source, target.toString(), directory));
        if (choice == TransferConflictChoice.OVERWRITE) {
            BasicFileAttributes targetAttributes = Files.readAttributes(
                    target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            boolean targetDirectory = targetAttributes.isDirectory() && !targetAttributes.isSymbolicLink();
            if (directory && !targetDirectory) deleteLocalTarget(target);
        }
        return switch (choice) {
            case OVERWRITE -> target;
            case SKIP -> null;
            case RENAME -> uniqueLocalTarget(target, directory, tree);
            case CANCEL -> throw new java.io.InterruptedIOException("Transfer cancelled by conflict decision");
        };
    }

    private static String uniqueRemoteTarget(SftpClient client, String target, boolean directory, TreeTransfer tree) throws IOException {
        for (int index = 1; index <= 10_000; index++) {
            String candidate = numberedName(target, directory, index);
            if ((tree == null || !tree.pending(candidate)) && !remoteExists(client, candidate)) return candidate;
        }
        throw new IOException("Unable to create a unique remote name");
    }

    private static Path uniqueLocalTarget(Path target, boolean directory, TreeTransfer tree) throws IOException {
        for (int index = 1; index <= 10_000; index++) {
            Path candidate = Path.of(numberedName(target.toString(), directory, index));
            if ((tree == null || !tree.pending(candidate.toString()))
                    && !Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) return candidate;
        }
        throw new IOException("Unable to create a unique local name");
    }

    private static String numberedName(String target, boolean directory, int index) {
        int slash = Math.max(target.lastIndexOf('/'), target.lastIndexOf(java.io.File.separatorChar));
        int dot = directory ? -1 : target.lastIndexOf('.');
        if (dot <= slash + 1) dot = -1;
        String suffix = " (" + index + ")";
        return dot < 0 ? target + suffix : target.substring(0, dot) + suffix + target.substring(dot);
    }

    private static boolean remoteExists(SftpClient client, String path) throws IOException {
        return remoteAttributesIfPresent(client, path).isPresent();
    }

    private static Optional<SftpClient.Attributes> remoteAttributesIfPresent(SftpClient client, String path)
            throws IOException {
        try {
            return Optional.of(client.lstat(path));
        } catch (SftpException failure) {
            if (isMissingStatus(failure.getStatus())) return Optional.empty();
            throw failure;
        }
    }

    static boolean isMissingStatus(int status) {
        // OpenSSH for Windows can report a missing drive-qualified child as NO_SUCH_PATH,
        // while POSIX servers usually report NO_SUCH_FILE for the same lstat probe.
        return status == SftpConstants.SSH_FX_NO_SUCH_FILE
                || status == SftpConstants.SSH_FX_NO_SUCH_PATH;
    }

    private static void deleteRemote(SftpClient client, String path) throws IOException {
        deleteRemote(client, path, TransferActivityListener.NONE);
    }

    private static void deleteRemote(SftpClient client, String path, TransferActivityListener activity) throws IOException {
        RemoteDeleteAccess access = remoteDeleteAccess(client, Integer.MAX_VALUE);
        List<RemoteDeleteFailure> failures = new ArrayList<>();
        deleteRemote(access, path, null, failures, activity);
        if (!failures.isEmpty()) {
            IOException aggregate = new IOException("Remote deletion partially failed");
            for (RemoteDeleteFailure failure : failures) {
                String reason = failure.cause().getMessage();
                if (reason == null || reason.isBlank()) reason = failure.cause().getClass().getSimpleName();
                aggregate.addSuppressed(new IOException(failure.path() + ": " + reason, failure.cause()));
            }
            throw aggregate;
        }
    }

    private static RemoteDeleteAccess remoteDeleteAccess(SftpClient client, int maximumEntries) {
        return remoteDeleteAccess(client, maximumEntries, client);
    }

    private static RemoteDeleteAccess remoteDeleteAccess(SftpClient client, int maximumEntries,
            java.io.Closeable closeable) {
        return new RemoteDeleteAccess() {
            @Override
            public RemoteDeleteAttributes attributes(String target) throws IOException {
                SftpClient.Attributes attributes = client.lstat(target);
                return new RemoteDeleteAttributes(attributes.isDirectory(), attributes.isSymbolicLink());
            }

            @Override
            public List<RemoteDeleteChild> children(String directory) throws IOException {
                List<RemoteDeleteChild> children = new ArrayList<>();
                try {
                    for (SftpClient.DirEntry entry : client.readDir(directory)) {
                        String name = entry.getFilename();
                        if (!name.equals(".") && !name.equals("..")) {
                            SftpClient.Attributes attributes = entry.getAttributes();
                            if (children.size() >= maximumEntries) {
                                throw new IOException("Directory is too large to snapshot");
                            }
                            children.add(new RemoteDeleteChild(name,
                                    new RemoteDeleteAttributes(
                                            attributes.isDirectory(), attributes.isSymbolicLink())));
                        }
                    }
                } catch (java.io.UncheckedIOException failure) {
                    throw failure.getCause();
                }
                return List.copyOf(children);
            }

            @Override public void close() throws IOException { closeable.close(); }

            @Override public boolean isOpen() { return client.isOpen(); }

            @Override public void remove(String target) throws IOException { client.remove(target); }

            @Override public void removeDirectory(String target) throws IOException { client.rmdir(target); }

            @Override
            public boolean isMissing(IOException failure) {
                return failure instanceof SftpException sftpFailure
                        && isMissingStatus(sftpFailure.getStatus());
            }
        };
    }

    static void deleteRemote(RemoteDeleteAccess access, String path, List<RemoteDeleteFailure> failures) {
        deleteRemote(access, path, null, failures, TransferActivityListener.NONE);
    }

    private static void deleteRemote(RemoteDeleteAccess access, String path,
            RemoteDeleteAttributes listedAttributes, List<RemoteDeleteFailure> failures,
            TransferActivityListener activity) {
        int failuresBeforeEntry = failures.size();
        RemoteDeleteAttributes attributes = listedAttributes;
        if (attributes == null) {
            try {
                attributes = access.attributes(path);
            } catch (IOException failure) {
                if (!access.isMissing(failure)) failures.add(new RemoteDeleteFailure(path, failure));
                return;
            }
        }
        activity.processing(new TransferActivity(TransferActivity.Operation.DELETE,
                activityType(attributes.directory(), attributes.symbolicLink()), path, path));
        if (!attributes.directory() || attributes.symbolicLink()) {
            try {
                access.remove(path);
            } catch (IOException failure) {
                if (!access.isMissing(failure)) failures.add(new RemoteDeleteFailure(path, failure));
            }
            return;
        }

        List<RemoteDeleteChild> children;
        try {
            // Snapshot before deleting: mutating a paged SFTP directory while iterating can
            // invalidate or rewind the server-side cursor.
            children = access.children(path);
        } catch (IOException failure) {
            if (!access.isMissing(failure)) failures.add(new RemoteDeleteFailure(path, failure));
            return;
        }
        for (RemoteDeleteChild child : children) {
            deleteRemote(access, joinRemote(path, child.name()), child.attributes(), failures, activity);
        }

        // Preserve a directory when a child remains. All of its siblings were still attempted.
        if (failures.size() != failuresBeforeEntry) return;
        try {
            access.removeDirectory(path);
        } catch (IOException failure) {
            if (!access.isMissing(failure)) failures.add(new RemoteDeleteFailure(path, failure));
        }
    }

    interface RemoteDeleteAccess extends AutoCloseable {
        @Override default void close() throws IOException { }

        default boolean isOpen() { return true; }

        RemoteDeleteAttributes attributes(String path) throws IOException;

        List<RemoteDeleteChild> children(String path) throws IOException;

        void remove(String path) throws IOException;

        void removeDirectory(String path) throws IOException;

        boolean isMissing(IOException failure);
    }

    record RemoteDeleteAttributes(boolean directory, boolean symbolicLink) { }

    record RemoteDeleteChild(String name, RemoteDeleteAttributes attributes) { }

    record RemoteDeleteFailure(String path, IOException cause) { }

    private static TransferActivity.ObjectType activityType(boolean directory, boolean symbolicLink) {
        if (symbolicLink) return TransferActivity.ObjectType.SYMBOLIC_LINK;
        return directory ? TransferActivity.ObjectType.DIRECTORY : TransferActivity.ObjectType.FILE;
    }

    private static void deleteLocalTarget(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isDirectory() && !attributes.isSymbolicLink()) {
            try (var children = Files.list(path)) {
                for (Path child : children.toList()) deleteLocalTarget(child);
            }
        }
        Files.delete(path);
    }

    private static boolean removeRemoteIfPresent(SftpClient client, String path) {
        try {
            if (remoteExists(client, path)) client.remove(path);
            return true;
        } catch (IOException ignored) {
            // Parallel trees retry after all worker channels (and remote handles) have closed.
            return false;
        }
    }

    private static String joinRemote(String directory, String name) {
        return directory.endsWith("/") ? directory + name : directory + "/" + name;
    }

    static String listedChildPath(String canonicalDirectory, String name) {
        String child = joinRemote(canonicalDirectory, name);
        if (canonicalDirectory.equals("/") && windowsDriveName(name) && !child.endsWith("/")) {
            // Windows SFTP servers commonly expose virtual drives as C: / D: under '/'.
            // C: means the drive's current directory; C:/ is the actual drive root.
            return child + "/";
        }
        return child;
    }

    static String protocolPath(String path) {
        String value = java.util.Objects.requireNonNull(path, "path");
        return value.matches("^/[A-Za-z]:(?:/.*)?$") ? value.substring(1) : value;
    }

    static String displayPath(String path) {
        String value = java.util.Objects.requireNonNull(path, "path").replace('\\', '/');
        return value.matches("^[A-Za-z]:(?:/.*)?$") ? "/" + value : value;
    }

    private static boolean windowsDriveName(String name) {
        return name.length() == 2 && Character.isLetter(name.charAt(0)) && name.charAt(1) == ':';
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private final class TreeTransfer implements AutoCloseable {
        private final FileTransferScheduler.Group group;
        private final java.util.Set<String> targets = new java.util.HashSet<>();

        private TreeTransfer(FileTransferScheduler.Group group) { this.group = group; }

        private final java.util.Set<String> cleanup = new java.util.HashSet<>();

        private synchronized void retryCleanup(String path) { cleanup.add(path); }

        @Override
        public void close() throws IOException {
            // Joining workers first prevents Windows sharing violations and late cancellation
            // interrupts from racing cleanup. Only failed transfers retain paths (at most 16).
            group.close();
            if (cleanup.isEmpty()) return;
            boolean interrupted = Thread.interrupted();
            try (var lease = channels.open()) {
                SftpClient client = lease.client();
                IOException failure = null;
                for (String path : cleanup) {
                    try { if (remoteExists(client, path)) client.remove(path); }
                    catch (IOException problem) {
                        if (failure == null) failure = new IOException("Could not remove temporary transfer files");
                        failure.addSuppressed(problem);
                    }
                }
                if (failure != null) throw failure;
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        }


        private boolean pending(String target) {
            synchronized (targets) { return targets.contains(target); }
        }

        private void awaitTarget(String target) throws IOException {
            while (true) {
                group.checkpoint();
                synchronized (targets) {
                    // A later sibling may replace a directory created by an earlier rename.
                    // Its descendants must finish before that directory's conflict is resolved.
                    if (targets.stream().noneMatch(pending -> pending.equals(target)
                            || pending.startsWith(target + "/")
                            || pending.startsWith(target + java.io.File.separator))) return;
                    try { targets.wait(100); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new java.io.InterruptedIOException("Transfer interrupted");
                    }
                }
            }
        }

        private void submit(String target, FileTransferScheduler.FileTask task) throws IOException {
            awaitTarget(target);
            synchronized (targets) { targets.add(target); }
            try {
                group.submit(control -> {
                    try { task.run(control); }
                    finally { release(target); }
                });
            } catch (IOException | RuntimeException failure) {
                release(target);
                throw failure;
            }
        }

        private void release(String target) {
            synchronized (targets) { targets.remove(target); targets.notifyAll(); }
        }
    }

    private static final class ProgressCounter {
        private final TransferProgressListener listener;
        private long discovered;
        private long transferred;
        private boolean notifying;
        private boolean dirty;

        private ProgressCounter(TransferProgressListener listener) { this.listener = listener; }
        private void discover(long bytes) { update(Math.max(0L, bytes), 0); }
        private void transferred(long bytes) { update(0, bytes); }

        private void update(long added, long copied) {
            synchronized (this) {
                discovered += added;
                transferred += copied;
                dirty = true;
                if (notifying) return;
                notifying = true;
            }
            try {
                while (true) {
                    long total;
                    long complete;
                    synchronized (this) {
                        if (!dirty) { notifying = false; return; }
                        dirty = false;
                        total = discovered;
                        complete = transferred;
                    }
                    listener.updated(total, complete);
                }
            } catch (RuntimeException | Error failure) {
                synchronized (this) { notifying = false; }
                throw failure;
            }
        }
    }

    private void requireOpen() throws IOException {
        if (!session.isOpen()) {
            throw new IOException("The SSH session is closed");
        }
    }
}
