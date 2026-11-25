// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public record SnippetVariable(
        SnippetVariableId id,
        Optional<SnippetVariableGroupId> groupId,
        String name,
        String variableName,
        SnippetVariableType type,
        String delimiter,
        String value,
        String description,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt) {
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    public SnippetVariable {
        Objects.requireNonNull(id, "id");
        groupId = Objects.requireNonNull(groupId, "groupId");
        name = Objects.requireNonNull(name, "name").strip();
        variableName = requireValidVariableName(variableName);
        Objects.requireNonNull(type, "type");
        delimiter = Objects.requireNonNull(delimiter, "delimiter");
        value = Objects.requireNonNull(value, "value");
        description = Objects.requireNonNull(description, "description").strip();
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (name.isEmpty() || name.length() > 255) throw new IllegalArgumentException("变量名称长度必须为 1 到 255");
        if (type == SnippetVariableType.SINGLE && !delimiter.isEmpty()) {
            throw new IllegalArgumentException("单变量的分隔符必须为空");
        }
        if (type == SnippetVariableType.ARRAY && delimiter.isEmpty()) {
            throw new IllegalArgumentException("数组变量必须设置分隔符");
        }
        if (delimiter.length() > 64 || delimiter.indexOf('\r') >= 0 || delimiter.indexOf('\n') >= 0
                || delimiter.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("分隔符不能超过 64 个字符，且不能包含换行或 NUL");
        }
        if (value.length() > 64 * 1024) throw new IllegalArgumentException("变量值不能超过 64 KiB");
        if (description.length() > 1_024) throw new IllegalArgumentException("变量说明不能超过 1024 个字符");
        if (sortOrder < 0) throw new IllegalArgumentException("sortOrder must not be negative");
    }

    public List<String> expandedValues() {
        return type == SnippetVariableType.SINGLE
                ? List.of(value)
                : List.of(value.split(Pattern.quote(delimiter), -1));
    }

    public String indexedValue(int index) {
        if (index < 0) throw new SnippetTemplateException("变量索引不能小于 0：" + variableName);
        if (index == 0) return value;
        if (type == SnippetVariableType.SINGLE) {
            throw new SnippetTemplateException("单变量不支持 index>0：" + variableName);
        }
        List<String> values = expandedValues();
        if (index > values.size()) {
            throw new SnippetTemplateException(
                    "变量索引越界：" + variableName + " 的 index=" + index + "，数组长度=" + values.size());
        }
        return values.get(index - 1);
    }

    static String requireValidVariableName(String value) {
        String checked = Objects.requireNonNull(value, "variableName").strip();
        if (!VARIABLE_NAME.matcher(checked).matches()) {
            throw new IllegalArgumentException("变量名必须匹配 [A-Za-z_][A-Za-z0-9_]{0,63}");
        }
        return checked;
    }
}
