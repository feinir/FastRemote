// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LaunchRequestTest {
    @TempDir Path temporaryDirectory;

    @Test
    void localFilesFlagOpensDirectoriesWithoutRequestingATerminalAndSurvivesMerge() throws Exception {
        Path folder = Files.createDirectory(temporaryDirectory.resolve("中文 folder"));
        Path file = Files.writeString(temporaryDirectory.resolve("edit.txt"), "content");
        LaunchRequest request = LaunchRequest.parse(List.of("--local-files", folder.toString(), "-f", file.toString()));
        assertFalse(request.openLocalTerminal());
        assertTrue(request.opensWorkspace());
        assertEquals(List.of(folder), request.localDirectories());
        assertEquals(List.of(file), request.files());
        LaunchRequest merged = LaunchRequest.parse(List.of()).merge(request);
        assertEquals(request, merged);
        assertEquals(request, LaunchRequest.fromForwarded(false, null, List.of(file.toString()), List.of(folder.toString())));
        assertTrue(LaunchRequest.parse(List.of("--local-files", file.toString())).localDirectories().isEmpty());
    }

    @Test
    void nRequestsOneTerminalAndAcceptsAnExistingDirectory() {
        LaunchRequest request = LaunchRequest.parse(List.of("-n", temporaryDirectory.toString()));

        assertTrue(request.openLocalTerminal());
        assertTrue(request.forceNewTerminal());
        assertEquals(temporaryDirectory.toAbsolutePath().normalize(),
                request.initialDirectory().orElseThrow());
    }

    @Test
    void existingBareFileOpensAnEditorWhileNStillRequestsADefaultTerminal() throws Exception {
        Path file = Files.writeString(temporaryDirectory.resolve("example.txt"), "content");

        LaunchRequest request = LaunchRequest.parse(List.of(file.toString(), "-n"));

        assertTrue(request.initialDirectory().isEmpty());
        assertEquals(List.of(file.toAbsolutePath().normalize()), request.files());
        assertTrue(request.openLocalTerminal());
    }

    @Test
    void invalidPathStillRequestsATerminalAtTheDefaultLocation() {
        LaunchRequest request = LaunchRequest.parse(
                List.of("-n", temporaryDirectory.resolve("missing").toString()));

        assertTrue(request.openLocalTerminal());
        assertTrue(request.forceNewTerminal());
        assertTrue(request.initialDirectory().isEmpty());
    }

    @Test
    void trayTerminalRequestWithoutAFileManagerLocationUsesTheDefaultLocation() {
        LaunchRequest request = LaunchRequest.fromExternal(true, null);

        assertTrue(request.openLocalTerminal());
        assertTrue(request.forceNewTerminal());
        assertTrue(request.initialDirectory().isEmpty());
    }

    @Test
    void existingDirectoryWithoutNStillRequestsOneTerminalAtThatDirectory() {
        LaunchRequest request = LaunchRequest.parse(List.of(temporaryDirectory.toString()));

        assertTrue(request.openLocalTerminal());
        assertFalse(request.forceNewTerminal());
        assertEquals(temporaryDirectory.toAbsolutePath().normalize(),
                request.initialDirectory().orElseThrow());
    }

    @Test
    void desktopDirectoryFlagIsForwardedToTheLaunchRequest() {
        LaunchRequest request = LaunchRequest.parse(List.of("-d", temporaryDirectory.toString()));

        assertFalse(request.forceNewTerminal());
        assertEquals(temporaryDirectory.toAbsolutePath().normalize(),
                request.initialDirectory().orElseThrow());
    }

    @Test
    void directoryFlagKeepsTreatingAFileAsItsParentDirectory() throws Exception {
        Path file = Files.writeString(temporaryDirectory.resolve("terminal-location.txt"), "content");

        LaunchRequest request = LaunchRequest.parse(List.of("-d", file.toString()));

        assertTrue(request.files().isEmpty());
        assertEquals(temporaryDirectory.toAbsolutePath().normalize(),
                request.initialDirectory().orElseThrow());
        assertTrue(request.openLocalTerminal());
    }

    @Test
    void fileFlagAcceptsSeparateAndEqualsForms() throws Exception {
        Path first = Files.writeString(temporaryDirectory.resolve("first.txt"), "one");
        Path second = Files.writeString(temporaryDirectory.resolve("second.txt"), "two");

        LaunchRequest request = LaunchRequest.parse(List.of(
                "-f", first.toString(), "-f=" + second));

        assertEquals(List.of(first.toAbsolutePath().normalize(), second.toAbsolutePath().normalize()),
                request.files());
        assertFalse(request.openLocalTerminal());
        assertTrue(request.opensWorkspace());
    }

    @Test
    void invalidPathWithoutNDoesNotRequestATerminal() {
        LaunchRequest request = LaunchRequest.parse(
                List.of(temporaryDirectory.resolve("missing").toString()));

        assertFalse(request.openLocalTerminal());
        assertTrue(request.initialDirectory().isEmpty());
    }

    @Test
    void existingFileWithoutNRequestsOnlyAnEditor() throws Exception {
        Path file = Files.writeString(temporaryDirectory.resolve("standalone.txt"), "content");

        LaunchRequest request = LaunchRequest.parse(List.of(file.toString()));

        assertFalse(request.openLocalTerminal());
        assertTrue(request.opensWorkspace());
        assertEquals(List.of(file.toAbsolutePath().normalize()), request.files());
    }

    @Test
    void mergeKeepsNIndependentWhileTakingTheAvailableDirectory() {
        LaunchRequest merged = LaunchRequest.parse(List.of("-n"))
                .merge(LaunchRequest.parse(List.of(temporaryDirectory.toString())));

        assertTrue(merged.forceNewTerminal());
        assertEquals(temporaryDirectory.toAbsolutePath().normalize(),
                merged.initialDirectory().orElseThrow());
    }

    @Test
    void mergePreservesRepeatedFilesSoEachRequestCanOpenANewTab() throws Exception {
        Path file = Files.writeString(temporaryDirectory.resolve("repeat.txt"), "content");

        LaunchRequest merged = LaunchRequest.parse(List.of(file.toString()))
                .merge(LaunchRequest.parse(List.of("-f", file.toString())));

        assertEquals(List.of(file.toAbsolutePath().normalize(), file.toAbsolutePath().normalize()),
                merged.files());
    }
}
