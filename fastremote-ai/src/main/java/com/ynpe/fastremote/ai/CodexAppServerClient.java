// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import com.ynpe.fastremote.domain.ai.AiInferenceOptions;
import com.ynpe.fastremote.domain.ai.AiInputModality;
import com.ynpe.fastremote.domain.ai.AiModelCapabilities;
import com.ynpe.fastremote.domain.ai.AiReasoningEffort;
import com.ynpe.fastremote.domain.ai.AiWorkspaceContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Minimal versioned client for the official Codex App Server JSONL protocol. */
public final class CodexAppServerClient implements AutoCloseable {
    private static final long REQUEST_TIMEOUT_SECONDS = 45;
    private static final long TURN_TIMEOUT_MINUTES = 10;
    private final ObjectMapper json = new ObjectMapper();
    private final Path codexHome;
    private final URI mcpEndpoint;
    private final String mcpToken;
    private final AtomicLong requestIds = new AtomicLong();
    private final AtomicLong receivedBytes = new AtomicLong();
    private final AtomicLong sentBytes = new AtomicLong();
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private final Object writeLock = new Object();
    private volatile Process process;
    private volatile BufferedWriter writer;
    private volatile CompletableFuture<LoginResult> loginCompletion;
    private volatile String activeLoginId = "";
    private volatile TurnCollector activeTurn;

    public CodexAppServerClient(Path codexHome, URI mcpEndpoint, String mcpToken) {
        this.codexHome = Objects.requireNonNull(codexHome, "codexHome").toAbsolutePath().normalize();
        this.mcpEndpoint = Objects.requireNonNull(mcpEndpoint, "mcpEndpoint");
        this.mcpToken = Objects.requireNonNull(mcpToken, "mcpToken");
    }

    public long receivedBytes() { return receivedBytes.get(); }

    public long sentBytes() { return sentBytes.get(); }

    public Account account() throws Exception {
        JsonNode result = request("account/read", json.createObjectNode().put("refreshToken", false));
        JsonNode account = result.path("account");
        if (account.isMissingNode() || account.isNull()) return Account.signedOut();
        return new Account(true, Optional.ofNullable(account.path("email").textValue()),
                Optional.ofNullable(account.path("planType").textValue()));
    }

    public LoginStart startBrowserLogin() throws Exception {
        ObjectNode params = json.createObjectNode().put("type", "chatgpt")
                .put("useHostedLoginSuccessPage", true).put("appBrand", "chatgpt");
        JsonNode result = request("account/login/start", params);
        String loginId = requiredText(result, "loginId");
        URI authUrl = URI.create(requiredText(result, "authUrl"));
        activeLoginId = loginId;
        loginCompletion = new CompletableFuture<>();
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            return new LoginStart(loginId, authUrl, false);
        }
        Desktop.getDesktop().browse(authUrl);
        return new LoginStart(loginId, authUrl, true);
    }

    public LoginResult awaitLogin() throws Exception {
        CompletableFuture<LoginResult> future = loginCompletion;
        if (future == null) throw new IllegalStateException("ChatGPT login has not been started");
        return future.get(5, TimeUnit.MINUTES);
    }

    public void logout() throws Exception {
        request("account/logout", json.createObjectNode());
    }

    public List<String> models() throws Exception {
        return modelCapabilities().stream().map(AiModelCapabilities::model).toList();
    }

    public List<AiModelCapabilities> modelCapabilities() throws Exception {
        JsonNode result = request("model/list", json.createObjectNode().put("includeHidden", false));
        java.util.LinkedHashMap<String, AiModelCapabilities> models = new java.util.LinkedHashMap<>();
        for (JsonNode value : result.path("data")) {
            String model = value.path("model").asText("").strip();
            if (model.isEmpty()) continue;
            List<AiReasoningEffort> efforts = new java.util.ArrayList<>();
            for (JsonNode option : value.path("supportedReasoningEfforts")) {
                parseReasoningEffort(option.path("reasoningEffort").asText()).ifPresent(efforts::add);
            }
            Optional<AiReasoningEffort> suggested = parseReasoningEffort(
                    value.path("defaultReasoningEffort").asText());
            List<AiInputModality> inputModalities = new java.util.ArrayList<>();
            for (JsonNode modality : value.path("inputModalities")) {
                switch (modality.asText("").toLowerCase(java.util.Locale.ROOT)) {
                    case "text" -> inputModalities.add(AiInputModality.TEXT);
                    case "image" -> inputModalities.add(AiInputModality.IMAGE);
                    default -> { }
                }
            }
            if (inputModalities.isEmpty()) inputModalities.add(AiInputModality.TEXT);
            models.putIfAbsent(model, new AiModelCapabilities(model, efforts, suggested, inputModalities));
        }
        return List.copyOf(models.values());
    }

    public AiAssistantReply reply(
            String model, List<AiConversationMessage> history, String prompt,
            AiWorkspaceContext workspace, AiInferenceOptions inference, AiStreamListener listener) throws Exception {
        return reply(model, history, prompt, List.of(), workspace, inference, listener);
    }

    public AiAssistantReply reply(
            String model, List<AiConversationMessage> history, String prompt, List<AiImageInput> images,
            AiWorkspaceContext workspace, AiInferenceOptions inference, AiStreamListener listener) throws Exception {
        return replyInternal(model, history, prompt, images, workspace, inference, listener, Optional.empty(), id -> {}, false);
    }

    @FunctionalInterface public interface ThreadBinding { void save(String threadId) throws IOException; }

    public AiAssistantReply reply(String model, List<AiConversationMessage> history, String prompt,
            List<AiImageInput> images, AiWorkspaceContext workspace, AiInferenceOptions inference,
            AiStreamListener listener, Optional<String> previousThread, ThreadBinding binding) throws Exception {
        return replyInternal(model, history, prompt, images, workspace, inference, listener, previousThread, binding, true);
    }

    private AiAssistantReply replyInternal(String model, List<AiConversationMessage> history, String prompt,
            List<AiImageInput> images, AiWorkspaceContext workspace, AiInferenceOptions inference,
            AiStreamListener listener, Optional<String> previousThread, ThreadBinding binding, boolean persistent) throws Exception {
        Objects.requireNonNull(listener, "listener");
        synchronized (lifecycleLock) {
            ensureStarted();
            ObjectNode threadParams = json.createObjectNode();
            threadParams.put("model", model);
            Path workspaceDirectory = codexHome.resolve("workspace");
            Files.createDirectories(workspaceDirectory);
            threadParams.put("cwd", workspaceDirectory.toString());
            threadParams.put("ephemeral", !persistent);
            threadParams.put("approvalPolicy", "on-request");
            threadParams.put("sandbox", "read-only");
            threadParams.put("baseInstructions", "You are the AI assistant embedded in FastRemote. "
                    + "Use only the fastremote MCP tools for application actions. FastRemote MCP tools have their own "
                    + "user approval flow, so call them when needed and do not refuse merely because Codex native tool "
                    + "permissions differ. For commands on this computer, immediately call the fastremote MCP tool "
                    + "run_local_terminal_command; do not search for, assess, plan to use, or request any native shell, "
                    + "terminal, command-execution, or local-filesystem tool. Native shell and local filesystem tools are "
                    + "unavailable, and approval requests for them are always declined. Let FastRemote display its own "
                    + "approval dialog when an MCP operation requires consent. "
                    + com.ynpe.fastremote.domain.automation.AutomationExecutionGuidance.TRANSFER_GUIDANCE + " "
                    + com.ynpe.fastremote.domain.automation.AutomationCatalogTool.GUIDANCE
                    + com.ynpe.fastremote.domain.automation.AutomationExecutionGuidance.REMOTE_INPUT_GUIDANCE);
            if (previousThread.isPresent()) {
                threadParams.remove("ephemeral");
                threadParams.put("threadId", previousThread.orElseThrow());
            }
            JsonNode started = request(previousThread.isPresent() ? "thread/resume" : "thread/start", threadParams);
            String threadId = requiredText(started.path("thread"), "id");
            binding.save(threadId);
            ObjectNode turnParams = json.createObjectNode().put("threadId", threadId).put("model", model);
            inference.reasoningEffort().ifPresent(value ->
                    turnParams.put("effort", value.name().toLowerCase(java.util.Locale.ROOT)));
            turnParams.put("summary", inference.showReasoningSummary() ? "concise" : "none");
            ArrayNode input = turnParams.putArray("input");
            input.addObject().put("type", "text").put("text", composedPrompt(previousThread.isPresent() ? List.of() : history, prompt, workspace));
            for (AiConversationMessage message : previousThread.isPresent() ? List.<AiConversationMessage>of() : history) {
                for (AiImageInput image : message.images()) {
                    input.addObject().put("type", "image").put("url", dataUrl(image));
                }
            }
            for (AiImageInput image : List.copyOf(Objects.requireNonNull(images, "images"))) {
                input.addObject().put("type", "image").put("url", dataUrl(image));
            }
            TurnCollector collector = new TurnCollector(threadId, listener);
            activeTurn = collector;
            try {
                JsonNode turnStarted = request("turn/start", turnParams);
                collector.turnId = requiredText(turnStarted.path("turn"), "id");
                return collector.completion.get(TURN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            } catch (TimeoutException timeout) {
                throw new IOException("Codex response timed out", timeout);
            } finally {
                activeTurn = null;
            }
        }
    }

    /** False is a definite local absence of an active turn, never an ambiguous transport failure. */
    public boolean steer(AiSteeringInput input) throws Exception {
        TurnCollector collector = activeTurn;
        if (collector == null || collector.turnId.isBlank() || collector.completion.isDone()) return false;
        ObjectNode params = json.createObjectNode().put("threadId", collector.threadId)
                .put("expectedTurnId", collector.turnId);
        ArrayNode values = params.putArray("input");
        if (!input.text().isBlank()) values.addObject().put("type", "text").put("text", input.text());
        for (var image : input.images()) values.addObject().put("type", "image").put("url", dataUrl(image));
        long id = requestIds.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        // Executed by the protocol reader before any subsequent output notifications.
        var acknowledged = future.thenApply(result -> {
            collector.listener.onSteeringAccepted(input);
            return result;
        });
        pending.put(id, future);
        try {
            ObjectNode message = json.createObjectNode().put("id", id).put("method", "turn/steer");
            message.set("params", params); write(message);
            acknowledged.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (java.util.concurrent.ExecutionException failure) {
            if (failure.getCause() instanceof RpcFailure rpc && rpc.code == -32601) return false;
            throw failure;
        } finally { pending.remove(id); }
    }

    private static final class RpcFailure extends IOException {
        final int code;
        RpcFailure(int code, String message) { super(message); this.code = code; }
    }

    private static String dataUrl(AiImageInput image) {
        return "data:" + image.mediaType() + ";base64," + Base64.getEncoder().encodeToString(image.data());
    }

    /** Requests protocol-level cancellation and kills a wedged App Server as a bounded fallback. */
    public boolean interruptActiveTurn() {
        TurnCollector collector = activeTurn;
        if (collector == null || collector.completion.isDone()) return false;
        collector.interruptRequested.set(true);
        Thread.ofVirtual().name("fastremote-codex-interrupt").start(() -> interruptAndBound(collector));
        return true;
    }

    private void interruptAndBound(TurnCollector collector) {
        try {
            String turnId = collector.turnId;
            if (!turnId.isBlank()) {
                ObjectNode params = json.createObjectNode()
                        .put("threadId", collector.threadId).put("turnId", turnId);
                request("turn/interrupt", params, 2);
            }
            collector.completion.get(2, TimeUnit.SECONDS);
            return;
        } catch (CancellationException expected) {
            return;
        } catch (Exception ignored) {
            // A tool transport can be wedged. The process fallback below bounds cancellation latency.
        }
        if (activeTurn != collector || collector.completion.isDone()) return;
        Process current = process;
        if (current != null && current.isAlive()) current.destroyForcibly();
        failAll(new CancellationException("Codex turn interrupted"));
    }

    private JsonNode request(String method, JsonNode params) throws Exception {
        return request(method, params, REQUEST_TIMEOUT_SECONDS);
    }

    private JsonNode request(String method, JsonNode params, long timeoutSeconds) throws Exception {
        ensureStarted();
        long id = requestIds.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        ObjectNode request = json.createObjectNode().put("id", id).put("method", method);
        request.set("params", params);
        try {
            write(request);
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            throw new IOException("Codex App Server request timed out: " + method, timeout);
        } finally {
            pending.remove(id);
        }
    }

    private void ensureStarted() throws Exception {
        if (closed.get()) throw new IllegalStateException("Codex App Server client is closed");
        if (process != null && process.isAlive()) return;
        synchronized (lifecycleLock) {
            if (process != null && process.isAlive()) return;
            Files.createDirectories(codexHome);
            String executable = CodexExecutable.resolve(codexHome);
            List<String> command = appServerCommand(executable);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().put("CODEX_HOME", codexHome.toString());
            builder.environment().put("FASTREMOTE_MCP_TOKEN", mcpToken);
            ensureLoopbackProxyBypass(builder.environment());
            Process started;
            try {
                started = builder.start();
            } catch (IOException missing) {
                throw new IOException("Codex component is unavailable. Install Codex or configure "
                        + "-Dfastremote.codex.executable=/absolute/path/to/codex", missing);
            }
            process = started;
            writer = started.outputWriter(StandardCharsets.UTF_8);
            Thread.ofPlatform().name("fastremote-codex-protocol").daemon(true)
                    .start(() -> readProtocol(started.inputReader(StandardCharsets.UTF_8)));
            Thread.ofPlatform().name("fastremote-codex-stderr").daemon(true)
                    .start(() -> drainErrors(started.errorReader(StandardCharsets.UTF_8)));
            ObjectNode initialize = json.createObjectNode();
            initialize.putObject("clientInfo").put("name", "FastRemote").put("title", "FastRemote").put("version", "1");
            requestWithoutStart("initialize", initialize);
            write(json.createObjectNode().put("method", "initialized"));
        }
    }

    List<String> appServerCommand(String executable) {
        List<String> command = new java.util.ArrayList<>(List.of(executable,
                "-c", "mcp_servers.fastremote.url=\"" + mcpEndpoint + "\"",
                "-c", "mcp_servers.fastremote.bearer_token_env_var=\"FASTREMOTE_MCP_TOKEN\"",
                // Codex must not stop at its own MCP approval layer. FastRemote performs the real,
                // capability-specific approval after the loopback request reaches its gateway.
                "-c", "mcp_servers.fastremote.default_tools_approval_mode=\"approve\"",
                "-c", "mcp_servers.fastremote.required=true",
                "-c", "mcp_servers.fastremote.tool_timeout_sec=180"));
        for (String feature : List.of("shell_tool", "unified_exec", "browser_use", "computer_use",
                "apps", "plugins", "hooks", "multi_agent", "image_generation", "view_image",
                "workspace_dependencies", "skill_search")) {
            command.add("--disable");
            command.add(feature);
        }
        command.add("app-server");
        return List.copyOf(command);
    }

    static void ensureLoopbackProxyBypass(Map<String, String> environment) {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        addProxyBypassHosts(hosts, environment.get("NO_PROXY"));
        addProxyBypassHosts(hosts, environment.get("no_proxy"));
        hosts.add("127.0.0.1");
        hosts.add("localhost");
        hosts.add("::1");
        String value = String.join(",", hosts);
        environment.put("NO_PROXY", value);
        environment.put("no_proxy", value);
    }

    private static void addProxyBypassHosts(LinkedHashSet<String> hosts, String configured) {
        if (configured == null || configured.isBlank()) return;
        for (String host : configured.split(",")) {
            String value = host.strip();
            if (!value.isEmpty()) hosts.add(value);
        }
    }

    private JsonNode requestWithoutStart(String method, JsonNode params) throws Exception {
        long id = requestIds.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        ObjectNode request = json.createObjectNode().put("id", id).put("method", method);
        request.set("params", params);
        try {
            write(request);
            return future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            pending.remove(id);
        }
    }

    private void readProtocol(BufferedReader input) {
        try (input) {
            String line;
            while ((line = input.readLine()) != null) {
                receivedBytes.addAndGet(line.getBytes(StandardCharsets.UTF_8).length + 1L);
                dispatch(json.readTree(line));
            }
            failAll(new IOException("Codex App Server stopped unexpectedly"));
        } catch (Exception failure) {
            failAll(new IOException("Codex App Server protocol failed", failure));
        }
    }

    private void dispatch(JsonNode message) throws IOException {
        if (message.has("id") && (message.has("result") || message.has("error"))) {
            CompletableFuture<JsonNode> future = pending.get(message.path("id").asLong());
            if (future == null) return;
            if (message.has("error")) future.completeExceptionally(
                    new RpcFailure(message.path("error").path("code").asInt(), message.path("error").path("message").asText("Codex request failed")));
            else future.complete(message.path("result"));
            return;
        }
        String method = message.path("method").asText();
        JsonNode params = message.path("params");
        if (declineNativeApproval(message, method)) return;
        if (method.equals("account/login/completed")) {
            CompletableFuture<LoginResult> future = loginCompletion;
            if (future != null && activeLoginId.equals(params.path("loginId").asText())) {
                future.complete(new LoginResult(params.path("success").asBoolean(false),
                        Optional.ofNullable(params.path("error").textValue())));
            }
            return;
        }
        TurnCollector collector = activeTurn;
        if (collector == null || !collector.threadId.equals(params.path("threadId").asText())) return;
        String notifiedTurn = params.path("turnId").asText(params.path("turn").path("id").asText(""));
        if (!collector.turnId.isBlank() && !notifiedTurn.isBlank() && !collector.turnId.equals(notifiedTurn)) return;
        switch (method) {
            case "turn/started" -> collector.turnId = params.path("turn").path("id").asText();
            case "item/agentMessage/delta" -> {
                collector.segment(params.path("itemId").asText());
                collector.text(params.path("delta").asText());
            }
            case "item/mcpToolCall/progress" -> collector.listener.onToolProgress(
                    params.path("itemId").asText(), params.path("message").asText());
            case "item/started" -> collector.item(params.path("item"), false);
            case "item/completed" -> collector.item(params.path("item"), true);
            case "item/reasoning/summaryTextDelta", "item/reasoning/textDelta" ->
                    collector.reasoning(params.path("delta").asText());
            case "item/reasoning/summaryPartAdded" -> collector.reasoningPartAdded();
            case "turn/completed" -> collector.complete(params.path("turn"));
            default -> { }
        }
    }

    private boolean declineNativeApproval(JsonNode message, String method) throws IOException {
        if (!message.has("id")) return false;
        ObjectNode result = switch (method) {
            case "item/commandExecution/requestApproval", "item/fileChange/requestApproval" ->
                    json.createObjectNode().put("decision", "decline");
            case "item/permissions/requestApproval" ->
                    json.createObjectNode().set("permissions", json.createObjectNode());
            default -> null;
        };
        if (result == null) return false;
        ObjectNode response = json.createObjectNode();
        response.set("id", message.get("id"));
        response.set("result", result);
        write(response);
        return true;
    }

    private void write(JsonNode value) throws IOException {
        synchronized (writeLock) {
            BufferedWriter output = writer;
            if (output == null) throw new IOException("Codex App Server is not running");
            String encoded = json.writeValueAsString(value);
            output.write(encoded);
            output.newLine();
            output.flush();
            sentBytes.addAndGet(encoded.getBytes(StandardCharsets.UTF_8).length + 1L);
        }
    }

    private static String composedPrompt(
            List<AiConversationMessage> history, String prompt, AiWorkspaceContext workspace) {
        StringBuilder result = new StringBuilder("FastRemote conversation associated host context (independent of foreground workspace): ")
                .append(workspace.kind()).append(' ').append(workspace.label()).append('\n');
        workspace.remoteHostId().ifPresent(id -> result.append("Remote host id: ").append(id).append('\n'));
        result.append("The association is optional and may be empty. It is only a reference for task context, never a mandatory target or an access restriction. Use FastRemote MCP get_conversation_context for the saved association and list_active_hosts to query all currently open hosts, including quick connections. You may select and operate other hosts for this task without changing the saved association.\n");
        if (!history.isEmpty()) result.append("Conversation so far:\n");
        for (AiConversationMessage message : history) {
            result.append(message.role() == AiConversationMessage.Role.USER ? "User: " : "Assistant: ")
                    .append(message.text()).append("\n\n");
        }
        return result.append("User: ").append(prompt).toString();
    }

    private static String requiredText(JsonNode node, String name) {
        String value = node.path(name).asText("");
        if (value.isBlank()) throw new IllegalStateException("Codex response is missing " + name);
        return value;
    }

    private static Optional<AiReasoningEffort> parseReasoningEffort(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(AiReasoningEffort.valueOf(value.strip().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException unsupported) {
            return Optional.empty();
        }
    }

    private void drainErrors(BufferedReader errors) {
        try (errors) { while (errors.readLine() != null) { } }
        catch (IOException ignored) { }
    }

    private void failAll(Exception failure) {
        pending.values().forEach(future -> future.completeExceptionally(failure));
        TurnCollector turn = activeTurn;
        if (turn != null) turn.completion.completeExceptionally(failure);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        Process current = process;
        if (current != null) current.destroy();
        failAll(new IOException("Codex App Server client closed"));
    }

    public record Account(boolean signedIn, Optional<String> email, Optional<String> planType) {
        public Account {
            email = Objects.requireNonNull(email, "email");
            planType = Objects.requireNonNull(planType, "planType");
        }
        public static Account signedOut() { return new Account(false, Optional.empty(), Optional.empty()); }
    }
    public record LoginStart(String loginId, URI authUrl, boolean browserOpened) { }
    public record LoginResult(boolean success, Optional<String> error) { }

    private static final class TurnCollector {
        private final String threadId;
        private final AiStreamListener listener;
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final CompletableFuture<AiAssistantReply> completion = new CompletableFuture<>();
        private final AtomicBoolean interruptRequested = new AtomicBoolean();
        private volatile String turnId = "";
        private TurnCollector(String threadId, AiStreamListener listener) {
            this.threadId = threadId;
            this.listener = listener;
        }
        private String segmentId = "";
        private final List<AiToolExecution> tools = new java.util.ArrayList<>();
        private void segment(String id) {
            if (!id.isBlank() && !id.equals(segmentId)) {
                segmentId = id; listener.onSegmentStarted(id);
            }
        }
        private void item(JsonNode item, boolean complete) {
            String type = item.path("type").asText(), id = item.path("id").asText();
            if (type.equals("agentMessage") && !complete) segment(id);
            if (!type.equals("mcpToolCall")) return;
            String tool = item.path("tool").asText("MCP");
            if (!complete) listener.onToolStarted(id, tool);
            else {
                boolean ok = !item.path("status").asText().equals("failed") && (item.path("error").isNull() || item.path("error").isMissingNode());
                var execution = new AiToolExecution(tool, tool, ok);
                tools.add(execution); listener.onToolFinished(id, execution);
            }
        }
        private void text(String delta) { text.append(delta); listener.onTextDelta(delta); }
        private void reasoning(String delta) { reasoning.append(delta); listener.onReasoningDelta(delta); }
        private void reasoningPartAdded() {
            if (reasoning.isEmpty() || reasoning.toString().endsWith("\n\n")) return;
            String separator = reasoning.toString().endsWith("\n") ? "\n" : "\n\n";
            reasoning.append(separator);
            listener.onReasoningDelta(separator);
        }
        private void complete(JsonNode turn) {
            String status = turn.path("status").asText();
            if (status.equals("completed")) completion.complete(new AiAssistantReply(text.toString(), reasoning.toString(), List.copyOf(tools)));
            else if (status.equals("interrupted") || interruptRequested.get()) {
                completion.completeExceptionally(new CancellationException("Codex turn interrupted"));
            }
            else completion.completeExceptionally(new IOException("Codex turn ended with status: " + status));
        }
    }
}
