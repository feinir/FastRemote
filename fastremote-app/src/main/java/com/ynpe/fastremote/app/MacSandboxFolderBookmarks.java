// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.PointerByReference;
import com.ynpe.fastremote.ui.SandboxFolderBookmarks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps macOS folder grants active for both file browsing and background transfers. */
final class MacSandboxFolderBookmarks implements SandboxFolderBookmarks, AutoCloseable {
    private static final System.Logger LOG = System.getLogger(MacSandboxFolderBookmarks.class.getName());
    private static final long CREATE_WITH_SECURITY_SCOPE = 1L << 11;
    private static final long RESOLVE_WITHOUT_UI_OR_MOUNTING = (1L << 8) | (1L << 9);
    private static final long RESOLVE_WITH_SECURITY_SCOPE = 1L << 10;
    private static final String BOOKMARK_SUFFIX = ".bookmark";

    private final CoreFoundation cf = Native.load("CoreFoundation", CoreFoundation.class);
    private final Path directory;
    private final Map<Path, Pointer> active = new LinkedHashMap<>();

    MacSandboxFolderBookmarks(Path directory) {
        this.directory = directory;
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(BOOKMARK_SUFFIX))
                    .sorted().toList()) {
                try {
                    restore(file);
                } catch (IOException failure) {
                    // Leave the saved grant in place for a volume that is currently disconnected.
                    LOG.log(System.Logger.Level.DEBUG, "Could not restore a saved folder grant", failure);
                }
            }
        } catch (IOException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not read saved folder grants", failure);
        }
    }

    @Override public synchronized List<Path> folders() { return List.copyOf(active.keySet()); }

    @Override public synchronized void remember(Path folder) throws IOException {
        if (covers(folder)) return;
        byte[] path = folder.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8);
        Pointer url = cf.CFURLCreateFromFileSystemRepresentation(null, path, path.length, (byte) 1);
        if (url == null) throw new IOException("Could not create a folder URL");
        try {
            Files.createDirectories(directory);
            byte[] data = createBookmark(url);
            Path file = directory.resolve(java.util.UUID.randomUUID() + BOOKMARK_SUFFIX);
            writeBookmark(file, data);
            try {
                restore(file);
            } catch (IOException failure) {
                Files.deleteIfExists(file);
                throw failure;
            }
        } finally {
            cf.CFRelease(url);
        }
    }

    private void restore(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        Pointer data = cf.CFDataCreate(null, bytes, bytes.length);
        if (data == null) throw new IOException("Could not load bookmark data");
        ByteByReference stale = new ByteByReference();
        PointerByReference error = new PointerByReference();
        Pointer url;
        try {
            url = cf.CFURLCreateByResolvingBookmarkData(null, data,
                    RESOLVE_WITH_SECURITY_SCOPE | RESOLVE_WITHOUT_UI_OR_MOUNTING, null, null, stale, error);
        } finally {
            cf.CFRelease(data);
        }
        if (url == null) throw nativeFailure("Could not resolve the folder bookmark", error);

        boolean accessing = false;
        boolean keep = false;
        try {
            accessing = cf.CFURLStartAccessingSecurityScopedResource(url) != 0;
            if (!accessing) throw new IOException("macOS denied access to the saved folder");
            byte[] path = new byte[4096];
            if (cf.CFURLGetFileSystemRepresentation(url, (byte) 1, path, path.length) == 0) {
                throw new IOException("Could not read the bookmarked folder path");
            }
            Path folder = Path.of(Native.toString(path, StandardCharsets.UTF_8)).toAbsolutePath().normalize();
            if (!Files.isDirectory(folder)) throw new IOException("The bookmarked folder is unavailable");
            if (stale.getValue() != 0) {
                try {
                    writeBookmark(file, createBookmark(url));
                } catch (IOException failure) {
                    LOG.log(System.Logger.Level.WARNING, "Could not refresh a saved folder grant", failure);
                }
            }
            keep = active.putIfAbsent(folder, url) == null;
        } finally {
            if (!keep) {
                if (accessing) cf.CFURLStopAccessingSecurityScopedResource(url);
                cf.CFRelease(url);
            }
        }
    }

    private byte[] createBookmark(Pointer url) throws IOException {
        PointerByReference error = new PointerByReference();
        Pointer data = cf.CFURLCreateBookmarkData(null, url, CREATE_WITH_SECURITY_SCOPE, null, null, error);
        if (data == null) throw nativeFailure("Could not create the folder bookmark", error);
        try {
            int size = Math.toIntExact(cf.CFDataGetLength(data));
            return cf.CFDataGetBytePtr(data).getByteArray(0, size);
        } finally {
            cf.CFRelease(data);
        }
    }

    private static void writeBookmark(Path file, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), "folder-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private IOException nativeFailure(String message, PointerByReference error) {
        Pointer detail = error.getValue();
        if (detail == null) return new IOException(message);
        try {
            return new IOException(message + " (macOS error " + cf.CFErrorGetCode(detail) + ")");
        } finally {
            cf.CFRelease(detail);
        }
    }

    @Override public synchronized void close() {
        active.values().forEach(url -> {
            cf.CFURLStopAccessingSecurityScopedResource(url);
            cf.CFRelease(url);
        });
        active.clear();
    }

    private interface CoreFoundation extends Library {
        Pointer CFURLCreateFromFileSystemRepresentation(Pointer allocator, byte[] path, long length, byte directory);
        Pointer CFURLCreateBookmarkData(Pointer allocator, Pointer url, long options,
                Pointer properties, Pointer relativeUrl, PointerByReference error);
        Pointer CFURLCreateByResolvingBookmarkData(Pointer allocator, Pointer data, long options,
                Pointer relativeUrl, Pointer properties, ByteByReference stale, PointerByReference error);
        byte CFURLStartAccessingSecurityScopedResource(Pointer url);
        void CFURLStopAccessingSecurityScopedResource(Pointer url);
        byte CFURLGetFileSystemRepresentation(Pointer url, byte resolveAgainstBase, byte[] path, long capacity);
        Pointer CFDataCreate(Pointer allocator, byte[] bytes, long length);
        long CFDataGetLength(Pointer data);
        Pointer CFDataGetBytePtr(Pointer data);
        long CFErrorGetCode(Pointer error);
        void CFRelease(Pointer value);
    }
}
