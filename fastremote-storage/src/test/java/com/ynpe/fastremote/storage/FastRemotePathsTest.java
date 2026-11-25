// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FastRemotePathsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void explicitDataDirectoryOverridesThePlatformDefault() {
        String previous = System.getProperty(FastRemotePaths.DATA_DIRECTORY_PROPERTY);
        try {
            Path configured = temporaryDirectory.resolve("development-data").resolve("..").resolve("development-data");
            System.setProperty(FastRemotePaths.DATA_DIRECTORY_PROPERTY, configured.toString());

            assertEquals(configured.toAbsolutePath().normalize(), FastRemotePaths.applicationDataDirectory());
        } finally {
            restoreProperty(previous);
        }
    }

    private static void restoreProperty(String value) {
        if (value == null) {
            System.clearProperty(FastRemotePaths.DATA_DIRECTORY_PROPERTY);
        } else {
            System.setProperty(FastRemotePaths.DATA_DIRECTORY_PROPERTY, value);
        }
    }
}
