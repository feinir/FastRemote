// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/** Password input with the same explicit reveal interaction used by the vault dialog. */
public final class RevealablePasswordEditor extends StackPane {
    private static final String EYE_PATH =
            "M1,6 C2.8,2.7 5.2,1 8,1 C10.8,1 13.2,2.7 15,6 "
                    + "C13.2,9.3 10.8,11 8,11 C5.2,11 2.8,9.3 1,6 Z "
                    + "M8,3.5 A2.5,2.5 0 1,0 8,8.5 A2.5,2.5 0 1,0 8,3.5 Z";

    private final PasswordField password = new PasswordField();
    private final TextField visiblePassword = new TextField();
    private final TextField savedPreview = new TextField();
    private final Button visibilityButton = new Button();
    private boolean revealed;
    private boolean previewingSaved;
    private int revealGeneration;
    private Supplier<CompletionStage<ResolvedCredential>> savedSecretLoader;
    private Consumer<Throwable> revealFailure;

    public RevealablePasswordEditor() {
        this(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7be1419614f3508d"));
    }

    RevealablePasswordEditor(String accessibleText) {
        password.setAccessibleText(accessibleText);
        visiblePassword.setAccessibleText(accessibleText + com.ynpe.fastremote.i18n.Texts.text("legacy.java.0885285e8d773fa5"));
        password.getStyleClass().add("revealable-password-input");
        visiblePassword.getStyleClass().addAll("revealable-password-input", "vault-visible-password-input");
        visiblePassword.textProperty().bindBidirectional(password.textProperty());
        visiblePassword.setVisible(false);
        visiblePassword.setManaged(false);
        savedPreview.setAccessibleText(visiblePassword.getAccessibleText());
        savedPreview.getStyleClass().addAll("revealable-password-input", "vault-visible-password-input");
        savedPreview.setEditable(false);
        savedPreview.setVisible(false);
        savedPreview.setManaged(false);
        password.textProperty().addListener((ignored, previous, current) -> {
            revealGeneration++;
            visibilityButton.setDisable(false);
            if (previewingSaved) setRevealed(false);
        });

        SVGPath eye = new SVGPath();
        eye.setContent(EYE_PATH);
        eye.getStyleClass().add("vault-password-action-icon");
        visibilityButton.setGraphic(eye);
        visibilityButton.getStyleClass().add("vault-password-action-button");
        visibilityButton.setOnAction(event -> {
            if (!revealed && getText().isEmpty() && savedSecretLoader != null) loadSavedSecret();
            else setRevealed(!revealed);
        });
        updateVisibilityAction();

        HBox actions = new HBox(visibilityButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPickOnBounds(false);
        actions.getStyleClass().add("vault-password-actions");
        getStyleClass().add("revealable-password-editor");
        getChildren().setAll(password, visiblePassword, savedPreview, actions);
        StackPane.setAlignment(actions, Pos.CENTER_RIGHT);
    }

    public void setPromptText(String promptText) {
        password.setPromptText(promptText);
        visiblePassword.setPromptText(promptText);
    }

    public String getPromptText() {
        return password.getPromptText();
    }

    public String getText() {
        return password.getText();
    }

    public void setText(String text) {
        password.setText(text == null ? "" : text);
    }

    StringProperty textProperty() {
        return password.textProperty();
    }

    void focusInput() {
        TextField active = previewingSaved ? savedPreview : revealed ? visiblePassword : password;
        active.requestFocus();
        active.positionCaret(active.getText().length());
    }

    void clear() {
        revealGeneration++;
        visibilityButton.setDisable(false);
        password.clear();
        setRevealed(false);
    }

    void setRevealed(boolean value) {
        revealed = value;
        if (!value) {
            previewingSaved = false;
            savedPreview.clear();
        }
        savedPreview.setVisible(value && previewingSaved);
        savedPreview.setManaged(value && previewingSaved);
        visiblePassword.setVisible(value && !previewingSaved);
        visiblePassword.setManaged(value && !previewingSaved);
        password.setVisible(!value);
        password.setManaged(!value);
        TextField active = previewingSaved ? savedPreview : value ? visiblePassword : password;
        active.requestFocus();
        active.positionCaret(active.getText().length());
        updateVisibilityAction();
    }

    void setInputEditable(boolean editable) {
        password.setEditable(editable);
        visiblePassword.setEditable(editable);
    }

    void setSavedSecretLoader(Supplier<CompletionStage<ResolvedCredential>> loader, Consumer<Throwable> onFailure) {
        revealGeneration++;
        visibilityButton.setDisable(false);
        if (previewingSaved) setRevealed(false);
        savedSecretLoader = loader;
        revealFailure = onFailure;
    }

    private void loadSavedSecret() {
        int generation = ++revealGeneration;
        visibilityButton.setDisable(true);
        CompletionStage<ResolvedCredential> read;
        try {
            read = savedSecretLoader.get();
        } catch (RuntimeException failure) {
            visibilityButton.setDisable(false);
            revealFailure.accept(failure);
            return;
        }
        read.whenComplete((credential, failure) -> Platform.runLater(() -> {
            try (credential) {
                if (generation != revealGeneration) return;
                visibilityButton.setDisable(false);
                if (getScene() == null || getScene().getWindow() == null
                        || !getScene().getWindow().isShowing()) return;
                if (failure != null) {
                    Throwable cause = failure;
                    while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    if (!(cause instanceof CancellationException)) revealFailure.accept(cause);
                    return;
                }
                char[] secret = credential.copySecret();
                try {
                    savedPreview.setText(new String(secret));
                    previewingSaved = true;
                    setRevealed(true);
                } finally {
                    Arrays.fill(secret, '\0');
                }
            }
        }));
    }

    boolean isRevealed() {
        return revealed;
    }

    private void updateVisibilityAction() {
        String action = revealed ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.dd1d07bd9ceb7f82") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.ffe21a671190323e");
        visibilityButton.setAccessibleText(action);
        visibilityButton.setTooltip(new Tooltip(action));
    }
}
