// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.ai.AiChatSession;
import com.ynpe.fastremote.domain.ai.AiSettings;
import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import java.util.List;
import java.util.Optional;

final class AiModelSelection {
    record Selection(ModelProviderConfig provider, String model) { }

    static Optional<Selection> restore(AiSettings settings, AiChatSession current, List<AiChatSession> history) {
        if (current != null) {
            var own = fromSession(settings, current);
            if (own.isPresent()) return own;
        }
        for (var session : history) {
            var saved = fromSession(settings, session);
            if (saved.isPresent()) return saved;
        }
        return settings.providers().stream().findFirst()
                .map(provider -> new Selection(provider, provider.model()));
    }

    private static Optional<Selection> fromSession(AiSettings settings, AiChatSession session) {
        return session.providerId().flatMap(id -> settings.providers().stream()
                .filter(provider -> provider.id().equals(id)).findFirst())
                .filter(ignored -> session.model().filter(model -> !model.isBlank()).isPresent())
                .map(provider -> new Selection(provider, session.model().orElseThrow()));
    }

    static String availableModel(String saved, String configured, List<String> models) {
        if (models.contains(saved)) return saved;
        if (models.contains(configured)) return configured;
        return models.isEmpty() ? "" : models.getFirst();
    }
}
