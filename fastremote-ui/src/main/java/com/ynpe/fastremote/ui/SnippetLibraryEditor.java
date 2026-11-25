// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.UiFailureMessages.messageFor;
import static com.ynpe.fastremote.ui.UiFailureMessages.unwrap;
import static com.ynpe.fastremote.ui.LibraryMutationSequence.runDeleteOperations;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.snippet.Snippet;
import com.ynpe.fastremote.domain.snippet.SnippetGroup;
import com.ynpe.fastremote.domain.snippet.SnippetGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetId;
import com.ynpe.fastremote.domain.snippet.SnippetTemplateEngine;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

final class SnippetLibraryEditor {
    private final SnippetWorkflow snippetWorkflow;
    private final Supplier<SnippetSnapshot> snapshot;
    private final LibraryEditorActions actions;
    private final SnippetTemplateEngine snippetTemplateEngine;

    SnippetLibraryEditor(SnippetWorkflow workflow, Supplier<SnippetSnapshot> snapshot, LibraryEditorActions actions, SnippetTemplateEngine templates) {
        this.snippetWorkflow = workflow;
        this.snapshot = snapshot;
        this.actions = actions;
        this.snippetTemplateEngine = templates;
    }

    void showSnippetGroupEditor(SnippetGroup existing) {
        GroupEditorDialog.showFlat(actions.owner(),
                existing == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.251a5300a581e964") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.a853449fcce6727b"),
                existing == null ? "" : existing.name(), existing == null ? GroupColor.BLUE : existing.color())
                .ifPresent(request -> {
            int order = existing == null ? snapshot.get().groups().stream().mapToInt(SnippetGroup::sortOrder)
                    .max().orElse(-10) + 10 : existing.sortOrder();
            SnippetGroup group = new SnippetGroup(
                    existing == null ? SnippetGroupId.create() : existing.id(), request.name(), order, request.color());
            snippetWorkflow.saveGroup(group).whenComplete((saved, error) -> Platform.runLater(() -> {
                if (error != null) actions.status(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.46483ab8972ebfc6")), true);
                else { actions.status(com.ynpe.fastremote.i18n.Texts.text("legacy.java.fb6fa1f322d681ba") + saved.name(), false); actions.refresh(); }
            }));
        });
    }

    void deleteSnippetGroup(SnippetGroup group) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, com.ynpe.fastremote.i18n.Texts.text("legacy.java.263bf6efbc1ac66c") + group.name() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.1d347b6120846c62"),
                ButtonType.CANCEL, ButtonType.OK);
        alert.initOwner(actions.owner());
        alert.setHeaderText(null);
        alert.showAndWait().filter(ButtonType.OK::equals).ifPresent(ignored ->
                snippetWorkflow.deleteGroup(group.id()).whenComplete((done, error) -> Platform.runLater(() -> {
                    if (error != null) actions.status(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.72df61a7d65e48c5")), true);
                    else { actions.status(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2072b86518528b89") + group.name(), false); actions.refresh(); }
                })));
    }

    void copySnippet(Snippet source) {
        Instant now = Instant.now();
        Snippet copy = new Snippet(SnippetId.create(), source.groupId(), source.name() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.fcbed84febef9e56"),
                source.description(), source.content(), nextSnippetOrder(source.groupId()), now, now);
        showSnippetEditor(copy, copy.groupId());
    }

    void showSnippetEditor(Snippet existing, Optional<SnippetGroupId> initialGroup) {
        SnippetDialogs.edit(actions.owner(), existing, initialGroup,
                snapshot.get(), snippetTemplateEngine, this::nextSnippetOrder)
                .ifPresent(snippet -> snippetWorkflow.saveSnippet(snippet)
                .whenComplete((saved, error) -> Platform.runLater(() -> {
                    if (error != null) actions.status(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.f1e0854524ad1d1b")), true);
                    else { actions.status(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2088eab33ae5eda0") + saved.name(), false); actions.refresh(); }
                })));
    }

    int nextSnippetOrder(Optional<SnippetGroupId> groupId) {
        return snapshot.get().snippets().stream().filter(snippet -> snippet.groupId().equals(groupId))
                .mapToInt(Snippet::sortOrder).max().orElse(-10) + 10;
    }

    void deleteSnippetSelection(List<Snippet> snippets, List<SnippetGroup> groups) {
        if (snippets.isEmpty() && groups.isEmpty()) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.4eb2f60c03a3de03") + (snippets.size() + groups.size()) + com.ynpe.fastremote.i18n.Texts.text("legacy.java.448ef751446fb42f"),
                ButtonType.CANCEL, ButtonType.OK);
        alert.initOwner(actions.owner()); alert.setHeaderText(null);
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        List<Supplier<CompletionStage<Void>>> operations = new ArrayList<>();
        snippets.forEach(value -> operations.add(() -> snippetWorkflow.deleteSnippet(value.id())));
        groups.forEach(value -> operations.add(() -> snippetWorkflow.deleteGroup(value.id())));
        runDeleteOperations(operations).whenComplete((errors, error) -> Platform.runLater(() -> {
            actions.refresh();
            if (error != null || (errors != null && !errors.isEmpty()))
                actions.status(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6b74dd64edb8f202"), true);
        }));
    }

    void deleteSnippets(List<Snippet> snippets) {
        if (snippets.isEmpty()) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, snippets.size() == 1
                ? com.ynpe.fastremote.i18n.Texts.text("snippet.deleteOneConfirm", snippets.getFirst().name())
                : com.ynpe.fastremote.i18n.Texts.text("legacy.java.2cebc9b0c4e6766c") + snippets.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.83c73f073bb0dadd"),
                ButtonType.CANCEL, ButtonType.OK);
        alert.initOwner(actions.owner());
        alert.setHeaderText(null);
        alert.showAndWait().filter(ButtonType.OK::equals).ifPresent(ignored ->
                runDeleteOperations(snippets.stream().<Supplier<CompletionStage<Void>>>map(snippet ->
                        () -> snippetWorkflow.deleteSnippet(snippet.id())).toList()).whenComplete((done, error) -> Platform.runLater(() -> {
                    if (error != null) actions.status(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.033506ec3aa31453")), true);
                    else { actions.status(com.ynpe.fastremote.i18n.Texts.text("legacy.java.dc40036150bca213"), false); actions.refresh(); }
                })));
    }

    void inspectAndImport(Path file, boolean variablesOnly) {
        CompletionStage<LibraryImportPreview> inspection = variablesOnly
                ? snippetWorkflow.inspectVariablesText(file) : snippetWorkflow.inspectText(file);
        inspection.whenComplete((preview, inspectError) -> Platform.runLater(() -> {
            if (inspectError != null) {
                actions.status(messageFor(inspectError, variablesOnly ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.7383542bce7fef1d") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.90437b2e9e788658")),
                        true);
                return;
            }
            Optional<ImportConflictPolicy> policy = importConflictPolicy(preview);
            if (policy.isEmpty()) return;
            CompletionStage<LibraryImportResult> importing = variablesOnly
                    ? snippetWorkflow.importVariablesText(file, policy.get())
                    : snippetWorkflow.importText(file, policy.get());
            importing.whenComplete((result, importError) -> Platform.runLater(() -> {
                if (importError != null) {
                    actions.status(messageFor(importError, variablesOnly ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.498676c38d857940") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.dec78b97a3973183")),
                            true);
                    return;
                }
                actions.status(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2a9010137d7ba142") + result.commandCount() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.78701ecdff86864b") + result.variableCount() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.7caa09ce6fd525d6"),
                        false);
                actions.refresh();
            }));
        }));
    }

    Optional<ImportConflictPolicy> importConflictPolicy(LibraryImportPreview preview) {
        if (!preview.hasConflicts()) return Optional.of(ImportConflictPolicy.OVERWRITE);
        ButtonType overwrite = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2ca7ea12db796310"), ButtonBar.ButtonData.YES);
        ButtonType skip = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.11153705f4024ddb"), ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.563947542629d422") + preview.commandConflictCount() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.616b8ee3f02d4bbf")
                        + preview.variableConflictCount() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.318b87d078e2f79f"),
                ButtonType.CANCEL, skip, overwrite);
        alert.initOwner(actions.owner());
        alert.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.69709a7083bc3475"));
        alert.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8bb595009cdbd4db"));
        ButtonType choice = alert.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == overwrite) return Optional.of(ImportConflictPolicy.OVERWRITE);
        if (choice == skip) return Optional.of(ImportConflictPolicy.SKIP);
        return Optional.empty();
    }

}
