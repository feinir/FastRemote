// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.UUID;

/** Called only before runtime creation, with the source instance lock and startup lock held. */
final class DataDirectoryMigration {
    private static final Set<String> INSTANCE_FILES = Set.of("instance.lock", "instance.properties");
    private DataDirectoryMigration() { }

    static void copyAndInstall(Path source, Path target) throws IOException {
        source = source.toAbsolutePath().normalize();
        target = target.toAbsolutePath().normalize();
        if (source.equals(target) || source.startsWith(target) || target.startsWith(source)) {
            throw new IOException("Data directories must not overlap");
        }
        rejectLinkedAncestors(source);
        rejectLinkedAncestors(target);
        Files.createDirectories(target.getParent());
        Path staging = Files.createTempDirectory(target.getParent(), ".fastremote-migration-");
        restrict(staging, true);
        Path origin = source;
        boolean installed = false;
        try {
            Files.walkFileTree(source, Set.of(), 128, new SimpleFileVisitor<>() {
                private int entries;
                private void count() throws IOException {
                    if (++entries > 1_000_000) throw new IOException("Too many data files to migrate");
                }
                @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs)
                        throws IOException {
                    count();
                    Path destination = staging.resolve(origin.relativize(directory));
                    if (!directory.equals(origin)) Files.copy(directory, destination, StandardCopyOption.COPY_ATTRIBUTES);
                    restrict(destination, true);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    count();
                    if (file.getParent().equals(origin) && INSTANCE_FILES.contains(file.getFileName().toString())) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (!attrs.isRegularFile()) throw new IOException("Data migration requires regular files: " + file);
                    Path destination = staging.resolve(origin.relativize(file));
                    Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
                    restrict(destination, false);
                    if (Files.mismatch(file, destination) != -1) throw new IOException("Data copy verification failed");
                    return FileVisitResult.CONTINUE;
                }
            });
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.move(target, target.resolveSibling(target.getFileName() + ".backup-" + UUID.randomUUID()),
                        StandardCopyOption.ATOMIC_MOVE);
            }
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            installed = true;
        } finally {
            // On failure keep the verified/partial staging tree for recovery; never delete user data.
            if (!installed) System.err.println("[FastRemote] Migration incomplete; source retained, staging: " + staging);
        }
    }

    private static void restrict(Path path, boolean directory) throws IOException {
        var posix = Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView.class);
        if (posix != null) {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString(
                    directory || Files.isExecutable(path) ? "rwx------" : "rw-------"));
            return;
        }
        var acl = Files.getFileAttributeView(path, java.nio.file.attribute.AclFileAttributeView.class);
        if (acl == null) throw new IOException("Destination cannot protect private application data");
        var entry = java.nio.file.attribute.AclEntry.newBuilder()
                .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                .setPrincipal(Files.getOwner(path))
                .setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class));
        if (directory) entry.setFlags(java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT,
                java.nio.file.attribute.AclEntryFlag.FILE_INHERIT);
        acl.setAcl(java.util.List.of(entry.build()));
    }

    static void rejectLinkedAncestors(Path path) throws IOException {
        for (Path entry = path; entry != null; entry = entry.getParent()) {
            if (Files.isSymbolicLink(entry)) throw new IOException("Linked data directories cannot be migrated: " + entry);
        }
    }
}
