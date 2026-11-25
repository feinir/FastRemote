// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static com.ynpe.fastremote.storage.SettingsYamlValues.*;

import com.ynpe.fastremote.domain.settings.DiagnosticLogSettings;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** YAML mapping for DiagnosticLog settings. */
final class DiagnosticLogSettingsCodec {
    private DiagnosticLogSettingsCodec() {}

    static DiagnosticLogSettings decode(Map<String, Object> settings)
            throws IOException {
        Object value = settings.get("diagnosticLog");
        if (value == null) return DiagnosticLogSettings.defaults();
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IOException("Global setting 'diagnosticLog' must be a mapping");
        }
        @SuppressWarnings("unchecked") Map<String, Object> values = (Map<String, Object>) raw;
        boolean enabled = optionalBool(values, "enabled", true);
        Object directory = values.get("directory");
        if (directory == null) return new DiagnosticLogSettings(enabled, "");
        if (!(directory instanceof String text)) {
            throw new IOException("Global setting 'diagnosticLog.directory' must be a string");
        }
        try {
            return new DiagnosticLogSettings(enabled, text);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Global setting 'diagnosticLog.directory' is invalid", invalid);
        }
    }

    static Map<String, Object> encode(DiagnosticLogSettings settings) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", settings.enabled());
        values.put("directory", settings.directory());
        return values;
    }
}
