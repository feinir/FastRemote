// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandLineToolInstallationTest {
    @TempDir Path directory;

    @Test void launcherPreservesArgumentsAndExitStatusWithoutShellEvaluation() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        Path bin = directory.resolve("工具 ' $ bin");
        Path target = directory.resolve("capture ' arguments");
        Files.writeString(target, "#!/bin/sh\nprintf '%s\\n' \"$@\"\nexit 7\n");
        target.toFile().setExecutable(true, true);
        Path script = CommandLineToolInstallation.writeLauncher(bin, List.of(target.toString(), "fixed ' argument"), false);
        List<String> args = List.of("中文 file.txt", "$(touch forbidden)", "-n", "a;b", "line\nend");
        java.util.ArrayList<String> command = new java.util.ArrayList<>(List.of(script.toString()));
        command.addAll(args);
        Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS));
        assertEquals(7, process.exitValue());
        assertEquals("fixed ' argument\n" + String.join("\n", args) + "\n",
                new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        assertFalse(Files.exists(directory.resolve("forbidden")));
    }

    @Test void refusesToOverwriteAnUnrelatedCommandOrSymlink() throws Exception {
        Path bin = Files.createDirectories(directory.resolve("bin"));
        Path original = Files.writeString(bin.resolve("fr"), "my own command");
        assertThrows(java.io.IOException.class, () -> CommandLineToolInstallation.writeLauncher(bin, List.of("java"), false));
        assertEquals("my own command", Files.readString(original));
        Assumptions.assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Files.delete(original);
        Path target = Files.writeString(directory.resolve("other"), "other command");
        Files.createSymbolicLink(original, target);
        assertThrows(java.io.IOException.class, () -> CommandLineToolInstallation.writeLauncher(bin, List.of("java"), false));
        assertEquals("other command", Files.readString(target));
    }

    @Test void installationPreservesProfilesIsIdempotentAndDoesNotMaskTheLoginProfile() throws Exception {
        Path home = directory.resolve("home");
        Files.createDirectories(home);
        Files.writeString(home.resolve(".profile"), "# keep existing settings\nexport ORIGINAL=1");
        Path bin = home.resolve(".local/bin");
        CommandLineToolInstallation.installPosixPath(home, bin, null);
        String first = Files.readString(home.resolve(".profile"));
        CommandLineToolInstallation.installPosixPath(home, bin, null);
        assertEquals(first, Files.readString(home.resolve(".profile")));
        assertTrue(first.startsWith("# keep existing settings\nexport ORIGINAL=1\n"));
        assertFalse(Files.exists(home.resolve(".bash_profile")));
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        Process process = new ProcessBuilder("/bin/sh", "-c", ". \"$1\"; printf '%s' \"$PATH\"", "sh", home.resolve(".profile").toString()).start();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        assertTrue(new String(process.getInputStream().readAllBytes()).startsWith(bin + ":"));
    }

    @Test void builtInCommandRemainsAvailableAfterMacLoginShellInitialization() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("mac"));
        Path bin = directory.resolve("bin with space");
        CommandLineToolInstallation.writeLauncher(bin, List.of("/usr/bin/printf", "%s", "FR_READY"), false);
        ProcessBuilder builder = new ProcessBuilder("/bin/zsh", "-lic", "fr");
        CommandLineToolInstallation.prependPath(builder.environment(), bin, false);
        Process process = builder.start();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        assertEquals("FR_READY", new String(process.getInputStream().readAllBytes()));
    }

    @Test void bashCommandStillWorksWhenAProfileReplacesPath() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        Path bin = directory.resolve("bash cli");
        CommandLineToolInstallation.writeLauncher(bin, List.of("/usr/bin/printf", "%s", "FR_BASH"), false);
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-lc", "PATH=/usr/bin:/bin; fr");
        CommandLineToolInstallation.configureShell(builder.environment(), new String[]{"/bin/bash", "-l"}, bin);
        Process child = builder.start();
        assertTrue(child.waitFor(5, TimeUnit.SECONDS));
        assertEquals(0, child.exitValue(), new String(child.getErrorStream().readAllBytes()));
        assertEquals("FR_BASH", new String(child.getInputStream().readAllBytes()));
    }

    @Test void windowsUsesTheExistingPathKeyWithoutCreatingACaseVariant() {
        var env = new HashMap<String, String>();
        env.put("Path", "C:\\Windows");
        CommandLineToolInstallation.prependPath(env, directory, true);
        assertEquals(1, env.size());
        assertEquals(directory + ";C:\\Windows", env.get("Path"));
    }
}
