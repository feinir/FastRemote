// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.UiFailureMessages.messageFor;
import static com.ynpe.fastremote.ui.UiFailureMessages.unwrap;
import static com.ynpe.fastremote.ui.LibraryMutationSequence.runDeleteOperations;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.snippet.SnippetVariable;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroup;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroupId;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.application.Platform;

final class VariableLibraryEditor {
    private final SnippetWorkflow snippetWorkflow;
    private final Supplier<SnippetSnapshot> snapshot;
    private final LibraryEditorActions actions;

    VariableLibraryEditor(SnippetWorkflow workflow, Supplier<SnippetSnapshot> snapshot, LibraryEditorActions actions) {
        this.snippetWorkflow = workflow;
        this.snapshot = snapshot;
        this.actions = actions;
    }

    void showVariableEditor(
            SnippetVariable existing, Optional<SnippetVariableGroupId> initialGroup) {
        VariableDialogs.edit(actions.owner(), existing, initialGroup,
                        snapshot.get(), this::nextVariableOrder)
                .ifPresent(variable -> snippetWorkflow.saveVariable(variable).whenComplete((saved, error) ->
                        Platform.runLater(() -> {
                            if (error != null) actions.status(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.a16afb7f17b939ad")), true);
                            else { actions.status(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f9b981e17960d34d") + saved.name(), false); actions.refresh(); }
                        })));
    }

    int nextVariableOrder(Optional<SnippetVariableGroupId> groupId) {
        return snapshot.get().variables().stream().filter(variable -> variable.groupId().equals(groupId))
                .mapToInt(SnippetVariable::sortOrder).max().orElse(-10) + 10;
    }

    void showVariableGroupEditor(SnippetVariableGroup existing) {
        GroupEditorDialog.showFlat(actions.owner(),
                existing == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.8d2d1a8e8380bdb5") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.297a7415c0541d99"),
                existing == null ? "" : existing.name(), existing == null ? GroupColor.BLUE : existing.color())
                .ifPresent(result -> {
                    int order = existing == null ? snapshot.get().variableGroups().stream()
                            .mapToInt(SnippetVariableGroup::sortOrder).max().orElse(-10) + 10 : existing.sortOrder();
                    SnippetVariableGroup group = new SnippetVariableGroup(
                            existing == null ? SnippetVariableGroupId.create() : existing.id(),
                            result.name(), order, result.color());
                    snippetWorkflow.saveVariableGroup(group).whenComplete((saved, error) -> Platform.runLater(() -> {
                        if (error != null) actions.status(messageFor(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d4eb9dce43e27d1e")), true);
                        else { actions.status(com.ynpe.fastremote.i18n.Texts.text("legacy.java.85588d3ef7957bba") + saved.name(), false); actions.refresh(); }
                    }));
                });
    }

}
