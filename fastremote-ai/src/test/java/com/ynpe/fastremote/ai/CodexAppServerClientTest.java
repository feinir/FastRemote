// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.ai.AiInferenceOptions;
import com.ynpe.fastremote.domain.ai.AiWorkspaceContext;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class CodexAppServerClientTest {
    @TempDir Path directory;

    @Test @Timeout(15)
    void nativeSteerUsesExpectedTurnAndResumesPersistentThreadWithoutHistoryReplay() throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
        Path executable = directory.resolve("steering-codex");
        Files.writeString(executable, """
                #!/bin/sh
                while IFS= read -r line; do
                  case "$line" in
                    *'"id":1'*) printf '%s\n' '{"id":1,"result":{}}' ;;
                    *'"id":2'*'"ephemeral":false'*) printf '%s\n' '{"id":2,"result":{"thread":{"id":"persistent"}}}' ;;
                    *'"id":3'*'"threadId":"persistent"'*)
                      printf '%s\n' '{"id":3,"result":{"turn":{"id":"turn-1"}}}'
                      printf '%s\n' '{"method":"item/agentMessage/delta","params":{"threadId":"persistent","turnId":"turn-1","itemId":"a","delta":"Before"}}' ;;
                    *'"id":4'*'"method":"turn/steer"'*'"expectedTurnId":"turn-1"'*'Change course'*)
                      printf '%s\n' '{"id":4,"result":{"turnId":"turn-1"}}'
                      printf '%s\n' '{"method":"item/agentMessage/delta","params":{"threadId":"persistent","turnId":"old-turn","itemId":"old","delta":"STALE"}}'
                      printf '%s\n' '{"method":"item/started","params":{"threadId":"persistent","turnId":"turn-1","item":{"id":"op","type":"mcpToolCall","tool":"list_hosts"}}}'
                      printf '%s\n' '{"method":"item/completed","params":{"threadId":"persistent","turnId":"turn-1","item":{"id":"op","type":"mcpToolCall","tool":"list_hosts","status":"completed","error":null}}}'
                      printf '%s\n' '{"method":"item/agentMessage/delta","params":{"threadId":"persistent","turnId":"turn-1","itemId":"b","delta":"After"}}'
                      printf '%s\n' '{"method":"turn/completed","params":{"threadId":"persistent","turn":{"id":"turn-1","status":"completed"}}}' ;;
                    *'"id":5'*'"method":"thread/resume"'*'"threadId":"persistent"'*) printf '%s\n' '{"id":5,"result":{"thread":{"id":"persistent"}}}' ;;
                    *'"id":6'*'DO_NOT_REPLAY'*) exit 9 ;;
                    *'"id":6'*)
                      printf '%s\n' '{"id":6,"result":{"turn":{"id":"turn-2"}}}'
                      printf '%s\n' '{"method":"item/agentMessage/delta","params":{"threadId":"persistent","turnId":"turn-2","itemId":"c","delta":"Resumed"}}'
                      printf '%s\n' '{"method":"turn/completed","params":{"threadId":"persistent","turn":{"id":"turn-2","status":"completed"}}}' ;;
                  esac
                done
                """);
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        String previous = System.getProperty("fastremote.codex.executable");
        System.setProperty("fastremote.codex.executable", executable.toString());
        var events = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var ready = new CountDownLatch(1);
        var thread = new java.util.concurrent.atomic.AtomicReference<String>();
        try (var client = new CodexAppServerClient(directory.resolve("steer-home"), URI.create("http://127.0.0.1:1234/mcp"), "token")) {
            var listener = new AiStreamListener() {
                @Override public void onTextDelta(String text) { events.add(text); if (text.equals("Before")) ready.countDown(); }
                @Override public void onSteeringAccepted(AiSteeringInput input) { events.add("accepted"); }
                @Override public void onToolStarted(String id, String tool) { events.add("start:" + id); }
                @Override public void onToolFinished(String id, AiToolExecution tool) { events.add("done:" + id); assertTrue(tool.succeeded()); }
            };
            var reply = CompletableFuture.supplyAsync(() -> {
                try { return client.reply("test", List.of(), "Original", List.of(), AiWorkspaceContext.none(),
                        AiInferenceOptions.DEFAULT, listener, Optional.empty(), thread::set); }
                catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            assertTrue(client.steer(new AiSteeringInput(java.util.UUID.randomUUID(), "Change course", List.of())));
            assertEquals("BeforeAfter", reply.get(5, TimeUnit.SECONDS).text());
            assertEquals(List.of("Before", "accepted", "start:op", "done:op", "After"), events);
            assertEquals("persistent", thread.get());
            var next = client.reply("test", List.of(new AiConversationMessage(AiConversationMessage.Role.USER, "DO_NOT_REPLAY")),
                    "Next", List.of(), AiWorkspaceContext.none(), AiInferenceOptions.DEFAULT, AiStreamListener.NONE,
                    Optional.of(thread.get()), thread::set);
            assertEquals("Resumed", next.text());
            assertFalse(client.steer(new AiSteeringInput(java.util.UUID.randomUUID(), "Too late", List.of())));
        } finally {
            if (previous == null) System.clearProperty("fastremote.codex.executable"); else System.setProperty("fastremote.codex.executable", previous);
        }
    }

    @Test
    void configuresThePrivateFastRemoteMcpToDeferApprovalToTheApplication() {
        try (var client = new CodexAppServerClient(directory.resolve("command-home"),
                URI.create("http://127.0.0.1:1234/mcp"), "token")) {
            List<String> command = client.appServerCommand("codex");

            assertTrue(command.contains("mcp_servers.fastremote.default_tools_approval_mode=\"approve\""));
            assertTrue(command.contains("mcp_servers.fastremote.required=true"));
            assertTrue(command.contains("mcp_servers.fastremote.tool_timeout_sec=180"));
        }
    }

    @Test
    void bypassesSystemProxyForThePrivateLoopbackMcpWithoutDroppingExistingRules() {
        Map<String, String> environment = new HashMap<>();
        environment.put("NO_PROXY", "internal.example,localhost");
        environment.put("no_proxy", "service.example,127.0.0.1");

        CodexAppServerClient.ensureLoopbackProxyBypass(environment);

        assertEquals("internal.example,localhost,service.example,127.0.0.1,::1",
                environment.get("NO_PROXY"));
        assertEquals(environment.get("NO_PROXY"), environment.get("no_proxy"));
    }

    @Test
    @Timeout(10)
    void initializesReadsAccountListsModelsAndStreamsReply() throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
        Path executable = directory.resolve("fake-codex");
        Files.writeString(executable, """
                #!/bin/sh
                while IFS= read -r line; do
                  case "$line" in
                    *'"id":1'*) printf '%s\n' '{"id":1,"result":{"userAgent":"test"}}' ;;
                    *'"id":2'*) printf '%s\n' '{"id":2,"result":{"account":null,"requiresOpenaiAuth":true}}' ;;
                    *'"id":3'*) printf '%s\n' '{"id":3,"result":{"data":[{"model":"gpt-test","inputModalities":["text","image"],"defaultReasoningEffort":"high","supportedReasoningEfforts":[{"reasoningEffort":"low"},{"reasoningEffort":"high"}]}]}}' ;;
                    *'"id":4'*'"approvalPolicy":"on-request"'*'run_local_terminal_command'*'get_operation'*'read_workspace_operation'*'Before every upload or download'*'get_remote_system'*) printf '%s\n' '{"id":4,"result":{"thread":{"id":"thread-1"}}}' ;;
                    *'"id":5'*'"effort":"high"'*'"summary":"concise"'*'data:image/png;base64,AQID'*)
                      printf '%s\n' '{"id":5,"result":{"turn":{"id":"turn-1"}}}'
                      printf '%s\n' '{"id":900,"method":"item/commandExecution/requestApproval","params":{"threadId":"thread-1","turnId":"turn-1","itemId":"native-command","command":["top"]}}' ;;
                    *'"id":900'*'"decision":"decline"'*)
                      printf '%s\n' '{"id":901,"method":"item/fileChange/requestApproval","params":{"threadId":"thread-1","turnId":"turn-1","itemId":"native-file"}}' ;;
                    *'"id":901'*'"decision":"decline"'*)
                      printf '%s\n' '{"id":902,"method":"item/permissions/requestApproval","params":{"threadId":"thread-1","turnId":"turn-1","itemId":"native-permissions"}}' ;;
                    *'"id":902'*'"permissions":{}'*)
                      printf '%s\n' '{"method":"item/agentMessage/delta","params":{"threadId":"thread-1","turnId":"turn-1","itemId":"item-1","delta":"hello"}}'
                      printf '%s\n' '{"method":"item/reasoning/summaryPartAdded","params":{"threadId":"thread-1","turnId":"turn-1","itemId":"reasoning-1","summaryIndex":0}}'
                      printf '%s\n' '{"method":"item/reasoning/summaryTextDelta","params":{"threadId":"thread-1","turnId":"turn-1","itemId":"reasoning-1","summaryIndex":0,"delta":"**Inspecting configuration**"}}'
                      printf '%s\n' '{"method":"item/reasoning/summaryPartAdded","params":{"threadId":"thread-1","turnId":"turn-1","itemId":"reasoning-1","summaryIndex":1}}'
                      printf '%s\n' '{"method":"item/reasoning/summaryTextDelta","params":{"threadId":"thread-1","turnId":"turn-1","itemId":"reasoning-1","summaryIndex":1,"delta":"**Applying changes**"}}'
                      printf '%s\n' '{"method":"turn/completed","params":{"threadId":"thread-1","turn":{"id":"turn-1","items":[],"status":"completed"}}}' ;;
                  esac
                done
                """);
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        String previous = System.getProperty("fastremote.codex.executable");
        System.setProperty("fastremote.codex.executable", executable.toString());
        try (var client = new CodexAppServerClient(directory.resolve("home"),
                URI.create("http://127.0.0.1:1234/mcp"), "token")) {
            assertFalse(client.account().signedIn());
            var capabilities = client.modelCapabilities();
            assertEquals(List.of("gpt-test"), capabilities.stream().map(
                    com.ynpe.fastremote.domain.ai.AiModelCapabilities::model).toList());
            assertEquals(List.of(com.ynpe.fastremote.domain.ai.AiReasoningEffort.LOW,
                    com.ynpe.fastremote.domain.ai.AiReasoningEffort.HIGH),
                    capabilities.getFirst().supportedReasoningEfforts());
            assertEquals(com.ynpe.fastremote.domain.ai.AiReasoningEffort.HIGH,
                    capabilities.getFirst().defaultReasoningEffort().orElseThrow());
            assertEquals(List.of(com.ynpe.fastremote.domain.ai.AiInputModality.TEXT,
                    com.ynpe.fastremote.domain.ai.AiInputModality.IMAGE), capabilities.getFirst().inputModalities());
            var deltas = new StringBuilder();
            var reasoningDeltas = new StringBuilder();
            var reply = client.reply("gpt-test", List.of(), "hello",
                    List.of(new AiImageInput("image/png", new byte[] {1, 2, 3}, 1, 1)),
                    AiWorkspaceContext.none(),
                    new AiInferenceOptions(Optional.of(com.ynpe.fastremote.domain.ai.AiReasoningEffort.HIGH), true),
                    new AiStreamListener() {
                        @Override public void onTextDelta(String delta) { deltas.append(delta); }
                        @Override public void onReasoningDelta(String delta) { reasoningDeltas.append(delta); }
                    });
            assertEquals("hello", reply.text());
            assertEquals("hello", deltas.toString());
            assertEquals("**Inspecting configuration**\n\n**Applying changes**", reply.reasoningSummary());
            assertEquals(reply.reasoningSummary(), reasoningDeltas.toString());
        } finally {
            if (previous == null) System.clearProperty("fastremote.codex.executable");
            else System.setProperty("fastremote.codex.executable", previous);
        }
    }

    @Test
    @Timeout(10)
    void interruptsAnActiveTurnThroughTheOfficialProtocol() throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
        Path executable = directory.resolve("fake-codex-interrupt");
        Files.writeString(executable, """
                #!/bin/sh
                while IFS= read -r line; do
                  case "$line" in
                    *'"id":1'*) printf '%s\n' '{"id":1,"result":{"userAgent":"test"}}' ;;
                    *'"id":2'*'"method":"thread/start"'*)
                      printf '%s\n' '{"id":2,"result":{"thread":{"id":"thread-stop"}}}' ;;
                    *'"id":3'*'"method":"turn/start"'*)
                      printf '%s\n' '{"id":3,"result":{"turn":{"id":"turn-stop"}}}'
                      sleep 0.1
                      printf '%s\n' '{"method":"item/reasoning/summaryTextDelta","params":{"threadId":"thread-stop","turnId":"turn-stop","itemId":"reasoning-1","delta":"working"}}' ;;
                    *'"id":4'*'"method":"turn/interrupt"'*'"threadId":"thread-stop"'*'"turnId":"turn-stop"'*)
                      printf '%s\n' '{"id":4,"result":{}}'
                      printf '%s\n' '{"method":"turn/completed","params":{"threadId":"thread-stop","turn":{"id":"turn-stop","items":[],"status":"interrupted"}}}' ;;
                  esac
                done
                """);
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        String previous = System.getProperty("fastremote.codex.executable");
        System.setProperty("fastremote.codex.executable", executable.toString());
        CountDownLatch streaming = new CountDownLatch(1);
        try (var client = new CodexAppServerClient(directory.resolve("interrupt-home"),
                URI.create("http://127.0.0.1:1234/mcp"), "token")) {
            CompletableFuture<AiAssistantReply> reply = CompletableFuture.supplyAsync(() -> {
                try {
                    return client.reply("gpt-test", List.of(), "wait", AiWorkspaceContext.none(),
                            AiInferenceOptions.DEFAULT, new AiStreamListener() {
                                @Override public void onReasoningDelta(String delta) { streaming.countDown(); }
                            });
                } catch (Exception failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            });
            assertTrue(streaming.await(2, TimeUnit.SECONDS));
            assertTrue(client.interruptActiveTurn());
            ExecutionException stopped = assertThrows(ExecutionException.class,
                    () -> reply.get(3, TimeUnit.SECONDS));
            assertInstanceOf(java.util.concurrent.CancellationException.class, stopped.getCause());
        } finally {
            if (previous == null) System.clearProperty("fastremote.codex.executable");
            else System.setProperty("fastremote.codex.executable", previous);
        }
    }
}
