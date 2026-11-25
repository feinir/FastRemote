// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.ai.*;
import java.util.*;
import java.util.function.Consumer;
import com.ynpe.fastremote.ui.MainWindowController.AiTranscriptEntry;

/** FX-thread presentation and queue owned by one conversation, independent of the selected page. */
final class AiConversationRuntime {
        final UUID id;
        final List<com.ynpe.fastremote.ai.AiConversationMessage> conversation = new ArrayList<>();
        final Map<String, com.ynpe.fastremote.ai.AiImageInput> images = new HashMap<>();
        final List<AiTranscriptEntry> transcript = new ArrayList<>();
        final AiPromptQueue pending = new AiPromptQueue();
        UUID startingPromptId;
        UUID runId;
        boolean queueSaving;
        int queueWrites;
        final AiChatView view;
        boolean initialized;
        volatile boolean running;
        volatile boolean interruptRequested;
        boolean interruptInProgress;
        String draft = "";
        List<com.ynpe.fastremote.ai.AiImageInput> draftImages = List.of();
        ModelProviderConfig provider;
        String model;
        AiInferenceOptions inference;
        AiWorkspaceContext context;
        AiConversationRuntime(UUID id, Consumer<String> status, Consumer<com.ynpe.fastremote.ai.AiImageInput> imageOpen) {
            this.id = id;
            view = new AiChatView(status, imageId -> {
                var image = images.get(imageId);
                if (image != null) imageOpen.accept(image);
            });
        }
}
