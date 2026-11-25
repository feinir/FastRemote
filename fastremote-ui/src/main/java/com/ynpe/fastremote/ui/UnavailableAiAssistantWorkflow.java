// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.ai.AiAssistantReply;
import com.ynpe.fastremote.ai.AiConversationMessage;
import com.ynpe.fastremote.domain.ai.AiSettings;
import com.ynpe.fastremote.domain.ai.AiChatMessage;
import com.ynpe.fastremote.domain.ai.AiChatSession;
import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.AiWorkspaceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class UnavailableAiAssistantWorkflow implements AiAssistantWorkflow {
    private static <T> CompletionStage<T> unavailable() {
        return CompletableFuture.failedFuture(new IllegalStateException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c00601bc4dbcaa06")));
    }
    @Override public CompletionStage<AiSettings> loadSettings() { return CompletableFuture.completedFuture(AiSettings.defaults()); }
    @Override public CompletionStage<AiSettings> saveSettings(AiSettings settings) { return unavailable(); }
    @Override public CompletionStage<ModelProviderConfig> saveProvider(ModelProviderConfig provider, Optional<char[]> key) {
        key.ifPresent(value -> java.util.Arrays.fill(value, '\0')); return unavailable();
    }
    @Override public CompletionStage<AiSettings> deleteProvider(String providerId) { return unavailable(); }
    @Override public CompletionStage<List<String>> discoverModels(String providerId) { return unavailable(); }
    @Override public CompletionStage<List<AiChatSession>> listChatSessions() {
        return CompletableFuture.completedFuture(List.of());
    }
    @Override public CompletionStage<AiChatSession> createChatSession() { return unavailable(); }
    @Override public CompletionStage<AiChatSession> updateChatModelSelection(
            UUID sessionId, Optional<String> providerId, Optional<String> model) { return unavailable(); }
    @Override public CompletionStage<AiChatTranscript> loadChatSession(UUID sessionId) { return unavailable(); }
    @Override public CompletionStage<AiChatSession> renameChatSession(UUID sessionId, String title) {
        return unavailable();
    }
    @Override public CompletionStage<AiChatSession> appendChatMessage(
            UUID sessionId, AiChatMessage.Role role, String content, Optional<String> newTitle) {
        return unavailable();
    }
    @Override public CompletionStage<Void> deleteChatSession(UUID sessionId) { return unavailable(); }
    @Override public CompletionStage<AiAssistantReply> send(String providerId, String model,
            List<AiConversationMessage> history,
            String prompt, AiWorkspaceContext workspaceContext,
            com.ynpe.fastremote.domain.ai.AiInferenceOptions inference) { return unavailable(); }
    @Override public CompletionStage<String> generateChatTitle(
            String providerId, String model, String userPrompt, String assistantAnswer) { return unavailable(); }
    @Override public CompletionStage<McpTokenResult> regenerateMcpToken() { return unavailable(); }
    @Override public CompletionStage<String> loadMcpToken() { return unavailable(); }
    @Override public CompletionStage<ChatGptAccount> loadChatGptAccount() { return unavailable(); }
    @Override public CompletionStage<ChatGptLoginStart> startChatGptLogin() { return unavailable(); }
    @Override public CompletionStage<ChatGptAccount> awaitChatGptLogin() { return unavailable(); }
    @Override public CompletionStage<Void> logoutChatGpt() { return unavailable(); }
    @Override public void close() { }
}
