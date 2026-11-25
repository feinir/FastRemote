// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.i18n.Texts;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
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

    static void chooseDirectory(Window owner, Path initialDirectory, SandboxFolderBookmarks bookmarks,
            Consumer<Path> navigate, Consumer<String> reportFailure) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(Texts.text("localFiles.authorizeDirectory"));
        if (initialDirectory != null && initialDirectory.toFile().isDirectory()) {
            chooser.setInitialDirectory(initialDirectory.toFile());
        }
        File selected = PlatformFileChooser.showDialog(chooser, owner);
        if (selected == null) return;
        Path folder = selected.toPath();
        try {
            bookmarks.remember(folder);
        } catch (IOException failure) {
            System.getLogger(SandboxFileAccess.class.getName()).log(System.Logger.Level.WARNING,
                    "Could not save a folder grant", failure);
            reportFailure.accept(Texts.text("localFiles.authorizationSaveFailed"));
        }
        navigate.accept(folder);
    }

    static Optional<Menu> savedFolders(SandboxFolderBookmarks bookmarks, Consumer<Path> navigate) {
        List<Path> folders = bookmarks.folders();
        if (folders.isEmpty()) return Optional.empty();
        Menu menu = new Menu(Texts.text("localFiles.authorizedFolders"));
        for (Path folder : folders) {
            MenuItem item = new MenuItem(folder.toString());
            item.setMnemonicParsing(false);
            item.setOnAction(event -> navigate.accept(folder));
            menu.getItems().add(item);
        }
        return Optional.of(menu);
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
