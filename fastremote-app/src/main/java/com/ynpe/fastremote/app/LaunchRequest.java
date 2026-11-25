// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A bounded, platform-neutral request received from a native launcher or shell integration. */
record LaunchRequest(boolean forceNewTerminal, Optional<Path> initialDirectory, List<Path> files, List<Path> localDirectories) {
    static final int MAX_FILES = 64;

    LaunchRequest(boolean forceNewTerminal, Optional<Path> initialDirectory, List<Path> files) {
        this(forceNewTerminal, initialDirectory, files, List.of());
    }

    LaunchRequest {
        initialDirectory = Objects.requireNonNull(initialDirectory, "initialDirectory")
                .map(path -> path.toAbsolutePath().normalize());
        localDirectories = Objects.requireNonNull(localDirectories, "localDirectories").stream()
                .map(path -> path.toAbsolutePath().normalize()).limit(MAX_FILES).toList();
        files = Objects.requireNonNull(files, "files").stream()
                .filter(Objects::nonNull)
                .map(path -> path.toAbsolutePath().normalize())
                .limit(MAX_FILES - localDirectories.size())
                .toList();
    }

    boolean openLocalTerminal() {
        return forceNewTerminal || initialDirectory.isPresent();
    }

    boolean opensWorkspace() {
        return openLocalTerminal() || !files.isEmpty() || !localDirectories.isEmpty();
    }

    static LaunchRequest parse(List<String> arguments) {
        Objects.requireNonNull(arguments, "arguments");
        boolean newTerminal = arguments.stream().anyMatch("-n"::equals);
        Optional<Path> directory = Optional.empty();
        List<Path> files = new ArrayList<>();
        List<Path> localDirectories = new ArrayList<>();
        for (int index = 0; index < arguments.size(); index++) {
            String argument = Objects.requireNonNull(arguments.get(index), "argument");
            if ("-n".equals(argument)) continue;
            if ("-d".equals(argument)) {
                if (++index < arguments.size()) directory = existingDirectory(arguments.get(index));
            } else if (argument.startsWith("-d=")) {
                directory = existingDirectory(argument.substring(3));
            } else if ("--local-files".equals(argument)) {
                if (++index < arguments.size()) existingLocalDirectory(arguments.get(index)).ifPresent(localDirectories::add);
            } else if ("-f".equals(argument)) {
                if (++index < arguments.size()) existingFile(arguments.get(index)).ifPresent(files::add);
            } else if (argument.startsWith("-f=")) {
                existingFile(argument.substring(3)).ifPresent(files::add);
            } else if (!argument.isBlank()) {
                Optional<Path> file = existingFile(argument);
                if (file.isPresent()) files.add(file.orElseThrow());
                else if (directory.isEmpty()) directory = existingDirectory(argument);
            }
        }
        return new LaunchRequest(newTerminal, directory, files, localDirectories);
    }

    static LaunchRequest fromExternal(boolean forceNewTerminal, String location) {
        Optional<Path> directory = location == null || location.isBlank()
                ? Optional.empty() : existingDirectory(location);
        return new LaunchRequest(forceNewTerminal, directory, List.of());
    }

    static LaunchRequest fromForwarded(
            boolean forceNewTerminal, String location, List<String> fileLocations) {
        Optional<Path> directory = location == null || location.isBlank()
                ? Optional.empty() : existingDirectory(location);
        List<Path> files = Objects.requireNonNull(fileLocations, "fileLocations").stream()
                .map(LaunchRequest::existingFile)
                .flatMap(Optional::stream)
                .toList();
        return new LaunchRequest(forceNewTerminal, directory, files);
    }

    static LaunchRequest fromForwarded(boolean forceNewTerminal, String location,
            List<String> files, List<String> directories) {
        LaunchRequest base = fromForwarded(forceNewTerminal, location, files);
        return new LaunchRequest(base.forceNewTerminal(), base.initialDirectory(), base.files(),
                directories.stream().map(LaunchRequest::existingLocalDirectory).flatMap(Optional::stream).toList());
    }

    LaunchRequest merge(LaunchRequest other) {
        Objects.requireNonNull(other, "other");
        List<Path> combinedFiles = new ArrayList<>(files);
        combinedFiles.addAll(other.files);
        List<Path> combinedDirectories = new ArrayList<>(localDirectories);
        combinedDirectories.addAll(other.localDirectories);
        return new LaunchRequest(forceNewTerminal || other.forceNewTerminal,
                initialDirectory.isPresent() ? initialDirectory : other.initialDirectory,
                combinedFiles, combinedDirectories);
    }

    private static Optional<Path> existingLocalDirectory(String argument) {
        try {
            Path candidate = Path.of(argument).toAbsolutePath().normalize();
            return Files.isDirectory(candidate) ? Optional.of(candidate) : Optional.empty();
        } catch (InvalidPathException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Path> existingFile(String argument) {
        try {
            Path candidate = Path.of(argument).toAbsolutePath().normalize();
            return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
        } catch (InvalidPathException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Path> existingDirectory(String argument) {
        try {
            Path candidate = Path.of(argument).toAbsolutePath().normalize();
            if (Files.isDirectory(candidate)) return Optional.of(candidate);
            if (Files.isRegularFile(candidate)) {
                Path parent = candidate.getParent();
                if (parent != null && Files.isDirectory(parent)) return Optional.of(parent);
            }
        } catch (InvalidPathException | SecurityException ignored) {
            // Shell integrations may pass stale or malformed paths. Opening a terminal at the
            // normal home directory remains useful and does not turn invalid input into a crash.
        }
        return Optional.empty();
    }
}
