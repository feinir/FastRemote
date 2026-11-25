// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.List;
import java.util.Objects;

/** Builds one literal directory-change command for the active terminal shell. */
final class TerminalDirectoryCommand {
    private TerminalDirectoryCommand() { }

    static String local(String path, LocalTerminalHandle.ShellFamily shellFamily) {
        String safePath = requireSingleCommandPath(path);
        return switch (Objects.requireNonNull(shellFamily, "shellFamily")) {
            case UNKNOWN -> throw new IllegalArgumentException("Terminal shell is unknown");
            case POSIX -> "cd " + posixLiteral(safePath) + '\n';
            case POWERSHELL -> "Set-Location -LiteralPath " + powershellLiteral(safePath) + '\r';
            case COMMAND_PROMPT -> "cd /d \"" + safePath + "\"\r\n";
        };
    }

    static String remote(String path, ServerOverview.SystemFamily systemFamily,
            LocalTerminalHandle.ShellFamily shellFamily) {
        String safePath = requireSingleCommandPath(path);
        if (Objects.requireNonNull(systemFamily, "systemFamily") == ServerOverview.SystemFamily.WINDOWS
                || looksLikeWindowsRemotePath(safePath)) {
            if (safePath.matches("^/[A-Za-z]:/.*")) safePath = safePath.substring(1);
            String windowsPath = safePath.replace('/', '\\');
            if (shellFamily == LocalTerminalHandle.ShellFamily.POWERSHELL) {
                return "Set-Location -LiteralPath " + powershellLiteral(windowsPath) + '\r';
            }
            return "cd /d \"" + windowsPath + "\"\r\n";
        }
        return "cd " + posixLiteral(safePath) + '\n';
    }

    static String localArguments(List<String> paths, LocalTerminalHandle.ShellFamily shellFamily) {
        return arguments(paths, localSystemFamily(), shellFamily);
    }

    static String remoteArguments(List<String> paths, ServerOverview.SystemFamily systemFamily,
            LocalTerminalHandle.ShellFamily shellFamily) {
        return arguments(paths, Objects.requireNonNull(systemFamily, "systemFamily"), shellFamily);
    }

    private static String arguments(List<String> paths, ServerOverview.SystemFamily systemFamily,
            LocalTerminalHandle.ShellFamily shellFamily) {
        Objects.requireNonNull(paths, "paths");
        if (paths.isEmpty()) throw new IllegalArgumentException("At least one path is required");
        LocalTerminalHandle.ShellFamily shell = Objects.requireNonNull(shellFamily, "shellFamily");
        if (shell == LocalTerminalHandle.ShellFamily.UNKNOWN) {
            shell = systemFamily == ServerOverview.SystemFamily.WINDOWS
                    ? LocalTerminalHandle.ShellFamily.COMMAND_PROMPT
                    : LocalTerminalHandle.ShellFamily.POSIX;
        }
        LocalTerminalHandle.ShellFamily resolvedShell = shell;
        return paths.stream().map(TerminalDirectoryCommand::requireArgumentPath)
                .map(path -> argumentLiteral(path, systemFamily, resolvedShell))
                .collect(java.util.stream.Collectors.joining(" ")) + ' ';
    }

    private static String argumentLiteral(String path, ServerOverview.SystemFamily systemFamily,
            LocalTerminalHandle.ShellFamily shellFamily) {
        boolean windows = systemFamily == ServerOverview.SystemFamily.WINDOWS
                || looksLikeWindowsRemotePath(path);
        String value = path;
        if (windows) {
            if (value.matches("^/[A-Za-z]:/.*")) value = value.substring(1);
            value = value.replace('/', '\\');
        }
        return switch (shellFamily) {
            case UNKNOWN -> throw new IllegalArgumentException("Terminal shell is unknown");
            case POSIX -> posixLiteral(value);
            case POWERSHELL -> powershellLiteral(value);
            case COMMAND_PROMPT -> commandPromptLiteral(value);
        };
    }

    private static ServerOverview.SystemFamily localSystemFamily() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? ServerOverview.SystemFamily.WINDOWS : ServerOverview.SystemFamily.POSIX;
    }

    static boolean looksLikeWindowsRemotePath(String path) {
        if (path == null) return false;
        return path.matches("^/[A-Za-z]:(?:/.*)?$")
                || path.matches("^[A-Za-z]:[\\\\/].*$");
    }

    private static String requireSingleCommandPath(String path) {
        String value = Objects.requireNonNull(path, "path");
        if (value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\0') >= 0 || value.indexOf('"') >= 0) {
            throw new IllegalArgumentException("Favorite path cannot be represented as one terminal command");
        }
        return value;
    }

    private static String requireArgumentPath(String path) {
        String value = Objects.requireNonNull(path, "path");
        if (value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Path cannot be represented as a terminal argument");
        }
        return value;
    }

    private static String posixLiteral(String value) {
        return '\'' + value.replace("'", "'\\''") + '\'';
    }

    private static String powershellLiteral(String value) {
        return '\'' + value.replace("'", "''") + '\'';
    }

    private static String commandPromptLiteral(String value) {
        if (value.indexOf('"') >= 0) {
            throw new IllegalArgumentException("Path cannot be represented as a command prompt argument");
        }
        return '"' + value + '"';
    }
}
