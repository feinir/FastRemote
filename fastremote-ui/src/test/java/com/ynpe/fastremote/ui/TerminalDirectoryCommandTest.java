// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class TerminalDirectoryCommandTest {
    @Test
    void localDirectoryCommandsQuotePathsForEachShellAndSwitchCmdDrives() {
        assertEquals("cd '/srv/team'\\''s files'\n", TerminalDirectoryCommand.local(
                "/srv/team's files", LocalTerminalHandle.ShellFamily.POSIX));
        assertEquals("Set-Location -LiteralPath 'D:\\Team''s Files'\r",
                TerminalDirectoryCommand.local(
                        "D:\\Team's Files", LocalTerminalHandle.ShellFamily.POWERSHELL));
        assertEquals("cd /d \"D:\\Team Files\"\r\n", TerminalDirectoryCommand.local(
                "D:\\Team Files", LocalTerminalHandle.ShellFamily.COMMAND_PROMPT));
    }

    @Test
    void remoteDirectoryCommandsUseDetectedLineEndingAndRejectCommandInjection() {
        assertEquals("cd '/srv/releases'\n", TerminalDirectoryCommand.remote(
                "/srv/releases", ServerOverview.SystemFamily.POSIX,
                LocalTerminalHandle.ShellFamily.POSIX));
        assertEquals("cd /d \"D:\\Releases\"\r\n", TerminalDirectoryCommand.remote(
                "/D:/Releases", ServerOverview.SystemFamily.WINDOWS,
                LocalTerminalHandle.ShellFamily.COMMAND_PROMPT));
        assertEquals("Set-Location -LiteralPath 'D:\\Releases'\r", TerminalDirectoryCommand.remote(
                "/D:/Releases", ServerOverview.SystemFamily.WINDOWS,
                LocalTerminalHandle.ShellFamily.POWERSHELL));
        assertEquals("cd /d \"D:\\Releases\"\r\n", TerminalDirectoryCommand.remote(
                "/D:/Releases", ServerOverview.SystemFamily.UNKNOWN,
                LocalTerminalHandle.ShellFamily.COMMAND_PROMPT));
        assertEquals("Set-Location -LiteralPath 'D:\\Releases'\r", TerminalDirectoryCommand.remote(
                "/D:/Releases", ServerOverview.SystemFamily.UNKNOWN,
                LocalTerminalHandle.ShellFamily.POWERSHELL));
        assertThrows(IllegalArgumentException.class, () -> TerminalDirectoryCommand.local(
                "/srv/one\nwhoami", LocalTerminalHandle.ShellFamily.POSIX));
    }

    @Test
    void draggedPathsBecomeQuotedArgumentsWithoutChangingDirectoryOrPressingEnter() {
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("win");
        String firstPath = windows ? "C:\\tmp\\one file" : "/tmp/one file";
        String secondPath = windows ? "C:\\tmp\\team's" : "/tmp/team's";
        String expected = windows
                ? "'C:\\tmp\\one file' 'C:\\tmp\\team'\\''s' "
                : "'/tmp/one file' '/tmp/team'\\''s' ";
        assertEquals(expected,
                TerminalDirectoryCommand.localArguments(
                        List.of(firstPath, secondPath),
                        LocalTerminalHandle.ShellFamily.POSIX));
        assertEquals("'D:\\One File' 'D:\\Team''s' ",
                TerminalDirectoryCommand.remoteArguments(
                        List.of("/D:/One File", "/D:/Team's"),
                        ServerOverview.SystemFamily.WINDOWS,
                        LocalTerminalHandle.ShellFamily.POWERSHELL));
        assertEquals("\"D:\\One File\" ", TerminalDirectoryCommand.remoteArguments(
                List.of("/D:/One File"), ServerOverview.SystemFamily.WINDOWS,
                LocalTerminalHandle.ShellFamily.COMMAND_PROMPT));
        assertThrows(IllegalArgumentException.class, () ->
                TerminalDirectoryCommand.localArguments(List.of("/tmp/a\nwhoami"),
                        LocalTerminalHandle.ShellFamily.POSIX));
    }
}
