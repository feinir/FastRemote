// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.util.Arrays;

/** Terminal capability name advertised through TERM and SSH PTY requests. */
public enum TerminalType {
    XTERM_256COLOR("xterm-256color"),
    XTERM("xterm"),
    VT100("vt100"),
    ANSI("ansi"),
    LINUX("linux");

    private final String ptyName;

    TerminalType(String ptyName) {
        this.ptyName = ptyName;
    }

    public String ptyName() {
        return ptyName;
    }

    public static TerminalType fromPtyName(String value) {
        return Arrays.stream(values())
                .filter(type -> type.ptyName.equalsIgnoreCase(value == null ? "" : value.strip()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported terminal type: " + value));
    }

    @Override
    public String toString() {
        return ptyName;
    }
}
