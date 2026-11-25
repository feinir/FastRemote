// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import java.util.Objects;
import java.util.Optional;

public record AiInferenceOptions(Optional<AiReasoningEffort> reasoningEffort, boolean showReasoningSummary) {
    public static final AiInferenceOptions DEFAULT = new AiInferenceOptions(Optional.empty(), false);

    public AiInferenceOptions {
        reasoningEffort = Objects.requireNonNull(reasoningEffort, "reasoningEffort");
    }
}
