// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IncompatibleDataNoticeTest {
    @Test
    void usesTheRealDataDirectoryOptionAndPlatformLauncherName() {
        Path backup = Path.of("build", "backup data").toAbsolutePath().normalize();
        String quoted = "\"" + backup + "\"";

        assertEquals("FastRemote.exe --user-data-dir " + quoted,
                FastRemoteApplication.incompatibleDataLaunchCommand(backup, "Windows 11"));
        assertEquals("FastRemote.app/Contents/MacOS/FastRemote --user-data-dir " + quoted,
                FastRemoteApplication.incompatibleDataLaunchCommand(backup, "Mac OS X"));
        assertEquals("FastRemote --user-data-dir " + quoted,
                FastRemoteApplication.incompatibleDataLaunchCommand(backup, "Linux"));
    }
}
