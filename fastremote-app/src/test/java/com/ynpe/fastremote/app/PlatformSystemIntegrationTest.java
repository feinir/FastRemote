// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PlatformSystemIntegrationTest {
    @Test
    void windowsRegistersFileDirectoryAndDirectoryBackgroundVerbsWithPaths() {
        Path executable = Path.of("C:\\Program Files\\FastRemote\\FastRemote.exe");

        List<List<String>> commands = PlatformSystemIntegration.windowsRegistryCommands(
                executable, "Open with FastRemote");

        assertEquals(9, commands.size());
        assertTrue(commands.stream().flatMap(List::stream)
                .anyMatch(value -> value.contains("Directory\\shell\\FastRemote\\command")));
        assertTrue(commands.stream().flatMap(List::stream)
                .anyMatch(value -> value.contains("Directory\\Background\\shell\\FastRemote\\command")));
        assertTrue(commands.stream().flatMap(List::stream).anyMatch(value -> value.contains("\"%1\"")));
        assertTrue(commands.stream().flatMap(List::stream).anyMatch(value -> value.contains("\"%V\"")));
    }

    @Test
    void linuxDesktopEntryPassesSelectedDirectoriesAsOrdinaryArguments() {
        Assumptions.assumeFalse(File.separatorChar == '\\',
                "Linux desktop paths cannot be represented by a Windows Path");
        String desktop = PlatformSystemIntegration.linuxDesktopEntry(
                Path.of("/opt/Fast Remote/FastRemote"), "Open with FastRemote");

        assertTrue(desktop.contains("Exec=\"/opt/Fast Remote/FastRemote\" %F"));
        assertTrue(desktop.contains("MimeType=inode/directory;"));
        assertTrue(desktop.contains("Name=Open with FastRemote"));
        assertTrue(desktop.contains("NoDisplay=true"));
    }

    @Test
    void linuxLauncherDesktopEntryUsesTheCurrentPortableImageWithoutHardcodedInstallPath() {
        Assumptions.assumeFalse(File.separatorChar == '\\',
                "Linux desktop paths cannot be represented by a Windows Path");
        String desktop = PlatformSystemIntegration.linuxLauncherDesktopEntry(
                Path.of("/mnt/Portable Apps/FastRemote/bin/FastRemote"));

        assertTrue(desktop.contains("Name=FastRemote"));
        assertTrue(desktop.contains("Exec=\"/mnt/Portable Apps/FastRemote/bin/FastRemote\""));
        assertTrue(desktop.contains("TryExec=/mnt/Portable Apps/FastRemote/bin/FastRemote"));
        assertTrue(desktop.contains("Icon=/mnt/Portable Apps/FastRemote/lib/FastRemote.png"));
        assertTrue(desktop.contains("NoDisplay=false"));
        assertTrue(desktop.contains(
                "StartupWMClass=com.ynpe.fastremote.app.FastRemoteApplication"));
    }
}
