// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.ModelProviderType;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModelCatalogClientTest {
    @Test
    void discoversOpenAiCompatibleModels() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.json("/v1/models", 200, "{\"data\":[{\"id\":\"model-a\"},{\"id\":\"model-b\"}]}");
            ModelProviderConfig provider = provider(fixture, ModelProviderType.OPENAI_COMPATIBLE);
            ModelCatalogClient client = new ModelCatalogClient();
            assertEquals(List.of("model-a", "model-b"), client.discover(provider, new char[0]));
            org.junit.jupiter.api.Assertions.assertTrue(client.receivedBytes() > 0L);
            assertEquals(0L, client.sentBytes());
        }
    }

    @Test
    void fallsBackToNativeOllamaTags() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.json("/v1/models", 404, "{}");
            fixture.json("/api/tags", 200, "{\"models\":[{\"name\":\"qwen3:8b\"},{\"name\":\"gemma3:4b\"}]}");
            ModelProviderConfig provider = provider(fixture, ModelProviderType.OLLAMA);
            assertEquals(List.of("qwen3:8b", "gemma3:4b"), new ModelCatalogClient().discover(provider, new char[0]));
        }
    }

    private static ModelProviderConfig provider(Fixture fixture, ModelProviderType type) {
        return new ModelProviderConfig("test", "Test", type, fixture.baseUri(), "", Optional.empty(), false);
    }

    private static final class Fixture implements AutoCloseable {
        private final HttpServer server;
        private Fixture() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 2); server.start();
        }
        private void json(String path, int status, String body) {
            server.createContext(path, exchange -> {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            });
        }
        private URI baseUri() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"); }
        @Override public void close() { server.stop(0); }
    }
}
