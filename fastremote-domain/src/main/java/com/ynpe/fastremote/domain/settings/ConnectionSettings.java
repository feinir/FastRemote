// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.time.Duration;

/** Global SSH connection timing and terminal-idle policy. */
public record ConnectionSettings(
        int connectTimeoutSeconds,
        int authenticationTimeoutSeconds,
        int keepAliveIntervalSeconds,
        boolean disconnectIdleTerminal,
        int terminalIdleTimeoutMinutes) {
    public static final int MIN_CONNECT_TIMEOUT_SECONDS = 3;
    public static final int MAX_CONNECT_TIMEOUT_SECONDS = 120;
    public static final int MIN_AUTHENTICATION_TIMEOUT_SECONDS = 3;
    public static final int MAX_AUTHENTICATION_TIMEOUT_SECONDS = 180;
    public static final int MIN_KEEP_ALIVE_INTERVAL_SECONDS = 5;
    public static final int MAX_KEEP_ALIVE_INTERVAL_SECONDS = 600;
    public static final int MIN_TERMINAL_IDLE_TIMEOUT_MINUTES = 1;
    public static final int MAX_TERMINAL_IDLE_TIMEOUT_MINUTES = 1_440;

    public ConnectionSettings {
        requireRange(connectTimeoutSeconds, MIN_CONNECT_TIMEOUT_SECONDS,
                MAX_CONNECT_TIMEOUT_SECONDS, "connectTimeoutSeconds");
        requireRange(authenticationTimeoutSeconds, MIN_AUTHENTICATION_TIMEOUT_SECONDS,
                MAX_AUTHENTICATION_TIMEOUT_SECONDS, "authenticationTimeoutSeconds");
        requireRange(keepAliveIntervalSeconds, MIN_KEEP_ALIVE_INTERVAL_SECONDS,
                MAX_KEEP_ALIVE_INTERVAL_SECONDS, "keepAliveIntervalSeconds");
        requireRange(terminalIdleTimeoutMinutes, MIN_TERMINAL_IDLE_TIMEOUT_MINUTES,
                MAX_TERMINAL_IDLE_TIMEOUT_MINUTES, "terminalIdleTimeoutMinutes");
    }

    public static ConnectionSettings defaults() {
        return new ConnectionSettings(20, 30, 30, false, 30);
    }

    public Duration connectTimeout() {
        return Duration.ofSeconds(connectTimeoutSeconds);
    }

    public Duration authenticationTimeout() {
        return Duration.ofSeconds(authenticationTimeoutSeconds);
    }

    public Duration terminalIdleTimeout() {
        return Duration.ofMinutes(terminalIdleTimeoutMinutes);
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
    }
}
