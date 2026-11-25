// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.SftpBookmarks;
import com.ynpe.fastremote.domain.transfer.RemotePathSyntax;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

final class SftpBookmarkDialog {
    private SftpBookmarkDialog() {}

    static void show(Window owner, String title, SftpBookmarks current,
            Function<SftpBookmarks, CompletionStage<Void>> save) {
        createDialog(owner, title, current, save).showAndWait();
    }

    static Dialog<Void> createDialog(Window owner, String title, SftpBookmarks current,
            Function<SftpBookmarks, CompletionStage<Void>> save) {
        Objects.requireNonNull(save, "save");
        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setResizable(true);
        dialog.setTitle(title);
        dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7ea9f30a94d3d78b"));
        DialogSpacing.prepare(dialog);
        configureDialogGeometry(dialog);
        configureDialogButtons(dialog);

        ListView<String> local = ReorderableListView.create(current.localPaths());
        ListView<String> remote = ReorderableListView.create(current.remotePaths());
        DialogSpacing.closeOnEscape(dialog, local);
        DialogSpacing.closeOnEscape(dialog, remote);
        BookmarkPersistenceQueue persistence = new BookmarkPersistenceQueue(save);
        AtomicBoolean changeScheduled = new AtomicBoolean();
        Runnable persistChangedLists = () -> {
            if (!changeScheduled.compareAndSet(false, true)) return;
            Platform.runLater(() -> {
                changeScheduled.set(false);
                persistence.submit(new SftpBookmarks(
                        List.copyOf(local.getItems()), List.copyOf(remote.getItems())));
            });
        };
        local.getItems().addListener((ListChangeListener<String>) ignored -> persistChangedLists.run());
        remote.getItems().addListener((ListChangeListener<String>) ignored -> persistChangedLists.run());
        VBox localContent = localPane(owner, local);
        VBox remoteContent = remotePane(owner, remote);
        VBox shell = DialogTabSwitcher.create(List.of(
                new DialogTabSwitcher.Page(
                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.bf4ad761840fcd0a"), localContent),
                new DialogTabSwitcher.Page(
                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.7ba00927dde9122f"), remoteContent)));
        shell.getStyleClass().add("sftp-bookmark-dialog");
        dialog.getDialogPane().setContent(shell);
        return dialog;
    }

    static void configureDialogButtons(javafx.scene.control.Dialog<?> dialog) {
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK);
    }

    static void configureDialogGeometry(javafx.scene.control.Dialog<?> dialog) {
        DialogSpacing.configureResizableWindowGeometry(dialog, 600.0, 390.0, 600.0, 390.0);
    }

    private static VBox localPane(Window owner, ListView<String> list) {
        Button add = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bce53fe72c92f970"));
        Button delete = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2f9daa828907b93f"));
        add.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0e6ccf997977b289"));
            File selected = PlatformFileChooser.showDialog(chooser, owner);
            if (selected != null) addUnique(list,
                    selected.toPath().toAbsolutePath().normalize().toString(), false);
        });
        delete.setOnAction(event -> list.getItems().removeAll(List.copyOf(list.getSelectionModel().getSelectedItems())));
        return pane(list, add, delete);
    }

    private static VBox remotePane(Window owner, ListView<String> list) {
        Button add = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bce53fe72c92f970"));
        Button delete = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2f9daa828907b93f"));
        add.setOnAction(event -> {
            TextInputDialog input = new TextInputDialog("/");
            input.initOwner(owner);
            input.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.611d193c0b9fd235"));
            input.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c2c339f3eca8e7ac"));
            input.setContentText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e4ccf934ab2438ee"));
            DialogSpacing.prepare(input, 460.0);
            input.showAndWait().map(String::strip).filter(value -> !value.isEmpty())
                    .ifPresent(value -> addUnique(list, value, true));
        });
        delete.setOnAction(event -> list.getItems().removeAll(List.copyOf(list.getSelectionModel().getSelectedItems())));
        return pane(list, add, delete);
    }

    private static VBox pane(ListView<String> list, Button add, Button delete) {
        list.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        HBox actions = new HBox(8.0, add, delete);
        actions.getStyleClass().add("sftp-bookmark-actions");
        VBox pane = new VBox(8.0, list, actions);
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setPrefHeight(180.0);
        list.setMinHeight(150.0);
        list.setMaxHeight(Double.MAX_VALUE);
        pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return pane;
    }

    private static void addUnique(ListView<String> list, String path, boolean remote) {
        String normalized = remote ? normalizeRemoteBookmarkInput(path) : normalizeBookmarkInput(path);
        if (!list.getItems().contains(normalized)) list.getItems().add(normalized);
        list.getSelectionModel().select(normalized);
        list.scrollTo(normalized);
    }

    static String normalizeBookmarkInput(String path) {
        String normalized = Objects.requireNonNull(path, "path").strip();
        int minimumLength = normalized.matches("^[A-Za-z]:[\\\\/]$") ? 3 : 1;
        while (normalized.length() > minimumLength
                && (normalized.endsWith("/") || normalized.endsWith("\\"))) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    static String normalizeRemoteBookmarkInput(String path) {
        return RemotePathSyntax.normalizeBookmark(path);
    }

    static final class BookmarkPersistenceQueue {
        private final Function<SftpBookmarks, CompletionStage<Void>> save;
        private CompletionStage<Void> tail = CompletableFuture.completedFuture(null);

        BookmarkPersistenceQueue(Function<SftpBookmarks, CompletionStage<Void>> save) {
            this.save = Objects.requireNonNull(save, "save");
        }

        synchronized void submit(SftpBookmarks bookmarks) {
            tail = tail.handle((ignored, error) -> null).thenCompose(ignored -> {
                try {
                    return Objects.requireNonNull(save.apply(bookmarks), "save result");
                } catch (RuntimeException failure) {
                    return CompletableFuture.failedFuture(failure);
                }
            });
        }

        synchronized CompletionStage<Void> completion() {
            return tail;
        }
    }
}
