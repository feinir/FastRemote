// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.ModelProviderType;
import com.ynpe.fastremote.domain.ai.AiInferenceOptions;
import com.ynpe.fastremote.domain.ai.AiWorkspaceContext;
import com.ynpe.fastremote.domain.ai.AiReasoningEffort;
import com.ynpe.fastremote.domain.automation.AutomationGateway;
import com.ynpe.fastremote.domain.automation.AutomationLocalEntry;
import com.ynpe.fastremote.domain.automation.AutomationLocalListing;
import com.ynpe.fastremote.domain.automation.AutomationOperationId;
import com.ynpe.fastremote.domain.automation.AutomationOperationStatus;
import com.ynpe.fastremote.domain.automation.AutomationHost;
import com.ynpe.fastremote.domain.automation.AutomationSource;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.terminal.RemoteCommandResult;
import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiAgentProtocolTest {
    @Test
    void localBackgroundOutputAndNoticeReachTheModelWithoutRemoteHostSelection() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        List<String> responses = List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"local-1\",\"type\":\"function\",\"function\":{\"name\":\"run_local_command\",\"arguments\":\"{\\\"command\\\":\\\"printf result\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"result\"}}]}");
        StubGateway gateway = new StubGateway() {
            @Override public CompletionStage<com.ynpe.fastremote.domain.terminal.LocalCommandResult> runLocalCommand(
                    AutomationSource source, String command, Optional<Path> directory) {
                assertEquals("printf result", command); assertTrue(directory.isEmpty());
                return CompletableFuture.completedFuture(new com.ynpe.fastremote.domain.terminal.LocalCommandResult(
                        "fixture-local-result" + "\0".repeat(90000), "\0".repeat(90000), Optional.of(0), "SUCCEEDED", false, true, "/home/test",
                        List.of("/bin/sh", "-c"), "not displayed in a terminal Tab"));
            }
        };
        try (ServerFixture server = new ServerFixture("/v1/chat/completions", request, responses)) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            new AiAgent(gateway).reply(provider, "qwen", new char[0], List.of(), "需要本机准确返回值", Optional.empty());
            assertTrue(request.get().contains("fixture-local-result"));
            assertTrue(request.get().contains("not displayed in a terminal Tab"));
            assertTrue(request.get().contains(com.ynpe.fastremote.domain.automation.AutomationExecutionGuidance.LOCAL_BACKGROUND_COMMAND));
            var body = new ObjectMapper().readTree(request.get());
            var toolMessage = java.util.stream.StreamSupport.stream(body.path("messages").spliterator(), false)
                    .filter(message -> message.path("role").asText().equals("tool")).findFirst().orElseThrow();
            var result = new ObjectMapper().readTree(toolMessage.path("content").asText());
            assertTrue(result.path("truncated").asBoolean()); assertEquals(0, result.path("exitCode").asInt());
            assertTrue(result.path("notice").asText().contains("not displayed"));
            assertTrue(gateway.commandHost.get() == null); assertTrue(gateway.localVisibleCommand.get() == null);
        }
    }

    @Test
    void sendsObservedTerminalResultBackToTheModel() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        List<String> responses = List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"result-1\",\"type\":\"function\",\"function\":{\"name\":\"read_workspace_operation\",\"arguments\":\"{\\\"operation_id\\\":\\\"run-1\\\",\\\"after\\\":0,\\\"maximum\\\":1024,\\\"wait_ms\\\":0}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"已读取结果\"}}]}");
        var gateway = new StubGateway() {
            @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationSnapshot> readWorkspaceOperation(
                    AutomationSource source, String operation, long after, int maximum, int waitMillis) {
                assertEquals("run-1", operation);
                var output = new com.ynpe.fastremote.domain.terminal.TerminalOutput("g", 0, 9, 9,
                        false, false, "fixture-output", "", false, "READY", true);
                return CompletableFuture.completedFuture(new com.ynpe.fastremote.domain.automation.WorkspaceOperationSnapshot(
                        operation, "pane", "g", "TERMINAL", "FAILED", Optional.of(2), Optional.of(output),
                        0, 0, List.of(), false, ""));
            }
        };
        try (ServerFixture server = new ServerFixture("/v1/chat/completions", request, responses)) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            new AiAgent(gateway).reply(provider, "qwen", new char[0], List.of(), "读取终端结果", Optional.empty());
            assertTrue(request.get().contains("fixture-output"));
            assertTrue(request.get().contains("FAILED"));
        }
    }

    @Test
    void steerAfterAToolSkipsRemainingEffectsAndContinuesWithNewContext() throws Exception {
        var request = new AtomicReference<String>();
        String call = "data: {\"choices\":[{\"delta\":{\"content\":\"Inspecting\",\"tool_calls\":[{\"index\":0,\"id\":\"call-a\",\"function\":{\"name\":\"list_hosts\",\"arguments\":\"{}\"}},{\"index\":1,\"id\":\"call-b\",\"function\":{\"name\":\"list_hosts\",\"arguments\":\"{}\"}}]}}]}\n\ndata: [DONE]\n\n";
        String answer = "data: {\"choices\":[{\"delta\":{\"content\":\"Updated\"}}]}\n\ndata: [DONE]\n\n";
        var inbox = new AiTurnInbox();
        var events = new ArrayList<String>();
        var input = new AiSteeringInput(UUID.randomUUID(), "Only use host B", List.of());
        try (var server = ServerFixture.streaming("/v1/chat/completions", request, List.of(call, answer));
             var agent = new AiAgent(new StubGateway())) {
            var provider = new ModelProviderConfig("fixture", "Fixture", ModelProviderType.OPENAI_COMPATIBLE,
                    server.baseUri(), "test", Optional.empty(), false);
            var reply = agent.replyStreaming(provider, "test", new char[0], List.of(), "Inspect", List.of(),
                    AiWorkspaceContext.none(), AiInferenceOptions.DEFAULT, new AiStreamListener() {
                        @Override public void onToolStarted(String id, String name) { events.add("start:" + id); }
                        @Override public void onToolFinished(String id, AiToolExecution execution) {
                            events.add("finish:" + id);
                            if (id.equals("call-a")) assertTrue(inbox.offer(input));
                        }
                        @Override public void onSteeringAccepted(AiSteeringInput value) { events.add("steer:" + value.text()); }
                    }, 8, inbox);
            assertTrue(reply.text().contains("Updated"));
            assertEquals(List.of("start:call-a", "finish:call-a", "finish:call-b", "steer:Only use host B"), events);
            var messages = new ObjectMapper().readTree(request.get()).path("messages");
            assertEquals("Only use host B", messages.get(messages.size() - 1).path("content").asText());
            assertTrue(request.get().contains("Not executed: new user instructions"));
            assertEquals(1, reply.toolExecutions().size());
        }
    }

    @Test
    void steerDuringTextOnlyResponseTriggersContinuationWithoutReplayingOriginalPrompt() throws Exception {
        var request = new AtomicReference<String>();
        String first = "data: {\"choices\":[{\"delta\":{\"content\":\"First\"}}]}\n\ndata: [DONE]\n\n";
        String second = first.replace("First", "Second");
        var inbox = new AiTurnInbox(); var accepted = new AtomicInteger();
        try (var server = ServerFixture.streaming("/v1/chat/completions", request, List.of(first, second));
             var agent = new AiAgent(new StubGateway())) {
            var provider = new ModelProviderConfig("fixture", "Fixture", ModelProviderType.OPENAI_COMPATIBLE,
                    server.baseUri(), "test", Optional.empty(), false);
            var reply = agent.replyStreaming(provider, "test", new char[0], List.of(), "Original", List.of(),
                    AiWorkspaceContext.none(), AiInferenceOptions.DEFAULT, new AiStreamListener() {
                        @Override public void onTextDelta(String text) {
                            if (text.equals("First")) assertTrue(inbox.offer(new AiSteeringInput(UUID.randomUUID(), "Update", List.of())));
                        }
                        @Override public void onSteeringAccepted(AiSteeringInput input) { accepted.incrementAndGet(); }
                    }, 8, inbox);
            assertEquals("First\n\nSecond", reply.text()); assertEquals(1, accepted.get());
            assertEquals(1, request.get().split("Original", -1).length - 1);
            assertTrue(request.get().contains("First")); assertTrue(request.get().contains("Update"));
        }
    }

    @Test
    void contextToolUsesTheRequestSnapshotInsteadOfTheSelectedConversation() throws Exception {
        var request = new AtomicReference<String>();
        String call = "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"ctx-1\",\"function\":{\"name\":\"get_conversation_context\",\"arguments\":\"{}\"}}]}}]}\n\ndata: [DONE]\n\n";
        String answer = "data: {\"choices\":[{\"delta\":{\"content\":\"Done\"}}]}\n\ndata: [DONE]\n\n";
        java.util.UUID id = java.util.UUID.randomUUID();
        try (var server = ServerFixture.streaming("/v1/chat/completions", request, List.of(call, answer));
             var agent = new AiAgent(new StubGateway())) {
            var provider = new ModelProviderConfig("fixture", "Fixture", ModelProviderType.OPENAI_COMPATIBLE,
                    server.baseUri(), "test", Optional.empty(), false);
            var reply = agent.replyStreaming(provider, "test", new char[0], List.of(), "Read context", List.of(),
                    AiWorkspaceContext.none().withConversation(id, Optional.empty()), AiInferenceOptions.DEFAULT, AiStreamListener.NONE, 8);
            assertEquals("Done", reply.text());
            assertTrue(reply.toolExecutions().getFirst().succeeded());
            assertTrue(request.get().contains(id.toString()));
        }
    }

    @Test
    void geminiAgentKeepsSignaturesForSequentialCallsWithoutProviderIds() throws Exception {
        var request = new AtomicReference<String>();
        String first = "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"thoughtSignature\":\"signature-one\",\"functionCall\":{\"name\":\"list_hosts\",\"args\":{}}}]}}]}\n\n";
        String second = first.replace("signature-one", "signature-two");
        String last = "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Done\"}]}}]}\n\n";
        try (var server = ServerFixture.streaming("/v1/models/gemini-test:streamGenerateContent", request, List.of(first, second, last))) {
            var provider = new ModelProviderConfig("gemini", "Gemini", ModelProviderType.GEMINI_API,
                    server.baseUri(), "gemini-test", Optional.empty(), false);
            var reply = new AiAgent(new StubGateway()).replyStreaming(provider, provider.model(), "fixture-key".toCharArray(),
                    List.of(), "List hosts", Optional.empty(), AiInferenceOptions.DEFAULT, AiStreamListener.NONE);
            assertEquals("Done", reply.text());
            assertEquals(2, reply.toolExecutions().size());
            assertTrue(reply.toolExecutions().stream().allMatch(AiToolExecution::succeeded));
            assertTrue(request.get().contains("signature-one") && request.get().contains("signature-two"));
            assertTrue(!request.get().contains("gemini-call-"), "Internal correlation IDs must not be invented in the wire protocol");
        }
    }

    @Test
    void preservesKimiReasoningAcrossNonStreamingToolCalls() throws Exception {
        verifyKimiToolReasoning(false);
    }

    @Test
    void preservesKimiReasoningAcrossStreamingToolCalls() throws Exception {
        verifyKimiToolReasoning(true);
    }

    private void verifyKimiToolReasoning(boolean streaming) throws Exception {
        var json = new ObjectMapper();
        String reasoning = "  Inspect hosts\nthen continue.  ";
        var first = json.createObjectNode();
        var message = first.putArray("choices").addObject().putObject(streaming ? "delta" : "message");
        message.put("role", "assistant").putNull("content").put("reasoning_content", reasoning);
        var call = message.putArray("tool_calls").addObject().put("index", 0)
                .put("id", "kimi-call").put("type", "function");
        call.putObject("function").put("name", "list_hosts").put("arguments", "{}");
        var last = json.createObjectNode();
        last.putArray("choices").addObject().putObject(streaming ? "delta" : "message")
                .put("content", "Done");
        var responses = List.of(first.toString(), last.toString());
        if (streaming) {
            // Providers may send sparse continuation chunks with explicit null fields.
            call.withObject("function").putNull("arguments");
            var continuation = json.createObjectNode();
            var continuedCall = continuation.putArray("choices").addObject().putObject("delta")
                    .putNull("content").putArray("tool_calls").addObject().put("index", 0).putNull("id");
            continuedCall.putObject("function").putNull("name").put("arguments", "{}");
            responses = List.of("data: " + first + "\n\ndata: " + continuation + "\n\ndata: [DONE]\n\n",
                    "data: " + last + "\n\ndata: [DONE]\n\n");
        }
        var request = new AtomicReference<String>();
        try (var server = streaming
                ? ServerFixture.streaming("/v1/chat/completions", request, responses)
                : new ServerFixture("/v1/chat/completions", request, responses)) {
            var provider = new ModelProviderConfig("kimi", "Kimi", ModelProviderType.KIMI_MOONSHOT,
                    server.baseUri(), "kimi-k2.7-code", Optional.empty(), false);
            var agent = new AiAgent(new StubGateway());
            var reply = streaming
                    ? agent.replyStreaming(provider, provider.model(), new char[0], List.of(), "List hosts",
                            Optional.empty(), AiInferenceOptions.DEFAULT, new AiStreamListener() { })
                    : agent.reply(provider, provider.model(), new char[0], List.of(), "List hosts", Optional.empty());
            assertEquals("Done", reply.text());
            assertEquals(1, reply.toolExecutions().size());
            var body = json.readTree(request.get());
            var messages = body.path("messages");
            assertEquals(reasoning, messages.get(messages.size() - 2).path("reasoning_content").asText());
            assertEquals("kimi-call", messages.get(messages.size() - 1).path("tool_call_id").asText());
            assertTrue(!body.has("thinking") && !body.has("enable_thinking") && !body.has("reasoning_effort"));
        }
    }

    @Test
    void stopsImmediatelyWhenAConfiguredPermissionRejectsATool() throws Exception {
        List<String> responses = List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"local-1\",\"type\":\"function\",\"function\":{\"name\":\"list_local_directory\",\"arguments\":\"{}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"不应继续\"}}]}");
        StubGateway gateway = new StubGateway();
        gateway.localListingFailure = new SecurityException("No local access directories are configured");
        try (ServerFixture server = new ServerFixture(
                "/v1/chat/completions", new AtomicReference<>(), responses)) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new AiAgent(gateway).reply(provider, "qwen", new char[0], List.of(),
                            "上传本地文件", Optional.empty()));

            assertTrue(failure.getMessage().contains("尚未配置 AI 本地访问目录"));
        }
    }

    @Test
    void reportsAFailedTransferStatusInsteadOfPollingUntilTheToolLimit() throws Exception {
        AutomationOperationId operationId = AutomationOperationId.create();
        String response = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"id\":\"status-1\",\"type\":\"function\",\"function\":{"
                + "\"name\":\"get_operation\",\"arguments\":\"{\\\"operation_id\\\":\\\""
                + operationId + "\\\"}\"}}]}}]}";
        StubGateway gateway = new StubGateway();
        gateway.operationStatus = new AutomationOperationStatus(operationId,
                AutomationOperationStatus.State.FAILED, 0, 0,
                Optional.of("This automation capability is disabled in settings"));
        try (ServerFixture server = new ServerFixture(
                "/v1/chat/completions", new AtomicReference<>(), List.of(response))) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new AiAgent(gateway).reply(provider, "qwen", new char[0], List.of(),
                            "检查上传状态", Optional.empty()));

            assertTrue(failure.getMessage().contains("此 AI 操作已被禁用"));
        }
    }

    @Test
    void waitsInternallyForLongRunningUploadsAndDownloadsWithoutConsumingToolRounds() throws Exception {
        AutomationOperationId operationId = AutomationOperationId.create();
        String statusCall = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"id\":\"status-long\",\"type\":\"function\",\"function\":{"
                + "\"name\":\"get_operation\",\"arguments\":\"{\\\"operation_id\\\":\\\""
                + operationId + "\\\"}\"}}]}}]}";
        String finalAnswer = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"传输完成\"}}]}";
        StubGateway gateway = new StubGateway();
        AutomationOperationStatus running = new AutomationOperationStatus(operationId,
                AutomationOperationStatus.State.RUNNING, 1_000, 100, Optional.empty());
        AutomationOperationStatus succeeded = new AutomationOperationStatus(operationId,
                AutomationOperationStatus.State.SUCCEEDED, 1_000, 1_000, Optional.empty());
        gateway.operationStatuses = new ArrayList<>(java.util.Collections.nCopies(25, running));
        gateway.operationStatuses.add(succeeded);
        try (ServerFixture server = new ServerFixture(
                "/v1/chat/completions", new AtomicReference<>(), List.of(statusCall, finalAnswer))) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);

            AiAssistantReply reply = new AiAgent(gateway, LocalRuntimeEnvironment.current(),
                    Duration.ZERO, Duration.ofSeconds(2)).reply(
                    provider, "qwen", new char[0], List.of(), "等待传输完成", Optional.empty());

            assertEquals("传输完成", reply.text());
            assertEquals(26, gateway.operationReads.get());
            assertEquals(1, reply.toolExecutions().size());
            assertEquals("get_operation", reply.toolExecutions().getFirst().name());
            assertTrue(reply.toolExecutions().getFirst().succeeded());
        }
    }

    @Test
    void stopsTheModelLoopWhenTheBoundedTransferWaitExpires() throws Exception {
        AutomationOperationId operationId = AutomationOperationId.create();
        String statusCall = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"id\":\"status-timeout\",\"type\":\"function\",\"function\":{"
                + "\"name\":\"get_operation\",\"arguments\":\"{\\\"operation_id\\\":\\\""
                + operationId + "\\\"}\"}}]}}]}";
        StubGateway gateway = new StubGateway();
        gateway.operationStatuses = List.of(new AutomationOperationStatus(operationId,
                AutomationOperationStatus.State.RUNNING, 1_000, 100, Optional.empty()));
        try (ServerFixture server = new ServerFixture(
                "/v1/chat/completions", new AtomicReference<>(), List.of(statusCall))) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new AiAgent(gateway, LocalRuntimeEnvironment.current(),
                            Duration.ZERO, Duration.ofNanos(1)).reply(
                            provider, "qwen", new char[0], List.of(), "等待传输完成", Optional.empty()));

            assertTrue(failure.getMessage().contains("等待一小时后传输仍未完成"));
        }
    }

    @Test
    void workspaceContextDescriptionDistinguishesLocalAndRemoteTabs() {
        assertEquals("本地终端（Shell）", AiAgent.workspaceContextDescription(
                AiWorkspaceContext.localTerminal("Shell")));
        assertEquals("Associated host: SFTP · staging", AiAgent.workspaceContextDescription(
                AiWorkspaceContext.remote("SFTP · staging", Optional.empty())));
    }
    @Test
    void streamsOpenAiReasoningBeforeTheFinalAnswer() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        String stream = """
                data: {"type":"response.reasoning_summary_text.delta","delta":"正在检查"}

                data: {"type":"response.reasoning_summary_text.delta","delta":"会话"}

                data: {"type":"response.output_text.delta","delta":"检查"}

                data: {"type":"response.output_text.delta","delta":"完成"}

                data: [DONE]

                """;
        try (ServerFixture server = ServerFixture.streaming("/v1/responses", request, List.of(stream))) {
            var provider = new ModelProviderConfig("openai", "OpenAI", ModelProviderType.OPENAI_RESPONSES,
                    server.baseUri(), "gpt-test", Optional.empty(), false);
            StringBuilder events = new StringBuilder();
            AiAssistantReply reply = new AiAgent(new StubGateway()).replyStreaming(
                    provider, "gpt-test", new char[0], List.of(), "检查会话", Optional.empty(),
                    new AiInferenceOptions(Optional.of(AiReasoningEffort.MEDIUM), true),
                    new AiStreamListener() {
                        @Override public void onReasoningDelta(String delta) { events.append("R:").append(delta).append('|'); }
                        @Override public void onTextDelta(String delta) { events.append("T:").append(delta).append('|'); }
                    });

            assertEquals("正在检查会话", reply.reasoningSummary());
            assertEquals("检查完成", reply.text());
            assertEquals("R:正在检查|R:会话|T:检查|T:完成|", events.toString());
            assertTrue(new ObjectMapper().readTree(request.get()).path("stream").asBoolean());
        }
    }

    @Test
    void sendsImagesAsResponsesInputImageDataUrls() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        String stream = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"看到了\"}\n\n"
                + "data: [DONE]\n\n";
        try (ServerFixture server = ServerFixture.streaming("/v1/responses", request, List.of(stream))) {
            var provider = new ModelProviderConfig("openai", "OpenAI", ModelProviderType.OPENAI_RESPONSES,
                    server.baseUri(), "gpt-test", Optional.empty(), false);
            var image = new AiImageInput("image/png", new byte[] {1, 2, 3}, 1, 1);
            var reply = new AiAgent(new StubGateway()).replyStreaming(provider, "gpt-test", new char[0],
                    List.of(), "这是什么", List.of(image), AiWorkspaceContext.none(),
                    AiInferenceOptions.DEFAULT, AiStreamListener.NONE, 8);

            assertEquals("看到了", reply.text());
            var body = new ObjectMapper().readTree(request.get());
            var content = body.path("input").path(body.path("input").size() - 1).path("content");
            assertEquals("input_text", content.path(0).path("type").asText());
            assertEquals("这是什么", content.path(0).path("text").asText());
            assertEquals("input_image", content.path(1).path("type").asText());
            assertEquals("data:image/png;base64,AQID", content.path(1).path("image_url").asText());
        }
    }

    @Test
    void assemblesStreamedCompatibleToolCallsAndContinuesWithText() throws Exception {
        String toolStream = """
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"list-1","function":{"name":"list_","arguments":"{"}}]}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"hosts","arguments":"}"}}]}}]}

                data: [DONE]

                """;
        String answerStream = """
                data: {"choices":[{"delta":{"content":"共有 "}}]}

                data: {"choices":[{"delta":{"content":"1 个会话"}}]}

                data: [DONE]

                """;
        try (ServerFixture server = ServerFixture.streaming(
                "/v1/chat/completions", new AtomicReference<>(), List.of(toolStream, answerStream))) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            StringBuilder text = new StringBuilder();
            List<String> tools = new java.util.ArrayList<>();
            AiAssistantReply reply = new AiAgent(new StubGateway()).replyStreaming(
                    provider, "qwen", new char[0], List.of(), "列出会话", Optional.empty(),
                    AiInferenceOptions.DEFAULT, new AiStreamListener() {
                        @Override public void onTextDelta(String delta) { text.append(delta); }
                        @Override public void onToolExecution(AiToolExecution execution) {
                            tools.add(execution.name());
                        }
                    });

            assertEquals("共有 1 个会话", text.toString());
            assertEquals("共有 1 个会话", reply.text());
            assertEquals(List.of("list_hosts"), tools);
        }
    }

    @Test
    void streamsOllamaReasoningWithSelectableEffort() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        String stream = """
                data: {"choices":[{"delta":{"reasoning":"先检查"}}]}

                data: {"choices":[{"delta":{"reasoning":"会话","content":"检查"}}]}

                data: {"choices":[{"delta":{"content":"完成"}}]}

                data: [DONE]

                """;
        try (ServerFixture server = ServerFixture.streaming("/v1/chat/completions", request, List.of(stream))) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            StringBuilder events = new StringBuilder();
            AiAssistantReply reply = new AiAgent(new StubGateway()).replyStreaming(
                    provider, "qwen", new char[0], List.of(), "检查会话", Optional.empty(),
                    new AiInferenceOptions(Optional.of(AiReasoningEffort.MEDIUM), true), new AiStreamListener() {
                        @Override public void onReasoningDelta(String delta) {
                            events.append("R:").append(delta).append('|');
                        }
                        @Override public void onTextDelta(String delta) {
                            events.append("T:").append(delta).append('|');
                        }
                    });

            assertEquals("先检查会话", reply.reasoningSummary());
            assertEquals("检查完成", reply.text());
            assertEquals("R:先检查|R:会话|T:检查|T:完成|", events.toString());
            assertEquals("medium", new ObjectMapper().readTree(request.get())
                    .path("reasoning_effort").asText());
        }
    }

    @Test
    void automationJsonSupportsOptionalErrorsAndOptionalTimestamps() throws Exception {
        AutomationOperationStatus status = new AutomationOperationStatus(AutomationOperationId.create(),
                AutomationOperationStatus.State.SUCCEEDED, 12, 12, Optional.empty());
        RemoteFileEntry remote = new RemoteFileEntry("file.txt", "/data/file.txt", false, false, 12,
                Optional.of(Instant.parse("2026-08-24T16:00:00Z")));

        var json = AiAgent.automationJsonMapper();
        assertTrue(json.writeValueAsString(status).contains("\"error\":null"));
        assertTrue(json.writeValueAsString(remote).contains("modifiedAt"));
    }

    @Test
    void translatesCommonToolContractFailuresIntoActionableChineseMessages() {
        assertEquals("当前没有选中已保存的远程主机，请先调用 list_hosts 和 select_host",
                AiAgent.friendlyToolError("No saved host is selected"));
        assertEquals("FastRemote 无法打开该保存主机的 SFTP 标签",
                AiAgent.friendlyToolError("FastRemote could not open the saved host SFTP tab"));
        assertEquals("切换目录前请先调用 open_sftp 打开该主机的 SFTP 标签",
                AiAgent.friendlyToolError("Open the saved host SFTP tab before changing its directory"));
        assertEquals("本地路径不存在或无法读取，请先调用 list_local_directory 并原样使用返回路径",
                AiAgent.friendlyToolError("Local path cannot be validated"));
        assertEquals("模型请求了 FastRemote 不支持的工具：unsupported_tool",
                AiAgent.friendlyToolError("Unknown tool: unsupported_tool"));
    }

    @Test
    void generatesAndSanitizesAConversationTitleWithoutAutomationTools() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        try (ServerFixture server = new ServerFixture("/v1/chat/completions", request,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"**巡检服务器磁盘**。\"}}]}")) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            String title = new AiAgent(new StubGateway()).generateChatTitle(
                    provider, "qwen", new char[0], "检查 hkvps 的磁盘占用", "根分区使用率 14%");

            assertEquals("巡检服务器磁盘", title);
            var body = new ObjectMapper().readTree(request.get());
            assertTrue(body.path("tools").isMissingNode());
            assertTrue(body.path("messages").path(0).path("content").asText().contains("Return only the title"));
        }
        assertEquals("新会话", AiAgent.sanitizeChatTitle("` `", ""));
    }

    @Test
    void usesResponsesPayloadForNativeOpenAiProvider() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        try (ServerFixture server = new ServerFixture("/v1/responses", request,
                "{\"output\":[{\"type\":\"reasoning\",\"summary\":[{\"type\":\"summary_text\",\"text\":\"检查了可用工具\"}]},{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"完成\"}]}]}")) {
            var provider = new ModelProviderConfig("openai", "OpenAI", ModelProviderType.OPENAI_RESPONSES,
                    server.baseUri(), "gpt-test", Optional.empty(), false);
            AiAssistantReply reply = new AiAgent(new StubGateway()).reply(provider, "gpt-test", new char[0], List.of(),
                    "你好", Optional.empty(), new AiInferenceOptions(Optional.of(AiReasoningEffort.HIGH), true));
            assertEquals("完成", reply.text());
            assertEquals("检查了可用工具", reply.reasoningSummary());
            var body = new ObjectMapper().readTree(request.get());
            assertTrue(body.path("input").isArray());
            assertEquals(false, body.path("store").asBoolean());
            assertEquals("high", body.path("reasoning").path("effort").asText());
            assertEquals("auto", body.path("reasoning").path("summary").asText());
            assertTrue(body.path("input").path(0).path("content").asText().contains("SSH and SFTP desktop application"));
            assertTrue(body.path("input").path(0).path("content").asText().contains("get_operation"));
            assertTrue(body.path("input").path(0).path("content").asText().contains("list_local_directory"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("get_operation"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("cancel_operation"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("list_local_directory"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("select_host"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("open_sftp"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("change_sftp_remote_directory"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("change_sftp_local_directory"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("run_command"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("run_terminal_command"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("run_local_terminal_command"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("upload_in_sftp"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("download_in_sftp"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("open_local_terminal"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("close_workspace_tabs"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("list_workspace"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("list_active_hosts"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("get_conversation_context"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("read_workspace_text"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("write_workspace_text"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("read_workspace_log"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("open_import_dialog"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("write_local_text"));
            assertTrue(body.path("tools").findValuesAsText("name").contains("delete_remote"));
            assertTrue(body.path("input").path(0).path("content").asText().contains("Visible terminal commands"));
            assertTrue(body.path("input").path(0).path("content").asText()
                    .contains("never requires list_hosts or select_host"));
            assertTrue(body.path("input").path(0).path("content").asText()
                    .contains("Call open_sftp before either SFTP directory-change tool"));
            assertTrue(body.path("input").path(0).path("content").asText()
                    .contains("read_terminal_output returns authorized bounded"));
            var localTool = body.path("tools").findParents("name").stream()
                    .filter(tool -> "list_local_directory".equals(tool.path("name").asText()))
                    .findFirst().orElseThrow();
            assertEquals("directory", localTool.path("parameters").path("required").path(0).asText());
            assertTrue(localTool.path("parameters").path("properties").path("directory").path("type").isArray());
            var visibleUploadTool = body.path("tools").findParents("name").stream()
                    .filter(tool -> "upload_in_sftp".equals(tool.path("name").asText()))
                    .findFirst().orElseThrow();
            var backgroundUploadTool = body.path("tools").findParents("name").stream()
                    .filter(tool -> "upload".equals(tool.path("name").asText()))
                    .findFirst().orElseThrow();
            assertTrue(java.util.stream.StreamSupport.stream(backgroundUploadTool.path("parameters")
                            .path("required").spliterator(), false)
                    .anyMatch(item -> "conflict_policy".equals(item.asText())));
            assertTrue(java.util.stream.StreamSupport.stream(backgroundUploadTool.path("parameters")
                            .path("properties").path("conflict_policy").path("enum").spliterator(), false)
                    .anyMatch(item -> "OVERWRITE".equals(item.asText())));
            assertTrue(java.util.stream.StreamSupport.stream(visibleUploadTool.path("parameters")
                            .path("required").spliterator(), false)
                    .anyMatch(item -> "conflict_policy".equals(item.asText())));
            assertTrue(java.util.stream.StreamSupport.stream(visibleUploadTool.path("parameters")
                            .path("properties").path("conflict_policy").path("enum").spliterator(), false)
                    .anyMatch(item -> "OVERWRITE".equals(item.asText())));
            for (var tool : body.path("tools")) {
                var properties = tool.path("parameters").path("properties");
                var required = tool.path("parameters").path("required");
                assertEquals(properties.size(), required.size(), tool.path("name").asText());
                properties.fieldNames().forEachRemaining(name ->
                        assertTrue(required.findValuesAsText("").contains(name)
                                || java.util.stream.StreamSupport.stream(required.spliterator(), false)
                                        .anyMatch(item -> name.equals(item.asText())), tool.path("name").asText()));
            }
        }
    }

    @Test
    void submitsACommandToTheVisibleTerminalTool() throws Exception {
        HostId selected = HostId.create();
        List<String> responses = List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"visible-1\",\"type\":\"function\",\"function\":{\"name\":\"run_terminal_command\",\"arguments\":\"{\\\"command\\\":\\\"df -h\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"已发送到终端\"}}]}");
        StubGateway gateway = new StubGateway();
        try (ServerFixture server = new ServerFixture("/v1/chat/completions", new AtomicReference<>(), responses)) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            AiAssistantReply reply = new AiAgent(gateway).reply(provider, "qwen", new char[0], List.of(),
                    "在终端标签里运行 df -h", Optional.of(selected));
            assertEquals("已发送到终端", reply.text());
            assertEquals(selected, gateway.visibleCommandHost.get());
            assertEquals("df -h", gateway.visibleCommand.get());
        }
    }

    @Test
    void submitsACommandToAVisibleLocalTerminalWithoutASavedHost() throws Exception {
        List<String> responses = List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"local-visible-1\",\"type\":\"function\",\"function\":{\"name\":\"run_local_terminal_command\",\"arguments\":\"{\\\"command\\\":\\\"pwd\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"已发送到本地终端\"}}]}");
        StubGateway gateway = new StubGateway();
        try (ServerFixture server = new ServerFixture("/v1/chat/completions", new AtomicReference<>(), responses)) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            AiAssistantReply reply = new AiAgent(gateway).reply(provider, "qwen", new char[0], List.of(),
                    "在本地终端运行 pwd", Optional.empty());
            assertEquals("已发送到本地终端", reply.text());
            assertEquals("pwd", gateway.localVisibleCommand.get());
            assertTrue(gateway.visibleCommandHost.get() == null);
        }
    }

    @Test
    void declaresTheMacLocalRuntimeWithoutApplyingItToRemoteCommands() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        try (ServerFixture server = new ServerFixture("/v1/chat/completions", request,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"完成\"}}]}")) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            var environment = new LocalRuntimeEnvironment("macOS", "15.6", "aarch64", "/bin/zsh", "Asia/Bangkok");

            new AiAgent(new StubGateway(), environment).reply(provider, "qwen", new char[0], List.of(),
                    "检查本机磁盘", Optional.empty());

            var body = new ObjectMapper().readTree(request.get());
            String system = body.path("messages").path(0).path("content").asText();
            assertTrue(system.contains(com.ynpe.fastremote.domain.automation.AutomationExecutionGuidance.VISIBLE_FIRST));
            assertTrue(system.contains("Use get_operation with the returned operation ID for background upload and download"));
            assertTrue(system.contains("Use read_workspace_operation with the returned operationId for visible SFTP"));
            assertTrue(system.contains("Before every upload or download, compare each source name"));
            assertTrue(system.contains(com.ynpe.fastremote.domain.automation.AutomationExecutionGuidance.REMOTE_INPUT_GUIDANCE));
            assertTrue(system.contains("operating system=macOS 15.6"));
            assertTrue(system.contains("architecture=aarch64"));
            assertTrue(system.contains("local terminal shell=/bin/zsh"));
            assertTrue(system.contains("Do not use Linux-only"));
            assertTrue(system.contains("never infer a remote server's command syntax from the local environment"));
            var localTool = body.path("tools").findParents("name").stream()
                    .filter(tool -> "run_local_terminal_command".equals(tool.path("name").asText()))
                    .findFirst().orElseThrow();
            assertTrue(localTool.path("description").asText().contains("macOS/BSD-compatible"));
            assertTrue(localTool.path("description").asText().contains("/bin/zsh"));
            assertTrue(localTool.path("description").asText().contains("visible AI-> local terminal"));
            var backgroundTool = body.path("tools").findParents("name").stream()
                    .filter(tool -> "run_command".equals(tool.path("name").asText()))
                    .findFirst().orElseThrow();
            assertEquals(com.ynpe.fastremote.domain.automation.AutomationExecutionGuidance.BACKGROUND_COMMAND,
                    backgroundTool.path("description").asText());
        }
    }

    @Test
    void carriesResponsesReasoningItemsAcrossToolRounds() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        List<String> responses = List.of(
                "{\"output\":[{\"id\":\"rs_1\",\"type\":\"reasoning\",\"summary\":[]},{\"type\":\"function_call\",\"call_id\":\"list-1\",\"name\":\"list_hosts\",\"arguments\":\"{}\"}]}",
                "{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"完成\"}]}]}");
        try (ServerFixture server = new ServerFixture("/v1/responses", request, responses)) {
            var provider = new ModelProviderConfig("openai", "OpenAI", ModelProviderType.OPENAI_RESPONSES,
                    server.baseUri(), "gpt-5.6-sol", Optional.empty(), false);
            AiAssistantReply reply = new AiAgent(new StubGateway()).reply(provider, "gpt-5.6-sol", new char[0],
                    List.of(), "列出会话", Optional.empty(),
                    new AiInferenceOptions(Optional.of(AiReasoningEffort.MEDIUM), false));

            assertEquals("完成", reply.text());
            var secondBody = new ObjectMapper().readTree(request.get());
            assertTrue(java.util.stream.StreamSupport.stream(secondBody.path("input").spliterator(), false)
                    .anyMatch(item -> "reasoning".equals(item.path("type").asText())
                            && "rs_1".equals(item.path("id").asText())));
        }
    }

    @Test
    void selectsASavedHostThenRunsACommandWithoutAnInitialTab() throws Exception {
        HostId selected = new HostId(UUID.fromString("00000000-0000-0000-0000-000000000123"));
        List<String> responses = List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"list-1\",\"type\":\"function\",\"function\":{\"name\":\"list_hosts\",\"arguments\":\"{}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"select-1\",\"type\":\"function\",\"function\":{\"name\":\"select_host\",\"arguments\":\"{\\\"host_id\\\":\\\"" + selected + "\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"command-1\",\"type\":\"function\",\"function\":{\"name\":\"run_command\",\"arguments\":\"{\\\"command\\\":\\\"uname -a\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"巡检完成\"}}]}");
        StubGateway gateway = new StubGateway();
        gateway.hostId = selected;
        try (ServerFixture server = new ServerFixture("/v1/chat/completions", new AtomicReference<>(), responses)) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            AiAssistantReply reply = new AiAgent(gateway).reply(provider, "qwen", new char[0], List.of(),
                    "巡检保存的服务器", Optional.empty());
            assertEquals("巡检完成", reply.text());
            assertEquals(selected, gateway.activated.get());
            assertEquals(selected, gateway.commandHost.get());
            assertEquals("uname -a", gateway.command.get());
        }
    }

    @Test
    void catalogToolsUseTheSameGatewayWithTheBuiltInAiSource() throws Exception {
        var json = new ObjectMapper();
        var call = json.createObjectNode();
        var choice = call.putArray("choices").addObject().putObject("message").put("role", "assistant");
        choice.putArray("tool_calls").addObject().put("id", "catalog-1").put("type", "function")
                .putObject("function").put("name", "add_host_group")
                .put("arguments", "{\"name\":\"测试组\",\"parent_id\":null,\"color\":null}");
        var invoked = new AtomicReference<com.ynpe.fastremote.domain.automation.AutomationCatalogTool>();
        var gateway = new StubGateway() {
            @Override public java.util.concurrent.CompletionStage<Object> catalogTool(AutomationSource source,
                    com.ynpe.fastremote.domain.automation.AutomationCatalogTool tool,
                    java.util.Map<String, Object> arguments) {
                assertEquals(AutomationSource.BUILT_IN_AI, source);
                tool.validate(arguments);
                assertEquals("测试组", arguments.get("name"));
                invoked.set(tool);
                return CompletableFuture.completedFuture(java.util.Map.of("name", "测试组"));
            }
        };
        var captured = new AtomicReference<String>();
        try (var server = new ServerFixture("/v1/chat/completions", captured, List.of(call.toString(),
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"完成\"}}]}"));
             var agent = new AiAgent(gateway)) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            agent.reply(provider, "qwen", new char[0], List.of(), "新建主机分组", Optional.empty());
            var body = json.readTree(captured.get());
            var addGroup = body.path("tools").findParents("name").stream()
                    .filter(tool -> "add_host_group".equals(tool.path("name").asText()))
                    .findFirst().orElseThrow();
            assertEquals(3, addGroup.path("parameters").path("required").size());
            assertEquals("null", addGroup.path("parameters").path("properties").path("parent_id").path("type").get(1).asText());
            assertEquals(com.ynpe.fastremote.domain.automation.AutomationCatalogTool.ADD_HOST_GROUP, invoked.get());
        }
    }

    @Test
    void catalogSecretsReachTheGatewayWithoutBeingReplayedToTheModel() throws Exception {
        var json = new ObjectMapper();
        String secret = UUID.randomUUID().toString();
        String arguments = json.createObjectNode().put("label", "Fixture").put("username", "test")
                .put("password", secret).toString();
        for (var providerType : List.of(ModelProviderType.OLLAMA, ModelProviderType.OPENAI_RESPONSES)) {
            boolean responses = providerType == ModelProviderType.OPENAI_RESPONSES;
            var first = json.createObjectNode();
            var second = json.createObjectNode();
            if (responses) {
                first.putArray("output").addObject().put("type", "function_call").put("call_id", "catalog-1")
                        .put("name", "add_credential").put("arguments", arguments);
                second.putArray("output").addObject().put("type", "message")
                        .putArray("content").addObject().put("type", "output_text").put("text", "Saved");
            } else {
                first.putArray("choices").addObject().putObject("message").putArray("tool_calls").addObject()
                        .put("id", "catalog-1").put("type", "function").putObject("function")
                        .put("name", "add_credential").put("arguments", arguments);
                second.putArray("choices").addObject().putObject("message").put("content", "Saved");
            }
            var invoked = new java.util.concurrent.atomic.AtomicBoolean();
            var gateway = new StubGateway() {
                @Override public java.util.concurrent.CompletionStage<Object> catalogTool(AutomationSource source,
                        com.ynpe.fastremote.domain.automation.AutomationCatalogTool tool,
                        java.util.Map<String, Object> inputs) {
                    tool.validate(inputs);
                    assertTrue(secret.equals(inputs.get("password")));
                    invoked.set(true);
                    return CompletableFuture.completedFuture(java.util.Map.of("label", "Fixture"));
                }
            };
            var captured = new AtomicReference<String>();
            try (var server = new ServerFixture(responses ? "/v1/responses" : "/v1/chat/completions", captured,
                    List.of(first.toString(), second.toString()));
                 var agent = new AiAgent(gateway)) {
                var provider = new ModelProviderConfig("fixture", "Fixture", providerType,
                        server.baseUri(), "fixture", Optional.empty(), false);
                var reply = agent.reply(provider, "fixture", new char[0], List.of(), "Save SSH credential", Optional.empty());
                assertTrue(invoked.get());
                assertEquals("Saved", reply.text());
                assertFalse(captured.get().contains(secret));
                var replayed = json.readTree(captured.get()).findValuesAsText("arguments");
                assertEquals(1, replayed.size());
                assertTrue(json.readTree(replayed.getFirst()).path("password").isNull());
            }
        }
    }

    @Test
    void passesHostSearchAndPageArgumentsToTheCompleteCatalogQuery() throws Exception {
        List<String> responses = List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{"
                        + "\"id\":\"list-page\",\"type\":\"function\",\"function\":{\"name\":\"list_hosts\","
                        + "\"arguments\":\"{\\\"query\\\":\\\"9.67-lan\\\",\\\"group_id\\\":\\\"00000000-0000-0000-0000-000000000123\\\",\\\"offset\\\":200,\\\"limit\\\":25}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"完成\"}}]}");
        StubGateway gateway = new StubGateway();
        AtomicReference<String> captured = new AtomicReference<>();
        try (ServerFixture server = new ServerFixture("/v1/chat/completions", captured, responses)) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            new AiAgent(gateway).reply(provider, "qwen", new char[0], List.of(),
                    "查找 9.67-lan", Optional.empty());
        }

        assertEquals(Optional.of("9.67-lan"), gateway.hostQuery);
        assertEquals(Optional.of(com.ynpe.fastremote.domain.catalog.GroupId.parse("00000000-0000-0000-0000-000000000123")), gateway.hostGroup);
        assertEquals(200, gateway.hostOffset);
        assertEquals(25, gateway.hostLimit);
        var request = new ObjectMapper().readTree(captured.get());
        var toolMessage = java.util.stream.StreamSupport.stream(request.path("messages").spliterator(), false)
                .filter(message -> message.path("role").asText().equals("tool")).findFirst().orElseThrow();
        var result = new ObjectMapper().readTree(toolMessage.path("content").asText());
        assertEquals("vps", result.get(0).path("groupName").asText());
        assertEquals("vps", result.get(0).path("groupPath").asText());
    }

    @Test
    void resolvesAUniqueExactHostNameWhenTheModelDoesNotReturnTheUuid() throws Exception {
        HostId selected = new HostId(UUID.fromString("00000000-0000-0000-0000-000000000789"));
        List<String> responses = List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"select-name-1\",\"type\":\"function\",\"function\":{\"name\":\"select_host\",\"arguments\":\"{\\\"host_id\\\":\\\"server\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"会话已打开\"}}]}");
        StubGateway gateway = new StubGateway();
        gateway.hostId = selected;
        try (ServerFixture server = new ServerFixture("/v1/chat/completions", new AtomicReference<>(), responses)) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            AiAssistantReply reply = new AiAgent(gateway).reply(provider, "qwen", new char[0], List.of(),
                    "打开 server", Optional.empty());
            assertEquals("会话已打开", reply.text());
            assertEquals(selected, gateway.activated.get());
        }
    }

    @Test
    void opensSftpThenChangesItsRemoteAndLocalDirectories() throws Exception {
        HostId selected = new HostId(UUID.fromString("00000000-0000-0000-0000-000000000456"));
        List<String> responses = List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"sftp-1\",\"type\":\"function\",\"function\":{\"name\":\"open_sftp\",\"arguments\":\"{}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"remote-1\",\"type\":\"function\",\"function\":{\"name\":\"change_sftp_remote_directory\",\"arguments\":\"{\\\"directory\\\":\\\"Data\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"local-1\",\"type\":\"function\",\"function\":{\"name\":\"change_sftp_local_directory\",\"arguments\":\"{\\\"directory\\\":\\\"/allowed/Desktop\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"SFTP 已打开并切换目录\"}}]}");
        StubGateway gateway = new StubGateway();
        try (ServerFixture server = new ServerFixture("/v1/chat/completions", new AtomicReference<>(), responses)) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            AiAssistantReply reply = new AiAgent(gateway).reply(provider, "qwen", new char[0], List.of(),
                    "打开 SFTP 并切换目录", Optional.of(selected));
            assertEquals("SFTP 已打开并切换目录", reply.text());
            assertEquals(selected, gateway.openedSftpHost.get());
            assertEquals(selected, gateway.changedSftpRemoteHost.get());
            assertEquals("Data", gateway.changedSftpRemoteDirectory.get());
            assertEquals(selected, gateway.changedSftpLocalHost.get());
            assertEquals(Path.of("/allowed/Desktop"), gateway.changedSftpLocalDirectory.get());
        }
    }

    @Test
    void usesChatCompletionsForOllama() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        try (ServerFixture server = new ServerFixture("/v1/chat/completions", request,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"本地完成\"}}]}")) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            AiAgent agent = new AiAgent(new StubGateway());
            AiAssistantReply reply = agent.reply(provider, "qwen", new char[0], List.of(),
                    "你好", Optional.empty());
            assertEquals("本地完成", reply.text());
            assertTrue(agent.sentBytes() > 0L);
            assertTrue(agent.receivedBytes() > 0L);
            var body = new ObjectMapper().readTree(request.get());
            assertEquals("qwen", body.path("model").asText());
            assertEquals("none", body.path("reasoning_effort").asText());
        }
    }

    @Test
    void listsAllowedLocalFilesThenStartsOneBatchUpload() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        List<String> responses = List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"local-1\",\"type\":\"function\",\"function\":{\"name\":\"list_local_directory\",\"arguments\":\"{\\\"directory\\\":\\\"/allowed/Desktop\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"upload-1\",\"type\":\"function\",\"function\":{\"name\":\"upload\",\"arguments\":\"{\\\"local_sources\\\":[\\\"/allowed/Desktop/a.txt\\\",\\\"/allowed/Desktop/folder\\\"],\\\"remote_directory\\\":\\\"/data\\\",\\\"conflict_policy\\\":\\\"SKIP\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"上传已开始\"}}]}");
        StubGateway gateway = new StubGateway();
        try (ServerFixture server = new ServerFixture("/v1/chat/completions", request, responses)) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            AiAssistantReply reply = new AiAgent(gateway).reply(provider, "qwen", new char[0], List.of(),
                    "上传桌面内容到 /data", Optional.of(new HostId(UUID.randomUUID())));
            assertEquals("上传已开始", reply.text());
            assertEquals(List.of(Path.of("/allowed/Desktop/a.txt"), Path.of("/allowed/Desktop/folder")),
                    gateway.uploaded.get());
            assertEquals("/data", gateway.remoteDirectory.get());
            assertEquals(TransferConflictChoice.SKIP, gateway.backgroundUploadConflict.get());
        }
    }

    @Test
    void permissionAndConflictFailuresNameTheRequiredAction() {
        assertTrue(AiAgent.friendlyToolError(
                "This automation capability is disabled in settings: EDIT_REMOTE")
                .contains("远程文件编辑"));
        assertTrue(AiAgent.friendlyToolError(
                "The user denied the automation operation: UPLOAD").contains("上传"));
        assertTrue(AiAgent.friendlyToolError("Transfer cancelled by conflict decision")
                .contains("远程目标已存在"));
    }

    @Test
    void submitsVisibleOverwriteUploadWithoutAConflictDialogRound() throws Exception {
        HostId selected = HostId.create();
        List<String> responses = List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"upload-visible-1\",\"type\":\"function\",\"function\":{\"name\":\"upload_in_sftp\",\"arguments\":\"{\\\"local_sources\\\":[\\\"/allowed/Desktop/a.txt\\\"],\\\"remote_directory\\\":\\\"/data\\\",\\\"conflict_policy\\\":\\\"OVERWRITE\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"覆盖上传已提交\"}}]}");
        StubGateway gateway = new StubGateway();
        try (ServerFixture server = new ServerFixture("/v1/chat/completions", new AtomicReference<>(), responses)) {
            var provider = new ModelProviderConfig("ollama", "Ollama", ModelProviderType.OLLAMA,
                    server.baseUri(), "qwen", Optional.empty(), false);
            AiAssistantReply reply = new AiAgent(gateway).reply(provider, "qwen", new char[0], List.of(),
                    "覆盖上传 a.txt 到 /data", Optional.of(selected));
            assertEquals("覆盖上传已提交", reply.text());
            assertEquals(selected, gateway.visibleUploadHost.get());
            assertEquals(List.of(Path.of("/allowed/Desktop/a.txt")), gateway.visibleUploaded.get());
            assertEquals("/data", gateway.visibleUploadDirectory.get());
            assertEquals(TransferConflictChoice.OVERWRITE, gateway.visibleUploadConflict.get());
        }
    }

    private static final class ServerFixture implements AutoCloseable {
        private final HttpServer server;
        private ServerFixture(String path, AtomicReference<String> request, String response) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
            server.createContext(path, exchange -> {
                request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start();
        }
        private ServerFixture(String path, AtomicReference<String> request, List<String> responses) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
            java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger();
            server.createContext(path, exchange -> {
                request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String response = responses.get(Math.min(index.getAndIncrement(), responses.size() - 1));
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start();
        }
        private static ServerFixture streaming(
                String path, AtomicReference<String> request, List<String> responses) throws Exception {
            return new ServerFixture(path, request, responses, "text/event-stream");
        }
        private ServerFixture(
                String path, AtomicReference<String> request, List<String> responses, String contentType) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
            java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger();
            server.createContext(path, exchange -> {
                request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String response = responses.get(Math.min(index.getAndIncrement(), responses.size() - 1));
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
        }
        URI baseUri() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"); }
        @Override public void close() { server.stop(0); }
    }

    private static class StubGateway implements AutomationGateway {
        private final AtomicReference<List<Path>> uploaded = new AtomicReference<>();
        private final AtomicReference<String> remoteDirectory = new AtomicReference<>();
        private final AtomicReference<TransferConflictChoice> backgroundUploadConflict = new AtomicReference<>();
        private final AtomicReference<HostId> activated = new AtomicReference<>();
        private final AtomicReference<HostId> commandHost = new AtomicReference<>();
        private final AtomicReference<String> command = new AtomicReference<>();
        private final AtomicReference<HostId> visibleCommandHost = new AtomicReference<>();
        private final AtomicReference<String> visibleCommand = new AtomicReference<>();
        private final AtomicReference<String> localVisibleCommand = new AtomicReference<>();
        private final AtomicReference<HostId> openedSftpHost = new AtomicReference<>();
        private final AtomicReference<HostId> changedSftpRemoteHost = new AtomicReference<>();
        private final AtomicReference<String> changedSftpRemoteDirectory = new AtomicReference<>();
        private final AtomicReference<HostId> changedSftpLocalHost = new AtomicReference<>();
        private final AtomicReference<Path> changedSftpLocalDirectory = new AtomicReference<>();
        private final AtomicReference<HostId> visibleUploadHost = new AtomicReference<>();
        private final AtomicReference<List<Path>> visibleUploaded = new AtomicReference<>();
        private final AtomicReference<String> visibleUploadDirectory = new AtomicReference<>();
        private final AtomicReference<TransferConflictChoice> visibleUploadConflict = new AtomicReference<>();
        private RuntimeException localListingFailure;
        private AutomationOperationStatus operationStatus;
        private List<AutomationOperationStatus> operationStatuses = List.of();
        private final AtomicInteger operationReads = new AtomicInteger();
        private HostId hostId = HostId.create();
        private Optional<String> hostQuery = Optional.empty();
        private Optional<com.ynpe.fastremote.domain.catalog.GroupId> hostGroup = Optional.empty();
        private int hostOffset = -1;
        private int hostLimit = -1;
        private static <T> CompletionStage<T> failed() { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
        @Override public CompletionStage<List<AutomationHost>> listHosts(AutomationSource source) {
            return CompletableFuture.completedFuture(List.of(new AutomationHost(hostId, "server", "root", "host", 22)));
        }
        @Override public CompletionStage<List<AutomationHost>> listHosts(
                AutomationSource source, Optional<String> query, int offset, int limit) {
            hostQuery = query;
            hostOffset = offset;
            hostLimit = limit;
            return listHosts(source);
        }
        @Override public CompletionStage<List<AutomationHost>> listHosts(AutomationSource source, Optional<String> query,
                Optional<com.ynpe.fastremote.domain.catalog.GroupId> groupId, int offset, int limit) {
            hostGroup = groupId;
            return listHosts(source, query, offset, limit).thenApply(values -> groupId.isEmpty() ? values : values.stream()
                    .map(host -> new AutomationHost(host.id(), host.name(), host.username(), host.host(), host.port(), groupId,
                            "vps", "vps", List.of(groupId.orElseThrow()))).toList());
        }
        @Override public CompletionStage<Boolean> activateHost(AutomationSource source, HostId hostId) {
            activated.set(hostId); return CompletableFuture.completedFuture(true);
        }
        @Override public CompletionStage<Boolean> openSftp(AutomationSource source, HostId hostId) {
            openedSftpHost.set(hostId); return CompletableFuture.completedFuture(true);
        }
        @Override public CompletionStage<Boolean> changeSftpRemoteDirectory(
                AutomationSource source, HostId hostId, String directory) {
            changedSftpRemoteHost.set(hostId); changedSftpRemoteDirectory.set(directory);
            return CompletableFuture.completedFuture(true);
        }
        @Override public CompletionStage<Boolean> changeSftpLocalDirectory(
                AutomationSource source, HostId hostId, Path directory) {
            changedSftpLocalHost.set(hostId); changedSftpLocalDirectory.set(directory);
            return CompletableFuture.completedFuture(true);
        }
        @Override public CompletionStage<RemoteCommandResult> runCommand(AutomationSource source, HostId hostId, String command) {
            commandHost.set(hostId); this.command.set(command);
            return CompletableFuture.completedFuture(new RemoteCommandResult("Linux", "", 0, false));
        }
        @Override public CompletionStage<Boolean> runTerminalCommand(
                AutomationSource source, HostId hostId, String command) {
            visibleCommandHost.set(hostId); visibleCommand.set(command);
            return CompletableFuture.completedFuture(true);
        }
        @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> startLocalTerminalCommand(
                AutomationSource source, String command, boolean reuse) {
            return runLocalTerminalCommand(source, command, reuse).thenApply(value -> new com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt(
                    "local-operation", "local-pane", "generation", value, "TERMINAL"));
        }
        @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> startTerminalCommand(
                AutomationSource source, HostId host, String command, boolean reuse) {
            return runTerminalCommand(source, host, command).thenApply(value -> new com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt(
                    "remote-operation", "remote-pane", "generation", value, "TERMINAL"));
        }
        @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> startSftpUpload(
                AutomationSource source, HostId host, List<Path> local, String remote, TransferConflictChoice choice, boolean reuse) {
            return uploadInSftp(source, host, local, remote, choice).thenApply(value -> new com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt(
                    "sftp-operation", "sftp-pane", "generation", value, "SFTP"));
        }
        @Override public CompletionStage<Boolean> runLocalTerminalCommand(
                AutomationSource source, String command) {
            localVisibleCommand.set(command);
            return CompletableFuture.completedFuture(true);
        }
        @Override public CompletionStage<List<RemoteFileEntry>> listRemote(AutomationSource source, HostId hostId, String directory) { return failed(); }
        @Override public CompletionStage<AutomationLocalListing> listLocal(AutomationSource source, Optional<Path> directory) {
            if (localListingFailure != null) return CompletableFuture.failedFuture(localListingFailure);
            return CompletableFuture.completedFuture(new AutomationLocalListing("/allowed/Desktop", List.of(
                    new AutomationLocalEntry("a.txt", "/allowed/Desktop/a.txt", false, 5),
                    new AutomationLocalEntry("folder", "/allowed/Desktop/folder", true, 0)), false));
        }
        @Override public CompletionStage<AutomationOperationId> upload(AutomationSource source, HostId hostId, Path localSource, String remoteTarget) { return failed(); }
        @Override public CompletionStage<AutomationOperationId> uploadIntoDirectory(AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory) {
            uploaded.set(List.copyOf(localSources)); this.remoteDirectory.set(remoteDirectory);
            return CompletableFuture.completedFuture(AutomationOperationId.create());
        }
        @Override public CompletionStage<AutomationOperationId> uploadIntoDirectory(
                AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory,
                TransferConflictChoice conflictChoice) {
            uploaded.set(List.copyOf(localSources));
            this.remoteDirectory.set(remoteDirectory);
            backgroundUploadConflict.set(conflictChoice);
            return CompletableFuture.completedFuture(AutomationOperationId.create());
        }
        @Override public CompletionStage<Boolean> uploadInSftp(
                AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory,
                TransferConflictChoice conflictChoice) {
            visibleUploadHost.set(hostId);
            visibleUploaded.set(List.copyOf(localSources));
            visibleUploadDirectory.set(remoteDirectory);
            visibleUploadConflict.set(conflictChoice);
            return CompletableFuture.completedFuture(true);
        }
        @Override public CompletionStage<AutomationOperationId> download(AutomationSource source, HostId hostId, String remoteSource, Path localTarget) { return failed(); }
        @Override public CompletionStage<Optional<AutomationOperationStatus>> operation(AutomationOperationId id) {
            if (!operationStatuses.isEmpty()) {
                int index = Math.min(operationReads.getAndIncrement(), operationStatuses.size() - 1);
                return CompletableFuture.completedFuture(Optional.of(operationStatuses.get(index)));
            }
            return operationStatus == null ? failed() : CompletableFuture.completedFuture(Optional.of(operationStatus));
        }
        @Override public CompletionStage<Boolean> cancel(AutomationOperationId id) { return failed(); }
        @Override public void close() { }
    }
}
