// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.UiFailureMessages.messageFor;
import static com.ynpe.fastremote.ui.UiFailureMessages.unwrap;
import static com.ynpe.fastremote.ui.LibraryMutationSequence.runDeleteOperations;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.vault.SecretId;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import com.ynpe.fastremote.domain.credential.CredentialGroup;
import com.ynpe.fastremote.domain.credential.CredentialGroupId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

final class CredentialLibraryEditor {
    private final HostCatalogWorkflow catalogWorkflow;
    private final Supplier<HostCatalogSnapshot> snapshot;
    private final Actions actions;
    interface Actions extends LibraryEditorActions {
        void manageVault();
        CompletionStage<ResolvedCredential> readSavedCredential(Window owner, SecretId id);
    }

    CredentialLibraryEditor(HostCatalogWorkflow workflow, Supplier<HostCatalogSnapshot> snapshot, Actions actions) {
        this.catalogWorkflow = workflow;
        this.snapshot = snapshot;
        this.actions = actions;
    }

    void showCredentialGroupEditor(CredentialGroup existing) {
        GroupEditorDialog.showFlat(actions.owner(), existing == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.751497401ea494fd") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.93caacbe48d5dfcb"),
                existing == null ? "" : existing.name(), existing == null ? GroupColor.BLUE : existing.color()).ifPresent(result -> {
            int order = existing == null ? snapshot.get().credentialGroups().stream().mapToInt(CredentialGroup::sortOrder)
                    .max().orElse(-10) + 10 : existing.sortOrder();
            CredentialGroup group = new CredentialGroup(existing == null ? CredentialGroupId.random() : existing.id(),
                    result.name(), order, result.color());
            catalogWorkflow.saveCredentialGroup(group).whenComplete((saved, error) -> Platform.runLater(actions::refresh));
        });
    }

    void createReusableCredentialAt(Optional<CredentialGroupId> groupId) {
        if (snapshot.get().vaultStatus() != VaultStatus.UNLOCKED) {
            actions.status(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a73efdadb5a67e69"), true);
            actions.manageVault();
            return;
        }
        CredentialEditorDialog.showCreate(actions.owner()).ifPresent(request ->
                catalogWorkflow.createReusableCredential(
                        request.label(), request.username(), request.replacement().orElseThrow())
                        .whenComplete((credential, error) -> Platform.runLater(() -> {
                            if (error != null) actions.status(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.5c7c838a79ceee9d")), true);
                            else if (groupId.isPresent()) {
                                int order = snapshot.get().credentials().stream().filter(value -> value.groupId().equals(groupId))
                                        .mapToInt(GlobalCredential::sortOrder).max().orElse(-10) + 10;
                                catalogWorkflow.moveReusableCredential(credential, groupId, order)
                                        .whenComplete((moved, moveError) -> Platform.runLater(() -> {
                                            if (moveError != null) actions.status(messageFor(moveError, com.ynpe.fastremote.i18n.Texts.text("legacy.java.6e7ad9af0827c098")), true);
                                            else actions.status(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3d3f84de842363b2") + moved.label(), false);
                                            actions.refresh();
                                        }));
                            } else {
                                actions.status(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3d3f84de842363b2") + credential.label(), false);
                                actions.refresh();
                            }
                        })));
    }

    void editReusableCredential(GlobalCredential credential) {
        CredentialEditorDialog.showEdit(actions.owner(), credential, actions::readSavedCredential).ifPresent(request ->
                catalogWorkflow.updateReusableCredential(
                        credential, request.label(), request.username(), request.replacement())
                        .whenComplete((updated, error) -> Platform.runLater(() -> {
                            if (error != null) actions.status(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.68c5ad197b2364a7")), true);
                            else {
                                actions.status(com.ynpe.fastremote.i18n.Texts.text("legacy.java.17c1581d0527816e") + updated.label(), false);
                                actions.refresh();
                            }
                        })));
    }

    void deleteReusableCredentials(List<GlobalCredential> credentials, Window owner) {
        if (credentials.isEmpty()) return;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(owner);
        confirmation.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5da9db927b31092a"));
        confirmation.setHeaderText(credentials.size() == 1
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.2ac7fd2fd8db298f") + credentials.getFirst().label() + "”？"
                : com.ynpe.fastremote.i18n.Texts.text("legacy.java.f2c699b7c0d1072f") + credentials.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.751390ce5de70057"));
        confirmation.setContentText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ecc6b06072a10971"));
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        List<Supplier<CompletionStage<Void>>> operations = credentials.stream()
                .<Supplier<CompletionStage<Void>>>map(credential ->
                        () -> catalogWorkflow.deleteReusableCredential(credential))
                .toList();
        runDeleteOperations(operations).whenComplete((errors, error) -> Platform.runLater(() -> {
            List<Throwable> failures = error == null ? errors : List.of(unwrap(error));
            int deleted = credentials.size() - failures.size();
            if (failures.isEmpty()) {
                actions.status(credentials.size() == 1
                        ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.1f235f9a073a84ce") + credentials.getFirst().label()
                        : com.ynpe.fastremote.i18n.Texts.text("legacy.java.4f616c9f960e9818") + deleted + com.ynpe.fastremote.i18n.Texts.text("legacy.java.ed60aa48e07d596e"), false);
            } else {
                actions.status(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4f616c9f960e9818") + deleted + com.ynpe.fastremote.i18n.Texts.text("legacy.java.b44515a50c75c270") + failures.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.3272a917194f77a0")
                        + messageFor(failures.getFirst(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.94c9d84a78f63e1c")), true);
            }
            actions.refresh();
        }));
    }

}
