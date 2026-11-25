// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.i18n.Texts;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

final class LocalTerminalPresentation {
    private static final URI DOWNLOAD_PAGE = URI.create("https://download.ynpe.com/community/");

    private LocalTerminalPresentation() { }

    static Tab createTab(String title, Node terminalHost, boolean sandboxed, Consumer<String> showError) {
        if (!sandboxed) {
            return new Tab(title, terminalHost);
        }

        Label message = new Label(Texts.text("localTerminal.sandboxNotice"));
        message.setWrapText(true);
        message.setMinWidth(0);
        HBox.setHgrow(message, Priority.ALWAYS);

        Hyperlink download = new Hyperlink(Texts.text("localTerminal.downloadFullVersion"));
        download.setTooltip(new Tooltip(DOWNLOAD_PAGE.toString()));
        download.setOnAction(event -> openDownloadPage(showError));

        HBox notice = new HBox(6, message, download);
        notice.setAlignment(Pos.CENTER_LEFT);
        notice.getStyleClass().add("inline-security-note");

        BorderPane content = new BorderPane(terminalHost);
        content.setTop(notice);
        return new Tab(title, content);
    }

    private static void openDownloadPage(Consumer<String> showError) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(DOWNLOAD_PAGE);
                return;
            } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
                // Show the address so the user can open it manually if the browser fails.
            }
        }
        showError.accept(Texts.text("legacy.java.c9d050f4689ed402") + " " + DOWNLOAD_PAGE);
    }
}
