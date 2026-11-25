// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.util.Objects;

/** Remote terminal discovery preferences and optional user-authored supplemental commands. */
public record RemoteProbeSettings(
        boolean probeServerInformation,
        String linuxSupplement,
        String macosSupplement,
        String freebsdSupplement,
        String windowsSupplement) {
    public static final int MAX_SUPPLEMENT_CHARS = 64 * 1024;
    public static final int MAX_SUPPLEMENT_COMMANDS = 32;

    public RemoteProbeSettings {
        linuxSupplement = command(linuxSupplement, "linuxSupplement");
        macosSupplement = command(macosSupplement, "macosSupplement");
        freebsdSupplement = command(freebsdSupplement, "freebsdSupplement");
        windowsSupplement = command(windowsSupplement, "windowsSupplement");
    }

    public static RemoteProbeSettings defaults() {
        return new RemoteProbeSettings(true, "", "", "", "");
    }

    public String supplementFor(String operatingSystem) {
        String value = Objects.requireNonNullElse(operatingSystem, "");
        if (value.equalsIgnoreCase("Linux")) return linuxSupplement;
        if (value.equalsIgnoreCase("Darwin")) return macosSupplement;
        if (value.equalsIgnoreCase("FreeBSD")) return freebsdSupplement;
        if (value.equalsIgnoreCase("Windows")) return windowsSupplement;
        return "";
    }

    private static String command(String value, String name) {
        String command = Objects.requireNonNull(value, name).strip();
        if (command.length() > MAX_SUPPLEMENT_CHARS) {
            throw new IllegalArgumentException(name + " exceeds 64 KiB");
        }
        if (command.lines().map(String::strip).filter(line -> !line.isEmpty()).count()
                > MAX_SUPPLEMENT_COMMANDS) {
            throw new IllegalArgumentException(name + " contains more than 32 commands");
        }
        return command;
    }
}
