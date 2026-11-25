// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.terminal.RemoteCommandResult;
import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import com.ynpe.fastremote.domain.terminal.TerminalTermination;
import com.ynpe.fastremote.domain.settings.RemoteProbeSettings;
import com.ynpe.fastremote.ui.ServerOverview;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class RemoteSystemProbeTest {
    @Test
    void formatsBoundedLinuxFactsIncludingUserAndInitialDirectory() {
        var facts = RemoteSystemProbe.parseFacts("""
                ignored output
                FRP1_OS=Linux
                FRP1_HOST=demo-server
                FRP1_USER=deploy
                FRP1_WORK_DIR=/home/deploy
                FRP1_SYSTEM=Linux 6.8.0 x86_64
                FRP1_UNAME_ALL=Linux demo-server 6.8.0 #1 SMP x86_64 GNU/Linux
                FRP1_ARCH=x86_64
                FRP1_OS_RELEASE_PRETTY_NAME=Ubuntu 26.04 LTS
                FRP1_CPU_COUNT=8
                FRP1_CPU_MODEL=Example CPU
                FRP1_MEM_TOTAL_KB=16777216
                FRP1_MEM_AVAILABLE_KB=12582912
                FRP1_UPTIME_SECONDS=183845
                FRP1_NET=eth0  10.20.30.40/24
                FRP1_NET=eth0  2001:db8::2/64
                """);

        var overview = RemoteSystemProbe.overview(facts, false);

        assertTrue(overview.text().startsWith("Linux demo-server 6.8.0 #1 SMP x86_64 GNU/Linux\n"));
        assertTrue(overview.text().contains("主机名  demo-server"));
        assertTrue(overview.text().contains("用户  deploy    初始目录  /home/deploy"));
        assertTrue(overview.text().contains("系统  Ubuntu 26.04 LTS"));
        assertTrue(overview.text().contains("CPU   8 核 · Example CPU"));
        assertTrue(overview.text().contains("内存  4.0 GiB 已用 / 16.0 GiB 总计"));
        assertTrue(overview.text().contains("运行  2 天 3 小时 4 分钟"));
        assertTrue(overview.text().contains("eth0  10.20.30.40/24"));
        assertEquals(ServerOverview.SystemFamily.POSIX, overview.systemFamily());
    }

    @Test
    void formatsMacFreeBsdAndWindowsByteBasedFacts() {
        for (String os : List.of("Darwin", "FreeBSD", "Windows")) {
            var facts = RemoteSystemProbe.parseFacts("""
                    FRP1_OS=%s
                    FRP1_SYSTEM=system-version
                    FRP1_OS_PRETTY=pretty-version
                    FRP1_MEM_TOTAL_BYTES=8589934592
                    FRP1_MEM_AVAILABLE_BYTES=2147483648
                    """.formatted(os));

            var overview = RemoteSystemProbe.overview(facts, true);

            assertTrue(overview.text().contains("系统  pretty-version"));
            assertTrue(overview.text().contains("内存  6.0 GiB 已用 / 8.0 GiB 总计"));
            assertTrue(overview.text().contains("256 KiB"));
            assertEquals(os.equals("Windows")
                            ? ServerOverview.SystemFamily.WINDOWS : ServerOverview.SystemFamily.POSIX,
                    overview.systemFamily());
        }
    }

    @Test
    void ignoresUnknownAndControlBearingFields() {
        var facts = RemoteSystemProbe.parseFacts("""
                FRP1_OS=Linux
                FRP1_UNKNOWN=value
                FRP1_HOST=demo\u0000-server
                NOT_FRP1_USER=attacker
                """);

        assertEquals("demo-server", facts.values().get("HOST"));
        assertFalse(facts.values().containsKey("UNKNOWN"));
        assertFalse(facts.values().containsKey("USER"));
    }

    @Test
    void fallsBackFromPosixToPowerShellAndSharesTheLogicalBudget() throws Exception {
        FakeTerminalConnection connection = new FakeTerminalConnection(
                new RemoteCommandResult("", "uname is not recognized", 1, false),
                new RemoteCommandResult("FRP1_OS=Windows\nFRP1_HOST=win-host\n", "", 0, false));

        var overview = RemoteSystemProbe.probe(connection).overview();

        assertTrue(overview.text().contains("主机名  win-host"));
        assertEquals(2, connection.commands.size());
        assertTrue(connection.commands.get(0).contains("uname -s"));
        assertTrue(connection.commands.get(1).startsWith("powershell.exe "));
        String encoded = connection.commands.get(1).substring(connection.commands.get(1).lastIndexOf(' ') + 1);
        String script = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_16LE);
        assertTrue(script.contains("[Console]::OutputEncoding"));
        assertTrue(script.contains("$OutputEncoding"));
        assertTrue(connection.timeouts.stream().allMatch(timeout -> !timeout.isNegative()
                && timeout.compareTo(Duration.ofSeconds(10)) <= 0));
        assertEquals(RemoteSystemProbe.MAXIMUM_OUTPUT_BYTES, connection.outputLimits.get(0));
        assertTrue(connection.outputLimits.get(1) < connection.outputLimits.get(0));
    }

    @Test
    void automationReportsOperatingSystemsAndUnknownWithoutRunningSupplementalCommands() throws Exception {
        var family = com.ynpe.fastremote.domain.automation.RemoteHostSystem.SystemFamily.POSIX;
        for (String os : List.of("Linux", "Darwin", "FreeBSD")) {
            var connection = new FakeTerminalConnection(
                    new RemoteCommandResult("FRP1_OS=" + os + "\n", "", 0, false));
            var system = RemoteSystemProbe.detectSystem(connection);
            assertEquals(os.equals("Darwin") ? "macOS" : os, system.operatingSystem());
            assertEquals(family, system.systemFamily());
            assertEquals(1, connection.commands.size());
        }
        var failure = new RemoteCommandResult("", "command unavailable", 1, false);
        var windows = RemoteSystemProbe.detectSystem(new FakeTerminalConnection(failure,
                new RemoteCommandResult("FRP1_OS=Windows\n", "", 0, false)));
        assertEquals("Windows", windows.operatingSystem());
        assertEquals(com.ynpe.fastremote.domain.automation.RemoteHostSystem.SystemFamily.WINDOWS, windows.systemFamily());
        assertEquals(com.ynpe.fastremote.domain.automation.RemoteHostSystem.unknown(),
                RemoteSystemProbe.detectSystem(new FakeTerminalConnection(failure, failure, failure)));
    }

    @Test
    void cmdFallbackSwitchesToUtf8BeforeEmittingAsciiSystemIdentity() throws Exception {
        FakeTerminalConnection connection = new FakeTerminalConnection(
                new RemoteCommandResult("", "no uname", 1, false),
                new RemoteCommandResult("", "no powershell", 1, false),
                new RemoteCommandResult("FRP1_OS=Windows\r\nFRP1_HOST=win-host\r\n", "", 0, false));

        var overview = RemoteSystemProbe.probe(connection).overview();

        assertEquals(ServerOverview.SystemFamily.WINDOWS, overview.systemFamily());
        assertEquals(3, connection.commands.size());
        assertTrue(connection.commands.get(2).contains("chcp 65001>nul"));
        assertTrue(connection.commands.get(2).contains("echo FRP1_OS=Windows"));
    }

    @Test
    void appendsTheMatchingSupplementAfterTheFixedSystemProbe() throws Exception {
        FakeTerminalConnection connection = new FakeTerminalConnection(
                new RemoteCommandResult("FRP1_OS=Linux\nFRP1_HOST=demo\n", "", 0, false),
                new RemoteCommandResult("eth0  10.0.0.2\n", "", 0, false),
                new RemoteCommandResult("/dev/sda1  40%\n", "", 0, false));
        var settings = new RemoteProbeSettings(true,
                "ifconfig -a\ndf -h", "", "", "");

        var overview = RemoteSystemProbe.probe(connection, settings).overview();

        assertEquals(3, connection.commands.size());
        assertTrue(connection.commands.get(0).contains("uname -s"));
        assertTrue(connection.commands.get(1).contains("ifconfig -a"));
        assertTrue(connection.commands.get(2).contains("df -h"));
        assertTrue(overview.text().contains("补充探测\n命令 [ifconfig]：\neth0  10.0.0.2"));
        assertTrue(overview.text().contains("命令 [df]：\n/dev/sda1  40%"));
        assertTrue(connection.outputLimits.get(1) <= connection.outputLimits.get(0));
        assertTrue(connection.outputLimits.get(2) < connection.outputLimits.get(1));
    }

    private static final class FakeTerminalConnection implements TerminalConnection {
        private final ArrayDeque<RemoteCommandResult> results = new ArrayDeque<>();
        private final List<String> commands = new ArrayList<>();
        private final List<Duration> timeouts = new ArrayList<>();
        private final List<Integer> outputLimits = new ArrayList<>();

        private FakeTerminalConnection(RemoteCommandResult... results) {
            this.results.addAll(List.of(results));
        }

        @Override
        public RemoteCommandResult executeCommand(String command, Duration timeout, int maximumOutputBytes) {
            commands.add(command);
            timeouts.add(timeout);
            outputLimits.add(maximumOutputBytes);
            return results.removeFirst();
        }

        @Override public InputStream input() { return new ByteArrayInputStream(new byte[0]); }
        @Override public OutputStream output() { return new ByteArrayOutputStream(); }
        @Override public boolean isOpen() { return true; }
        @Override public void resize(int columns, int rows) { }
        @Override public java.util.concurrent.CompletionStage<TerminalTermination> termination() {
            return new CompletableFuture<>();
        }
        @Override public void close() { }
    }
}
