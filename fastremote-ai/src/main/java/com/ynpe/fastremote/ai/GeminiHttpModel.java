// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ynpe.fastremote.ai.OpenAiHttpModel.*;
import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.ModelProviderType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Native GenerateContent protocol. Returned model parts retain opaque thought signatures. */
final class GeminiHttpModel implements AutoCloseable {
    private final ObjectMapper json;
    private final GeminiHttpTransport transport;
    GeminiHttpModel(ObjectMapper json) { this.json = json; transport = new GeminiHttpTransport(json); }
    @Override public void close() { transport.close(); }
    long receivedBytes() { return transport.receivedBytes(); }
    long sentBytes() { return transport.sentBytes(); }

    List<String> discover(ModelProviderConfig provider, char[] credential) throws IOException, InterruptedException {
        var models = new LinkedHashSet<String>();
        var tokens = new HashSet<String>();
        String page = "";
        for (int n = 0; n < 16; n++) {
            String suffix = "models?pageSize=100" + (page.isEmpty() ? "" : "&pageToken=" +
                    URLEncoder.encode(page, StandardCharsets.UTF_8));
            var request = request(provider, suffix, credential).GET().build();
            JsonNode root = transport.json(request, 0);
            for (JsonNode model : root.path("models")) {
                boolean generates = false;
                for (JsonNode method : model.path("supportedGenerationMethods")) {
                    if (method.asText().equals("generateContent")) generates = true;
                }
                String name = model.path("name").asText("");
                if (generates && name.startsWith("models/") && validModel(name.substring(7))) {
                    models.add(name.substring(7));
                    if (models.size() >= 512) return List.copyOf(models);
                }
            }
            page = root.path("nextPageToken").asText("");
            if (page.isEmpty()) {
                if (models.isEmpty()) throw new IOException("Gemini returned no content generation models");
                return List.copyOf(models);
            }
            if (page.length() > 4096 || !tokens.add(page)) throw new IOException("Invalid Gemini catalog pagination");
        }
        throw new IOException("Gemini catalog exceeds 16 pages");
    }

    ModelResponse complete(ModelProviderConfig provider, String model, char[] credential,
            List<ModelMessage> messages, List<ToolDefinition> tools, boolean stream, AiStreamListener listener)
            throws IOException, InterruptedException {
        String id = model.startsWith("models/") ? model.substring(7) : model;
        if (!validModel(id)) throw new IllegalArgumentException("Invalid Gemini model identifier");
        byte[] body = json.writeValueAsBytes(body(messages, tools));
        var request = request(provider, "models/" + id + (stream ? ":streamGenerateContent?alt=sse" : ":generateContent"), credential)
                .header("Content-Type", "application/json").header("Accept", stream ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var decoded = new Decoder(listener);
        if (!stream) decoded.accept(transport.json(request, body.length));
        else transport.send(request, body.length, input -> {
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder data = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (!data.isEmpty()) { decoded.event(data.toString()); data.setLength(0); }
                    } else if (line.startsWith("data:")) {
                        if (!data.isEmpty()) data.append('\n');
                        data.append(line.substring(5).stripLeading());
                    }
                }
                if (!data.isEmpty()) decoded.event(data.toString());
            }
            return null;
        });
        return decoded.finish();
    }

    private HttpRequest.Builder request(ModelProviderConfig provider, String suffix, char[] credential) throws IOException {
        URI base = provider.baseUri();
        if (base.getRawQuery() != null) throw new IllegalArgumentException("Gemini Base URL must not contain a query");
        String url = base.toString();
        var request = HttpRequest.newBuilder(URI.create(url + (url.endsWith("/") ? "" : "/") + suffix))
                .timeout(Duration.ofSeconds(120));
        if (credential.length == 0) throw new IOException("Gemini API Key is required");
        request.header("x-goog-api-key", new String(credential));
        return request;
    }

    private static boolean validModel(String value) { return value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}"); }

    private ObjectNode body(List<ModelMessage> messages, List<ToolDefinition> tools) throws IOException {
        var body = json.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        Map<String, JsonNode> calls = new HashMap<>();
        for (var message : messages) {
            switch (message) {
                case TextMessage text -> {
                    if (text.role().equals("system")) {
                        body.withObject("systemInstruction").withArray("parts").addObject().put("text", text.text());
                    } else {
                        parts(contents, text.role().equals("assistant") ? "model" : "user").addObject().put("text", text.text());
                    }
                }
                case UserMessage user -> {
                    var parts = parts(contents, "user");
                    if (!user.text().isBlank()) parts.addObject().put("text", user.text());
                    for (var image : user.images()) parts.addObject().putObject("inlineData")
                            .put("mimeType", image.mediaType()).put("data", Base64.getEncoder().encodeToString(image.data()));
                }
                case ResponseItemsMessage nativeContent -> {
                    for (JsonNode content : nativeContent.items()) {
                        contents.add(content.deepCopy());
                        int callIndex = 0;
                        for (JsonNode part : content.path("parts")) {
                            if (part.has("functionCall")) {
                                var call = part.path("functionCall");
                                calls.put(callId(call, callIndex++), call);
                            }
                        }
                    }
                }
                case ToolResultMessage result -> {
                    JsonNode call = calls.get(result.callId());
                    if (call == null) throw new IOException("Gemini tool response has no matching call");
                    var response = parts(contents, "user").addObject().putObject("functionResponse");
                    response.put("name", call.path("name").asText());
                    if (call.hasNonNull("id")) response.set("id", call.get("id"));
                    response.putObject("response").put("output", result.output());
                }
                case AssistantToolMessage ignored -> throw new IOException("Gemini requires original model parts for tool continuation");
            }
        }
        if (!tools.isEmpty()) {
            var declarations = body.putArray("tools").addObject().putArray("functionDeclarations");
            for (var tool : tools) declarations.addObject().put("name", tool.name()).put("description", tool.description())
                    .set("parametersJsonSchema", tool.parameters());
        }
        return body;
    }

    private ArrayNode parts(ArrayNode contents, String role) {
        if (!contents.isEmpty() && contents.get(contents.size() - 1).path("role").asText().equals(role)) {
            return (ArrayNode) contents.get(contents.size() - 1).get("parts");
        }
        return contents.addObject().put("role", role).putArray("parts");
    }

    private static String callId(JsonNode call, int index) {
        return call.path("id").asText("").isEmpty() ? "gemini-call-" + index : call.path("id").asText();
    }

    private final class Decoder {
        private final AiStreamListener listener;
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final ObjectNode content = json.createObjectNode().put("role", "model");
        private final ArrayNode parts = content.putArray("parts");
        private final List<ToolCall> calls = new ArrayList<>();
        private final java.util.Set<String> ids = new HashSet<>();
        Decoder(AiStreamListener listener) { this.listener = listener; }
        void event(String event) throws IOException {
            if (event.equals("[DONE]")) return;
            try { accept(json.readTree(event)); }
            catch (com.fasterxml.jackson.core.JacksonException invalid) { throw new IOException("Invalid Gemini stream event"); }
        }
        void accept(JsonNode root) throws IOException {
            if (root.hasNonNull("error")) throw new IOException("Gemini stream returned an error");
            if (root.path("promptFeedback").hasNonNull("blockReason")) throw new IOException("Gemini blocked the prompt");
            var candidate = root.path("candidates").path(0);
            String finish = candidate.path("finishReason").asText("");
            if (!finish.isEmpty() && !finish.equals("STOP") && !finish.equals("MAX_TOKENS")) {
                throw new IOException("Gemini could not complete the response");
            }
            for (JsonNode part : candidate.path("content").path("parts")) {
                parts.add(part.deepCopy());
                String delta = part.path("text").asText("");
                if (!delta.isEmpty()) {
                    if (part.path("thought").asBoolean(false)) { reasoning.append(delta); listener.onReasoningDelta(delta); }
                    else { text.append(delta); listener.onTextDelta(delta); }
                }
                if (part.has("functionCall")) {
                    JsonNode call = part.path("functionCall");
                    String name = call.path("name").asText("");
                    JsonNode args = call.path("args");
                    if (args.isMissingNode() || args.isNull()) args = json.createObjectNode();
                    if (name.isBlank() || !args.isObject()) throw new IOException("Invalid Gemini function call");
                    String id = callId(call, calls.size());
                    if (!ids.add(id)) throw new IOException("Duplicate Gemini function call");
                    calls.add(new ToolCall(id, name, args.toString()));
                }
            }
        }
        ModelResponse finish() throws IOException {
            if (text.isEmpty() && calls.isEmpty()) throw new IOException("Gemini returned no text or tool calls");
            return new ModelResponse(text.toString(), reasoning.toString(), List.copyOf(calls), List.of(content));
        }
    }
}
