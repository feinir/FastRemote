// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.i18n.Texts;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;

/** Keeps log actions inside the text menu without taking space from the activity pane. */
final class SftpLogContextMenu {
    private SftpLogContextMenu() { }

    static ContextMenu create(TextArea view, Runnable clear) {
        ContextMenu menu = LocalizedTextInputContextMenus.createMenu(view, Texts.localizer());
        MenuItem clearLog = new MenuItem(Texts.text("sftp.log.clear"));
        clearLog.setId("sftp-log-clear");
        clearLog.setOnAction(event -> clear.run());
        Runnable appendClear = () -> menu.getItems().addAll(new SeparatorMenuItem(), clearLog);
        appendClear.run();
        // The standard text actions are rebuilt before every opening; append after that refresh.
        ApplicationContextMenus.prepareBeforeShow(menu, appendClear);
        return menu;
    }
}
