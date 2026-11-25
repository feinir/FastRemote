// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import java.nio.file.Path;
import java.util.Locale;

public final class FastRemotePaths {
    public static final String DATA_DIRECTORY_PROPERTY = "fastremote.dataDirectory";

    private FastRemotePaths() {
    }

    public static Path applicationDataDirectory() {
        String configured = System.getProperty(DATA_DIRECTORY_PROPERTY, "").strip();
        if (!configured.isEmpty()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return userDataDirectory();
    }

    public static Path userDataDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", "FastRemote");
        }
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, "FastRemote");
            }
            return Path.of(System.getProperty("user.home"), "AppData", "Roaming", "FastRemote");
        }
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome != null && !xdgDataHome.isBlank()) {
            return Path.of(xdgDataHome, "fastremote");
        }
        return Path.of(System.getProperty("user.home"), ".local", "share", "fastremote");
    }
}
