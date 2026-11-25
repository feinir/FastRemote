// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.TerminalColorPalette;
import java.nio.file.Path;
import java.util.List;

public record TerminalPaletteImportBatch(List<TerminalColorPalette> palettes, List<Failure> failures) {
    public TerminalPaletteImportBatch { palettes = List.copyOf(palettes); failures = List.copyOf(failures); }
    public record Failure(Path source, String message) { }
}
