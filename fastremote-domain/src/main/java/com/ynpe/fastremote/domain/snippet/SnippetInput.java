// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import java.util.Objects;
import java.util.Optional;

public record SnippetInput(
        String name,
        Optional<String> defaultValue,
        Optional<String> variableName,
        boolean required) {
    public SnippetInput {
        name = Objects.requireNonNull(name, "name").strip();
        defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        variableName = Objects.requireNonNull(variableName, "variableName")
                .map(SnippetVariable::requireValidVariableName);
        if (name.isEmpty() || name.length() > 64) throw new SnippetTemplateException("输入项名称长度必须为 1 到 64");
    }
}
