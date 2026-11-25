// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import java.io.IOException;
import java.nio.file.Path;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Stores only bootstrap paths and process identity, never configuration or credentials. */
final class DataLocationPreference {
    record State(Path current, Path pending, long sourcePid, long sourceStartedMillis) {
        State(Path current, Path pending) { this(current, pending, 0, 0); }
    }
    private final Preferences preferences;
    private final Path defaultDirectory;

    DataLocationPreference(Preferences preferences, Path defaultDirectory) {
        this.preferences = preferences;
        this.defaultDirectory = defaultDirectory.toAbsolutePath().normalize();
    }

    State read() throws IOException {
        try {
            preferences.sync();
            String value = preferences.get("location-v1", "");
            if (value.isEmpty()) return new State(defaultDirectory, null);
            String[] parts = value.split("\\n", -1);
            if (parts.length != 5 || !parts[0].equals("1")) throw new IOException("Invalid data location preference");
            Path current = Path.of(parts[1]);
            Path pending = parts[2].isEmpty() ? null : Path.of(parts[2]);
            if (!current.isAbsolute() || pending != null && !pending.isAbsolute()) {
                throw new IOException("Data locations must be absolute");
            }
            return new State(current.normalize(), pending == null ? null : pending.normalize(),
                    Long.parseLong(parts[3]), Long.parseLong(parts[4]));
        } catch (BackingStoreException | IllegalArgumentException failure) {
            throw new IOException("Cannot read startup data location", failure);
        }
    }

    void write(State state) throws IOException {
        String value = "1\n" + state.current() + "\n" + (state.pending() == null ? "" : state.pending())
                + "\n" + state.sourcePid() + "\n" + state.sourceStartedMillis();
        if (state.current().toString().contains("\n")
                || state.pending() != null && state.pending().toString().contains("\n")) {
            throw new IOException("Unsupported data directory name");
        }
        String previous = preferences.get("location-v1", "");
        try {
            preferences.put("location-v1", value);
            preferences.flush();
        } catch (BackingStoreException | IllegalArgumentException failure) {
            preferences.put("location-v1", previous);
            throw new IOException("Cannot save startup data location", failure);
        }
    }
}
