// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.scene.Node;
import javafx.scene.control.ContextMenu;

/** Host UI policy for terminal-owned popups; terminal actions and dismissal remain untouched. */
public final class TerminalContextMenuPreparation {
    private TerminalContextMenuPreparation() { }

    public static void prepare(ContextMenu menu, Node owner) {
        if (LocalizedTextInputContextMenus.isLinux()) ContextMenuScreenPlacement.install(menu);
        ThemeManager.prepareContextMenuBeforeShow(menu, owner);
    }
}
