// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;

/** Presentation-ready, non-sensitive summary collected from the connected server. */
public record ServerOverview(String text, SystemFamily systemFamily) {
    public enum SystemFamily { WINDOWS, POSIX, UNKNOWN }

    public ServerOverview(String text) {
        this(text, SystemFamily.UNKNOWN);
    }

    public ServerOverview {
        text = Objects.requireNonNull(text, "text").strip();
        systemFamily = Objects.requireNonNull(systemFamily, "systemFamily");
    }
}
