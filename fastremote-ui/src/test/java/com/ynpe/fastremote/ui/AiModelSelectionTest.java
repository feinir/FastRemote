// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.ynpe.fastremote.domain.ai.*;
import java.util.*;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AiModelSelectionTest {
    private static ModelProviderConfig provider(String id) {
        return new ModelProviderConfig(id, id, ModelProviderType.OPENAI_COMPATIBLE,
                URI.create("https://example.com"), "", Optional.empty(), false);
    }
    private static AiSettings settings() {
        var defaults = AiSettings.defaults();
        return new AiSettings(List.of(provider("first"), provider("second")), Optional.empty(),
                defaults.builtInPermissions(), defaults.mcpPermissions(), List.of(), defaults.mcpServer());
    }
    private static AiChatSession chat(String provider, String model) {
        return new AiChatSession(UUID.randomUUID(), "Chat", Instant.EPOCH, Instant.EPOCH, 0,
                Optional.ofNullable(provider), Optional.ofNullable(model));
    }
    @Test void firstLaunchChoosesFirstConfiguredProvider() {
        assertEquals("first", AiModelSelection.restore(settings(), chat(null, null), List.of())
                .orElseThrow().provider().id());
        assertEquals("model-a", AiModelSelection.availableModel("", "", List.of("model-a", "model-b")));
    }
    @Test void emptyNewChatRestoresLastSavedModelAcrossRestart() {
        var saved = chat("second", "chosen");
        var restored = AiModelSelection.restore(settings(), chat(null, null), List.of(chat(null, null), saved))
                .orElseThrow();
        assertEquals("second", restored.provider().id());
        assertEquals("chosen", restored.model());
    }
    @Test void removedProviderAndModelFallBackToAvailableConfiguration() {
        assertEquals("first", AiModelSelection.restore(settings(), chat("removed", "gone"), List.of())
                .orElseThrow().provider().id());
        assertEquals("available", AiModelSelection.availableModel("gone", "", List.of("available")));
        assertEquals("", AiModelSelection.availableModel("gone", "gone", List.of()));
    }
    @Test void savedModelHasPriorityOverConfiguredDefault() {
        assertEquals("saved", AiModelSelection.availableModel("saved", "default", List.of("default", "saved")));
    }
}
