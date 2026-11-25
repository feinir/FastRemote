// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class CodexExecutableTest {
    @TempDir Path directory;

    @Test
    void discoversRegisteredMsixVersionsAndArchitecturesWithoutHardcodedWindowsAppsPaths() throws Exception {
        for (String packageName : List.of("OpenAI.Codex_26.915.4065.0_arm64__publisher",
                "OpenAI.Codex_99.1.0.0_x64__different-publisher")) {
            Path root = directory.resolve("Windows Apps").resolve(packageName);
            Path source = executable(root.resolve("app/resources/codex.exe"), packageName);
            executable(root.resolve("app/Codex.exe"), "desktop UI");
            Path result = Path.of(resolve("", Map.of(), "Windows 11", "aarch64", List.of(root)));
            assertEquals(directory.resolve("cache/codex.exe"), result);
            assertEquals(Files.readString(source), Files.readString(result));
        }
        try (var files = Files.list(directory.resolve("cache"))) {
            assertEquals(List.of("codex.exe"), files.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void neverUsesDesktopLauncherWhenPackageCliIsMissing() throws Exception {
        Path root = directory.resolve("package");
        executable(root.resolve("app/Codex.exe"), "desktop UI");
        assertEquals("codex", resolve("", Map.of(), "Windows 11", "amd64", List.of(root)));
    }

    @Test
    void explicitOverridesAvoidDiscovery() throws Exception {
        assertEquals("configured", CodexExecutable.resolve("configured",
                Map.of("FASTREMOTE_CODEX_EXECUTABLE", "environment"), "Windows 11", "amd64", directory,
                directory.resolve("cache"), () -> { throw new AssertionError("unnecessary package query"); }));
        assertEquals("environment", resolve(" ", Map.of("FASTREMOTE_CODEX_EXECUTABLE", "environment"),
                "Windows 11", "amd64", List.of()));
    }

    @Test
    void findsWindowsNpmNativePayloadInsteadOfCmdShim() throws Exception {
        Path roaming = directory.resolve("Roaming Data");
        Path binary = executable(roaming.resolve("npm/node_modules/@openai/codex/node_modules/"
                + "@openai/codex-win32-arm64/vendor/aarch64-pc-windows-msvc/bin/codex.exe"), "native");
        executable(roaming.resolve("npm/codex.cmd"), "shim");
        assertEquals(binary.toString(), resolve("", Map.of("APPDATA", roaming.toString()), "Windows 11", "aarch64", List.of()));
    }

    @Test
    void findsLinuxUserLocalInstallationWithEmptyDesktopPath() throws Exception {
        Path binary = executable(directory.resolve(".local/bin/codex"), "native");
        assertEquals(binary.toString(), resolve("", Map.of("PATH", ""), "Linux", "aarch64", List.of()));
    }

    @Test
    void findsNvmNativePayloadWithoutStartingShellOrNeedingNodeOnPath() throws Exception {
        Path node = directory.resolve(".nvm/versions/node/v22.18.0");
        Path binary = executable(node.resolve("lib/node_modules/@openai/codex/node_modules/"
                + "@openai/codex-linux-arm64/vendor/aarch64-unknown-linux-musl/bin/codex"), "native");
        executable(node.resolve("bin/codex"), "#!/usr/bin/env node");
        assertEquals(binary.toString(), resolve("", Map.of(), "Linux", "aarch64", List.of()));
    }

    @Test
    void supportsLegacyNpmVendorLayoutAndCaseInsensitiveWindowsPath() throws Exception {
        Path bin = directory.resolve("custom bin");
        Path binary = executable(bin.resolve("node_modules/@openai/codex/vendor/x86_64-pc-windows-msvc/codex/codex.exe"), "native");
        assertEquals(binary.toString(), resolve("", Map.of("Path", bin.toString()), "Windows 10", "amd64", List.of()));
    }

    @Test
    void reusesVerifiedCopyAndReplacesCorruptionAndUpgradesWithoutAccumulatingVersions() throws Exception {
        Path source = executable(directory.resolve("installed.exe"), "version 1");
        Path cached = CodexExecutable.cacheInstalledComponent(source, directory.resolve("cache"));
        var stamp = Files.getLastModifiedTime(cached);
        assertEquals(cached, CodexExecutable.cacheInstalledComponent(source, directory.resolve("cache")));
        assertEquals(stamp, Files.getLastModifiedTime(cached));
        Files.writeString(cached, "corrupt");
        CodexExecutable.cacheInstalledComponent(source, directory.resolve("cache"));
        assertEquals("version 1", Files.readString(cached));
        Files.writeString(source, "version 2");
        CodexExecutable.cacheInstalledComponent(source, directory.resolve("cache"));
        assertEquals("version 2", Files.readString(cached));
    }

    @Test
    void missingPackageQueryCommandFallsBackAndLeavesNoCache() throws Exception {
        assertEquals(List.of(), CodexExecutable.queryPackageRoots(List.of(directory.resolve("missing.exe").toString())));
        assertEquals("codex", resolve("", Map.of(), "Windows 11", "amd64", List.of()));
        assertFalse(Files.exists(directory.resolve("cache")));
    }

    @Test
    void prefersNewestNodeVersionNumerically() throws Exception {
        executable(directory.resolve(".nvm/versions/node/v9.0.0/bin/codex"), "old");
        Path current = executable(directory.resolve(".nvm/versions/node/v22.0.0/bin/codex"), "new");
        assertEquals(current.toString(), resolve("", Map.of(), "Linux", "aarch64", List.of()));
    }

    @Test
    void packageDiscoveryAcceptsOnlyAbsolutePathsAndBoundsOutput() throws Exception {
        assertEquals(List.of(directory), CodexExecutable.queryPackageRoots(queryCommand("paths", directory)));
        assertEquals(List.of(), CodexExecutable.queryPackageRoots(queryCommand("overflow", directory)));
        assertEquals(List.of(), CodexExecutable.queryPackageRoots(queryCommand("failure", directory)));
    }

    @Test
    @Timeout(12)
    void packageDiscoveryTerminatesHungProcess() throws Exception {
        Path pid = directory.resolve("pid.txt");
        assertEquals(List.of(), CodexExecutable.queryPackageRoots(queryCommand("hang", pid)));
        long processId = Long.parseLong(Files.readString(pid));
        var process = ProcessHandle.of(processId);
        if (process.isPresent()) process.orElseThrow().onExit().get(2, java.util.concurrent.TimeUnit.SECONDS);
        assertFalse(ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false));
    }

    private List<String> queryCommand(String mode, Path output) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = Path.of(CodexDiscoveryProcessFixture.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toString();
        return List.of(java, "-cp", classes, CodexDiscoveryProcessFixture.class.getName(), mode, output.toString());
    }

    private String resolve(String configured, Map<String, String> environment, String os, String arch, List<Path> packages) throws IOException {
        return CodexExecutable.resolve(configured, environment, os, arch, directory, directory.resolve("cache"), () -> packages);
    }

    private Path executable(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        assertTrue(path.toFile().setExecutable(true));
        return path;
    }
}
