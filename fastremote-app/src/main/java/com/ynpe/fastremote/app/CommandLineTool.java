// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.storage.FastRemotePaths;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The fr entry point uses the existing authenticated, per-data-directory instance broker. */
public final class CommandLineTool {
    private CommandLineTool() { }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("fr: Missing application data directory.");
            System.exit(2);
            return;
        }
        if (!usesAutomaticDirectory(args[0])) {
            System.setProperty(FastRemotePaths.DATA_DIRECTORY_PROPERTY, args[0]);
        }
        System.exit(run(java.util.Arrays.asList(args).subList(1, args.length), System.out, System.err));
    }

    static boolean usesAutomaticDirectory(String argument) {
        if (argument.equals("--auto-data-directory")) return true;
        // Existing installed fr scripts used to embed the default user directory.
        return !System.getProperty("jpackage.app-path", "").isBlank()
                && Path.of(argument).toAbsolutePath().normalize()
                        .equals(FastRemotePaths.userDataDirectory().toAbsolutePath().normalize());
    }

    public static List<String> cliCommand() {
        List<String> command = javaCommand();
        String packaged = System.getProperty("jpackage.app-path", "");
        if (!packaged.isBlank()) command.add("-Djpackage.app-path=" + packaged);
        command.add(CommandLineTool.class.getName());
        command.add(DataLocationService.managed() ? "--auto-data-directory"
                : FastRemotePaths.applicationDataDirectory().toString());
        return List.copyOf(command);
    }

    static int run(List<String> arguments, PrintStream out, PrintStream error) {
        if (arguments.equals(List.of("--help")) || arguments.equals(List.of("-h"))) {
            out.println("Usage: fr [--] PATH [PATH ...]\nOpen files in the FastRemote editor and directories in local files.");
            return 0;
        }
        try {
            LaunchRequest request = files(arguments, Path.of(""));
            Path data = DataLocationService.selectedDirectory();
            try (SingleInstanceBroker broker = DataLocationService.start(request)) {
                if (broker == null) return 0;
            }
            // Release the temporary broker before starting the GUI. The GUI performs its own
            // single-instance arbitration, including simultaneous fr invocations during startup.
            List<String> command = new ArrayList<>(launcherCommand());
            if (!DataLocationService.managed()) command.add("--user-data-dir=" + data);
            for (Path file : request.files()) {
                command.add("-f");
                command.add(file.toString());
            }
            for (Path directory : request.localDirectories()) {
                command.add("--local-files");
                command.add(directory.toString());
            }
            new ProcessBuilder(command).redirectInput(ProcessBuilder.Redirect.PIPE)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start().getOutputStream().close();
            return 0;
        } catch (IOException | IllegalArgumentException failure) {
            error.println("fr: " + failure.getMessage());
            return 2;
        }
    }

    static LaunchRequest files(List<String> arguments, Path directory) throws IOException {
        int start = !arguments.isEmpty() && "--".equals(arguments.getFirst()) ? 1 : 0;
        if (arguments.size() == start) throw new IllegalArgumentException("Usage: fr [--] PATH [PATH ...]");
        if (arguments.size() - start > LaunchRequest.MAX_FILES) {
            throw new IllegalArgumentException("Open at most " + LaunchRequest.MAX_FILES + " paths at a time.");
        }
        List<Path> files = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        for (String argument : arguments.subList(start, arguments.size())) {
            if (argument.isEmpty()) throw new IllegalArgumentException("File path is empty.");
            Path path = directory.resolve(argument).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path) && !Files.isDirectory(path)) {
                throw new IOException("Not a regular file or directory: " + path);
            }
            if (!Files.isReadable(path)) throw new IOException("Cannot read file: " + path);
            if (Files.isDirectory(path)) directories.add(path);
            else files.add(path);
        }
        return new LaunchRequest(false, Optional.empty(), files, directories);
    }

    public static List<String> launcherCommand() {
        String packaged = System.getProperty("jpackage.app-path", "");
        if (!packaged.isBlank()) return List.of(Path.of(packaged).toAbsolutePath().toString());
        // Gradle development runs have an immutable JAR snapshot. Never route them to the
        // installed production app or reuse its license/data directory.
        List<String> command = javaCommand();
        command.add(FastRemoteLauncher.class.getName());
        return List.copyOf(command);
    }

    private static List<String> javaCommand() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString());
        command.add("--add-modules=javafx.controls,javafx.fxml,javafx.swing");
        command.add("--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED");
        for (String property : List.of("fastremote.license.agent", "fastremote.license.serverUrl")) {
            String value = System.getProperty(property);
            if (value != null) command.add("-D" + property + "=" + value);
        }
        command.add("-cp");
        String classpath = System.getProperty("java.class.path");
        if (!System.getProperty("jpackage.app-path", "").isBlank()) {
            try {
                classpath = Path.of(CommandLineTool.class.getProtectionDomain().getCodeSource()
                        .getLocation().toURI()).getParent().toString() + java.io.File.separator + "*";
            } catch (java.net.URISyntaxException failure) {
                throw new IllegalStateException("Cannot locate the installed application.", failure);
            }
            command.add(classpath);
            return command;
        }
        command.add(java.util.Arrays.stream(classpath.split(
                        java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .map(entry -> Path.of(entry).toAbsolutePath().normalize().toString())
                .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator)));
        return command;
    }
}
