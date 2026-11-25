// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StartupOptionsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void longOptionSelectsDataDirectoryWithoutBecomingALaunchLocation() {
        StartupOptions options = StartupOptions.parse(List.of(
                "--user-data-dir", temporaryDirectory.toString(), "-n"));

        assertEquals(temporaryDirectory.toAbsolutePath().normalize(), options.dataDirectory().orElseThrow());
        assertEquals(List.of("-n"), options.applicationArguments());
    }

    @Test
    void acceptsChromeStyleForms() {
        assertEquals(temporaryDirectory.toAbsolutePath().normalize(), StartupOptions.parse(List.of(
                "--user-data-dir=" + temporaryDirectory)).dataDirectory().orElseThrow());
        assertEquals(temporaryDirectory.toAbsolutePath().normalize(), StartupOptions.parse(List.of(
                "--user-data-dir", temporaryDirectory.toString())).dataDirectory().orElseThrow());
    }

    @Test
    void preservesTerminalLocationArguments() {
        Path terminalDirectory = temporaryDirectory.resolve("terminal");
        StartupOptions options = StartupOptions.parse(List.of(
                terminalDirectory.toString(), "--user-data-dir", temporaryDirectory.toString()));

        assertEquals(List.of(terminalDirectory.toString()), options.applicationArguments());
    }

    @Test
    void rejectsMissingBlankAndDuplicateDataDirectories() {
        assertThrows(IllegalArgumentException.class, () -> StartupOptions.parse(List.of("--user-data-dir")));
        assertThrows(IllegalArgumentException.class, () -> StartupOptions.parse(List.of("--user-data-dir=")));
        assertThrows(IllegalArgumentException.class, () -> StartupOptions.parse(List.of(
                "--user-data-dir", temporaryDirectory.toString(), "--user-data-dir", temporaryDirectory.toString())));
    }
}
