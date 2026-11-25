// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded bodies and a deadline that also covers stalled SSE reads. */
final class GeminiHttpTransport implements AutoCloseable {
    // Bound admission as well as the deadline scheduler queue across all client instances.
    private static final java.util.concurrent.Semaphore ACTIVE = new java.util.concurrent.Semaphore(32);
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final ScheduledThreadPoolExecutor DEADLINES = new ScheduledThreadPoolExecutor(1,
            Thread.ofPlatform().daemon(true).name("gemini-http-deadline").factory());
    static { DEADLINES.setRemoveOnCancelPolicy(true); }
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong sent = new AtomicLong();
    private final ObjectMapper json;

    GeminiHttpTransport(ObjectMapper json) { this.json = json; }

    <T> T send(HttpRequest request, int requestBytes, Reader<T> reader) throws IOException, InterruptedException {
        if (!ACTIVE.tryAcquire()) throw new IOException("Too many active Google requests");
        try {
            sent.addAndGet(requestBytes);
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                var deadline = DEADLINES.schedule(() -> {
                    try { body.close(); } catch (IOException ignored) { }
                }, request.timeout().orElse(Duration.ofSeconds(120)).toMillis(), TimeUnit.MILLISECONDS);
                try (var bounded = new FilterInputStream(body) {
                    private int count;
                    private void account(int amount) throws IOException {
                        if (amount <= 0) return;
                        received.addAndGet(amount);
                        count += amount;
                        if (count > MAX_BYTES) throw new IOException("Gemini response exceeds 2 MiB");
                        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
                    }
                    @Override public int read() throws IOException {
                        int value = in.read(); if (value >= 0) account(1); return value;
                    }
                    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                        int count = in.read(bytes, offset, Math.min(length, MAX_BYTES + 1)); account(count); return count;
                    }
                }) {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        // Do not display upstream bodies: authentication services may echo credentials.
                        throw new IOException("Google service returned HTTP " + response.statusCode());
                    }
                    T result = reader.read(bounded);
                    if (deadline.isDone()) throw new IOException("Google response timed out");
                    return result;
                } finally { deadline.cancel(false); }
            }
        } finally { ACTIVE.release(); }
    }

    JsonNode json(HttpRequest request, int requestBytes) throws IOException, InterruptedException {
        return send(request, requestBytes, input -> {
            try {
                JsonNode result = json.readTree(input.readAllBytes());
                if (result == null || !result.isObject()) throw new IOException("Google returned no JSON object");
                return result;
            }
            catch (com.fasterxml.jackson.core.JacksonException invalid) {
                throw new IOException("Google returned invalid JSON");
            }
        });
    }

    @Override public void close() { http.shutdownNow(); }
    long receivedBytes() { return received.get(); }
    long sentBytes() { return sent.get(); }
    @FunctionalInterface interface Reader<T> { T read(InputStream input) throws IOException; }
}
