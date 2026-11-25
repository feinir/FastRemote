// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import java.util.List;
import java.util.Objects;

/** Bounded local directory result exposed only through the automation gateway. */
public record AutomationLocalListing(String directory, List<AutomationLocalEntry> entries, boolean truncated) {
    public AutomationLocalListing {
        directory = Objects.requireNonNull(directory, "directory");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }
}
