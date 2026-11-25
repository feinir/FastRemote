// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.transfer;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record RemoteFileEntry(
        String name,
        String path,
        boolean directory,
        boolean symbolicLink,
        long size,
        Optional<Instant> modifiedAt) {
    public RemoteFileEntry {
        name = Objects.requireNonNull(name, "name");
        path = Objects.requireNonNull(path, "path");
        modifiedAt = Objects.requireNonNull(modifiedAt, "modifiedAt");
        if (name.isBlank() || path.isBlank() || size < 0) {
            throw new IllegalArgumentException("Remote file metadata is invalid");
        }
    }
}
