// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.terminal.FastRemoteTerminal;
import com.ynpe.fastremote.ui.TerminalContextMenuAction;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Maps FastRemote's terminal menu port to the concrete terminal adapter contract. */
final class TerminalContextMenuBridge {
    private TerminalContextMenuBridge() { }

    static Supplier<List<FastRemoteTerminal.ContextMenuAction>> adapt(
            Supplier<List<TerminalContextMenuAction>> source) {
        Objects.requireNonNull(source, "source");
        return () -> source.get().stream().map(TerminalContextMenuBridge::adapt).toList();
    }

    private static FastRemoteTerminal.ContextMenuAction adapt(TerminalContextMenuAction action) {
        if (action instanceof TerminalContextMenuAction.Item item) {
            return new FastRemoteTerminal.ContextMenuAction.Item(
                    item.label(), item.callback(), item.enabled());
        }
        TerminalContextMenuAction.Submenu submenu = (TerminalContextMenuAction.Submenu) action;
        return new FastRemoteTerminal.ContextMenuAction.Submenu(
                submenu.label(), submenu.children().stream()
                        .map(TerminalContextMenuBridge::adapt).toList(),
                submenu.enabled());
    }
}
