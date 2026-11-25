// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.ui.ConfigurationDialog.WorkspaceLayout;
import com.ynpe.fastremote.domain.settings.SftpBookmarks;
import com.ynpe.fastremote.domain.settings.SftpSettings;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

final class ConfigurationSaveController {
    private ConfigurationSaveController() {}

    static void saveChanges(
            Dialog<Void> dialog,
            WorkspaceLayout selected,
            boolean closeAfterSave,
            Function<WorkspaceLayout, CompletionStage<Void>> save,
            EditTracker edits,
            AtomicBoolean saving,
            AtomicBoolean approvedClose,
            Button applyButton,
            Button confirmButton,
            Button cancelButton,
            Runnable updateDirtyState) {
        if (!saving.compareAndSet(false, true)) {
            return;
        }
        applyButton.setDisable(true);
        confirmButton.setDisable(true);
        cancelButton.setDisable(true);
        CompletionStage<Void> operation;
        try {
            operation = Objects.requireNonNull(save.apply(selected), "save result");
        } catch (RuntimeException failure) {
            operation = java.util.concurrent.CompletableFuture.failedFuture(failure);
        }
        operation.whenComplete((ignored, error) -> Platform.runLater(() -> {
            saving.set(false);
            confirmButton.setDisable(false);
            cancelButton.setDisable(false);
            if (error != null) {
                updateDirtyState.run();
                Alert failure = new Alert(Alert.AlertType.ERROR);
                failure.initOwner(dialog.getDialogPane().getScene().getWindow());
                failure.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.472c5529bab8ecee"));
                failure.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6e1d931cdd0db0a2"));
                failure.setContentText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.02f4a7b4347745d5"));
                failure.show();
                return;
            }
            edits.markSaved(selected);
            updateDirtyState.run();
            if (closeAfterSave) {
                approvedClose.set(true);
                // This is an already-approved programmatic close. hide() avoids a second
                // Dialog close-request/result negotiation after the asynchronous save.
                dialog.hide();
            }
        }));
    }

    static boolean confirmDiscardChanges(Window owner) {
        ButtonType discard = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9b7824cefa1e8b16"), ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8ec50b190b3a8b7d"));
        alert.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.27c26284b231529b"));
        alert.setContentText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3ad4f8b0366e96ed"));
        alert.getButtonTypes().setAll(ButtonType.CANCEL, discard);
        return alert.showAndWait().filter(discard::equals).isPresent();
    }

    static final class EditTracker {
        private WorkspaceLayout saved;

        EditTracker(WorkspaceLayout saved) {
            this.saved = Objects.requireNonNull(saved, "saved");
        }

        boolean isDirty(WorkspaceLayout current) {
            return !saved.equals(Objects.requireNonNull(current, "current"));
        }

        void markSaved(WorkspaceLayout current) {
            saved = Objects.requireNonNull(current, "current");
        }

        void markGlobalBookmarksSaved(SftpBookmarks bookmarks) {
            SftpSettings current = saved.sftpSettings();
            SftpSettings updated = new SftpSettings(
                    current.showHiddenFiles(), current.concurrentTransfers(), bookmarks,
                    current.hostBookmarks(), current.maxEditableFileBytes(),
                    current.editorFontFamily(), current.editorFontSize(), current.editorTheme(),
                    current.doubleClickFileAction(), current.editorTextAppearance());
            saved = saved.withSftpSettings(updated);
        }
    }
}
