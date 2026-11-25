// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Credentials;
import com.ynpe.fastremote.domain.connection.SessionConnector;
import com.ynpe.fastremote.domain.profile.ConnectionProfileRepository;
import com.ynpe.fastremote.domain.security.HostKeyPrompt;
import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

class SshConnectionWorkflowLocalFilesTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void listsLocalDirectoriesBeforeFilesWithoutBlockingTheFxController() throws Exception {
        Files.writeString(directory.resolve("zeta.txt"), "content");
        Files.createDirectory(directory.resolve("Alpha"));
        var workflow = new SshConnectionWorkflow(new UnusedConnector(), new EmptyProfiles());
        try {
            var entries = workflow.listLocal(directory).toCompletableFuture().get();

            assertEquals(2, entries.size());
            assertEquals("Alpha", entries.getFirst().name());
            assertTrue(entries.getFirst().directory());
            assertEquals("zeta.txt", entries.getLast().name());
            assertEquals(7L, entries.getLast().size());
            assertTrue(entries.getLast().createdAt().isPresent());
        } finally {
            workflow.close();
        }
    }

    @Test
    void inspectsOnlyTheFilesSelectedForUpload() throws Exception {
        Path selected = Files.writeString(directory.resolve("selected.txt"), "upload");
        Files.writeString(directory.resolve("unselected.txt"), "other");
        try (var workflow = new SshConnectionWorkflow(new UnusedConnector(), new EmptyProfiles())) {
            var entries = workflow.inspectLocalEntries(java.util.List.of(selected)).toCompletableFuture().get();
            assertEquals(1, entries.size());
            assertEquals(selected, entries.getFirst().path());
            assertEquals("selected.txt", entries.getFirst().name());
            assertEquals(6, entries.getFirst().size());
        }
    }

    @Test
    void createsEveryLocalRootWithoutProbingUnavailableDrives() {
        Path available = directory.resolve("available-root");
        Path unavailable = directory.resolve("unavailable-root");

        var roots = SshConnectionWorkflow.localRootEntries(java.util.List.of(available, unavailable));

        assertEquals(2, roots.size());
        assertEquals(available.toAbsolutePath().normalize(), roots.get(0).path());
        assertEquals(unavailable.toAbsolutePath().normalize(), roots.get(1).path());
        assertTrue(roots.stream().allMatch(com.ynpe.fastremote.ui.LocalFileEntry::directory));
        assertTrue(roots.stream().allMatch(entry -> entry.modifiedAt().isEmpty()));
    }

    @Test
    void identifiesSymbolicLinksWithoutFollowingOrRecursivelyScanningThem() throws Exception {
        Path target = Files.writeString(directory.resolve("target.txt"), "content");
        Path link = directory.resolve("target-link");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | IOException unavailable) {
            Assumptions.abort("Symbolic links are unavailable on this file system");
        }
        var workflow = new SshConnectionWorkflow(new UnusedConnector(), new EmptyProfiles());
        try {
            var symbolic = workflow.listLocal(directory).toCompletableFuture().get().stream()
                    .filter(entry -> entry.name().equals("target-link"))
                    .findFirst().orElseThrow();
            assertTrue(symbolic.symbolicLink());
            assertTrue(!symbolic.directory());
        } finally {
            workflow.close();
        }
    }

    @Test
    void readsWithHardLimitAndAtomicallyReplacesLocalEditorFiles() throws Exception {
        Path file = Files.writeString(directory.resolve("notes.txt"), "before");
        var workflow = new SshConnectionWorkflow(new UnusedConnector(), new EmptyProfiles());
        try {
            assertArrayEquals("before".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    workflow.readLocalFile(file, 6).toCompletableFuture().get());
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> workflow.readLocalFile(file, 5).toCompletableFuture().get());
            workflow.writeLocalFileAtomic(file, "after".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toCompletableFuture().get();
            assertEquals("after", Files.readString(file));
        } finally {
            workflow.close();
        }
    }

    @Test
    void createsAndDeletesRemoteEditorUploadSnapshot() throws Exception {
        var workflow = new SshConnectionWorkflow(new UnusedConnector(), new EmptyProfiles());
        Path temporary = null;
        try {
            byte[] content = "上传快照".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            temporary = workflow.createEditorTemporaryFile(content).toCompletableFuture().get();
            assertTrue(Files.isRegularFile(temporary));
            assertArrayEquals(content, Files.readAllBytes(temporary));

            workflow.deleteEditorTemporaryFile(temporary).toCompletableFuture().get();
            assertTrue(Files.notExists(temporary));
        } finally {
            if (temporary != null) Files.deleteIfExists(temporary);
            workflow.close();
        }
    }

    @Test
    void copiesDirectoriesAndMovesFilesForLocalWorkspaceClipboard() throws Exception {
        Path sourceDirectory = Files.createDirectory(directory.resolve("source"));
        Files.writeString(sourceDirectory.resolve("nested.txt"), "nested");
        Path moveSource = Files.writeString(directory.resolve("move.txt"), "move");
        var workflow = new SshConnectionWorkflow(new UnusedConnector(), new EmptyProfiles());
        try {
            Path copied = directory.resolve("copied");
            workflow.copyLocal(sourceDirectory, copied, false).toCompletableFuture().get();
            assertEquals("nested", Files.readString(copied.resolve("nested.txt")));

            Path moved = directory.resolve("moved.txt");
            workflow.moveLocal(moveSource, moved, false).toCompletableFuture().get();
            assertTrue(Files.notExists(moveSource));
            assertEquals("move", Files.readString(moved));
        } finally {
            workflow.close();
        }
    }

    @Test
    void rejectsCopyingDirectoryIntoItsOwnSubtree() throws Exception {
        Path source = Files.createDirectory(directory.resolve("source"));
        var workflow = new SshConnectionWorkflow(new UnusedConnector(), new EmptyProfiles());
        try {
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> workflow.copyLocal(source, source.resolve("child"), false)
                            .toCompletableFuture().get());
        } finally {
            workflow.close();
        }
    }

    @Test
    void movesDirectoryWithContentsAndRejectsExistingFileAndDirectoryTargets() throws Exception {
        Path source = Files.createDirectory(directory.resolve("source"));
        Files.writeString(source.resolve("nested.txt"), "source content");
        Path target = Files.createDirectory(directory.resolve("target"));
        var workflow = new SshConnectionWorkflow(new UnusedConnector(), new EmptyProfiles());
        try {
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> workflow.moveLocal(source, target, false).toCompletableFuture().get());
            assertEquals("source content", Files.readString(source.resolve("nested.txt")));
            try (var contents = Files.list(target)) { assertTrue(contents.findAny().isEmpty()); }
            workflow.moveLocal(source, target.resolve("source"), false).toCompletableFuture().get();
            assertTrue(Files.notExists(source));
            assertEquals("source content", Files.readString(target.resolve("source/nested.txt")));
            Path first = Files.writeString(directory.resolve("a.txt"), "first");
            Path second = Files.writeString(directory.resolve("b.txt"), "second");
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> workflow.moveLocal(first, second, false).toCompletableFuture().get());
            assertEquals("first", Files.readString(first));
            assertEquals("second", Files.readString(second));
        } finally { workflow.close(); }
    }

    @Test
    void rejectsMovingDirectoryIntoItsSubtreeThroughASymlink() throws Exception {
        Path source = Files.createDirectory(directory.resolve("source"));
        Path child = Files.createDirectory(source.resolve("child"));
        Path alias = directory.resolve("alias");
        try { Files.createSymbolicLink(alias, child); }
        catch (UnsupportedOperationException | IOException unavailable) {
            Assumptions.abort("Symbolic links are unavailable on this file system");
        }
        var workflow = new SshConnectionWorkflow(new UnusedConnector(), new EmptyProfiles());
        try {
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> workflow.moveLocal(source, alias.resolve("source"), false).toCompletableFuture().get());
            assertTrue(Files.isDirectory(source));
            assertTrue(Files.notExists(child.resolve("source")));
        } finally { workflow.close(); }
    }

    private static final class UnusedConnector implements SessionConnector {
        @Override
        public TerminalConnection connect(
                ConnectionProfile profile, Credentials credentials, HostKeyPrompt prompt) {
            throw new AssertionError("Connection is not used by this test");
        }

        @Override
        public void close() {
        }
    }

    private static final class EmptyProfiles implements ConnectionProfileRepository {
        @Override
        public Optional<ConnectionProfile> loadLastProfile() {
            return Optional.empty();
        }

        @Override
        public void saveLastProfile(ConnectionProfile profile) throws IOException {
        }
    }
}
