// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Model-picker capabilities reported by the active provider. */
public record AiModelCapabilities(
        String model,
        List<AiReasoningEffort> supportedReasoningEfforts,
        Optional<AiReasoningEffort> defaultReasoningEffort,
        List<AiInputModality> inputModalities) {
    public AiModelCapabilities(String model, List<AiReasoningEffort> supportedReasoningEfforts,
            Optional<AiReasoningEffort> defaultReasoningEffort) {
        this(model, supportedReasoningEfforts, defaultReasoningEffort, List.of(AiInputModality.TEXT));
    }

    public AiModelCapabilities {
        model = Objects.requireNonNull(model, "model").strip();
        if (model.isEmpty()) throw new IllegalArgumentException("Model id is empty");
        supportedReasoningEfforts = List.copyOf(Objects.requireNonNull(
                supportedReasoningEfforts, "supportedReasoningEfforts"));
        defaultReasoningEffort = Objects.requireNonNull(defaultReasoningEffort, "defaultReasoningEffort")
                .filter(supportedReasoningEfforts::contains);
        inputModalities = List.copyOf(Objects.requireNonNull(inputModalities, "inputModalities"));
        if (inputModalities.stream().distinct().count() != inputModalities.size()) {
            throw new IllegalArgumentException("Duplicate AI input modality");
        }
    }

    public static AiModelCapabilities unknown(String model) {
        return new AiModelCapabilities(model, List.of(), Optional.empty(), List.of(AiInputModality.TEXT));
    }
}
