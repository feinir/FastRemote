// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.ai.AiChatRepository;
import com.ynpe.fastremote.domain.ai.AiSettings;
import com.ynpe.fastremote.domain.ai.AiSettingsRepository;
import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.ModelProviderType;
import com.ynpe.fastremote.domain.ai.McpServerSettings;
import com.ynpe.fastremote.domain.automation.AutomationGateway;
import com.ynpe.fastremote.domain.vault.ManagedSecretVault;
import com.ynpe.fastremote.domain.vault.SecretId;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import com.ynpe.fastremote.ui.AiAssistantWorkflow;
import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultAiAssistantWorkflowConcurrencyTest {
    @Test
    void codexConversationsUseIndependentProcessesAndCancellation(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
        var executable = directory.resolve("fake-codex");
        java.nio.file.Files.writeString(executable, """
                #!/bin/sh
                model=a
                while IFS= read -r line; do
                  case "$line" in
                    *'"method":"initialize"'*) printf '%s\n' '{"id":1,"result":{}}' ;;
                    *'"method":"thread/start"'*)
                      case "$line" in *'model-b'*) model=b ;; esac
                      printf '%s\n' '{"id":2,"result":{"thread":{"id":"thread-test"}}}' ;;
                    *'"method":"turn/start"'*)
                      touch '%s/started-'"$model"
                      printf '%s\n' '{"id":3,"result":{"turn":{"id":"turn-test"}}}'
                      if [ "$model" = b ]; then
                        (
                          while [ ! -f '%s/release-b' ]; do sleep 0.05; done
                          printf '%s\n' '{"method":"item/agentMessage/delta","params":{"threadId":"thread-test","delta":"answer-b"}}'
                          printf '%s\n' '{"method":"turn/completed","params":{"threadId":"thread-test","turn":{"id":"turn-test","status":"completed"}}}'
                        ) &
                      fi ;;
                    *'"method":"turn/interrupt"'*)
                      printf '%s\n' '{"id":4,"result":{}}'
                      printf '%s\n' '{"method":"turn/completed","params":{"threadId":"thread-test","turn":{"id":"turn-test","status":"interrupted"}}}' ;;
                  esac
                done
                """.replace("%s/started-", directory.toString() + "/started-")
                    .replace("%s/release-b", directory.toString() + "/release-b"));
        java.nio.file.Files.setPosixFilePermissions(executable, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        String previous = System.getProperty("fastremote.codex.executable");
        System.setProperty("fastremote.codex.executable", executable.toString());
        try (var workflow = new DefaultAiAssistantWorkflow(fixed(AiSettings.defaults()), (AiChatRepository) Proxy.newProxyInstance(
                        AiChatRepository.class.getClassLoader(), new Class<?>[]{AiChatRepository.class}, (proxy, method, args) -> {
                            if (method.getName().equals("loadCodexThread")) return Optional.empty();
                            if (method.getName().equals("saveCodexThread")) return null;
                            throw new AssertionError("Unexpected chat call: " + method.getName());
                        }), unused(ManagedSecretVault.class), unused(AutomationGateway.class), directory.resolve("home"))) {
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            var first = workflow.sendStreaming(a, com.ynpe.fastremote.domain.ai.ChatGptCodexProvider.ID, "model-a", List.of(), "first", List.of(),
                    com.ynpe.fastremote.domain.ai.AiWorkspaceContext.none().withConversation(a, Optional.empty()),
                    com.ynpe.fastremote.domain.ai.AiInferenceOptions.DEFAULT, com.ynpe.fastremote.ai.AiStreamListener.NONE).toCompletableFuture();
            var second = workflow.sendStreaming(b, com.ynpe.fastremote.domain.ai.ChatGptCodexProvider.ID, "model-b", List.of(), "second", List.of(),
                    com.ynpe.fastremote.domain.ai.AiWorkspaceContext.none().withConversation(b, Optional.empty()),
                    com.ynpe.fastremote.domain.ai.AiInferenceOptions.DEFAULT, com.ynpe.fastremote.ai.AiStreamListener.NONE).toCompletableFuture();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while ((!java.nio.file.Files.exists(directory.resolve("started-a")) || !java.nio.file.Files.exists(directory.resolve("started-b")))
                    && System.nanoTime() < deadline) Thread.sleep(20);
            assertTrue(java.nio.file.Files.exists(directory.resolve("started-a")));
            assertTrue(java.nio.file.Files.exists(directory.resolve("started-b")));
            workflow.cancelRequest(a).toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertThrows(java.util.concurrent.ExecutionException.class, () -> first.get(4, TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertFalse(second.isDone());
            java.nio.file.Files.writeString(directory.resolve("release-b"), "go");
            assertEquals("answer-b", second.get(5, TimeUnit.SECONDS).text());
        } finally {
            java.nio.file.Files.writeString(directory.resolve("release-b"), "go");
            if (previous == null) System.clearProperty("fastremote.codex.executable"); else System.setProperty("fastremote.codex.executable", previous);
        }
    }

    @Test
    void differentModelsOverlapAndCancellationOnlyStopsItsConversation() throws Exception {
        var bothStarted = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var models = java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var handlers = java.util.concurrent.Executors.newCachedThreadPool();
        server.setExecutor(handlers);
        server.createContext("/v1/chat/completions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var model = new com.fasterxml.jackson.databind.ObjectMapper().readTree(request).path("model").asText();
            models.add(model); bothStarted.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
                byte[] body = ("data: {\"choices\":[{\"delta\":{\"content\":\"reply-" + model + "\"}}]}\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        var provider = new ModelProviderConfig("fixture", "Fixture", ModelProviderType.OPENAI_COMPATIBLE,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "model-a", Optional.empty(), true);
        var defaults = AiSettings.defaults();
        var settings = new AiSettings(List.of(provider), Optional.of(provider.id()), defaults.builtInPermissions(),
                defaults.mcpPermissions(), List.of(), List.of(), defaults.maxTransferBytes(), defaults.maxToolRounds(), defaults.mcpServer());
        try (var workflow = new DefaultAiAssistantWorkflow(fixed(settings), unused(AiChatRepository.class),
                unused(ManagedSecretVault.class), unused(AutomationGateway.class))) {
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            var first = workflow.sendStreaming(a, provider.id(), "model-a", List.of(), "first", List.of(),
                    com.ynpe.fastremote.domain.ai.AiWorkspaceContext.none().withConversation(a, Optional.empty()),
                    com.ynpe.fastremote.domain.ai.AiInferenceOptions.DEFAULT, com.ynpe.fastremote.ai.AiStreamListener.NONE).toCompletableFuture();
            var second = workflow.sendStreaming(b, provider.id(), "model-b", List.of(), "second", List.of(),
                    com.ynpe.fastremote.domain.ai.AiWorkspaceContext.none().withConversation(b, Optional.empty()),
                    com.ynpe.fastremote.domain.ai.AiInferenceOptions.DEFAULT, com.ynpe.fastremote.ai.AiStreamListener.NONE).toCompletableFuture();
            assertTrue(bothStarted.await(5, TimeUnit.SECONDS), "Both models must start before either response is released");
            assertEquals(java.util.Set.of("model-a", "model-b"), models);
            workflow.cancelRequest(a).toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertThrows(java.util.concurrent.ExecutionException.class, () -> first.get(3, TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertFalse(second.isDone());
            release.countDown();
            assertEquals("reply-model-b", second.get(5, TimeUnit.SECONDS).text());
        } finally { release.countDown(); server.stop(0); handlers.shutdownNow(); }
    }

    @Test
    void includesBackgroundAutomationTransfersInTheTrafficSnapshot() {
        AutomationGateway automation = (AutomationGateway) Proxy.newProxyInstance(
                AutomationGateway.class.getClassLoader(), new Class<?>[] {AutomationGateway.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "receivedBytes" -> 4096L;
                    case "sentBytes" -> 2048L;
                    case "close" -> null;
                    default -> throw new AssertionError("Unexpected automation call: " + method.getName());
                });
        try (DefaultAiAssistantWorkflow workflow = new DefaultAiAssistantWorkflow(
                fixed(AiSettings.defaults()), unused(AiChatRepository.class),
                unused(ManagedSecretVault.class), automation)) {
            AiAssistantWorkflow.TrafficSnapshot traffic = workflow.trafficSnapshot();

            assertEquals(4096L, traffic.receivedBytes());
            assertEquals(2048L, traffic.sentBytes());
        }
    }

    @Test
    void reportsRunningOnlyAfterTheMcpListenerStarts() throws Exception {
        try (java.net.ServerSocket reservation = new java.net.ServerSocket(0)) {
            int port = reservation.getLocalPort();
            reservation.close();
            AiSettings initial = settingsWithMcp(true, port);
            try (DefaultAiAssistantWorkflow workflow = new DefaultAiAssistantWorkflow(
                    fixed(initial), unused(AiChatRepository.class), unused(ManagedSecretVault.class),
                    unused(AutomationGateway.class))) {
                workflow.startConfiguredMcpServer().toCompletableFuture().get(2, TimeUnit.SECONDS);

                AiAssistantWorkflow.McpServerStatus status = workflow.loadMcpServerStatus()
                        .toCompletableFuture().get(2, TimeUnit.SECONDS);

                assertEquals(AiAssistantWorkflow.McpServerState.RUNNING, status.state());
                assertEquals(Optional.of(port), status.port());
                assertTrue(status.error().isEmpty());
            }
        }
    }

    @Test
    void reportsFailureWhenTheConfiguredMcpPortCannotBeBound() throws Exception {
        HttpServer blocker = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        blocker.start();
        AiSettings initial = settingsWithMcp(true, blocker.getAddress().getPort());
        try (DefaultAiAssistantWorkflow workflow = new DefaultAiAssistantWorkflow(
                fixed(initial), unused(AiChatRepository.class), unused(ManagedSecretVault.class),
                unused(AutomationGateway.class))) {
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> workflow.startConfiguredMcpServer().toCompletableFuture().get(2, TimeUnit.SECONDS));

            AiAssistantWorkflow.McpServerStatus status = workflow.loadMcpServerStatus()
                    .toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertEquals(AiAssistantWorkflow.McpServerState.FAILED, status.state());
            assertTrue(status.port().isEmpty());
            assertTrue(status.error().isPresent());
        } finally {
            blocker.stop(0);
        }
    }

    @Test
    void refusesToDisplayAnMcpKeyThatDoesNotMatchItsConfiguredHash() throws Exception {
        SecretId secretId = new SecretId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        McpServerSettings mcp = new McpServerSettings(false, McpServerSettings.DEFAULT_PORT,
                Optional.of(com.ynpe.fastremote.mcp.FastRemoteMcpServer.storeHash("current-key")), Optional.of(secretId));
        AiSettings initial = new AiSettings(AiSettings.defaults().providers(), AiSettings.defaults().selectedProviderId(),
                AiSettings.defaults().builtInPermissions(), AiSettings.defaults().mcpPermissions(),
                List.of(), List.of(), AiSettings.DEFAULT_MAX_TOOL_ROUNDS, mcp);
        AiSettingsRepository repository = new AiSettingsRepository() {
            @Override public AiSettings load() { return initial; }
            @Override public void save(AiSettings settings) { throw new AssertionError("Unexpected save"); }
        };
        ManagedSecretVault vault = (ManagedSecretVault) Proxy.newProxyInstance(
                ManagedSecretVault.class.getClassLoader(), new Class<?>[]{ManagedSecretVault.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "status" -> VaultStatus.UNLOCKED;
                    case "read" -> "stale-key".getBytes(StandardCharsets.UTF_8);
                    case "close" -> null;
                    default -> throw new AssertionError("Unexpected vault call: " + method.getName());
                });

        try (DefaultAiAssistantWorkflow workflow = new DefaultAiAssistantWorkflow(
                repository, unused(AiChatRepository.class), vault, unused(AutomationGateway.class))) {
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> workflow.loadMcpToken().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause().getMessage().contains("does not match"));
        }
    }

    @Test
    void savesSettingsWithoutWaitingForAnActiveModelNetworkRequest() throws Exception {
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseRequest = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            requestStarted.countDown();
            try {
                releaseRequest.await(10, TimeUnit.SECONDS);
                byte[] body = "{\"data\":[{\"id\":\"test-model\"}]}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        ModelProviderConfig provider = new ModelProviderConfig(
                "blocked", "Blocked provider", ModelProviderType.OPENAI_COMPATIBLE,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"),
                "test-model", Optional.empty(), false);
        AiSettings initial = new AiSettings(List.of(provider), Optional.of(provider.id()),
                AiSettings.defaults().builtInPermissions(), AiSettings.defaults().mcpPermissions(),
                List.of(), List.of(), AiSettings.DEFAULT_MAX_TOOL_ROUNDS,
                AiSettings.defaults().mcpServer());
        AtomicReference<AiSettings> stored = new AtomicReference<>(initial);
        AiSettingsRepository repository = new AiSettingsRepository() {
            @Override public AiSettings load() { return stored.get(); }
            @Override public void save(AiSettings settings) { stored.set(settings); }
        };

        try (DefaultAiAssistantWorkflow workflow = new DefaultAiAssistantWorkflow(
                repository, unused(AiChatRepository.class), unused(ManagedSecretVault.class),
                unused(AutomationGateway.class))) {
            var discovery = workflow.discoverModels(provider.id()).toCompletableFuture();
            assertTrue(requestStarted.await(2, TimeUnit.SECONDS));
            AiSettings updated = new AiSettings(initial.providers(), initial.selectedProviderId(),
                    initial.builtInPermissions(), initial.mcpPermissions(), initial.allowedLocalRoots(),
                    initial.allowedRemoteRoots(), initial.maxToolRounds() + 1, initial.mcpServer());

            AiSettings saved = workflow.saveSettings(updated).toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertEquals(updated, saved);
            assertEquals(updated, stored.get());
            releaseRequest.countDown();
            assertEquals(List.of("test-model"), discovery.get(2, TimeUnit.SECONDS));
        } finally {
            releaseRequest.countDown();
            server.stop(0);
        }
    }

    @Test
    void discoversLocalModelsWithoutWaitingForAnUnavailableOnlineCatalog() throws Exception {
        CountDownLatch onlineRequestStarted = new CountDownLatch(1);
        CountDownLatch releaseOnlineRequest = new CountDownLatch(1);
        HttpServer online = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        online.createContext("/v1/models", exchange -> {
            onlineRequestStarted.countDown();
            try {
                releaseOnlineRequest.await(10, TimeUnit.SECONDS);
                byte[] body = "{\"data\":[{\"id\":\"online-model\"}]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        online.start();
        HttpServer ollama = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ollama.createContext("/v1/models", exchange -> {
            byte[] body = "{\"data\":[{\"id\":\"local-model\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        ollama.start();

        ModelProviderConfig remoteProvider = new ModelProviderConfig(
                "remote", "Remote", ModelProviderType.OPENAI_COMPATIBLE,
                URI.create("http://127.0.0.1:" + online.getAddress().getPort() + "/v1"),
                "online-model", Optional.empty(), false);
        ModelProviderConfig localProvider = new ModelProviderConfig(
                "local", "Local", ModelProviderType.OLLAMA,
                URI.create("http://127.0.0.1:" + ollama.getAddress().getPort() + "/v1"),
                "local-model", Optional.empty(), false);
        AiSettings defaults = AiSettings.defaults();
        AiSettings settings = new AiSettings(List.of(remoteProvider, localProvider), Optional.of(remoteProvider.id()),
                defaults.builtInPermissions(), defaults.mcpPermissions(), List.of(), List.of(),
                defaults.maxToolRounds(), defaults.mcpServer());

        try (DefaultAiAssistantWorkflow workflow = new DefaultAiAssistantWorkflow(
                fixed(settings), unused(AiChatRepository.class), unused(ManagedSecretVault.class),
                unused(AutomationGateway.class))) {
            var remoteDiscovery = workflow.discoverModelCapabilities(remoteProvider.id()).toCompletableFuture();
            assertTrue(onlineRequestStarted.await(2, TimeUnit.SECONDS));

            assertEquals(List.of("local-model"), workflow.discoverModelCapabilities(localProvider.id())
                    .toCompletableFuture().get(2, TimeUnit.SECONDS).stream()
                    .map(capabilities -> capabilities.model()).toList());

            releaseOnlineRequest.countDown();
            assertEquals(List.of("online-model"), remoteDiscovery.get(2, TimeUnit.SECONDS).stream()
                    .map(capabilities -> capabilities.model()).toList());
        } finally {
            releaseOnlineRequest.countDown();
            online.stop(0);
            ollama.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T unused(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> {
                    throw new AssertionError("Unexpected call to " + type.getSimpleName() + "." + method.getName());
                });
    }

    private static AiSettings settingsWithMcp(boolean enabled, int port) {
        McpServerSettings mcp = new McpServerSettings(enabled, port,
                Optional.of(com.ynpe.fastremote.mcp.FastRemoteMcpServer.storeHash("test-key")), Optional.empty());
        AiSettings defaults = AiSettings.defaults();
        return new AiSettings(defaults.providers(), defaults.selectedProviderId(), defaults.builtInPermissions(),
                defaults.mcpPermissions(), defaults.allowedLocalRoots(), defaults.allowedRemoteRoots(),
                defaults.maxTransferBytes(), defaults.maxToolRounds(), mcp);
    }

    private static AiSettingsRepository fixed(AiSettings settings) {
        return new AiSettingsRepository() {
            @Override public AiSettings load() { return settings; }
            @Override public void save(AiSettings value) { throw new AssertionError("Unexpected save"); }
        };
    }
}
