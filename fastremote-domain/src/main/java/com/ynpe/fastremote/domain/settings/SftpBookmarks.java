// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import com.ynpe.fastremote.domain.transfer.RemotePathSyntax;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Ordered SFTP bookmark paths for one scope. */
public record SftpBookmarks(List<String> localPaths, List<String> remotePaths) {
    public static final int MAX_PATHS_PER_SIDE = 1_000;

    public SftpBookmarks {
        localPaths = validated(localPaths, "localPaths", false);
        remotePaths = validated(remotePaths, "remotePaths", true);
    }

    public static SftpBookmarks empty() {
        return new SftpBookmarks(List.of(), List.of());
    }

    private static List<String> validated(List<String> paths, String name, boolean remote) {
        Objects.requireNonNull(paths, name);
        if (paths.size() > MAX_PATHS_PER_SIDE) {
            throw new IllegalArgumentException(name + " contains too many paths");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String path : paths) {
            if (path == null || path.isBlank() || path.length() > 4_096) {
                throw new IllegalArgumentException(name + " contains an invalid path");
            }
            unique.add(remote ? RemotePathSyntax.normalizeBookmark(path) : path.strip());
        }
        return List.copyOf(unique);
    }
}
