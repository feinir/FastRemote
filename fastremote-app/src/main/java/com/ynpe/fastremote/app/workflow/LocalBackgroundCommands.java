// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.terminal.LocalCommandResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Bounded non-PTY execution. Drains both pipes even after the capture limit, never opening a UI workspace. */
final class LocalBackgroundCommands implements AutoCloseable {
    static final String NOTICE = "Executed locally in the background; not displayed in a terminal Tab. "
            + "Prefer visible GUI workspace tools whenever they can perform the task. Use this interface only when "
            + "accurate captured output/exit status is needed and visible workspace results are insufficient. "
            + "This is a separate non-interactive process, not the existing Tab's cwd/environment. "
            + "Output is decoded as UTF-8. Results describe the foreground process; detached descendants and business effects are not verified. "
            + "Timeout/cancellation may leave partial effects; never automatically repeat a mutation.";
    private final Object lifecycle = new Object();
    private final Set<Process> active = new HashSet<>();
    private final Semaphore slots = new Semaphore(4);
    private final ThreadPoolExecutor readers = new ThreadPoolExecutor(8, 8, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), task -> {
                Thread thread = new Thread(task, "local-command-output"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private boolean closed;

    LocalBackgroundCommands() { readers.allowCoreThreadTimeOut(true); }

    LocalCommandResult execute(String command, Optional<Path> requestedDirectory, Duration timeout,
            int outputLimit, BooleanSupplier canceled) throws Exception {
        if (timeout.isNegative() || timeout.isZero() || outputLimit < 1) throw new IllegalArgumentException("Invalid execution limit");
        if (!slots.tryAcquire()) throw new IllegalStateException("Local background command capacity exceeded");
        Process process = null;
        Future<?> stdoutTask = null, stderrTask = null;
        var children = new LinkedHashSet<ProcessHandle>();
        Capture stdout = new Capture(outputLimit / 2), stderr = new Capture(outputLimit - outputLimit / 2);
        boolean timedOut = false, complete = false;
        try {
            Path directory = requestedDirectory.orElseGet(() -> Path.of(System.getProperty("user.home"))).toRealPath();
            if (!Files.isDirectory(directory)) throw new IllegalArgumentException("Working directory is not a directory");
            List<String> shell = shellPrefix();
            var invocation = new ArrayList<>(shell);
            String script = shell.contains("-NonInteractive")
                    ? "[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false); $OutputEncoding = [Console]::OutputEncoding; " + command
                    : command;
            invocation.add(script);
            var builder = new ProcessBuilder(invocation).directory(directory.toFile());
            synchronized (lifecycle) {
                if (closed || canceled.getAsBoolean()) throw new CancellationException("Local command canceled before start");
                process = builder.start(); active.add(process);
            }
            // No prompts/input: commands requiring a terminal must use the visible terminal tool.
            process.getOutputStream().close();
            Process running = process;
            stdoutTask = readers.submit(() -> stdout.drain(running.getInputStream(), running::isAlive));
            stderrTask = readers.submit(() -> stderr.drain(running.getErrorStream(), running::isAlive));
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                rememberChildren(process, children);
                if (canceled.getAsBoolean() || Thread.currentThread().isInterrupted())
                    throw new CancellationException("Local background command canceled; partial effects may remain");
                if (process.waitFor(25, TimeUnit.MILLISECONDS)) break;
                if (System.nanoTime() >= deadline) { timedOut = true; break; }
            }
            if (timedOut) terminate(process, children);
            // A child can inherit a pipe after the shell exits. Never wait forever for that pipe.
            long drainDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            complete = await(stdoutTask, drainDeadline) & await(stderrTask, drainDeadline);
            complete &= stdout.complete() && stderr.complete();
            Optional<Integer> exit = timedOut ? Optional.empty() : Optional.of(process.exitValue());
            return new LocalCommandResult(stdout.text(), stderr.text(), exit,
                    timedOut ? "TIMED_OUT" : exit.orElseThrow() == 0 ? "SUCCEEDED" : "FAILED",
                    stdout.truncated() || stderr.truncated(), complete && !timedOut,
                    directory.toString(), shell, NOTICE);
        } finally {
            if (process != null) {
                if (process.isAlive() || canceled.getAsBoolean() || !complete) terminate(process, children);
                closePipe(process.getInputStream()); closePipe(process.getErrorStream());
                synchronized (lifecycle) { active.remove(process); }
            }
            if (stdoutTask != null) stdoutTask.cancel(true);
            if (stderrTask != null) stderrTask.cancel(true);
            slots.release();
        }
    }

    private static boolean await(Future<?> future, long deadline) throws InterruptedException {
        try { future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); return true; }
        catch (TimeoutException | ExecutionException ignored) { return false; }
    }
    private static void rememberChildren(Process process, Set<ProcessHandle> children) {
        if (children.size() >= 256) return;
        try (var descendants = process.descendants()) { descendants.limit(256 - children.size()).forEach(children::add); }
    }
    private static void terminate(Process process, Set<ProcessHandle> children) {
        rememberChildren(process, children);
        // Best-effort descendants; detached/reparented processes cannot be guaranteed without OS job control.
        for (ProcessHandle child : children) if (child.isAlive()) child.destroyForcibly();
        if (process.isAlive()) process.destroyForcibly();
    }
    private static void closePipe(InputStream stream) { try { stream.close(); } catch (IOException ignored) { } }

    static List<String> shellPrefix() {
        String executable = PtyLocalTerminalWorkflow.shellCommand()[0];
        return switch (PtyLocalTerminalWorkflow.shellFamily(new String[]{executable})) {
            case POWERSHELL -> List.of(executable, "-NoLogo", "-NoProfile", "-NonInteractive", "-Command");
            case COMMAND_PROMPT -> List.of(executable, "/d", "/s", "/c");
            default -> List.of(executable, "-c");
        };
    }
    @Override public void close() {
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            for (Process process : active) terminate(process, new LinkedHashSet<>());
        }
        readers.shutdownNow();
    }

    private static final class Capture {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit;
        private boolean truncated, complete;
        Capture(int limit) { this.limit = limit; }
        void drain(InputStream input, BooleanSupplier alive) {
            try {
                byte[] buffer = new byte[8192];
                while (!Thread.currentThread().isInterrupted()) {
                    int available = input.available();
                    if (available == 0) {
                        if (!alive.getAsBoolean()) { synchronized (this) { complete = true; } return; }
                        Thread.sleep(10); continue;
                    }
                    int count = input.read(buffer, 0, Math.min(buffer.length, available));
                    if (count < 0) { synchronized (this) { complete = true; } return; }
                    synchronized (this) {
                        int retained = Math.min(count, limit - bytes.size());
                        bytes.write(buffer, 0, retained); truncated |= retained < count;
                    }
                }
            } catch (IOException ignored) { /* Incomplete output is reported explicitly. */ }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
        synchronized boolean complete() { return complete; }
        synchronized boolean truncated() { return truncated; }
        synchronized String text() {
            // At a capacity cut, discard an incomplete trailing UTF-8 sequence instead of inventing a character.
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE);
            byte[] data = bytes.toByteArray();
            var input = java.nio.ByteBuffer.wrap(data);
            var output = java.nio.CharBuffer.allocate(data.length);
            decoder.decode(input, output, !truncated); output.flip(); return output.toString();
        }
    }
}
