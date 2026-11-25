// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Discovers installed native Codex CLIs without launching the desktop UI or a login shell. */
final class CodexExecutable {
    private CodexExecutable() { }

    static String resolve(Path codexHome) throws IOException {
        Map<String, String> environment = System.getenv();
        return resolve(System.getProperty("fastremote.codex.executable", ""), environment,
                System.getProperty("os.name", ""), System.getProperty("os.arch", ""),
                Path.of(System.getProperty("user.home")), codexHome.resolve("installed-component"),
                () -> windowsPackages(environment));
    }

    static String resolve(String configured, Map<String, String> environment, String osName, String architecture,
            Path home, Path componentCache, Supplier<List<Path>> windowsPackages) throws IOException {
        if (!configured.isBlank()) return configured;
        String override = environment.getOrDefault("FASTREMOTE_CODEX_EXECUTABLE", "");
        if (!override.isBlank()) return override;

        boolean windows = osName.toLowerCase(Locale.ROOT).startsWith("windows");
        String name = windows ? "codex.exe" : "codex";
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        if (windows) {
            environmentPath(environment, "LOCALAPPDATA").ifPresent(local -> {
                addDesktopCandidates(candidates, local.resolve("Programs/Codex"), name);
                addDesktopCandidates(candidates, local.resolve("Codex"), name);
            });
        } else if (osName.toLowerCase(Locale.ROOT).contains("mac")) {
            // Preserve the working macOS CLI preference before considering the desktop app.
            candidates.add(Path.of("/opt/homebrew/bin/codex"));
            candidates.add(Path.of("/usr/local/bin/codex"));
            candidates.add(Path.of("/usr/bin/codex"));
            candidates.add(Path.of("/Applications/Codex.app/Contents/Resources/codex"));
            candidates.add(home.resolve("Applications/Codex.app/Contents/Resources/codex"));
        }

        String searchPath = environment.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("PATH"))
                .map(Map.Entry::getValue).findFirst().orElse("");
        for (String part : searchPath.split(Pattern.quote(windows ? ";" : ":"))) {
            // Empty/relative PATH entries must not execute a file from an arbitrary working directory.
            path(part).filter(Path::isAbsolute).ifPresent(bin -> addCliCandidates(candidates, bin, name, windows, architecture));
        }
        if (windows) {
            environmentPath(environment, "APPDATA").ifPresent(roaming ->
                    addCliCandidates(candidates, roaming.resolve("npm"), name, true, architecture));
        } else {
            for (String bin : List.of(".local/bin", ".npm-global/bin", ".npm/bin", ".volta/bin", ".local/share/pnpm")) {
                addCliCandidates(candidates, home.resolve(bin), name, false, architecture);
            }
            environmentPath(environment, "NVM_BIN").ifPresent(bin ->
                    addCliCandidates(candidates, bin, name, false, architecture));
            Path nvm = environmentPath(environment, "NVM_DIR").orElse(home.resolve(".nvm"));
            addNodeVersions(candidates, nvm.resolve("versions/node"), name, architecture);
            addNodeVersions(candidates, home.resolve(".local/share/mise/installs/node"), name, architecture);
            for (String bin : List.of("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "/snap/bin")) {
                addCliCandidates(candidates, Path.of(bin), name, false, architecture);
            }
        }
        var installed = candidates.stream().filter(Files::isRegularFile).filter(Files::isExecutable).findFirst();
        if (installed.isPresent()) return installed.orElseThrow().toString();
        if (windows) {
            // MSIX blocks execution from WindowsApps outside its package identity. Only use roots
            // returned by the current user's package registry and stage the native CLI, never the UI.
            for (Path root : windowsPackages.get()) {
                Path executable = root.resolve("app/resources/codex.exe");
                if (Files.isRegularFile(executable) && Files.isReadable(executable)) {
                    return cacheInstalledComponent(executable, componentCache).toString();
                }
            }
        }
        return "codex";
    }

    static Path cacheInstalledComponent(Path source, Path cache) throws IOException {
        Path executable = cache.resolve("codex.exe");
        if (Files.isSymbolicLink(cache) || Files.isSymbolicLink(executable)) {
            throw new IOException("Installed Codex component cache must not be a symbolic link");
        }
        byte[] sourceHash = sha256(source);
        if (Files.isRegularFile(executable) && !Files.isSymbolicLink(executable)
                && java.security.MessageDigest.isEqual(sourceHash, sha256(executable))) return executable;
        Files.createDirectories(cache);
        Path temporary = Files.createTempFile(cache, "codex-", ".tmp");
        try {
            Files.copy(source, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (!java.security.MessageDigest.isEqual(sourceHash, sha256(temporary))) {
                throw new IOException("Installed Codex component changed during discovery; retry login");
            }
            Files.move(temporary, executable, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return executable;
    }

    private static byte[] sha256(Path file) throws IOException {
        java.security.MessageDigest digest;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int length;
            while ((length = input.read(buffer)) >= 0) digest.update(buffer, 0, length);
        }
        return digest.digest();
    }

    private static void addDesktopCandidates(LinkedHashSet<Path> candidates, Path app, String name) {
        candidates.add(app.resolve("resources").resolve(name));
        // Older standalone CLI installations used this directory without an Electron desktop launcher.
        // Codex.exe in an Electron application root is the UI, not the App Server.
        if (!Files.isDirectory(app.resolve("resources"))) candidates.add(app.resolve(name));
    }

    private static void addCliCandidates(LinkedHashSet<Path> candidates, Path bin, String name,
            boolean windows, String architecture) {
        String cpu = architecture.equalsIgnoreCase("aarch64") || architecture.equalsIgnoreCase("arm64") ? "arm64" : "x64";
        String triple = (cpu.equals("arm64") ? "aarch64" : "x86_64")
                + (windows ? "-pc-windows-msvc" : "-unknown-linux-musl");
        Path npm = windows ? bin.resolve("node_modules/@openai/codex")
                : bin.resolve("../lib/node_modules/@openai/codex").normalize();
        // npm's .cmd/JS shim requires a shell or Node on PATH; prefer the official native payload.
        for (Path root : List.of(npm.resolve("node_modules/@openai/codex-" + (windows ? "win32-" : "linux-") + cpu), npm)) {
            candidates.add(root.resolve("vendor").resolve(triple).resolve("bin").resolve(name));
            candidates.add(root.resolve("vendor").resolve(triple).resolve("codex").resolve(name));
        }
        candidates.add(bin.resolve(name));
    }

    private static void addNodeVersions(LinkedHashSet<Path> candidates, Path versions, String name, String architecture) {
        if (!Files.isDirectory(versions)) return;
        try (var entries = Files.list(versions)) {
            entries.filter(Files::isDirectory).filter(entry -> entry.getFileName().toString().matches("v?[1-9][0-9]*(\\.[0-9]+){0,2}"))
                    .limit(64).sorted(Comparator.comparing(CodexExecutable::nodeVersion).reversed())
                    .forEach(version -> addCliCandidates(candidates, version.resolve("bin"), name, false, architecture));
        } catch (IOException ignored) {
            // Unreadable optional installations must not prevent other supported locations from working.
        }
    }

    private static Runtime.Version nodeVersion(Path entry) {
        String name = entry.getFileName().toString().replaceFirst("^v", "");
        // Runtime.Version excludes trailing zero components (Node commonly uses 22.0.0).
        try {
            return Runtime.Version.parse(name.replaceFirst("(\\.0)+$", ""));
        } catch (IllegalArgumentException ignored) {
            return Runtime.Version.parse("1");
        }
    }

    private static Optional<Path> environmentPath(Map<String, String> environment, String key) {
        return path(environment.getOrDefault(key, ""));
    }

    private static Optional<Path> path(String value) {
        if (value.isBlank()) return Optional.empty();
        try {
            return Optional.of(Path.of(value));
        } catch (InvalidPathException ignored) {
            return Optional.empty();
        }
    }

    private static List<Path> windowsPackages(Map<String, String> environment) {
        Path system = environmentPath(environment, "SystemRoot").orElse(Path.of("C:\\Windows"));
        Path powershell = system.resolve("System32/WindowsPowerShell/v1.0/powershell.exe");
        String query = "[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new($false); "
                + "Get-AppxPackage -Name OpenAI.Codex -ErrorAction SilentlyContinue | "
                + "Sort-Object Version -Descending | ForEach-Object { $_.InstallLocation }";
        return queryPackageRoots(List.of(powershell.toString(), "-NoProfile", "-NonInteractive", "-Command", query));
    }

    static List<Path> queryPackageRoots(List<String> command) {
        Process process = null;
        FutureTask<byte[]> output = null;
        try {
            process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process.getOutputStream().close();
            var input = process.getInputStream();
            output = new FutureTask<>(() -> input.readNBytes(16 * 1024 + 1));
            Thread.ofVirtual().name("fastremote-codex-discovery").start(output);
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) return List.of();
            byte[] bytes = output.get(1, TimeUnit.SECONDS);
            if (bytes.length > 16 * 1024) return List.of();
            List<Path> roots = new ArrayList<>();
            for (String line : new String(bytes, StandardCharsets.UTF_8).split("\\R")) {
                path(line.strip()).filter(Path::isAbsolute).ifPresent(roots::add);
            }
            return List.copyOf(roots);
        } catch (IOException | ExecutionException | TimeoutException ignored) {
            return List.of();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return List.of();
        } finally {
            if (process != null) {
                if (process.isAlive()) process.destroyForcibly();
                try { process.getInputStream().close(); } catch (IOException ignored) { }
            }
            if (output != null) output.cancel(true);
        }
    }
}
