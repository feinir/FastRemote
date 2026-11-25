// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.ynpe.fastremote.ai.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import org.junit.jupiter.api.*;

class AiRunPresentationTest {
    @BeforeAll static void start() throws Exception {
        var latch = new CountDownLatch(1);
        try { Platform.startup(latch::countDown); } catch (IllegalStateException started) { latch.countDown(); }
        assertTrue(latch.await(10, TimeUnit.SECONDS)); Platform.setImplicitExit(false);
    }
    @Test void streamsChronologicallyAndUpdatesOneToolAcrossAnInterleavedUserMessage() throws Exception {
        var rendered = new ArrayList<String>(); var accepted = new ArrayList<UUID>();
        var presentation = fx(() -> new AiRunPresentation((kind, text, complete, group, images) -> {
            int index = rendered.size(); rendered.add(kind + ":" + text);
            return (value, done) -> rendered.set(index, kind + ":" + value);
        }, input -> accepted.add(input.id())));
        UUID id = UUID.randomUUID();
        presentation.onTextDelta("Before"); presentation.onToolStarted("op-1", "inspect");
        presentation.onToolProgress("op-1", "Inspecting details");
        presentation.onSteeringAccepted(new AiSteeringInput(id, "Use host B", List.of()));
        presentation.onToolFinished("op-1", new AiToolExecution("inspect", "inspected", true));
        presentation.onTextDelta("After");
        var saved = fx(() -> presentation.finish(new AiAssistantReply("BeforeAfter", "", List.of())));
        assertEquals(List.of("ASSISTANT:Before", "TOOL:✓ inspected", "USER:Use host B", "ASSISTANT:After"), rendered);
        assertEquals(List.of("ASSISTANT", "TOOL", "USER", "ASSISTANT"), saved.stream().map(AiRunPresentation.Saved::kind).toList());
        assertEquals(List.of(id), accepted);
        presentation.onTextDelta("Late callback"); fx(() -> null); assertEquals(4, rendered.size());
    }
    @Test void interruptionKeepsPartialTextAndDoesNotClaimPendingToolCompleted() throws Exception {
        var presentation = fx(() -> new AiRunPresentation((kind, text, complete, group, images) -> (value, done) -> {}, input -> {}));
        presentation.onReasoningDelta("Reasoning"); presentation.onTextDelta("Partial"); presentation.onToolStarted("pending", "upload");
        var saved = fx(() -> presentation.finish(null));
        assertEquals("Partial", saved.get(1).text()); assertTrue(saved.get(2).text().startsWith("… upload — "));
    }
    @Test void terminalSubmissionAndStatusReadsHaveNoSuccessCheckmark() throws Exception {
        var presentation = fx(() -> new AiRunPresentation(
                (kind, text, complete, group, images) -> (value, done) -> {}, input -> {}));
        presentation.onToolFinished("send", new AiToolExecution("run_terminal_command", "completed", true));
        presentation.onToolFinished("status", new AiToolExecution("read_workspace_operation", "completed", true));
        presentation.onToolFinished("error", new AiToolExecution("run_terminal_command", "disconnected", false));

        var saved = fx(() -> presentation.finish(null));
        assertEquals("↪ " + com.ynpe.fastremote.i18n.Texts.text("ai.run.terminalSubmitted"), saved.get(0).text());
        assertEquals("↪ " + com.ynpe.fastremote.i18n.Texts.text("ai.run.operationStatusRead"), saved.get(1).text());
        assertEquals("✗ disconnected", saved.get(2).text());
    }

    private static <T> T fx(Callable<T> call) throws Exception {
        var task = new FutureTask<T>(call); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
}
