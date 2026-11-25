// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import java.util.List;
import java.util.Objects;

public record AiAssistantReply(String text, String reasoningSummary, List<AiToolExecution> toolExecutions) {
    public AiAssistantReply {
        text = Objects.requireNonNull(text, "text");
        reasoningSummary = Objects.requireNonNull(reasoningSummary, "reasoningSummary");
        toolExecutions = List.copyOf(Objects.requireNonNull(toolExecutions, "toolExecutions"));
    }

    public AiAssistantReply(String text, List<AiToolExecution> toolExecutions) {
        this(text, "", toolExecutions);
    }
}
