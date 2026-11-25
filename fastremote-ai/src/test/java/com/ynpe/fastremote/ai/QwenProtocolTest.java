// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ynpe.fastremote.domain.ai.AiInferenceOptions;
import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.ModelProviderType;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class QwenProtocolTest {
    @Test
    void discoversModelsAndUsesBearerAuthForNonStreamingAndStreamingToolRoundTrip() throws Exception {
        var json = new ObjectMapper();
        List<JsonNode> requests = new CopyOnWriteArrayList<>();
        List<String> auth = new CopyOnWriteArrayList<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/compatible-mode/v1/", exchange -> {
            auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            String response;
            String contentType = "application/json";
            if (exchange.getRequestURI().getPath().endsWith("/models")) {
                response = "{\"data\":[{\"id\":\"qwen-plus\"}]}";
            } else {
                var request = json.readTree(exchange.getRequestBody());
                requests.add(request);
                if (!request.path("stream").asBoolean()) {
                    response = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Title\"}}]}";
                } else {
                    contentType = "text/event-stream";
                    response = requests.size() == 2
                            ? "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"lookup\",\"arguments\":\"{}\"}}]}}]}\n\ndata: [DONE]\n\n"
                            : "data: {\"choices\":[{\"delta\":{\"content\":\"Done\"}}]}\n\ndata: [DONE]\n\n";
                }
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        char[] key = "fixture-token".toCharArray();
        try {
            var provider = new ModelProviderConfig("qwen", "Qwen", ModelProviderType.QWEN_DASHSCOPE,
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/compatible-mode/v1"),
                    "qwen-plus", Optional.empty(), false);
            assertEquals(List.of("qwen-plus"), new ModelCatalogClient().discover(provider, key));
            var model = new OpenAiHttpModel(json);
            List<OpenAiHttpModel.ModelMessage> messages = new ArrayList<>();
            messages.add(new OpenAiHttpModel.TextMessage("user", "lookup"));
            assertEquals("Title", model.complete(provider, "qwen-plus", key, messages, List.of(), AiInferenceOptions.DEFAULT).text());
            var schema = json.createObjectNode().put("type", "object").put("additionalProperties", false);
            schema.putObject("properties");
            schema.putArray("required");
            var tools = List.of(new OpenAiHttpModel.ToolDefinition("lookup", "Look up a value", schema));
            var first = model.completeStreaming(provider, "qwen-plus", key, messages, tools, AiInferenceOptions.DEFAULT, new AiStreamListener() { });
            assertEquals("lookup", first.calls().getFirst().name());
            messages.add(new OpenAiHttpModel.AssistantToolMessage(first.text(), first.calls()));
            messages.add(new OpenAiHttpModel.ToolResultMessage(first.calls().getFirst().id(), "fixture-result"));
            var deltas = new StringBuilder();
            var last = model.completeStreaming(provider, "qwen-plus", key, messages, tools, AiInferenceOptions.DEFAULT, new AiStreamListener() { @Override public void onTextDelta(String delta) { deltas.append(delta); } });
            assertEquals("Done", last.text());
            assertEquals(last.text(), deltas.toString());
            assertTrue(auth.stream().allMatch("Bearer fixture-token"::equals));
            assertEquals(4, auth.size());
            for (var request : requests) {
                assertTrue(request.has("enable_thinking"));
                assertFalse(request.path("enable_thinking").asBoolean());
                assertFalse(request.has("reasoning_effort"));
            }
            assertEquals("call-1", requests.getLast().path("messages").get(2).path("tool_call_id").asText());
        } finally {
            java.util.Arrays.fill(key, '\0');
            server.stop(0);
        }
    }
}
