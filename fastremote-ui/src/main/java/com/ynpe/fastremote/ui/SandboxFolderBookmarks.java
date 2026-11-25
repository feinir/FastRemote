// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** App-scoped access to folders selected through the system file picker. */
public interface SandboxFolderBookmarks {
    SandboxFolderBookmarks UNAVAILABLE = new SandboxFolderBookmarks() {
        @Override public void remember(Path folder) throws IOException {
            throw new IOException("Persistent folder access is unavailable");
        }

        @Override public List<Path> folders() { return List.of(); }
    };

    void remember(Path folder) throws IOException;

    List<Path> folders();

    default boolean covers(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return folders().stream().anyMatch(normalized::startsWith);
    }

    default Path initialFolder(Path home) {
        return covers(home) ? home : folders().stream().findFirst().orElse(home);
    }
}
