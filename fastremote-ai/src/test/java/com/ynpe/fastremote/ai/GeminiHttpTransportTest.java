// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class GeminiHttpTransportTest {
    @Test
    void closeCancelsAnActiveBodyReadAndIsIdempotent() throws Exception {
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 0); exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        }); server.start();
        try (var transport = new GeminiHttpTransport(new ObjectMapper())) {
            var result = java.util.concurrent.CompletableFuture.runAsync(() -> assertThrows(IOException.class,
                    () -> transport.send(request(server, Duration.ofSeconds(10)), 0, input -> {
                        input.read(); entered.countDown(); return input.readAllBytes();
                    })));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            transport.close(); transport.close();
            result.get(2, TimeUnit.SECONDS);
        } finally { release.countDown(); server.stop(0); }
    }

    @Test
    void enforcesSizeBeforeBufferingAnUnlimitedBody() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/", exchange -> {
            byte[] body = new byte[2 * 1024 * 1024 + 1];
            exchange.sendResponseHeaders(200, body.length);
            try { exchange.getResponseBody().write(body); } finally { exchange.close(); }
        }); server.start();
        try {
            var transport = new GeminiHttpTransport(new ObjectMapper());
            var failure = assertThrows(IOException.class, () -> transport.send(request(server, Duration.ofSeconds(5)), 0, input -> input.readAllBytes()));
            assertTrue(failure.getMessage().contains("2 MiB"));
        } finally { server.stop(0); }
    }

    @Test
    void deadlineAlsoClosesABodyThatStallsAfterHeaders() throws Exception {
        var release = new CountDownLatch(1);
        var enteredBody = new AtomicBoolean();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 0); exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        }); server.start();
        try {
            var transport = new GeminiHttpTransport(new ObjectMapper());
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertThrows(IOException.class,
                    () -> transport.send(request(server, Duration.ofMillis(500)), 0, input -> {
                        assertEquals('{', input.read()); enteredBody.set(true); return input.readAllBytes();
                    })));
            assertTrue(enteredBody.get());
        } finally { release.countDown(); server.stop(0); }
    }
    private static HttpRequest request(HttpServer server, Duration timeout) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"))
                .timeout(timeout).GET().build();
    }
}
