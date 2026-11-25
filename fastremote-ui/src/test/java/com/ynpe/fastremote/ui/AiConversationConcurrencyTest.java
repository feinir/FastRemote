// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.ynpe.fastremote.ai.*;
import com.ynpe.fastremote.domain.ai.*;
import java.lang.reflect.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AiConversationConcurrencyTest {
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS)); Platform.setImplicitExit(false);
    }

    @Test void switchingModelsKeepsRepliesQueuesAndStopOwnedByTheirConversation() throws Exception {
        var futures = new ConcurrentHashMap<UUID, CompletableFuture<AiAssistantReply>>();
        var listeners = new ConcurrentHashMap<UUID, AiStreamListener>();
        var requests = new ConcurrentHashMap<UUID, String>();
        var cancelled = new CopyOnWriteArrayList<UUID>();
        var first = session("A"); var second = session("B");
        var sessions = List.of(first, second);
        AiAssistantWorkflow workflow = (AiAssistantWorkflow) Proxy.newProxyInstance(
                AiAssistantWorkflow.class.getClassLoader(), new Class<?>[]{AiAssistantWorkflow.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "sendStreaming" -> {
                            UUID id = (UUID) args[0];
                            requests.put(id, args[1] + "/" + args[2]);
                            assertEquals(Optional.of(id), ((AiWorkspaceContext) args[6]).conversationId());
                            listeners.put(id, (AiStreamListener) args[8]);
                            var future = new CompletableFuture<AiAssistantReply>(); futures.put(id, future); yield future;
                        }
                        case "appendChatMessage", "updateChatModelSelection" -> CompletableFuture.completedFuture(
                                sessions.stream().filter(s -> s.id().equals(args[0])).findFirst().orElseThrow());
                        case "savePending" -> CompletableFuture.completedFuture(null);
                        case "listChatSessions" -> CompletableFuture.completedFuture(sessions);
                        case "loadChatSession" -> CompletableFuture.completedFuture(new AiAssistantWorkflow.AiChatTranscript(
                                sessions.stream().filter(s -> s.id().equals(args[0])).findFirst().orElseThrow(), List.of()));
                        case "cancelRequest" -> {
                            UUID id = (UUID) args[0]; cancelled.add(id);
                            futures.get(id).completeExceptionally(new CancellationException());
                            yield CompletableFuture.completedFuture(null);
                        }
                        default -> CompletionStage.class.isAssignableFrom(method.getReturnType()) ? new CompletableFuture<>() : null;
                    };
                });
        var providerA = provider("provider-a"); var providerB = provider("provider-b");
        MainWindowController[] controller = new MainWindowController[1]; Stage[] window = new Stage[1];
        try {
            fx(() -> {
                var main = new MainWindowController(unused(ConnectionWorkflow.class), unused(HostCatalogWorkflow.class),
                        unused(SettingsWorkflow.class), unused(SnippetWorkflow.class), unused(LocalTerminalWorkflow.class), workflow, null);
                controller[0] = main;
                var loader = new FXMLLoader(MainView.fxml(), com.ynpe.fastremote.i18n.Texts.localizer().bundle());
                loader.setControllerFactory(type -> {
                    if (type == MainWindowController.class) return main;
                    try { return type.getDeclaredConstructor().newInstance(); } catch (Exception e) { throw new RuntimeException(e); }
                });
                Parent root = loader.load(); window[0] = new Stage(); window[0].setScene(new Scene(root));
                var defaults = AiSettings.defaults();
                set(main, "aiSettings", new AiSettings(List.of(providerA, providerB), Optional.of(providerA.id()),
                        defaults.builtInPermissions(), defaults.mcpPermissions(), List.of(), List.of(), defaults.maxTransferBytes(),
                        defaults.maxToolRounds(), defaults.mcpServer()));
                ((List<AiChatSession>) get(main, "aiChatSessions")).addAll(sessions);
                show(main, first); start(main, providerA, "model-a", "prompt-a");
                assertFalse(((Button) get(main, "aiNewChatButton")).isDisabled());
                call(main, "activateAiChat", new Class<?>[]{UUID.class}, second.id());
                return null;
            });
            fx(() -> { start(controller[0], providerB, "model-b", "prompt-b"); return null; });
            assertEquals(Map.of(first.id(), "provider-a/model-a", second.id(), "provider-b/model-b"), requests);
            listeners.get(first.id()).onTextDelta("answer-a"); listeners.get(second.id()).onTextDelta("partial-b");
            futures.get(first.id()).complete(new AiAssistantReply("answer-a", List.of()));
            fx(() -> null); fx(() -> null);
            fx(() -> {
                var main = controller[0];
                assertEquals(second.id(), get(main, "activeAiChatId"));
                var runtimes = (Map<UUID, AiConversationRuntime>) get(main, "aiRuntimes");
                assertTrue(runtimes.get(first.id()).conversation.stream().anyMatch(m -> m.text().equals("answer-a")));
                assertFalse(runtimes.get(second.id()).conversation.stream().anyMatch(m -> m.text().contains("answer-a")));
                assertFalse(runtimes.get(first.id()).running); assertTrue(runtimes.get(second.id()).running);
                call(main, "interruptAiRequest", new Class<?>[]{}); return null;
            });
            fx(() -> null); fx(() -> null);
            fx(() -> {
                assertEquals(List.of(second.id()), cancelled);
                var runtimes = (Map<UUID, AiConversationRuntime>) get(controller[0], "aiRuntimes");
                assertFalse(runtimes.get(second.id()).running);
                assertFalse(runtimes.get(first.id()).interruptRequested);
                return null;
            });
        } finally { fx(() -> { if (controller[0] != null) controller[0].close(); if (window[0] != null) window[0].close(); return null; }); }
    }
    @Test void sendQueuesWhileRunningSteerIsSeparateAndStopPreservesTheNextPrompt() throws Exception {
        var session = session("Queue test"); var provider = provider("fixture");
        var runs = new CopyOnWriteArrayList<CompletableFuture<AiAssistantReply>>();
        var listeners = new CopyOnWriteArrayList<AiStreamListener>();
        var prompts = new CopyOnWriteArrayList<String>();
        var saved = new CopyOnWriteArrayList<String>();
        var pending = new java.util.concurrent.atomic.AtomicReference<List<AiPendingPrompt>>(List.of());
        AiAssistantWorkflow workflow = (AiAssistantWorkflow) Proxy.newProxyInstance(
                AiAssistantWorkflow.class.getClassLoader(), new Class<?>[]{AiAssistantWorkflow.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "sendStreaming" -> {
                        prompts.add((String) args[4]); listeners.add((AiStreamListener) args[8]);
                        var future = new CompletableFuture<AiAssistantReply>(); runs.add(future); yield future;
                    }
                    case "appendChatMessage" -> { saved.add(args[1] + ":" + args[2]); yield CompletableFuture.completedFuture(session); }
                    case "savePending" -> { pending.set(List.copyOf((List<AiPendingPrompt>) args[1])); yield CompletableFuture.completedFuture(null); }
                    case "listChatSessions" -> CompletableFuture.completedFuture(List.of(session));
                    case "updateChatModelSelection", "renameChatSession" -> CompletableFuture.completedFuture(session);
                    case "cancelRequest" -> { runs.getLast().completeExceptionally(new CancellationException()); yield CompletableFuture.completedFuture(null); }
                    case "steer" -> {
                        listeners.getLast().onSteeringAccepted((AiSteeringInput) args[1]);
                        yield CompletableFuture.completedFuture(AiAssistantWorkflow.SteeringResult.ACCEPTED);
                    }
                    default -> CompletionStage.class.isAssignableFrom(method.getReturnType()) ? new CompletableFuture<>() : null;
                });
        MainWindowController[] holder = new MainWindowController[1];
        try {
            fx(() -> {
                var main = new MainWindowController(unused(ConnectionWorkflow.class), unused(HostCatalogWorkflow.class),
                        unused(SettingsWorkflow.class), unused(SnippetWorkflow.class), unused(LocalTerminalWorkflow.class), workflow, null);
                holder[0] = main;
                var loader = new FXMLLoader(MainView.fxml(), com.ynpe.fastremote.i18n.Texts.localizer().bundle());
                loader.setControllerFactory(type -> {
                    if (type == MainWindowController.class) return main;
                    try { return type.getDeclaredConstructor().newInstance(); } catch (Exception error) { throw new RuntimeException(error); }
                });
                Parent root = loader.load(); new Scene(root, 1200, 800);
                var defaults = AiSettings.defaults();
                set(main, "aiSettings", new AiSettings(List.of(provider), Optional.of(provider.id()), defaults.builtInPermissions(),
                        defaults.mcpPermissions(), List.of(), List.of(), defaults.maxTransferBytes(), defaults.maxToolRounds(), defaults.mcpServer()));
                ((List<AiChatSession>) get(main, "aiChatSessions")).add(session);
                show(main, session);
                ((javafx.scene.control.ComboBox<ModelProviderConfig>) get(main, "aiProviderField")).setValue(provider);
                ((javafx.scene.control.ComboBox<String>) get(main, "aiModelField")).setValue("fixture-model");
                start(main, provider, "fixture-model", "First"); return null;
            });
            queueWithSendButton(holder[0], "Second");
            waitFx(() -> pending.get().size() == 1);
            queueWithSendButton(holder[0], "Third");
            waitFx(() -> pending.get().size() == 2);
            assertEquals(List.of("First"), prompts);
            assertEquals(List.of("USER:First"), saved, "Queued messages must not enter model history prematurely");
            listeners.getFirst().onTextDelta("First answer"); runs.getFirst().complete(new AiAssistantReply("First answer", List.of()));
            waitFx(() -> prompts.size() == 2 && !runtime(holder[0], session.id()).queueSaving);
            assertEquals(List.of("First", "Second"), prompts);
            listeners.getLast().onTextDelta("Partial second");
            fx(() -> { call(holder[0], "interruptAiRequest", new Class<?>[]{}); return null; });
            waitFx(() -> !runtime(holder[0], session.id()).running && !runtime(holder[0], session.id()).queueSaving);
            assertEquals(List.of("USER:First", "ASSISTANT:First answer", "USER:Second", "ASSISTANT:Partial second"), saved);
            assertEquals(List.of("Third"), pending.get().stream().map(AiPendingPrompt::text).toList());
            fx(() -> {
                var runtime = runtime(holder[0], session.id()); assertTrue(runtime.pending.paused);
                runtime.pending.paused = false; call(holder[0], "sendNextPendingAiPrompt", new Class<?>[]{AiConversationRuntime.class}, runtime);
                return null;
            });
            waitFx(() -> prompts.size() == 3 && !runtime(holder[0], session.id()).queueSaving);
            fx(() -> {
                ((javafx.scene.control.TextArea) get(holder[0], "aiPromptField")).setText("Change target");
                call(holder[0], "sendAuthorizedAiMessage", new Class<?>[]{boolean.class}, true); return null;
            });
            waitFx(() -> runtime(holder[0], session.id()).pending.entries().stream().anyMatch(e -> e.text().equals("Change target") && e.state() == AiPromptQueue.State.ACCEPTED));
            listeners.getLast().onTextDelta("Changed target"); runs.getLast().complete(new AiAssistantReply("Changed target", List.of()));
            waitFx(() -> !runtime(holder[0], session.id()).running && !runtime(holder[0], session.id()).queueSaving);
            assertEquals(List.of("First", "Second", "Third"), prompts, "Steer must not start a separate queued request");
            assertTrue(pending.get().isEmpty());
            assertEquals(List.of("USER:Third", "USER:Change target", "ASSISTANT:Changed target"), saved.subList(4, 7));
        } finally { fx(() -> { if (holder[0] != null) holder[0].close(); return null; }); }
    }
    private static void queueWithSendButton(MainWindowController main, String text) throws Exception {
        fx(() -> {
            ((javafx.scene.control.TextArea) get(main, "aiPromptField")).setText(text);
            var send = (Button) get(main, "aiSendButton"); assertFalse(send.isDisabled()); send.fire(); return null;
        });
    }
    private static AiConversationRuntime runtime(MainWindowController main, UUID id) throws Exception {
        return ((Map<UUID, AiConversationRuntime>) get(main, "aiRuntimes")).get(id);
    }
    private static void waitFx(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!fx(condition)) { assertTrue(System.nanoTime() < deadline, "AI state did not settle"); Thread.sleep(20); }
    }

    private static AiChatSession session(String title) { return new AiChatSession(UUID.randomUUID(), title, Instant.now(), Instant.now(), 2); }
    private static ModelProviderConfig provider(String id) { return new ModelProviderConfig(id, id, ModelProviderType.OPENAI_COMPATIBLE, URI.create("http://127.0.0.1:11434/v1"), "model", Optional.empty(), true); }
    private static void show(MainWindowController main, AiChatSession s) throws Exception { call(main, "showAiChat", new Class<?>[]{AiChatSession.class,List.class}, s,List.of()); }
    private static void start(MainWindowController main, ModelProviderConfig p, String model, String prompt) throws Exception { call(main,"startAiRequest",new Class<?>[]{ModelProviderConfig.class,String.class,String.class,List.class,boolean.class},p,model,prompt,List.of(),false); }
    private static Object call(Object o,String name,Class<?>[] types,Object... args) throws Exception { var m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args); }
    private static Object get(Object o,String name) throws Exception { var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o); }
    private static void set(Object o,String name,Object value) throws Exception { var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value); }
    private static <T> T unused(Class<T> type) { return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->CompletionStage.class.isAssignableFrom(m.getReturnType())?new CompletableFuture<>():null)); }
    private static <T> T fx(Callable<T> action) throws Exception { var task=new FutureTask<T>(action);Platform.runLater(task);return task.get(15,TimeUnit.SECONDS); }
}
