// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.InputEvent;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Avoids GTK's disabled-window size lock while preserving modal input blocking. */
final class LinuxDialogModality {
    private LinuxDialogModality() { }

    static Runnable block(Window owner, Modality modality, Window dialogWindow) {
        List<Runnable> restore = new ArrayList<>();
        for (Window window : List.copyOf(Window.getWindows())) {
            if (window == dialogWindow || window.getScene() == null) continue;
            if (modality == Modality.NONE) continue;
            if (modality == Modality.WINDOW_MODAL && !ownedBy(owner, window)) continue;
            Scene scene = window.getScene();
            EventHandler<InputEvent> filter = event -> {
                event.consume();
                if (dialogWindow != null && dialogWindow.isShowing()) dialogWindow.requestFocus();
            };
            EventHandler<javafx.stage.WindowEvent> closeFilter = event -> event.consume();
            scene.addEventFilter(InputEvent.ANY, filter);
            window.addEventFilter(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, closeFilter);
            restore.add(() -> {
                scene.removeEventFilter(InputEvent.ANY, filter);
                window.removeEventFilter(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, closeFilter);
            });
        }
        return () -> restore.forEach(Runnable::run);
    }

    private static boolean ownedBy(Window owner, Window window) {
        for (Window current = owner; current != null; ) {
            if (current == window) return true;
            current = current instanceof javafx.stage.Stage stage ? stage.getOwner() : null;
        }
        return false;
    }

    static <T> T nativeDialog(Window owner, java.util.function.Function<Window, T> show) {
        if (!LocalizedTextInputContextMenus.isLinux()) return show.apply(owner);
        Runnable restore = block(owner, Modality.APPLICATION_MODAL, null);
        try { return show.apply(null); }
        finally { restore.run(); }
    }

    static void prepare(javafx.scene.control.Dialog<?> dialog) {
        if (!LocalizedTextInputContextMenus.isLinux()) return;
        Modality[] modality = {null};
        Runnable[] restore = {() -> {}};
        dialog.addEventHandler(javafx.scene.control.DialogEvent.DIALOG_SHOWING, event -> {
            if (modality[0] == null) {
                modality[0] = dialog.getModality();
                dialog.initModality(Modality.NONE);
            }
        });
        dialog.addEventHandler(javafx.scene.control.DialogEvent.DIALOG_SHOWN, event ->
                restore[0] = block(dialog.getOwner(), modality[0], dialog.getDialogPane().getScene().getWindow()));
        dialog.addEventHandler(javafx.scene.control.DialogEvent.DIALOG_HIDDEN, event -> {
            restore[0].run(); restore[0] = () -> {};
        });
    }

    static void prepare(javafx.stage.Stage dialog) {
        if (!LocalizedTextInputContextMenus.isLinux()) return;
        Modality modality = dialog.getModality();
        dialog.initModality(Modality.NONE);
        Runnable[] restore = {() -> {}};
        dialog.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING,
                event -> restore[0] = block(dialog.getOwner(), modality, dialog));
        dialog.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, event -> {
            restore[0].run(); restore[0] = () -> {};
        });
    }
}
