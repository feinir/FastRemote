// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import java.util.Map;
import java.util.Objects;

public record SnippetTemplateContext(
        Map<String, String> inputs,
        Map<String, SnippetVariable> variables,
        Map<String, String> sessionFields) {
    public SnippetTemplateContext {
        inputs = Map.copyOf(Objects.requireNonNull(inputs, "inputs"));
        variables = Map.copyOf(Objects.requireNonNull(variables, "variables"));
        sessionFields = Map.copyOf(Objects.requireNonNull(sessionFields, "sessionFields"));
    }
}
