// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.snippet.Snippet;
import com.ynpe.fastremote.domain.snippet.SnippetGroup;
import com.ynpe.fastremote.domain.snippet.SnippetVariable;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroup;
import java.util.List;

public record SnippetSnapshot(
        List<SnippetGroup> groups,
        List<Snippet> snippets,
        List<SnippetVariableGroup> variableGroups,
        List<SnippetVariable> variables) {
    public SnippetSnapshot {
        groups = List.copyOf(groups);
        snippets = List.copyOf(snippets);
        variableGroups = List.copyOf(variableGroups);
        variables = List.copyOf(variables);
    }

    public SnippetSnapshot(List<SnippetGroup> groups, List<Snippet> snippets, List<SnippetVariable> variables) {
        this(groups, snippets, List.of(), variables);
    }
}
