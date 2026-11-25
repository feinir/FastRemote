// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.i18n.Texts;
import java.util.Locale;

/** Supplies one platform-specific label for every local file-manager reveal action. */
final class PlatformFileManagerLabel {
    private PlatformFileManagerLabel() { }

    static String reveal() {
        return Texts.text(revealKey(System.getProperty("os.name", "")));
    }

    static String revealKey(String osName) {
        String normalized = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (normalized.contains("mac")) return "legacy.java.1280364b7f8430c6";
        if (normalized.contains("win")) return "legacy.java.9e3fc11213b90ed0";
        return "legacy.java.7959616dd346227d";
    }
}
