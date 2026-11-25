// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.nio.file.Path;
import java.util.Optional;

/** Startup preference, deliberately separate from the application configuration. */
public record DataLocationSettings(Path current, Path userDirectory, Optional<Path> programDirectory,
        boolean changeAllowed, Path selectedDirectory, boolean pending) { }
