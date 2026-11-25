// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.ModelProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Discovers bounded model identifiers from OpenAI-compatible and Ollama endpoints. */
public final class ModelCatalogClient implements AutoCloseable {
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_MODELS = 512;
    private final ObjectMapper json = new ObjectMapper();
    private final GeminiHttpModel gemini = new GeminiHttpModel(json);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final AtomicLong receivedBytes = new AtomicLong();

    public List<String> discover(ModelProviderConfig provider, char[] apiKey)
            throws IOException, InterruptedException {
        Objects.requireNonNull(provider, "provider");
        char[] key = Objects.requireNonNull(apiKey, "apiKey").clone();
        try {
            if (provider.type() == ModelProviderType.GEMINI_API) {
                return gemini.discover(provider, key);
            }
            HttpResult result = get(append(provider.baseUri(), "models"), key);
            if (result.status() >= 200 && result.status() < 300) {
                return decodeOpenAi(result.body());
            }
            if (provider.type() == ModelProviderType.OLLAMA) {
                URI tags = append(withoutVersionSuffix(provider.baseUri()), "api/tags");
                HttpResult fallback = get(tags, key);
                if (fallback.status() >= 200 && fallback.status() < 300) return decodeOllama(fallback.body());
                throw serviceError(fallback);
            }
            throw serviceError(result);
        } finally {
            Arrays.fill(key, '\0');
        }
    }

    private HttpResult get(URI endpoint, char[] apiKey) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json").GET();
        if (apiKey.length > 0) request.header("Authorization", "Bearer " + new String(apiKey));
        HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        receivedBytes.addAndGet(response.body().length);
        if (response.body().length > MAX_RESPONSE_BYTES) throw new IOException("Model catalog exceeds 2 MiB");
        return new HttpResult(response.statusCode(), response.body());
    }

    private List<String> decodeOpenAi(byte[] body) throws IOException {
        JsonNode root = json.readTree(body);
        List<String> values = new ArrayList<>();
        for (JsonNode model : root.path("data")) add(values, model.path("id").asText());
        if (values.isEmpty()) throw new IOException("Model service returned no model identifiers");
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private List<String> decodeOllama(byte[] body) throws IOException {
        JsonNode root = json.readTree(body);
        List<String> values = new ArrayList<>();
        for (JsonNode model : root.path("models")) add(values, model.path("name").asText());
        if (values.isEmpty()) throw new IOException("Ollama returned no model identifiers");
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private static void add(List<String> values, String value) {
        String safe = Objects.requireNonNullElse(value, "").strip();
        if (!safe.isEmpty() && safe.length() <= 200 && values.size() < MAX_MODELS) values.add(safe);
    }

    private IOException serviceError(HttpResult result) {
        try {
            String message = json.readTree(result.body()).path("error").path("message").asText();
            if (!message.isBlank()) return new IOException(limit(message));
        } catch (IOException ignored) { }
        return new IOException("Model service returned HTTP " + result.status());
    }

    private static String limit(String value) {
        return value.length() <= 1_000 ? value : value.substring(0, 1_000);
    }

    private static URI withoutVersionSuffix(URI value) {
        String raw = value.toString();
        if (raw.endsWith("/v1/")) raw = raw.substring(0, raw.length() - 3);
        else if (raw.endsWith("/v1")) raw = raw.substring(0, raw.length() - 2);
        return URI.create(raw);
    }

    private static URI append(URI base, String suffix) {
        String value = base.toString();
        if (!value.endsWith("/")) value += "/";
        return URI.create(value + suffix);
    }

    @Override public void close() { gemini.close(); http.shutdownNow(); }

    public long receivedBytes() { return receivedBytes.get() + gemini.receivedBytes(); }

    public long sentBytes() { return gemini.sentBytes(); }

    private record HttpResult(int status, byte[] body) {
        private HttpResult { body = body.clone(); }
    }
}
