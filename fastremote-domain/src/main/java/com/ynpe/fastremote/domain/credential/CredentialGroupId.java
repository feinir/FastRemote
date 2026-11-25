// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.credential;

import java.util.Objects;
import java.util.UUID;

public record CredentialGroupId(UUID value) {
    public CredentialGroupId { Objects.requireNonNull(value, "value"); }
    public static CredentialGroupId random() { return new CredentialGroupId(UUID.randomUUID()); }
    public static CredentialGroupId parse(String value) { return new CredentialGroupId(UUID.fromString(value)); }
    @Override public String toString() { return value.toString(); }
}
