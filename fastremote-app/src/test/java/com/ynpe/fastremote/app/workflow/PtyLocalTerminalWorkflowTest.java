// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.ui.LocalTerminalHandle;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PtyLocalTerminalWorkflowTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path cliTestDirectory;
    @Test
    void detectsTheActualLocalShellFamilyForDirectoryChanges() {
        assertEquals(LocalTerminalHandle.ShellFamily.POWERSHELL,
                PtyLocalTerminalWorkflow.shellFamily(new String[]{"pwsh.exe"}));
        assertEquals(LocalTerminalHandle.ShellFamily.POWERSHELL,
                PtyLocalTerminalWorkflow.shellFamily(new String[]{"C:\\Windows\\powershell.exe"}));
        assertEquals(LocalTerminalHandle.ShellFamily.COMMAND_PROMPT,
                PtyLocalTerminalWorkflow.shellFamily(new String[]{"cmd.exe"}));
        assertEquals(LocalTerminalHandle.ShellFamily.POSIX,
                PtyLocalTerminalWorkflow.shellFamily(new String[]{"/bin/zsh", "-l"}));
    }

    @Test
    void localZshHidesItsDefaultTransientEndOfLineMarker() {
        var environment = new java.util.HashMap<String, String>();

        PtyLocalTerminalWorkflow.configureShellEnvironment(
                environment, new String[]{"/bin/zsh", "-l"});

        assertEquals("", environment.get("PROMPT_EOL_MARK"));
    }

    @Test
    void localShellEnvironmentPreservesExplicitMarkerAndLeavesOtherShellsUntouched() {
        var explicit = new java.util.HashMap<String, String>();
        explicit.put("PROMPT_EOL_MARK", "custom");
        PtyLocalTerminalWorkflow.configureShellEnvironment(explicit, new String[]{"/bin/zsh", "-l"});
        assertEquals("custom", explicit.get("PROMPT_EOL_MARK"));

        var bash = new java.util.HashMap<String, String>();
        PtyLocalTerminalWorkflow.configureShellEnvironment(bash, new String[]{"/bin/bash", "-l"});
        assertFalse(bash.containsKey("PROMPT_EOL_MARK"));
    }

    @Test
    void localPosixShellRepairsOnlyTheEffectiveCCharacterLocale() {
        var ctypeC = new java.util.HashMap<String, String>();
        ctypeC.put("LANG", "");
        ctypeC.put("LC_CTYPE", "C");
        PtyLocalTerminalWorkflow.configureShellEnvironment(ctypeC, new String[]{"/bin/sh", "-l"});
        assertEquals("en_US.UTF-8", ctypeC.get("LC_CTYPE"));

        var allC = new java.util.HashMap<String, String>();
        allC.put("LC_ALL", "POSIX");
        allC.put("LC_CTYPE", "zh_CN.UTF-8");
        PtyLocalTerminalWorkflow.configureShellEnvironment(allC, new String[]{"/bin/sh", "-l"});
        assertEquals("en_US.UTF-8", allC.get("LC_ALL"));
        assertEquals("zh_CN.UTF-8", allC.get("LC_CTYPE"));

        var inheritedDefault = new java.util.HashMap<String, String>();
        PtyLocalTerminalWorkflow.configureShellEnvironment(
                inheritedDefault, new String[]{"/bin/sh", "-l"});
        assertEquals("en_US.UTF-8", inheritedDefault.get("LC_CTYPE"));
    }

    @Test
    void localPosixShellPreservesExistingNonCCharacterLocales() {
        var configuredCtype = new java.util.HashMap<String, String>();
        configuredCtype.put("LANG", "C");
        configuredCtype.put("LC_CTYPE", "zh_CN.GB18030");
        PtyLocalTerminalWorkflow.configureShellEnvironment(
                configuredCtype, new String[]{"/bin/bash", "-l"});
        assertEquals("zh_CN.GB18030", configuredCtype.get("LC_CTYPE"));

        var configuredAll = new java.util.HashMap<String, String>();
        configuredAll.put("LC_ALL", "ja_JP.UTF-8");
        configuredAll.put("LC_CTYPE", "C");
        PtyLocalTerminalWorkflow.configureShellEnvironment(
                configuredAll, new String[]{"/bin/bash", "-l"});
        assertEquals("ja_JP.UTF-8", configuredAll.get("LC_ALL"));
        assertEquals("C", configuredAll.get("LC_CTYPE"));

        var windows = new java.util.HashMap<String, String>();
        windows.put("LC_CTYPE", "C");
        PtyLocalTerminalWorkflow.configureShellEnvironment(windows, new String[]{"cmd.exe"});
        assertEquals("C", windows.get("LC_CTYPE"));
    }

    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.setImplicitExit(false);
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        } catch (UnsupportedOperationException unavailable) {
            Assumptions.abort("JavaFX toolkit is unavailable on this build host");
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    @Test
    void bundledPtyStartsARealLocalProcessWithoutAScriptFile() throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String[] command = windows
                ? new String[]{"cmd.exe", "/c", "echo|set /p=FASTREMOTE_PTY"}
                : new String[]{"/bin/sh", "-lc", "printf FASTREMOTE_PTY"};
        PtyProcess process = new PtyProcessBuilder(command).start();
        try {
            var output = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return process.getInputStream().readAllBytes(); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            });
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertTrue(new String(output.get(2, TimeUnit.SECONDS), StandardCharsets.UTF_8)
                    .contains("FASTREMOTE_PTY"));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Test
    void localTerminalSurfaceIsCreatedOnTheJavaFxThread() throws Exception {
        String dataProperty = com.ynpe.fastremote.storage.FastRemotePaths.DATA_DIRECTORY_PROPERTY;
        String previousData = System.getProperty(dataProperty);
        System.setProperty(dataProperty, cliTestDirectory.toString());
        try {
            PtyLocalTerminalWorkflow workflow = new PtyLocalTerminalWorkflow();
            var handle = workflow.open(TerminalPreferences.defaults()).thenApply(value -> {
                assertTrue(Platform.isFxApplicationThread());
                return value;
            }).toCompletableFuture().get(10, TimeUnit.SECONDS);
            try {
                assertTrue(handle.node() != null);
                assertEquals(java.nio.file.Path.of(System.getProperty("user.home")).toAbsolutePath().normalize(),
                        handle.workingDirectory().orElseThrow());
                assertEquals(java.nio.file.Path.of(System.getProperty("user.home")).toAbsolutePath().normalize(),
                        handle.resolveWorkingDirectory().toCompletableFuture().get(5, TimeUnit.SECONDS).orElseThrow());
            } finally {
                handle.close();
                workflow.close();
            }
        } finally {
            if (previousData == null) System.clearProperty(dataProperty);
            else System.setProperty(dataProperty, previousData);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/bin/bash", "/bin/zsh"})
    @org.junit.jupiter.api.Timeout(30)
    void observedCommandSurvivesSlowInteractiveShellStartupOnRealPty(String shell) throws Exception {
        Assumptions.assumeTrue(java.nio.file.Files.isExecutable(java.nio.file.Path.of(shell)));
        var environment = new java.util.HashMap<>(System.getenv());
        environment.put("TERM", "xterm-256color");
        environment.put("PS1", "PTY_READY> ");
        PtyProcess process = new PtyProcessBuilder(new String[]{"/bin/sh", "-c",
                "sleep 1; exec " + shell + (shell.endsWith("bash") ? " --noprofile --norc -i" : " -f -i")}).setEnvironment(environment).start();
        var ended = new java.util.concurrent.CompletableFuture<com.ynpe.fastremote.domain.terminal.TerminalTermination>();
        var created = new java.util.concurrent.CompletableFuture<com.ynpe.fastremote.terminal.FastRemoteTerminal>();
        Platform.runLater(() -> {
            var terminal = new com.ynpe.fastremote.terminal.FastRemoteTerminal(TerminalPreferences.defaults());
            terminal.connect(new com.ynpe.fastremote.domain.terminal.TerminalConnection() {
                public InputStream input() { return process.getInputStream(); }
                public OutputStream output() { return process.getOutputStream(); }
                public boolean isOpen() { return process.isAlive(); }
                public void resize(int columns, int rows) { process.setWinSize(new com.pty4j.WinSize(columns, rows)); }
                public java.util.concurrent.CompletionStage<com.ynpe.fastremote.domain.terminal.TerminalTermination> termination() { return ended; }
                public void close() { process.destroyForcibly(); ended.complete(com.ynpe.fastremote.domain.terminal.TerminalTermination.eof()); }
            });
            created.complete(terminal);
        });
        var terminal = created.get(5, TimeUnit.SECONDS);
        try {
            var observation = terminal.observation();
            String id;
            try {
                id = observation.execute("printf 'PTY_RESULT中文\\n'; false", true, "POSIX")
                        .toCompletableFuture().get(12, TimeUnit.SECONDS);
            } catch (Exception failure) {
                throw new AssertionError(observation.read(0, 32768, false).text(), failure);
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!observation.operation(id, 0, 4096).terminalState() && System.nanoTime() < deadline) Thread.sleep(10);
            var result = observation.operation(id, 0, 4096);
            assertEquals("FAILED", result.state(), () -> observation.read(0, 32768, false).text());
            assertEquals(1, result.exitCode().orElseThrow());
            assertTrue(result.output().orElseThrow().text().contains("PTY_RESULT中文"));
            String next = observation.execute("cd /tmp; printf 'SECOND_RESULT\\n'", false, "POSIX")
                    .toCompletableFuture().get(3, TimeUnit.SECONDS);
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!observation.operation(next, 0, 4096).terminalState() && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals("SUCCEEDED", observation.operation(next, 0, 4096).state());
            assertEquals(0, observation.operation(next, 0, 4096).exitCode().orElseThrow());
            var responsive = new java.util.concurrent.CompletableFuture<Boolean>();
            Platform.runLater(() -> responsive.complete(true));
            assertTrue(responsive.get(2, TimeUnit.SECONDS));
        } finally {
            process.destroyForcibly();
            Platform.runLater(terminal::close);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/bin/bash", "/bin/zsh"})
    @org.junit.jupiter.api.Timeout(30)
    void automationStartupPreservesProfilesWithoutEchoingHooks(String shell) throws Exception {
        Assumptions.assumeTrue(java.nio.file.Files.isExecutable(java.nio.file.Path.of(shell)));
        var environment = new java.util.HashMap<>(System.getenv());
        environment.put("HOME", cliTestDirectory.toString());
        environment.remove("ZDOTDIR");
        environment.put("TERM", "xterm-256color");
        String profile = "export FR_PROFILE_CHECK=profile-ok\n";
        if (shell.endsWith("zsh")) {
            var custom = cliTestDirectory.resolve("custom config");
            java.nio.file.Files.createDirectories(custom);
            java.nio.file.Files.writeString(cliTestDirectory.resolve(".zshenv"),
                    "export ZDOTDIR='" + custom + "'\n");
            java.nio.file.Files.writeString(custom.resolve(".zprofile"), profile);
            java.nio.file.Files.writeString(custom.resolve(".zshrc"), "sleep 0.3\nPS1='STARTUP_READY> '\nfixture_prompt() { sleep 0.2; }; precmd_functions+=(fixture_prompt)\n");
            java.nio.file.Files.writeString(custom.resolve(".zlogin"), "export FR_LOGIN_CHECK=login-ok\n");
        } else {
            java.nio.file.Files.writeString(cliTestDirectory.resolve(".bash_profile"), profile
                    + "sleep 0.3\nexport FR_LOGIN_CHECK=login-ok\nPS1='STARTUP_READY> '\n");
        }
        var startup = LocalShellStartup.prepare(new String[]{shell, "-l"}, environment);
        assertTrue(startup != null);
        var process = new PtyProcessBuilder(startup.command()).setEnvironment(environment)
                .setDirectory(cliTestDirectory.toString()).start();
        var ended = new java.util.concurrent.CompletableFuture<com.ynpe.fastremote.domain.terminal.TerminalTermination>();
        var created = new java.util.concurrent.CompletableFuture<com.ynpe.fastremote.terminal.FastRemoteTerminal>();
        Platform.runLater(() -> {
            var terminal = new com.ynpe.fastremote.terminal.FastRemoteTerminal(TerminalPreferences.defaults());
            terminal.expectShellIntegration(startup.nonce());
            terminal.connect(new com.ynpe.fastremote.domain.terminal.TerminalConnection() {
                public InputStream input() { return process.getInputStream(); }
                public OutputStream output() { return process.getOutputStream(); }
                public boolean isOpen() { return process.isAlive(); }
                public void resize(int columns, int rows) { process.setWinSize(new com.pty4j.WinSize(columns, rows)); }
                public java.util.concurrent.CompletionStage<com.ynpe.fastremote.domain.terminal.TerminalTermination> termination() { return ended; }
                public void close() { process.destroyForcibly(); ended.complete(com.ynpe.fastremote.domain.terminal.TerminalTermination.eof()); }
            });
            created.complete(terminal);
        });
        var terminal = created.get(5, TimeUnit.SECONDS);
        try {
            var observation = terminal.observation();
            String id = observation.execute("printf '%s %s 中文\\n' \"$FR_PROFILE_CHECK\" \"$FR_LOGIN_CHECK\"; false", false, "POSIX")
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!observation.operation(id, 0, 4096).terminalState() && System.nanoTime() < deadline) Thread.sleep(10);
            var result = observation.operation(id, 0, 4096);
            assertEquals("FAILED", result.state());
            assertEquals(1, result.exitCode().orElseThrow());
            String output = observation.read(0, 32768, false).text();
            assertTrue(output.contains("profile-ok login-ok 中文"));
            assertFalse(output.contains("_fr_"));
            assertFalse(output.contains("6973"));
            assertFalse(output.contains("then quote>"));
            assertFalse(output.contains("integration.sh"));
            if (shell.endsWith("zsh")) {
                String echo = "printf '%s %s";
                assertEquals(output.indexOf(echo), output.lastIndexOf(echo), "Command must not echo before the first prompt");
                assertTrue(output.indexOf("STARTUP_READY>") < output.indexOf(echo));
            }
            String second = observation.execute("printf 'CONTINUED\\n'", false, "POSIX").toCompletableFuture().get(3, TimeUnit.SECONDS);
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!observation.operation(second, 0, 4096).terminalState() && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals("SUCCEEDED", observation.operation(second, 0, 4096).state());
        } finally {
            process.destroyForcibly();
            Platform.runLater(terminal::close);
            startup.close();
        }
        assertFalse(java.nio.file.Files.exists(startup.directory()));
    }

    @Test
    void closingStillDestroysProcessesWithoutProcessHandleSupport() {
        UnsupportedHandleProcess process = new UnsupportedHandleProcess();

        PtyLocalTerminalWorkflow.destroyProcess(process);

        assertFalse(process.isAlive());
    }

    private static final class UnsupportedHandleProcess extends Process {
        private boolean alive = true;
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { alive = false; return 0; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() { alive = false; }
        @Override public boolean isAlive() { return alive; }
        @Override public ProcessHandle toHandle() { throw new UnsupportedOperationException("unsupported"); }
    }
}
