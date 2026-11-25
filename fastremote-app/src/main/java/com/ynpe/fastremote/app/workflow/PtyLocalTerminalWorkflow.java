// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import com.ynpe.fastremote.domain.terminal.TerminalTermination;
import com.ynpe.fastremote.terminal.FastRemoteTerminal;
import com.ynpe.fastremote.ui.LocalTerminalHandle;
import com.ynpe.fastremote.ui.LocalTerminalWorkflow;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;

/** Starts a real local PTY under the permissions of the application process. */
public final class PtyLocalTerminalWorkflow implements LocalTerminalWorkflow {
    private final boolean appStore;
    private final ExecutorService executor = new ThreadPoolExecutor(0, 32, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofVirtual().name("fastremote-local-pty-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());
    private final AtomicBoolean closed = new AtomicBoolean();

    public PtyLocalTerminalWorkflow() {
        this(false);
    }

    public PtyLocalTerminalWorkflow(boolean appStore) {
        this.appStore = appStore;
    }

    @Override public CompletionStage<LocalTerminalHandle> open(TerminalPreferences preferences) {
        return open(preferences, Path.of(System.getProperty("user.home")));
    }

    @Override public CompletionStage<LocalTerminalHandle> open(
            TerminalPreferences preferences, Path initialDirectory) {
        return open(preferences, initialDirectory, false);
    }

    @Override public CompletionStage<LocalTerminalHandle> openAutomation(
            TerminalPreferences preferences, Path initialDirectory) {
        return open(preferences, initialDirectory, true);
    }

    private CompletionStage<LocalTerminalHandle> open(
            TerminalPreferences preferences, Path initialDirectory, boolean automation) {
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.685f7422410b19af")));
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        Path requestedDirectory = initialDirectory.toAbsolutePath().normalize();
        // The Store PTY inherits static sandbox permissions. A directory granted to the GUI
        // through a file picker does not become the shell's working directory outside the container.
        Path resolvedWorkingDirectory = (!appStore || requestedDirectory.startsWith(home))
                && java.nio.file.Files.isDirectory(requestedDirectory) ? requestedDirectory : home;
        return CompletableFuture.supplyAsync(() -> {
            LocalShellStartup startup = null;
            try {
                Map<String, String> environment = new HashMap<>(System.getenv());
                environment.put("TERM", preferences.terminalType().ptyName());
                String[] command = appStore ? new String[]{"/bin/zsh", "-f"} : shellCommand();
                if (appStore) environment.put("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");
                configureShellEnvironment(environment, command);
                if (com.ynpe.fastremote.app.CommandLineToolInstallation.available()) {
                    Path cliBin = com.ynpe.fastremote.app.CommandLineToolInstallation.prepare(environment);
                    com.ynpe.fastremote.app.CommandLineToolInstallation.configureShell(environment, command, cliBin);
                }
                if (automation) startup = LocalShellStartup.prepare(command, environment);
                if (startup != null) command = startup.command();
                PtyProcess process = new PtyProcessBuilder(command)
                        .setEnvironment(environment)
                        .setDirectory(resolvedWorkingDirectory.toString())
                        .setInitialColumns(preferences.initialColumns())
                        .setInitialRows(preferences.initialRows())
                        .setConsole(false).setCygwin(false).start();
                return new LocalPtyConnection(process, executor, shellFamily(command), startup);
            } catch (Exception failure) {
                if (startup != null) startup.close();
                throw new java.util.concurrent.CompletionException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6c006a6699806914"), failure);
            }
        }, executor).thenApplyAsync(connection -> {
            try {
                // JediTermFX owns JavaFX nodes, timelines and layout listeners. Creating and starting it
                // off the application thread leaves repaint requests dormant until a scroll or resize.
                FastRemoteTerminal terminal = new FastRemoteTerminal(preferences,
                        com.ynpe.fastremote.ui.TerminalContextMenuPreparation::prepare);
                if (connection.startup != null) terminal.expectShellIntegration(connection.startup.nonce());
                terminal.connect(connection);
                return new LocalTerminalHandle(terminal.node(), terminal.focusTarget(), terminal::close,
                        terminal.termination(), terminal::applyPreferences, terminal::sendInput,
                        terminal::receivedBytes, terminal::sentBytes, terminal::applyKeyboardShortcuts,
                        resolvedWorkingDirectory, terminal::setWorkingDirectoryListener,
                        () -> CompletableFuture.supplyAsync(
                                () -> processWorkingDirectory(connection.process), executor),
                        connection.shellFamily, items -> terminal.setContextMenuItems(
                                TerminalContextMenuBridge.adapt(items))).withObservation(terminal.observation());
            } catch (RuntimeException failure) {
                connection.close();
                throw new java.util.concurrent.CompletionException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6c006a6699806914"), failure);
            }
        }, Platform::runLater);
    }

    static String[] shellCommand() {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            for (String candidate : new String[]{"pwsh.exe", "powershell.exe", "cmd.exe"}) {
                if (findOnPath(candidate)) return new String[]{candidate};
            }
            return new String[]{"cmd.exe"};
        }
        String shell = System.getenv("SHELL");
        if (shell == null || shell.isBlank() || !java.nio.file.Files.isExecutable(Path.of(shell))) shell = "/bin/sh";
        return new String[]{shell, "-l"};
    }

    static LocalTerminalHandle.ShellFamily shellFamily(String[] command) {
        if (command.length == 0) return LocalTerminalHandle.ShellFamily.POSIX;
        String normalized = command[0].replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String executable = separator < 0 ? normalized : normalized.substring(separator + 1);
        return switch (executable.toLowerCase(Locale.ROOT)) {
            case "cmd", "cmd.exe" -> LocalTerminalHandle.ShellFamily.COMMAND_PROMPT;
            case "powershell", "powershell.exe", "pwsh", "pwsh.exe" ->
                    LocalTerminalHandle.ShellFamily.POWERSHELL;
            default -> LocalTerminalHandle.ShellFamily.POSIX;
        };
    }

    static void configureShellEnvironment(Map<String, String> environment, String[] command) {
        if (command.length == 0) return;
        if (shellFamily(command) == LocalTerminalHandle.ShellFamily.POSIX) {
            normalizePosixCharacterLocale(environment);
        }
        Path executable = Path.of(command[0]);
        Path fileName = executable.getFileName();
        if (fileName != null && "zsh".equals(fileName.toString())) {
            // zsh paints a visible end-of-line marker, then erases it by padding to the PTY width.
            // During JavaFX's first layout the emulator may resize before those bytes are parsed,
            // leaving the marker in scrollback. Keep an explicitly exported user value, otherwise
            // make only the marker invisible; zsh's PROMPT_SP line-preservation behavior remains on.
            environment.putIfAbsent("PROMPT_EOL_MARK", "");
        }
    }

    static void normalizePosixCharacterLocale(Map<String, String> environment) {
        String lcAll = environment.getOrDefault("LC_ALL", "").trim();
        if (!lcAll.isEmpty()) {
            if (isDefaultPosixLocale(lcAll)) environment.put("LC_ALL", "en_US.UTF-8");
            return;
        }
        String lcCtype = environment.getOrDefault("LC_CTYPE", "").trim();
        if (!lcCtype.isEmpty()) {
            if (isDefaultPosixLocale(lcCtype)) environment.put("LC_CTYPE", "en_US.UTF-8");
            return;
        }
        String lang = environment.getOrDefault("LANG", "").trim();
        if (lang.isEmpty() || isDefaultPosixLocale(lang)) {
            environment.put("LC_CTYPE", "en_US.UTF-8");
        }
    }

    private static boolean isDefaultPosixLocale(String value) {
        return "C".equalsIgnoreCase(value) || "POSIX".equalsIgnoreCase(value);
    }

    static java.util.Optional<Path> processWorkingDirectory(PtyProcess process) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("linux")) {
            try {
                return java.util.Optional.of(java.nio.file.Files.readSymbolicLink(
                        Path.of("/proc", Long.toString(process.pid()), "cwd")).toAbsolutePath().normalize());
            } catch (java.io.IOException | RuntimeException ignored) {
                return java.util.Optional.empty();
            }
        }
        if (os.contains("mac")) {
            Process query = null;
            try {
                query = new ProcessBuilder("/usr/sbin/lsof", "-a", "-p", Long.toString(process.pid()),
                        "-d", "cwd", "-Fn").redirectErrorStream(true).start();
                if (!query.waitFor(2, TimeUnit.SECONDS)) {
                    query.destroyForcibly();
                    return java.util.Optional.empty();
                }
                String output = new String(query.getInputStream().readNBytes(8192), StandardCharsets.UTF_8);
                return output.lines().filter(line -> line.startsWith("n/")).findFirst()
                        .map(line -> Path.of(line.substring(1)).toAbsolutePath().normalize());
            } catch (java.io.IOException | InterruptedException | RuntimeException ignored) {
                if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
                if (query != null && query.isAlive()) query.destroyForcibly();
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.empty();
    }

    private static boolean findOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String item : path.split(java.io.File.pathSeparator)) {
            if (java.nio.file.Files.isRegularFile(Path.of(item, executable))) return true;
        }
        return false;
    }

    @Override public CompletionStage<Path> installCommandLineTools() {
        return CompletableFuture.supplyAsync(() -> {
            try { return com.ynpe.fastremote.app.CommandLineToolInstallation.install(); }
            catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, executor);
    }

    @Override public void close() { if (closed.compareAndSet(false, true)) executor.shutdownNow(); }

    private static final class LocalPtyConnection implements TerminalConnection {
        private final PtyProcess process;
        private final LocalTerminalHandle.ShellFamily shellFamily;
        private final CompletableFuture<TerminalTermination> termination;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final LocalShellStartup startup;
        LocalPtyConnection(PtyProcess process, ExecutorService executor,
                LocalTerminalHandle.ShellFamily shellFamily, LocalShellStartup startup) {
            this.process = process;
            this.shellFamily = Objects.requireNonNull(shellFamily, "shellFamily");
            this.startup = startup;
            termination = CompletableFuture.supplyAsync(() -> {
                try { return TerminalTermination.exited(process.waitFor()); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return TerminalTermination.eof(); }
                finally { if (startup != null) startup.close(); }
            }, executor);
        }
        public InputStream input() { return process.getInputStream(); }
        public OutputStream output() { return process.getOutputStream(); }
        public boolean isOpen() { return process.isAlive() && !closed.get(); }
        public void resize(int columns, int rows) { if (isOpen()) process.setWinSize(new WinSize(columns, rows)); }
        public CompletionStage<TerminalTermination> termination() { return termination; }
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            destroyProcess(process);
        }

    }

    static void destroyProcess(Process process) {
        try {
            process.toHandle().descendants().forEach(ProcessHandle::destroy);
        } catch (UnsupportedOperationException ignored) {
            // pty4j's UnixPtyProcess deliberately does not expose a ProcessHandle.
        }
        process.destroy();
        if (process.isAlive()) process.destroyForcibly();
    }
}
