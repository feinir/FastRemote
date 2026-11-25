// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.EnumSet;

/** Collapses duplicate Scene and menu accelerator dispatch within one JavaFX event cycle. */
final class ShortcutDispatchGate {
    private final EnumSet<ShortcutAction> pending = EnumSet.noneOf(ShortcutAction.class);

    boolean begin(ShortcutAction action) {
        return pending.add(action);
    }

    void release(ShortcutAction action) {
        pending.remove(action);
    }
}
