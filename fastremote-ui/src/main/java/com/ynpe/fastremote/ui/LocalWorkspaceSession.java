// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javafx.scene.control.Tab;

/** One-to-one association between a local terminal and its local file workspace. */
final class LocalWorkspaceSession {
    private final UUID id;
    private Tab terminalTab;
    private Tab filesTab;
    private Path lastDirectory;

    LocalWorkspaceSession(Path initialDirectory) {
        this(UUID.randomUUID(), initialDirectory);
    }

    LocalWorkspaceSession(UUID id, Path initialDirectory) {
        this.id = Objects.requireNonNull(id, "id");
        lastDirectory = normalize(initialDirectory);
    }

    UUID id() { return id; }
    Optional<Tab> terminalTab() { return Optional.ofNullable(terminalTab); }
    Optional<Tab> filesTab() { return Optional.ofNullable(filesTab); }
    Optional<Path> lastDirectory() { return Optional.ofNullable(lastDirectory); }
    void terminalTab(Tab value) { terminalTab = value; }
    void filesTab(Tab value) { filesTab = value; }
    void directory(Path value) { lastDirectory = normalize(value); }
    boolean empty() { return terminalTab == null && filesTab == null; }

    private static Path normalize(Path value) {
        return value == null ? null : value.toAbsolutePath().normalize();
    }
}
