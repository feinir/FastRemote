// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.credential;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.vault.SecretId;
import java.util.Objects;
import java.util.Optional;

/** Login identity metadata; sensitive material is held by the global vault. */
public record GlobalCredential(
        SecretId id,
        String label,
        String username,
        AuthenticationType authenticationType,
        CredentialScope scope,
        Optional<HostId> ownerHostId,
        Optional<CredentialGroupId> groupId,
        int sortOrder) {
    public GlobalCredential {
        Objects.requireNonNull(id, "id");
        label = Objects.requireNonNull(label, "label").strip();
        username = Objects.requireNonNull(username, "username").strip();
        Objects.requireNonNull(authenticationType, "authenticationType");
        Objects.requireNonNull(scope, "scope");
        ownerHostId = Objects.requireNonNull(ownerHostId, "ownerHostId");
        groupId = Objects.requireNonNull(groupId, "groupId");
        if (label.isEmpty() || label.length() > 255) {
            throw new IllegalArgumentException("Credential label must contain 1 to 255 characters");
        }
        if (username.length() > 255) {
            throw new IllegalArgumentException("Credential username must not exceed 255 characters");
        }
        if ((scope == CredentialScope.HOST_BOUND) != ownerHostId.isPresent()) {
            throw new IllegalArgumentException("Host-bound credentials require exactly one owner host");
        }
        if (scope == CredentialScope.HOST_BOUND && groupId.isPresent()) {
            throw new IllegalArgumentException("Host-bound credentials cannot belong to a reusable group");
        }
        if (sortOrder < 0) throw new IllegalArgumentException("sortOrder must not be negative");
    }

    public GlobalCredential(SecretId id, String label, String username,
            AuthenticationType authenticationType, CredentialScope scope, Optional<HostId> ownerHostId) {
        this(id, label, username, authenticationType, scope, ownerHostId, Optional.empty(), 0);
    }

    public boolean embeddedPrivateKey() {
        return authenticationType == AuthenticationType.PRIVATE_KEY;
    }
}
