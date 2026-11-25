// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class FilePanelMovesTest {
    @Test
    void localFilesAndDirectoriesMoveOnlyIntoOtherDirectories() {
        Path base = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("panel");
        var file = local(base.resolve("a.txt"), false);
        var folder = local(base.resolve("folder"), true);
        assertTrue(FilePanelMoves.localAllowed(file, base.resolve("test")));
        assertTrue(FilePanelMoves.localAllowed(folder, base.resolve("test")));
        assertFalse(FilePanelMoves.localAllowed(file, base));
        assertFalse(FilePanelMoves.localAllowed(folder, folder.path()));
        assertFalse(FilePanelMoves.localAllowed(folder, folder.path().resolve("child")));
        assertTrue(FilePanelMoves.localAllowed(folder, base.resolve("folder2")));
        assertFalse(FilePanelMoves.localAllowed(local(base.getRoot(), true), base));
        assertNull(FilePanelMoves.localTarget(file, base));
        assertEquals(folder.path(), FilePanelMoves.localTarget(folder, base));
        assertEquals(base, FilePanelMoves.localTarget(null, base));
    }

    @Test
    void remotePolicyRejectsRootsSelfDescendantsNavigationAndUnsafeNames() {
        var folder = remote("/root/folder", true);
        assertTrue(FilePanelMoves.remoteAllowed(remote("/root/a.txt", false), "/root/test"));
        assertTrue(FilePanelMoves.remoteAllowed(folder, "/root/folder2"));
        for (String target : List.of("/root", "/root/folder", "/root/folder/sub", "/root/other/../folder/sub")) {
            assertFalse(FilePanelMoves.remoteAllowed(folder, target), target);
        }
        assertFalse(FilePanelMoves.remoteAllowed(remote("/", true), "/tmp"));
        assertFalse(FilePanelMoves.remoteAllowed(remote("/C:/", true), "/D:/test"));
        assertFalse(FilePanelMoves.remoteAllowed(new RemoteFileEntry("..", "/root", true, false,
                0, Optional.empty()), "/tmp"));
        assertFalse(FilePanelMoves.remoteAllowed(new RemoteFileEntry("../escape", "/root/evil", false, false,
                0, Optional.empty()), "/tmp"));
        assertNull(FilePanelMoves.remoteTarget(remote("/root/a.txt", false), "/root"));
        assertEquals("/root/folder", FilePanelMoves.remoteTarget(folder, "/root"));
        assertEquals("/root", FilePanelMoves.remoteTarget(null, "/root"));
        assertTrue(FilePanelMoves.remoteAllowed(remote("/C:/source", true), "D:/target"));
    }

    @Test
    void movesRemoteFileAndDirectoryUsingRenameWithoutTransferringTheirContents() {
        List<String> calls = new ArrayList<>();
        var files = files(calls, Map.of(), List.of(), null);
        FilePanelMoves.moveRemote(files, remote("/root/a.txt", false), "/root/test").toCompletableFuture().join();
        FilePanelMoves.moveRemote(files, remote("/root/folder", true), "/root/test").toCompletableFuture().join();
        assertEquals(List.of("canonical:/root/test", "list:/root/test", "rename:/root/a.txt->/root/test/a.txt",
                "canonical:/root/test", "canonical:/root/folder", "list:/root/test",
                "rename:/root/folder->/root/test/folder"), calls);
    }

    @Test
    void remoteCollisionLeavesSourceAndTargetUntouched() {
        List<String> calls = new ArrayList<>();
        var files = files(calls, Map.of(), List.of(remote("/root/test/a.txt", false)), null);
        var error = assertThrows(CompletionException.class, () -> FilePanelMoves.moveRemote(
                files, remote("/root/a.txt", false), "/root/test").toCompletableFuture().join());
        assertInstanceOf(FileAlreadyExistsException.class, error.getCause());
        assertEquals(List.of("canonical:/root/test", "list:/root/test"), calls);
    }

    @Test
    void remoteCanonicalDirectoryPreventsSymlinkAliasIntoOwnSubtree() {
        List<String> calls = new ArrayList<>();
        var files = files(calls, Map.of("/alias", "/root/folder/sub"), List.of(), null);
        assertThrows(CompletionException.class, () -> FilePanelMoves.moveRemote(
                files, remote("/root/folder", true), "/alias").toCompletableFuture().join());
        assertEquals(List.of("canonical:/alias", "canonical:/root/folder"), calls);
    }

    @Test
    void remoteFailureDoesNotAttemptDeleteOrCopyFallback() {
        List<String> calls = new ArrayList<>();
        IOException denied = new IOException("Permission denied");
        var files = files(calls, Map.of(), List.of(), denied);
        var error = assertThrows(CompletionException.class, () -> FilePanelMoves.moveRemote(
                files, remote("/root/a.txt", false), "/root/test").toCompletableFuture().join());
        assertSame(denied, error.getCause());
        assertEquals(3, calls.size());
    }

    @Test
    void movingBrowsedDirectoryKeepsBrowserInsideRelocatedDirectory() {
        Path base = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
        var source = local(base.resolve("folder"), true);
        assertEquals(base.resolve("test/folder/sub"), FilePanelMoves.localLocationAfterMove(
                base.resolve("folder/sub"), source, base.resolve("test")));
        assertEquals(base.resolve("folder2"), FilePanelMoves.localLocationAfterMove(
                base.resolve("folder2"), source, base.resolve("test")));
        assertEquals("/root/test/folder/sub", FilePanelMoves.remoteLocationAfterMove(
                "/root/folder/sub", remote("/root/folder", true), "/root/test"));
        assertEquals("/root/folder2", FilePanelMoves.remoteLocationAfterMove(
                "/root/folder2", remote("/root/folder", true), "/root/test"));
    }

    private static LocalFileEntry local(Path path, boolean directory) {
        return new LocalFileEntry(path.getFileName() == null ? path.toString() : path.getFileName().toString(),
                path, directory, false, 0, Optional.empty());
    }

    private static RemoteFileEntry remote(String path, boolean directory) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return new RemoteFileEntry(name.isEmpty() ? path : name, path, directory, false, 0, Optional.empty());
    }

    private static RemoteFilesHandle files(List<String> calls, Map<String, String> canonical,
            List<RemoteFileEntry> entries, Throwable renameFailure) {
        return (RemoteFilesHandle) Proxy.newProxyInstance(RemoteFilesHandle.class.getClassLoader(),
                new Class<?>[] {RemoteFilesHandle.class}, (proxy, method, arguments) -> {
                    String source = (String) arguments[0];
                    return switch (method.getName()) {
                        case "canonicalPath" -> {
                            calls.add("canonical:" + source);
                            yield CompletableFuture.completedFuture(canonical.getOrDefault(source, source));
                        }
                        case "list" -> {
                            calls.add("list:" + source);
                            yield CompletableFuture.completedFuture(entries);
                        }
                        case "rename" -> {
                            calls.add("rename:" + source + "->" + arguments[1]);
                            yield renameFailure == null ? CompletableFuture.completedFuture(null)
                                    : CompletableFuture.failedFuture(renameFailure);
                        }
                        default -> throw new AssertionError("Unexpected operation: " + method.getName());
                    };
                });
    }
}
