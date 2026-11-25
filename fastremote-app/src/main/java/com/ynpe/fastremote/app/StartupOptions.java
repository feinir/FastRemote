// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Process-wide options that must be applied before single-instance and JavaFX initialization. */
record StartupOptions(Optional<Path> dataDirectory, List<String> applicationArguments) {
    private static final String LONG_DATA_DIRECTORY_OPTION = "--user-data-dir";

    StartupOptions {
        dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory")
                .map(path -> path.toAbsolutePath().normalize());
        applicationArguments = List.copyOf(Objects.requireNonNull(applicationArguments, "applicationArguments"));
    }

    static StartupOptions parse(List<String> arguments) {
        Objects.requireNonNull(arguments, "arguments");
        Path dataDirectory = null;
        List<String> applicationArguments = new ArrayList<>(arguments.size());
        for (int index = 0; index < arguments.size(); index++) {
            String argument = Objects.requireNonNull(arguments.get(index), "argument");
            String value = null;
            if (LONG_DATA_DIRECTORY_OPTION.equals(argument)) {
                if (++index >= arguments.size()) {
                    throw new IllegalArgumentException(argument + " requires a directory path");
                }
                value = arguments.get(index);
            } else if (argument.startsWith(LONG_DATA_DIRECTORY_OPTION + "=")) {
                value = argument.substring((LONG_DATA_DIRECTORY_OPTION + "=").length());
            } else {
                applicationArguments.add(argument);
            }
            if (value != null) {
                if (dataDirectory != null) {
                    throw new IllegalArgumentException("the data directory option may only be specified once");
                }
                dataDirectory = dataDirectory(value);
            }
        }
        return new StartupOptions(Optional.ofNullable(dataDirectory), applicationArguments);
    }

    private static Path dataDirectory(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("the data directory path must not be blank");
        }
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (InvalidPathException | SecurityException failure) {
            throw new IllegalArgumentException("the data directory path is invalid", failure);
        }
    }
}
