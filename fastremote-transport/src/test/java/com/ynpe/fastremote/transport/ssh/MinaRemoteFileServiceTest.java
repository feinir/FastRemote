// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.sshd.sftp.common.SftpConstants;
import org.junit.jupiter.api.Test;

class MinaRemoteFileServiceTest {
    @Test
    void expandsLinkTargetsToFullPathsWithoutRequiringTheirExistence() throws Exception {
        assertEquals("/etc/nginx/sites-available/web.conf",
                MinaRemoteFileService.fullSymbolicLinkTarget("/etc/nginx/sites-enabled", "../sites-available/web.conf"));
        assertEquals("/data/missing", MinaRemoteFileService.fullSymbolicLinkTarget("/etc", "/data/missing"));
        assertEquals("/C:/data/file", MinaRemoteFileService.fullSymbolicLinkTarget("/D:/links", "C:/data/file"));
        assertEquals("/file", MinaRemoteFileService.fullSymbolicLinkTarget("/dir", "../../file"));
    }

    @Test
    void representsWindowsVirtualDriveEntriesAsDriveRoots() {
        assertEquals("/C:/", MinaRemoteFileService.listedChildPath("/", "C:"));
        assertEquals("/D:/", MinaRemoteFileService.listedChildPath("/", "D:"));
        assertEquals("/home", MinaRemoteFileService.listedChildPath("/", "home"));
        assertEquals("/C:/Applications", MinaRemoteFileService.listedChildPath("/C:/", "Applications"));
    }

    @Test
    void translatesWindowsVirtualDrivePathsOnlyAtTheSftpProtocolBoundary() {
        assertEquals("D:/Downloads/file.txt", MinaRemoteFileService.protocolPath("/D:/Downloads/file.txt"));
        assertEquals("D:/", MinaRemoteFileService.protocolPath("/D:/"));
        assertEquals("/var/data/file.txt", MinaRemoteFileService.protocolPath("/var/data/file.txt"));
        assertEquals("/D:/Downloads/file.txt", MinaRemoteFileService.displayPath("D:\\Downloads\\file.txt"));
        assertEquals("/var/data/file.txt", MinaRemoteFileService.displayPath("/var/data/file.txt"));
    }

    @Test
    void acceptsBothSftpMissingStatusesWhenProbingATransferTarget() {
        assertTrue(MinaRemoteFileService.isMissingStatus(SftpConstants.SSH_FX_NO_SUCH_FILE));
        assertTrue(MinaRemoteFileService.isMissingStatus(SftpConstants.SSH_FX_NO_SUCH_PATH));
        assertFalse(MinaRemoteFileService.isMissingStatus(SftpConstants.SSH_FX_PERMISSION_DENIED));
    }

    @Test
    void recursiveDeleteSnapshotsChildrenAndContinuesAfterIndividualFailure() {
        FakeDeleteAccess access = new FakeDeleteAccess();
        access.directory("/root", List.of("gone.txt", "blocked.txt", "later"));
        access.file("/root/gone.txt");
        access.file("/root/blocked.txt");
        access.directory("/root/later", List.of("child.txt"));
        access.file("/root/later/child.txt");
        access.missingOnRemove.add("/root/gone.txt");
        access.failOnRemove.add("/root/blocked.txt");

        List<MinaRemoteFileService.RemoteDeleteFailure> failures = new ArrayList<>();
        MinaRemoteFileService.deleteRemote(access, "/root", failures);

        assertEquals(List.of("/root/blocked.txt"), failures.stream()
                .map(MinaRemoteFileService.RemoteDeleteFailure::path).toList());
        assertEquals(1, access.childrenCalls.get("/root"));
        assertEquals(List.of("/root"), access.attributeAttempts);
        assertEquals(List.of(
                "/root/gone.txt", "/root/blocked.txt", "/root/later/child.txt"), access.removeAttempts);
        assertEquals(List.of("/root/later"), access.removedDirectories);
        assertFalse(access.removedDirectories.contains("/root"));
    }

    @Test
    void recursiveDeleteTreatsAlreadyMissingObjectAsDeleted() {
        FakeDeleteAccess access = new FakeDeleteAccess();
        access.missingOnAttributes.add("/gone");
        List<MinaRemoteFileService.RemoteDeleteFailure> failures = new ArrayList<>();

        MinaRemoteFileService.deleteRemote(access, "/gone", failures);

        assertEquals(List.of(), failures);
        assertEquals(List.of(), access.removeAttempts);
    }

    @Test
    void remoteReplaceUsesStandardAtomicOverwriteFromSftpVersionFive() throws Exception {
        FakeReplaceAccess access = new FakeReplaceAccess(5, true, true);

        MinaRemoteFileService.replaceRemoteFile(access, "/file.part", "/file");

        assertEquals(List.of("atomic:/file.part->/file"), access.operations);
    }

    @Test
    void remoteReplaceUsesOpenSshPosixRenameForOlderSftpVersions() throws Exception {
        FakeReplaceAccess access = new FakeReplaceAccess(3, true, true);

        MinaRemoteFileService.replaceRemoteFile(access, "/file.part", "/file");

        assertEquals(List.of("posix:/file.part->/file"), access.operations);
    }

    @Test
    void remoteReplaceWithoutExtensionBacksUpAndRemovesExistingTarget() throws Exception {
        FakeReplaceAccess access = new FakeReplaceAccess(3, false, true);

        MinaRemoteFileService.replaceRemoteFile(access, "/file.part", "/file");

        assertEquals(3, access.operations.size());
        String backup = operationTarget(access.operations.get(0));
        assertTrue(backup.startsWith("/file.fastremote-"));
        assertTrue(backup.endsWith(".bak"));
        assertEquals("rename:/file->" + backup, access.operations.get(0));
        assertEquals("rename:/file.part->/file", access.operations.get(1));
        assertEquals("remove:" + backup, access.operations.get(2));
    }

    @Test
    void remoteReplaceWithoutExtensionRestoresOriginalWhenInstallFails() {
        FakeReplaceAccess access = new FakeReplaceAccess(3, false, true);
        access.failInstall = true;

        IOException failure = assertThrows(IOException.class,
                () -> MinaRemoteFileService.replaceRemoteFile(access, "/file.part", "/file"));

        assertEquals("install failed", failure.getMessage());
        assertEquals(3, access.operations.size());
        String backup = operationTarget(access.operations.get(0));
        assertEquals("rename:/file.part->/file", access.operations.get(1));
        assertEquals("rename:" + backup + "->/file", access.operations.get(2));
    }

    @Test
    void remoteReplacePreservesRollbackFailureAsSuppressedContext() {
        FakeReplaceAccess access = new FakeReplaceAccess(3, false, true);
        access.failInstall = true;
        access.failRollback = true;

        IOException failure = assertThrows(IOException.class,
                () -> MinaRemoteFileService.replaceRemoteFile(access, "/file.part", "/file"));

        assertEquals("install failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("rollback failed", failure.getSuppressed()[0].getMessage());
    }

    @Test
    void remoteReplaceReportsThatContentChangedWhenBackupCleanupFails() {
        FakeReplaceAccess access = new FakeReplaceAccess(3, false, true);
        access.failRemove = true;

        IOException failure = assertThrows(IOException.class,
                () -> MinaRemoteFileService.replaceRemoteFile(access, "/file.part", "/file"));

        assertTrue(failure.getMessage().startsWith(
                "Remote file was updated, but its compatibility backup could not be removed:"));
        assertEquals("cleanup failed", failure.getCause().getMessage());
    }

    @Test
    void remoteReplaceWithoutExistingTargetNeedsOnlyAPlainRename() throws Exception {
        FakeReplaceAccess access = new FakeReplaceAccess(3, false, false);

        MinaRemoteFileService.replaceRemoteFile(access, "/file.part", "/file");

        assertEquals(List.of("rename:/file.part->/file"), access.operations);
    }

    private static String operationTarget(String operation) {
        return operation.substring(operation.indexOf("->") + 2);
    }

    private static final class FakeDeleteAccess implements MinaRemoteFileService.RemoteDeleteAccess {
        private final Map<String, MinaRemoteFileService.RemoteDeleteAttributes> attributes = new LinkedHashMap<>();
        private final Map<String, List<MinaRemoteFileService.RemoteDeleteChild>> children = new LinkedHashMap<>();
        private final Map<String, Integer> childrenCalls = new LinkedHashMap<>();
        private final List<String> missingOnAttributes = new ArrayList<>();
        private final List<String> missingOnRemove = new ArrayList<>();
        private final List<String> failOnRemove = new ArrayList<>();
        private final List<String> removeAttempts = new ArrayList<>();
        private final List<String> removedDirectories = new ArrayList<>();
        private final List<String> attributeAttempts = new ArrayList<>();

        void directory(String path, List<String> entries) {
            attributes.put(path, new MinaRemoteFileService.RemoteDeleteAttributes(true, false));
            children.put(path, entries.stream().map(name -> new MinaRemoteFileService.RemoteDeleteChild(
                    name, attributes.getOrDefault(path + "/" + name,
                            new MinaRemoteFileService.RemoteDeleteAttributes(false, false)))).toList());
        }

        void file(String path) {
            attributes.put(path, new MinaRemoteFileService.RemoteDeleteAttributes(false, false));
        }

        @Override
        public MinaRemoteFileService.RemoteDeleteAttributes attributes(String path) throws IOException {
            attributeAttempts.add(path);
            if (missingOnAttributes.contains(path)) throw new MissingFileException();
            MinaRemoteFileService.RemoteDeleteAttributes value = attributes.get(path);
            if (value == null) throw new IOException("unexpected path " + path);
            return value;
        }

        @Override
        public List<MinaRemoteFileService.RemoteDeleteChild> children(String path) {
            childrenCalls.merge(path, 1, Integer::sum);
            return children.get(path).stream().map(child -> new MinaRemoteFileService.RemoteDeleteChild(
                    child.name(), attributes.getOrDefault(path + "/" + child.name(), child.attributes()))).toList();
        }

        @Override
        public void remove(String path) throws IOException {
            removeAttempts.add(path);
            if (missingOnRemove.contains(path)) throw new MissingFileException();
            if (failOnRemove.contains(path)) throw new IOException("Permission denied");
        }

        @Override
        public void removeDirectory(String path) {
            removedDirectories.add(path);
        }

        @Override
        public boolean isMissing(IOException failure) {
            return failure instanceof MissingFileException;
        }
    }

    private static final class MissingFileException extends IOException { }

    private static final class FakeReplaceAccess implements MinaRemoteFileService.RemoteReplaceAccess {
        private final int version;
        private final boolean posixRename;
        private final boolean targetExists;
        private final List<String> operations = new ArrayList<>();
        private boolean failInstall;
        private boolean failRollback;
        private boolean failRemove;

        private FakeReplaceAccess(int version, boolean posixRename, boolean targetExists) {
            this.version = version;
            this.posixRename = posixRename;
            this.targetExists = targetExists;
        }

        @Override public int version() { return version; }
        @Override public boolean supportsPosixRename() { return posixRename; }
        @Override public boolean exists(String path) { return targetExists; }
        @Override public void atomicOverwrite(String source, String target) {
            operations.add("atomic:" + source + "->" + target);
        }
        @Override public void posixRename(String source, String target) {
            operations.add("posix:" + source + "->" + target);
        }
        @Override public void rename(String source, String target) throws IOException {
            operations.add("rename:" + source + "->" + target);
            if (failInstall && source.equals("/file.part") && target.equals("/file")) {
                throw new IOException("install failed");
            }
            if (failRollback && source.contains(".fastremote-") && source.endsWith(".bak")) {
                throw new IOException("rollback failed");
            }
        }
        @Override public void remove(String path) throws IOException {
            operations.add("remove:" + path);
            if (failRemove) throw new IOException("cleanup failed");
        }
    }
}
