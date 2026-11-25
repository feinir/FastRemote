// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.ModelProviderType;
import com.ynpe.fastremote.domain.ai.AiInferenceOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded HTTP adapter for OpenAI Responses and OpenAI-compatible Chat Completions. */
final class OpenAiHttpModel {
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final ObjectMapper json;
    private final HttpClient http;
    private final AtomicLong receivedBytes = new AtomicLong();
    private final AtomicLong sentBytes = new AtomicLong();

    OpenAiHttpModel(ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "json");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    ModelResponse complete(
            ModelProviderConfig provider, String model, char[] apiKey, List<ModelMessage> messages,
            List<ToolDefinition> tools, AiInferenceOptions inference) throws IOException, InterruptedException {
        boolean responses = provider.type() == ModelProviderType.OPENAI_RESPONSES;
        ObjectNode body = responses ? responsesBody(model, messages, tools, inference)
                : chatBody(provider.type(), model, messages, tools, inference);
        URI endpoint = append(provider.baseUri(), responses ? "responses" : "chat/completions");
        String requestBody = json.writeValueAsString(body);
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
        if (apiKey.length > 0) request.header("Authorization", "Bearer " + new String(apiKey));
        sentBytes.addAndGet(requestBody.getBytes(StandardCharsets.UTF_8).length);
        HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        receivedBytes.addAndGet(response.body().length);
        if (response.body().length > MAX_RESPONSE_BYTES) throw new IOException("Model response exceeds 2 MiB");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            JsonNode error;
            try { error = json.readTree(response.body()); }
            catch (IOException malformed) { throw new IOException("Model service returned HTTP " + response.statusCode()); }
            String message = error.path("error").path("message").asText("Model service returned HTTP " + response.statusCode());
            throw new IOException(message.length() > 1_000 ? message.substring(0, 1_000) : message);
        }
        JsonNode root = json.readTree(response.body());
        return responses ? decodeResponses(root) : decodeChat(root);
    }

    ModelResponse completeStreaming(
            ModelProviderConfig provider, String model, char[] apiKey, List<ModelMessage> messages,
            List<ToolDefinition> tools, AiInferenceOptions inference, AiStreamListener listener)
            throws IOException, InterruptedException {
        boolean responses = provider.type() == ModelProviderType.OPENAI_RESPONSES;
        ObjectNode body = responses ? responsesBody(model, messages, tools, inference)
                : chatBody(provider.type(), model, messages, tools, inference);
        body.put("stream", true);
        URI endpoint = append(provider.baseUri(), responses ? "responses" : "chat/completions");
        String requestBody = json.writeValueAsString(body);
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
        if (apiKey.length > 0) request.header("Authorization", "Bearer " + new String(apiKey));
        sentBytes.addAndGet(requestBody.getBytes(StandardCharsets.UTF_8).length);
        HttpResponse<InputStream> response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = new BoundedInputStream(
                response.body(), MAX_RESPONSE_BYTES, receivedBytes::addAndGet)) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                byte[] errorBody = input.readAllBytes();
                JsonNode error;
                try { error = json.readTree(errorBody); }
                catch (IOException malformed) {
                    throw new IOException("Model service returned HTTP " + response.statusCode());
                }
                String message = error.path("error").path("message")
                        .asText("Model service returned HTTP " + response.statusCode());
                throw new IOException(message.length() > 1_000 ? message.substring(0, 1_000) : message);
            }
            return responses ? decodeResponsesStream(input, listener) : decodeChatStream(input, listener);
        }
    }

    private ModelResponse decodeResponsesStream(InputStream input, AiStreamListener listener) throws IOException {
        StringBuilder text = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        List<JsonNode> responseItems = new ArrayList<>();
        readSse(input, data -> {
            if ("[DONE]".equals(data)) return;
            JsonNode event = json.readTree(data);
            switch (event.path("type").asText()) {
                case "response.output_text.delta" -> appendDelta(text, event.path("delta").asText(), listener::onTextDelta);
                case "response.output_text.done" -> appendMissing(
                        text, event.path("text").asText(), listener::onTextDelta);
                case "response.reasoning_summary_text.delta", "response.reasoning_text.delta" ->
                        appendDelta(reasoning, event.path("delta").asText(), listener::onReasoningDelta);
                case "response.reasoning_summary_text.done", "response.reasoning_text.done" ->
                        appendMissing(reasoning, event.path("text").asText(), listener::onReasoningDelta);
                case "response.output_item.done" -> {
                    JsonNode item = event.path("item");
                    if (!item.isObject()) return;
                    responseItems.add(item.deepCopy());
                    if ("function_call".equals(item.path("type").asText())) {
                        calls.add(new ToolCall(required(item, "call_id"), required(item, "name"),
                                required(item, "arguments")));
                    }
                }
                case "response.failed", "error" -> throw new IOException(streamError(event));
                default -> { }
            }
        });
        return new ModelResponse(text.toString(), reasoning.toString(), List.copyOf(calls), List.copyOf(responseItems));
    }

    private ModelResponse decodeChatStream(InputStream input, AiStreamListener listener) throws IOException {
        StringBuilder text = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        Map<Integer, ChatToolCallBuilder> calls = new TreeMap<>();
        readSse(input, data -> {
            if ("[DONE]".equals(data)) return;
            JsonNode root = json.readTree(data);
            JsonNode choice = root.path("choices").path(0);
            JsonNode delta = choice.path("delta");
            appendDelta(reasoning, chatReasoning(delta), listener::onReasoningDelta);
            appendDelta(text, delta.path("content").asText(""), listener::onTextDelta);
            for (JsonNode call : delta.path("tool_calls")) {
                int index = call.path("index").asInt(calls.size());
                ChatToolCallBuilder builder = calls.computeIfAbsent(index, ignored -> new ChatToolCallBuilder());
                String id = call.path("id").asText("");
                if (!id.isBlank()) builder.id = id;
                String name = call.path("function").path("name").asText("");
                if (!name.isBlank()) builder.name.append(name);
                builder.arguments.append(call.path("function").path("arguments").asText(""));
            }
            if (!root.path("error").isMissingNode() && !root.path("error").isNull()) {
                throw new IOException(streamError(root));
            }
        });
        List<ToolCall> completed = new ArrayList<>();
        for (ChatToolCallBuilder value : calls.values()) {
            if (value.id.isBlank() || value.name.isEmpty()) throw new IOException("Model tool call is incomplete");
            completed.add(new ToolCall(value.id, value.name.toString(), value.arguments.toString()));
        }
        return new ModelResponse(text.toString(), reasoning.toString(), List.copyOf(completed), List.of());
    }

    private void readSse(InputStream input, SseConsumer consumer) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        consumer.accept(data.toString());
                        data.setLength(0);
                    }
                } else if (line.startsWith("data:")) {
                    if (!data.isEmpty()) data.append('\n');
                    data.append(line.substring(5).stripLeading());
                }
            }
            if (!data.isEmpty()) consumer.accept(data.toString());
        }
    }

    private static void appendDelta(StringBuilder target, String delta, java.util.function.Consumer<String> sink) {
        if (delta == null || delta.isEmpty()) return;
        target.append(delta);
        sink.accept(delta);
    }

    private static void appendMissing(StringBuilder target, String completed, java.util.function.Consumer<String> sink) {
        if (completed == null || completed.isEmpty()) return;
        String existing = target.toString();
        if (completed.equals(existing)) return;
        String missing = completed.startsWith(existing) ? completed.substring(existing.length()) : completed;
        appendDelta(target, missing, sink);
    }

    private static String streamError(JsonNode event) {
        String value = event.path("response").path("error").path("message").asText();
        if (value.isBlank()) value = event.path("error").path("message").asText();
        if (value.isBlank()) value = event.path("message").asText("Model stream failed");
        return value.length() <= 1_000 ? value : value.substring(0, 1_000);
    }

    private ObjectNode chatBody(
            ModelProviderType providerType, String model, List<ModelMessage> messages,
            List<ToolDefinition> tools, AiInferenceOptions inference) {
        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        if (providerType == ModelProviderType.QWEN_DASHSCOPE) {
            // Match the current non-reasoning provider UI, including non-streaming title requests.
            // Qwen hybrid models may default to thinking and reject non-streaming requests otherwise.
            body.put("enable_thinking", false);
        }
        if (providerType == ModelProviderType.OLLAMA) {
            // Ollama's OpenAI-compatible endpoint maps this field onto its native think option.
            String effort = inference.showReasoningSummary()
                    ? inference.reasoningEffort().orElse(com.ynpe.fastremote.domain.ai.AiReasoningEffort.MEDIUM)
                            .name().toLowerCase(java.util.Locale.ROOT)
                    : "none";
            body.put("reasoning_effort", effort);
        }
        ArrayNode encoded = body.putArray("messages");
        for (ModelMessage message : messages) encodeChatMessage(encoded, message);
        if (!tools.isEmpty()) {
            ArrayNode definitions = body.putArray("tools");
            for (ToolDefinition tool : tools) {
                ObjectNode function = definitions.addObject().put("type", "function").putObject("function");
                function.put("name", tool.name()).put("description", tool.description());
                function.set("parameters", tool.parameters());
                function.put("strict", true);
            }
            body.put("tool_choice", "auto");
            body.put("parallel_tool_calls", false);
        }
        return body;
    }

    private void encodeChatMessage(ArrayNode target, ModelMessage message) {
        switch (message) {
            case TextMessage text -> target.addObject().put("role", text.role()).put("content", text.text());
            case UserMessage user -> {
                ObjectNode encoded = target.addObject().put("role", "user");
                ArrayNode content = encoded.putArray("content");
                if (!user.text().isBlank()) content.addObject().put("type", "text").put("text", user.text());
                for (AiImageInput image : user.images()) {
                    content.addObject().put("type", "image_url").putObject("image_url")
                            .put("url", dataUrl(image));
                }
            }
            case AssistantToolMessage assistant -> {
                ObjectNode encoded = target.addObject().put("role", "assistant");
                if (!assistant.text().isBlank()) encoded.put("content", assistant.text());
                if (!assistant.reasoningContent().isEmpty()) {
                    encoded.put("reasoning_content", assistant.reasoningContent());
                }
                ArrayNode calls = encoded.putArray("tool_calls");
                for (ToolCall call : assistant.calls()) {
                    ObjectNode item = calls.addObject().put("id", call.id()).put("type", "function");
                    item.putObject("function").put("name", call.name()).put("arguments", call.arguments());
                }
            }
            case ToolResultMessage result -> target.addObject().put("role", "tool")
                    .put("tool_call_id", result.callId()).put("content", result.output());
            case ResponseItemsMessage ignored -> throw new IllegalArgumentException(
                    "Responses output items cannot be encoded for Chat Completions");
        }
    }

    private ObjectNode responsesBody(
            String model, List<ModelMessage> messages, List<ToolDefinition> tools, AiInferenceOptions inference) {
        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        body.put("store", false);
        inference.reasoningEffort().ifPresent(effort -> {
            ObjectNode reasoning = body.putObject("reasoning");
            reasoning.put("effort", effort.name().toLowerCase(java.util.Locale.ROOT));
            if (inference.showReasoningSummary()) reasoning.put("summary", "auto");
        });
        ArrayNode input = body.putArray("input");
        for (ModelMessage message : messages) encodeResponseInput(input, message);
        if (!tools.isEmpty()) {
            ArrayNode definitions = body.putArray("tools");
            for (ToolDefinition tool : tools) {
                ObjectNode function = definitions.addObject().put("type", "function")
                        .put("name", tool.name()).put("description", tool.description());
                function.set("parameters", tool.parameters());
                function.put("strict", true);
            }
            body.put("tool_choice", "auto");
            body.put("parallel_tool_calls", false);
        }
        return body;
    }

    private void encodeResponseInput(ArrayNode target, ModelMessage message) {
        switch (message) {
            case TextMessage text -> target.addObject().put("role", text.role()).put("content", text.text());
            case UserMessage user -> {
                ObjectNode encoded = target.addObject().put("role", "user");
                ArrayNode content = encoded.putArray("content");
                if (!user.text().isBlank()) {
                    content.addObject().put("type", "input_text").put("text", user.text());
                }
                for (AiImageInput image : user.images()) {
                    content.addObject().put("type", "input_image")
                            .put("image_url", dataUrl(image)).put("detail", "auto");
                }
            }
            case AssistantToolMessage assistant -> {
                if (!assistant.text().isBlank()) target.addObject().put("role", "assistant").put("content", assistant.text());
                for (ToolCall call : assistant.calls()) target.addObject().put("type", "function_call")
                        .put("call_id", call.id()).put("name", call.name()).put("arguments", call.arguments());
            }
            case ToolResultMessage result -> target.addObject().put("type", "function_call_output")
                    .put("call_id", result.callId()).put("output", result.output());
            case ResponseItemsMessage response -> response.items().forEach(item -> target.add(item.deepCopy()));
        }
    }

    private ModelResponse decodeChat(JsonNode root) throws IOException {
        JsonNode message = root.path("choices").path(0).path("message");
        if (message.isMissingNode()) throw new IOException("Model response does not contain a message");
        String text = message.path("content").isNull() ? "" : message.path("content").asText("");
        String reasoning = chatReasoning(message);
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            calls.add(new ToolCall(required(call, "id"), required(call.path("function"), "name"),
                    required(call.path("function"), "arguments")));
        }
        return new ModelResponse(text, reasoning, calls, List.of());
    }

    private static String chatReasoning(JsonNode message) {
        for (String field : List.of("reasoning", "reasoning_content", "thinking")) {
            JsonNode value = message.path(field);
            if (value.isTextual() && !value.asText().isEmpty()) return value.asText();
        }
        return "";
    }

    private ModelResponse decodeResponses(JsonNode root) throws IOException {
        StringBuilder text = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        List<JsonNode> responseItems = new ArrayList<>();
        for (JsonNode item : root.path("output")) {
            responseItems.add(item.deepCopy());
            if (item.path("type").asText().equals("function_call")) {
                calls.add(new ToolCall(required(item, "call_id"), required(item, "name"), required(item, "arguments")));
            } else if (item.path("type").asText().equals("message")) {
                for (JsonNode content : item.path("content")) {
                    if (content.path("type").asText().equals("output_text")) text.append(content.path("text").asText());
                }
            } else if (item.path("type").asText().equals("reasoning")) {
                for (JsonNode summary : item.path("summary")) {
                    String value = summary.path("text").asText();
                    if (!value.isBlank()) {
                        if (!reasoning.isEmpty()) reasoning.append('\n');
                        reasoning.append(value);
                    }
                }
            }
        }
        return new ModelResponse(text.toString(), reasoning.toString(), calls, List.copyOf(responseItems));
    }

    private static String required(JsonNode node, String field) throws IOException {
        String value = node.path(field).asText();
        if (value.isBlank()) throw new IOException("Model tool call is missing " + field);
        return value;
    }

    private static URI append(URI base, String suffix) {
        String value = base.toString();
        if (!value.endsWith("/")) value += "/";
        return URI.create(value + suffix);
    }

    long receivedBytes() { return receivedBytes.get(); }

    long sentBytes() { return sentBytes.get(); }

    private static String dataUrl(AiImageInput image) {
        return "data:" + image.mediaType() + ";base64," + Base64.getEncoder().encodeToString(image.data());
    }

    sealed interface ModelMessage permits TextMessage, UserMessage, AssistantToolMessage, ToolResultMessage,
            ResponseItemsMessage { }
    record TextMessage(String role, String text) implements ModelMessage { }
    record UserMessage(String text, List<AiImageInput> images) implements ModelMessage {
        UserMessage { images = List.copyOf(images); }
    }
    record AssistantToolMessage(String text, List<ToolCall> calls, String reasoningContent) implements ModelMessage {
        AssistantToolMessage(String text, List<ToolCall> calls) { this(text, calls, ""); }
    }
    record ToolResultMessage(String callId, String output) implements ModelMessage { }
    record ResponseItemsMessage(List<JsonNode> items) implements ModelMessage {
        ResponseItemsMessage { items = List.copyOf(items); }
    }
    record ToolCall(String id, String name, String arguments) {
        @Override public String toString() { return "ToolCall[id=" + id + ", name=" + name + "]"; }
    }
    record ToolDefinition(String name, String description, ObjectNode parameters) { }
    record ModelResponse(String text, String reasoningSummary, List<ToolCall> calls, List<JsonNode> responseItems) { }

    @FunctionalInterface
    private interface SseConsumer { void accept(String data) throws IOException; }

    private static final class ChatToolCallBuilder {
        private String id = "";
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private final long limit;
        private final java.util.function.LongConsumer byteObserver;
        private long count;

        private BoundedInputStream(
                InputStream input, long limit, java.util.function.LongConsumer byteObserver) {
            super(input);
            this.limit = limit;
            this.byteObserver = Objects.requireNonNull(byteObserver, "byteObserver");
        }

        @Override public int read() throws IOException {
            int value = super.read();
            if (value >= 0) increment(1);
            return value;
        }

        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) increment(read);
            return read;
        }

        private void increment(int amount) throws IOException {
            count += amount;
            byteObserver.accept(amount);
            if (count > limit) throw new IOException("Model response exceeds 2 MiB");
        }
    }
}
