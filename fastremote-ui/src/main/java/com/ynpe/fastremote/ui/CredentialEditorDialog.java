// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

final class CredentialEditorDialog {
    static final double FORM_CONTROL_HEIGHT = 34.0;
    static final double FORM_ROW_HEIGHT = 44.0;
    private static final double LABEL_HEIGHT = FormLabel.COMPACT_TWO_LINE_HEIGHT;
    private static final double FIELD_MIN_WIDTH = 300.0;
    static final double LABEL_WIDTH = FormLabel.ENGLISH_BASELINE_WIDTH;
    private static final double FIELD_LEFT = LABEL_WIDTH + 15.0;
    static final double DIALOG_WIDTH = 600.0;
    static final double DIALOG_HEIGHT = 404.0;

    private CredentialEditorDialog() {}

    static Optional<Request> showCreate(Window owner) {
        return show(owner, Optional.empty(), null);
    }

    static Optional<Request> showEdit(Window owner, GlobalCredential credential) {
        return show(owner, Optional.of(Objects.requireNonNull(credential, "credential")), null);
    }

    static Optional<Request> showEdit(Window owner, GlobalCredential credential, SavedCredentialReader reader) {
        return show(owner, Optional.of(Objects.requireNonNull(credential, "credential")), reader);
    }

    private static Optional<Request> show(Window owner, Optional<GlobalCredential> existing, SavedCredentialReader reader) {
        boolean editing = existing.isPresent();
        Dialog<Request> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(editing ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.4cf2ff842a0cfa66") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.9ef774b3220dc7d9"));
        dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4b8969c39b744766"));
        DialogSpacing.contentToButtonBar(dialog);
        ButtonType saveType = new ButtonType(editing ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.991bb7cfe5a81550") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.91ae1892b68dbf24"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, saveType);
        dialog.setResultConverter(DialogResultPolicy.explicitOnly());

        TextField name = new TextField();
        name.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e436877a7956dd3a"));
        ComboBox<AuthenticationType> type = authenticationTypeField();
        TextField username = new TextField();
        RevealablePasswordEditor password = new RevealablePasswordEditor(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1cb733485569462c"));
        password.setPromptText(editing ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.93e91a4b0a7d1920") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.66bb62ebb9b9da52"));
        TextField certificate = new TextField();
        certificate.setEditable(false);
        certificate.setPromptText(editing ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.fe094c368609d67f") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.720a21d86afc2182"));
        Button choose = FileSelectionButton.create(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.c11330b85234f9c0"));
        AnchorPane certificateRow = new AnchorPane(certificate, choose);
        certificateRow.setMinHeight(FORM_CONTROL_HEIGHT);
        certificateRow.setPrefHeight(FORM_CONTROL_HEIGHT);
        certificateRow.setMaxHeight(FORM_CONTROL_HEIGHT);
        AnchorPane.setLeftAnchor(certificate, 0.0);
        AnchorPane.setRightAnchor(certificate, FileSelectionButton.WIDTH + 6.0);
        AnchorPane.setTopAnchor(certificate, 0.0);
        AnchorPane.setBottomAnchor(certificate, 0.0);
        AnchorPane.setRightAnchor(choose, 0.0);
        AnchorPane.setTopAnchor(choose, 0.0);
        AnchorPane.setBottomAnchor(choose, 0.0);
        RevealablePasswordEditor passphrase = new RevealablePasswordEditor(com.ynpe.fastremote.i18n.Texts.text("legacy.java.13610905c385728f"));
        passphrase.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.29e50ea8dce06223"));
        Label validation = ConnectionFeedback.reservedLabel();

        existing.ifPresent(value -> {
            name.setText(value.label());
            username.setText(value.username());
            type.setValue(value.authenticationType());
        });

        AnchorPane form = new AnchorPane();
        double formHeight = formHeight(7);
        form.setMinHeight(formHeight);
        form.setPrefHeight(formHeight);
        form.setMaxHeight(formHeight);
        List<String> labels = formLabels();
        addRow(form, 0, label(labels.get(0)), name);
        addRow(form, 1, label(labels.get(1)), type);
        addRow(form, 2, label(labels.get(2)), username);
        addRow(form, 3, label(labels.get(3)), password);
        addRow(form, 4, label(labels.get(4)), certificateRow);
        addRow(form, 5, label(labels.get(5)), passphrase);
        validation.setPrefHeight(ConnectionFeedback.RESERVED_HEIGHT);
        validation.setMaxHeight(ConnectionFeedback.RESERVED_HEIGHT);
        AnchorPane.setLeftAnchor(validation, 0.0);
        AnchorPane.setRightAnchor(validation, 0.0);
        AnchorPane.setTopAnchor(validation,
                6 * FORM_ROW_HEIGHT + (FORM_ROW_HEIGHT - ConnectionFeedback.RESERVED_HEIGHT) / 2.0);
        form.getChildren().add(validation);
        dialog.getDialogPane().setContent(DialogSpacing.anchoredContent(form));
        configureDialogSizing(dialog);

        Runnable update = () -> {
            boolean certificateType = type.getValue() == AuthenticationType.PRIVATE_KEY;
            boolean sameType = editing && type.getValue() == existing.orElseThrow().authenticationType();
            password.setPromptText(com.ynpe.fastremote.i18n.Texts.text(sameType
                    ? "legacy.java.93e91a4b0a7d1920" : "legacy.java.66bb62ebb9b9da52"));
            certificate.setPromptText(com.ynpe.fastremote.i18n.Texts.text(sameType
                    ? "legacy.java.fe094c368609d67f" : "legacy.java.720a21d86afc2182"));
            password.setSavedSecretLoader(sameType && !certificateType && reader != null
                    ? () -> {
                        ConnectionFeedback.clear(validation);
                        return reader.read(dialog.getDialogPane().getScene().getWindow(), existing.orElseThrow().id());
                    } : null, failure -> ConnectionFeedback.show(validation,
                            com.ynpe.fastremote.i18n.Texts.text("legacy.java.fbdae73221a140d4"), ConnectionFeedback.Tone.ERROR));
            ConnectionFeedback.clear(validation);
            password.setDisable(certificateType);
            certificateRow.setDisable(!certificateType);
            passphrase.setDisable(!certificateType);
        };
        type.valueProperty().addListener((ignored, previous, selected) -> update.run());
        update.run();

        choose.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d154893b818a4031"));
            File selected = PlatformFileChooser.showOpenDialog(chooser, owner);
            if (selected != null) certificate.setText(selected.getAbsolutePath());
        });

        Button save = (Button) dialog.getDialogPane().lookupButton(saveType);
        boolean[] replacementAuthorized = {false};
        save.addEventFilter(ActionEvent.ACTION, event -> {
            char[] secret = new char[0];
            try {
                String credentialName = required(name, com.ynpe.fastremote.i18n.Texts.text("legacy.java.4a97bbb667f26932"));
                String user = required(username, com.ynpe.fastremote.i18n.Texts.text("legacy.java.c723b1fab58f5dbc"));
                FormValidation.require(name, credentialName.length() <= 255, "Credential label must contain 1 to 255 characters");
                FormValidation.require(username, user.length() <= 255, "Credential username must not exceed 255 characters");
                AuthenticationType authentication = type.getValue();
                boolean requiresReplacement = !editing
                        || authentication != existing.orElseThrow().authenticationType();
                Optional<CredentialDraft> replacement;
                if (authentication == AuthenticationType.PASSWORD) {
                    secret = password.getText().toCharArray();
                    if (requiresReplacement && secret.length == 0) {
                        password.focusInput();
                        throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.728a7b601c56bdf8"));
                    }
                    replacement = secret.length == 0
                            ? Optional.empty()
                            : Optional.of(new CredentialDraft(authentication, secret, Optional.empty()));
                } else {
                    secret = passphrase.getText().toCharArray();
                    if (requiresReplacement && certificate.getText().isBlank()) {
                        choose.requestFocus();
                        throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.720a21d86afc2182"));
                    }
                    if (editing && certificate.getText().isBlank() && secret.length > 0) {
                        choose.requestFocus();
                        throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a7606671c996f95a"));
                    }
                    replacement = certificate.getText().isBlank()
                            ? Optional.empty()
                            : Optional.of(new CredentialDraft(
                                    authentication, secret, Optional.of(FormValidation.value(choose, () -> Path.of(certificate.getText())))));
                }
                if (editing && reader != null && replacement.isPresent() && !replacementAuthorized[0]) {
                    replacement.orElseThrow().close();
                    event.consume();
                    save.setDisable(true);
                    reader.read(dialog.getDialogPane().getScene().getWindow(), existing.orElseThrow().id())
                            .whenComplete((loaded, failure) -> Platform.runLater(() -> {
                                if (loaded != null) loaded.close();
                                save.setDisable(false);
                                if (!dialog.isShowing()) return;
                                if (failure != null) {
                                    ConnectionFeedback.show(validation,
                                            com.ynpe.fastremote.i18n.Texts.text("legacy.java.fbdae73221a140d4"),
                                            ConnectionFeedback.Tone.ERROR);
                                } else {
                                    replacementAuthorized[0] = true;
                                    save.fire();
                                }
                            }));
                    return;
                }
                dialog.setResult(new Request(credentialName, user, replacement));
                event.consume();
                dialog.close();
            } catch (IllegalArgumentException invalid) {
                event.consume();
                ConnectionFeedback.show(validation,
                        com.ynpe.fastremote.i18n.Texts.translateExternalText(invalid.getMessage()),
                        ConnectionFeedback.Tone.ERROR);
            } finally {
                Arrays.fill(secret, '\0');
            }
        });
        dialog.setOnHidden(event -> {
            password.clear();
            passphrase.clear();
        });
        DialogSpacing.focusFirstInputOnShown(dialog);
        Optional<Request> result = dialog.showAndWait();
        password.clear();
        passphrase.clear();
        return result;
    }

    private static ComboBox<AuthenticationType> authenticationTypeField() {
        ComboBox<AuthenticationType> field = new ComboBox<>();
        field.getItems().setAll(AuthenticationType.PASSWORD, AuthenticationType.PRIVATE_KEY);
        field.setValue(AuthenticationType.PASSWORD);
        field.setCellFactory(ignored -> authenticationTypeCell());
        field.setButtonCell(authenticationTypeCell());
        field.setConverter(new StringConverter<>() {
            @Override public String toString(AuthenticationType value) { return authenticationText(value); }
            @Override public AuthenticationType fromString(String value) { throw new UnsupportedOperationException(); }
        });
        return field;
    }

    private static ListCell<AuthenticationType> authenticationTypeCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(AuthenticationType value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : authenticationText(value));
            }
        };
    }

    private static String authenticationText(AuthenticationType value) {
        if (value == null) return "";
        return value == AuthenticationType.PASSWORD ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.a621ab606db2a11f") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.69eb41708bcbe92c");
    }

    private static FormLabel label(String text) {
        return new FormLabel(text, LABEL_WIDTH, LABEL_HEIGHT);
    }

    static List<String> formLabels() {
        return List.of(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8ef4c232c867864f"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.ba40014ff496f64e"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.1a3f0617d6de8e52"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.a621ab606db2a11f"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.69eb41708bcbe92c"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.13610905c385728f"));
    }

    static double formHeight(int rowCount) {
        return rowCount <= 0 ? 0.0 : rowCount * FORM_ROW_HEIGHT;
    }

    static void configureDialogSizing(javafx.scene.control.Dialog<?> dialog) {
        dialog.setResizable(false);
        DialogSpacing.configureFixedWindowSize(dialog, DIALOG_WIDTH, DIALOG_HEIGHT);
    }

    private static void addRow(AnchorPane form, int row, FormLabel label, Node field) {
        double top = row * FORM_ROW_HEIGHT;
        AnchorPane.setLeftAnchor(label, 0.0);
        AnchorPane.setTopAnchor(label, top + (FORM_ROW_HEIGHT - LABEL_HEIGHT) / 2.0);
        AnchorPane.setLeftAnchor(field, FIELD_LEFT);
        AnchorPane.setRightAnchor(field, 0.0);
        AnchorPane.setTopAnchor(field, top + (FORM_ROW_HEIGHT - FORM_CONTROL_HEIGHT) / 2.0);
        if (field instanceof Region region) {
            region.setMinWidth(FIELD_MIN_WIDTH);
            region.setMinHeight(FORM_CONTROL_HEIGHT);
            region.setPrefHeight(FORM_CONTROL_HEIGHT);
            region.setMaxHeight(FORM_CONTROL_HEIGHT);
        }
        form.getChildren().addAll(label, field);
    }

    private static String required(TextField field, String message) {
        String value = field.getText().strip();
        if (value.isEmpty()) {
            DialogSpacing.focusAtEnd(field);
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    record Request(String label, String username, Optional<CredentialDraft> replacement) {
        Request {
            label = Objects.requireNonNull(label, "label");
            username = Objects.requireNonNull(username, "username");
            replacement = Objects.requireNonNull(replacement, "replacement");
        }
    }
}
