// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.vault.VaultStatus;
import com.ynpe.fastremote.i18n.Texts;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VaultDialogTransitionTest {
    @Test
    void hostSaveUnlockKeepsButtonsVisibleAfterEmptyInputAndWrongPasswordRetry() throws Exception {
        exerciseHostSaveFeedback(false);
    }

    @Test
    void hostSaveCreationKeepsButtonsVisibleAfterValidationAndAsyncFailure() throws Exception {
        exerciseHostSaveFeedback(true);
    }

    private void exerciseHostSaveFeedback(boolean create) throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var attempt = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>>();
        HostCatalogWorkflow workflow = HostCatalogWorkflow.class.cast(Proxy.newProxyInstance(
                HostCatalogWorkflow.class.getClassLoader(), new Class<?>[] {HostCatalogWorkflow.class},
                (proxy, method, args) -> {
                    if (method.getName().equals(create ? "createVault" : "unlockVault")) {
                        calls.incrementAndGet();
                        var pending = new CompletableFuture<Void>();
                        attempt.set(pending);
                        return pending;
                    }
                    throw new AssertionError("Unexpected call: " + method.getName());
                }));
        var result = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletionStage<Boolean>>();
        Stage owner = fx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 800, 600));
            stage.show();
            MainWindowController controller = new MainWindowController(unused(ConnectionWorkflow.class),
                    workflow, unused(SettingsWorkflow.class), unused(SnippetWorkflow.class));
            var method = MainWindowController.class.getDeclaredMethod(create
                    ? "requestVaultCreationForHostSave" : "requestLockedVaultForHostSave", Window.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            var opened = (java.util.concurrent.CompletionStage<Boolean>) method.invoke(controller, stage);
            result.set(opened);
            return stage;
        });
        DialogPane pane = fx(() -> pane(Texts.text(create
                ? "legacy.java.0a72ed1550a7591d" : "legacy.java.86e913c3ed764b1b")));
        try {
            double width = fx(() -> pane.getScene().getWidth());
            fx(() -> { hostSaveAction(pane).fire(); return null; });
            awaitLayout(pane);
            fx(() -> {
                assertEquals(0, calls.get());
                assertHostSaveButtonsInsideWindow(pane);
                return null;
            });
            for (int retry = 1; retry <= 2; retry++) {
                fx(() -> {
                    var content = (javafx.scene.layout.VBox) pane.getContent();
                    content.getChildren().stream().filter(RevealablePasswordEditor.class::isInstance)
                            .map(RevealablePasswordEditor.class::cast)
                            .forEach(editor -> editor.setText("incorrect-master"));
                    hostSaveAction(pane).fire();
                    assertTrue(hostSaveAction(pane).isDisabled());
                    return null;
                });
                awaitLayout(pane);
                // Complete after the progress label is already laid out. This is a text-only
                // change, so observing managed/visible alone would miss the longer error.
                attempt.get().completeExceptionally(new com.ynpe.fastremote.domain.vault.VaultException(
                        com.ynpe.fastremote.domain.vault.VaultFailureType.INVALID_PASSWORD_OR_CORRUPTED,
                        "The master password is incorrect or the global configuration is damaged. ".repeat(4)));
                fx(() -> null);
                awaitLayout(pane);
                int expectedCalls = retry;
                fx(() -> {
                    assertEquals(expectedCalls, calls.get());
                    assertHostSaveButtonsInsideWindow(pane);
                    assertEquals(width, pane.getScene().getWidth(), 1.0);
                    assertFalse(hostSaveAction(pane).isDisabled());
                    assertFalse(result.get().toCompletableFuture().isDone());
                    if (!create) {
                        var error = (javafx.scene.control.Label) pane.lookup(".vault-password-validation-error");
                        assertEquals(Texts.text("legacy.java.6a401c6c1416de43"), error.getText());
                    }
                    return null;
                });
            }
            fx(() -> { ((Button) pane.lookupButton(ButtonType.CANCEL)).fire(); return null; });
            assertFalse(result.get().toCompletableFuture().get(5, TimeUnit.SECONDS));
        } finally {
            fx(() -> {
                if (pane.getScene() != null && pane.getScene().getWindow().isShowing()) {
                    ((Button) pane.lookupButton(ButtonType.CANCEL)).fire();
                }
                owner.close();
                return null;
            });
        }
    }

    private static Button hostSaveAction(DialogPane pane) {
        return (Button) pane.lookupButton(pane.getButtonTypes().stream()
                .filter(type -> type != ButtonType.CANCEL).findFirst().orElseThrow());
    }

    private static void assertHostSaveButtonsInsideWindow(DialogPane pane) {
        assertTrue(pane.getScene().getWindow().isShowing());
        for (ButtonType type : pane.getButtonTypes()) {
            var button = pane.lookupButton(type);
            var bounds = button.localToScene(button.getBoundsInLocal());
            assertTrue(button.isVisible());
            assertTrue(bounds.getMinY() >= 0);
            assertTrue(bounds.getMaxY() <= pane.getScene().getHeight() + 1,
                    "button bar must fit inside the native client area");
        }
    }

    private static void awaitLayout(DialogPane pane) throws Exception {
        CountDownLatch laidOut = new CountDownLatch(1);
        fx(() -> {
            Runnable[] observer = new Runnable[1];
            observer[0] = () -> {
                pane.getScene().removePostLayoutPulseListener(observer[0]);
                laidOut.countDown();
            };
            pane.getScene().addPostLayoutPulseListener(observer[0]);
            pane.requestLayout();
            Platform.requestNextPulse();
            return null;
        });
        assertTrue(laidOut.await(5, TimeUnit.SECONDS));
    }

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void changePasswordFromMainWindowKeepsQueuedFocusAndRenderingAlive() throws Exception {
        exerciseTransition(false, "legacy.java.41c964f19c974372", 3);
    }

    @Test
    void changePasswordFromSettingsKeepsTheSettingsOwnerAndRenders() throws Exception {
        exerciseTransition(true, "legacy.java.41c964f19c974372", 3);
    }

    @Test
    void clearRememberedUnlockAlsoWaitsForTheManagerLoopToFinish() throws Exception {
        exerciseTransition(false, "legacy.java.084670b30fa83d6b", 1);
    }

    @Test
    void invalidPasswordChangesStayOpenAndShowErrors() throws Exception {
        exerciseChangeValidation(false);
    }

    @Test
    void rememberedPasswordChangeHidesOldPasswordAndUsesSavedMaterial() throws Exception {
        exerciseChangeValidation(true);
    }

    private void exerciseChangeValidation(boolean saved) throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        HostCatalogWorkflow workflow = HostCatalogWorkflow.class.cast(Proxy.newProxyInstance(
                HostCatalogWorkflow.class.getClassLoader(), new Class<?>[] {HostCatalogWorkflow.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("rememberedVaultUnlockConfigured")) {
                        return CompletableFuture.completedFuture(saved);
                    }
                    if (method.getName().equals("changeVaultMasterPassword")) {
                        calls.incrementAndGet();
                        assertEquals(saved ? 0 : 9, ((char[]) args[0]).length);
                        return CompletableFuture.failedFuture(new com.ynpe.fastremote.domain.vault.VaultException(
                                com.ynpe.fastremote.domain.vault.VaultFailureType.INVALID_PASSWORD_OR_CORRUPTED,
                                "fixture invalid password"));
                    }
                    throw new AssertionError("Unexpected call: " + method.getName());
                }));
        Stage main = fx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 640, 480));
            stage.show();
            MainWindowController controller = new MainWindowController(unused(ConnectionWorkflow.class),
                    workflow, unused(SettingsWorkflow.class), unused(SnippetWorkflow.class));
            var method = MainWindowController.class.getDeclaredMethod("showChangeMasterPasswordDialog", Window.class);
            method.setAccessible(true);
            method.invoke(controller, stage);
            return stage;
        });
        try {
            DialogPane pane = fx(() -> pane(Texts.text("legacy.java.41c964f19c974372")));
            fx(() -> {
                var fields = ((javafx.scene.layout.VBox) pane.getContent()).getChildren().stream()
                        .filter(PasswordField.class::isInstance).map(PasswordField.class::cast).toList();
                assertEquals(!saved, fields.get(0).isManaged());
                Button ok = (Button) pane.lookupButton(ButtonType.OK);
                ok.fire();
                assertTrue(pane.getScene().getWindow().isShowing());
                assertTrue(pane.lookup(".vault-password-validation-error").isVisible());
                assertEquals(0, calls.get());
                fields.get(0).setText("incorrect");
                fields.get(1).setText("short");
                fields.get(2).setText("short");
                ok.fire();
                assertEquals(0, calls.get());
                fields.get(1).setText("new-master");
                fields.get(2).setText("different-master");
                ok.fire();
                assertEquals(0, calls.get());
                fields.get(2).setText("new-master");
                ok.fire();
                return null;
            });
            fx(() -> {
                assertEquals(1, calls.get());
                assertTrue(pane.getScene().getWindow().isShowing());
                javafx.scene.control.Label error = (javafx.scene.control.Label) pane.lookup(".vault-password-validation-error");
                assertTrue(error.isVisible());
                assertEquals(Texts.text("legacy.java.6a401c6c1416de43"), error.getText());
                assertFalse(pane.lookupButton(ButtonType.OK).isDisabled());
                ((Button) pane.lookupButton(ButtonType.CANCEL)).fire();
                return null;
            });
        } finally {
            fx(() -> { main.close(); return null; });
        }
    }

    @Test
    void exportWithSavedMaterialHidesPasswordAndValidatesSavedKey() throws Exception {
        exerciseExportValidation(true);
    }

    @Test
    void exportWithoutSavedMaterialRequiresPasswordAndKeepsErrorsVisible() throws Exception {
        exerciseExportValidation(false);
    }

    private void exerciseExportValidation(boolean saved) throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        HostCatalogWorkflow workflow = HostCatalogWorkflow.class.cast(Proxy.newProxyInstance(
                HostCatalogWorkflow.class.getClassLoader(), new Class<?>[] {HostCatalogWorkflow.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("rememberedVaultUnlockConfigured")) return CompletableFuture.completedFuture(saved);
                    if (method.getName().equals("verifyVaultMasterPassword")) {
                        assertEquals(saved ? 0 : 12, ((char[]) args[0]).length);
                        calls.incrementAndGet();
                        return CompletableFuture.failedFuture(new com.ynpe.fastremote.domain.vault.VaultException(
                                com.ynpe.fastremote.domain.vault.VaultFailureType.INVALID_PASSWORD_OR_CORRUPTED, "fixture"));
                    }
                    throw new AssertionError("Unexpected call: " + method.getName());
                }));
        java.util.concurrent.atomic.AtomicReference<MainWindowController> controller = new java.util.concurrent.atomic.AtomicReference<>();
        Stage main = fx(() -> {
            Stage stage = new Stage();
            var root = new javafx.scene.layout.BorderPane();
            stage.setScene(new Scene(root, 640, 480));
            stage.show();
            controller.set(new MainWindowController(unused(ConnectionWorkflow.class), workflow,
                    unused(SettingsWorkflow.class), unused(SnippetWorkflow.class)));
            var field = MainWindowController.class.getDeclaredField("rootPane");
            field.setAccessible(true);
            field.set(controller.get(), root);
            return stage;
        });
        FutureTask<Void> open = new FutureTask<>(() -> {
            var method = MainWindowController.class.getDeclaredMethod("exportCatalog");
            method.setAccessible(true);
            method.invoke(controller.get());
            return null;
        });
        try {
            Platform.runLater(open);
            DialogPane pane = fx(() -> pane(Texts.text("config.export.title")));
            fx(() -> {
                var content = (javafx.scene.layout.VBox) pane.getContent();
                RevealablePasswordEditor editor = content.getChildren().stream()
                        .filter(RevealablePasswordEditor.class::isInstance).map(RevealablePasswordEditor.class::cast).findFirst().orElseThrow();
                assertEquals(!saved, editor.isManaged());
                content.getChildren().stream().filter(node -> node instanceof javafx.scene.control.CheckBox check
                                && Texts.text("legacy.java.1ea5ac836eb68bb1").equals(check.getText()))
                        .map(javafx.scene.control.CheckBox.class::cast).findFirst().orElseThrow().setSelected(true);
                Button ok = (Button) pane.lookupButton(ButtonType.OK);
                if (!saved) {
                    ok.fire();
                    assertEquals(0, calls.get());
                    assertTrue(pane.getScene().getWindow().isShowing());
                    editor.setText("wrong-master");
                }
                ok.fire();
                return null;
            });
            fx(() -> {
                assertEquals(1, calls.get());
                assertTrue(pane.getScene().getWindow().isShowing());
                assertTrue(pane.lookup(".vault-password-validation-error").isVisible());
                assertFalse(pane.lookupButton(ButtonType.OK).isDisabled());
                ((Button) pane.lookupButton(ButtonType.CANCEL)).fire();
                return null;
            });
            open.get(5, TimeUnit.SECONDS);
        } finally {
            fx(() -> { main.close(); return null; });
        }
    }

    @Test
    void englishAndGermanPasswordFormsWrapInsteadOfGrowingRightward() throws Exception {
        var previous = Texts.localizer();
        try {
            for (var language : List.of(com.ynpe.fastremote.domain.settings.InterfaceLanguage.ENGLISH,
                    com.ynpe.fastremote.domain.settings.InterfaceLanguage.GERMAN)) {
                Stage main = fx(() -> {
                    Texts.configure(com.ynpe.fastremote.i18n.Localizer.forLanguage(language));
                    Stage stage = new Stage();
                    stage.setScene(new Scene(new StackPane(), 800, 650));
                    stage.show();
                    HostCatalogWorkflow workflow = HostCatalogWorkflow.class.cast(Proxy.newProxyInstance(
                            HostCatalogWorkflow.class.getClassLoader(), new Class<?>[] {HostCatalogWorkflow.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("rememberedVaultUnlockConfigured")) return CompletableFuture.completedFuture(false);
                                throw new AssertionError("Unexpected call: " + method.getName());
                            }));
                    var controller = new MainWindowController(unused(ConnectionWorkflow.class), workflow,
                            unused(SettingsWorkflow.class), unused(SnippetWorkflow.class));
                    var method = MainWindowController.class.getDeclaredMethod("showChangeMasterPasswordDialog", Window.class);
                    method.setAccessible(true);
                    method.invoke(controller, stage);
                    return stage;
                });
                try {
                    awaitNativeLayout();
                    DialogPane pane = fx(() -> pane(Texts.text("legacy.java.41c964f19c974372")));
                    double width = fx(() -> {
                        var content = (javafx.scene.layout.VBox) pane.getContent();
                        var remember = content.getChildren().stream().filter(javafx.scene.control.CheckBox.class::isInstance)
                                .map(javafx.scene.control.CheckBox.class::cast).findFirst().orElseThrow();
                        assertTrue(remember.isWrapText());
                        assertTrue(content.getWidth() <= 500.0, () -> "content width=" + content.getWidth());
                        assertTrue(pane.getScene().getWindow().getWidth() < 550.0, () -> "window width=" + pane.getScene().getWindow().getWidth());
                        double initialWidth = pane.getScene().getWindow().getWidth();
                        ((Button) pane.lookupButton(ButtonType.OK)).fire();
                        var error = (javafx.scene.control.Label) pane.lookup(".vault-password-validation-error");
                        error.setText(error.getText().repeat(12));
                        pane.getScene().getWindow().sizeToScene();
                        return initialWidth;
                    });
                    awaitNativeLayout();
                    fx(() -> {
                        assertEquals(width, pane.getScene().getWindow().getWidth(), 2.0);
                        assertTrue(pane.lookup(".vault-password-validation-error").isVisible());
                        ((Button) pane.lookupButton(ButtonType.CANCEL)).fire();
                        return null;
                    });
                } finally { fx(() -> { main.close(); return null; }); }
            }
        } finally { fx(() -> { Texts.configure(previous); return null; }); }
    }

    private static void awaitNativeLayout() throws Exception {
        CountDownLatch laidOut = new CountDownLatch(1);
        fx(() -> {
            var wait = new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));
            wait.setOnFinished(ignored -> laidOut.countDown());
            wait.play();
            return null;
        });
        assertTrue(laidOut.await(5, TimeUnit.SECONDS));
    }

    private void exerciseTransition(boolean fromSettings, String actionKey, int fieldCount) throws Exception {
        Stage main = fx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 640, 480));
            stage.show();
            return stage;
        });
        Dialog<Void> settings = fx(() -> {
            if (!fromSettings) return null;
            Dialog<Void> dialog = new Dialog<>();
            dialog.initOwner(main);
            dialog.setTitle("Settings fixture");
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.show();
            return dialog;
        });
        Window owner = fx(() -> settings == null ? main : settings.getDialogPane().getScene().getWindow());
        MainWindowController controller = fx(() -> new MainWindowController(unused(ConnectionWorkflow.class),
                rememberedVault(), unused(SettingsWorkflow.class), unused(SnippetWorkflow.class)));
        FutureTask<Void> openManager = new FutureTask<>(() -> {
            var method = MainWindowController.class.getDeclaredMethod("showVaultManager", Window.class, VaultStatus.class);
            method.setAccessible(true);
            method.invoke(controller, owner, VaultStatus.UNLOCKED);
            return null;
        });
        try {
            Platform.runLater(openManager);
            DialogPane manager = fx(() -> pane(MainWindowController.masterPasswordManagerTitle()));
            Window managerWindow = fx(() -> manager.getScene().getWindow());
            // Do not wait for fire(): the old bug entered the next modal loop inside this callback.
            FutureTask<Void> click = new FutureTask<>(() -> {
                button(manager, Texts.text(actionKey)).fire();
                return null;
            });
            Platform.runLater(click);
            DialogPane successor = fx(() -> pane(Texts.text(actionKey)));
            assertTrue(click.isDone(), "the manager button callback must return before its successor opens");
            click.get(5, TimeUnit.SECONDS);
            CountDownLatch painted = new CountDownLatch(1);
            fx(() -> {
                Stage window = (Stage) successor.getScene().getWindow();
                assertSame(owner, window.getOwner());
                assertFalse(managerWindow.isShowing());
                var fields = successor.lookupAll(".password-field").stream()
                        .map(PasswordField.class::cast).toList();
                assertEquals(fieldCount, fields.size());
                PasswordField focused = (PasswordField) successor.getScene().getFocusOwner();
                assertTrue(fields.contains(focused));
                assertTrue(focused.getWidth() > 0);
                Runnable[] observer = new Runnable[1];
                observer[0] = () -> {
                    successor.getScene().removePostLayoutPulseListener(observer[0]);
                    painted.countDown();
                };
                successor.getScene().addPostLayoutPulseListener(observer[0]);
                focused.setText("non-secret-fixture");
                Platform.requestNextPulse();
                return null;
            });
            assertTrue(painted.await(5, TimeUnit.SECONDS), "the successor must receive layout/render pulses");
            fx(() -> { ((Button) successor.lookupButton(ButtonType.CANCEL)).fire(); return null; });
            openManager.get(5, TimeUnit.SECONDS);
        } finally {
            fx(() -> {
                List<Window> windows = List.copyOf(Window.getWindows());
                for (Window window : windows.reversed()) {
                    if (window instanceof Stage stage && (stage.getOwner() == owner || window == owner)) stage.close();
                }
                main.close();
                return null;
            });
        }
    }

    private static DialogPane pane(String title) {
        return Window.getWindows().stream().filter(window -> window instanceof Stage stage
                        && title.equals(stage.getTitle()) && stage.isShowing())
                .map(window -> (DialogPane) window.getScene().getRoot()).findFirst().orElseThrow();
    }

    private static Button button(DialogPane pane, String text) {
        return pane.lookupAll(".button").stream().filter(node -> node instanceof Button button
                && text.equals(button.getText())).map(Button.class::cast).findFirst().orElseThrow();
    }

    private static HostCatalogWorkflow rememberedVault() {
        return HostCatalogWorkflow.class.cast(Proxy.newProxyInstance(HostCatalogWorkflow.class.getClassLoader(),
                new Class<?>[] {HostCatalogWorkflow.class}, (proxy, method, args) -> {
                    if (method.getName().equals("rememberedVaultUnlockConfigured")) {
                        return CompletableFuture.completedFuture(true);
                    }
                    throw new AssertionError("Unexpected vault call: " + method.getName());
                }));
    }

    private static <T> T unused(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> { throw new AssertionError("Unexpected call: " + method.getName()); }));
    }

    private static <T> T fx(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(5, TimeUnit.SECONDS);
    }
}
