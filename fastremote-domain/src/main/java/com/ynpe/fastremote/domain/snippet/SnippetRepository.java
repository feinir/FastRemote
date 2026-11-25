// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import java.io.IOException;
import java.util.List;

public interface SnippetRepository {
    List<SnippetGroup> listGroups() throws IOException;

    List<Snippet> listSnippets() throws IOException;

    void saveGroup(SnippetGroup group) throws IOException;

    void deleteGroup(SnippetGroupId id) throws IOException;

    void saveSnippet(Snippet snippet) throws IOException;

    void saveSnippets(List<Snippet> snippets) throws IOException;

    default BuiltinSnippetImportResult importBuiltinSnippets(
            String languageTag, List<BuiltinSnippetGroup> groups, List<BuiltinSnippet> snippets)
            throws IOException {
        return importBuiltinSnippets(languageTag, BuiltinSnippetConflictPolicy.SKIP, groups, snippets);
    }

    default BuiltinSnippetImportResult importBuiltinSnippets(
            String languageTag,
            BuiltinSnippetConflictPolicy conflictPolicy,
            List<BuiltinSnippetGroup> groups,
            List<BuiltinSnippet> snippets) throws IOException {
        throw new UnsupportedOperationException("Built-in command import is not supported");
    }

    void deleteSnippet(SnippetId id) throws IOException;

    List<SnippetVariableGroup> listVariableGroups() throws IOException;

    List<SnippetVariable> listVariables() throws IOException;

    void saveVariableGroup(SnippetVariableGroup group) throws IOException;

    void deleteVariableGroup(SnippetVariableGroupId id) throws IOException;

    void saveVariable(SnippetVariable variable) throws IOException;

    void saveVariables(List<SnippetVariable> variables) throws IOException;

    void saveLibraryImport(
            List<SnippetGroup> groups,
            List<Snippet> snippets,
            List<SnippetVariableGroup> variableGroups,
            List<SnippetVariable> variables) throws IOException;

    void deleteVariable(SnippetVariableId id) throws IOException;
}
