// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EditorInitialFocusTest {
    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test void skipsButtonsReadOnlyDisabledAndHiddenInputsAndDoesNotStealLaterFocus() throws Exception {
        Dialog<Void> dialog = fx(() -> {
            Dialog<Void> result = new Dialog<>();
            DialogSpacing.prepare(result);
            result.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
            TextField readOnly = new TextField("read-only");
            readOnly.setEditable(false);
            TextField disabled = new TextField("disabled");
            VBox disabledContainer = new VBox(disabled);
            disabledContainer.setDisable(true);
            VBox hiddenContainer = new VBox(new TextField("hidden"));
            hiddenContainer.setVisible(false);
            TextField first = new TextField("name");
            first.setId("first-input");
            TextField second = new TextField("second");
            second.setId("second-input");
            result.getDialogPane().setContent(new VBox(new Button("clipboard"), readOnly,
                    disabledContainer, hiddenContainer, first, second));
            DialogSpacing.focusFirstInputOnShown(result);
            DialogSpacing.focusFirstInputOnShown(result);
            result.show();
            return result;
        });
        try {
            fx(() -> {
                assertSame(dialog.getDialogPane().lookup("#first-input"), dialog.getDialogPane().getScene().getFocusOwner());
                TextField first = (TextField) dialog.getDialogPane().lookup("#first-input");
                assertEquals(first.getLength(), first.getCaretPosition());
                assertEquals("", first.getSelectedText());
                dialog.getDialogPane().lookup("#second-input").requestFocus();
                return null;
            });
            fx(() -> {
                assertSame(dialog.getDialogPane().lookup("#second-input"), dialog.getDialogPane().getScene().getFocusOwner());
                return null;
            });
        } finally { fx(() -> { dialog.close(); return null; }); }
    }

    @Test void standardNewAndRenamePromptsFocusTheirEditor() throws Exception {
        for (String initial : new String[]{"", "existing name"}) {
            TextInputDialog dialog = fx(() -> {
                TextInputDialog result = new TextInputDialog(initial);
                DialogSpacing.prepare(result);
                result.show();
                return result;
            });
            try {
                fx(() -> {
                    assertSame(dialog.getEditor(), dialog.getDialogPane().getScene().getFocusOwner());
                    assertEquals(initial, dialog.getEditor().getText());
                    assertEquals(initial.length(), dialog.getEditor().getCaretPosition());
                    assertEquals(initial.length(), dialog.getEditor().getAnchor());
                    assertEquals("", dialog.getEditor().getSelectedText());
                    return null;
                });
            } finally { fx(() -> { dialog.close(); return null; }); }
        }
    }

    @Test void newHostAndCredentialDialogsInitiallyFocusName() throws Exception {
        for (boolean host : new boolean[]{true, false}) {
            FutureTask<Object> opened = new FutureTask<>(() -> host
                    ? HostEditorDialog.show(null, java.util.List.of(), java.util.List.of(), java.util.List.of(),
                            com.ynpe.fastremote.domain.vault.VaultStatus.LOCKED, java.util.Optional.empty(),
                            ignored -> CompletableFuture.completedFuture(false))
                    : CredentialEditorDialog.showCreate(null));
            Platform.runLater(opened);
            DialogPane pane = fx(() -> javafx.stage.Window.getWindows().stream()
                    .filter(window -> window.getScene() != null && window.getScene().getRoot() instanceof DialogPane)
                    .map(window -> (DialogPane) window.getScene().getRoot()).findFirst().orElseThrow());
            try {
                fx(() -> {
                    assertTrue(pane.getScene().getFocusOwner() instanceof TextField);
                    TextField input = (TextField) pane.getScene().getFocusOwner();
                    assertEquals(com.ynpe.fastremote.i18n.Texts.text(host
                            ? "legacy.java.270c4bee5ae7c922" : "legacy.java.e436877a7956dd3a"), input.getPromptText());
                    assertTrue(input.isEditable());
                    return null;
                });
            } finally {
                fx(() -> { ((Button) pane.lookupButton(ButtonType.CANCEL)).fire(); return null; });
                opened.get(5, TimeUnit.SECONDS);
            }
        }
    }

    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(5, TimeUnit.SECONDS);
    }
}
