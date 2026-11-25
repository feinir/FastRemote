// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.transfer;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Portable metadata exposed by the remote-file port without leaking SFTP implementation types. */
public record RemoteFileAttributes(
        String name,
        String path,
        boolean directory,
        boolean symbolicLink,
        long size,
        Optional<Instant> modifiedAt,
        String owner,
        String group,
        int permissions,
        Optional<String> symbolicLinkTarget) {
    public RemoteFileAttributes(String name, String path, boolean directory, boolean symbolicLink,
            long size, Optional<Instant> modifiedAt, String owner, String group, int permissions) {
        this(name, path, directory, symbolicLink, size, modifiedAt, owner, group, permissions, Optional.empty());
    }

    public RemoteFileAttributes {
        name = Objects.requireNonNull(name, "name");
        path = Objects.requireNonNull(path, "path");
        modifiedAt = Objects.requireNonNull(modifiedAt, "modifiedAt");
        owner = Objects.requireNonNull(owner, "owner");
        group = Objects.requireNonNull(group, "group");
        symbolicLinkTarget = Objects.requireNonNull(symbolicLinkTarget, "symbolicLinkTarget");
        if (symbolicLinkTarget.isPresent() && (!symbolicLink || symbolicLinkTarget.orElseThrow().isBlank())) {
            throw new IllegalArgumentException("Symbolic link target is invalid");
        }
        if (name.isBlank() || path.isBlank() || size < 0 || permissions < 0 || permissions > 07777) {
            throw new IllegalArgumentException("Remote file attributes are invalid");
        }
    }
}
