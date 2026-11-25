// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** UI-to-adapter terminal menu contract without JavaFX menu objects. */
public sealed interface TerminalContextMenuAction
        permits TerminalContextMenuAction.Item, TerminalContextMenuAction.Submenu {
    String label();

    BooleanSupplier enabled();

    record Item(String label, Runnable callback, BooleanSupplier enabled)
            implements TerminalContextMenuAction {
        public Item {
            label = requireLabel(label);
            Objects.requireNonNull(callback, "callback");
            Objects.requireNonNull(enabled, "enabled");
        }
    }

    record Submenu(String label, List<TerminalContextMenuAction> children, BooleanSupplier enabled)
            implements TerminalContextMenuAction {
        public Submenu {
            label = requireLabel(label);
            children = List.copyOf(Objects.requireNonNull(children, "children"));
            Objects.requireNonNull(enabled, "enabled");
        }
    }

    static Item item(String label, Runnable callback) {
        return new Item(label, callback, () -> true);
    }

    static Item item(String label, Runnable callback, BooleanSupplier enabled) {
        return new Item(label, callback, enabled);
    }

    static Submenu submenu(String label, List<TerminalContextMenuAction> children) {
        return new Submenu(label, children, () -> true);
    }

    static Submenu submenu(
            String label, List<TerminalContextMenuAction> children, BooleanSupplier enabled) {
        return new Submenu(label, children, enabled);
    }

    private static String requireLabel(String label) {
        String value = Objects.requireNonNull(label, "label").strip();
        if (value.isEmpty()) throw new IllegalArgumentException("label must not be blank");
        return value;
    }
}
