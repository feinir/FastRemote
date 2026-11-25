// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.transfer.TransferConflict;
import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;
import com.ynpe.fastremote.i18n.Texts;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.Parent;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Serial, non-nested native conflict dialogs for SFTP transfer workers. */
final class TransferConflictDialogQueue implements AutoCloseable {
    private static final SftpConflictDecision CANCEL =
            new SftpConflictDecision(TransferConflictChoice.CANCEL, false);

    private final Window owner;
    private final ArrayDeque<Request> pending = new ArrayDeque<>();
    private Request active;
    private Alert activeAlert;
    private Parent ownerRoot;
    private boolean ownerPreviouslyDisabled;
    private boolean ownerBlocked;
    private boolean closed;

    TransferConflictDialogQueue(Window owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    CompletableFuture<SftpConflictDecision> request(TransferConflict conflict) {
        return request(conflict, false);
    }

    CompletableFuture<SftpConflictDecision> request(TransferConflict conflict, boolean automated) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Transfer conflict dialogs must be requested on the JavaFX thread");
        }
        Objects.requireNonNull(conflict, "conflict");
        CompletableFuture<SftpConflictDecision> result = new CompletableFuture<>();
        if (closed) {
            result.complete(CANCEL);
            return result;
        }
        PauseTransition timeout = automated ? new PauseTransition(Duration.seconds(3)) : null;
        Request request = new Request(conflict, result, timeout);
        pending.addLast(request);
        if (timeout != null) {
            timeout.setOnFinished(event -> expire(request));
            timeout.play();
        }
        showNext();
        return result;
    }

    private void showNext() {
        if (closed || active != null || pending.isEmpty()) return;
        active = pending.removeFirst();

        TransferConflict conflict = active.conflict();
        ButtonType overwrite = new ButtonType(Texts.text("legacy.java.4ce4c98eb27e4b65"),
                ButtonBar.ButtonData.YES);
        ButtonType skip = new ButtonType(Texts.text("legacy.java.fc50a99caae0cddc"),
                ButtonBar.ButtonData.NO);
        ButtonType rename = new ButtonType(Texts.text("legacy.java.fbf81c1e9fe405d9"),
                ButtonBar.ButtonData.OTHER);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        activeAlert = alert;
        alert.initOwner(owner);
        // A native GTK DnD operation owns a pointer grab until Glass has fully
        // unwound the drag. Creating an application-modal Stage during that
        // handoff asks GTK for a competing grab and can leave the new dialog
        // keyboard-responsive but unable to receive mouse clicks. Keep the same
        // native Alert window and implement modality in the owner's scene graph,
        // so this prompt never requests a second native pointer grab.
        alert.initModality(Modality.NONE);
        blockOwner();
        alert.setTitle(Texts.text("legacy.java.9511c3111ae11038"));
        alert.setHeaderText(Texts.text(conflict.directory()
                ? "legacy.java.2b858dff748de595" : "legacy.java.3b3022f02c9dc224"));
        Label detail = new Label(Texts.text("legacy.java.32d7e7d16557bd4f") + conflict.source()
                + Texts.text("legacy.java.42653c3223c7311b") + conflict.target());
        detail.setWrapText(true);
        CheckBox applyAll = new CheckBox(Texts.text("legacy.java.558b2b869e0e9047"));
        alert.getDialogPane().setContent(new VBox(10.0, detail, applyAll));
        alert.getButtonTypes().setAll(overwrite, skip, rename, ButtonType.CANCEL);
        alert.setOnHidden(event -> {
            ButtonType selected = alert.getResult();
            TransferConflictChoice choice = selected == overwrite ? TransferConflictChoice.OVERWRITE
                    : selected == skip ? TransferConflictChoice.SKIP
                    : selected == rename ? TransferConflictChoice.RENAME
                    : TransferConflictChoice.CANCEL;
            finish(new SftpConflictDecision(choice,
                    applyAll.isSelected() && choice != TransferConflictChoice.CANCEL));
        });

        // Do not enter a nested event loop while Glass is unwinding native DnD.
        alert.show();
        if (active != null && activeAlert == alert && active.timeout() != null) active.timeout().playFromStart();
    }

    private void expire(Request request) {
        var decision = new SftpConflictDecision(TransferConflictChoice.CANCEL, false, true);
        if (active == request) {
            activeAlert.setOnHidden(null);
            activeAlert.setResult(ButtonType.CANCEL);
            activeAlert.close();
            finish(decision);
        } else if (pending.remove(request)) {
            request.result().complete(decision);
        }
    }

    private void finish(SftpConflictDecision decision) {
        if (active == null) return;
        Request completed = active;
        active = null;
        activeAlert = null;
        completed.complete(decision);
        if (pending.isEmpty()) unblockOwner();
        else showNext();
    }

    private void blockOwner() {
        if (ownerBlocked || owner.getScene() == null) return;
        ownerRoot = owner.getScene().getRoot();
        if (ownerRoot == null) return;
        ownerPreviouslyDisabled = ownerRoot.isDisable();
        ownerRoot.setDisable(true);
        ownerBlocked = true;
    }

    private void unblockOwner() {
        if (!ownerBlocked) return;
        if (ownerRoot != null) ownerRoot.setDisable(ownerPreviouslyDisabled);
        ownerRoot = null;
        ownerBlocked = false;
    }

    boolean isShowing() {
        return activeAlert != null && activeAlert.isShowing();
    }

    Alert activeAlert() {
        return activeAlert;
    }

    int pendingCount() {
        return pending.size();
    }

    @Override
    public void close() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::close);
            return;
        }
        if (closed) return;
        closed = true;
        Alert alert = activeAlert;
        if (alert != null) {
            alert.setOnHidden(null);
            alert.close();
        }
        activeAlert = null;
        if (active != null) {
            active.complete(CANCEL);
            active = null;
        }
        Request request;
        while ((request = pending.pollFirst()) != null) request.complete(CANCEL);
        unblockOwner();
    }

    private record Request(
            TransferConflict conflict,
            CompletableFuture<SftpConflictDecision> result,
            PauseTransition timeout) {
        void complete(SftpConflictDecision decision) {
            if (timeout != null) timeout.stop();
            result.complete(decision);
        }
    }
}
