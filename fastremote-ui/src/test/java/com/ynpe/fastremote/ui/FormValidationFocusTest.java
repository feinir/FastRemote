// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Window;
import com.ynpe.fastremote.domain.snippet.SnippetTemplateEngine;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import com.ynpe.fastremote.i18n.Texts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FormValidationFocusTest {
    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }
    @Test void hostNameAndOutOfRangePortFocusTheFailingField() throws Exception {
        exercise(() -> HostEditorDialog.show(null, List.of(), List.of(), List.of(), VaultStatus.LOCKED,
                Optional.empty(), ignored -> CompletableFuture.completedFuture(false)), pane -> {
            TextField name = input(pane, "legacy.java.270c4bee5ae7c922");
            name.setText("   ");
            save(pane); assertFocused(pane, name);
            name.setText("fixture");
            ComboBox<?> source = pane.lookupAll(".combo-box").stream().filter(n -> n instanceof ComboBox<?> c
                    && c.getValue() instanceof HostEditorDialog.CredentialSource).map(n -> (ComboBox<?>) n).findFirst().orElseThrow();
            source.getSelectionModel().select(1);
            TextField port = pane.lookupAll(".text-field").stream().filter(TextField.class::isInstance)
                    .map(TextField.class::cast).filter(f -> f.getText().equals("22")).findFirst().orElseThrow();
            for (var node : pane.lookupAll(".text-field")) {
                if (node instanceof TextField field && field != name && field != port && field.isEditable()
                        && !field.isDisabled() && !(field instanceof PasswordField)) field.setText("fixture");
            }
            port.setText("70000");
            save(pane); assertFocused(pane, port);
        });
    }
    @Test void credentialInvalidNameFocusesEndWithoutSelection() throws Exception {
        exercise(() -> CredentialEditorDialog.showCreate(null), pane -> {
            TextField name = input(pane, "legacy.java.e436877a7956dd3a");
            name.setText("   ");
            save(pane); assertFocused(pane, name);
        });
    }
    @Test void commandNameThenContentReceiveValidationFocus() throws Exception {
        exercise(() -> SnippetDialogs.edit(null, null, Optional.empty(), emptySnapshot(), new SnippetTemplateEngine(), ignored -> 0), pane -> {
            TextField name = input(pane, "legacy.java.838910be8554fb28");
            name.setText("   ");
            save(pane); assertFocused(pane, name);
            name.setText("fixture");
            TextArea content = (TextArea) pane.lookup(".text-area");
            save(pane); assertFocused(pane, content);
        });
    }
    @Test void invalidVariableIdentifierReceivesFocusWithoutSelection() throws Exception {
        exercise(() -> VariableDialogs.edit(null, null, Optional.empty(), emptySnapshot(), ignored -> 0), pane -> {
            input(pane, "legacy.java.8dc77eb54129c817").setText("fixture");
            TextField identifier = input(pane, "legacy.java.34afaebdb3871ed3");
            identifier.setText("bad-name");
            save(pane); assertFocused(pane, identifier);
        });
    }
    private static SnippetSnapshot emptySnapshot() { return new SnippetSnapshot(List.of(), List.of(), List.of()); }
    private static TextField input(DialogPane pane, String key) {
        return pane.lookupAll(".text-field").stream().filter(TextField.class::isInstance)
                .map(TextField.class::cast).filter(f -> Texts.text(key).equals(f.getPromptText())).findFirst().orElseThrow();
    }
    private static void save(DialogPane pane) {
        ButtonType type = pane.getButtonTypes().stream().filter(t -> t.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
        ((Button) pane.lookupButton(type)).fire();
        assertTrue(pane.getScene().getWindow().isShowing());
    }
    private static void assertFocused(DialogPane pane, TextInputControl input) {
        assertSame(input, pane.getScene().getFocusOwner());
        assertEquals(input.getLength(), input.getCaretPosition());
        assertEquals(input.getLength(), input.getAnchor());
        assertEquals("", input.getSelectedText());
    }
    private static void exercise(Callable<?> open, Consumer<DialogPane> checks) throws Exception {
        FutureTask<?> task = new FutureTask<>(open);
        Platform.runLater(task);
        DialogPane pane = fx(() -> Window.getWindows().stream()
                .filter(w -> w.getScene() != null && w.getScene().getRoot() instanceof DialogPane)
                .map(w -> (DialogPane) w.getScene().getRoot()).findFirst().orElseThrow());
        try { fx(() -> { checks.accept(pane); return null; }); }
        finally {
            fx(() -> { ((Button) pane.lookupButton(ButtonType.CANCEL)).fire(); return null; });
            task.get(5, TimeUnit.SECONDS);
        }
    }
    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(5, TimeUnit.SECONDS);
    }
}
