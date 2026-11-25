// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemoteWorkingDirectoryParserTest {
    @Test
    void infersCommonPosixAndPowerShellPromptPaths() {
        assertEquals("/srv/releases/current",
                RemoteWorkingDirectoryParser.inferredPath(
                        "deploy@build.example:/srv/releases/current$ ").orElseThrow());
        assertEquals("/var/log",
                RemoteWorkingDirectoryParser.inferredPath("[root@server /var/log]# ").orElseThrow());
        assertEquals("/Users/demo/My Project",
                RemoteWorkingDirectoryParser.inferredPath("host /Users/demo/My Project % ").orElseThrow());
        assertEquals("/C:/Users/demo/source",
                RemoteWorkingDirectoryParser.inferredPath("PS C:\\Users\\demo\\source> ").orElseThrow());
        assertEquals("/D:/work/project",
                RemoteWorkingDirectoryParser.inferredPath("D:\\work\\project> ").orElseThrow());
        assertEquals(RemoteWorkingDirectoryParser.ShellFamily.COMMAND_PROMPT,
                RemoteWorkingDirectoryParser.inferredPrompt(
                        "zephyr@ZEPHYR-POCKET4 D:\\FastRemote> ").orElseThrow().shellFamily());
        assertEquals("~/FastRemote",
                RemoteWorkingDirectoryParser.inferredPath(
                        "zephyr@Server-Ubuntu24:~/FastRemote$ ls").orElseThrow());
        assertEquals("~",
                RemoteWorkingDirectoryParser.inferredPath(
                        "zephyr@Server-Ubuntu24:~$ ").orElseThrow());
    }

    @Test
    void ignoresOutputThatDoesNotLookLikeAnAbsolutePromptPath() {
        assertTrue(RemoteWorkingDirectoryParser.inferredPath("downloaded /tmp/archive.tar").isEmpty());
        assertTrue(RemoteWorkingDirectoryParser.inferredPath("relative/path $ ").isEmpty());
    }

    @Test
    void handlesSplitAnsiOutputAndOnlyReportsPathChanges() {
        List<String> paths = new ArrayList<>();
        List<RemoteWorkingDirectoryParser.ShellFamily> shells = new ArrayList<>();
        var parser = new RemoteWorkingDirectoryParser(paths::add, shells::add);

        parser.accept("\u001b[32muser@host:\u001b[");
        parser.accept("34m/srv/app\u001b[0m$ ");
        parser.accept("\r\nuser@host:/srv/app$ ");
        parser.accept("\r\nuser@host:/srv/next$ ");

        assertEquals(List.of("/srv/app", "/srv/next"), paths);
        assertEquals(List.of(RemoteWorkingDirectoryParser.ShellFamily.POSIX), shells);
    }

    @Test
    void reportsShellChangesEvenWhenTheDirectoryDoesNotChange() {
        List<String> paths = new ArrayList<>();
        List<RemoteWorkingDirectoryParser.ShellFamily> shells = new ArrayList<>();
        var parser = new RemoteWorkingDirectoryParser(paths::add, shells::add);

        parser.accept("D:\\FastRemote> ");
        parser.accept("\r\nPS D:\\FastRemote> ");
        parser.accept("\r\nzephyr@ZEPHYR-POCKET4 D:\\FastRemote> ");

        assertEquals(List.of("/D:/FastRemote"), paths);
        assertEquals(List.of(
                RemoteWorkingDirectoryParser.ShellFamily.COMMAND_PROMPT,
                RemoteWorkingDirectoryParser.ShellFamily.POWERSHELL,
                RemoteWorkingDirectoryParser.ShellFamily.COMMAND_PROMPT), shells);
    }
}
