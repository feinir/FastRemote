// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.terminal;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TerminalCaptureTest {
    @Test void stripsSplitControlsBoundsHistoryAndKeepsUnicode() {
        var capture = new TerminalCapture(s -> CompletableFuture.completedFuture(null), () -> "screen");
        capture.accept("中文\u001b[3"); capture.accept("1m红色\u001b[0m\u001b]0;secret title");
        capture.accept("\u0007\n🙂\u001b[?1049h");
        var first = capture.read(0, 1000, true);
        assertEquals("中文红色\n🙂", first.text()); assertTrue(first.alternateScreen());
        assertEquals("screen", first.screen());
        capture.accept("x".repeat(600_000));
        var next = capture.read(first.nextCursor(), 16, false);
        assertTrue(next.historyLost()); assertTrue(next.truncated()); assertEquals(16, next.text().length());
        assertThrows(IllegalArgumentException.class, () -> capture.read(Long.MAX_VALUE, 1, false));
    }

    @Test void waitsForSplitEmojiInsteadOfReturningAnUnpairedSurrogate() {
        var capture = new TerminalCapture(s -> CompletableFuture.completedFuture(null), () -> "");
        capture.accept("\uD83D");
        assertEquals("", capture.read(0, 1, false).text());
        capture.accept("\uDE42");
        assertEquals("🙂", capture.read(0, 1, false).text());
        assertEquals(2, capture.read(0, 1, false).nextCursor());
    }

    @Test void unintegratedExecutionNeverInventsSuccessAndUsesSeparateGenerations() {
        var sent = new ArrayList<String>();
        var capture = new TerminalCapture(s -> { sent.add(s); return CompletableFuture.completedFuture(null); }, () -> "");
        String id = capture.execute("false", false, "POSIX").toCompletableFuture().join();
        capture.accept("$ ");
        assertEquals("UNKNOWN", capture.operation(id, 0, 1024).state());
        assertTrue(capture.operation(id, 0, 1024).exitCode().isEmpty());
        assertEquals(List.of("false\n"), sent);
        assertNotEquals(capture.generation(), new TerminalCapture(s -> null, () -> "").generation());
    }

    @Test void windowsAndUndetectedShellsSubmitEachLineWithCarriageReturn() {
        for (var expected : Map.of("COMMAND_PROMPT", "echo one\r\necho two\r\n",
                "POWERSHELL", "echo one\recho two\r", "UNKNOWN", "echo one\recho two\r").entrySet()) {
            var sent = new ArrayList<String>();
            var capture = new TerminalCapture(payload -> {
                sent.add(payload);
                return CompletableFuture.completedFuture(null);
            }, () -> "");
            String id = capture.execute("echo one\r\necho two\n", true, expected.getKey()).toCompletableFuture().join();
            assertEquals(List.of(expected.getValue()), sent, expected.getKey());
            var snapshot = capture.operation(id, 0, 1024);
            assertEquals("UNKNOWN", snapshot.state());
            assertTrue(snapshot.exitCode().isEmpty());
            assertTrue(snapshot.detail().contains("Execution and exit status are unconfirmed"));
        }
    }

    @Test void wrongNonceCannotCompleteRunAndDisconnectIsUnknown() {
        AtomicReference<TerminalCapture> ref = new AtomicReference<>();
        var capture = new TerminalCapture(s -> {
            if (s.startsWith("if [")) {
                var matcher = java.util.regex.Pattern.compile("6973;([a-f0-9]+);").matcher(s); assertTrue(matcher.find());
                ref.get().accept("\u001b]6973;" + matcher.group(1) + ";R\u0007");
            }
            return CompletableFuture.completedFuture(null);
        }, () -> ""); ref.set(capture);
        String id = capture.execute("sleep 10", true, "POSIX").toCompletableFuture().join();
        capture.accept("\u001b]6973;wrong;D;0\u0007");
        assertEquals("SUBMITTED", capture.operation(id, 0, 1024).state());
        assertThrows(CompletionException.class, () -> capture.execute("echo again", true, "POSIX").toCompletableFuture().join());
        capture.disconnect();
        assertEquals("UNKNOWN", capture.operation(id, 0, 1024).state());
    }

    @Test void mixedInputIsUnknownAndManualCommandsAreDiscoverable() throws Exception {
        AtomicReference<TerminalCapture> ref = new AtomicReference<>();
        AtomicReference<String> nonce = new AtomicReference<>();
        var capture = new TerminalCapture(s -> {
            if (s.startsWith("if [")) {
                var matcher = java.util.regex.Pattern.compile("6973;([a-f0-9]+);").matcher(s); assertTrue(matcher.find());
                nonce.set(matcher.group(1)); ref.get().accept("\u001b]6973;" + nonce.get() + ";R\u0007");
            }
            return CompletableFuture.completedFuture(null);
        }, () -> ""); ref.set(capture);
        String id = capture.execute("echo controlled", true, "POSIX").toCompletableFuture().join();
        String prefix = "\u001b]6973;" + nonce.get() + ";";
        capture.accept(prefix + "C\u0007"); capture.outsideInput();
        capture.accept("mixed\n" + prefix + "D;0\u0007" + prefix + "R\u0007");
        assertEquals("UNKNOWN", capture.operation(id, 0, 1024).state());
        capture.accept(prefix + "C\u0007manual-output\n" + prefix + "D;1\u0007" + prefix + "R\u0007");
        String manual = capture.operationIds().getLast();
        assertEquals("USER", capture.origin(manual));
        assertEquals("FAILED", capture.operation(manual, 0, 1024).state());
        assertTrue(capture.operation(manual, 0, 1024).output().orElseThrow().text().contains("manual-output"));
    }

    @Test void failedBootstrapDoesNotFeedMoreCommandsIntoAnUncertainShell() {
        var sent = new ArrayList<String>();
        var capture = new TerminalCapture(s -> {
            sent.add(s);
            return CompletableFuture.failedFuture(new java.io.IOException("closed input"));
        }, () -> "");
        assertThrows(CompletionException.class, () -> capture.execute("touch should-not-run", true, "POSIX")
                .toCompletableFuture().join());
        assertEquals("FAILED", capture.read(0, 4096, false).integration());
        assertThrows(CompletionException.class, () -> capture.execute("another-command", false, "POSIX")
                .toCompletableFuture().join());
        assertEquals(1, sent.size());
        assertTrue(capture.operation(capture.operationIds().getFirst(), 0, 4096).detail().contains("was not sent"));
    }

    @Test void realBashAndZshReportSuccessFailureAndPreserveCwd() throws Exception {
        for (String shell : List.of("/bin/bash", "/bin/zsh")) {
            if (!java.nio.file.Files.isExecutable(java.nio.file.Path.of(shell))) continue;
            List<String> command = shell.endsWith("bash") ? List.of(shell, "--noprofile", "--norc", "-i") : List.of(shell, "-f", "-i");
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try {
                var writer = new java.io.OutputStreamWriter(process.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8);
                var capture = new TerminalCapture(s -> {
                    try { synchronized (writer) { writer.write(s.replace('\r', '\n')); writer.flush(); } return CompletableFuture.completedFuture(null); }
                    catch (Exception e) { return CompletableFuture.failedFuture(e); }
                }, () -> "");
                Thread reader = Thread.ofPlatform().daemon().start(() -> {
                    try (var input = new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                        char[] chars = new char[1024]; int n;
                        while ((n = input.read(chars)) >= 0) capture.accept(new String(chars, 0, n));
                    } catch (java.io.IOException ignored) { }
                    finally { capture.disconnect(); }
                });
                String first;
                try { first = capture.execute("cd /tmp; printf '中文-result\\n'", true, "POSIX").toCompletableFuture().get(10, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new AssertionError(shell + ": " + capture.read(0, 65536, false).text(), failure); }
                awaitState(capture, first, "SUCCEEDED");
                assertTrue(capture.operation(first, 0, 65536).output().orElseThrow().text().contains("中文-result"));
                String second = capture.execute("false", true, "POSIX").toCompletableFuture().get(2, TimeUnit.SECONDS);
                awaitState(capture, second, "FAILED");
                assertEquals(1, capture.operation(second, 0, 1024).exitCode().orElseThrow());
                String third = capture.execute("pwd", true, "POSIX").toCompletableFuture().get(2, TimeUnit.SECONDS);
                awaitState(capture, third, "SUCCEEDED");
                assertTrue(capture.operation(third, 0, 1024).output().orElseThrow().text().contains("/tmp"));
                String multiline = capture.execute("printf 'line1\\n'\nprintf 'line2\\n'\nfalse", true, "POSIX").toCompletableFuture().get(2, TimeUnit.SECONDS);
                awaitState(capture, multiline, "FAILED");
                String malformed = capture.execute("echo )", true, "POSIX").toCompletableFuture().get(2, TimeUnit.SECONDS);
                awaitState(capture, malformed, "FAILED");
                process.destroy(); reader.join(2000);
            } finally { process.destroyForcibly(); }
        }
    }
    private static void awaitState(TerminalCapture capture, String id, String expected) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end && !capture.operation(id, 0, 1024).terminalState()) Thread.sleep(10);
        assertEquals(expected, capture.operation(id, 0, 65536).state(), () -> capture.read(0, 65536, false).text());
    }
}
