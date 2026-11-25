// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

/** Controls the optional on-disk copy of the bounded diagnostic log. */
public record DiagnosticLogSettings(boolean enabled, String directory) {
    public static final int MAX_DIRECTORY_LENGTH = 4_096;

    public DiagnosticLogSettings {
        directory = java.util.Objects.requireNonNull(directory, "directory").strip();
        if (directory.length() > MAX_DIRECTORY_LENGTH) {
            throw new IllegalArgumentException("directory is too long");
        }
        if (!directory.isEmpty()) {
            try {
                java.nio.file.Path.of(directory);
            } catch (java.nio.file.InvalidPathException invalid) {
                throw new IllegalArgumentException("directory is not a valid path", invalid);
            }
        }
    }

    public static DiagnosticLogSettings defaults() {
        return new DiagnosticLogSettings(true, "");
    }
}
