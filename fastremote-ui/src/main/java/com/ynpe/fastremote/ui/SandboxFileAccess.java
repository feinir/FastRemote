// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.i18n.Texts;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** Entry points for macOS to grant access to explicitly selected local paths. */
final class SandboxFileAccess {
    private SandboxFileAccess() { }

    static HBox notice(Runnable chooseDirectory, Runnable chooseFiles) {
        Label message = new Label(Texts.text("localFiles.sandboxNotice"));
        message.setWrapText(true);
        message.setMinWidth(0);
        HBox.setHgrow(message, Priority.ALWAYS);
        HBox notice = new HBox(6, message, link("localFiles.authorizeDirectory", chooseDirectory));
        if (chooseFiles != null) {
            notice.getChildren().add(link("localFiles.chooseUploadFiles", chooseFiles));
        }
        notice.setAlignment(Pos.CENTER_LEFT);
        notice.getStyleClass().add("inline-security-note");
        return notice;
    }

    static Optional<Path> chooseDirectory(Window owner) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(Texts.text("localFiles.authorizeDirectory"));
        return Optional.ofNullable(PlatformFileChooser.showDialog(chooser, owner)).map(File::toPath);
    }

    static List<Path> chooseUploadFiles(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Texts.text("localFiles.chooseUploadFiles"));
        List<File> selected = PlatformFileChooser.showOpenMultipleDialog(chooser, owner);
        return selected == null ? List.of() : selected.stream().map(File::toPath).toList();
    }

    private static Hyperlink link(String key, Runnable action) {
        Hyperlink link = new Hyperlink(Texts.text(key));
        link.setOnAction(event -> action.run());
        return link;
    }
}
