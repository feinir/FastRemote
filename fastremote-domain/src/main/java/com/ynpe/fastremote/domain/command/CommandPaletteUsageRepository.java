// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.command;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public interface CommandPaletteUsageRepository {
    List<CommandPaletteUsage> list() throws IOException;

    CommandPaletteUsage recordUse(String entryKey, Instant usedAt) throws IOException;
}
