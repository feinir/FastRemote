// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.automation.AutomationGateway;
import com.ynpe.fastremote.domain.automation.AutomationLocalListing;
import com.ynpe.fastremote.domain.automation.AutomationOperationId;
import com.ynpe.fastremote.domain.automation.AutomationOperationStatus;
import com.ynpe.fastremote.domain.automation.AutomationHost;
import com.ynpe.fastremote.domain.automation.AutomationSource;
import com.ynpe.fastremote.domain.automation.AutomationWorkspaceItem;
import com.ynpe.fastremote.domain.automation.AutomationWorkspaceKind;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.terminal.RemoteCommandResult;
import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FastRemoteMcpServerTest {
    @Test
    void localBackgroundToolRequiresNoHostAndReturnsResultAndVisibilityNotice() throws Exception {
        var token = FastRemoteMcpServer.generateToken();
        var gateway = new StubGateway() {
            @Override public CompletionStage<com.ynpe.fastremote.domain.terminal.LocalCommandResult> runLocalCommand(
                    AutomationSource source, String command, Optional<Path> directory) {
                assertEquals("exit 4", command); assertEquals(Optional.of(Path.of("/tmp")), directory);
                return CompletableFuture.completedFuture(new com.ynpe.fastremote.domain.terminal.LocalCommandResult(
                        "output", "error", Optional.of(4), "FAILED", false, true, "/tmp", List.of("/bin/sh", "-c"),
                        "Executed locally in the background; not displayed in a terminal Tab."));
            }
        };
        try (var server = new FastRemoteMcpServer(gateway, 0, token.storedHash())) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            var response = send(HttpClient.newHttpClient(), endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"run_local_command\",\"arguments\":{\"command\":\"exit 4\",\"working_directory\":\"/tmp\"}}}");
            var result = new ObjectMapper().readTree(response.body()).path("result");
            assertFalse(result.path("isError").asBoolean());
            var value = result.path("structuredContent");
            assertEquals(4, value.path("exitCode").asInt()); assertEquals("FAILED", value.path("status").asText());
            assertEquals("error", value.path("stderr").asText());
            assertTrue(value.path("notice").asText().contains("not displayed"));
        }
    }

    @Test
    void returnsTerminalOutputAndCursorOverAuthenticatedMcp() throws Exception {
        var token = FastRemoteMcpServer.generateToken();
        var gateway = new StubGateway() {
            @Override public CompletionStage<com.ynpe.fastremote.domain.terminal.TerminalOutput> readTerminal(
                    AutomationSource source, String workspace, String generation, long after, int maximum, boolean screen) {
                assertEquals("pane", workspace); assertEquals("generation", generation); assertEquals(4, after);
                return CompletableFuture.completedFuture(new com.ynpe.fastremote.domain.terminal.TerminalOutput(
                        generation, after, 8, 8, false, false, "中文ok", "", false, "READY", true));
            }
        };
        try (var server = new FastRemoteMcpServer(gateway, 0, token.storedHash())) {
            server.start();
            var endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            var response = send(HttpClient.newHttpClient(), endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"read_terminal_output\",\"arguments\":{\"workspace_id\":\"pane\",\"generation\":\"generation\",\"after\":4,\"maximum\":100,\"screen\":false}}}");
            var result = new ObjectMapper().readTree(response.body()).path("result");
            assertFalse(result.path("isError").asBoolean());
            assertEquals("中文ok", result.path("structuredContent").path("text").asText());
            assertEquals(8, result.path("structuredContent").path("nextCursor").asInt());
        }
    }

    @Test
    void scopedConversationContextDoesNotFollowTheSelectedUiConversation() throws Exception {
        var token = FastRemoteMcpServer.generateToken();
        UUID id = UUID.randomUUID();
        var host = new com.ynpe.fastremote.domain.ai.AiHostAssociation(Optional.empty(), "", "ops", "192.0.2.42", 2222);
        var context = new com.ynpe.fastremote.domain.automation.AutomationConversationContext(Optional.of(id), Optional.of(host));
        try (var server = new FastRemoteMcpServer(new StubGateway(), 0, token.storedHash(), false,
                AutomationSource.BUILT_IN_AI, () -> {}, Optional.of(context))) {
            server.start();
            var response = send(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.port() + "/mcp"),
                    token.plaintext(), "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"get_conversation_context\",\"arguments\":{}}}");
            var result = new ObjectMapper().readTree(response.body()).path("result");
            assertFalse(result.path("isError").asBoolean());
            String text = result.path("content").path(0).path("text").asText();
            assertTrue(text.contains(id.toString()));
            assertTrue(text.contains("192.0.2.42"));
        }
    }

    @Test
    void handlesEightToolRequestsInParallelAndQueuesTheNinth() throws Exception {
        CountDownLatch entered = new CountDownLatch(FastRemoteMcpServer.MAX_CONCURRENT_REQUESTS);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        StubGateway gateway = new StubGateway() {
            @Override public CompletionStage<List<AutomationHost>> listHosts(AutomationSource source) {
                int running = active.incrementAndGet();
                maximum.accumulateAndGet(running, Math::max);
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("requests did not release");
                    return CompletableFuture.completedFuture(List.of());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return CompletableFuture.failedFuture(interrupted);
                } finally {
                    active.decrementAndGet();
                }
            }
        };
        var token = FastRemoteMcpServer.generateToken();
        try (var server = new FastRemoteMcpServer(gateway, 0, token.storedHash())) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            HttpClient client = HttpClient.newHttpClient();
            List<CompletableFuture<HttpResponse<String>>> requests = java.util.stream.IntStream.range(
                    0, FastRemoteMcpServer.MAX_CONCURRENT_REQUESTS + 1).mapToObj(index -> client.sendAsync(
                            HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json")
                                    .header("Authorization", "Bearer " + token.plaintext())
                                    .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":"
                                            + index + ",\"method\":\"tools/call\",\"params\":{"
                                            + "\"name\":\"list_hosts\",\"arguments\":{}}}"))
                                    .build(), HttpResponse.BodyHandlers.ofString())).toList();

            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(FastRemoteMcpServer.MAX_CONCURRENT_REQUESTS, active.get());
            assertEquals(FastRemoteMcpServer.MAX_CONCURRENT_REQUESTS, maximum.get());
            release.countDown();
            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();
            assertEquals(FastRemoteMcpServer.MAX_CONCURRENT_REQUESTS, maximum.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void bindsToLoopbackByDefaultAndAllIpv4InterfacesOnlyWhenAllowed() throws Exception {
        var token = FastRemoteMcpServer.generateToken();
        try (var local = new FastRemoteMcpServer(new StubGateway(), 0, token.storedHash());
             var remote = new FastRemoteMcpServer(new StubGateway(), 0, token.storedHash(), true,
                     AutomationSource.MCP, () -> { })) {
            assertTrue(local.bindAddress().getAddress().isLoopbackAddress());
            assertTrue(remote.bindAddress().getAddress().isAnyLocalAddress());
        }
    }

    @Test
    void observesOnlyAuthenticatedToolCalls() throws Exception {
        var token = FastRemoteMcpServer.generateToken();
        var observed = new AtomicInteger();
        try (var server = new FastRemoteMcpServer(new StubGateway(), 0, token.storedHash(),
                AutomationSource.MCP, observed::incrementAndGet)) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            HttpClient client = HttpClient.newHttpClient();
            String call = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":\"list_hosts\",\"arguments\":{}}}";

            client.send(HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(call)).build(),
                    HttpResponse.BodyHandlers.ofString());
            send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/list\",\"params\":{}}");
            assertEquals(0, observed.get());

            send(client, endpoint, token.plaintext(), call);
            assertEquals(1, observed.get());
        }
    }

    @Test
    void requiresBearerTokenAndServesInitializeAndTools() throws Exception {
        var token = FastRemoteMcpServer.generateToken();
        try (var server = new FastRemoteMcpServer(new StubGateway(), 0, token.storedHash())) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            HttpClient client = HttpClient.newHttpClient();
            String initialize = """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
                    """;
            HttpResponse<String> unauthorized = client.send(HttpRequest.newBuilder(endpoint)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(initialize)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, unauthorized.statusCode());

            HttpResponse<String> initialized = send(client, endpoint, token.plaintext(), initialize);
            assertEquals(200, initialized.statusCode());
            assertTrue(server.receivedBytes() >= initialize.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            long expectedSentBytes = initialized.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (server.sentBytes() < expectedSentBytes && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(server.sentBytes() >= expectedSentBytes);
            assertEquals("2025-11-25", new ObjectMapper().readTree(initialized.body())
                    .path("result").path("protocolVersion").asText());
            String instructions = new ObjectMapper().readTree(initialized.body()).path("result").path("instructions").asText();
            assertTrue(instructions.contains(com.ynpe.fastremote.domain.automation.AutomationExecutionGuidance.VISIBLE_FIRST));
            assertTrue(instructions.contains("Use get_operation with the returned operation ID for background upload and download"));
            assertTrue(instructions.contains("Use read_workspace_operation with the returned operationId for visible SFTP"));
            assertTrue(instructions.contains("list_remote_directory for upload destinations and list_local_directory for download destinations"));
            assertTrue(instructions.contains("Verify the original destination path is clear before transferring"));
            assertTrue(instructions.contains(com.ynpe.fastremote.domain.automation.AutomationExecutionGuidance.REMOTE_INPUT_GUIDANCE));

            HttpResponse<String> tools = send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
            var listedTools = new ObjectMapper().readTree(tools.body()).path("result").path("tools");
            var names = new java.util.HashSet<String>();
            listedTools.forEach(tool -> names.add(tool.path("name").asText()));
            var catalogTools = com.ynpe.fastremote.domain.automation.AutomationCatalogTool.values();
            for (var catalogTool : catalogTools) {
                assertTrue(names.contains(catalogTool.toolName()), catalogTool.toolName());
            }
            assertEquals(listedTools.size(), names.size());
            assertTrue(java.util.stream.StreamSupport.stream(listedTools.spliterator(), false)
                    .anyMatch(tool -> tool.path("name").asText().equals("list_host_groups")));
            for (var listedTool : listedTools) {
                String description = listedTool.path("description").asText();
                switch (listedTool.path("name").asText()) {
                    case "add_host", "update_host", "add_credential", "update_credential" -> {
                        var fields = listedTool.path("inputSchema").path("properties");
                        for (String field : List.of("password", "private_key", "passphrase")) {
                            assertEquals("string", fields.path(field).path("type").asText());
                        }
                        assertFalse(fields.has("secret_file"));
                        assertFalse(fields.has("passphrase_file"));
                    }
                    case "run_command" -> assertEquals(
                            com.ynpe.fastremote.domain.automation.AutomationExecutionGuidance.BACKGROUND_COMMAND, description);
                    case "run_terminal_command" -> assertEquals(
                            com.ynpe.fastremote.domain.automation.AutomationExecutionGuidance.REMOTE_COMMAND, description);
                    case "run_local_terminal_command" -> assertEquals(
                            com.ynpe.fastremote.domain.automation.AutomationExecutionGuidance.LOCAL_COMMAND, description);
                    default -> { }
                }
            }
            assertTrue(listedTools.findValuesAsText("name").contains("list_command_groups"));
            assertTrue(listedTools.findValuesAsText("name").contains("add_command_group"));
            assertTrue(listedTools.findValuesAsText("name").contains("list_variable_groups"));
            assertTrue(listedTools.findValuesAsText("name").contains("add_variable_group"));
            assertTrue(listedTools.findValuesAsText("name").contains("open_host"));
            assertTrue(listedTools.findValuesAsText("name").contains("list_workspace"));
            assertTrue(listedTools.findValuesAsText("name").contains("list_active_hosts"));
            assertTrue(listedTools.findValuesAsText("name").contains("get_conversation_context"));
            for (String tool : List.of("list_active_hosts", "get_conversation_context")) {
                var response = send(client, endpoint, token.plaintext(),
                        "{\"jsonrpc\":\"2.0\",\"id\":90,\"method\":\"tools/call\",\"params\":{\"name\":\""
                                + tool + "\",\"arguments\":{}}}");
                var payload = new ObjectMapper().readTree(response.body()).path("result");
                assertFalse(payload.path("isError").asBoolean(), response.body());
                assertTrue(payload.toString().contains("associated.example"), response.body());
            }
            assertTrue(listedTools.findValuesAsText("name").contains("read_workspace_text"));
            assertTrue(listedTools.findValuesAsText("name").contains("write_workspace_text"));
            assertTrue(listedTools.findValuesAsText("name").contains("read_workspace_log"));
            assertTrue(listedTools.findValuesAsText("name").contains("open_local_terminal"));
            assertTrue(listedTools.findValuesAsText("name").contains("run_terminal_command"));
            assertTrue(listedTools.findValuesAsText("name").contains("run_local_terminal_command"));
            assertTrue(listedTools.findValuesAsText("name").contains("open_sftp"));
            assertTrue(listedTools.findValuesAsText("name").contains("change_sftp_remote_directory"));
            assertTrue(listedTools.findValuesAsText("name").contains("upload_in_sftp"));
            assertTrue(listedTools.findValuesAsText("name").contains("download_in_sftp"));
            var localCommand = java.util.stream.StreamSupport.stream(listedTools.spliterator(), false)
                    .filter(tool -> tool.path("name").asText().equals("run_local_terminal_command"))
                    .findFirst().orElseThrow();
            assertFalse(localCommand.path("inputSchema").path("properties").has("host_id"));
            assertEquals("command", localCommand.path("inputSchema").path("required").path(0).asText());
            assertEquals(1, localCommand.path("inputSchema").path("required").size());
            assertTrue(localCommand.path("inputSchema").path("properties").has("reuse_existing_tab"));
            var openHost = java.util.stream.StreamSupport.stream(listedTools.spliterator(), false)
                    .filter(tool -> tool.path("name").asText().equals("open_host"))
                    .findFirst().orElseThrow();
            assertEquals(1, openHost.path("inputSchema").path("required").size());
            assertEquals("host_id", openHost.path("inputSchema").path("required").path(0).asText());
            assertTrue(listedTools.findValuesAsText("name").contains("delete_remote"));
            assertTrue(listedTools.findValuesAsText("name").contains("add_command"));
            assertTrue(listedTools.findValuesAsText("name").contains("write_remote_text"));
        }
    }

    @Test
    void localTerminalCommandDoesNotRequireOrSubstituteASavedHost() throws Exception {
        var token = FastRemoteMcpServer.generateToken();
        var gateway = new StubGateway();
        try (var server = new FastRemoteMcpServer(gateway, 0, token.storedHash())) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            HttpResponse<String> response = send(HttpClient.newHttpClient(), endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{"
                            + "\"name\":\"run_local_terminal_command\",\"arguments\":{\"command\":\"pwd\"}}}");
            var result = new ObjectMapper().readTree(response.body()).path("result");
            assertEquals("pwd", gateway.localTerminalCommand);
            assertFalse(gateway.reuseExistingTab);
            assertTrue(result.path("structuredContent").path("submitted").asBoolean());
            assertEquals("local-operation", result.path("structuredContent").path("operationId").asText());
            assertFalse(result.path("isError").asBoolean());
            assertTrue(result.path("structuredContent").path("detail").asText()
                    .contains("Execution and exit status are unconfirmed"));
        }
    }

    @Test
    void getsTheSystemOfTheExplicitTargetHost() throws Exception {
        var host = HostId.create();
        var token = FastRemoteMcpServer.generateToken();
        var gateway = new StubGateway() {
            @Override public CompletionStage<com.ynpe.fastremote.domain.automation.RemoteHostSystem> getRemoteSystem(
                    AutomationSource source, HostId id) {
                assertEquals(AutomationSource.MCP, source);
                assertEquals(host, id);
                return CompletableFuture.completedFuture(new com.ynpe.fastremote.domain.automation.RemoteHostSystem(
                        "Windows", com.ynpe.fastremote.domain.automation.RemoteHostSystem.SystemFamily.WINDOWS));
            }
        };
        try (var server = new FastRemoteMcpServer(gateway, 0, token.storedHash());
             var client = HttpClient.newHttpClient()) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            String body = """
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{
                      "name":"get_remote_system","arguments":{"host_id":"%s"}}}
                    """.formatted(host);
            var response = send(client, endpoint, token.plaintext(), body);
            var result = new ObjectMapper().readTree(response.body()).path("result");
            assertFalse(result.path("isError").asBoolean(true));
            assertEquals("Windows", result.path("structuredContent").path("operatingSystem").asText());
            assertEquals("WINDOWS", result.path("structuredContent").path("systemFamily").asText());
        }
    }

    @Test
    void exposesOpenWorkspaceEditorsAndLogs() throws Exception {
        var token = FastRemoteMcpServer.generateToken();
        StubGateway gateway = new StubGateway() {
            @Override public CompletionStage<List<AutomationWorkspaceItem>> listWorkspace(AutomationSource source) {
                return CompletableFuture.completedFuture(List.of(new AutomationWorkspaceItem(
                        "editor-1", AutomationWorkspaceKind.EDITOR, "system.conf", "/tmp/system.conf",
                        true, true, true, true, false)));
            }
            @Override public CompletionStage<String> readWorkspaceText(AutomationSource source, String workspaceId) {
                return CompletableFuture.completedFuture("current buffer");
            }
            @Override public CompletionStage<Boolean> writeWorkspaceText(
                    AutomationSource source, String workspaceId, String content) {
                return CompletableFuture.completedFuture("changed".equals(content));
            }
            @Override public CompletionStage<String> readWorkspaceLog(AutomationSource source, String workspaceId) {
                return CompletableFuture.completedFuture("operation log");
            }
        };
        try (var server = new FastRemoteMcpServer(gateway, 0, token.storedHash())) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            HttpClient client = HttpClient.newHttpClient();
            assertEquals("system.conf", new ObjectMapper().readTree(send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":31,\"method\":\"tools/call\",\"params\":{\"name\":\"list_workspace\",\"arguments\":{}}}")
                    .body()).path("result").path("structuredContent").path("result").path(0).path("title").asText());
            assertEquals("current buffer", new ObjectMapper().readTree(send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":32,\"method\":\"tools/call\",\"params\":{\"name\":\"read_workspace_text\",\"arguments\":{\"workspace_id\":\"editor-1\"}}}")
                    .body()).path("result").path("structuredContent").path("result").asText());
            assertTrue(new ObjectMapper().readTree(send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":33,\"method\":\"tools/call\",\"params\":{\"name\":\"write_workspace_text\",\"arguments\":{\"workspace_id\":\"editor-1\",\"content\":\"changed\"}}}")
                    .body()).path("result").path("structuredContent").path("result").asBoolean());
            assertEquals("operation log", new ObjectMapper().readTree(send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":34,\"method\":\"tools/call\",\"params\":{\"name\":\"read_workspace_log\",\"arguments\":{\"workspace_id\":\"editor-1\"}}}")
                    .body()).path("result").path("structuredContent").path("result").asText());
        }
    }

    @Test
    void toolCallReturnsStructuredHostData() throws Exception {
        var token = FastRemoteMcpServer.generateToken();
        try (var server = new FastRemoteMcpServer(new StubGateway(), 0, token.storedHash())) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            HttpResponse<String> response = send(HttpClient.newHttpClient(), endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"list_hosts\",\"arguments\":{}}}");
            var result = new ObjectMapper().readTree(response.body()).path("result");
            // MCP requires structuredContent to be an object; list results are wrapped.
            assertTrue(result.path("structuredContent").isObject());
            assertTrue(result.path("structuredContent").path("result").isArray());
            assertEquals("example", result.path("structuredContent").path("result").path(0).path("name").asText());
        }
    }

    @Test
    void hostListingCanBeFilteredAndIsBoundedByDefault() throws Exception {
        var token = FastRemoteMcpServer.generateToken();
        try (var server = new FastRemoteMcpServer(new StubGateway(75), 0, token.storedHash())) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> bounded = send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{"
                            + "\"name\":\"list_hosts\",\"arguments\":{}}}");
            var boundedResult = new ObjectMapper().readTree(bounded.body()).path("result").path("structuredContent").path("result");
            assertEquals(50, boundedResult.size());

            HttpResponse<String> laterPage = send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\",\"params\":{"
                            + "\"name\":\"list_hosts\",\"arguments\":{\"offset\":70,\"limit\":5}}}");
            var laterPageResult = new ObjectMapper().readTree(laterPage.body())
                    .path("result").path("structuredContent").path("result");
            assertEquals(5, laterPageResult.size());
            assertEquals("host-70", laterPageResult.path(0).path("name").asText());
            assertEquals("host-74", laterPageResult.path(4).path("name").asText());

            HttpResponse<String> filtered = send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{"
                            + "\"name\":\"list_hosts\",\"arguments\":{\"query\":\"HOST-74\",\"limit\":5}}}");
            var filteredResult = new ObjectMapper().readTree(filtered.body()).path("result").path("structuredContent").path("result");
            assertEquals(1, filteredResult.size());
            assertEquals("host-74", filteredResult.path(0).path("name").asText());
        }
    }

    @Test
    void hostGroupMetadataAndExactGroupFilterReachTheWire() throws Exception {
        var group = com.ynpe.fastremote.domain.catalog.HostGroup.root("vps");
        StubGateway gateway = new StubGateway() {
            @Override public CompletionStage<List<com.ynpe.fastremote.domain.automation.AutomationHostGroup>> listHostGroups(AutomationSource source) {
                return CompletableFuture.completedFuture(com.ynpe.fastremote.domain.automation.AutomationHostGroup.describe(List.of(group)));
            }
        };
        gateway.hosts = List.of(new AutomationHost(HostId.create(), "alpha", "dev", "example", 22,
                Optional.of(group.id()), "vps", "vps", List.of(group.id())));
        var token = FastRemoteMcpServer.generateToken();
        try (var server = new FastRemoteMcpServer(gateway, 0, token.storedHash())) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            var client = HttpClient.newHttpClient();
            var mapper = new ObjectMapper();
            var request = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"list_host_groups\",\"arguments\":{}}}");
            var groups = mapper.readTree(send(client, endpoint, token.plaintext(), request.toString()).body()).path("result").path("structuredContent").path("result");
            assertEquals("vps", groups.get(0).path("path").asText());
            ((com.fasterxml.jackson.databind.node.ObjectNode) request.path("params")).put("name", "list_hosts");
            ((com.fasterxml.jackson.databind.node.ObjectNode) request.path("params").path("arguments")).put("group_id", group.id().toString());
            var result = mapper.readTree(send(client, endpoint, token.plaintext(), request.toString()).body()).path("result").path("structuredContent").path("result");
            assertEquals(1, result.size());
            assertEquals("vps", result.get(0).path("groupName").asText());
            assertEquals("vps", result.get(0).path("groupPath").asText());
            assertEquals(group.id().toString(), result.get(0).path("groupId").path("value").asText());
        }
    }

    @Test
    void protocolErrorsStayEnglishWhenTheInterfaceUsesTraditionalChinese() throws Exception {
        com.ynpe.fastremote.i18n.Texts.configure(com.ynpe.fastremote.i18n.Localizer.forLanguage(
                com.ynpe.fastremote.domain.settings.InterfaceLanguage.TRADITIONAL_CHINESE));
        var token = FastRemoteMcpServer.generateToken();
        try (var server = new FastRemoteMcpServer(new StubGateway(true), 0, token.storedHash())) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            HttpResponse<String> response = send(HttpClient.newHttpClient(), endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"list_local_directory\",\"arguments\":{}}}");
            var result = new ObjectMapper().readTree(response.body()).path("result");
            assertTrue(result.path("isError").asBoolean(), response.body());
            assertEquals("Command name length must be between 1 and 255",
                    result.path("content").path(0).path("text").asText());
        } finally {
            com.ynpe.fastremote.i18n.Texts.configure(com.ynpe.fastremote.i18n.Localizer.forLanguage(
                    com.ynpe.fastremote.domain.settings.InterfaceLanguage.SIMPLIFIED_CHINESE));
        }
    }

    @Test
    void followsMcpProtocolErrorAndMessageRules() throws Exception {
        var token = FastRemoteMcpServer.generateToken();
        try (var server = new FastRemoteMcpServer(new StubGateway(), 0, token.storedHash())) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            var unknownMethod = mapper.readTree(send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/list\"}").body());
            assertEquals(-32601, unknownMethod.path("error").path("code").asInt());

            var unknownTool = mapper.readTree(send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"missing_tool\"}}").body());
            assertEquals(-32602, unknownTool.path("error").path("code").asInt());

            // Argument validation failures are tool results the model can read, not protocol errors.
            var badArguments = mapper.readTree(send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":\"three\",\"method\":\"tools/call\",\"params\":{\"name\":\"open_host\",\"arguments\":{\"host_id\":\"not-a-uuid\"}}}").body());
            assertEquals("three", badArguments.path("id").asText());
            assertTrue(badArguments.path("result").path("isError").asBoolean(), badArguments.toString());
            assertFalse(badArguments.path("result").path("content").path(0).path("text").asText().isBlank());
            assertFalse(badArguments.has("error"));

            var omittedArguments = mapper.readTree(send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"list_hosts\"}}").body());
            assertFalse(omittedArguments.path("result").path("isError").asBoolean(), omittedArguments.toString());

            assertEquals(202, send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").statusCode());
            assertEquals(202, send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{}}").statusCode());
            assertEquals(400, send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"ping\"}").statusCode());
            assertEquals(400, send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":{\"x\":1},\"method\":\"ping\"}").statusCode());
            assertEquals(400, send(client, endpoint, token.plaintext(),
                    "[{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"ping\"}]").statusCode());
            assertEquals(-32602, mapper.readTree(send(client, endpoint, token.plaintext(),
                    "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"ping\",\"params\":[]}").body())
                    .path("error").path("code").asInt());

            HttpResponse<String> get = client.send(HttpRequest.newBuilder(endpoint)
                    .header("Authorization", "Bearer " + token.plaintext()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(405, get.statusCode());
            assertEquals("POST", get.headers().firstValue("Allow").orElse(""));
        }
    }

    @Test
    void negotiatesProtocolVersionAndRejectsUnsupportedHeader() throws Exception {
        var token = FastRemoteMcpServer.generateToken();
        try (var server = new FastRemoteMcpServer(new StubGateway(), 0, token.storedHash())) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();
            String initialize = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                    + "{\"protocolVersion\":\"%s\",\"capabilities\":{},\"clientInfo\":{\"name\":\"t\",\"version\":\"1\"}}}";
            assertEquals("2025-06-18", mapper.readTree(send(client, endpoint, token.plaintext(),
                    initialize.formatted("2025-06-18")).body()).path("result").path("protocolVersion").asText());
            assertEquals(FastRemoteMcpServer.PROTOCOL_VERSION, mapper.readTree(send(client, endpoint, token.plaintext(),
                    initialize.formatted("2099-01-01")).body()).path("result").path("protocolVersion").asText());

            HttpResponse<String> unsupported = client.send(HttpRequest.newBuilder(endpoint)
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + token.plaintext())
                    .header("MCP-Protocol-Version", "1999-01-01")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, unsupported.statusCode());
            HttpResponse<String> supported = client.send(HttpRequest.newBuilder(endpoint)
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + token.plaintext())
                    .header("MCP-Protocol-Version", "2025-06-18")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, supported.statusCode());
        }
    }

    private static HttpResponse<String> send(HttpClient client, URI endpoint, String token, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static class StubGateway implements AutomationGateway {
        private final HostId id = new HostId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        private final boolean chineseFailure;
        private String localTerminalCommand = "";
        private boolean reuseExistingTab = true;

        private StubGateway() { this(false); }
        private StubGateway(boolean chineseFailure) { this.chineseFailure = chineseFailure; }
        private StubGateway(int hostCount) {
            this.chineseFailure = false;
            this.hosts = java.util.stream.IntStream.range(0, hostCount)
                    .mapToObj(index -> new AutomationHost(new HostId(new UUID(0, index + 1L)),
                            "host-" + index, "user-" + index, "host-" + index, 22))
                    .toList();
        }
        private List<AutomationHost> hosts = List.of(new AutomationHost(id, "example", "dev", "host", 22));
        @Override public CompletionStage<List<AutomationHost>> listHosts(AutomationSource source) {
            return CompletableFuture.completedFuture(hosts);
        }
        @Override public CompletionStage<List<com.ynpe.fastremote.domain.automation.AutomationActiveHost>> listActiveHosts(AutomationSource source) {
            var host = new com.ynpe.fastremote.domain.ai.AiHostAssociation(Optional.empty(), "", "dev", "associated.example");
            return CompletableFuture.completedFuture(List.of(new com.ynpe.fastremote.domain.automation.AutomationActiveHost(host, List.of("tab-1"))));
        }
        @Override public CompletionStage<com.ynpe.fastremote.domain.automation.AutomationConversationContext> conversationContext(AutomationSource source) {
            return CompletableFuture.completedFuture(new com.ynpe.fastremote.domain.automation.AutomationConversationContext(
                    Optional.of(UUID.randomUUID()), Optional.of(new com.ynpe.fastremote.domain.ai.AiHostAssociation(
                            Optional.of(id), "Associated", "dev", "associated.example"))));
        }
        @Override public CompletionStage<Boolean> activateHost(AutomationSource source, HostId hostId) { return failed(); }
        @Override public CompletionStage<RemoteCommandResult> runCommand(AutomationSource source, HostId hostId, String command) { return failed(); }
        @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> startLocalTerminalCommand(
                AutomationSource source, String command, boolean reuse) {
            return runLocalTerminalCommand(source, command, reuse).thenApply(value -> new com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt(
                    "local-operation", "local-pane", "generation", value, "TERMINAL"));
        }
        @Override public CompletionStage<Boolean> runLocalTerminalCommand(
                AutomationSource source, String command) {
            localTerminalCommand = command;
            return CompletableFuture.completedFuture(true);
        }
        @Override public CompletionStage<Boolean> runLocalTerminalCommand(
                AutomationSource source, String command, boolean reuseExistingTab) {
            this.reuseExistingTab = reuseExistingTab;
            return runLocalTerminalCommand(source, command);
        }
        @Override public CompletionStage<List<RemoteFileEntry>> listRemote(AutomationSource source, HostId hostId, String directory) { return failed(); }
        @Override public CompletionStage<AutomationLocalListing> listLocal(AutomationSource source, Optional<Path> directory) { return failed(); }
        @Override public CompletionStage<AutomationOperationId> upload(AutomationSource source, HostId hostId, Path localSource, String remoteTarget) { return failed(); }
        @Override public CompletionStage<AutomationOperationId> uploadIntoDirectory(AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory) { return failed(); }
        @Override public CompletionStage<AutomationOperationId> download(AutomationSource source, HostId hostId, String remoteSource, Path localTarget) { return failed(); }
        @Override public CompletionStage<Optional<AutomationOperationStatus>> operation(AutomationOperationId id) { return failed(); }
        @Override public CompletionStage<Boolean> cancel(AutomationOperationId id) { return failed(); }
        @Override public void close() { }
        private <T> CompletionStage<T> failed() {
            return CompletableFuture.failedFuture(chineseFailure
                    ? new IllegalArgumentException("命令名称长度必须为 1 到 255")
                    : new UnsupportedOperationException());
        }
    }
}
