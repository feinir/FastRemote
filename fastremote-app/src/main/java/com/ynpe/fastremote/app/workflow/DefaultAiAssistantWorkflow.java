// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.ai.AiAgent;
import com.ynpe.fastremote.ai.AiAssistantReply;
import com.ynpe.fastremote.ai.AiConversationMessage;
import com.ynpe.fastremote.ai.AiImageInput;
import com.ynpe.fastremote.ai.AiStreamListener;
import com.ynpe.fastremote.ai.CodexAppServerClient;
import com.ynpe.fastremote.ai.ModelCatalogClient;
import com.ynpe.fastremote.domain.ai.AiSettings;
import com.ynpe.fastremote.domain.ai.AiModelCapabilities;
import com.ynpe.fastremote.domain.ai.AiChatMessage;
import com.ynpe.fastremote.domain.ai.AiChatImage;
import com.ynpe.fastremote.domain.ai.AiChatRepository;
import com.ynpe.fastremote.domain.ai.AiChatSession;
import com.ynpe.fastremote.domain.ai.AiSettingsRepository;
import com.ynpe.fastremote.domain.ai.McpServerSettings;
import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.AiInferenceOptions;
import com.ynpe.fastremote.domain.ai.AiInputModality;
import com.ynpe.fastremote.domain.ai.AiWorkspaceContext;
import com.ynpe.fastremote.domain.ai.ChatGptCodexProvider;
import com.ynpe.fastremote.domain.ai.ModelProviderType;
import com.ynpe.fastremote.domain.automation.AutomationSource;
import com.ynpe.fastremote.domain.automation.AutomationGateway;
import com.ynpe.fastremote.domain.vault.ManagedSecretVault;
import com.ynpe.fastremote.domain.vault.SecretId;
import com.ynpe.fastremote.domain.vault.SecretType;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import com.ynpe.fastremote.mcp.FastRemoteMcpServer;
import com.ynpe.fastremote.ui.AiAssistantWorkflow;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultAiAssistantWorkflow implements AiAssistantWorkflow {
    private final AiSettingsRepository repository;
    private final AiChatRepository chatRepository;
    private final ManagedSecretVault vault;
    private final AiAgent agent;
    private final ModelCatalogClient modelCatalog = new ModelCatalogClient();
    private final AutomationGateway automation;
    private final WorkflowThreadPool controlExecutor;
    private final WorkflowThreadPool modelExecutor;
    private final WorkflowThreadPool remoteCatalogExecutor;
    private final WorkflowThreadPool localCatalogExecutor;
    private final WorkflowThreadPool steeringExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final java.util.concurrent.ConcurrentMap<UUID, RequestControl> requests = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong retiredRequestReceived = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong retiredRequestSent = new java.util.concurrent.atomic.AtomicLong();
    private final ThreadLocal<RequestControl> executingRequest = new ThreadLocal<>();
    private final AtomicReference<McpServerStatus> mcpServerStatus =
            new AtomicReference<>(McpServerStatus.stopped());
    private final Path codexHome;
    private FastRemoteMcpServer mcpServer;
    private FastRemoteMcpServer codexMcpServer;
    private long retiredMcpReceivedBytes;
    private long retiredMcpSentBytes;
    private volatile Runnable externalMcpRequestObserver = () -> { };
    private CodexAppServerClient codex;

    public DefaultAiAssistantWorkflow(
            AiSettingsRepository repository, AiChatRepository chatRepository,
            ManagedSecretVault vault, AutomationGateway automation) {
        this(repository, chatRepository, vault, automation,
                Path.of(System.getProperty("java.io.tmpdir"), "fastremote-codex"));
    }

    public DefaultAiAssistantWorkflow(
            AiSettingsRepository repository, AiChatRepository chatRepository,
            ManagedSecretVault vault, AutomationGateway automation, Path codexHome) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.chatRepository = Objects.requireNonNull(chatRepository, "chatRepository");
        this.vault = Objects.requireNonNull(vault, "vault");
        this.automation = Objects.requireNonNull(automation, "automation");
        this.codexHome = Objects.requireNonNull(codexHome, "codexHome").toAbsolutePath().normalize();
        agent = new AiAgent(automation);
        steeringExecutor = executor("fastremote-ai-steer-", 16);
        controlExecutor = executor("fastremote-ai-control-", 32);
        modelExecutor = executor("fastremote-ai-model-", 16);
        modelExecutor.setMaximumPoolSize(8);
        modelExecutor.setCorePoolSize(8);
        remoteCatalogExecutor = executor("fastremote-ai-remote-catalog-", 16);
        localCatalogExecutor = executor("fastremote-ai-local-catalog-", 16);
    }

    public CompletionStage<Void> startConfiguredMcpServer() {
        return submit(() -> { applyMcp(repository.load().mcpServer()); return null; });
    }

    public void setExternalMcpRequestObserver(Runnable observer) {
        externalMcpRequestObserver = Objects.requireNonNull(observer, "observer");
    }

    @Override public CompletionStage<AiSettings> loadSettings() { return submit(() -> withChatGpt(repository.load())); }

    @Override public CompletionStage<McpServerStatus> loadMcpServerStatus() {
        return CompletableFuture.completedFuture(mcpServerStatus.get());
    }

    @Override public synchronized TrafficSnapshot trafficSnapshot() {
        long received = retiredRequestReceived.get() + agent.receivedBytes() + modelCatalog.receivedBytes() + retiredMcpReceivedBytes
                + automation.receivedBytes();
        long sent = retiredRequestSent.get() + agent.sentBytes() + modelCatalog.sentBytes() + retiredMcpSentBytes
                + automation.sentBytes();
        for (RequestControl request : requests.values()) {
            synchronized (request) {
                if (request.client != null) {
                    received += request.client.receivedBytes(); sent += request.client.sentBytes();
                }
                if (request.server != null) {
                    received += request.server.receivedBytes(); sent += request.server.sentBytes();
                }
            }
        }
        if (mcpServer != null) {
            received += mcpServer.receivedBytes();
            sent += mcpServer.sentBytes();
        }
        if (codexMcpServer != null) {
            received += codexMcpServer.receivedBytes();
            sent += codexMcpServer.sentBytes();
        }
        if (codex != null) {
            received += codex.receivedBytes();
            sent += codex.sentBytes();
        }
        return new TrafficSnapshot(received, sent);
    }

    @Override public CompletionStage<List<AiChatSession>> listChatSessions() {
        return submit(() -> chatRepository.listHosts(5000));
    }

    @Override public CompletionStage<AiChatSession> createChatSession() {
        return submit(() -> chatRepository.createSession(com.ynpe.fastremote.i18n.Texts.text("ai.chat.newSession")));
    }

    @Override public CompletionStage<AiChatTranscript> loadChatSession(UUID sessionId) {
        UUID safe = Objects.requireNonNull(sessionId, "sessionId");
        return submit(() -> new AiChatTranscript(chatRepository.findSession(safe)
                .orElseThrow(() -> new IllegalArgumentException("AI chat does not exist")),
                chatRepository.listMessages(safe), chatRepository.loadPending(safe)));
    }

    @Override public CompletionStage<Void> savePending(UUID sessionId,
            List<com.ynpe.fastremote.domain.ai.AiPendingPrompt> prompts) {
        var snapshot = List.copyOf(prompts);
        return submit(() -> { chatRepository.savePending(sessionId, snapshot); return null; });
    }

    @Override public CompletionStage<SteeringResult> steer(UUID sessionId, com.ynpe.fastremote.ai.AiSteeringInput input) {
        RequestControl request = requests.get(sessionId);
        if (request == null || request.cancelled.get()) return CompletableFuture.completedFuture(SteeringResult.NOT_RUNNING);
        if (!request.codexRequest) return CompletableFuture.completedFuture(
                request.inbox.offer(input) ? SteeringResult.WAITING : SteeringResult.NOT_RUNNING);
        // A protocol write must not wait behind the model executor or its lifecycle lock.
        return submit(steeringExecutor, () -> {
            if (request.cancelled.get() || requests.get(sessionId) != request) return SteeringResult.NOT_RUNNING;
            var client = request.client;
            if (client != null && client.steer(input)) return SteeringResult.ACCEPTED;
            return request.inbox.offer(input) ? SteeringResult.WAITING : SteeringResult.NOT_RUNNING;
        }, () -> {});
    }

    @Override public CompletionStage<AiChatSession> appendChatMessage(
            UUID sessionId, AiChatMessage.Role role, String content, Optional<String> newTitle) {
        return submit(() -> chatRepository.appendMessage(sessionId, role, content, newTitle));
    }

    @Override public CompletionStage<AiChatSession> appendChatMessage(
            UUID sessionId, AiChatMessage.Role role, String content, List<AiChatImage> images,
            Optional<String> newTitle) {
        return submit(() -> chatRepository.appendMessage(sessionId, role, content, images, newTitle));
    }

    @Override public CompletionStage<AiChatSession> renameChatSession(UUID sessionId, String title) {
        UUID safeId = Objects.requireNonNull(sessionId, "sessionId");
        String safeTitle = Objects.requireNonNull(title, "title");
        return submit(() -> chatRepository.renameSession(safeId, safeTitle));
    }

    @Override public CompletionStage<AiChatSession> updateChatModelSelection(
            UUID sessionId, Optional<String> providerId, Optional<String> model) {
        UUID safeId = Objects.requireNonNull(sessionId, "sessionId");
        Optional<String> safeProvider = Objects.requireNonNull(providerId, "providerId");
        Optional<String> safeModel = Objects.requireNonNull(model, "model");
        return submit(() -> chatRepository.updateModelSelection(safeId, safeProvider, safeModel));
    }

    @Override public CompletionStage<AiChatSession> updateAssociatedHost(UUID sessionId,
            Optional<com.ynpe.fastremote.domain.ai.AiHostAssociation> host) {
        return submit(() -> chatRepository.updateAssociatedHost(sessionId, host));
    }

    @Override public CompletionStage<List<AiChatSession>> setChatsPinned(List<UUID> ids, boolean pinned) {
        List<UUID> safe = List.copyOf(ids);
        return submit(() -> chatRepository.setPinned(safe, pinned));
    }

    @Override public CompletionStage<Void> deleteChatSessions(List<UUID> ids) {
        List<UUID> safe = List.copyOf(ids);
        return submit(() -> { chatRepository.deleteSessions(safe); return null; });
    }

    @Override public CompletionStage<Void> deleteChatSession(UUID sessionId) {
        return submit(() -> { chatRepository.deleteSession(sessionId); return null; });
    }

    @Override public CompletionStage<AiSettings> saveSettings(AiSettings settings) {
        AiSettings requested = Objects.requireNonNull(settings, "settings");
        AiSettings safe = withoutChatGpt(requested);
        return submit(() -> {
            AiSettings previous = repository.load();
            repository.save(safe);
            if (!previous.mcpServer().equals(safe.mcpServer()) || mcpRuntimeDiffers(safe.mcpServer())) {
                applyMcp(safe.mcpServer());
            }
            return requested;
        });
    }

    @Override
    public CompletionStage<ModelProviderConfig> saveProvider(
            ModelProviderConfig provider, Optional<char[]> replacementApiKey) {
        ModelProviderConfig safe = Objects.requireNonNull(provider, "provider");
        if (safe.type() == ModelProviderType.CHATGPT_CODEX) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("The ChatGPT provider is built in"));
        }
        Optional<char[]> replacement = Objects.requireNonNull(replacementApiKey, "replacementApiKey")
                .map(value -> {
                    char[] copy = value.clone();
                    Arrays.fill(value, '\0');
                    return copy;
                });
        return submit(() -> storeProvider(safe, replacement), () -> replacement.ifPresent(value -> Arrays.fill(value, '\0')))
                .whenComplete((value, failure) -> replacement.ifPresent(key -> Arrays.fill(key, '\0')));
    }

    private ModelProviderConfig storeProvider(ModelProviderConfig safe, Optional<char[]> replacement) throws Exception {
        AiSettings settings = repository.load();
        ModelProviderConfig existing = settings.providers().stream()
                .filter(value -> value.id().equals(safe.id())).findFirst().orElse(null);
        if (existing == null && settings.providers().size() >= 32) {
            throw new IllegalStateException("At most 32 custom model providers are supported");
        }
        Optional<SecretId> secretId = safe.apiKeySecretId();
        if (replacement.isPresent()) {
            if (vault.status() != VaultStatus.UNLOCKED) throw new IllegalStateException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.91f5e73a10b4e9e5"));
            byte[] encoded = encode(replacement.orElseThrow());
            try {
                if (secretId.isPresent()) vault.replace(secretId.orElseThrow(), SecretType.TOKEN, encoded);
                else secretId = Optional.of(vault.put(SecretType.TOKEN, encoded));
            } finally { Arrays.fill(encoded, (byte) 0); }
        } else if (secretId.isEmpty() && existing != null) secretId = existing.apiKeySecretId();
        ModelProviderConfig stored = new ModelProviderConfig(safe.id(), safe.name(), safe.type(), safe.baseUri(),
                safe.model(), secretId, safe.allowInsecureHttp());
        List<ModelProviderConfig> providers = new ArrayList<>(settings.providers());
        providers.removeIf(value -> value.id().equals(stored.id()));
        providers.add(stored);
        AiSettings updated = copy(settings, providers, Optional.of(stored.id()), settings.mcpServer());
        repository.save(updated);
        return stored;
    }

    @Override public CompletionStage<AiSettings> deleteProvider(String providerId) {
        String id = Objects.requireNonNull(providerId, "providerId").strip();
        if (id.equals(ChatGptCodexProvider.ID)) return loadSettings();
        return submit(() -> {
            AiSettings settings = repository.load();
            ModelProviderConfig removed = settings.providers().stream()
                    .filter(value -> value.id().equals(id)).findFirst().orElse(null);
            if (removed == null) return withChatGpt(settings);
            if (removed.apiKeySecretId().isPresent() && vault.status() != VaultStatus.UNLOCKED) {
                throw new IllegalStateException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d8e805fe3bbce6f2"));
            }
            List<ModelProviderConfig> providers = settings.providers().stream()
                    .filter(value -> !value.id().equals(id)).toList();
            AiSettings updated = copy(settings, providers,
                    settings.selectedProviderId().filter(value -> !value.equals(id)), settings.mcpServer());
            repository.save(updated);
            if (removed.apiKeySecretId().isPresent()) vault.delete(removed.apiKeySecretId().orElseThrow());
            return withChatGpt(updated);
        });
    }

    @Override
    public CompletionStage<AiAssistantReply> send(
            String providerId, String model, List<AiConversationMessage> history, String prompt,
            AiWorkspaceContext workspaceContext, AiInferenceOptions inference) {
        String id = Objects.requireNonNull(providerId, "providerId").strip();
        List<AiConversationMessage> safeHistory = List.copyOf(history);
        String safePrompt = Objects.requireNonNull(prompt, "prompt");
        AiWorkspaceContext context = Objects.requireNonNull(workspaceContext, "workspaceContext");
        AiInferenceOptions safeInference = Objects.requireNonNull(inference, "inference");
        if (id.equals(ChatGptCodexProvider.ID)) {
            return submitRequest(true, () -> requestCodex().reply(model, safeHistory, safePrompt, context,
                    safeInference, AiStreamListener.NONE));
        }
        return submitRequest(false, () -> {
            AiSettings settings = repository.load();
            ModelProviderConfig provider = settings.providers().stream()
                    .filter(value -> value.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2381a638c1b3a20e")));
            char[] apiKey = readApiKey(provider);
            try {
                return agent.reply(provider, model, apiKey, safeHistory, safePrompt, context, safeInference,
                        settings.maxToolRounds());
            }
            finally { Arrays.fill(apiKey, '\0'); }
        });
    }

    @Override
    public CompletionStage<AiAssistantReply> sendStreaming(
            String providerId, String model, List<AiConversationMessage> history, String prompt,
            AiWorkspaceContext workspaceContext, AiInferenceOptions inference, AiStreamListener listener) {
        String id = Objects.requireNonNull(providerId, "providerId").strip();
        List<AiConversationMessage> safeHistory = List.copyOf(history);
        String safePrompt = Objects.requireNonNull(prompt, "prompt");
        AiWorkspaceContext context = Objects.requireNonNull(workspaceContext, "workspaceContext");
        AiInferenceOptions safeInference = Objects.requireNonNull(inference, "inference");
        AiStreamListener safeListener = Objects.requireNonNull(listener, "listener");
        if (id.equals(ChatGptCodexProvider.ID)) {
            return submitRequest(true, () -> requestCodex().reply(model, safeHistory, safePrompt, context,
                    safeInference, safeListener));
        }
        return submitRequest(false, () -> {
            AiSettings settings = repository.load();
            ModelProviderConfig provider = settings.providers().stream()
                    .filter(value -> value.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2381a638c1b3a20e")));
            char[] apiKey = readApiKey(provider);
            try {
                return agent.replyStreaming(provider, model, apiKey, safeHistory, safePrompt, context,
                        safeInference, safeListener, settings.maxToolRounds());
            } finally {
                Arrays.fill(apiKey, '\0');
            }
        });
    }

    @Override
    public CompletionStage<AiAssistantReply> sendStreaming(
            String providerId, String model, List<AiConversationMessage> history, String prompt,
            List<AiImageInput> images, AiWorkspaceContext workspaceContext,
            AiInferenceOptions inference, AiStreamListener listener) {
        if (providerId.equals(ChatGptCodexProvider.ID) && workspaceContext.conversationId().isEmpty()) {
            var safeHistory = List.copyOf(history); var safeImages = List.copyOf(images);
            return submitRequest(UUID.randomUUID(), workspaceContext, true, () -> requestCodex().reply(
                    model, safeHistory, prompt, safeImages, workspaceContext, inference, listener));
        }
        return sendStreaming(workspaceContext.conversationId().orElseGet(UUID::randomUUID), providerId,
                model, history, prompt, images, workspaceContext, inference, listener);
    }

    @Override public CompletionStage<AiAssistantReply> sendStreaming(UUID sessionId,
            String providerId, String model, List<AiConversationMessage> history, String prompt,
            List<AiImageInput> images, AiWorkspaceContext workspaceContext,
            AiInferenceOptions inference, AiStreamListener listener) {
        String id = Objects.requireNonNull(providerId, "providerId").strip();
        List<AiConversationMessage> safeHistory = List.copyOf(history);
        String safePrompt = Objects.requireNonNull(prompt, "prompt");
        List<AiImageInput> safeImages = List.copyOf(Objects.requireNonNull(images, "images"));
        AiWorkspaceContext context = Objects.requireNonNull(workspaceContext, "workspaceContext");
        AiInferenceOptions safeInference = Objects.requireNonNull(inference, "inference");
        AiStreamListener safeListener = Objects.requireNonNull(listener, "listener");
        if (id.equals(ChatGptCodexProvider.ID)) {
            return submitRequest(sessionId, context, true, () -> {
                RequestControl request = executingRequest.get();
                String contextKey = model + "\n" + context.kind() + "\n" + context.label()
                        + "\n" + context.remoteHostId().map(Object::toString).orElse("")
                        + "\n" + context.associatedHost().map(com.ynpe.fastremote.domain.ai.AiHostAssociation::endpoint).orElse("");
                Optional<String> thread = chatRepository.loadCodexThread(sessionId, contextKey);
                var client = requestCodex();
                AiAssistantReply reply = client.reply(model, safeHistory, safePrompt, safeImages,
                        context, safeInference, safeListener, thread,
                        threadId -> chatRepository.saveCodexThread(sessionId, contextKey, threadId));
                // Older App Servers (or a message arriving before turn/start) use a safe boundary.
                while (!request.inbox.finishIfEmpty()) {
                    for (var input : request.inbox.drain()) {
                        if (request.cancelled.get()) throw new java.util.concurrent.CancellationException();
                        safeListener.onSteeringAccepted(input);
                        reply = client.reply(model, List.of(), input.text(), input.images(), context,
                                safeInference, safeListener, chatRepository.loadCodexThread(sessionId, contextKey),
                                threadId -> chatRepository.saveCodexThread(sessionId, contextKey, threadId));
                    }
                }
                return reply;
            });
        }
        return submitRequest(sessionId, context, false, () -> {
            AiSettings settings = repository.load();
            ModelProviderConfig provider = settings.providers().stream()
                    .filter(value -> value.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2381a638c1b3a20e")));
            if (!safeImages.isEmpty() && provider.type() != ModelProviderType.OPENAI_RESPONSES) {
                throw new UnsupportedOperationException("The selected provider does not accept image input");
            }
            char[] apiKey = readApiKey(provider);
            try {
                return agent.replyStreaming(provider, model, apiKey, safeHistory, safePrompt, safeImages, context,
                        safeInference, safeListener, settings.maxToolRounds(), executingRequest.get().inbox);
            } finally {
                Arrays.fill(apiKey, '\0');
            }
        });
    }

    @Override
    public CompletionStage<String> generateChatTitle(
            String providerId, String model, String userPrompt, String assistantAnswer) {
        return generateChatTitle(UUID.randomUUID(), providerId, model, userPrompt, assistantAnswer);
    }

    @Override public CompletionStage<String> generateChatTitle(UUID sessionId,
            String providerId, String model, String userPrompt, String assistantAnswer) {
        String id = Objects.requireNonNull(providerId, "providerId").strip();
        String safeModel = Objects.requireNonNull(model, "model").strip();
        String prompt = Objects.requireNonNull(userPrompt, "userPrompt");
        String answer = Objects.requireNonNull(assistantAnswer, "assistantAnswer");
        if (id.equals(ChatGptCodexProvider.ID)) {
            return submitRequest(sessionId, AiWorkspaceContext.none().withConversation(sessionId, Optional.empty()), true, () -> requestCodex().reply(safeModel, List.of(),
                    "Create a concise title of at most 40 characters for this conversation. Return only the title.\nUser: "
                            + prompt + "\nAssistant: " + answer,
                    AiWorkspaceContext.none(), AiInferenceOptions.DEFAULT, AiStreamListener.NONE).text().strip());
        }
        return submitRequest(sessionId, AiWorkspaceContext.none().withConversation(sessionId, Optional.empty()), false, () -> {
            ModelProviderConfig provider = repository.load().providers().stream()
                    .filter(value -> value.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2381a638c1b3a20e")));
            char[] apiKey = readApiKey(provider);
            try { return agent.generateChatTitle(provider, safeModel, apiKey, prompt, answer); }
            finally { Arrays.fill(apiKey, '\0'); }
        });
    }

    @Override public CompletionStage<List<String>> discoverModels(String providerId) {
        String id = Objects.requireNonNull(providerId, "providerId").strip();
        if (id.equals(ChatGptCodexProvider.ID)) {
            return submit(remoteCatalogExecutor, () -> ensureCodex().models(), () -> {});
        }
        return loadProvider(id).thenCompose(provider -> submitCatalog(provider, () -> {
            char[] apiKey = readApiKey(provider);
            try { return modelCatalog.discover(provider, apiKey); }
            finally { Arrays.fill(apiKey, '\0'); }
        }));
    }

    @Override public CompletionStage<List<AiModelCapabilities>> discoverModelCapabilities(String providerId) {
        String id = Objects.requireNonNull(providerId, "providerId").strip();
        if (id.equals(ChatGptCodexProvider.ID)) {
            return submit(remoteCatalogExecutor, () -> ensureCodex().modelCapabilities(), () -> {});
        }
        return loadProvider(id).thenCompose(provider -> submitCatalog(provider, () -> {
            char[] apiKey = readApiKey(provider);
            try {
                List<AiInputModality> modalities = provider.type() == ModelProviderType.OPENAI_RESPONSES
                        ? List.of(AiInputModality.TEXT, AiInputModality.IMAGE)
                        : List.of(AiInputModality.TEXT);
                return modelCatalog.discover(provider, apiKey).stream()
                        .map(model -> new AiModelCapabilities(model, List.of(), Optional.empty(), modalities)).toList();
            } finally {
                Arrays.fill(apiKey, '\0');
            }
        }));
    }

    @Override public CompletionStage<McpTokenResult> regenerateMcpToken() {
        return submit(() -> {
            if (vault.status() != VaultStatus.UNLOCKED) throw new IllegalStateException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e01351f725698bf2"));
            var generated = FastRemoteMcpServer.generateToken();
            AiSettings settings = repository.load();
            byte[] encoded = generated.plaintext().getBytes(StandardCharsets.UTF_8);
            SecretId secretId;
            try { secretId = vault.put(SecretType.TOKEN, encoded); }
            finally { Arrays.fill(encoded, (byte) 0); }
            McpServerSettings mcp = new McpServerSettings(settings.mcpServer().enabled(),
                    settings.mcpServer().allowRemoteAccess(), settings.mcpServer().port(),
                    Optional.of(generated.storedHash()), Optional.of(secretId));
            AiSettings updated = copy(settings, settings.providers(), settings.selectedProviderId(), mcp);
            try {
                repository.save(updated);
                applyMcp(mcp);
            } catch (Exception failure) {
                vault.delete(secretId);
                throw failure;
            }
            if (settings.mcpServer().tokenSecretId().isPresent()) {
                vault.delete(settings.mcpServer().tokenSecretId().orElseThrow());
            }
            return new McpTokenResult(generated.plaintext(), withChatGpt(updated));
        });
    }

    @Override public CompletionStage<String> loadMcpToken() {
        return submit(() -> {
            McpServerSettings mcp = repository.load().mcpServer();
            if (mcp.tokenSecretId().isEmpty()) throw new IllegalStateException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e03298ad52f8f0fb"));
            if (vault.status() != VaultStatus.UNLOCKED) throw new IllegalStateException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c280248a73c40557"));
            byte[] encoded = vault.read(mcp.tokenSecretId().orElseThrow());
            try {
                String plaintext = new String(encoded, StandardCharsets.UTF_8);
                if (mcp.tokenHash().isEmpty() || !FastRemoteMcpServer.storeHash(plaintext)
                        .equals(mcp.tokenHash().orElseThrow())) {
                    throw new IllegalStateException("The saved MCP key does not match the running server; regenerate it");
                }
                return plaintext;
            }
            finally { Arrays.fill(encoded, (byte) 0); }
        });
    }

    @Override public CompletionStage<ChatGptAccount> loadChatGptAccount() {
        return submitModel(() -> account(ensureCodex().account()));
    }

    @Override public CompletionStage<ChatGptLoginStart> startChatGptLogin() {
        return submitModel(() -> {
            var result = ensureCodex().startBrowserLogin();
            return new ChatGptLoginStart(result.authUrl(), result.browserOpened());
        });
    }

    @Override public CompletionStage<ChatGptAccount> awaitChatGptLogin() {
        return submitModel(() -> {
            var result = ensureCodex().awaitLogin();
            if (!result.success()) throw new IllegalStateException(result.error().orElse("ChatGPT login failed"));
            return account(ensureCodex().account());
        });
    }

    @Override public CompletionStage<Void> logoutChatGpt() {
        return submitModel(() -> { ensureCodex().logout(); return null; });
    }

    @Override public CompletionStage<Void> cancelActiveRequest() {
        requests.keySet().forEach(this::cancelRequest);
        return CompletableFuture.completedFuture(null);
    }

    @Override public CompletionStage<Void> cancelRequest(UUID sessionId) {
        RequestControl request = requests.get(sessionId);
        if (request != null) request.cancel();
        return CompletableFuture.completedFuture(null);
    }

    private CodexAppServerClient requestCodex() throws IOException {
        RequestControl request = executingRequest.get();
        if (request == null) throw new IllegalStateException("No model request scope");
        synchronized (request) {
            if (request.cancelled.get()) throw new java.util.concurrent.CancellationException();
            if (request.client == null) {
                var token = FastRemoteMcpServer.generateToken();
                var server = new FastRemoteMcpServer(automation, 0, token.storedHash(), false,
                        AutomationSource.BUILT_IN_AI, () -> {},
                        request.context.conversationId().isPresent()
                                ? Optional.of(request.context.conversationContext()) : Optional.empty());
                server.start();
                request.server = server;
                request.client = new CodexAppServerClient(codexHome,
                        URI.create("http://127.0.0.1:" + server.port() + FastRemoteMcpServer.ENDPOINT), token.plaintext());
            }
            return request.client;
        }
    }

    private static final class RequestControl {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AiWorkspaceContext context;
        volatile Thread thread;
        volatile CodexAppServerClient client;
        FastRemoteMcpServer server;
        final com.ynpe.fastremote.ai.AiTurnInbox inbox = new com.ynpe.fastremote.ai.AiTurnInbox();
        final boolean codexRequest;
        RequestControl(AiWorkspaceContext context, boolean codexRequest) {
            this.context = context; this.codexRequest = codexRequest;
        }
        synchronized void cancel() {
            cancelled.set(true);
            inbox.close();
            if (client != null && client.interruptActiveTurn()) return;
            if (thread != null) thread.interrupt();
        }
        synchronized void close() {
            inbox.close();
            if (client != null) client.close();
            if (server != null) server.close();
        }
    }

    private static ChatGptAccount account(CodexAppServerClient.Account account) {
        return new ChatGptAccount(account.signedIn(), account.email(), account.planType());
    }

    private synchronized CodexAppServerClient ensureCodex() throws IOException {
        if (codex != null) return codex;
        var token = FastRemoteMcpServer.generateToken();
        FastRemoteMcpServer server = new FastRemoteMcpServer(
                automation, 0, token.storedHash(), AutomationSource.BUILT_IN_AI);
        server.start();
        try {
            CodexAppServerClient client = new CodexAppServerClient(codexHome,
                    URI.create("http://127.0.0.1:" + server.port() + FastRemoteMcpServer.ENDPOINT),
                    token.plaintext());
            codexMcpServer = server;
            codex = client;
            return client;
        } catch (RuntimeException failure) {
            server.close();
            throw failure;
        }
    }

    private char[] readApiKey(ModelProviderConfig provider) throws Exception {
        if (provider.apiKeySecretId().isEmpty()) return new char[0];
        if (vault.status() != VaultStatus.UNLOCKED) throw new IllegalStateException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.30bb4bc2966078e0"));
        byte[] encoded = vault.read(provider.apiKeySecretId().orElseThrow());
        try { return decode(encoded); }
        finally { Arrays.fill(encoded, (byte) 0); }
    }

    private synchronized void applyMcp(McpServerSettings settings) throws IOException {
        if (mcpServer != null) {
            retiredMcpReceivedBytes += mcpServer.receivedBytes();
            retiredMcpSentBytes += mcpServer.sentBytes();
            mcpServer.close();
            mcpServer = null;
        }
        if (!settings.enabled()) {
            mcpServerStatus.set(McpServerStatus.stopped());
            return;
        }
        mcpServerStatus.set(McpServerStatus.starting());
        FastRemoteMcpServer candidate = null;
        try {
            candidate = new FastRemoteMcpServer(
                    automation, settings.port(), settings.tokenHash().orElseThrow(),
                    settings.allowRemoteAccess(), AutomationSource.MCP, externalMcpRequestObserver);
            candidate.start();
            mcpServer = candidate;
            mcpServerStatus.set(McpServerStatus.running(candidate.port()));
        } catch (IOException | RuntimeException failure) {
            if (candidate != null) candidate.close();
            mcpServerStatus.set(McpServerStatus.failed(safeMcpFailure(failure)));
            throw failure;
        }
    }

    private synchronized boolean mcpRuntimeDiffers(McpServerSettings settings) {
        return settings.enabled() != (mcpServer != null
                && mcpServerStatus.get().state() == McpServerState.RUNNING);
    }

    private static String safeMcpFailure(Throwable failure) {
        Throwable value = failure;
        while (value.getCause() != null && (value instanceof java.util.concurrent.ExecutionException
                || value instanceof CompletionException)) value = value.getCause();
        String message = value.getMessage();
        if (message == null || message.isBlank()) message = value.getClass().getSimpleName();
        message = com.ynpe.fastremote.i18n.Texts.translateExternalText(message)
                .replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').strip();
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private static AiSettings copy(AiSettings settings, List<ModelProviderConfig> providers,
            Optional<String> selected, McpServerSettings mcp) {
        return new AiSettings(providers, selected, settings.builtInPermissions(), settings.mcpPermissions(),
                settings.allowedLocalRoots(), settings.allowedRemoteRoots(), settings.maxTransferBytes(),
                settings.maxToolRounds(), mcp);
    }

    private static AiSettings withChatGpt(AiSettings settings) {
        if (settings.providers().stream().anyMatch(value -> value.id().equals(ChatGptCodexProvider.ID))) return settings;
        List<ModelProviderConfig> providers = new ArrayList<>();
        providers.add(ChatGptCodexProvider.CONFIG);
        providers.addAll(settings.providers());
        return copy(settings, providers, settings.selectedProviderId(), settings.mcpServer());
    }

    private static AiSettings withoutChatGpt(AiSettings settings) {
        List<ModelProviderConfig> providers = settings.providers().stream()
                .filter(value -> !value.id().equals(ChatGptCodexProvider.ID)).toList();
        return copy(settings, providers,
                settings.selectedProviderId().filter(value -> !value.equals(ChatGptCodexProvider.ID)),
                settings.mcpServer());
    }

    private <T> CompletionStage<T> submit(CheckedSupplier<T> supplier) {
        return submit(controlExecutor, supplier, () -> {});
    }

    private <T> CompletionStage<T> submit(CheckedSupplier<T> supplier, Runnable rejectedCleanup) {
        return submit(controlExecutor, supplier, rejectedCleanup);
    }

    private <T> CompletionStage<T> submitModel(CheckedSupplier<T> supplier) {
        return submit(modelExecutor, supplier, () -> {});
    }

    private CompletionStage<ModelProviderConfig> loadProvider(String providerId) {
        return submit(() -> repository.load().providers().stream()
                .filter(value -> value.id().equals(providerId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.2381a638c1b3a20e"))));
    }

    private <T> CompletionStage<T> submitCatalog(
            ModelProviderConfig provider, CheckedSupplier<T> supplier) {
        WorkflowThreadPool target = provider.type() == ModelProviderType.OLLAMA
                ? localCatalogExecutor : remoteCatalogExecutor;
        return submit(target, supplier, () -> {});
    }

    private <T> CompletionStage<T> submitRequest(boolean codexRequest, CheckedSupplier<T> supplier) {
        return submitRequest(UUID.randomUUID(), AiWorkspaceContext.none(), codexRequest, supplier);
    }

    private <T> CompletionStage<T> submitRequest(UUID sessionId, AiWorkspaceContext context,
            boolean codexRequest, CheckedSupplier<T> supplier) {
        RequestControl request = new RequestControl(context, codexRequest);
        if (requests.putIfAbsent(sessionId, request) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("A request is already running in this conversation"));
        }
        return submit(modelExecutor, () -> {
            request.thread = Thread.currentThread();
            executingRequest.set(request);
            try {
                if (request.cancelled.get()) throw new java.util.concurrent.CancellationException("AI request interrupted before execution");
                return supplier.get();
            } finally {
                request.close();
                synchronized (DefaultAiAssistantWorkflow.this) {
                if (request.client != null) {
                    retiredRequestReceived.addAndGet(request.client.receivedBytes());
                    retiredRequestSent.addAndGet(request.client.sentBytes());
                }
                if (request.server != null) {
                    retiredRequestReceived.addAndGet(request.server.receivedBytes());
                    retiredRequestSent.addAndGet(request.server.sentBytes());
                }
                requests.remove(sessionId, request);
                }
                executingRequest.remove();
                Thread.interrupted();
            }
        }, () -> requests.remove(sessionId, request));
    }

    private <T> CompletionStage<T> submit(
            WorkflowThreadPool target, CheckedSupplier<T> supplier, Runnable rejectedCleanup) {
        if (closed.get()) { rejectedCleanup.run(); return CompletableFuture.failedFuture(new IllegalStateException("AI workflow is closed")); }
        try {
            return target.supply(() -> {
                try { return supplier.get(); }
                catch (Exception failure) { throw new CompletionException(failure); }
            }, rejectedCleanup);
        } catch (RejectedExecutionException rejected) {
            rejectedCleanup.run();
            return CompletableFuture.failedFuture(new IllegalStateException("AI request queue is full", rejected));
        }
    }

    private static WorkflowThreadPool executor(String threadName, int queueCapacity) {
        WorkflowThreadPool value = new WorkflowThreadPool(
                1, 1, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().name(threadName, 0).daemon(false).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        value.allowCoreThreadTimeOut(true);
        return value;
    }

    private static byte[] encode(char[] value) throws CharacterCodingException {
        var encoder = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer encoded = encoder.encode(CharBuffer.wrap(value));
        byte[] result = new byte[encoded.remaining()]; encoded.get(result);
        if (encoded.hasArray()) Arrays.fill(encoded.array(), (byte) 0);
        return result;
    }
    private static char[] decode(byte[] value) throws CharacterCodingException {
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer decoded = decoder.decode(ByteBuffer.wrap(value));
        char[] result = new char[decoded.remaining()]; decoded.get(result);
        if (decoded.hasArray()) Arrays.fill(decoded.array(), '\0');
        return result;
    }

    public java.util.concurrent.CompletionStage<Void> stopped() {
        return java.util.concurrent.CompletableFuture.allOf(steeringExecutor.stopped().toCompletableFuture(), controlExecutor.stopped().toCompletableFuture(),
                modelExecutor.stopped().toCompletableFuture(), remoteCatalogExecutor.stopped().toCompletableFuture(),
                localCatalogExecutor.stopped().toCompletableFuture());
    }

    @Override public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (mcpServer != null) { mcpServer.close(); mcpServer = null; }
        mcpServerStatus.set(McpServerStatus.stopped());
        if (codex != null) { codex.close(); codex = null; }
        if (codexMcpServer != null) { codexMcpServer.close(); codexMcpServer = null; }
        requests.values().forEach(RequestControl::cancel);
        agent.close();
        modelCatalog.close();
        steeringExecutor.shutdownNow();
        controlExecutor.shutdownNow();
        modelExecutor.shutdownNow();
        remoteCatalogExecutor.shutdownNow();
        localCatalogExecutor.shutdownNow();
    }
    @FunctionalInterface private interface CheckedSupplier<T> { T get() throws Exception; }
}
