package com.techsenger.jeditermfx.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Toolkit-neutral description of a host action rendered by JediTermFX. */
public sealed interface TerminalContextMenuEntry
        permits TerminalContextMenuEntry.Action, TerminalContextMenuEntry.Submenu {
    String label();

    BooleanSupplier enabled();

    record Action(String label, Runnable callback, BooleanSupplier enabled)
            implements TerminalContextMenuEntry {
        public Action {
            label = requireLabel(label);
            Objects.requireNonNull(callback, "callback");
            Objects.requireNonNull(enabled, "enabled");
        }
    }

    record Submenu(String label, List<TerminalContextMenuEntry> children, BooleanSupplier enabled)
            implements TerminalContextMenuEntry {
        public Submenu {
            label = requireLabel(label);
            children = List.copyOf(Objects.requireNonNull(children, "children"));
            Objects.requireNonNull(enabled, "enabled");
        }
    }

    static Action action(String label, Runnable callback) {
        return new Action(label, callback, () -> true);
    }

    static Submenu submenu(String label, List<TerminalContextMenuEntry> children) {
        return new Submenu(label, children, () -> true);
    }

    private static String requireLabel(String label) {
        String value = Objects.requireNonNull(label, "label").strip();
        if (value.isEmpty()) throw new IllegalArgumentException("label must not be blank");
        return value;
    }
}
