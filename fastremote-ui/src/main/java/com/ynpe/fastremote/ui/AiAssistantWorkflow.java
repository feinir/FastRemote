// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.ai.AiAssistantReply;
import com.ynpe.fastremote.ai.AiConversationMessage;
import com.ynpe.fastremote.ai.AiImageInput;
import com.ynpe.fastremote.ai.AiStreamListener;
import com.ynpe.fastremote.domain.ai.AiSettings;
import com.ynpe.fastremote.domain.ai.AiChatMessage;
import com.ynpe.fastremote.domain.ai.AiChatImage;
import com.ynpe.fastremote.domain.ai.AiChatSession;
import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.AiInferenceOptions;
import com.ynpe.fastremote.domain.ai.AiModelCapabilities;
import com.ynpe.fastremote.domain.ai.AiWorkspaceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.net.URI;

public interface AiAssistantWorkflow extends AutoCloseable {
    enum SteeringResult { ACCEPTED, WAITING, NOT_RUNNING }
    default CompletionStage<SteeringResult> steer(UUID sessionId, com.ynpe.fastremote.ai.AiSteeringInput input) {
        return CompletableFuture.completedFuture(SteeringResult.NOT_RUNNING);
    }
    default CompletionStage<Void> savePending(UUID sessionId,
            List<com.ynpe.fastremote.domain.ai.AiPendingPrompt> prompts) {
        return prompts.isEmpty() ? CompletableFuture.completedFuture(null) :
                CompletableFuture.failedFuture(new UnsupportedOperationException("AI queue persistence is unavailable"));
    }
    CompletionStage<AiSettings> loadSettings();
    CompletionStage<AiSettings> saveSettings(AiSettings settings);
    CompletionStage<ModelProviderConfig> saveProvider(ModelProviderConfig provider, Optional<char[]> replacementApiKey);
    CompletionStage<AiSettings> deleteProvider(String providerId);
    CompletionStage<List<String>> discoverModels(String providerId);
    default CompletionStage<List<AiModelCapabilities>> discoverModelCapabilities(String providerId) {
        return discoverModels(providerId).thenApply(models -> models.stream()
                .map(AiModelCapabilities::unknown).toList());
    }
    CompletionStage<List<AiChatSession>> listChatSessions();
    CompletionStage<AiChatSession> createChatSession();
    CompletionStage<AiChatTranscript> loadChatSession(UUID sessionId);
    CompletionStage<AiChatSession> renameChatSession(UUID sessionId, String title);
    CompletionStage<AiChatSession> updateChatModelSelection(
            UUID sessionId, Optional<String> providerId, Optional<String> model);
    default CompletionStage<AiChatSession> updateAssociatedHost(UUID sessionId,
            Optional<com.ynpe.fastremote.domain.ai.AiHostAssociation> host) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("AI host association is unavailable"));
    }
    CompletionStage<AiChatSession> appendChatMessage(
            UUID sessionId, AiChatMessage.Role role, String content, Optional<String> newTitle);
    default CompletionStage<AiChatSession> appendChatMessage(
            UUID sessionId, AiChatMessage.Role role, String content, List<AiChatImage> images,
            Optional<String> newTitle) {
        if (!images.isEmpty()) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new UnsupportedOperationException("Chat image persistence is unavailable"));
        }
        return appendChatMessage(sessionId, role, content, newTitle);
    }
    CompletionStage<Void> deleteChatSession(UUID sessionId);
    default CompletionStage<List<AiChatSession>> setChatsPinned(List<UUID> ids, boolean pinned) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("AI chat pinning is unavailable"));
    }
    default CompletionStage<Void> deleteChatSessions(List<UUID> ids) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("AI bulk deletion is unavailable"));
    }
    CompletionStage<AiAssistantReply> send(
            String providerId, String model, List<AiConversationMessage> history,
            String prompt, AiWorkspaceContext workspaceContext, AiInferenceOptions inference);
    default CompletionStage<AiAssistantReply> sendStreaming(
            String providerId, String model, List<AiConversationMessage> history,
            String prompt, AiWorkspaceContext workspaceContext, AiInferenceOptions inference,
            AiStreamListener listener) {
        return send(providerId, model, history, prompt, workspaceContext, inference);
    }
    default CompletionStage<AiAssistantReply> sendStreaming(
            String providerId, String model, List<AiConversationMessage> history,
            String prompt, List<AiImageInput> images, AiWorkspaceContext workspaceContext,
            AiInferenceOptions inference, AiStreamListener listener) {
        if (!images.isEmpty()) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new UnsupportedOperationException("The selected model does not accept image input"));
        }
        return sendStreaming(providerId, model, history, prompt, workspaceContext, inference, listener);
    }
    default CompletionStage<AiAssistantReply> sendStreaming(UUID sessionId,
            String providerId, String model, List<AiConversationMessage> history, String prompt,
            List<AiImageInput> images, AiWorkspaceContext context, AiInferenceOptions inference, AiStreamListener listener) {
        return sendStreaming(providerId, model, history, prompt, images, context, inference, listener);
    }
    default CompletionStage<Void> cancelRequest(UUID sessionId) { return cancelActiveRequest(); }
    default CompletionStage<String> generateChatTitle(UUID sessionId,
            String providerId, String model, String prompt, String answer) {
        return generateChatTitle(providerId, model, prompt, answer);
    }
    default CompletionStage<Void> cancelActiveRequest() {
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
    CompletionStage<String> generateChatTitle(
            String providerId, String model, String userPrompt, String assistantAnswer);
    CompletionStage<McpTokenResult> regenerateMcpToken();
    CompletionStage<String> loadMcpToken();
    default CompletionStage<McpServerStatus> loadMcpServerStatus() {
        return CompletableFuture.completedFuture(McpServerStatus.stopped());
    }
    default TrafficSnapshot trafficSnapshot() { return new TrafficSnapshot(0L, 0L); }
    CompletionStage<ChatGptAccount> loadChatGptAccount();
    CompletionStage<ChatGptLoginStart> startChatGptLogin();
    CompletionStage<ChatGptAccount> awaitChatGptLogin();
    CompletionStage<Void> logoutChatGpt();
    @Override void close();

    record McpTokenResult(String plaintext, AiSettings settings) { }
    record TrafficSnapshot(long receivedBytes, long sentBytes) {
        public TrafficSnapshot {
            if (receivedBytes < 0L || sentBytes < 0L) {
                throw new IllegalArgumentException("traffic byte count is negative");
            }
        }
    }
    enum McpServerState { STARTING, RUNNING, STOPPED, FAILED }
    record McpServerStatus(McpServerState state, Optional<Integer> port, Optional<String> error) {
        public McpServerStatus {
            java.util.Objects.requireNonNull(state, "state");
            port = java.util.Objects.requireNonNull(port, "port");
            error = java.util.Objects.requireNonNull(error, "error");
            if (state == McpServerState.RUNNING && port.isEmpty()) {
                throw new IllegalArgumentException("A running MCP server must expose its bound port");
            }
        }
        public static McpServerStatus starting() {
            return new McpServerStatus(McpServerState.STARTING, Optional.empty(), Optional.empty());
        }
        public static McpServerStatus running(int port) {
            return new McpServerStatus(McpServerState.RUNNING, Optional.of(port), Optional.empty());
        }
        public static McpServerStatus stopped() {
            return new McpServerStatus(McpServerState.STOPPED, Optional.empty(), Optional.empty());
        }
        public static McpServerStatus failed(String error) {
            return new McpServerStatus(McpServerState.FAILED, Optional.empty(), Optional.of(error));
        }
    }
    record ChatGptAccount(boolean signedIn, Optional<String> email, Optional<String> planType) { }
    record ChatGptLoginStart(URI authUrl, boolean browserOpened) { }
    record AiChatTranscript(AiChatSession session, List<AiChatMessage> messages,
            List<com.ynpe.fastremote.domain.ai.AiPendingPrompt> pending) {
        public AiChatTranscript(AiChatSession session, List<AiChatMessage> messages) { this(session, messages, List.of()); }
        public AiChatTranscript {
            java.util.Objects.requireNonNull(session, "session");
            messages = List.copyOf(java.util.Objects.requireNonNull(messages, "messages"));
            pending = List.copyOf(pending);
        }
    }
}
