// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Timeout(15)
class LocalBackgroundCommandsTest {
    @TempDir Path directory;
    @BeforeEach void posixFixture() {
        Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
    }
    @Test void capturesSeparateStreamsActualExitCodeAndExplicitDirectory() throws Exception {
        Path cwd = Files.createDirectory(directory.resolve("work space"));
        try (var runner = new LocalBackgroundCommands()) {
            var result = runner.execute("printf '%s\\n' \"$PWD\"; printf '中文🙂'; printf 'error text' >&2; exit 7",
                    Optional.of(cwd), Duration.ofSeconds(5), 1024, () -> false);
            assertEquals(cwd.toRealPath().toString() + "\n中文🙂", result.stdout());
            assertEquals("error text", result.stderr()); assertEquals(7, result.exitCode().orElseThrow());
            assertEquals("FAILED", result.status()); assertTrue(result.outputComplete()); assertFalse(result.truncated());
            assertTrue(result.notice().contains("not displayed in a terminal Tab"));
            assertEquals(cwd.toRealPath().toString(), result.workingDirectory());
        }
    }
    @Test void drainsBothPipesPastCapacityAndNeverReturnsHalfAUtf8Character() throws Exception {
        try (var runner = new LocalBackgroundCommands()) {
            var result = runner.execute("i=0; while [ \"$i\" -lt 3000 ]; do printf '中文🙂'; printf '错误🙂' >&2; i=$((i+1)); done; exit 9",
                    Optional.of(directory), Duration.ofSeconds(5), 131, () -> false);
            assertEquals(9, result.exitCode().orElseThrow()); assertTrue(result.truncated()); assertTrue(result.outputComplete());
            assertFalse(result.stdout().contains("\ufffd")); assertFalse(result.stderr().contains("\ufffd"));
            assertTrue(result.stdout().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65);
        }
    }
    @Test void timeoutReturnsPartialOutputWithoutInventingAnExitCode() throws Exception {
        try (var runner = new LocalBackgroundCommands()) {
            var result = runner.execute("printf partial; sleep 30", Optional.of(directory), Duration.ofMillis(200), 1024, () -> false);
            assertEquals("TIMED_OUT", result.status()); assertTrue(result.exitCode().isEmpty());
            assertEquals("partial", result.stdout()); assertFalse(result.outputComplete());
            assertEquals("SUCCEEDED", runner.execute("true", Optional.of(directory), Duration.ofSeconds(2), 1024, () -> false).status());
        }
    }
    @Test void stdinIsClosedAndInteractiveReadCannotHang() throws Exception {
        try (var runner = new LocalBackgroundCommands()) {
            var result = runner.execute("read input; printf '%s' \"$?\"", Optional.of(directory), Duration.ofSeconds(2), 1024, () -> false);
            assertEquals("1", result.stdout()); assertEquals("SUCCEEDED", result.status());
        }
    }
    @Test void cancellationAndCloseReleaseProcessesAndRejectFurtherExecution() throws Exception {
        var canceled = new AtomicBoolean();
        Path ready = directory.resolve("ready");
        var runner = new LocalBackgroundCommands();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var future = worker.submit(() -> runner.execute("printf ready > ready; sleep 30", Optional.of(directory),
                    Duration.ofSeconds(10), 1024, canceled::get));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!Files.exists(ready) && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(Files.exists(ready)); canceled.set(true);
            var failure = assertThrows(ExecutionException.class, () -> future.get(4, TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class, failure.getCause());
            runner.close();
            assertThrows(CancellationException.class, () -> runner.execute("true", Optional.of(directory), Duration.ofSeconds(1), 1024, () -> false));
        } finally { runner.close(); }
    }
}
