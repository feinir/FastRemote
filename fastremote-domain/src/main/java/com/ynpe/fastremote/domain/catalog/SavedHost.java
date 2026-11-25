// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.catalog;

import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.vault.SecretId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record SavedHost(
        HostId id,
        Optional<GroupId> groupId,
        String name,
        ConnectionProfile profile,
        Optional<HostId> jumpHostId,
        Optional<SecretId> credentialSecretId,
        boolean favorite,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt,
        HostIcon icon) {

    public SavedHost {
        Objects.requireNonNull(id, "id");
        groupId = Objects.requireNonNull(groupId, "groupId");
        name = Objects.requireNonNull(name, "name").strip();
        Objects.requireNonNull(profile, "profile");
        jumpHostId = Objects.requireNonNull(jumpHostId, "jumpHostId");
        if (jumpHostId.equals(Optional.of(id))) {
            throw new IllegalArgumentException("A host cannot use itself as a jump host");
        }
        credentialSecretId = Objects.requireNonNull(credentialSecretId, "credentialSecretId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(icon, "icon");
        if (name.isEmpty() || name.length() > 255) {
            throw new IllegalArgumentException("Host name must contain 1 to 255 characters");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Updated time cannot precede creation time");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("Host sort order must not be negative");
        }
    }

    public SavedHost(
            HostId id,
            Optional<GroupId> groupId,
            String name,
            ConnectionProfile profile,
            Optional<SecretId> credentialSecretId,
            boolean favorite,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt) {
        this(id, groupId, name, profile, Optional.empty(), credentialSecretId, favorite, sortOrder,
                createdAt, updatedAt, HostIcon.SERVER);
    }

    public SavedHost(
            HostId id,
            Optional<GroupId> groupId,
            String name,
            ConnectionProfile profile,
            Optional<SecretId> credentialSecretId,
            boolean favorite,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt,
            HostIcon icon) {
        this(id, groupId, name, profile, Optional.empty(), credentialSecretId, favorite, sortOrder,
                createdAt, updatedAt, icon);
    }

    public static SavedHost create(String name, Optional<GroupId> groupId, ConnectionProfile profile) {
        return create(name, groupId, profile, 0);
    }

    public static SavedHost create(
            String name, Optional<GroupId> groupId, ConnectionProfile profile, int sortOrder) {
        Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
        return new SavedHost(
                HostId.create(), groupId, name, profile, Optional.empty(), Optional.empty(), false,
                sortOrder, now, now, HostIcon.SERVER);
    }

    public static SavedHost create(
            String name, Optional<GroupId> groupId, ConnectionProfile profile, int sortOrder, HostIcon icon) {
        Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
        return new SavedHost(
                HostId.create(), groupId, name, profile, Optional.empty(), Optional.empty(), false,
                sortOrder, now, now, icon);
    }

    public SavedHost withCredential(Optional<SecretId> secretId) {
        return new SavedHost(
                id,
                groupId,
                name,
                profile,
                jumpHostId,
                secretId,
                favorite,
                sortOrder,
                createdAt,
                Instant.ofEpochMilli(System.currentTimeMillis()),
                icon);
    }

    public SavedHost withLocation(Optional<GroupId> newGroupId, int newSortOrder) {
        return new SavedHost(
                id,
                Objects.requireNonNull(newGroupId, "newGroupId"),
                name,
                profile,
                jumpHostId,
                credentialSecretId,
                favorite,
                newSortOrder,
                createdAt,
                Instant.ofEpochMilli(System.currentTimeMillis()),
                icon);
    }

    public SavedHost withFavorite(boolean newFavorite) {
        return new SavedHost(
                id,
                groupId,
                name,
                profile,
                jumpHostId,
                credentialSecretId,
                newFavorite,
                sortOrder,
                createdAt,
                Instant.ofEpochMilli(System.currentTimeMillis()),
                icon);
    }

    public SavedHost withIcon(HostIcon newIcon) {
        return new SavedHost(
                id, groupId, name, profile, jumpHostId, credentialSecretId, favorite, sortOrder, createdAt,
                Instant.ofEpochMilli(System.currentTimeMillis()), Objects.requireNonNull(newIcon, "newIcon"));
    }

    public SavedHost withJumpHost(Optional<HostId> newJumpHostId) {
        return new SavedHost(
                id, groupId, name, profile, Objects.requireNonNull(newJumpHostId, "newJumpHostId"),
                credentialSecretId, favorite, sortOrder, createdAt,
                Instant.ofEpochMilli(System.currentTimeMillis()), icon);
    }
}
