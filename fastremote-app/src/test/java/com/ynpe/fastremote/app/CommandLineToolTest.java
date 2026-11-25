// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.*;
import com.ynpe.fastremote.storage.FastRemotePaths;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandLineToolTest {
    @TempDir Path directory;

    @Test void installedLegacyDefaultScriptFollowsGlobalChoiceButCustomScriptRemainsExplicit() {
        String previous = System.getProperty("jpackage.app-path");
        try {
            System.setProperty("jpackage.app-path", directory.resolve("FastRemote").toString());
            assertTrue(CommandLineTool.usesAutomaticDirectory("--auto-data-directory"));
            assertTrue(CommandLineTool.usesAutomaticDirectory(FastRemotePaths.userDataDirectory().toString()));
            assertFalse(CommandLineTool.usesAutomaticDirectory(directory.resolve("custom-data").toString()));
            System.clearProperty("jpackage.app-path");
            assertFalse(CommandLineTool.usesAutomaticDirectory(FastRemotePaths.userDataDirectory().toString()));
        } finally {
            if (previous == null) System.clearProperty("jpackage.app-path");
            else System.setProperty("jpackage.app-path", previous);
        }
    }

    @Test void resolvesRelativeUnicodeAndOptionLikeFileNamesWithoutExecutingThem() throws Exception {
        Path first = Files.writeString(directory.resolve("中文 file ' $().txt"), "first");
        Path second = Files.writeString(directory.resolve("-n"), "second");
        LaunchRequest request = CommandLineTool.files(List.of(first.getFileName().toString(), "-n"), directory);
        assertEquals(List.of(first, second), request.files());
        assertFalse(request.openLocalTerminal());
        assertEquals(request, CommandLineTool.files(List.of("--", first.toString(), second.toString()), directory));
    }

    @Test void rejectsMissingPathsAndOversizedBatchesBeforeDispatch() throws Exception {
        Path file = Files.writeString(directory.resolve("existing.txt"), "content");
        assertThrows(java.io.IOException.class, () -> CommandLineTool.files(List.of(file.toString(), "missing"), directory));
        assertEquals(List.of(directory), CommandLineTool.files(List.of("."), directory).localDirectories());
        assertThrows(IllegalArgumentException.class, () -> CommandLineTool.files(List.of(), directory));
        assertThrows(IllegalArgumentException.class, () -> CommandLineTool.files(
                java.util.Collections.nCopies(65, file.toString()), directory));
    }

    @Test void forwardsToTheAuthenticatedExistingInstanceAndReturnsWithoutStartingAnotherGui() throws Exception {
        String previous = System.getProperty(FastRemotePaths.DATA_DIRECTORY_PROPERTY);
        Path state = directory.resolve("state");
        Path file = Files.writeString(directory.resolve("edit me.txt"), "unchanged");
        Path folder = Files.createDirectory(directory.resolve("中文 folder"));
        System.setProperty(FastRemotePaths.DATA_DIRECTORY_PROPERTY, state.toString());
        try (SingleInstanceBroker broker = SingleInstanceBroker.startOrForward(state, LaunchRequest.parse(List.of()))) {
            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<LaunchRequest> request = new AtomicReference<>();
            broker.installConsumer(value -> { request.set(value); received.countDown(); });
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            assertEquals(0, CommandLineTool.run(List.of(file.toString(), folder.toString()), System.out, new PrintStream(errors)));
            assertTrue(received.await(3, TimeUnit.SECONDS));
            assertEquals(List.of(file), request.get().files());
            assertEquals(List.of(folder), request.get().localDirectories());
            assertFalse(request.get().openLocalTerminal());
            assertEquals("unchanged", Files.readString(file));
            assertEquals("", errors.toString());
        } finally {
            if (previous == null) System.clearProperty(FastRemotePaths.DATA_DIRECTORY_PROPERTY);
            else System.setProperty(FastRemotePaths.DATA_DIRECTORY_PROPERTY, previous);
        }
    }

    @Test void actualCliChildProcessForwardsRelativeFilesAndExits() throws Exception {
        Path state = directory.resolve("child-state");
        Path file = Files.writeString(directory.resolve("中文 child file.txt"), "unchanged");
        String classpath = java.util.stream.Stream.of(CommandLineTool.class, FastRemotePaths.class)
                .map(type -> {
                    try { return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(); }
                    catch (java.net.URISyntaxException failure) { throw new AssertionError(failure); }
                }).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        try (SingleInstanceBroker broker = SingleInstanceBroker.startOrForward(state, LaunchRequest.parse(List.of()))) {
            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<LaunchRequest> request = new AtomicReference<>();
            broker.installConsumer(value -> { request.set(value); received.countDown(); });
            boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
            var command = new java.util.ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin",
                    windows ? "java.exe" : "java").toString(), "-cp", classpath,
                    CommandLineTool.class.getName(), state.toString()));
            if (!windows) {
                Path script = CommandLineToolInstallation.writeLauncher(directory.resolve("cli"), command, false);
                command = new java.util.ArrayList<>(List.of(script.toString()));
            }
            command.add(file.getFileName().toString());
            command.add(".");
            Process child = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
            assertTrue(child.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, child.exitValue(), new String(child.getInputStream().readAllBytes()));
            assertTrue(received.await(3, TimeUnit.SECONDS));
            assertTrue(Files.isSameFile(file, request.get().files().getFirst()));
            assertTrue(Files.isSameFile(directory, request.get().localDirectories().getFirst()));
        }
    }

    @Test void coldStartLaunchesTheGuiWithAbsoluteFilesAndReturnsToTheShell() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        Path capture = directory.resolve("cold-start-arguments");
        Path fakeGui = directory.resolve("gui launcher");
        Files.writeString(fakeGui, "#!/bin/sh\nprintf '%s\\n' \"$@\" > "
                + CommandLineToolInstallation.shellQuote(capture.toString()) + "\n");
        fakeGui.toFile().setExecutable(true, true);
        Path file = Files.writeString(directory.resolve("cold file.txt"), "keep");
        String dataProperty = FastRemotePaths.DATA_DIRECTORY_PROPERTY;
        String previousData = System.getProperty(dataProperty);
        String previousLauncher = System.getProperty("jpackage.app-path");
        Path data = directory.resolve("cold-state");
        System.setProperty(dataProperty, data.toString());
        System.setProperty("jpackage.app-path", fakeGui.toString());
        try {
            assertEquals(0, CommandLineTool.run(List.of(file.toString(), directory.toString()), System.out, System.err));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!Files.exists(capture) && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(List.of("--user-data-dir=" + data, "-f", file.toString(), "--local-files", directory.toString()), Files.readAllLines(capture));
        } finally {
            if (previousData == null) System.clearProperty(dataProperty);
            else System.setProperty(dataProperty, previousData);
            if (previousLauncher == null) System.clearProperty("jpackage.app-path");
            else System.setProperty("jpackage.app-path", previousLauncher);
        }
    }

    @Test void invalidInputHasANonzeroExitCodeAndHelpDoesNotStartTheApp() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        assertEquals(0, CommandLineTool.run(List.of("--help"), new PrintStream(out), new PrintStream(error)));
        assertTrue(out.toString().contains("Usage: fr"));
        assertEquals(2, CommandLineTool.run(List.of(directory.resolve("absent").toString()),
                new PrintStream(out), new PrintStream(error)));
        assertTrue(error.toString().contains("Not a regular file"));
    }
}
