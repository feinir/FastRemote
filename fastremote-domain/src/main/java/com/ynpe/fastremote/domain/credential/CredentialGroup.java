// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.credential;

import com.ynpe.fastremote.domain.catalog.GroupColor;
import java.util.Objects;

public record CredentialGroup(CredentialGroupId id, String name, int sortOrder, GroupColor color) {
    public CredentialGroup {
        Objects.requireNonNull(id, "id");
        name = Objects.requireNonNull(name, "name").strip();
        Objects.requireNonNull(color, "color");
        if (name.isEmpty() || name.length() > 255) throw new IllegalArgumentException("凭证分组名称长度必须为 1 到 255");
        if (sortOrder < 0) throw new IllegalArgumentException("sortOrder must not be negative");
    }
}
