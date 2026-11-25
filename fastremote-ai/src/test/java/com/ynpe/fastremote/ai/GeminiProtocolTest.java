// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.ynpe.fastremote.domain.ai.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GeminiProtocolTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void discoversOnlyGenerationModelsAcrossPagesWithoutPuttingTheKeyInTheUrl() throws Exception {
        var requests = new ArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/v1beta/models", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            assertEquals("fixture-google-key", exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            String body = requests.size() == 1
                    ? "{\"models\":[{\"name\":\"models/embed\",\"supportedGenerationMethods\":[\"embedContent\"]}],\"nextPageToken\":\"a+b/=&\"}"
                    : "{\"models\":[{\"name\":\"models/gemini-test\",\"supportedGenerationMethods\":[\"generateContent\"]}]}";
            respond(exchange, body, "application/json");
        });
        server.start();
        try {
            assertEquals(List.of("gemini-test"), new ModelCatalogClient().discover(provider(server), "fixture-google-key".toCharArray()));
            assertEquals(2, requests.size());
            assertTrue(requests.get(1).contains("pageToken=a%2Bb%2F%3D%26"));
            assertTrue(requests.stream().noneMatch(value -> value.contains("fixture-google-key")));
        } finally { server.stop(0); }
    }

    @Test
    void rejectsRepeatedCatalogPageTokens() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        var count = new AtomicInteger();
        server.createContext("/v1beta/models", exchange -> {
            count.incrementAndGet(); respond(exchange, "{\"nextPageToken\":\"repeated\"}", "application/json");
        }); server.start();
        try {
            assertThrows(IOException.class, () -> new ModelCatalogClient().discover(provider(server), "fixture".toCharArray()));
            assertEquals(2, count.get());
        } finally { server.stop(0); }
    }

    @Test
    void retainsNativePartsSignaturesAndFunctionIdsAcrossMultipleToolSteps() throws Exception {
        var requests = new ArrayList<JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/v1beta/models/gemini-test:streamGenerateContent", exchange -> {
            requests.add(json.readTree(exchange.getRequestBody()));
            int step = requests.size();
            String part = step < 3
                    ? "{\"thoughtSignature\":\"signature-" + step + "\",\"functionCall\":{\"id\":\"call-" + step + "\",\"name\":\"lookup\",\"args\":{}}}"
                    : "{\"text\":\"Done\",\"thoughtSignature\":\"final-signature\"}";
            respond(exchange, "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[" + part + "]},\"finishReason\":\"STOP\"}]}\n\n", "text/event-stream");
        }); server.start();
        try {
            var model = new GeminiHttpModel(json);
            var schema = json.createObjectNode().put("type", "object"); schema.putObject("properties");
            var tools = List.of(new OpenAiHttpModel.ToolDefinition("lookup", "Lookup", schema));
            List<OpenAiHttpModel.ModelMessage> messages = new ArrayList<>();
            messages.add(new OpenAiHttpModel.TextMessage("system", "System instruction"));
            messages.add(new OpenAiHttpModel.TextMessage("user", "Lookup twice"));
            for (int step = 1; step <= 2; step++) {
                var response = model.complete(provider(server), "models/gemini-test", "fixture".toCharArray(), messages, tools, true, AiStreamListener.NONE);
                assertEquals("call-" + step, response.calls().getFirst().id());
                messages.add(new OpenAiHttpModel.ResponseItemsMessage(response.responseItems()));
                messages.add(new OpenAiHttpModel.ToolResultMessage(response.calls().getFirst().id(), "result"));
            }
            var deltas = new StringBuilder();
            var result = model.complete(provider(server), "gemini-test", "fixture".toCharArray(), messages, tools, true,
                    new AiStreamListener() { @Override public void onTextDelta(String delta) { deltas.append(delta); } });
            assertEquals("Done", result.text()); assertEquals("Done", deltas.toString());
            var last = requests.getLast();
            assertEquals("System instruction", last.path("systemInstruction").path("parts").get(0).path("text").asText());
            assertEquals("signature-1", last.path("contents").get(1).path("parts").get(0).path("thoughtSignature").asText());
            assertEquals("signature-2", last.path("contents").get(3).path("parts").get(0).path("thoughtSignature").asText());
            assertEquals("call-2", last.path("contents").get(4).path("parts").get(0).path("functionResponse").path("id").asText());
            assertTrue(last.path("tools").get(0).path("functionDeclarations").get(0).has("parametersJsonSchema"));
        } finally { server.stop(0); }
    }

    @Test
    void decodesNonStreamingRepliesAndRejectsPromptBlocksOrEmptyOutput() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        var count = new AtomicInteger();
        server.createContext("/v1beta/models/gemini-test:generateContent", exchange -> {
            String body = switch (count.getAndIncrement()) {
                case 0 -> "{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"Reason\"},{\"text\":\"Title\"}]}}]}";
                case 1 -> "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";
                default -> "{}";
            };
            respond(exchange, body, "application/json");
        }); server.start();
        try {
            var model = new GeminiHttpModel(json);
            var messages = List.<OpenAiHttpModel.ModelMessage>of(new OpenAiHttpModel.TextMessage("user", "title"));
            var response = model.complete(provider(server), "gemini-test", "fixture".toCharArray(), messages, List.of(), false, AiStreamListener.NONE);
            assertEquals("Title", response.text()); assertEquals("Reason", response.reasoningSummary());
            for (int i = 0; i < 2; i++) assertThrows(IOException.class, () -> model.complete(provider(server), "gemini-test", "fixture".toCharArray(), messages, List.of(), false, AiStreamListener.NONE));
            assertThrows(IllegalArgumentException.class, () -> model.complete(provider(server), "../models?key=x", "fixture".toCharArray(), messages, List.of(), false, AiStreamListener.NONE));
        } finally { server.stop(0); }
    }

    private static ModelProviderConfig provider(HttpServer server) {
        return new ModelProviderConfig("gemini", "Gemini", ModelProviderType.GEMINI_API,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta"), "gemini-test", Optional.empty(), false);
    }
    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body, String type) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type); exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes); exchange.close();
    }
}
