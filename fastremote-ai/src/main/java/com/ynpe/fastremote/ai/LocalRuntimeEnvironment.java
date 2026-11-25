// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/** Non-secret facts needed to generate commands for FastRemote's local terminal. */
record LocalRuntimeEnvironment(
        String platform,
        String version,
        String architecture,
        String shell,
        String timeZone) {

    LocalRuntimeEnvironment {
        platform = required(platform, "platform");
        version = required(version, "version");
        architecture = required(architecture, "architecture");
        shell = required(shell, "shell");
        timeZone = required(timeZone, "timeZone");
    }

    static LocalRuntimeEnvironment current() {
        String rawName = System.getProperty("os.name", "unknown");
        String lowerName = rawName.toLowerCase(Locale.ROOT);
        String platform = lowerName.contains("mac") ? "macOS"
                : lowerName.contains("win") ? "Windows"
                : lowerName.contains("linux") ? "Linux"
                : safeToken(rawName, "unknown");
        return new LocalRuntimeEnvironment(
                platform,
                safeToken(System.getProperty("os.version", "unknown"), "unknown"),
                safeToken(System.getProperty("os.arch", "unknown"), "unknown"),
                detectShell(platform),
                safeToken(ZoneId.systemDefault().getId(), "unknown"));
    }

    String promptDescription() {
        return "operating system=" + platform + " " + version
                + ", architecture=" + architecture
                + ", local terminal shell=" + shell
                + ", time zone=" + timeZone;
    }

    String commandGuidance() {
        return switch (platform) {
            case "macOS" -> "Generate macOS/BSD-compatible commands for local execution. Do not use Linux-only "
                    + "interfaces or utilities such as /proc, hostnamectl, systemctl, lsblk, ip, or apt. Prefer "
                    + "macOS commands such as sw_vers, system_profiler, diskutil, launchctl, networksetup, and "
                    + "the BSD variants of common Unix tools when they fit the request.";
            case "Windows" -> shell.toLowerCase(Locale.ROOT).contains("powershell")
                    || shell.toLowerCase(Locale.ROOT).contains("pwsh")
                    ? "Generate PowerShell-compatible commands for local execution; do not emit POSIX shell syntax."
                    : "Generate cmd.exe-compatible commands for local execution; do not emit POSIX shell syntax.";
            case "Linux" -> "Generate Linux commands for local execution and use syntax compatible with " + shell + ".";
            default -> "Generate commands compatible with the declared local operating system and shell; if a "
                    + "platform-specific operation is uncertain, inspect the environment with a harmless command first.";
        };
    }

    private static String detectShell(String platform) {
        if ("Windows".equals(platform)) {
            for (String candidate : new String[]{"pwsh.exe", "powershell.exe", "cmd.exe"}) {
                if (findOnPath(candidate)) return candidate;
            }
            return "cmd.exe";
        }
        String configured = System.getenv("SHELL");
        if (configured == null || configured.isBlank()) return "/bin/sh";
        try {
            Path path = Path.of(configured);
            if (Files.isExecutable(path)) return safePath(configured, "/bin/sh");
        } catch (RuntimeException ignored) {
            // An invalid inherited SHELL must not make the AI request fail.
        }
        return "/bin/sh";
    }

    private static boolean findOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) return false;
        for (String item : path.split(java.io.File.pathSeparator)) {
            try {
                if (Files.isRegularFile(Path.of(item, executable))) return true;
            } catch (RuntimeException ignored) {
                // Ignore malformed inherited PATH entries and continue with the bounded candidate list.
            }
        }
        return false;
    }

    private static String required(String value, String name) {
        String safe = Objects.requireNonNull(value, name).strip();
        if (safe.isEmpty() || safe.length() > 256 || safe.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return safe;
    }

    private static String safeToken(String value, String fallback) {
        if (value == null) return fallback;
        String safe = value.strip();
        return safe.isEmpty() || safe.length() > 120 || safe.chars().anyMatch(Character::isISOControl)
                ? fallback : safe;
    }

    private static String safePath(String value, String fallback) {
        String safe = safeToken(value, fallback);
        return safe.matches("[A-Za-z0-9_./:\\\\ -]+") ? safe : fallback;
    }
}
