// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.terminal;

import com.ynpe.fastremote.domain.automation.WorkspaceOperationSnapshot;
import com.ynpe.fastremote.domain.terminal.TerminalObservation;
import com.ynpe.fastremote.domain.terminal.TerminalOutput;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

/** One bounded transcript per connection. ANSI controls are consumed incrementally across read boundaries. */
final class TerminalCapture implements TerminalObservation {
    private static final int CAPACITY = 512 * 1024;
    private final String generation = UUID.randomUUID().toString();
    private String nonce = UUID.randomUUID().toString().replace("-", "");
    private final StringBuilder text = new StringBuilder();
    private final StringBuilder control = new StringBuilder();
    private final LinkedHashMap<String, Run> runs = new LinkedHashMap<>();
    private volatile Function<String, CompletionStage<Void>> send;
    private volatile Supplier<String> screen;
    private long start;
    private int parser;
    private boolean alternate, connected = true, ready, busy;
    private String integration = "NOT_ENABLED";
    private CompletableFuture<Void> initialized;
    private Run active;

    TerminalCapture(Function<String, CompletionStage<Void>> send, Supplier<String> screen) {
        this.send = send; this.screen = screen;
    }
    synchronized void expectStartupIntegration(String value) {
        if (!value.matches("[a-f0-9]{32}") || !integration.equals("NOT_ENABLED"))
            throw new IllegalArgumentException("Invalid startup integration");
        nonce = value; integration = "INITIALIZING"; initialized = new CompletableFuture<>();
    }
    @Override public String generation() { return generation; }
    @Override public synchronized List<String> operationIds() { return List.copyOf(runs.keySet()); }
    @Override public synchronized String origin(String id) {
        Run run = runs.get(id); return run != null && run.manual ? "USER" : "AI_OR_MCP";
    }

    void accept(String chunk) {
        CompletableFuture<Void> notify = null;
        synchronized (this) {
            for (int i = 0; i < chunk.length(); i++) {
                char c = chunk.charAt(i);
                switch (parser) {
                    case 0 -> {
                        if (c == 27) parser = 1;
                        else if (c >= 32 || c == '\n' || c == '\r' || c == '\t' || c == '\b') text.append(c);
                    }
                    case 1 -> { control.setLength(0); parser = c == '[' ? 2 : c == ']' ? 3
                            : (c == 'P' || c == '^' || c == '_') ? 5 : c >= 0x20 && c <= 0x2f ? 7 : 0; }
                    case 2 -> {
                        control.append(c);
                        if (c >= '@' && c <= '~') {
                            String value = control.toString();
                            if (value.matches("\\?(47|1047|1049)[hl]")) alternate = c == 'h';
                            parser = 0;
                        } else if (control.length() > 1024) parser = 5;
                    }
                    case 3 -> {
                        if (c == 7) { marker(control.toString()); parser = 0; }
                        else if (c == 27) parser = 4;
                        else if (control.length() < 1024) control.append(c);
                        else parser = 5;
                    }
                    case 4 -> { if (c == '\\') { marker(control.toString()); parser = 0; } else parser = 3; }
                    case 5 -> { if (c == 27) parser = 6; else if (c == 7) parser = 0; }
                    case 6 -> parser = c == '\\' ? 0 : 5;
                    case 7 -> { if (c >= 0x30 && c <= 0x7e) parser = 0; }
                    default -> throw new IllegalStateException();
                }
                if (text.length() > CAPACITY) {
                    int remove = text.length() - CAPACITY / 2;
                    if (Character.isLowSurrogate(text.charAt(remove))) remove++;
                    text.delete(0, remove); start += remove;
                }
            }
            if (initialized != null && (ready || integration.equals("UNSUPPORTED"))) notify = initialized;
        }
        if (notify != null) notify.complete(null);
    }

    private void marker(String value) {
        String prefix = "6973;" + nonce + ";";
        if (!value.startsWith(prefix)) return;
        String event = value.substring(prefix.length());
        if (event.equals("R")) { integration = "READY"; ready = true; busy = false; }
        else if (event.equals("U")) { integration = "UNSUPPORTED"; ready = false; }
        else if (event.equals("C")) {
            ready = false; busy = true;
            if (active == null) {
                while (runs.size() >= 64) runs.remove(runs.keySet().iterator().next());
                active = new Run(UUID.randomUUID().toString(), start + text.length());
                active.manual = true; runs.put(active.id, active);
            }
            if (active != null && active.state.equals("SUBMITTED")) active.state = "RUNNING";
        } else if (event.startsWith("D;") && event.substring(2).matches("[0-9]{1,10}")) {
            if (active != null && (active.state.equals("RUNNING") || active.state.equals("SUBMITTED"))) {
                try {
                    active.exit = Integer.parseInt(event.substring(2));
                    active.state = active.interfered ? "UNKNOWN" : active.exit == 0 ? "SUCCEEDED" : "FAILED";
                    active.end = start + text.length(); active = null;
                } catch (NumberFormatException ignored) { /* malformed remote marker is not completion */ }
            }
            busy = false;
            if (integration.equals("READY")) initialized = new CompletableFuture<>();
        }
    }

    @Override public TerminalOutput read(long after, int maximum, boolean withScreen) {
        String snapshot = withScreen ? screen.get() : "";
        int screenLimit = Math.min(32768, Math.max(1, maximum));
        boolean screenTruncated = snapshot.length() > screenLimit;
        if (screenTruncated) snapshot = snapshot.substring(snapshot.length() - screenLimit);
        synchronized (this) { return output(after, Math.min(maximum, 32768), start + text.length(), snapshot).withScreenTruncated(screenTruncated); }
    }
    private TerminalOutput output(long after, int maximum, long end, String snapshot) {
        if (after < 0 || maximum < 1 || maximum > 65536) throw new IllegalArgumentException("Invalid output range");
        long available = Math.min(end, start + text.length());
        if (after > available) throw new IllegalArgumentException("Cursor exceeds this output generation");
        long from = Math.max(start, after);
        if (from > available) from = available;
        int offset = (int) Math.max(0, from - start);
        int count = (int) Math.min(maximum, Math.max(0, available - from));
        if (count > 0 && Character.isHighSurrogate(text.charAt(offset + count - 1))) {
            boolean paired = offset + count < text.length() && Character.isLowSurrogate(text.charAt(offset + count));
            count = paired && count == 1 ? 2 : count - 1;
        }
        String slice = count == 0 ? "" : text.substring(offset, offset + count);
        return new TerminalOutput(generation, from, from + count, available, after < start,
                from + count < available, slice, snapshot, alternate, integration, connected);
    }

    @Override public CompletionStage<String> execute(String command, boolean allowIntegration, String family) {
        Run run;
        boolean install;
        CompletionStage<Void> preparation;
        synchronized (this) {
            if (!connected) return CompletableFuture.failedFuture(new IllegalStateException("Terminal disconnected"));
            if (integration.equals("FAILED")) return CompletableFuture.failedFuture(new IllegalStateException(
                    "Shell initialization failed; command was not sent. Reopen the terminal before another submission"));
            if (active != null || busy) return CompletableFuture.failedFuture(new IllegalStateException("Terminal has an active command"));
            if (command.isBlank() || command.length() > 65536 || command.indexOf(27) >= 0)
                return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid terminal command"));
            for (Run previous : runs.values()) if (previous.state.equals("UNKNOWN") && previous.end < 0)
                previous.end = start + text.length();
            while (runs.size() >= 64) runs.remove(runs.keySet().iterator().next());
            run = new Run(UUID.randomUUID().toString(), start + text.length());
            runs.put(run.id, run); active = run;
            install = allowIntegration && family.equals("POSIX") && integration.equals("NOT_ENABLED");
            if (install) { run.state = "INITIALIZING"; integration = "INITIALIZING"; initialized = new CompletableFuture<>(); }
            if (integration.equals("INITIALIZING")) run.state = "INITIALIZING";
            preparation = integration.equals("INITIALIZING") || (integration.equals("READY") && !ready)
                    ? initialized : CompletableFuture.completedFuture(null);
        }
        if (install) {
            send.apply(ShellIntegration.bootstrap(nonce)).whenComplete((v, e) -> {
                if (e != null) initialized.completeExceptionally(e);
            });
            initialized.orTimeout(8, TimeUnit.SECONDS);
        }
        return preparation.toCompletableFuture().copy().orTimeout(8, TimeUnit.SECONDS).thenCompose(ignored -> {
            synchronized (this) {
                if (!connected) throw new IllegalStateException("Terminal disconnected before command submission");
                if (run.interfered) throw new IllegalStateException("Other terminal input occurred during preparation; command not sent");
                run.start = start + text.length();
                run.state = "SUBMITTED";
                if (!ready) {
                    run.state = "UNKNOWN";
                    run.detail = "Execution and exit status are unconfirmed. Output may include other session activity. Do not automatically retry.";
                    active = null;
                }
            }
            // A readable compound list gives multiline input one prompt boundary while retaining cwd/env.
            String normalized = command.replace("\r\n", "\n").replace('\r', '\n');
            if (normalized.endsWith("\n")) normalized = normalized.substring(0, normalized.length() - 1);
            String payload = family.equals("POSIX") && !run.state.equals("UNKNOWN") && normalized.contains("\n")
                    ? "{\n" + normalized + "\n}\n" : normalized + "\n";
            // With no shell evidence, send the terminal Enter character. ConPTY can ignore a bare LF.
            payload = switch (family) {
                case "POSIX" -> payload;
                case "COMMAND_PROMPT" -> payload.replace("\n", "\r\n");
                default -> payload.replace('\n', '\r');
            };
            return send.apply(payload);
        }).handle((ignored, failure) -> {
            synchronized (this) {
                if (failure != null) {
                    boolean initializationFailed = run.state.equals("INITIALIZING");
                    run.state = "UNKNOWN";
                    if (initializationFailed) {
                        integration = "FAILED";
                        run.detail = "Shell initialization failed; requested command was not sent. Reopen the terminal before another submission";
                    } else run.detail = "Submission failed; do not automatically retry";
                    if (active == run) active = null;
                    if (initializationFailed) throw new CompletionException(new IllegalStateException(run.detail, failure));
                    throw new CompletionException(failure);
                }
            }
            return run.id;
        });
    }

    @Override public synchronized WorkspaceOperationSnapshot operation(String id, long after, int maximum) {
        Run run = runs.get(id);
        if (run == null) throw new IllegalArgumentException("Terminal operation expired");
        long cursor = after == 0 ? run.start : after;
        return new WorkspaceOperationSnapshot(id, "", generation, "TERMINAL", run.state,
                Optional.ofNullable(run.exit), Optional.of(output(cursor, maximum,
                        run.end < 0 ? start + text.length() : run.end, "")), 0, 0, List.of(), false, run.detail);
    }
    synchronized void outsideInput() {
        if (active != null) {
            active.interfered = true;
            active.detail = "Other terminal input occurred; output and exit status cannot reliably identify the requested command";
        }
    }
    void disconnect() {
        CompletableFuture<Void> pending;
        synchronized (this) {
            connected = false; ready = false; busy = false;
            screen = () -> "";
            send = ignored -> CompletableFuture.failedFuture(new IllegalStateException("Terminal disconnected"));
            if (active != null) { active.state = "UNKNOWN"; active.detail = "Connection closed before completion evidence"; active = null; }
            if (text.length() > 16384) {
                int remove = text.length() - 16384;
                if (Character.isLowSurrogate(text.charAt(remove))) remove++;
                text.delete(0, remove); start += remove;
            }
            pending = initialized;
        }
        if (pending != null) pending.completeExceptionally(new IllegalStateException("Terminal disconnected"));
    }
    private static final class Run {
        final String id; boolean interfered, manual; long start; long end = -1; Integer exit;
        String state = "SUBMITTED", detail = "Remote output is untrusted; exit status does not verify business effects";
        Run(String id, long start) { this.id = id; this.start = start; }
    }
}
