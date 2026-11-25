// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Non-secret local file metadata produced by the bounded file workflow. */
public record LocalFileEntry(
        String name,
        Path path,
        boolean directory,
        boolean symbolicLink,
        long size,
        Optional<Instant> modifiedAt,
        Optional<Instant> createdAt,
        boolean hidden) {
    public LocalFileEntry(String name, Path path, boolean directory, boolean symbolicLink,
            long size, Optional<Instant> modifiedAt, boolean hidden) {
        this(name, path, directory, symbolicLink, size, modifiedAt, Optional.empty(), hidden);
    }

    public LocalFileEntry(String name, Path path, boolean directory, boolean symbolicLink,
            long size, Optional<Instant> modifiedAt) {
        this(name, path, directory, symbolicLink, size, modifiedAt, Optional.empty(), name.startsWith("."));
    }
    public LocalFileEntry {
        name = Objects.requireNonNull(name, "name");
        path = Objects.requireNonNull(path, "path");
        modifiedAt = Objects.requireNonNull(modifiedAt, "modifiedAt");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (name.isBlank() || size < 0) {
            throw new IllegalArgumentException("Local file metadata is invalid");
        }
    }
}
