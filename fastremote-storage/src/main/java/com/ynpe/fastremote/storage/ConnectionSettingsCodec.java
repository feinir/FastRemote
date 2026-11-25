// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static com.ynpe.fastremote.storage.SettingsYamlValues.*;

import com.ynpe.fastremote.domain.settings.ConnectionSettings;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** YAML mapping for Connection settings. */
final class ConnectionSettingsCodec {
    private ConnectionSettingsCodec() {}

    static ConnectionSettings decode(Map<String, Object> settings) throws IOException {
        Object value = settings.get("connection");
        if (value == null) return ConnectionSettings.defaults();
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IOException("Global setting 'connection' must be a mapping");
        }
        @SuppressWarnings("unchecked") Map<String, Object> connection = (Map<String, Object>) raw;
        ConnectionSettings defaults = ConnectionSettings.defaults();
        try {
            int keepAlive = optionalInteger(connection, "keepAliveIntervalSeconds",
                    defaults.keepAliveIntervalSeconds());
            if (keepAlive == 0) keepAlive = defaults.keepAliveIntervalSeconds();
            return new ConnectionSettings(
                    optionalInteger(connection, "connectTimeoutSeconds", defaults.connectTimeoutSeconds()),
                    optionalInteger(connection, "authenticationTimeoutSeconds",
                            defaults.authenticationTimeoutSeconds()),
                    keepAlive,
                    optionalBool(connection, "disconnectIdleTerminal", defaults.disconnectIdleTerminal()),
                    optionalInteger(connection, "terminalIdleTimeoutMinutes",
                            defaults.terminalIdleTimeoutMinutes()));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Global connection settings are outside the supported range", invalid);
        }
    }

    static Map<String, Object> encode(ConnectionSettings settings) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("connectTimeoutSeconds", settings.connectTimeoutSeconds());
        values.put("authenticationTimeoutSeconds", settings.authenticationTimeoutSeconds());
        values.put("keepAliveIntervalSeconds", settings.keepAliveIntervalSeconds());
        values.put("disconnectIdleTerminal", settings.disconnectIdleTerminal());
        values.put("terminalIdleTimeoutMinutes", settings.terminalIdleTimeoutMinutes());
        return values;
    }
}
