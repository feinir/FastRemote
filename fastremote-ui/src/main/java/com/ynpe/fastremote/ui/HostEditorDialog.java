// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.catalog.HostIcon;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.ConnectionProgress;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.Username;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import com.ynpe.fastremote.domain.credential.CredentialGroup;
import com.ynpe.fastremote.domain.vault.SecretId;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** Compact editor for fields that are currently persisted by a saved SSH host. */
final class HostEditorDialog {
    static final double FORM_LABEL_WIDTH = FormLabel.ENGLISH_BASELINE_WIDTH;
    static final double FORM_FIELD_GAP = 15.0;
    static final double FORM_COLUMN_GAP = 18.0;
    static final double FORM_CONTROL_HEIGHT = 34.0;
    static final double FORM_ROW_HEIGHT = 44.0;
    static final double FORM_LABEL_HEIGHT = FormLabel.COMPACT_TWO_LINE_HEIGHT;
    static final double FORM_ROW_GAP = 0.0;
    static final double FORM_FIELD_MIN_WIDTH = 160.0;
    static final double FORM_CELL_MIN_WIDTH = FORM_LABEL_WIDTH + FORM_FIELD_GAP + FORM_FIELD_MIN_WIDTH;
    static final double DIALOG_WIDTH = 680.0;
    static final double CREATE_DIALOG_HEIGHT = 749.0 - FORM_ROW_HEIGHT;
    static final double EDIT_DIALOG_HEIGHT = CREATE_DIALOG_HEIGHT - FORM_ROW_HEIGHT;

    private HostEditorDialog() {}

    static Optional<HostRequest> show(
            Window owner, List<HostGroup> groups, List<SavedHost> hosts,
            List<GlobalCredential> credentials, VaultStatus vaultStatus, Optional<GroupId> initialGroup,
            Function<Window, CompletionStage<Boolean>> unlockVault) {
        return show(owner, groups, hosts, credentials, vaultStatus, initialGroup, Optional.empty(), Mode.CREATE,
                unlockVault, List.of(), unavailableTester());
    }

    static Optional<HostRequest> edit(
            Window owner, List<HostGroup> groups, List<SavedHost> hosts,
            List<GlobalCredential> credentials, VaultStatus vaultStatus, SavedHost host,
            Function<Window, CompletionStage<Boolean>> unlockVault) {
        return show(owner, groups, hosts, credentials, vaultStatus,
                host.groupId(), Optional.of(host), Mode.EDIT, unlockVault, List.of(), unavailableTester());
    }

    static Optional<HostRequest> copy(
            Window owner, List<HostGroup> groups, List<SavedHost> hosts,
            List<GlobalCredential> credentials, VaultStatus vaultStatus, SavedHost source,
            Function<Window, CompletionStage<Boolean>> unlockVault) {
        return show(owner, groups, hosts, credentials, vaultStatus,
                source.groupId(), Optional.of(source), Mode.COPY, unlockVault, List.of(), unavailableTester());
    }

    static Optional<HostRequest> show(Window owner, List<HostGroup> groups, List<SavedHost> hosts,
            List<GlobalCredential> credentials, List<CredentialGroup> credentialGroups, VaultStatus vaultStatus,
            Optional<GroupId> initialGroup, Function<Window, CompletionStage<Boolean>> unlockVault,
            ConnectionTester connectionTester) {
        return show(owner, groups, hosts, credentials, vaultStatus, initialGroup, Optional.empty(), Mode.CREATE,
                unlockVault, credentialGroups, connectionTester);
    }

    static Optional<HostRequest> edit(Window owner, List<HostGroup> groups, List<SavedHost> hosts,
            List<GlobalCredential> credentials, List<CredentialGroup> credentialGroups, VaultStatus vaultStatus,
            SavedHost host, Function<Window, CompletionStage<Boolean>> unlockVault,
            ConnectionTester connectionTester) {
        return show(owner, groups, hosts, credentials, vaultStatus, host.groupId(), Optional.of(host), Mode.EDIT,
                unlockVault, credentialGroups, connectionTester);
    }

    static Optional<HostRequest> edit(Window owner, List<HostGroup> groups, List<SavedHost> hosts,
            List<GlobalCredential> credentials, List<CredentialGroup> credentialGroups, VaultStatus vaultStatus,
            SavedHost host, Function<Window, CompletionStage<Boolean>> unlockVault,
            ConnectionTester connectionTester, SavedCredentialReader savedCredentialReader) {
        return show(owner, groups, hosts, credentials, vaultStatus, host.groupId(), Optional.of(host), Mode.EDIT,
                unlockVault, credentialGroups, connectionTester, savedCredentialReader);
    }

    static Optional<HostRequest> copy(Window owner, List<HostGroup> groups, List<SavedHost> hosts,
            List<GlobalCredential> credentials, List<CredentialGroup> credentialGroups, VaultStatus vaultStatus,
            SavedHost host, Function<Window, CompletionStage<Boolean>> unlockVault,
            ConnectionTester connectionTester) {
        return show(owner, groups, hosts, credentials, vaultStatus, host.groupId(), Optional.of(host), Mode.COPY,
                unlockVault, credentialGroups, connectionTester);
    }

    private static Optional<HostRequest> show(
            Window owner, List<HostGroup> groups, List<SavedHost> hosts,
            List<GlobalCredential> credentials, VaultStatus vaultStatus, Optional<GroupId> initialGroup,
            Optional<SavedHost> source, Mode mode,
            Function<Window, CompletionStage<Boolean>> unlockVault,
            List<CredentialGroup> credentialGroups,
            ConnectionTester connectionTester) {
        return show(owner, groups, hosts, credentials, vaultStatus, initialGroup, source, mode,
                unlockVault, credentialGroups, connectionTester, null);
    }

    private static Optional<HostRequest> show(
            Window owner, List<HostGroup> groups, List<SavedHost> hosts,
            List<GlobalCredential> credentials, VaultStatus vaultStatus, Optional<GroupId> initialGroup,
            Optional<SavedHost> source, Mode mode,
            Function<Window, CompletionStage<Boolean>> unlockVault,
            List<CredentialGroup> credentialGroups, ConnectionTester connectionTester,
            SavedCredentialReader savedCredentialReader) {
        Objects.requireNonNull(groups, "groups");
        Objects.requireNonNull(hosts, "hosts");
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(unlockVault, "unlockVault");
        Objects.requireNonNull(connectionTester, "connectionTester");
        SavedHost initial = source.orElse(null);

        Dialog<HostRequest> dialog = new Dialog<>();
        dialog.setDialogPane(new InsertionOrderedDialogPane());
        dialog.initOwner(owner);
        dialog.setTitle(switch (mode) {
            case CREATE -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.22a3f0c4b4bd8606");
            case EDIT -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.50158b7fd5855d55");
            case COPY -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.dd56ba585c080a47");
        });
        dialog.setHeaderText(mode == Mode.CREATE
                ? com.ynpe.fastremote.i18n.Texts.text("hostEditor.createHeader")
                : null);
        dialog.setResizable(LocalizedTextInputContextMenus.isLinux());
        DialogSpacing.prepare(dialog);
        dialog.getDialogPane().getStyleClass().add("host-editor-dialog");
        ButtonType save = new ButtonType(mode == Mode.EDIT ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.991bb7cfe5a81550") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.5c973ac87f12512c"), ButtonBar.ButtonData.OK_DONE);
        ButtonType test = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0a98a48eaaac4175"), ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().setAll(test, ButtonType.CANCEL, save);
        dialog.setResultConverter(DialogResultPolicy.explicitOnly());

        TextField name = new TextField(initialHostName(initial, mode));
        name.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.270c4bee5ae7c922"));
        ComboBox<GroupChoice> group = new ComboBox<>();
        group.setMaxWidth(Double.MAX_VALUE);
        group.getItems().setAll(groupChoices(groups));
        group.getSelectionModel().select(group.getItems().stream()
                .filter(choice -> choice.id().equals(initialGroup)).findFirst().orElse(group.getItems().getFirst()));

        ComboBox<IconChoice> icon = new ComboBox<>();
        icon.setMaxWidth(Double.MAX_VALUE);
        icon.getItems().setAll(iconChoices());
        icon.setCellFactory(ignored -> iconCell());
        icon.setButtonCell(iconCell());
        icon.setMaxWidth(Double.MAX_VALUE);
        icon.getSelectionModel().select(icon.getItems().stream()
                .filter(choice -> initial != null && choice.icon() == initial.icon())
                .findFirst().orElse(icon.getItems().getFirst()));
        CheckBox favorite = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.991ab30a8e4da7c7"));
        favorite.setSelected(initial != null && initial.favorite());

        TextField host = new TextField(initial == null ? "" : initial.profile().host().value());
        host.setPromptText("server.example.com");
        TextField port = new TextField(initial == null ? "22" : Integer.toString(initial.profile().port().value()));
        port.setPrefColumnCount(5);
        TextField username = new TextField(initial == null ? "" : initial.profile().username().value());
        ComboBox<AuthenticationType> authentication = new ComboBox<>();
        authentication.getItems().setAll(AuthenticationType.PASSWORD, AuthenticationType.PRIVATE_KEY);
        authentication.setValue(initial == null ? AuthenticationType.PASSWORD : initial.profile().authenticationType());
        authentication.setCellFactory(ignored -> authenticationCell());
        authentication.setButtonCell(authenticationCell());

        Optional<com.ynpe.fastremote.domain.catalog.HostId> initialJump = initial == null
                ? Optional.empty() : initial.jumpHostId();
        MenuButton jumpHost = new MenuButton();
        SearchableJumpHostSelector jumpSelector = new SearchableJumpHostSelector(
                jumpHost, hosts, groups, initialJump,
                initial == null ? Optional.empty() : Optional.of(initial.id()));
        CheckBox legacyAlgorithms = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8702dbfeecca0ce2"));
        legacyAlgorithms.setSelected(initial != null && initial.profile().legacyAlgorithmsEnabled());
        legacyAlgorithms.setTooltip(new Tooltip(com.ynpe.fastremote.i18n.Texts.text("legacy.java.cf205dc1042fc648")));

        ComboBox<CredentialSource> credentialSource = new ComboBox<>();
        credentialSource.setMaxWidth(Double.MAX_VALUE);
        List<CredentialSource> sourceChoices = newHostCredentialSources();
        CredentialSource saveCredential = sourceChoices.get(0);
        CredentialSource promptCredential = sourceChoices.get(1);
        CredentialSource libraryCredential = sourceChoices.get(2);
        credentialSource.getItems().setAll(sourceChoices);

        MenuButton credentialLibrary = new MenuButton();
        SearchableCredentialSelector credentialSelector = new SearchableCredentialSelector(
                credentialLibrary, credentials, credentialGroups,
                initial == null ? Optional.empty() : initial.credentialSecretId());
        GlobalCredential initialLibraryCredential = credentialSelector.selectedCredential().orElse(null);
        CredentialSource defaultCredentialSource = switch (initialCredentialSourceKind(
                mode, initial != null && initial.credentialSecretId().isPresent(),
                initialLibraryCredential != null)) {
            case LIBRARY -> libraryCredential;
            case CURRENT_HOST -> {
                CredentialSource current = CredentialSource.current(
                        initial.credentialSecretId().orElseThrow());
                credentialSource.getItems().add(0, current);
                yield current;
            }
            case SAVE_TO_HOST -> saveCredential;
            case PROMPT -> promptCredential;
        };
        credentialSource.setValue(defaultCredentialSource);

        TextField privateKey = new TextField(initial == null ? "" : initial.profile().privateKeyPath()
                .map(Path::toString).orElse(""));
        Button chooseKey = FileSelectionButton.create(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.c11330b85234f9c0"));
        HBox keyRow = fileSelectionRow(privateKey, chooseKey);
        RevealablePasswordEditor hostPassword = new RevealablePasswordEditor(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7be1419614f3508d"));
        hostPassword.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6df4a44a517fcc38"));
        RevealablePasswordEditor privateKeyPassphrase = new RevealablePasswordEditor(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d2ec1829b67bbe98"));
        privateKeyPassphrase.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9005b81142cd10cd"));
        Label feedback = ConnectionFeedback.reservedLabel();

        Button parseClipboard = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1c0d0153188749d8"));
        parseClipboard.setMaxWidth(Double.MAX_VALUE);

        List<String> formLabels = formLabels();
        AnchorPane form = compactForm(11);
        addTwoColumnRow(form, 0, formLabels.get(0), name, formLabels.get(1), group);
        addTwoColumnRow(form, 1, formLabels.get(2), icon, "", favorite);
        Label connectionTitle = sectionTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7bcff7ee240c4490"));
        addFullWidthNode(form, 2, connectionTitle);
        addTwoColumnRow(form, 3, formLabels.get(3), host, formLabels.get(4), port);
        addTwoColumnRow(form, 4, formLabels.get(5), username, formLabels.get(6), legacyAlgorithms);
        addTwoColumnRow(form, 5, formLabels.get(7), credentialSource, formLabels.get(8), authentication);
        addFullRow(form, 6, formLabels.get(9), hostPassword);
        addFullRow(form, 7, formLabels.get(10), keyRow);
        addFullRow(form, 8, formLabels.get(11), privateKeyPassphrase);
        addFullRow(form, 9, formLabels.get(12), credentialLibrary);
        addFullRow(form, 10, formLabels.get(13), jumpHost);

        VBox content = new VBox(10);
        content.getChildren().addAll(
                parseClipboard,
                sectionTitle(mode == Mode.EDIT ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.f09b65301578427f") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.6248fcce8800fd05")),
                form, feedback);
        content.setPadding(new Insets(14, 18, 6, 18));
        dialog.getDialogPane().setContent(content);
        configureDialogSizing(dialog, mode);

        java.util.function.Consumer<String> showValidation = message -> {
            ConnectionFeedback.show(feedback, message, ConnectionFeedback.Tone.ERROR);
        };
        Runnable clearValidation = () -> {
            ConnectionFeedback.clear(feedback);
        };

        parseClipboard.setOnAction(event -> {
            try {
                ConnectionClipboardImporter.ParsedConnection parsed =
                        ConnectionClipboardImporter.parseSystemClipboard();
                credentialSource.setValue(saveCredential);
                credentialSelector.clearSelection();
                ConnectionClipboardImporter.apply(parsed, Optional.of(name), host, port, username,
                        authentication, hostPassword::setText, privateKey, privateKeyPassphrase::setText);
                ConnectionClipboardImporter.revealParsedSecret(parsed, hostPassword, privateKeyPassphrase);
                clearValidation.run();
            } catch (IllegalArgumentException failure) {
                showValidation.accept(com.ynpe.fastremote.i18n.Texts.translateExternalText(failure.getMessage()));
            }
        });

        chooseKey.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1c331767f030aa90"));
            File selected = PlatformFileChooser.showOpenDialog(chooser, owner);
            if (selected != null) privateKey.setText(selected.getAbsolutePath());
        });
        Runnable updateFields = () -> {
            CredentialSource selectedSource = credentialSource.getValue();
            boolean fromLibrary = selectedSource != null && selectedSource.kind() == CredentialSourceKind.LIBRARY;
            boolean saveToHost = selectedSource != null && selectedSource.kind() == CredentialSourceKind.SAVE_TO_HOST;
            boolean promptOnConnect = selectedSource != null && selectedSource.kind() == CredentialSourceKind.PROMPT;
            boolean currentHostCredential = selectedSource != null
                    && selectedSource.kind() == CredentialSourceKind.CURRENT_HOST;
            GlobalCredential selected = fromLibrary ? credentialSelector.selectedCredential().orElse(null) : null;
            if (selected != null) {
                username.setText(selected.username());
                authentication.setValue(selected.authenticationType());
            }
            boolean fixedIdentity = fromLibrary || currentHostCredential;
            boolean privateKeyAuthentication = authentication.getValue() == AuthenticationType.PRIVATE_KEY;
            if (fixedIdentity) hostPassword.clear();
            boolean editingBound = mode == Mode.EDIT && initial != null
                    && initial.credentialSecretId().isPresent() && initialLibraryCredential == null
                    && saveToHost && authentication.getValue() == initial.profile().authenticationType();
            hostPassword.setPromptText(com.ynpe.fastremote.i18n.Texts.text(editingBound
                    ? "legacy.java.93e91a4b0a7d1920" : "legacy.java.6df4a44a517fcc38"));
            privateKey.setPromptText(editingBound
                    ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.fe094c368609d67f") : "");
            Optional<SecretId> previewId = editingBound ? initial.credentialSecretId()
                    : currentHostCredential ? selectedSource.referenceId()
                    : selected == null ? Optional.empty() : Optional.of(selected.id());
            boolean canPreview = mode == Mode.EDIT && savedCredentialReader != null
                    && !privateKeyAuthentication && previewId.isPresent();
            hostPassword.setSavedSecretLoader(canPreview
                    ? () -> {
                        clearValidation.run();
                        return savedCredentialReader.read(dialog.getDialogPane().getScene().getWindow(), previewId.orElseThrow());
                    }
                    : null, failure -> showValidation.accept(
                            com.ynpe.fastremote.i18n.Texts.text("legacy.java.fbdae73221a140d4")));
            hostPassword.setInputEditable(!fixedIdentity);
            credentialLibrary.setDisable(!fromLibrary);
            username.setDisable(fixedIdentity);
            authentication.setDisable(fixedIdentity);
            hostPassword.setDisable((!saveToHost && !promptOnConnect && !canPreview) || privateKeyAuthentication);
            privateKey.setDisable(fixedIdentity || !privateKeyAuthentication);
            chooseKey.setDisable(fixedIdentity || !privateKeyAuthentication);
            privateKeyPassphrase.setDisable((!saveToHost && !promptOnConnect) || !privateKeyAuthentication);
        };
        credentialSource.valueProperty().addListener((ignored, previous, selected) -> updateFields.run());
        credentialSelector.setOnSelectionChanged(updateFields);
        authentication.valueProperty().addListener((ignored, previous, selected) -> updateFields.run());
        updateFields.run();

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        Button testButton = (Button) dialog.getDialogPane().lookupButton(test);
        boolean[] vaultAvailable = {vaultStatus == VaultStatus.UNLOCKED};
        AtomicReference<ConnectionTestAttempt> activeTest = new AtomicReference<>();
        java.util.function.BiConsumer<String, TestTone> showTestStatus = (message, tone) -> {
            ConnectionFeedback.show(feedback, message, switch (tone) {
                case SUCCESS -> ConnectionFeedback.Tone.SUCCESS;
                case ERROR -> ConnectionFeedback.Tone.ERROR;
                case WORKING -> ConnectionFeedback.Tone.WORKING;
            });
        };
        testButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            ConnectionTestAttempt running = activeTest.get();
            if (running != null) {
                showTestStatus.accept(com.ynpe.fastremote.i18n.Texts.text("legacy.java.58db0fe39e87bf53"), TestTone.WORKING);
                running.cancel();
                return;
            }
            CredentialDraft draft = null;
            try {
                AuthenticationType auth = Objects.requireNonNull(authentication.getValue(), "authentication");
                CredentialSource selectedSource = submissionCredentialSource(
                        mode, initial, initialLibraryCredential != null,
                        Objects.requireNonNull(credentialSource.getValue(), "credentialSource"), auth,
                        hostPassword.getText(), privateKey.getText(), privateKeyPassphrase.getText());
                GlobalCredential selectedLibraryCredential = selectedSource.kind() == CredentialSourceKind.LIBRARY
                        ? credentialSelector.selectedCredential().orElse(null) : null;
                if (selectedSource.kind() == CredentialSourceKind.LIBRARY && selectedLibraryCredential == null) {
                    FormValidation.focus(credentialLibrary);
                    throw new IllegalArgumentException(credentials.isEmpty()
                            ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.5f05ae6b503e519a")
                            : com.ynpe.fastremote.i18n.Texts.text("legacy.java.080357ce1f4da1e8"));
                }
                boolean embeddedReusable = selectedLibraryCredential != null
                        && selectedLibraryCredential.embeddedPrivateKey();
                boolean existingBound = selectedSource.kind() == CredentialSourceKind.CURRENT_HOST;
                Optional<Path> key = auth == AuthenticationType.PRIVATE_KEY && !embeddedReusable && !existingBound
                        ? Optional.of(FormValidation.value(privateKey, () -> Path.of(required(privateKey, com.ynpe.fastremote.i18n.Texts.text("legacy.java.5b1a68b125e621fd"))))) : Optional.empty();
                ConnectionProfile profile = new ConnectionProfile(
                        FormValidation.value(host, () -> new Host(required(host, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d4a24182bf296e26")))), new Port(parsePort(port)),
                        FormValidation.value(username, () -> new Username(required(username, com.ynpe.fastremote.i18n.Texts.text("legacy.java.c723b1fab58f5dbc")))), auth, key,
                        legacyAlgorithms.isSelected());
                Optional<SecretId> credentialId = selectedLibraryCredential != null
                        ? Optional.of(selectedLibraryCredential.id()) : selectedSource.referenceId();
                Optional<HostId> jumpId = jumpSelector.selectedHostId();
                FormValidation.value(jumpHost, () -> { validateJumpSelection(initial == null ? Optional.empty() : Optional.of(initial.id()), jumpId, hosts); return null; });
                if (connectionTestRequiresVault(credentialId, jumpId, hosts) && !vaultAvailable[0]) {
                    showTestStatus.accept(com.ynpe.fastremote.i18n.Texts.text("legacy.java.325fa105f60b51c4"), TestTone.ERROR);
                    testButton.setDisable(true);
                    CompletionStage<Boolean> unlockAttempt = Objects.requireNonNull(
                            unlockVault.apply(dialog.getDialogPane().getScene().getWindow()), "unlockAttempt");
                    unlockAttempt.whenComplete((unlocked, failure) -> Platform.runLater(() -> {
                        testButton.setDisable(false);
                        if (failure != null || !Boolean.TRUE.equals(unlocked)) {
                            showTestStatus.accept(com.ynpe.fastremote.i18n.Texts.text("legacy.java.627b097a22304cff"), TestTone.ERROR);
                        } else {
                            vaultAvailable[0] = true;
                            testButton.fire();
                        }
                    }));
                    return;
                }
                if (credentialId.isEmpty()) {
                    if (auth == AuthenticationType.PASSWORD && hostPassword.getText().isEmpty()) {
                        hostPassword.focusInput();
                        throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3fb7c6e8eada2289"));
                    }
                    char[] entered = (auth == AuthenticationType.PASSWORD
                            ? hostPassword.getText() : privateKeyPassphrase.getText()).toCharArray();
                    try {
                        draft = new CredentialDraft(auth, entered,
                                auth == AuthenticationType.PRIVATE_KEY ? key : Optional.empty());
                    } finally {
                        java.util.Arrays.fill(entered, '\0');
                    }
                }
                ConnectionTestRequest request = new ConnectionTestRequest(
                        profile, jumpId, credentialId, Optional.ofNullable(draft));
                draft = null;
                ConnectionTestAttempt attempt;
                try {
                    attempt = Objects.requireNonNull(connectionTester.test(
                            dialog.getDialogPane().getScene().getWindow(), request,
                            progress -> Platform.runLater(() -> showTestStatus.accept(
                                    testProgressMessage(progress), TestTone.WORKING))), "connectionTestAttempt");
                } catch (RuntimeException failure) {
                    request.close();
                    throw failure;
                }
                activeTest.set(attempt);
                form.setDisable(true);
                parseClipboard.setDisable(true);
                saveButton.setDisable(true);
                testButton.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.49be72e6045c007d"));
                showTestStatus.accept(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5ece418ec332f50"), TestTone.WORKING);
                attempt.result().whenComplete((ignored, failure) -> Platform.runLater(() -> {
                    if (!activeTest.compareAndSet(attempt, null)) return;
                    form.setDisable(false);
                    parseClipboard.setDisable(false);
                    saveButton.setDisable(false);
                    testButton.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0a98a48eaaac4175"));
                    if (failure == null) {
                        showTestStatus.accept(com.ynpe.fastremote.i18n.Texts.text("legacy.java.da0bfef256bc82e4"), TestTone.SUCCESS);
                    } else {
                        Throwable cause = unwrap(failure);
                        showTestStatus.accept(cause instanceof CancellationException
                                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.b7c75172b393766f") : MainWindowController.connectionFailureMessage(cause),
                                TestTone.ERROR);
                    }
                }));
            } catch (IllegalArgumentException | NullPointerException invalid) {
                if (draft != null) draft.close();
                showTestStatus.accept(invalid.getMessage() == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.85f2ba637f7f5d7e")
                                : com.ynpe.fastremote.i18n.Texts.translateExternalText(invalid.getMessage()),
                        TestTone.ERROR);
            }
        });
        Runnable clearCompletedTest = () -> {
            if (activeTest.get() != null || !feedback.isVisible()) return;
            ConnectionFeedback.clear(feedback);
        };
        host.textProperty().addListener((ignored, previous, value) -> clearCompletedTest.run());
        port.textProperty().addListener((ignored, previous, value) -> clearCompletedTest.run());
        username.textProperty().addListener((ignored, previous, value) -> clearCompletedTest.run());
        authentication.valueProperty().addListener((ignored, previous, value) -> clearCompletedTest.run());
        credentialSource.valueProperty().addListener((ignored, previous, value) -> clearCompletedTest.run());
        credentialSelector.setOnSelectionChanged(() -> {
            updateFields.run();
            clearCompletedTest.run();
        });
        hostPassword.textProperty().addListener((ignored, previous, value) -> clearCompletedTest.run());
        privateKey.textProperty().addListener((ignored, previous, value) -> clearCompletedTest.run());
        privateKeyPassphrase.textProperty().addListener((ignored, previous, value) -> clearCompletedTest.run());
        legacyAlgorithms.selectedProperty().addListener((ignored, previous, value) -> clearCompletedTest.run());
        jumpSelector.setOnSelectionChanged(clearCompletedTest);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            try {
                FormValidation.require(name, !name.getText().strip().isEmpty() && name.getText().strip().length() <= 255,
                        "Host name must contain 1 to 255 characters");
                GroupChoice selectedGroup = Objects.requireNonNull(group.getValue(), "group");
                AuthenticationType auth = Objects.requireNonNull(authentication.getValue(), "authentication");
                CredentialSource selectedSource = submissionCredentialSource(
                        mode, initial, initialLibraryCredential != null,
                        Objects.requireNonNull(credentialSource.getValue(), "credentialSource"), auth,
                        hostPassword.getText(), privateKey.getText(), privateKeyPassphrase.getText());
                Optional<String> passwordError = validateSavedHostPassword(
                        selectedSource, auth, hostPassword.getText());
                if (passwordError.isPresent()) {
                    showValidation.accept(passwordError.get());
                    hostPassword.focusInput();
                    return;
                }
                GlobalCredential selectedLibraryCredential = selectedSource.kind() == CredentialSourceKind.LIBRARY
                        ? credentialSelector.selectedCredential().orElse(null) : null;
                if (selectedSource.kind() == CredentialSourceKind.LIBRARY && selectedLibraryCredential == null) {
                    FormValidation.focus(credentialLibrary);
                    throw new IllegalArgumentException(credentials.isEmpty()
                            ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.5f05ae6b503e519a")
                            : com.ynpe.fastremote.i18n.Texts.text("legacy.java.080357ce1f4da1e8"));
                }
                boolean embeddedReusable = selectedLibraryCredential != null
                        && selectedLibraryCredential.embeddedPrivateKey();
                boolean existingBound = selectedSource.kind() == CredentialSourceKind.CURRENT_HOST;
                Optional<Path> key = auth == AuthenticationType.PRIVATE_KEY && !embeddedReusable && !existingBound
                        ? Optional.of(FormValidation.value(privateKey, () -> Path.of(required(privateKey, com.ynpe.fastremote.i18n.Texts.text("legacy.java.5b1a68b125e621fd"))))) : Optional.empty();
                ConnectionProfile profile = new ConnectionProfile(
                        FormValidation.value(host, () -> new Host(required(host, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d4a24182bf296e26")))), new Port(parsePort(port)),
                        FormValidation.value(username, () -> new Username(required(username, com.ynpe.fastremote.i18n.Texts.text("legacy.java.c723b1fab58f5dbc")))), auth, key,
                        legacyAlgorithms.isSelected());
                Optional<SecretId> credentialId = selectedLibraryCredential != null
                        ? Optional.of(selectedLibraryCredential.id()) : selectedSource.referenceId();
                if (mode == Mode.EDIT && selectedSource.kind() == CredentialSourceKind.SAVE_TO_HOST
                        && initial != null && initial.credentialSecretId().isPresent()) {
                    // Preserve the stable bound id so the workflow replaces its encrypted payload.
                    credentialId = initial.credentialSecretId();
                }
                Optional<com.ynpe.fastremote.domain.catalog.HostId> jumpId = jumpSelector.selectedHostId();
                FormValidation.value(jumpHost, () -> { validateJumpSelection(initial == null ? Optional.empty() : Optional.of(initial.id()), jumpId, hosts); return null; });
                SavedHost savedHost;
                if (!createsNewHost(mode)) {
                    int sortOrder = initial.groupId().equals(selectedGroup.id())
                            ? initial.sortOrder() : nextSortOrder(hosts, selectedGroup.id());
                    savedHost = new SavedHost(
                            initial.id(), selectedGroup.id(), required(name, com.ynpe.fastremote.i18n.Texts.text("legacy.java.bbbc16cbea1b5b77")), profile,
                            jumpId, credentialId, favorite.isSelected(), sortOrder, initial.createdAt(), Instant.now(),
                            Objects.requireNonNull(icon.getValue(), "icon").icon());
                } else {
                    savedHost = SavedHost.create(
                            required(name, com.ynpe.fastremote.i18n.Texts.text("legacy.java.bbbc16cbea1b5b77")), selectedGroup.id(), profile,
                            nextSortOrder(hosts, selectedGroup.id()),
                            Objects.requireNonNull(icon.getValue(), "icon").icon())
                            .withFavorite(favorite.isSelected())
                            .withJumpHost(jumpId);
                    if (credentialId.isPresent()) savedHost = savedHost.withCredential(credentialId);
                }
                if (requiresVaultUnlock(selectedSource, vaultAvailable[0], mode)) {
                    showValidation.accept(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6cc2d3e45779c66e"));
                    saveButton.setDisable(true);
                    Window dialogWindow = dialog.getDialogPane().getScene().getWindow();
                    CompletionStage<Boolean> unlockAttempt;
                    try {
                        unlockAttempt = Objects.requireNonNull(unlockVault.apply(dialogWindow), "unlockAttempt");
                    } catch (RuntimeException failure) {
                        saveButton.setDisable(false);
                        throw failure;
                    }
                    unlockAttempt.whenComplete((unlocked, failure) -> Platform.runLater(() -> {
                        saveButton.setDisable(false);
                        if (failure != null) {
                            showValidation.accept(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c5fec693e1f2b06f"));
                        } else if (!Boolean.TRUE.equals(unlocked)) {
                            showValidation.accept(com.ynpe.fastremote.i18n.Texts.text("legacy.java.cfd2ed25b14c1d96"));
                        } else {
                            vaultAvailable[0] = true;
                            clearValidation.run();
                            saveButton.fire();
                        }
                    }));
                    return;
                }
                Optional<CredentialDraft> value = Optional.empty();
                if (selectedSource.kind() == CredentialSourceKind.SAVE_TO_HOST) {
                    char[] entered = (auth == AuthenticationType.PASSWORD
                            ? hostPassword.getText() : privateKeyPassphrase.getText()).toCharArray();
                    try {
                        value = Optional.of(new CredentialDraft(auth, entered,
                                auth == AuthenticationType.PRIVATE_KEY
                                        ? Optional.of(FormValidation.value(privateKey, () -> Path.of(required(privateKey, com.ynpe.fastremote.i18n.Texts.text("legacy.java.5b1a68b125e621fd")))))
                                        : Optional.empty()));
                    } finally {
                        java.util.Arrays.fill(entered, '\0');
                    }
                }
                hostPassword.clear();
                privateKeyPassphrase.clear();
                dialog.setResult(new HostRequest(savedHost, value));
                dialog.close();
            } catch (IllegalArgumentException | NullPointerException invalid) {
                showValidation.accept(invalid.getMessage() == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.e312a6b2c5b3723c")
                        : com.ynpe.fastremote.i18n.Texts.translateExternalText(invalid.getMessage()));
            }
        });
        dialog.setOnHidden(event -> {
            ConnectionTestAttempt running = activeTest.getAndSet(null);
            if (running != null) running.cancel();
            hostPassword.clear();
            privateKeyPassphrase.clear();
        });
        Node ownerFocus = owner == null || owner.getScene() == null ? null : owner.getScene().getFocusOwner();
        try {
            DialogSpacing.focusFirstInputOnShown(dialog);
            return dialog.showAndWait();
        } finally {
            // Wait until the child modal loop has unwound and Glass has enabled the owner.
            // Otherwise macOS can leave the library inactive: Escape goes elsewhere and the
            // first title-bar close click only activates the window.
            Platform.runLater(() -> {
                if (owner == null || !owner.isShowing()) return;
                owner.requestFocus();
                if (ownerFocus != null && ownerFocus.getScene() == owner.getScene()) ownerFocus.requestFocus();
            });
        }
    }

    static void configureDialogSizing(javafx.scene.control.Dialog<?> dialog, Mode mode) {
        Objects.requireNonNull(dialog, "dialog");
        Objects.requireNonNull(mode, "mode");
        dialog.setResizable(LocalizedTextInputContextMenus.isLinux());
        if (LocalizedTextInputContextMenus.isLinux()) {
            DialogSpacing.configureResizableWindowGeometry(dialog, DIALOG_WIDTH, dialogHeight(mode), 480, 400);
        } else {
            DialogSpacing.configureFixedWindowSize(dialog, DIALOG_WIDTH, dialogHeight(mode));
        }
    }

    static double dialogHeight(Mode mode) {
        return mode == Mode.CREATE ? CREATE_DIALOG_HEIGHT : EDIT_DIALOG_HEIGHT;
    }

    static String initialHostName(SavedHost source, Mode mode) {
        if (source == null) return "";
        return source.name() + (mode == Mode.COPY
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.6ded4e175cd7f0e1") : "");
    }

    static boolean createsNewHost(Mode mode) {
        return mode != Mode.EDIT;
    }

    static boolean requiresVaultUnlock(CredentialSource source, boolean vaultAvailable) {
        return requiresVaultUnlock(source, vaultAvailable, Mode.CREATE);
    }

    static boolean requiresVaultUnlock(
            CredentialSource source, boolean vaultAvailable, Mode mode) {
        return !vaultAvailable && (source.kind() == CredentialSourceKind.SAVE_TO_HOST
                || mode == Mode.COPY && source.kind() == CredentialSourceKind.CURRENT_HOST);
    }

    static CredentialSourceKind initialCredentialSourceKind(
            Mode mode, boolean hasStoredCredential, boolean hasReusableCredential) {
        if (hasReusableCredential) return CredentialSourceKind.LIBRARY;
        if (hasStoredCredential && mode == Mode.COPY) return CredentialSourceKind.CURRENT_HOST;
        if (hasStoredCredential && mode == Mode.EDIT) return CredentialSourceKind.SAVE_TO_HOST;
        return mode == Mode.CREATE ? CredentialSourceKind.SAVE_TO_HOST : CredentialSourceKind.PROMPT;
    }

    // Keeping an unchanged secret is a submission detail, never a separate edit mode.
    static CredentialSource submissionCredentialSource(
            Mode mode, SavedHost initial, boolean reusable, CredentialSource selected,
            AuthenticationType auth, String password, String keyPath, String passphrase) {
        if (mode == Mode.EDIT && initial != null && !reusable
                && initial.credentialSecretId().isPresent()
                && selected.kind() == CredentialSourceKind.SAVE_TO_HOST
                && auth == initial.profile().authenticationType()
                && (auth == AuthenticationType.PASSWORD ? password.isEmpty()
                    : passphrase.isEmpty() && keyPath.equals(
                            initial.profile().privateKeyPath().map(Path::toString).orElse("")))) {
            return CredentialSource.current(initial.credentialSecretId().orElseThrow());
        }
        return selected;
    }

    static boolean connectionTestRequiresVault(
            Optional<SecretId> targetCredentialId, Optional<HostId> jumpHostId,
            List<SavedHost> hosts) {
        if (targetCredentialId.isPresent()) return true;
        if (jumpHostId.isEmpty()) return false;
        Map<HostId, SavedHost> byId = hosts.stream()
                .collect(java.util.stream.Collectors.toMap(SavedHost::id, Function.identity()));
        java.util.Set<HostId> visited = new java.util.HashSet<>();
        Optional<HostId> current = jumpHostId;
        while (current.isPresent() && visited.add(current.orElseThrow())) {
            SavedHost hop = byId.get(current.orElseThrow());
            if (hop == null) return false;
            if (hop.credentialSecretId().isPresent()) return true;
            current = hop.jumpHostId();
        }
        return false;
    }

    static String testProgressMessage(ConnectionProgress progress) {
        return switch (progress) {
            case NETWORK_CONNECTING -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5ece418ec332f50");
            case HOST_KEY_VALIDATING -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.904ab80933bac7ae");
            case PASSWORD_AUTHENTICATING -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.9464f164f601ce86");
            case PRIVATE_KEY_AUTHENTICATING -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.ba7a24c95649ebbb");
            case SESSION_ESTABLISHED -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.2d88f38aa0915a96");
        };
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static ConnectionTester unavailableTester() {
        return (owner, request, progress) -> {
            request.close();
            return new ConnectionTestAttempt(java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalStateException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c92fde94d98390e3"))), () -> {});
        };
    }

    static Optional<String> validateSavedHostPassword(
            CredentialSource source, AuthenticationType authenticationType, String password) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(authenticationType, "authenticationType");
        Objects.requireNonNull(password, "password");
        if (source.kind() == CredentialSourceKind.SAVE_TO_HOST
                && authenticationType == AuthenticationType.PASSWORD
                && password.isEmpty()) {
            return Optional.of(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f79c2da2bb2c6440"));
        }
        return Optional.empty();
    }

    static double formFieldLeft() {
        return FORM_LABEL_WIDTH + FORM_FIELD_GAP;
    }

    static HBox fileSelectionRow(TextField field, Button button) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(button, "button");
        field.setMinHeight(FORM_CONTROL_HEIGHT);
        field.setPrefHeight(FORM_CONTROL_HEIGHT);
        field.setMaxHeight(FORM_CONTROL_HEIGHT);
        button.setMinHeight(FORM_CONTROL_HEIGHT);
        button.setPrefHeight(FORM_CONTROL_HEIGHT);
        button.setMaxHeight(FORM_CONTROL_HEIGHT);
        HBox row = new HBox(6.0, field, button);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFillHeight(false);
        row.setMinHeight(FORM_CONTROL_HEIGHT);
        row.setPrefHeight(FORM_CONTROL_HEIGHT);
        row.setMaxHeight(FORM_CONTROL_HEIGHT);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    static List<String> formLabels() {
        return List.of(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.bc96b2e90db406b4"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.6c49c1f947d107d1"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.f1ed5572ce59b3b4"), com.ynpe.fastremote.i18n.Texts.text("form.targetHost"), com.ynpe.fastremote.i18n.Texts.text("form.connectionPort"), com.ynpe.fastremote.i18n.Texts.text("form.loginUser"),
                com.ynpe.fastremote.i18n.Texts.text("form.compatibilityMode"), com.ynpe.fastremote.i18n.Texts.text("form.credentialSource"), com.ynpe.fastremote.i18n.Texts.text("form.authenticationMethod"), com.ynpe.fastremote.i18n.Texts.text("form.hostPassword"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.69eb41708bcbe92c"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.13610905c385728f"), com.ynpe.fastremote.i18n.Texts.text("form.vaultCredential"), com.ynpe.fastremote.i18n.Texts.text("form.connectionPath"));
    }

    static double formHeight(int rowCount) {
        return rowCount <= 0 ? 0.0
                : rowCount * FORM_ROW_HEIGHT + (rowCount - 1) * FORM_ROW_GAP;
    }

    private static AnchorPane compactForm(int rowCount) {
        AnchorPane form = new AnchorPane();
        form.getStyleClass().add("host-editor-fields-shell");
        double height = formHeight(rowCount);
        form.setMinHeight(height);
        form.setPrefHeight(height);
        form.setMaxHeight(height);
        return form;
    }

    private static void addFullRow(AnchorPane form, int row, String text, Region field) {
        FormLabel label = formLabel(text);
        prepareField(field, FORM_FIELD_MIN_WIDTH * 2.0);
        AnchorPane.setLeftAnchor(label, 0.0);
        AnchorPane.setTopAnchor(label, rowTop(row) + labelTop());
        AnchorPane.setLeftAnchor(field, formFieldLeft());
        AnchorPane.setRightAnchor(field, 0.0);
        AnchorPane.setTopAnchor(field, rowTop(row) + controlTop());
        form.getChildren().addAll(label, field);
    }

    private static void addTwoColumnRow(
            AnchorPane form, int row, String leftText, Region leftField, String rightText, Region rightField) {
        GridPane rowPane = new GridPane();
        rowPane.setHgap(FORM_COLUMN_GAP);
        ColumnConstraints leftColumn = new ColumnConstraints();
        leftColumn.setPercentWidth(50.0);
        leftColumn.setMinWidth(FORM_CELL_MIN_WIDTH);
        ColumnConstraints rightColumn = new ColumnConstraints();
        rightColumn.setPercentWidth(50.0);
        rightColumn.setMinWidth(FORM_CELL_MIN_WIDTH);
        rowPane.getColumnConstraints().setAll(leftColumn, rightColumn);
        rowPane.setMinWidth(FORM_CELL_MIN_WIDTH * 2.0 + FORM_COLUMN_GAP);
        rowPane.add(formCell(leftText, leftField), 0, 0);
        rowPane.add(formCell(rightText, rightField), 1, 0);
        AnchorPane.setLeftAnchor(rowPane, 0.0);
        AnchorPane.setRightAnchor(rowPane, 0.0);
        AnchorPane.setTopAnchor(rowPane, rowTop(row));
        form.getChildren().add(rowPane);
    }

    private static AnchorPane formCell(String text, Region field) {
        AnchorPane cell = new AnchorPane();
        cell.setMinHeight(FORM_ROW_HEIGHT);
        cell.setPrefHeight(FORM_ROW_HEIGHT);
        cell.setMinWidth(FORM_CELL_MIN_WIDTH);
        FormLabel label = formLabel(text);
        prepareField(field, FORM_FIELD_MIN_WIDTH);
        AnchorPane.setLeftAnchor(label, 0.0);
        AnchorPane.setTopAnchor(label, labelTop());
        AnchorPane.setLeftAnchor(field, formFieldLeft());
        AnchorPane.setRightAnchor(field, 0.0);
        AnchorPane.setTopAnchor(field, controlTop());
        cell.getChildren().addAll(label, field);
        return cell;
    }

    private static void addFullWidthNode(AnchorPane form, int row, Region node) {
        node.setMinHeight(FORM_ROW_HEIGHT);
        node.setPrefHeight(FORM_ROW_HEIGHT);
        if (node instanceof Label label) label.setAlignment(Pos.CENTER_LEFT);
        AnchorPane.setLeftAnchor(node, 0.0);
        AnchorPane.setRightAnchor(node, 0.0);
        AnchorPane.setTopAnchor(node, rowTop(row));
        form.getChildren().add(node);
    }

    private static void prepareField(Region field, double minimumWidth) {
        field.setMinWidth(minimumWidth);
        field.setMinHeight(FORM_CONTROL_HEIGHT);
        field.setPrefHeight(FORM_CONTROL_HEIGHT);
        field.setMaxHeight(FORM_CONTROL_HEIGHT);
        if (field instanceof javafx.scene.control.Control control) {
            control.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private static FormLabel formLabel(String text) {
        return new FormLabel(text, FORM_LABEL_WIDTH, FORM_LABEL_HEIGHT);
    }

    private static double rowTop(int row) {
        return row * (FORM_ROW_HEIGHT + FORM_ROW_GAP);
    }

    private static double controlTop() {
        return (FORM_ROW_HEIGHT - FORM_CONTROL_HEIGHT) / 2.0;
    }

    private static double labelTop() {
        return (FORM_ROW_HEIGHT - FORM_LABEL_HEIGHT) / 2.0;
    }

    private static Label sectionTitle(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("host-editor-title");
        return title;
    }

    private static List<GroupChoice> groupChoices(List<HostGroup> groups) {
        List<GroupChoice> choices = new ArrayList<>();
        choices.add(new GroupChoice(Optional.empty(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.715c5e677336b16f")));
        List<HostGroup> roots = groups.stream().filter(group -> group.parentId().isEmpty())
                .sorted(Comparator.comparingInt(HostGroup::sortOrder).thenComparing(HostGroup::name)).toList();
        for (HostGroup root : roots) {
            choices.add(new GroupChoice(Optional.of(root.id()), root.name()));
            groups.stream().filter(group -> group.parentId().equals(Optional.of(root.id())))
                    .sorted(Comparator.comparingInt(HostGroup::sortOrder).thenComparing(HostGroup::name))
                    .forEach(child -> choices.add(new GroupChoice(
                            Optional.of(child.id()), root.name() + " / " + child.name())));
        }
        return choices;
    }

    private static List<IconChoice> iconChoices() {
        return List.of(
                new IconChoice(HostIcon.SERVER, com.ynpe.fastremote.i18n.Texts.text("legacy.java.2388cbc6a486af9f")), new IconChoice(HostIcon.LINUX, com.ynpe.fastremote.i18n.Texts.text("legacy.java.3ea3da471c77b324")),
                new IconChoice(HostIcon.WINDOWS, com.ynpe.fastremote.i18n.Texts.text("legacy.java.c5611b487608b58d")),
                new IconChoice(HostIcon.FREEBSD, com.ynpe.fastremote.i18n.Texts.text("legacy.java.86b024d92d1cc1a2")),
                new IconChoice(HostIcon.UNIX, com.ynpe.fastremote.i18n.Texts.text("legacy.java.af69b32c78f5cafa")), new IconChoice(HostIcon.MACOS, com.ynpe.fastremote.i18n.Texts.text("legacy.java.bbd8c228398189aa")),
                new IconChoice(HostIcon.DATABASE, com.ynpe.fastremote.i18n.Texts.text("legacy.java.263200d7d529b522")), new IconChoice(HostIcon.KUBERNETES, "Kubernetes"),
                new IconChoice(HostIcon.CONTAINER, com.ynpe.fastremote.i18n.Texts.text("legacy.java.8f4e8447912daeb0")), new IconChoice(HostIcon.CLOUD, com.ynpe.fastremote.i18n.Texts.text("legacy.java.27e1fc8693f555ed")),
                new IconChoice(HostIcon.NETWORK, com.ynpe.fastremote.i18n.Texts.text("legacy.java.3e973a71dac32438")), new IconChoice(HostIcon.STORAGE, com.ynpe.fastremote.i18n.Texts.text("legacy.java.6d93fe2a3f582bab")),
                new IconChoice(HostIcon.WORKSTATION, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d85c0d81b672523c")), new IconChoice(HostIcon.ROUTER, com.ynpe.fastremote.i18n.Texts.text("legacy.java.e8c68b5e46c67427")),
                new IconChoice(HostIcon.FIREWALL, com.ynpe.fastremote.i18n.Texts.text("legacy.java.eee30e9d97ca1e61")));
    }

    private static ListCell<IconChoice> iconCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(IconChoice choice, boolean empty) {
                super.updateItem(choice, empty);
                setText(empty || choice == null ? null : choice.label());
                setGraphic(empty || choice == null ? null : hostIcon(choice.icon()));
                setGraphicTextGap(9);
                setAccessibleText(empty || choice == null ? null : choice.label());
                setTooltip(empty || choice == null ? null : new Tooltip(choice.label()));
            }
        };
    }

    private static ListCell<AuthenticationType> authenticationCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(AuthenticationType authentication, boolean empty) {
                super.updateItem(authentication, empty);
                setText(empty || authentication == null ? null : switch (authentication) {
                    case PASSWORD -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.a621ab606db2a11f");
                    case PRIVATE_KEY -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.7ea96a871de9fe44");
                });
            }
        };
    }

    private static LucideIconView hostIcon(HostIcon icon) {
        LucideIconView graphic = new LucideIconView(HostIconGraphics.icon(icon));
        graphic.getStyleClass().setAll("catalog-item-icon", "catalog-host-icon");
        return graphic;
    }

    static int nextSortOrder(List<SavedHost> hosts, Optional<GroupId> groupId) {
        return hosts.stream().filter(host -> host.groupId().equals(groupId))
                .mapToInt(SavedHost::sortOrder).max().orElse(-10) + 10;
    }

    static void validateJumpSelection(
            Optional<com.ynpe.fastremote.domain.catalog.HostId> editedHostId,
            Optional<com.ynpe.fastremote.domain.catalog.HostId> jumpHostId,
            List<SavedHost> hosts) {
        if (jumpHostId.isEmpty()) return;
        java.util.Map<com.ynpe.fastremote.domain.catalog.HostId, SavedHost> byId = hosts.stream()
                .collect(java.util.stream.Collectors.toMap(SavedHost::id, java.util.function.Function.identity()));
        java.util.Set<com.ynpe.fastremote.domain.catalog.HostId> visited = new java.util.HashSet<>();
        editedHostId.ifPresent(visited::add);
        com.ynpe.fastremote.domain.catalog.HostId current = jumpHostId.orElseThrow();
        for (int depth = 0; depth < 5; depth++) {
            if (!visited.add(current)) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.aed4b72375fe23c6"));
            SavedHost hop = byId.get(current);
            if (hop == null) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.babb5bf9f281a4e2"));
            if (hop.jumpHostId().isEmpty()) return;
            current = hop.jumpHostId().orElseThrow();
        }
        throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1d017366e7b41936"));
    }

    static List<CredentialSource> newHostCredentialSources() {
        return List.of(CredentialSource.save(), CredentialSource.prompt(), CredentialSource.library());
    }

    private static int parsePort(TextField field) {
        try {
            return FormValidation.value(field, () -> new Port(Integer.parseInt(required(field, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d1192e27c3d45da3")))).value());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.49df3c993e2fc8a0"), invalid);
        }
    }

    private static String required(TextField field, String message) {
        String value = field.getText().strip();
        FormValidation.require(field, !value.isEmpty(), message);
        return value;
    }

    record HostRequest(SavedHost host, Optional<CredentialDraft> credential) {
        HostRequest {
            Objects.requireNonNull(host, "host");
            credential = Objects.requireNonNull(credential, "credential");
        }
    }

    record ConnectionTestRequest(
            ConnectionProfile profile,
            Optional<HostId> jumpHostId,
            Optional<SecretId> credentialId,
            Optional<CredentialDraft> credential) implements AutoCloseable {
        ConnectionTestRequest {
            Objects.requireNonNull(profile, "profile");
            jumpHostId = Objects.requireNonNull(jumpHostId, "jumpHostId");
            credentialId = Objects.requireNonNull(credentialId, "credentialId");
            credential = Objects.requireNonNull(credential, "credential");
            if (credentialId.isPresent() == credential.isPresent()) {
                throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6ff586f69e9dcc20"));
            }
        }

        @Override
        public void close() {
            credential.ifPresent(CredentialDraft::close);
        }
    }

    @FunctionalInterface
    interface ConnectionTester {
        ConnectionTestAttempt test(
                Window owner, ConnectionTestRequest request,
                java.util.function.Consumer<ConnectionProgress> progress);
    }

    enum Mode { CREATE, EDIT, COPY }
    private enum TestTone { WORKING, SUCCESS, ERROR }

    private record GroupChoice(Optional<GroupId> id, String label) {
        @Override public String toString() { return label; }
    }

    private record IconChoice(HostIcon icon, String label) {
        @Override public String toString() { return label; }
    }

    enum CredentialSourceKind { SAVE_TO_HOST, PROMPT, LIBRARY, CURRENT_HOST }

    record CredentialSource(CredentialSourceKind kind, Optional<SecretId> referenceId, String label) {
        CredentialSource {
            Objects.requireNonNull(kind, "kind");
            referenceId = Objects.requireNonNull(referenceId, "referenceId");
            label = Objects.requireNonNull(label, "label");
        }

        static CredentialSource save() {
            return new CredentialSource(
                    CredentialSourceKind.SAVE_TO_HOST, Optional.empty(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.937877c44dbfd13a"));
        }

        static CredentialSource prompt() {
            return new CredentialSource(CredentialSourceKind.PROMPT, Optional.empty(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.b9eb9483b682a64e"));
        }

        static CredentialSource library() {
            return new CredentialSource(CredentialSourceKind.LIBRARY, Optional.empty(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.8c7ab4e04a0d22ea"));
        }

        static CredentialSource current(SecretId id) {
            return new CredentialSource(
                    CredentialSourceKind.CURRENT_HOST, Optional.of(id), com.ynpe.fastremote.i18n.Texts.text("legacy.java.84c52ac10f9b53e0"));
        }

        @Override public String toString() { return label; }
    }
}
