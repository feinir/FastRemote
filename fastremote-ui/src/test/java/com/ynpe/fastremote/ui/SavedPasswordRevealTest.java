// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.connection.*;
import com.ynpe.fastremote.domain.credential.*;
import com.ynpe.fastremote.domain.vault.*;
import com.ynpe.fastremote.i18n.Texts;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SavedPasswordRevealTest {
    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test void hostEditRevealsBoundPasswordWithoutReplacingItOnSave() throws Exception { exerciseEditor(true, false); }
    @Test void hostEditRevealsReferencedLibraryPasswordWithoutReplacingItOnSave() throws Exception { exerciseEditor(true, true); }
    @Test void credentialEditRevealsPasswordWithoutReplacingItOnSave() throws Exception { exerciseEditor(false, false); }

    @Test void hostEditDirectlyReplacesBoundPasswordAfterUnlock() throws Exception {
        exerciseEditor(true, false, true);
    }

    private void exerciseEditor(boolean hostEditor, boolean reusable) throws Exception {
        exerciseEditor(hostEditor, reusable, false);
    }

    private void exerciseEditor(boolean hostEditor, boolean reusable, boolean replace) throws Exception {
        SecretId id = SecretId.create();
        GlobalCredential credential = new GlobalCredential(id, "fixture", "fixture", AuthenticationType.PASSWORD,
                CredentialScope.REUSABLE, Optional.empty());
        SavedHost host = SavedHost.create("fixture", Optional.empty(), new ConnectionProfile(
                new Host("example.test"), new Port(22), new Username("fixture"),
                AuthenticationType.PASSWORD, Optional.empty())).withCredential(Optional.of(id));
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger unlocks = new AtomicInteger();
        AtomicInteger connectionTests = new AtomicInteger();
        AtomicReference<ResolvedCredential> loaded = new AtomicReference<>();
        SavedCredentialReader reader = (owner, requestedId) -> {
            assertEquals(id, requestedId);
            reads.incrementAndGet();
            ResolvedCredential result = secret();
            loaded.set(result);
            return CompletableFuture.completedFuture(result);
        };
        Stage owner = fx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 800, 600));
            stage.show();
            return stage;
        });
        FutureTask<Optional<?>> open = new FutureTask<>(() -> hostEditor
                ? HostEditorDialog.edit(owner, List.of(), List.of(host), reusable ? List.of(credential) : List.of(), List.of(), VaultStatus.LOCKED,
                        host, ignored -> { unlocks.incrementAndGet(); return CompletableFuture.completedFuture(true); },
                        (window, request, progress) -> {
                            connectionTests.incrementAndGet();
                            try (request) {
                                assertTrue(replace);
                                assertTrue(request.credentialId().isEmpty(), "test the replacement, not the saved secret");
                                char[] value = request.credential().orElseThrow().copySecret();
                                try { assertArrayEquals("fixture-replacement".toCharArray(), value); }
                                finally { Arrays.fill(value, '\0'); }
                            }
                            return new ConnectionTestAttempt(CompletableFuture.completedFuture(null), () -> {});
                        }, reader)
                : CredentialEditorDialog.showEdit(owner, credential, reader));
        Platform.runLater(open);
        try {
            DialogPane pane = fx(() -> Window.getWindows().stream().filter(window -> window instanceof Stage stage
                            && Texts.text(hostEditor ? "legacy.java.50158b7fd5855d55" : "legacy.java.4cf2ff842a0cfa66")
                            .equals(stage.getTitle()))
                    .map(window -> (DialogPane) window.getScene().getRoot()).findFirst().orElseThrow());
            RevealablePasswordEditor editor = fx(() -> pane.lookupAll(".revealable-password-editor").stream()
                    .map(RevealablePasswordEditor.class::cast)
                    .filter(value -> value.lookupAll(".revealable-password-input").stream()
                            .anyMatch(node -> node instanceof PasswordField field && !field.isDisabled()))
                    .findFirst().orElseThrow());
            fx(() -> {
                assertEquals(0, reads.get(), "opening an editor must not decrypt its password");
                assertTrue(pane.getScene().getFocusOwner() instanceof TextField);
                assertEquals(Texts.text(hostEditor ? "legacy.java.270c4bee5ae7c922" : "legacy.java.e436877a7956dd3a"),
                        ((TextField) pane.getScene().getFocusOwner()).getPromptText());
                assertTrue(((TextField) pane.getScene().getFocusOwner()).isEditable());
                TextField focusedName = (TextField) pane.getScene().getFocusOwner();
                assertEquals(focusedName.getLength(), focusedName.getCaretPosition());
                assertEquals("", focusedName.getSelectedText());
                if (hostEditor) {
                    ComboBox<?> source = pane.lookupAll(".combo-box").stream()
                            .filter(node -> node instanceof ComboBox<?> box
                                    && box.getValue() instanceof HostEditorDialog.CredentialSource)
                            .map(node -> (ComboBox<?>) node).findFirst().orElseThrow();
                    assertEquals(reusable ? HostEditorDialog.CredentialSourceKind.LIBRARY
                                    : HostEditorDialog.CredentialSourceKind.SAVE_TO_HOST,
                            ((HostEditorDialog.CredentialSource) source.getValue()).kind());
                    assertEquals(3, source.getItems().size());
                    ComboBox<?> auth = pane.lookupAll(".combo-box").stream()
                            .filter(node -> node instanceof ComboBox<?> box
                                    && box.getValue() instanceof AuthenticationType)
                            .map(node -> (ComboBox<?>) node).findFirst().orElseThrow();
                    assertEquals(reusable, auth.isDisabled());
                    PasswordField input = (PasswordField) editor.lookupAll(".revealable-password-input").stream()
                            .filter(PasswordField.class::isInstance).findFirst().orElseThrow();
                    assertEquals(!reusable, input.isEditable());
                }
                eye(editor).fire();
                return null;
            });
            fx(() -> {
                assertEquals(1, reads.get());
                assertTrue(editor.isRevealed());
                assertEquals("fixture-password", visibleText(editor).getText());
                assertFalse(visibleText(editor).isEditable());
                assertEquals("", editor.getText(), "saved preview must not become a replacement draft");
                assertThrows(IllegalStateException.class, () -> loaded.get().copySecret());
                if (replace) {
                    editor.setText("fixture-replacement");
                    ButtonType test = pane.getButtonTypes().stream()
                            .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OTHER).findFirst().orElseThrow();
                    ((Button) pane.lookupButton(test)).fire();
                    assertEquals(1, connectionTests.get());
                }
                return null;
            });
            fx(() -> {
                ButtonType save = pane.getButtonTypes().stream()
                        .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
                ((Button) pane.lookupButton(save)).fire();
                return null;
            });
            Optional<?> result = open.get(5, TimeUnit.SECONDS);
            assertTrue(result.isPresent());
            if (hostEditor) {
                HostEditorDialog.HostRequest request = (HostEditorDialog.HostRequest) result.orElseThrow();
                assertEquals(!replace, request.credential().isEmpty());
                assertEquals(replace ? 1 : 0, unlocks.get());
                if (replace) {
                    try (CredentialDraft draft = request.credential().orElseThrow()) {
                        char[] value = draft.copySecret();
                        try { assertArrayEquals("fixture-replacement".toCharArray(), value); }
                        finally { Arrays.fill(value, '\0'); }
                    }
                }
                assertEquals(Optional.of(id), request.host().credentialSecretId());
            } else {
                CredentialEditorDialog.Request request = (CredentialEditorDialog.Request) result.orElseThrow();
                assertTrue(request.replacement().isEmpty());
            }
            fx(() -> {
                assertFalse(editor.isRevealed());
                assertTrue(editor.getChildren().stream().filter(TextField.class::isInstance)
                        .map(TextField.class::cast).allMatch(field -> field.getText().isEmpty()));
                return null;
            });
        } finally {
            fx(() -> {
                for (Window window : List.copyOf(Window.getWindows()).reversed()) {
                    if (window instanceof Stage stage && stage.getOwner() == owner) stage.close();
                }
                owner.close();
                return null;
            });
        }
    }

    @Test void credentialEditChangesPasswordToCertificate() throws Exception {
        changeCredentialType(AuthenticationType.PASSWORD, AuthenticationType.PRIVATE_KEY);
    }

    @Test void credentialEditChangesCertificateToPassword() throws Exception {
        changeCredentialType(AuthenticationType.PRIVATE_KEY, AuthenticationType.PASSWORD);
    }

    private void changeCredentialType(AuthenticationType original, AuthenticationType target) throws Exception {
        GlobalCredential credential = new GlobalCredential(SecretId.create(), "fixture", "fixture",
                original, CredentialScope.REUSABLE, Optional.empty());
        AtomicInteger reads = new AtomicInteger();
        Stage owner = fx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 800, 600));
            stage.show();
            return stage;
        });
        FutureTask<Optional<CredentialEditorDialog.Request>> open = new FutureTask<>(() ->
                CredentialEditorDialog.showEdit(owner, credential, (window, id) -> {
                    reads.incrementAndGet();
                    return CompletableFuture.completedFuture(secret());
                }));
        Platform.runLater(open);
        try {
            fx(() -> {
                DialogPane pane = Window.getWindows().stream().filter(window -> window instanceof Stage stage
                                && Texts.text("legacy.java.4cf2ff842a0cfa66").equals(stage.getTitle()))
                        .map(window -> (DialogPane) window.getScene().getRoot()).findFirst().orElseThrow();
                @SuppressWarnings("unchecked")
                ComboBox<AuthenticationType> type = (ComboBox<AuthenticationType>) pane.lookup(".combo-box");
                assertFalse(type.isDisabled());
                assertEquals(original, type.getValue());
                type.setValue(target);
                ButtonType saveType = pane.getButtonTypes().stream()
                        .filter(value -> value.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
                Button save = (Button) pane.lookupButton(saveType);
                save.fire();
                assertTrue(pane.getScene().getWindow().isShowing(), "new type requires new material");
                assertEquals(0, reads.get());
                if (target == AuthenticationType.PASSWORD) {
                    RevealablePasswordEditor input = pane.lookupAll(".revealable-password-editor").stream()
                            .map(RevealablePasswordEditor.class::cast).filter(value -> !value.isDisabled())
                            .findFirst().orElseThrow();
                    input.setText("fixture-replacement");
                } else {
                    TextField certificate = pane.lookupAll(".text-field").stream()
                            .filter(TextField.class::isInstance).map(TextField.class::cast)
                            .filter(value -> Texts.text("legacy.java.720a21d86afc2182").equals(value.getPromptText()))
                            .findFirst().orElseThrow();
                    certificate.setText("/fixture/replacement-key");
                }
                save.fire();
                return null;
            });
            CredentialEditorDialog.Request result = open.get(5, TimeUnit.SECONDS).orElseThrow();
            assertEquals(1, reads.get(), "replacement must authorize access to the locked credential");
            try (CredentialDraft replacement = result.replacement().orElseThrow()) {
                assertEquals(target, replacement.authenticationType());
            }
        } finally { fx(() -> { owner.close(); return null; }); }
    }

    @Test void credentialReplacementWaitsForUnlockAndPreservesTheTypedPassword() throws Exception {
        GlobalCredential credential = new GlobalCredential(SecretId.create(), "fixture", "fixture",
                AuthenticationType.PASSWORD, CredentialScope.REUSABLE, Optional.empty());
        CompletableFuture<ResolvedCredential> authorized = new CompletableFuture<>();
        Stage owner = fx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 800, 600));
            stage.show();
            return stage;
        });
        FutureTask<Optional<CredentialEditorDialog.Request>> open = new FutureTask<>(() ->
                CredentialEditorDialog.showEdit(owner, credential, (window, id) -> authorized));
        Platform.runLater(open);
        try {
            DialogPane pane = fx(() -> Window.getWindows().stream().filter(window -> window instanceof Stage stage
                            && Texts.text("legacy.java.4cf2ff842a0cfa66").equals(stage.getTitle()))
                    .map(window -> (DialogPane) window.getScene().getRoot()).findFirst().orElseThrow());
            fx(() -> {
                RevealablePasswordEditor editor = pane.lookupAll(".revealable-password-editor").stream()
                        .map(RevealablePasswordEditor.class::cast).filter(value -> !value.isDisabled()).findFirst().orElseThrow();
                editor.setText("fixture-replacement");
                ButtonType save = pane.getButtonTypes().stream()
                        .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
                ((Button) pane.lookupButton(save)).fire();
                assertTrue(pane.getScene().getWindow().isShowing());
                assertTrue(pane.lookupButton(save).isDisabled());
                assertFalse(open.isDone());
                return null;
            });
            ResolvedCredential loaded = secret();
            authorized.complete(loaded);
            CredentialEditorDialog.Request result = open.get(5, TimeUnit.SECONDS).orElseThrow();
            try (CredentialDraft replacement = result.replacement().orElseThrow()) {
                char[] value = replacement.copySecret();
                try { assertArrayEquals("fixture-replacement".toCharArray(), value); }
                finally { Arrays.fill(value, '\0'); }
            }
            assertThrows(IllegalStateException.class, () -> loaded.copySecret());
        } finally { fx(() -> { owner.close(); return null; }); }
    }

    @Test void hidingSavedPasswordClearsPreviewAndTypingStillUsesTheNormalEye() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        RevealablePasswordEditor editor = fx(RevealablePasswordEditor::new);
        Stage stage = fx(() -> {
            editor.setSavedSecretLoader(() -> {
                reads.incrementAndGet();
                return CompletableFuture.completedFuture(secret());
            }, error -> fail("No failure expected"));
            Stage shown = new Stage();
            shown.setScene(new Scene(new StackPane(editor), 400, 100));
            shown.show();
            eye(editor).fire();
            return shown;
        });
        try {
            fx(() -> {
                assertTrue(editor.isRevealed());
                eye(editor).fire();
                assertFalse(editor.isRevealed());
                assertTrue(editor.getChildren().stream().filter(TextField.class::isInstance)
                        .map(TextField.class::cast).allMatch(field -> field.getText().isEmpty()));
                editor.setText("fixture-replacement");
                eye(editor).fire();
                assertTrue(editor.isRevealed());
                assertEquals("fixture-replacement", visibleText(editor).getText());
                assertTrue(visibleText(editor).isEditable());
                assertEquals("fixture-replacement", editor.getText());
                assertEquals(1, reads.get());
                return null;
            });
        } finally { fx(() -> { editor.clear(); stage.close(); return null; }); }
    }

    @Test void latePasswordReadIsClearedAfterTheEditorCloses() throws Exception {
        CompletableFuture<ResolvedCredential> read = new CompletableFuture<>();
        RevealablePasswordEditor editor = fx(RevealablePasswordEditor::new);
        Stage stage = fx(() -> {
            editor.setSavedSecretLoader(() -> read, error -> fail("No failure expected"));
            Stage shown = new Stage();
            shown.setScene(new Scene(new StackPane(editor), 400, 100));
            shown.show();
            eye(editor).fire();
            editor.clear();
            shown.close();
            return shown;
        });
        ResolvedCredential loaded = secret();
        read.complete(loaded);
        fx(() -> {
            assertFalse(stage.isShowing());
            assertFalse(editor.isRevealed());
            assertEquals("", editor.getText());
            assertThrows(IllegalStateException.class, () -> loaded.copySecret());
            return null;
        });
    }

    @Test void lockedVaultWaitsForSuccessfulMasterPasswordVerification() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        CompletableFuture<Boolean> verified = new CompletableFuture<>();
        CompletableFuture<ResolvedCredential> result = SavedCredentialAccess.read(() -> reads.incrementAndGet() == 1
                        ? CompletableFuture.failedFuture(new VaultException(VaultFailureType.LOCKED, "fixture"))
                        : CompletableFuture.completedFuture(secret()),
                () -> { assertTrue(Platform.isFxApplicationThread()); return verified; }).toCompletableFuture();
        fx(() -> null);
        assertFalse(result.isDone());
        assertEquals(1, reads.get());
        verified.complete(true);
        try (ResolvedCredential loaded = result.get(5, TimeUnit.SECONDS)) {
            assertEquals(2, reads.get());
            char[] value = loaded.copySecret();
            try { assertArrayEquals("fixture-password".toCharArray(), value); }
            finally { Arrays.fill(value, '\0'); }
        }
    }

    @Test void cancelledOrIncorrectMasterPasswordNeverReadsTheSecretAgain() throws Exception {
        for (boolean cancel : List.of(false, true)) {
            AtomicInteger reads = new AtomicInteger();
            CompletableFuture<ResolvedCredential> result = SavedCredentialAccess.read(() -> {
                reads.incrementAndGet();
                return CompletableFuture.failedFuture(new VaultException(VaultFailureType.LOCKED, "fixture"));
            }, () -> cancel ? CompletableFuture.completedFuture(false)
                    : CompletableFuture.failedFuture(new VaultException(
                            VaultFailureType.INVALID_PASSWORD_OR_CORRUPTED, "fixture"))).toCompletableFuture();
            fx(() -> null);
            assertTrue(result.isCompletedExceptionally());
            assertEquals(1, reads.get());
        }
    }

    @Test void unlockedVaultDoesNotAskForMasterPasswordAgain() throws Exception {
        try (ResolvedCredential loaded = SavedCredentialAccess.read(() -> CompletableFuture.completedFuture(secret()),
                () -> { throw new AssertionError("Already unlocked"); }).toCompletableFuture().get(5, TimeUnit.SECONDS)) {
            assertEquals(AuthenticationType.PASSWORD, loaded.authenticationType());
        }
    }

    private static ResolvedCredential secret() {
        char[] value = "fixture-password".toCharArray();
        try { return new ResolvedCredential(AuthenticationType.PASSWORD, value, null); }
        finally { Arrays.fill(value, '\0'); }
    }
    private static Button eye(RevealablePasswordEditor editor) {
        return (Button) editor.lookup(".vault-password-action-button");
    }
    private static TextField visibleText(RevealablePasswordEditor editor) {
        return editor.getChildren().stream().filter(node -> node instanceof TextField field && field.isVisible())
                .map(TextField.class::cast).findFirst().orElseThrow();
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
