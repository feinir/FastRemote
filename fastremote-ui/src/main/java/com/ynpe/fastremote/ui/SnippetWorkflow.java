// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.snippet.Snippet;
import com.ynpe.fastremote.domain.snippet.SnippetGroup;
import com.ynpe.fastremote.domain.snippet.SnippetGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetId;
import com.ynpe.fastremote.domain.snippet.SnippetVariable;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroup;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetVariableId;
import java.util.concurrent.CompletionStage;
import java.nio.file.Path;
import java.util.List;
import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippetImportResult;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippetConflictPolicy;

public interface SnippetWorkflow extends AutoCloseable {
    CompletionStage<SnippetSnapshot> load();

    default CompletionStage<BuiltinSnippetImportResult> importBuiltinCommands(InterfaceLanguage language) {
        return importBuiltinCommands(language, BuiltinSnippetConflictPolicy.SKIP);
    }

    CompletionStage<BuiltinSnippetImportResult> importBuiltinCommands(
            InterfaceLanguage language, BuiltinSnippetConflictPolicy conflictPolicy);

    List<InterfaceLanguage> builtinCommandLanguages();

    CompletionStage<SnippetGroup> saveGroup(SnippetGroup group);

    CompletionStage<Void> deleteGroup(SnippetGroupId id);

    CompletionStage<Snippet> saveSnippet(Snippet snippet);

    CompletionStage<LibraryImportPreview> inspectText(Path file);

    CompletionStage<LibraryImportResult> importText(Path file, ImportConflictPolicy conflictPolicy);

    CompletionStage<Integer> exportText(Path file);

    CompletionStage<Void> deleteSnippet(SnippetId id);

    CompletionStage<SnippetVariable> saveVariable(SnippetVariable variable);

    CompletionStage<SnippetVariableGroup> saveVariableGroup(SnippetVariableGroup group);

    CompletionStage<Void> deleteVariableGroup(SnippetVariableGroupId id);

    CompletionStage<LibraryImportPreview> inspectVariablesText(Path file);

    CompletionStage<LibraryImportResult> importVariablesText(Path file, ImportConflictPolicy conflictPolicy);

    CompletionStage<Integer> exportVariablesText(Path file);

    CompletionStage<Void> deleteVariable(SnippetVariableId id);

    @Override
    void close();
}
