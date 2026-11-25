// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.terminal;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Best-effort working-directory inference from visible remote prompt output. */
final class RemoteWorkingDirectoryParser {
    enum ShellFamily { POSIX, POWERSHELL, COMMAND_PROMPT }

    record Prompt(String path, ShellFamily shellFamily) { }
    private static final int MAXIMUM_TAIL_CHARS = 16 * 1024;
    private static final int MAXIMUM_PATH_CHARS = 4096;
    private static final Pattern CONTROL_SEQUENCE = Pattern.compile(
            "\\u001B(?:\\][^\\u0007\\u001B]*(?:\\u0007|\\u001B\\\\)|\\[[0-?]*[ -/]*[@-~])");
    private static final Pattern POWERSHELL = Pattern.compile("^PS\\s+([A-Za-z]:\\\\.*)>\\s*$");
    private static final Pattern WINDOWS_CMD = Pattern.compile(
            "^(?:[^\\r\\n\\s>]+\\s+)?([A-Za-z]:\\\\.*?)>\\s*$");
    private static final Pattern USER_HOST_COLON = Pattern.compile(
            "^[^\\r\\n]*?@[^:\\s]+:((?:/.*?|~(?:/.*?)?))\\s*[#$%>](?:\\s+[^\\r\\n]*)?$");
    private static final Pattern BRACKETED_USER_HOST = Pattern.compile(
            "^\\[[^]\\r\\n]*?@[^]\\r\\n]*?[ :]((?:/.*?|~(?:/.*?)?))\\]\\s*[#$%>]\\s*$");
    private static final Pattern GENERIC_POSIX = Pattern.compile(
            "^(?:[^\\r\\n]*?\\s)?((?:/[^\\r\\n]*?|~(?:/[^\\r\\n]*?)?))\\s*[#$%>]\\s*$");

    private final StringBuilder tail = new StringBuilder();
    private final Consumer<String> pathConsumer;
    private final Consumer<ShellFamily> shellFamilyConsumer;
    private String lastReported;
    private ShellFamily lastReportedShellFamily;

    RemoteWorkingDirectoryParser(Consumer<String> pathConsumer) {
        this(pathConsumer, ignored -> {});
    }

    RemoteWorkingDirectoryParser(Consumer<String> pathConsumer, Consumer<ShellFamily> shellFamilyConsumer) {
        this.pathConsumer = java.util.Objects.requireNonNull(pathConsumer, "pathConsumer");
        this.shellFamilyConsumer = java.util.Objects.requireNonNull(shellFamilyConsumer, "shellFamilyConsumer");
    }

    synchronized void accept(String output) {
        if (output == null || output.isEmpty()) return;
        tail.append(output);
        if (tail.length() > MAXIMUM_TAIL_CHARS) {
            tail.delete(0, tail.length() - MAXIMUM_TAIL_CHARS);
        }
        String visible = CONTROL_SEQUENCE.matcher(tail).replaceAll("");
        String line = currentLine(visible);
        inferredPrompt(line).ifPresent(prompt -> {
            if (prompt.shellFamily() != lastReportedShellFamily) {
                lastReportedShellFamily = prompt.shellFamily();
                shellFamilyConsumer.accept(prompt.shellFamily());
            }
            if (!prompt.path().equals(lastReported)) {
                lastReported = prompt.path();
                pathConsumer.accept(prompt.path());
            }
        });
    }

    static Optional<String> inferredPath(String line) {
        return inferredPrompt(line).map(Prompt::path);
    }

    static Optional<Prompt> inferredPrompt(String line) {
        if (line == null || line.isBlank()) return Optional.empty();
        String normalized = eraseBackspaces(line).stripTrailing();
        Matcher powershell = POWERSHELL.matcher(normalized);
        if (powershell.matches()) {
            return validPath("/" + powershell.group(1).replace('\\', '/'))
                    .map(path -> new Prompt(path, ShellFamily.POWERSHELL));
        }
        Matcher commandPrompt = WINDOWS_CMD.matcher(normalized);
        if (commandPrompt.matches()) {
            return validPath("/" + commandPrompt.group(1).replace('\\', '/'))
                    .map(path -> new Prompt(path, ShellFamily.COMMAND_PROMPT));
        }
        for (Pattern pattern : new Pattern[] { USER_HOST_COLON, BRACKETED_USER_HOST, GENERIC_POSIX }) {
            Matcher matcher = pattern.matcher(normalized);
            if (matcher.matches()) {
                return validPath(matcher.group(1).stripTrailing())
                        .map(path -> new Prompt(path, ShellFamily.POSIX));
            }
        }
        return Optional.empty();
    }

    private static String currentLine(String text) {
        int newline = Math.max(text.lastIndexOf('\n'), text.lastIndexOf('\r'));
        return text.substring(newline + 1);
    }

    private static String eraseBackspaces(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\b') {
                if (!result.isEmpty()) result.deleteCharAt(result.length() - 1);
            } else if (!Character.isISOControl(character)) {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static Optional<String> validPath(String path) {
        if (path.isBlank() || path.length() > MAXIMUM_PATH_CHARS || path.contains("\u0000")) {
            return Optional.empty();
        }
        return Optional.of(path);
    }
}
