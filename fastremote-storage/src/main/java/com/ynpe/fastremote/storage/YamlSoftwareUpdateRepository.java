// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.update.SoftwareUpdateSettings;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Atomic non-secret update preferences and reminder state in global-config.yaml. */
public final class YamlSoftwareUpdateRepository {
    private static final String SECTION = "softwareUpdate";
    private final YamlGlobalConfigStore store;

    public YamlSoftwareUpdateRepository(YamlGlobalConfigStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public SoftwareUpdateSettings load() throws IOException {
        Map<String, Object> settings = YamlGlobalConfigStore.requireMap(store.read(), "settings");
        Object raw = settings.get(SECTION);
        if (raw == null) return SoftwareUpdateSettings.defaults();
        if (!(raw instanceof Map<?, ?> values)) throw new IOException("softwareUpdate must be a mapping");
        boolean enabled = optionalBoolean(values, "automaticChecksEnabled", true);
        Optional<Instant> lastCheck = optionalInstant(values, "lastSuccessfulCheckAt");
        String skipped = optionalString(values, "skippedVersion");
        try {
            return new SoftwareUpdateSettings(enabled, lastCheck, skipped);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("softwareUpdate contains invalid state", invalid);
        }
    }

    public SoftwareUpdateSettings setAutomaticChecksEnabled(boolean enabled) throws IOException {
        return update(values -> values.put("automaticChecksEnabled", enabled));
    }

    public SoftwareUpdateSettings recordSuccessfulCheck(Instant checkedAt) throws IOException {
        Objects.requireNonNull(checkedAt, "checkedAt");
        return update(values -> values.put("lastSuccessfulCheckAt", checkedAt.toString()));
    }

    public SoftwareUpdateSettings skipVersion(String version) throws IOException {
        String value = Objects.requireNonNull(version, "version").strip();
        return update(values -> values.put("skippedVersion", value));
    }

    private SoftwareUpdateSettings update(java.util.function.Consumer<Map<String, Object>> mutation)
            throws IOException {
        try {
            store.update(document -> {
                try {
                Map<String, Object> settings = YamlGlobalConfigStore.requireMap(document, "settings");
                Map<String, Object> values;
                Object existing = settings.get(SECTION);
                if (existing == null) {
                    values = new LinkedHashMap<>();
                    settings.put(SECTION, values);
                } else if (existing instanceof Map<?, ?> raw) {
                    @SuppressWarnings("unchecked") Map<String, Object> cast = (Map<String, Object>) raw;
                    values = cast;
                } else {
                    throw new IOException("softwareUpdate must be a mapping");
                }
                mutation.accept(values);
                return null;
                } catch (IOException invalid) {
                    throw new UpdateMutationException(invalid);
                }
            });
        } catch (UpdateMutationException invalid) {
            throw (IOException) invalid.getCause();
        }
        return load();
    }

    private static boolean optionalBoolean(Map<?, ?> values, String key, boolean fallback) throws IOException {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean result) return result;
        throw new IOException(key + " must be a boolean");
    }

    private static String optionalString(Map<?, ?> values, String key) throws IOException {
        Object value = values.get(key);
        if (value == null) return "";
        if (value instanceof String text) return text.strip();
        throw new IOException(key + " must be a string");
    }

    private static Optional<Instant> optionalInstant(Map<?, ?> values, String key) throws IOException {
        String value = optionalString(values, key);
        if (value.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException invalid) {
            throw new IOException(key + " must be RFC3339", invalid);
        }
    }

    private static final class UpdateMutationException extends RuntimeException {
        UpdateMutationException(IOException cause) { super(cause); }
    }
}
