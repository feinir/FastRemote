// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import java.util.List;
import java.util.Set;

public record SnippetTemplateAnalysis(List<SnippetInput> inputs, Set<String> variables, Set<String> sessionFields) {
    public SnippetTemplateAnalysis {
        inputs = List.copyOf(inputs);
        variables = Set.copyOf(variables);
        sessionFields = Set.copyOf(sessionFields);
    }
}
