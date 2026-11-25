// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.terminal;

import java.util.OptionalInt;

/** Final state of an interactive shell, without terminal input or remote diagnostic payloads. */
public record TerminalTermination(Kind kind, OptionalInt exitCode) {
    public TerminalTermination {
        if (kind == null || exitCode == null) {
            throw new NullPointerException("Terminal termination fields are required");
        }
    }

    public static TerminalTermination exited(int exitCode) {
        return new TerminalTermination(Kind.NORMAL, OptionalInt.of(exitCode));
    }

    public static TerminalTermination eof() {
        return new TerminalTermination(Kind.NORMAL, OptionalInt.empty());
    }

    public static TerminalTermination disconnected() {
        return new TerminalTermination(Kind.NETWORK_FAILURE, OptionalInt.empty());
    }

    public static TerminalTermination idleTimeout() {
        return new TerminalTermination(Kind.IDLE_TIMEOUT, OptionalInt.empty());
    }

    public enum Kind {
        NORMAL,
        NETWORK_FAILURE,
        IDLE_TIMEOUT
    }
}
