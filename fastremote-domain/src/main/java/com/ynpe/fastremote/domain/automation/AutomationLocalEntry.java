// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import java.util.Objects;

/** Non-secret metadata for one entry inside an explicitly allowed local automation root. */
public record AutomationLocalEntry(String name, String path, boolean directory, long size) {
    public AutomationLocalEntry {
        name = Objects.requireNonNull(name, "name");
        path = Objects.requireNonNull(path, "path");
        if (name.isBlank() || path.isBlank()) throw new IllegalArgumentException("Local entry requires a name and path");
        if (size < 0) throw new IllegalArgumentException("Local entry size cannot be negative");
    }
}
